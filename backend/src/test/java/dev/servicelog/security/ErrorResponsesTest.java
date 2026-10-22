package dev.servicelog.security;

import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
import dev.servicelog.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Through a real server, because MockMvc skips Spring's error page, which is where the bug
// was: a logged-in request to an unknown path came back 401, and the app logs out on 401.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorResponsesTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    private final HttpClient http = HttpClient.newHttpClient();
    private String token;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        token = jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());
    }

    @Test
    void anUnknownPath_whenLoggedIn_is404_not401() throws Exception {
        assertThat(send("POST", "/api/no-such-thing", token).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/customers/999999", token).statusCode()).isEqualTo(404);
    }

    @Test
    void anUnknownPath_withoutLogin_isStill401() throws Exception {
        assertThat(send("POST", "/api/no-such-thing", null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> send(String method, String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, method.equals("GET") ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json");
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
