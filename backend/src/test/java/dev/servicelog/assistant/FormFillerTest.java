package dev.servicelog.assistant;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// "Describe it" (S35): one sentence fills a form, with the same privacy as the assistant
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FormFillerTest {

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
    private String bearer;
    private long kowalski;
    private long property;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());
        Customer c = new Customer("David Kowalski", "+14135550108", null, null);
        c.addProperty(new Property("64 Pleasant St", null, "Easthampton", "MA", "01027", null));
        c = customers.save(c);
        kowalski = c.getId();
        property = c.getProperties().get(0).getId();
        sentToAi.clear();
    }

    @Test
    void customer_isFilledFromPlaceholders_andNoDetailReachesTheAi() throws Exception {
        aiAnswers(Map.of("name", "Person_1", "phone", "Phone_1", "email", "Email_1", "street", "Street_2",
                "unit", "", "town", "Westfield", "state", "ma", "zip", "01085", "notes", "Two oil burners"));

        fill("customer", "Mark Lee, 413-555-0166, mark@example.com, 22 Birch Road, Westfield 01085, two oil burners")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mark Lee"))
                .andExpect(jsonPath("$.phone").value("413-555-0166"))
                .andExpect(jsonPath("$.email").value("mark@example.com"))
                .andExpect(jsonPath("$.properties[0].street").value("22 Birch Road"))
                .andExpect(jsonPath("$.properties[0].unit").value(nullValue()))
                .andExpect(jsonPath("$.properties[0].state").value("MA"))
                .andExpect(jsonPath("$.properties[0].zip").value("01085"));
        assertThat(sentToAi.get(0)).doesNotContain("Mark", "Lee", "555-0166", "mark@", "Birch")
                .contains("Westfield", "01085", "\"strict\":true");
    }

    @Test
    void job_findsTheCustomerAndTheirOnlyAddress() throws Exception {
        aiAnswers(Map.of("customer", "Customer_" + kowalski, "type", "SERVICE_CALL", "date", "2026-10-27",
                "start_time", "9:00", "duration_minutes", 90, "urgent", true, "notes", "No heat, oil smell"));

        fill("job", "No heat at Kowalski's, oil smell, next Tuesday at 9, hour and a half")
                .andExpect(jsonPath("$.customerId").value(kowalski))
                .andExpect(jsonPath("$.customerName").value("David Kowalski"))
                .andExpect(jsonPath("$.propertyId").value(property))
                .andExpect(jsonPath("$.type").value("SERVICE_CALL"))
                .andExpect(jsonPath("$.date").value("2026-10-27"))
                .andExpect(jsonPath("$.startTime").value("09:00"))
                .andExpect(jsonPath("$.durationMinutes").value(90))
                .andExpect(jsonPath("$.urgency").value("URGENT"));
        assertThat(sentToAi.get(0)).doesNotContain("Kowalski").contains("Customer_" + kowalski);
    }

    @Test
    void job_withSomeoneUnknown_leavesTheCustomerForTheOwnerToPick() throws Exception {
        aiAnswers(Map.of("customer", "Person_1", "type", "TUNE_UP", "date", "not a date",
                "start_time", "25:99", "urgent", false));

        fill("job", "Tune-up for Sam Ortiz sometime")
                .andExpect(jsonPath("$.customerId").value(nullValue()))
                .andExpect(jsonPath("$.customerName").value("Sam Ortiz"))
                .andExpect(jsonPath("$.date").value(nullValue()))
                .andExpect(jsonPath("$.startTime").value(nullValue()));
    }

    @Test
    void equipment_isCleanedUp() throws Exception {
        aiAnswers(Map.of("type", "MINI_SPLIT_OUTDOOR", "brand", "Mitsubishi", "model", "mxz-3c24na",
                "serial_number", "54a01234", "install_date", "2099-01-01", "notes", "Side of the garage"));

        fill("equipment", "Mitsubishi mini split condenser MXZ-3C24NA serial 54A01234 on the side of the garage")
                .andExpect(jsonPath("$.type").value("MINI_SPLIT_OUTDOOR"))
                .andExpect(jsonPath("$.model").value("MXZ-3C24NA"))
                .andExpect(jsonPath("$.serialNumber").value("54A01234"))
                .andExpect(jsonPath("$.installDate").value(nullValue())); // never in the future
    }

    @Test
    void validation() throws Exception {
        fill("invoice", "anything").andExpect(status().isBadRequest());
        fill("customer", " ").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/assistant/fill").contentType(MediaType.APPLICATION_JSON)
                .content("{\"form\": \"customer\", \"text\": \"hi\"}")).andExpect(status().isUnauthorized());
    }

    private void aiAnswers(Map<String, Object> fields) {
        String content = json.writeValueAsString(fields);
        when(chatModel.complete(any())).thenAnswer(call -> {
            sentToAi.add(json.writeValueAsString(call.getArgument(0)));
            return json.readTree(json.writeValueAsString(Map.of("choices", List.of(Map.of("message",
                    Map.of("role", "assistant", "content", content))))));
        });
    }

    private ResultActions fill(String form, String text) throws Exception {
        return mockMvc.perform(post("/api/assistant/fill").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("form", form, "text", text))));
    }
}
