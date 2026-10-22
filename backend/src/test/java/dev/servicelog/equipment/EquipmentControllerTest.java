package dev.servicelog.equipment;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

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
class EquipmentControllerTest {

    private static final String CONDENSER = """
            {"type": "MINI_SPLIT_OUTDOOR", "brand": " Mitsubishi ", "model": "MXZ-3C24NA",
             "serialNumber": "SN-123", "installDate": "2023-06-15", "notes": ""}
            """;

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

    @MockitoBean
    private JavaMailSender mailSender;

    private String bearer;
    private Customer smith;
    private long oakStreet;
    private long elmStreet;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());

        smith = new Customer("Pat Smith", "+14135551234", null, null);
        smith.addProperty(new Property("12 Oak St", null, "Springfield", "MA", "01103", null));
        smith.addProperty(new Property("5 Elm St", "2B", "Holyoke", "MA", "01040", null));
        smith = customers.save(smith);
        oakStreet = smith.getProperties().get(0).getId();
        elmStreet = smith.getProperties().get(1).getId();
    }

    @Test
    void equipmentRoutes_requireLogin() throws Exception {
        mockMvc.perform(get("/api/equipment-types")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/properties/" + oakStreet + "/equipment")
                        .contentType(MediaType.APPLICATION_JSON).content(CONDENSER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void types_listsEveryTypeWithALabel() throws Exception {
        mockMvc.perform(get("/api/equipment-types").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(EquipmentType.values().length)))
                .andExpect(jsonPath("$[0].value").value("OIL_BURNER"))
                .andExpect(jsonPath("$[0].label").value("Oil burner"));
    }

    @Test
    void create_recordsEquipmentAtAProperty() throws Exception {
        send(post("/api/properties/" + oakStreet + "/equipment"), CONDENSER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipment.id").isNumber())
                .andExpect(jsonPath("$.equipment.propertyId").value(oakStreet))
                .andExpect(jsonPath("$.equipment.type").value("MINI_SPLIT_OUTDOOR"))
                .andExpect(jsonPath("$.equipment.typeLabel").value("Mini-split outdoor unit"))
                .andExpect(jsonPath("$.equipment.brand").value("Mitsubishi"))
                .andExpect(jsonPath("$.equipment.installDate").value("2023-06-15"))
                .andExpect(jsonPath("$.equipment.notes").value(nullValue()))
                .andExpect(jsonPath("$.customerId").value(smith.getId()))
                .andExpect(jsonPath("$.customerName").value("Pat Smith"))
                .andExpect(jsonPath("$.address").value("12 Oak St, Springfield, MA 01103"));
    }

    @Test
    void create_onlyTypeIsRequired() throws Exception {
        send(post("/api/properties/" + oakStreet + "/equipment"), "{\"type\": \"BOILER\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipment.serialNumber").value(nullValue()));
        send(post("/api/properties/" + oakStreet + "/equipment"), "{\"brand\": \"Burnham\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("type")));
    }

    @Test
    void create_rejectsUnknownType() throws Exception {
        send(post("/api/properties/" + oakStreet + "/equipment"), "{\"type\": \"SPACESHIP\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_rejectsFutureInstallDate() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        send(post("/api/properties/" + oakStreet + "/equipment"),
                "{\"type\": \"BOILER\", \"installDate\": \"" + tomorrow + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("future")));
    }

    @Test
    void create_atUnknownProperty_returns404() throws Exception {
        send(post("/api/properties/999999/equipment"), CONDENSER).andExpect(status().isNotFound());
    }

    @Test
    void serialNumber_mustBeUnique_ignoringCase_andSaysWhereItIs() throws Exception {
        send(post("/api/properties/" + oakStreet + "/equipment"), CONDENSER).andExpect(status().isCreated());

        send(post("/api/properties/" + elmStreet + "/equipment"), CONDENSER.replace("SN-123", "sn-123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(containsString("Pat Smith at 12 Oak St")));
    }

    @Test
    void manyUnitsWithoutSerials_areAllowed() throws Exception {
        for (int i = 0; i < 3; i++) {
            send(post("/api/properties/" + oakStreet + "/equipment"), "{\"type\": \"OTHER\", \"serialNumber\": \"  \"}")
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void get_returnsEquipmentWithLocation_or404() throws Exception {
        long id = create(oakStreet, CONDENSER);

        mockMvc.perform(get("/api/equipment/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment.serialNumber").value("SN-123"))
                .andExpect(jsonPath("$.customerName").value("Pat Smith"));
        mockMvc.perform(get("/api/equipment/999999").header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_editsEquipment_andCanKeepItsOwnSerial() throws Exception {
        long id = create(oakStreet, CONDENSER);

        send(put("/api/equipment/" + id), CONDENSER.replace("MXZ-3C24NA", "MXZ-4C36NA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment.model").value("MXZ-4C36NA"))
                .andExpect(jsonPath("$.equipment.serialNumber").value("SN-123"));
    }

    @Test
    void update_cannotTakeAnotherUnitsSerial() throws Exception {
        create(oakStreet, CONDENSER);
        long other = create(oakStreet, CONDENSER.replace("SN-123", "SN-999"));

        send(put("/api/equipment/" + other), CONDENSER).andExpect(status().isConflict());
    }

    @Test
    void customerEquipment_listsUnitsAcrossAllProperties() throws Exception {
        create(oakStreet, CONDENSER);
        create(elmStreet, "{\"type\": \"OIL_BURNER\", \"brand\": \"Beckett\"}");

        mockMvc.perform(get("/api/customers/" + smith.getId() + "/equipment").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].propertyId").value(oakStreet))
                .andExpect(jsonPath("$[1].propertyId").value(elmStreet))
                .andExpect(jsonPath("$[1].typeLabel").value("Oil burner"));
    }

    @Test
    void removingAnAddressWithEquipment_isBlocked() throws Exception {
        create(elmStreet, CONDENSER);

        // Customer update that keeps Oak St but drops Elm St, which has equipment
        send(put("/api/customers/" + smith.getId()), """
                {"name": "Pat Smith", "phone": "4135551234",
                 "properties": [{"id": %d, "street": "12 Oak St", "city": "Springfield", "state": "MA", "zip": "01103"}]}
                """.formatted(oakStreet))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("has equipment")));
    }

    @Test
    void removingAnAddressWithoutEquipment_stillWorks() throws Exception {
        create(oakStreet, CONDENSER);

        send(put("/api/customers/" + smith.getId()), """
                {"name": "Pat Smith", "phone": "4135551234",
                 "properties": [{"id": %d, "street": "12 Oak St", "city": "Springfield", "state": "MA", "zip": "01103"}]}
                """.formatted(oakStreet))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.properties", hasSize(1)));
    }

    private long create(long propertyId, String json) throws Exception {
        String body = send(post("/api/properties/" + propertyId + "/equipment"), json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.equipment.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
