package dev.servicelog.auth;

import dev.servicelog.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// End-to-end with the real limit of 5. Its own Spring context (fresh limiter per test
// via DirtiesContext), so other tests' deliberate failures don't count here.
@SpringBootTest(properties = "app.login.max-failures=5")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LoginRateLimitTest {

    private static final String EMAIL = "owner@example.com";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser(EMAIL, passwordEncoder.encode(PASSWORD));
        owner.markEmailVerified();
        users.save(owner);
    }

    @Test
    void afterFiveFailures_evenTheRightPasswordGets429() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong", "10.0.0.1").andExpect(status().isUnauthorized());
        }

        login(EMAIL, PASSWORD, "10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.message").value(containsString("Try again in 15 minutes")))
                // 15 minutes, give or take the second the test itself takes
                .andExpect(header().string("Retry-After", matchesPattern("^(899|900)$")));
    }

    @Test
    void theEmailIsBlockedFromAnyAddress() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong", "10.0.0." + i).andExpect(status().isUnauthorized());
        }

        login(EMAIL, PASSWORD, "10.0.0.99").andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownEmail_getsTheSame429_soAccountsCantBeDiscovered() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("nobody@example.com", "wrong", "10.0.0.1").andExpect(status().isUnauthorized());
        }

        login("nobody@example.com", "wrong", "10.0.0.2")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(containsString("Too many login attempts")));
    }

    @Test
    void successfulLogin_resetsTheCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(EMAIL, "wrong", "10.0.0.1").andExpect(status().isUnauthorized());
        }
        login(EMAIL, PASSWORD, "10.0.0.1").andExpect(status().isOk());

        login(EMAIL, "wrong", "10.0.0.2").andExpect(status().isUnauthorized());
        login(EMAIL, PASSWORD, "10.0.0.2").andExpect(status().isOk());
    }

    private ResultActions login(String email, String password, String address) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
