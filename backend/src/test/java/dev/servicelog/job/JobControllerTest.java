package dev.servicelog.job;

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
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class JobControllerTest {

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
        smith.addProperty(new Property("5 Elm St", null, "Holyoke", "MA", "01040", null));
        smith = customers.save(smith);
        oakStreet = smith.getProperties().get(0).getId();
        elmStreet = smith.getProperties().get(1).getId();
    }

    @Test
    void routes_requireLogin() throws Exception {
        mockMvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                .content(job(oakStreet, "2030-01-15", "09:00", null))).andExpect(status().isUnauthorized());
    }

    @Test
    void create_schedulesAJobWithCustomerAndAddress() throws Exception {
        send(post("/api/jobs"), """
                {"propertyId": %d, "date": "2030-01-15", "startTime": "09:30", "durationMinutes": 90,
                 "type": "SERVICE_CALL", "urgency": "URGENT", "notes": " No heat "}
                """.formatted(oakStreet))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.date").value("2030-01-15"))
                .andExpect(jsonPath("$.startTime").value("09:30:00"))
                .andExpect(jsonPath("$.durationMinutes").value(90))
                .andExpect(jsonPath("$.typeLabel").value("Service call"))
                .andExpect(jsonPath("$.urgency").value("URGENT"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.notes").value("No heat"))
                .andExpect(jsonPath("$.address").value("12 Oak St, Springfield, MA 01103"))
                .andExpect(jsonPath("$.customerId").value(smith.getId()))
                .andExpect(jsonPath("$.customerName").value("Pat Smith"))
                .andExpect(jsonPath("$.customerPhone").value("+14135551234"));
    }

    @Test
    void create_defaultsToOneHourAndNormalUrgency() throws Exception {
        send(post("/api/jobs"), job(oakStreet, "2030-01-15", "09:00", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.urgency").value("NORMAL"));
    }

    @Test
    void create_requiresPropertyDateTimeAndType() throws Exception {
        send(post("/api/jobs"), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("propertyId")))
                .andExpect(jsonPath("$.message").value(containsString("date")))
                .andExpect(jsonPath("$.message").value(containsString("startTime")))
                .andExpect(jsonPath("$.message").value(containsString("type")));
    }

    @Test
    void create_rejectsDurationsOutside15MinutesTo12Hours() throws Exception {
        send(post("/api/jobs"), job(oakStreet, "2030-01-15", "09:00", 5)).andExpect(status().isBadRequest());
        send(post("/api/jobs"), job(oakStreet, "2030-01-15", "09:00", 900)).andExpect(status().isBadRequest());
    }

    @Test
    void create_atUnknownProperty_returns404() throws Exception {
        send(post("/api/jobs"), job(999999, "2030-01-15", "09:00", null)).andExpect(status().isNotFound());
    }

    @Test
    void overlappingJob_isRejectedWithTheConflict() throws Exception {
        long existing = create(job(oakStreet, "2030-01-15", "09:00", 120));

        send(post("/api/jobs"), job(elmStreet, "2030-01-15", "10:30", 60))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_OVERLAP"))
                .andExpect(jsonPath("$.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].id").value(existing));
    }

    @Test
    void overlappingJob_canBeBookedAnywayWhenConfirmed() throws Exception {
        create(job(oakStreet, "2030-01-15", "09:00", 120));

        send(post("/api/jobs"), """
                {"propertyId": %d, "date": "2030-01-15", "startTime": "10:00", "type": "TUNE_UP",
                 "allowOverlap": true}
                """.formatted(elmStreet))
                .andExpect(status().isCreated());
    }

    @Test
    void backToBackJobs_dontOverlap() throws Exception {
        create(job(oakStreet, "2030-01-15", "09:00", 60));

        send(post("/api/jobs"), job(elmStreet, "2030-01-15", "10:00", 60)).andExpect(status().isCreated());
    }

    @Test
    void jobRunningPastMidnight_overlapsTheNextMorning() throws Exception {
        create(job(oakStreet, "2030-01-15", "23:00", 120)); // until 01:00 on the 16th

        send(post("/api/jobs"), job(elmStreet, "2030-01-16", "00:30", 30)).andExpect(status().isConflict());
    }

    @Test
    void cancelledJobs_dontBlockTheirTimeSlot() throws Exception {
        long existing = create(job(oakStreet, "2030-01-15", "09:00", 60));
        send(post("/api/jobs/" + existing + "/cancel"), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        send(post("/api/jobs"), job(elmStreet, "2030-01-15", "09:00", 60)).andExpect(status().isCreated());
    }

    @Test
    void reschedule_movesTheJob() throws Exception {
        long id = create(job(oakStreet, "2030-01-15", "09:00", 60));

        send(put("/api/jobs/" + id), job(oakStreet, "2030-01-17", "13:00", 60))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2030-01-17"))
                .andExpect(jsonPath("$.startTime").value("13:00:00"));
    }

    @Test
    void reschedule_doesntConflictWithItself_butDoesWithOthers() throws Exception {
        long id = create(job(oakStreet, "2030-01-15", "09:00", 60));
        create(job(elmStreet, "2030-01-15", "14:00", 60));

        // Shifting by 30 minutes overlaps its own old slot: fine
        send(put("/api/jobs/" + id), job(oakStreet, "2030-01-15", "09:30", 60)).andExpect(status().isOk());
        // Moving onto the other job: conflict
        send(put("/api/jobs/" + id), job(oakStreet, "2030-01-15", "14:30", 60)).andExpect(status().isConflict());
    }

    @Test
    void cancelledJob_cantBeRescheduled() throws Exception {
        long id = create(job(oakStreet, "2030-01-15", "09:00", 60));
        send(post("/api/jobs/" + id + "/cancel"), "").andExpect(status().isOk());

        send(put("/api/jobs/" + id), job(oakStreet, "2030-01-16", "09:00", 60)).andExpect(status().isConflict());
    }

    @Test
    void list_returnsScheduledJobsInRange_inTimeOrder() throws Exception {
        long later = create(job(oakStreet, "2030-01-15", "14:00", 60));
        long earlier = create(job(elmStreet, "2030-01-15", "08:00", 60));
        long nextDay = create(job(oakStreet, "2030-01-16", "07:00", 60));
        create(job(oakStreet, "2030-02-20", "09:00", 60)); // outside the range
        long cancelled = create(job(elmStreet, "2030-01-15", "11:00", 60));
        send(post("/api/jobs/" + cancelled + "/cancel"), "").andExpect(status().isOk());

        mockMvc.perform(get("/api/jobs").param("from", "2030-01-15").param("to", "2030-01-16")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(earlier))
                .andExpect(jsonPath("$[1].id").value(later))
                .andExpect(jsonPath("$[2].id").value(nextDay));
    }

    @Test
    void list_defaultsToTheNextSevenDays() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        long soon = create(job(oakStreet, today.plusDays(2).toString(), "09:00", 60));
        create(job(oakStreet, today.plusDays(10).toString(), "09:00", 60));

        mockMvc.perform(get("/api/jobs").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(soon));
    }

    @Test
    void list_rejectsBackwardsOrHugeRanges() throws Exception {
        mockMvc.perform(get("/api/jobs").param("from", "2030-01-15").param("to", "2030-01-10")
                .header("Authorization", bearer)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/jobs").param("from", "2030-01-01").param("to", "2031-01-01")
                .header("Authorization", bearer)).andExpect(status().isBadRequest());
    }

    @Test
    void customerJobs_listsUpcomingJobsAtAnyOfTheirAddresses() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        create(job(oakStreet, today.plusDays(1).toString(), "09:00", 60));
        create(job(elmStreet, today.plusDays(30).toString(), "09:00", 60));
        create(job(oakStreet, today.minusDays(3).toString(), "09:00", 60)); // past

        mockMvc.perform(get("/api/customers/" + smith.getId() + "/jobs").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void get_unknownJob_returns404() throws Exception {
        mockMvc.perform(get("/api/jobs/999999").header("Authorization", bearer)).andExpect(status().isNotFound());
    }

    @Test
    void removingAnAddressWithJobs_isBlocked() throws Exception {
        create(job(elmStreet, "2030-01-15", "09:00", 60));

        send(put("/api/customers/" + smith.getId()), """
                {"name": "Pat Smith", "phone": "4135551234",
                 "properties": [{"id": %d, "street": "12 Oak St", "city": "Springfield", "state": "MA", "zip": "01103"}]}
                """.formatted(oakStreet))
                .andExpect(status().isConflict());
    }

    private static String job(long propertyId, String date, String time, Integer duration) {
        return """
                {"propertyId": %d, "date": "%s", "startTime": "%s", "durationMinutes": %s, "type": "TUNE_UP"}
                """.formatted(propertyId, date, time, duration == null ? "null" : duration);
    }

    private long create(String json) throws Exception {
        String body = send(post("/api/jobs"), json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
