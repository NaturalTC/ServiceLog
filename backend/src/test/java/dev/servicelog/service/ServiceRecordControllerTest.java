package dev.servicelog.service;

import com.jayway.jsonpath.JsonPath;
import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.Property;
import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentRepository;
import dev.servicelog.equipment.EquipmentType;
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
import java.time.ZoneId;

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
class ServiceRecordControllerTest {

    private static final String TUNE_UP = """
            {"serviceDate": "2026-10-01", "kind": "TUNE_UP",
             "workDone": "  Cleaned nozzle and electrodes, replaced filter, checked combustion ",
             "partsUsed": "Nozzle 0.75 80A, RF1 filter", "notes": "", "followUpNeeded": false}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    private String bearer;
    private Equipment burner;
    private Customer smith;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());

        smith = new Customer("Pat Smith", "+14135551234", null, null);
        smith.addProperty(new Property("12 Oak St", null, "Springfield", "MA", "01103", null));
        smith = customers.save(smith);
        burner = equipmentRepository.save(new Equipment(smith.getProperties().get(0), EquipmentType.OIL_BURNER,
                "Beckett", "AFG", "SN-1", null, null));
    }

    @Test
    void routes_requireLogin() throws Exception {
        mockMvc.perform(get("/api/service-kinds")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/equipment/" + burner.getId() + "/service-records")
                        .contentType(MediaType.APPLICATION_JSON).content(TUNE_UP))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kinds_areListedWithLabels() throws Exception {
        mockMvc.perform(get("/api/service-kinds").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(ServiceKind.values().length)))
                .andExpect(jsonPath("$[0].label").value("Tune-up"));
    }

    @Test
    void create_recordsServiceOnEquipment() throws Exception {
        send(post("/api/equipment/" + burner.getId() + "/service-records"), TUNE_UP)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.record.id").isNumber())
                .andExpect(jsonPath("$.record.equipmentId").value(burner.getId()))
                .andExpect(jsonPath("$.record.serviceDate").value("2026-10-01"))
                .andExpect(jsonPath("$.record.kind").value("TUNE_UP"))
                .andExpect(jsonPath("$.record.kindLabel").value("Tune-up"))
                .andExpect(jsonPath("$.record.workDone").value("Cleaned nozzle and electrodes, replaced filter, checked combustion"))
                .andExpect(jsonPath("$.record.partsUsed").value("Nozzle 0.75 80A, RF1 filter"))
                .andExpect(jsonPath("$.record.notes").value(nullValue()))
                .andExpect(jsonPath("$.record.followUpNeeded").value(false))
                .andExpect(jsonPath("$.equipmentLabel").value("Oil burner · Beckett"))
                .andExpect(jsonPath("$.customerName").value("Pat Smith"))
                .andExpect(jsonPath("$.address").value("12 Oak St, Springfield, MA 01103"));
    }

    @Test
    void date_defaultsToTodayInTheBusinessTimeZone() throws Exception {
        String today = LocalDate.now(ZoneId.of("America/New_York")).toString();

        send(post("/api/equipment/" + burner.getId() + "/service-records"),
                "{\"kind\": \"REPAIR\", \"workDone\": \"Replaced igniter\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.record.serviceDate").value(today));
    }

    @Test
    void workDoneAndKind_areRequired() throws Exception {
        send(post("/api/equipment/" + burner.getId() + "/service-records"), "{\"workDone\": \"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("workDone")))
                .andExpect(jsonPath("$.message").value(containsString("kind")));
    }

    @Test
    void futureDate_isRejected() throws Exception {
        String nextWeek = LocalDate.now(ZoneId.of("America/New_York")).plusDays(7).toString();

        send(post("/api/equipment/" + burner.getId() + "/service-records"),
                "{\"serviceDate\": \"" + nextWeek + "\", \"kind\": \"REPAIR\", \"workDone\": \"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("future")));
    }

    @Test
    void followUpNote_isKeptOnlyWhenFollowUpIsNeeded() throws Exception {
        send(post("/api/equipment/" + burner.getId() + "/service-records"), """
                {"kind": "REPAIR", "workDone": "Patched leak", "followUpNeeded": true,
                 "followUpNote": "Quote a new heat exchanger"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.record.followUpNeeded").value(true))
                .andExpect(jsonPath("$.record.followUpNote").value("Quote a new heat exchanger"));

        send(post("/api/equipment/" + burner.getId() + "/service-records"), """
                {"kind": "REPAIR", "workDone": "Patched leak", "followUpNeeded": false,
                 "followUpNote": "Stale note left in the form"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.record.followUpNote").value(nullValue()));
    }

    @Test
    void create_onUnknownEquipment_returns404() throws Exception {
        send(post("/api/equipment/999999/service-records"), TUNE_UP).andExpect(status().isNotFound());
    }

    @Test
    void get_returnsRecord_or404() throws Exception {
        long id = create(TUNE_UP);

        mockMvc.perform(get("/api/service-records/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record.kind").value("TUNE_UP"));
        mockMvc.perform(get("/api/service-records/999999").header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_editsTheRecord() throws Exception {
        long id = create(TUNE_UP);

        send(put("/api/service-records/" + id), """
                {"serviceDate": "2026-10-02", "kind": "REPAIR", "workDone": "Replaced pump coupling",
                 "followUpNeeded": true, "followUpNote": "Check again next week"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record.serviceDate").value("2026-10-02"))
                .andExpect(jsonPath("$.record.kind").value("REPAIR"))
                .andExpect(jsonPath("$.record.partsUsed").value(nullValue()))
                .andExpect(jsonPath("$.record.followUpNote").value("Check again next week"));
    }

    @Test
    void update_unknownRecord_returns404() throws Exception {
        send(put("/api/service-records/999999"), TUNE_UP).andExpect(status().isNotFound());
    }

    private long create(String json) throws Exception {
        String body = send(post("/api/equipment/" + burner.getId() + "/service-records"), json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.record.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
