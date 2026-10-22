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
                .andExpect(jsonPath("$.customers[0].address").value("64 Pleasant St, Easthampton, MA 01027"));

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
