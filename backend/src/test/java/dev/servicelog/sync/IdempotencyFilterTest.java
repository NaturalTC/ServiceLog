package dev.servicelog.sync;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sending the same request twice from the offline queue never does the work twice (S21)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IdempotencyFilterTest {

    private static final String RECORD = "{\"kind\": \"REPAIR\", \"workDone\": \"Replaced the igniter\"}";

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
    private long unit;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.reset(jdbc);
        bearer = login("owner@example.com");
        Customer smith = new Customer("Pat Smith", "+14135551234", null, null);
        smith.addProperty(new Property("12 Oak St", null, "Springfield", "MA", "01103", null));
        long property = customers.save(smith).getProperties().get(0).getId();
        String body = mockMvc.perform(post("/api/properties/" + property + "/equipment")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"FURNACE\"}"))
                .andReturn().getResponse().getContentAsString();
        unit = ((Number) JsonPath.read(body, "$.equipment.id")).longValue();
    }

    @Test
    void sameKeyTwice_createsOneRecord_andReturnsTheSameReply() throws Exception {
        String first = addRecord(bearer, "key-0001-aaaa", RECORD)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = addRecord(bearer, "key-0001-aaaa", RECORD)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(records()).isEqualTo(1);
    }

    @Test
    void withoutAKey_requestsRunEachTime() throws Exception {
        addRecord(bearer, null, RECORD).andExpect(status().isCreated());
        addRecord(bearer, null, RECORD).andExpect(status().isCreated());
        assertThat(records()).isEqualTo(2);
    }

    @Test
    void aFailedFirstTry_canBeRetriedWithTheSameKey() throws Exception {
        addRecord(bearer, "key-0002-bbbb", "{\"kind\": \"REPAIR\"}").andExpect(status().isBadRequest());
        addRecord(bearer, "key-0002-bbbb", RECORD).andExpect(status().isCreated());
        assertThat(records()).isEqualTo(1);
    }

    @Test
    void aKeyCantBeReusedForADifferentRequestOrUser() throws Exception {
        addRecord(bearer, "key-0003-cccc", RECORD).andExpect(status().isCreated());

        mockMvc.perform(post("/api/jobs").header("Authorization", bearer).header("Idempotency-Key", "key-0003-cccc")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent());

        // Another user never gets the stored reply
        addRecord(login("someone@example.com"), "key-0003-cccc", RECORD)
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
        assertThat(records()).isEqualTo(1);
    }

    @Test
    void whileTheFirstIsStillRunning_aRepeatIsAskedToRetry() throws Exception {
        jdbc.update("insert into idempotent_request (idempotency_key, username, method, path, status) values (?, ?, ?, ?, 0)",
                "key-0004-dddd", "owner@example.com", "POST", "/api/equipment/" + unit + "/service-records");

        addRecord(bearer, "key-0004-dddd", RECORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_PROGRESS"));
        assertThat(records()).isZero();
    }

    @Test
    void notLoggedIn_getsNoStoredReply() throws Exception {
        addRecord(bearer, "key-0005-eeee", RECORD).andExpect(status().isCreated());
        addRecord(null, "key-0005-eeee", RECORD)
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    @Test
    void malformedKey_isRejected() throws Exception {
        addRecord(bearer, "no spaces/allowed", RECORD).andExpect(status().isBadRequest());
        assertThat(records()).isZero();
    }

    @Test
    void photoUploads_areProtectedToo() throws Exception {
        String body = addRecord(bearer, null, RECORD).andReturn().getResponse().getContentAsString();
        long record = ((Number) JsonPath.read(body, "$.record.id")).longValue();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(multipart("/api/service-records/" + record + "/photos").file(photo())
                            .header("Authorization", bearer).header("Idempotency-Key", "key-0006-ffff"))
                    .andExpect(status().isCreated());
        }
        assertThat(jdbc.queryForObject("select count(*) from service_record_photo", Integer.class)).isEqualTo(1);
    }

    @Test
    void storedRepliesExpireAfterAWeek() throws Exception {
        jdbc.update("""
                insert into idempotent_request (idempotency_key, username, method, path, status, created_at)
                values ('key-0007-old', 'owner@example.com', 'POST', '/x', 201, now() - interval '8 days')""");
        addRecord(bearer, "key-0008-new", RECORD).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from idempotent_request where idempotency_key = 'key-0007-old'",
                Integer.class)).isZero();
    }

    private ResultActions addRecord(String auth, String key, String json) throws Exception {
        var request = post("/api/equipment/" + unit + "/service-records")
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (auth != null) {
            request.header("Authorization", auth);
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private int records() {
        return jdbc.queryForObject("select count(*) from service_record", Integer.class);
    }

    private String login(String email) {
        AppUser user = new AppUser(email, "not-used-in-these-tests");
        user.markEmailVerified();
        users.save(user);
        return "Bearer " + jwtService.generateToken(user.getEmail(), user.getSessionVersion());
    }

    private static MockMultipartFile photo() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "jpeg", out);
        return new MockMultipartFile("files", "p.jpg", "image/jpeg", out.toByteArray());
    }
}
