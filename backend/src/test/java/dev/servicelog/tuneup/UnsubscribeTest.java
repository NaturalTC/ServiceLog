package dev.servicelog.tuneup;

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
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UnsubscribeTest {

    private static final Pattern PAGE_LINK = Pattern.compile("/unsubscribe\\?token=([0-9]+\\.[A-Za-z0-9_-]+)");

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
    private UnsubscribeTokens tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private BusinessCalendar calendar;

    private String bearer;
    private Customer smith;
    private long oak;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        when(calendar.today()).thenReturn(LocalDate.of(2026, 10, 15));
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        AppUser owner = new AppUser("owner@example.com", "x");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());

        smith = new Customer("Pat Smith", "+14135551234", "pat@example.com", null);
        smith.addProperty(new Property("12 Oak St", null, "Springfield", "MA", "01103", null));
        smith = customers.save(smith);
        oak = smith.getProperties().get(0).getId();
        equipment.save(new Equipment(smith.getProperties().get(0), EquipmentType.OIL_BURNER, null, null, null, null,
                null));
    }

    @Test
    void everyReminder_hasAnUnsubscribeLink_andOneClickHeaders() throws Exception {
        MimeMessage email = sendReminder();

        String body = (String) email.getContent();
        assertTrue(body.contains("Unsubscribe: http://localhost:5173/unsubscribe?token="));
        assertTrue(email.getHeader("List-Unsubscribe")[0].contains("/api/unsubscribe?token="));
        assertEquals("List-Unsubscribe=One-Click", email.getHeader("List-Unsubscribe-Post")[0]);
    }

    @Test
    void linkInTheEmail_unsubscribesTheCustomer_withoutLoggingIn() throws Exception {
        String token = tokenFrom(sendReminder());

        unsubscribe(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("unsubscribed")));

        assertNotNull(customers.findById(smith.getId()).orElseThrow().getRemindersOptedOutAt());
    }

    @Test
    void oneClickFromTheMailApp_unsubscribesToo() throws Exception {
        mockMvc.perform(post("/api/unsubscribe").param("token", tokens.tokenFor(smith.getId()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("List-Unsubscribe=One-Click"))
                .andExpect(status().isOk());

        assertNotNull(customers.findById(smith.getId()).orElseThrow().getRemindersOptedOutAt());
    }

    @Test
    void openingTheLink_doesntUnsubscribe() throws Exception {
        // Email scanners "click" every link with GET; only the page's button (a POST) unsubscribes
        mockMvc.perform(get("/api/unsubscribe").param("token", tokens.tokenFor(smith.getId())));

        assertNull(customers.findById(smith.getId()).orElseThrow().getRemindersOptedOutAt());
    }

    @Test
    void forgedOrMalformedTokens_areRejected() throws Exception {
        String valid = tokens.tokenFor(smith.getId());
        String otherCustomer = (smith.getId() + 1) + valid.substring(valid.indexOf('.'));

        unsubscribe(otherCustomer).andExpect(status().isBadRequest());
        unsubscribe("garbage").andExpect(status().isBadRequest());
        unsubscribe("").andExpect(status().isBadRequest());
        assertNull(customers.findById(smith.getId()).orElseThrow().getRemindersOptedOutAt());
    }

    @Test
    void unsubscribingTwice_keepsTheOriginalDate() throws Exception {
        String token = tokens.tokenFor(smith.getId());
        unsubscribe(token).andExpect(status().isOk());
        Instant first = customers.findById(smith.getId()).orElseThrow().getRemindersOptedOutAt();

        unsubscribe(token).andExpect(status().isOk());
        assertEquals(first, customers.findById(smith.getId()).orElseThrow().getRemindersOptedOutAt());
    }

    @Test
    void unsubscribedCustomer_cantBeSentAReminder_andTheDueListSaysSo() throws Exception {
        unsubscribe(tokens.tokenFor(smith.getId())).andExpect(status().isOk());

        sendReminderRequest().andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("unsubscribed")));
        mockMvc.perform(get("/api/tune-ups/due").header("Authorization", bearer))
                .andExpect(jsonPath("$.units[0].remindersOptedOut").value(true));
    }

    @Test
    void ownerCanResumeReminders_whenTheCustomerAsks() throws Exception {
        unsubscribe(tokens.tokenFor(smith.getId())).andExpect(status().isOk());

        mockMvc.perform(post("/api/customers/" + smith.getId() + "/reminders/resume").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remindersOptedOutAt").doesNotExist());
        sendReminderRequest().andExpect(status().isCreated());
    }

    @Test
    void customerResponse_showsWhenTheyUnsubscribed() throws Exception {
        unsubscribe(tokens.tokenFor(smith.getId())).andExpect(status().isOk());

        mockMvc.perform(get("/api/customers/" + smith.getId()).header("Authorization", bearer))
                .andExpect(jsonPath("$.remindersOptedOutAt", notNullValue()));
    }

    private MimeMessage sendReminder() throws Exception {
        sendReminderRequest().andExpect(status().isCreated());
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private ResultActions sendReminderRequest() throws Exception {
        return mockMvc.perform(post("/api/tune-ups/reminders").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": %d, \"propertyId\": %d}".formatted(smith.getId(), oak)));
    }

    private static String tokenFrom(MimeMessage email) throws Exception {
        Matcher matcher = PAGE_LINK.matcher((String) email.getContent());
        assertTrue(matcher.find(), "email should contain the unsubscribe page link");
        return matcher.group(1);
    }

    private ResultActions unsubscribe(String token) throws Exception {
        return mockMvc.perform(post("/api/unsubscribe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"));
    }
}
