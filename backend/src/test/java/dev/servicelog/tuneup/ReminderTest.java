package dev.servicelog.tuneup;

import com.jayway.jsonpath.JsonPath;
import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
import dev.servicelog.config.BusinessCalendar;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"servicelog.business.name=Valley HVAC", "servicelog.business.phone=(413) 555-0000"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReminderTest {

    private static final LocalDate FALL = LocalDate.of(2026, 10, 15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private EquipmentRepository equipment;

    @Autowired
    private TuneUpReminderRepository reminders;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    // Pin "today" to mid-October so heating equipment is in season whenever the tests run
    @MockitoBean
    private BusinessCalendar calendar;

    private String bearer;
    private Customer smith;
    private Property oak;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        when(calendar.today()).thenReturn(FALL);
        AppUser owner = new AppUser("owner@example.com", "x");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());

        smith = new Customer("Pat Smith", "+14135551234", "pat@example.com", null);
        smith.addProperty(new Property("12 Oak St", null, "Springfield", "MA", "01103", null));
        smith.addProperty(new Property("5 Elm St", null, "Holyoke", "MA", "01040", null));
        smith = customers.save(smith);
        oak = smith.getProperties().get(0);
        equipment.save(new Equipment(oak, EquipmentType.OIL_BURNER, "Beckett", null, null, null, null));
        equipment.save(new Equipment(oak, EquipmentType.BOILER, "Burnham", null, null, null, null));
    }

    @Test
    void send_emailsTheCustomer_fromTheTemplate_andLogsIt() throws Exception {
        send(oak.getId(), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.recipientEmail").value("pat@example.com"))
                .andExpect(jsonPath("$.subject").value("Time for your fall tune-up"))
                .andExpect(jsonPath("$.sentAt", notNullValue()));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage email = captor.getValue();
        assertEquals("pat@example.com", email.getTo()[0]);
        assertEquals("owner@example.com", email.getReplyTo()); // replies reach the owner
        assertEquals("Time for your fall tune-up", email.getSubject());
        String body = email.getText();
        assertTrue(body.startsWith("Hi Pat,"));
        assertTrue(body.contains("12 Oak St, Springfield, MA 01103"));
        assertTrue(body.contains("Oil burner (Beckett)"));
        assertTrue(body.contains("Boiler (Burnham)"));
        assertTrue(body.contains("or call (413) 555-0000 to pick a time"));
        assertTrue(body.trim().endsWith("Valley HVAC"));
        assertEquals(1, reminders.count());
    }

    @Test
    void customerWithoutEmail_cantBeEmailed() throws Exception {
        Customer noEmail = new Customer("Sam Lee", "+14135550000", null, null);
        noEmail.addProperty(new Property("1 Maple Rd", null, "Agawam", "MA", "01001", null));
        noEmail = customers.save(noEmail);
        equipment.save(new Equipment(noEmail.getProperties().get(0), EquipmentType.FURNACE, null, null, null, null, null));

        mockMvc.perform(post("/api/tune-ups/reminders").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": %d, \"propertyId\": %d}".formatted(
                                noEmail.getId(), noEmail.getProperties().get(0).getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("no email on file")));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void addressWithNothingDue_isRefused() throws Exception {
        long elm = smith.getProperties().get(1).getId(); // no equipment there

        send(elm, false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Nothing at this address is due")));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void anotherCustomersAddress_isRejected() throws Exception {
        Customer other = new Customer("Alex Adams", "+14135559876", "alex@example.com", null);
        other.addProperty(new Property("9 Birch Ln", null, "Ludlow", "MA", "01056", null));
        other = customers.save(other);

        send(other.getProperties().get(0).getId(), false).andExpect(status().isBadRequest());
    }

    @Test
    void secondReminderWithinTwoWeeks_needsConfirmation() throws Exception {
        send(oak.getId(), false).andExpect(status().isCreated());

        send(oak.getId(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMINDER_RECENTLY_SENT"))
                .andExpect(jsonPath("$.lastSentAt", notNullValue()));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));

        send(oak.getId(), true).andExpect(status().isCreated());
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void failedSend_isLogged_andReported() throws Exception {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        send(oak.getId(), false)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EMAIL_FAILED"));

        TuneUpReminder logged = reminders.findAll().get(0);
        assertEquals(TuneUpReminder.Status.FAILED, logged.getStatus());
        assertTrue(logged.getError().contains("SMTP down"));
    }

    @Test
    void failedSend_doesntCountAsRecent_soTheOwnerCanRetry() throws Exception {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        send(oak.getId(), false).andExpect(status().isBadGateway());

        org.mockito.Mockito.reset(mailSender);
        send(oak.getId(), false).andExpect(status().isCreated());
    }

    @Test
    void history_listsTheCustomersReminders_newestFirst() throws Exception {
        send(oak.getId(), false).andExpect(status().isCreated());
        String second = send(oak.getId(), true).andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/customers/" + smith.getId() + "/reminders").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(((Number) JsonPath.read(second, "$.id")).longValue()));
    }

    @Test
    void dueList_showsWhenTheAddressWasLastReminded() throws Exception {
        send(oak.getId(), false).andExpect(status().isCreated());

        mockMvc.perform(get("/api/tune-ups/due").header("Authorization", bearer))
                .andExpect(jsonPath("$.units[0].lastReminderAt", notNullValue()));
    }

    @Test
    void routes_requireLogin() throws Exception {
        mockMvc.perform(post("/api/tune-ups/reminders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": 1, \"propertyId\": 1}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/customers/1/reminders")).andExpect(status().isUnauthorized());
    }

    private ResultActions send(long propertyId, boolean resend) throws Exception {
        return mockMvc.perform(post("/api/tune-ups/reminders").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": %d, \"propertyId\": %d, \"resend\": %s}".formatted(
                        smith.getId(), propertyId, resend)));
    }
}
