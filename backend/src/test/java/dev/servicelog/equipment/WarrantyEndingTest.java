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

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Warranties ending soon (S25)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WarrantyEndingTest {

    // The business's "today" (app.time-zone), which decides what's ending soon
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/New_York"));

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
    private long property;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());

        Customer smith = new Customer("Pat Smith", "+14135551234", "pat@example.com", null);
        smith.addProperty(new Property("12 Oak St", null, "Springfield", "MA", "01103", null));
        property = customers.save(smith).getProperties().get(0).getId();
    }

    @Test
    void listsUnitsWhoseCoverEndsSoon_soonestFirst_withWhatToOffer() throws Exception {
        long extendedEnding = unit("HEAT_PUMP", TODAY.minusYears(5), TODAY.plusDays(60));
        long warrantyEnding = unit("FURNACE", TODAY.plusDays(30), null);

        mockMvc.perform(get("/api/equipment/warranty-ending").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowDays").value(90))
                .andExpect(jsonPath("$.units", hasSize(2)))
                .andExpect(jsonPath("$.units[0].equipmentId").value(warrantyEnding))
                .andExpect(jsonPath("$.units[0].offer").value("EXTENDED_WARRANTY"))
                .andExpect(jsonPath("$.units[0].endsOn").value(TODAY.plusDays(30).toString()))
                .andExpect(jsonPath("$.units[0].daysLeft").value(30))
                .andExpect(jsonPath("$.units[0].customerName").value("Pat Smith"))
                .andExpect(jsonPath("$.units[0].customerEmail").value("pat@example.com"))
                .andExpect(jsonPath("$.units[0].address").value("12 Oak St, Springfield, MA 01103"))
                .andExpect(jsonPath("$.units[1].equipmentId").value(extendedEnding))
                .andExpect(jsonPath("$.units[1].offer").value("RENEWAL"));
    }

    @Test
    void ignoresUnitsStillCoveredOrAlreadyEnded() throws Exception {
        unit("FURNACE", TODAY.plusDays(10), TODAY.plusYears(5)); // the extended warranty still covers it
        unit("BOILER", TODAY.plusDays(91), null); // beyond the window
        unit("CENTRAL_AC", TODAY.minusDays(1), null); // already ended
        unit("OTHER", null, null); // no dates recorded
        long lastDay = unit("WATER_HEATER", TODAY, null); // today is still covered

        mockMvc.perform(get("/api/equipment/warranty-ending").header("Authorization", bearer))
                .andExpect(jsonPath("$.units", hasSize(1)))
                .andExpect(jsonPath("$.units[0].equipmentId").value(lastDay))
                .andExpect(jsonPath("$.units[0].daysLeft").value(0));
    }

    @Test
    void markingOffered_movesItDown_andCanBeUndone() throws Exception {
        long soonest = unit("FURNACE", TODAY.plusDays(5), null);
        long later = unit("BOILER", TODAY.plusDays(40), null);

        mockMvc.perform(put("/api/equipment/" + soonest + "/warranty-offer").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/equipment/warranty-ending").header("Authorization", bearer))
                .andExpect(jsonPath("$.units[0].equipmentId").value(later))
                .andExpect(jsonPath("$.units[0].offeredOn").value(nullValue()))
                .andExpect(jsonPath("$.units[1].equipmentId").value(soonest))
                .andExpect(jsonPath("$.units[1].offeredOn").value(TODAY.toString()));

        mockMvc.perform(delete("/api/equipment/" + soonest + "/warranty-offer").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/equipment/warranty-ending").header("Authorization", bearer))
                .andExpect(jsonPath("$.units[0].equipmentId").value(soonest))
                .andExpect(jsonPath("$.units[0].offeredOn").value(nullValue()));
    }

    @Test
    void newEndDates_clearTheOffer() throws Exception {
        long id = unit("FURNACE", TODAY.plusDays(20), null);
        mockMvc.perform(put("/api/equipment/" + id + "/warranty-offer").header("Authorization", bearer));

        // Saving the unit unchanged keeps the offer...
        save(id, TODAY.plusDays(20), null);
        mockMvc.perform(get("/api/equipment/warranty-ending").header("Authorization", bearer))
                .andExpect(jsonPath("$.units[0].offeredOn").value(TODAY.toString()));

        // ...a renewal (new end date) clears it
        save(id, TODAY.plusDays(20), TODAY.plusDays(80));
        mockMvc.perform(get("/api/equipment/warranty-ending").header("Authorization", bearer))
                .andExpect(jsonPath("$.units[0].offer").value("RENEWAL"))
                .andExpect(jsonPath("$.units[0].offeredOn").value(nullValue()));
    }

    @Test
    void offerOnUnknownUnit_returns404() throws Exception {
        mockMvc.perform(put("/api/equipment/999999/warranty-offer").header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/equipment/warranty-ending")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/equipment/1/warranty-offer")).andExpect(status().isUnauthorized());
    }

    private long unit(String type, LocalDate endsOn, LocalDate extendedEndsOn) throws Exception {
        String body = mockMvc.perform(post("/api/properties/" + property + "/equipment")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(type, endsOn, extendedEndsOn)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.equipment.id")).longValue();
    }

    private void save(long id, LocalDate endsOn, LocalDate extendedEndsOn) throws Exception {
        mockMvc.perform(put("/api/equipment/" + id)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("FURNACE", endsOn, extendedEndsOn)))
                .andExpect(status().isOk());
    }

    private static String json(String type, LocalDate endsOn, LocalDate extendedEndsOn) {
        return """
                {"type": "%s", "installDate": "2015-01-01", "warrantyEndsOn": %s, "extendedWarrantyEndsOn": %s}
                """.formatted(type, quoted(endsOn), quoted(extendedEndsOn));
    }

    private static String quoted(LocalDate date) {
        return date == null ? "null" : "\"" + date + "\"";
    }
}
