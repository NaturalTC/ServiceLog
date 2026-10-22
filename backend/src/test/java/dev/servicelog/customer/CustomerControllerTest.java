package dev.servicelog.customer;

import com.jayway.jsonpath.JsonPath;
import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CustomerControllerTest {

    private static final String SMITH = """
            {"name": "  Pat Smith ", "phone": "(413) 555-1234", "email": "Pat@Example.com", "notes": "",
             "properties": [{"street": "12 Oak St", "city": "Springfield", "state": "ma", "zip": "01103"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    private String bearer;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());
    }

    @Test
    void customerRoutes_requireLogin() throws Exception {
        mockMvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(SMITH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_savesCustomerWithProperty_andCleansInput() throws Exception {
        send(post("/api/customers"), SMITH)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Pat Smith"))
                .andExpect(jsonPath("$.phone").value("+14135551234"))
                .andExpect(jsonPath("$.email").value("pat@example.com"))
                .andExpect(jsonPath("$.notes").value(nullValue()))
                .andExpect(jsonPath("$.properties", hasSize(1)))
                .andExpect(jsonPath("$.properties[0].id").isNumber())
                .andExpect(jsonPath("$.properties[0].state").value("MA"));
    }

    @Test
    void create_withSeveralProperties() throws Exception {
        send(post("/api/customers"), """
                {"name": "Pat Smith", "phone": "4135551234", "properties": [
                  {"street": "12 Oak St", "city": "Springfield", "state": "MA", "zip": "01103"},
                  {"street": "5 Elm St", "unit": "2B", "city": "Holyoke", "state": "MA", "zip": "01040-1234"}]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.properties", hasSize(2)))
                .andExpect(jsonPath("$.properties[1].unit").value("2B"));
    }

    @Test
    void create_requiresNamePhoneAndAProperty() throws Exception {
        send(post("/api/customers"), """
                {"name": " ", "phone": "", "properties": []}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value(containsString("name")))
                .andExpect(jsonPath("$.message").value(containsString("phone")))
                .andExpect(jsonPath("$.message").value(containsString("at least one property")));
    }

    @Test
    void create_rejectsInvalidPhone() throws Exception {
        send(post("/api/customers"), SMITH.replace("(413) 555-1234", "555-1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("10-digit")));
    }

    @Test
    void create_rejectsBadEmailStateAndZip() throws Exception {
        send(post("/api/customers"), """
                {"name": "Pat", "phone": "4135551234", "email": "not-an-email",
                 "properties": [{"street": "12 Oak St", "city": "Springfield", "state": "Mass", "zip": "1103"}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("email")))
                .andExpect(jsonPath("$.message").value(containsString("properties[0].state")))
                .andExpect(jsonPath("$.message").value(containsString("properties[0].zip")));
    }

    @Test
    void get_returnsCustomer_or404() throws Exception {
        long id = createSmith();

        mockMvc.perform(get("/api/customers/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pat Smith"));
        mockMvc.perform(get("/api/customers/999999").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void list_isSortedByName() throws Exception {
        createSmith();
        send(post("/api/customers"), SMITH.replace("Pat Smith", "Alex Adams")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/customers").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Alex Adams"))
                .andExpect(jsonPath("$[1].name").value("Pat Smith"));
    }

    @Test
    void update_changesDetails_andEditsAddsAndRemovesProperties() throws Exception {
        String created = send(post("/api/customers"), """
                {"name": "Pat Smith", "phone": "4135551234", "properties": [
                  {"street": "12 Oak St", "city": "Springfield", "state": "MA", "zip": "01103"},
                  {"street": "5 Elm St", "city": "Holyoke", "state": "MA", "zip": "01040"}]}
                """).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        long oakId = ((Number) JsonPath.read(created, "$.properties[0].id")).longValue();

        // Keep Oak St (edited), drop Elm St, add Maple Rd
        send(put("/api/customers/" + id), """
                {"name": "Pat Smith-Jones", "phone": "413-555-9999", "email": "pat@example.com",
                 "properties": [
                   {"id": %d, "street": "12 Oak Street", "city": "Springfield", "state": "MA", "zip": "01103"},
                   {"street": "7 Maple Rd", "city": "Westfield", "state": "MA", "zip": "01085"}]}
                """.formatted(oakId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pat Smith-Jones"))
                .andExpect(jsonPath("$.phone").value("+14135559999"))
                .andExpect(jsonPath("$.properties", hasSize(2)))
                .andExpect(jsonPath("$.properties[0].id").value(oakId))
                .andExpect(jsonPath("$.properties[0].street").value("12 Oak Street"))
                .andExpect(jsonPath("$.properties[1].street").value("7 Maple Rd"));
    }

    @Test
    void update_cannotRemoveEveryProperty() throws Exception {
        long id = createSmith();

        send(put("/api/customers/" + id), """
                {"name": "Pat Smith", "phone": "4135551234", "properties": []}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_rejectsAnotherCustomersProperty() throws Exception {
        long smith = createSmith();
        String other = send(post("/api/customers"), SMITH.replace("Pat Smith", "Alex Adams"))
                .andReturn().getResponse().getContentAsString();
        long othersPropertyId = ((Number) JsonPath.read(other, "$.properties[0].id")).longValue();

        send(put("/api/customers/" + smith), """
                {"name": "Pat Smith", "phone": "4135551234",
                 "properties": [{"id": %d, "street": "x", "city": "y", "state": "MA", "zip": "01103"}]}
                """.formatted(othersPropertyId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("doesn't belong")));
    }

    @Test
    void update_unknownCustomer_returns404() throws Exception {
        send(put("/api/customers/999999"), SMITH).andExpect(status().isNotFound());
    }

    @Test
    void malformedJson_returns400() throws Exception {
        send(post("/api/customers"), "{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private long createSmith() throws Exception {
        String body = send(post("/api/customers"), SMITH).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String json) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
