package dev.servicelog.ai;

import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.Property;
import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentRepository;
import dev.servicelog.equipment.EquipmentType;
import dev.servicelog.security.JwtService;
import dev.servicelog.servicerecord.ServiceKind;
import dev.servicelog.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The AI endpoints with a fake extractor: nothing here calls a real AI provider
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private EquipmentRepository equipment;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private NoteExtractor extractor;

    private String bearer;
    private Equipment burner;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "x");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());

        Customer smith = new Customer("Pat Smith", "+14135551234", "pat@example.com", null);
        smith.addProperty(new Property("12 Oak St", null, "Springfield", "MA", "01103", null));
        smith = customers.save(smith);
        burner = equipment.save(new Equipment(smith.getProperties().get(0), EquipmentType.OIL_BURNER, "Beckett",
                null, null, null, null));
        when(extractor.extract(anyString(), any())).thenReturn(new NoteExtraction(ServiceKind.TUNE_UP,
                "Changed the nozzle.", List.of("Nozzle"), List.of(new NoteExtraction.Reading("CO", 12, "ppm")),
                false, null));
    }

    @Test
    void draft_returnsTheExtractedFields() throws Exception {
        draft("annual, changed the nozzle, CO 12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("TUNE_UP"))
                .andExpect(jsonPath("$.partsUsed[0]").value("Nozzle"))
                .andExpect(jsonPath("$.readings[0].name").value("CO"))
                .andExpect(jsonPath("$.readings[0].value").value(12.0));
    }

    @Test
    void draft_neverSendsTheCustomersDetailsToTheAi() throws Exception {
        draft("Pat Smith at 12 Oak St, pat@example.com, 413-555-1234: Pat says it's loud. Changed the nozzle.")
                .andExpect(status().isOk());

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(extractor).extract(sent.capture(), eq(EquipmentType.OIL_BURNER));
        String note = sent.getValue();
        for (String privateDetail : List.of("Pat", "Smith", "Oak St", "pat@example.com", "555-1234")) {
            assertFalse(note.contains(privateDetail), "leaked: " + privateDetail);
        }
        assertTrue(note.contains("Changed the nozzle."));
    }

    @Test
    void draft_savesNothing() throws Exception {
        draft("changed the nozzle").andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/equipment/" + burner.getId() + "/service-records").header("Authorization", bearer))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aiFailure_returns503_soTheAppFallsBackToTheManualForm() throws Exception {
        when(extractor.extract(anyString(), any())).thenThrow(new AiUnavailableException("AI is down."));

        draft("changed the nozzle")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    }

    @Test
    void emptyOrHugeNotes_areRejected_withoutCallingTheAi() throws Exception {
        draft(" ").andExpect(status().isBadRequest());
        draft("x".repeat(4001)).andExpect(status().isBadRequest());
        verify(extractor, never()).extract(anyString(), any());
    }

    @Test
    void unknownEquipment_returns404() throws Exception {
        mockMvc.perform(post("/api/equipment/999999/service-records/draft").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void extractEndpoint_forTheEvaluationScript() throws Exception {
        mockMvc.perform(post("/api/ai/extract").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"changed the nozzle\", \"equipmentType\": \"OIL_BURNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDone").value("Changed the nozzle."));
    }

    @Test
    void aiRoutes_requireLogin() throws Exception {
        mockMvc.perform(post("/api/equipment/" + burner.getId() + "/service-records/draft")
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/extract").contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \"x\", \"equipmentType\": \"OIL_BURNER\"}")).andExpect(status().isUnauthorized());
    }

    private ResultActions draft(String note) throws Exception {
        return mockMvc.perform(post("/api/equipment/" + burner.getId() + "/service-records/draft")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \"" + note.replace("\"", "\\\"") + "\"}"));
    }
}
