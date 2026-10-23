package dev.servicelog.assistant;

import com.jayway.jsonpath.JsonPath;
import dev.servicelog.ai.AiUnavailableException;
import dev.servicelog.ai.ChatModel;
import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.Property;
import dev.servicelog.security.JwtService;
import dev.servicelog.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// "Ask ServiceLog" (S33), with a scripted AI: what the AI asks for, what it's sent back,
// and above all that no customer's personal details ever reach it.
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AssistantControllerTest {

    // Details that must never appear in anything sent to the AI provider
    private static final List<String> PRIVATE = List.of("David", "Kowalski", "555-0108", "5550108",
            "david@example.com", "Pleasant", "John", "Lee", "555-0199", "Elm Street");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private JavaMailSender mailSender;

    private final List<String> sentToAi = new ArrayList<>();
    private final Deque<String> scripted = new ArrayDeque<>();
    private String bearer;
    private long customerId;
    private long boilerId;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());

        Customer kowalski = new Customer("David Kowalski", "+14135550108", "david@example.com", null);
        kowalski.addProperty(new Property("64 Pleasant St", null, "Easthampton", "MA", "01027", null));
        kowalski = customers.save(kowalski);
        customerId = kowalski.getId();
        long property = kowalski.getProperties().get(0).getId();
        boilerId = id(api(post("/api/properties/" + property + "/equipment"),
                "{\"type\": \"BOILER\", \"brand\": \"Weil-McLain\", \"model\": \"WGO-4\"}"), "$.equipment.id");
        api(post("/api/equipment/" + boilerId + "/service-records"), """
                {"serviceDate": "2026-10-08", "kind": "TUNE_UP",
                 "workDone": "Annual on the boiler for David Kowalski at 64 Pleasant St, call him at 413-555-0108",
                 "partsUsed": "Nozzle 0.75 80B"}""");

        sentToAi.clear();
        scripted.clear();
        when(chatModel.complete(any())).thenAnswer(call -> {
            sentToAi.add(json.writeValueAsString(call.getArgument(0)));
            String next = scripted.poll();
            if (next == null) {
                throw new AssertionError("The AI was called more times than scripted");
            }
            return json.readTree(next);
        });
    }

    @Test
    void answersFromTheData_andNoPersonalDetailReachesTheAi() throws Exception {
        scripted.add(toolCall("call_1", "find_customers", "{\"query\": \"Customer_1\", \"town\": null}"));
        scripted.add(toolCall("call_2", "get_service_history",
                "{\"equipment_id\": " + boilerId + ", \"limit\": 3}"));
        scripted.add(answer("Last time at **Customer_1** (Street_1): a tune-up on **Thu, Oct 8**. "
                + "Parts: Nozzle 0.75 80B."));

        ask(null, "What did I do at the Kowalski house last time?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(
                        "Last time at **David Kowalski** (64 Pleasant St): a tune-up on **Thu, Oct 8**. "
                                + "Parts: Nozzle 0.75 80B."))
                .andExpect(jsonPath("$.customers", hasSize(1)))
                .andExpect(jsonPath("$.customers[0].id").value(customerId))
                .andExpect(jsonPath("$.customers[0].phone").value("+14135550108"))
                .andExpect(jsonPath("$.customers[0].address").value("64 Pleasant St, Easthampton, MA 01027"))
                .andExpect(jsonPath("$.lookups[0]").value("find_customers"))
                .andExpect(jsonPath("$.lookups[1]").value("get_service_history"));

        assertThat(sentToAi).hasSize(3);
        assertNothingPrivateSent();
        // The AI did get the facts it needed, with placeholders
        String lastRequest = sentToAi.get(2);
        assertThat(lastRequest).contains("Customer_1", "Nozzle 0.75 80B", "Weil-McLain", "Easthampton",
                "\\\"equipment_id\\\":" + boilerId);
        // ...including notes written about the customer, scrubbed the same way
        assertThat(lastRequest).contains("Annual on the boiler for Customer_1 at Street_1, call him at Phone_1");
    }

    @Test
    void someoneTheAppDoesntKnow_isHiddenToo() throws Exception {
        scripted.add(answer("I can't add customers from the chat yet. Use Customers, then + New, for Person_1."));

        ask(null, "Add a new customer John Lee, 413-555-0199, 12 Elm Street, Westfield")
                .andExpect(jsonPath("$.reply").value(containsString("for John Lee.")))
                .andExpect(jsonPath("$.customers", hasSize(0)));
        assertNothingPrivateSent();
        assertThat(sentToAi.get(0)).contains("Person_1", "Phone_1", "Westfield").containsPattern("Street_\\d");
    }

    @Test
    void aConversationKeepsItsHistoryAndPlaceholders() throws Exception {
        scripted.add(answer("Customer_1 has a Weil-McLain boiler."));
        String first = ask(null, "What does Kowalski have?").andReturn().getResponse().getContentAsString();
        String conversation = JsonPath.read(first, "$.conversationId");

        scripted.add(answer("Yes, Customer_1 is in Easthampton."));
        ask(conversation, "Is David Kowalski in Easthampton?")
                .andExpect(jsonPath("$.conversationId").value(conversation))
                .andExpect(jsonPath("$.reply").value("Yes, David Kowalski is in Easthampton."));

        // The second request carries the first question and answer, with the same placeholder
        assertThat(sentToAi.get(1)).contains("What does Customer_1 have?", "Customer_1 has a Weil-McLain boiler.",
                "Is Customer_1 in Easthampton?");
        assertNothingPrivateSent();
    }

    @Test
    void schedulingAJob_preparesACard_andBooksNothing() throws Exception {
        scripted.add(toolCall("call_1", "propose_job", """
                {"customer_id": 1, "property_id": null, "type": "TUNE_UP", "date": "2026-11-03",
                 "start_time": "9:00", "duration_minutes": 90, "urgent": false,
                 "notes": "Customer_1 prefers mornings, call Phone_1 first"}"""));
        scripted.add(answer("A tune-up for Customer_1 on Tue, Nov 3 at 9:00 AM is ready for you to confirm."));

        ask(null, "Book Kowalski for a tune-up Tuesday Nov 3 at 9, hour and a half, call 413-555-0108 first")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposals", hasSize(1)))
                .andExpect(jsonPath("$.proposals[0].type").value("JOB"))
                .andExpect(jsonPath("$.proposals[0].title").value("Tune-up"))
                .andExpect(jsonPath("$.proposals[0].customerId").value(customerId))
                .andExpect(jsonPath("$.proposals[0].payload.propertyId").isNumber())
                .andExpect(jsonPath("$.proposals[0].payload.date").value("2026-11-03"))
                .andExpect(jsonPath("$.proposals[0].payload.startTime").value("09:00"))
                .andExpect(jsonPath("$.proposals[0].payload.durationMinutes").value(90))
                .andExpect(jsonPath("$.proposals[0].payload.urgency").value("NORMAL"))
                .andExpect(jsonPath("$.proposals[0].payload.notes")
                        .value("David Kowalski prefers mornings, call 413-555-0108 first"))
                .andExpect(jsonPath("$.proposals[0].details[0].value").value("David Kowalski"))
                .andExpect(jsonPath("$.proposals[0].details[2].value").value("Tue, Nov 3 at 9:00 AM"))
                .andExpect(jsonPath("$.proposals[0].details[3].value").value("1 hr 30 min"));

        assertThat(jdbc.queryForObject("select count(*) from job", Integer.class)).isZero();
        assertThat(sentToAi.get(1)).contains("NOT saved until confirmed");
        assertNothingPrivateSent();
    }

    @Test
    void aNewCustomer_isPreparedFromPlaceholders_withWhatsStillMissing() throws Exception {
        scripted.add(toolCall("call_1", "propose_customer", """
                {"name": "Person_1", "phone": "Phone_1", "email": null, "street": "Street_2", "unit": null,
                 "town": "Westfield", "state": null, "zip": null, "notes": "Two oil burners"}"""));
        scripted.add(answer("Customer Person_1 is ready to confirm; add the ZIP code."));

        ask(null, "New customer John Lee, 413-555-0199, 12 Elm Street, Westfield, two oil burners")
                .andExpect(jsonPath("$.proposals[0].type").value("CUSTOMER"))
                .andExpect(jsonPath("$.proposals[0].payload.name").value("John Lee"))
                .andExpect(jsonPath("$.proposals[0].payload.phone").value("413-555-0199"))
                .andExpect(jsonPath("$.proposals[0].payload.properties[0].street").value("12 Elm Street"))
                .andExpect(jsonPath("$.proposals[0].payload.properties[0].city").value("Westfield"))
                .andExpect(jsonPath("$.proposals[0].payload.properties[0].state").value("MA"))
                .andExpect(jsonPath("$.proposals[0].missing[0]").value("ZIP code"))
                .andExpect(jsonPath("$.reply").value("Customer John Lee is ready to confirm; add the ZIP code."));

        assertThat(jdbc.queryForObject("select count(*) from customer", Integer.class)).isEqualTo(1);
        assertNothingPrivateSent();
    }

    @Test
    void aServiceRecord_isPreparedForTheRightUnit() throws Exception {
        scripted.add(toolCall("call_1", "propose_service_record", """
                {"equipment_id": %d, "kind": "REPAIR", "service_date": null, "work_done": "Replaced the igniter",
                 "parts_used": "Igniter", "notes": "Quote a new circulator.", "follow_up_needed": true,
                 "follow_up_note": "Quote a new circulator"}""".formatted(boilerId)));
        scripted.add(answer("Ready to confirm."));

        ask(null, "Log a repair on the Kowalski boiler: replaced the igniter, quote a new circulator")
                .andExpect(jsonPath("$.proposals[0].type").value("SERVICE_RECORD"))
                .andExpect(jsonPath("$.proposals[0].equipmentId").value(boilerId))
                .andExpect(jsonPath("$.proposals[0].payload.kind").value("REPAIR"))
                .andExpect(jsonPath("$.proposals[0].payload.followUpNote").value("Quote a new circulator"))
                // the same text in notes isn't repeated
                .andExpect(jsonPath("$.proposals[0].payload.notes").doesNotExist())
                .andExpect(jsonPath("$.proposals[0].details[1].value").value("David Kowalski · Easthampton"));
        assertThat(jdbc.queryForObject("select count(*) from service_record", Integer.class)).isEqualTo(1);
    }

    @Test
    void aProposalWithBadDetails_isSentBackToTheAiToFix() throws Exception {
        scripted.add(toolCall("call_1", "propose_job", """
                {"customer_id": 999, "property_id": null, "type": "TUNE_UP", "date": "2026-11-03",
                 "start_time": "09:00", "duration_minutes": 60, "urgent": false, "notes": null}"""));
        scripted.add(answer("I couldn't find that customer."));

        ask(null, "Book customer 999").andExpect(jsonPath("$.proposals", hasSize(0)));
        assertThat(sentToAi.get(1)).contains("No customer with that id");
    }

    @Test
    void troubleshooting_usesOtherUnitsOfTheSameModel_andIsLabelledAsSuggestions() throws Exception {
        Customer doe = new Customer("Jane Doe", "+14135550177", null, null);
        doe.addProperty(new Property("9 Cedar Ln", null, "Agawam", "MA", "01001", null));
        long doeProperty = customers.save(doe).getProperties().get(0).getId();
        long doeBoiler = id(api(post("/api/properties/" + doeProperty + "/equipment"),
                "{\"type\": \"BOILER\", \"brand\": \"Weil-McLain\", \"model\": \"WGO-4\"}"), "$.equipment.id");
        api(post("/api/equipment/" + doeBoiler + "/service-records"), """
                {"serviceDate": "2026-09-01", "kind": "REPAIR", "workDone": "Jane Doe's boiler: replaced the low water cutoff",
                 "partsUsed": "LWCO probe"}""");

        scripted.add(toolCall("call_1", "troubleshooting_context", "{\"equipment_id\": " + boilerId + "}"));
        scripted.add(answer("Shut it off first if you smell oil. Likely causes: the low water cutoff (failed on "
                + "another WGO-4), then the nozzle."));

        ask(null, "Help me troubleshoot unit #" + boilerId + ": boiler locks out")
                .andExpect(jsonPath("$.suggestions").value(true));

        String context = sentToAi.get(1);
        assertThat(context).contains("same_model\\\":true", "LWCO probe", "Nozzle 0.75 80B")
                .doesNotContain("Jane", "Doe", "Cedar");
        assertNothingPrivateSent();
    }

    @Test
    void ordinaryAnswers_arentLabelledAsSuggestions() throws Exception {
        scripted.add(answer("Nothing scheduled."));
        ask(null, "Anything today?").andExpect(jsonPath("$.suggestions").value(false));
    }

    @Test
    void aFailingLookup_isReportedToTheAi_notToTheOwner() throws Exception {
        scripted.add(toolCall("call_1", "get_service_history", "{\"equipment_id\": 999999, \"limit\": 3}"));
        scripted.add(answer("I couldn't find that unit."));

        ask(null, "History for unit 999999?").andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("I couldn't find that unit."));
        assertThat(sentToAi.get(1)).contains("No equipment with id 999999");
    }

    @Test
    void endlessLookups_stopWithAPoliteAnswer() throws Exception {
        for (int i = 0; i < 6; i++) {
            scripted.add(toolCall("call_" + i, "tune_ups_due", "{\"town\": null}"));
        }
        ask(null, "Who's due?").andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(containsString("couldn't finish")));
        assertThat(sentToAi).hasSize(6);
    }

    @Test
    void whenAiIsUnavailable_theOwnerGetsA503() throws Exception {
        doThrow(new AiUnavailableException("The AI assistant isn't set up on this server."))
                .when(chatModel).complete(any());
        ask(null, "What's on tomorrow?")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    }

    @Test
    void validationAndLogin() throws Exception {
        ask(null, " ").andExpect(status().isBadRequest());
        ask(null, "x".repeat(2001)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/assistant/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/assistant/conversations/abc").header("Authorization", bearer))
                .andExpect(status().isNoContent());
    }

    private void assertNothingPrivateSent() {
        for (String request : sentToAi) {
            for (String detail : PRIVATE) {
                assertThat(request).as("sent to the AI").doesNotContainIgnoringCase(detail);
            }
        }
    }

    private ResultActions ask(String conversationId, String message) throws Exception {
        return mockMvc.perform(post("/api/assistant/messages").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(conversationId == null
                        ? Map.of("message", message) : Map.of("conversationId", conversationId, "message", message))));
    }

    private String toolCall(String id, String name, String arguments) {
        return json.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of(
                "role", "assistant",
                "tool_calls", List.of(Map.of("id", id, "type", "function",
                        "function", Map.of("name", name, "arguments", arguments))))))));
    }

    private String answer(String content) {
        return json.writeValueAsString(Map.of("choices", List.of(Map.of("message",
                Map.of("role", "assistant", "content", content)))));
    }

    private String api(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                       String body) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse().getContentAsString();
    }

    private static long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }
}
