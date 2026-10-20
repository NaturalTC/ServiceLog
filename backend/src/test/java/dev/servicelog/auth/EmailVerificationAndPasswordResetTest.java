package dev.servicelog.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// End-to-end: capture the email the app would send, then "click" the link in it
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EmailVerificationAndPasswordResetTest {

    private static final String EMAIL = "owner@example.com";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String NEW_PASSWORD = "a-brand-new-password";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private AuthTokenRepository authTokens;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void cleanStart() {
        users.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        users.deleteAll();
    }

    // --- Email verification ---

    @Test
    void unverifiedOwner_cannotLogIn() throws Exception {
        createOwner(false);

        login(EMAIL, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void verificationLink_verifiesEmail_andAllowsLogin() throws Exception {
        AppUser owner = createOwner(false);
        authService.sendVerificationEmail(owner);
        String token = tokenFromLatestEmail("Verify your ServiceLog email");

        postJson("/api/auth/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isOk());

        login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void verificationLink_worksOnlyOnce() throws Exception {
        AppUser owner = createOwner(false);
        authService.sendVerificationEmail(owner);
        String token = tokenFromLatestEmail("Verify your ServiceLog email");

        postJson("/api/auth/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isOk());
        postJson("/api/auth/verify-email", "{\"token\":\"" + token + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredVerificationLink_isRejected() throws Exception {
        AppUser owner = createOwner(false);
        // Issue a token that expired one second ago
        String token = authTokenService.issue(owner, AuthToken.Purpose.VERIFY_EMAIL, Duration.ofSeconds(-1));

        postJson("/api/auth/verify-email", "{\"token\":\"" + token + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void resendingVerification_invalidatesTheOldLink() throws Exception {
        AppUser owner = createOwner(false);
        authService.sendVerificationEmail(owner);
        String oldToken = tokenFromLatestEmail("Verify your ServiceLog email");
        clearInvocations(mailSender);

        postJson("/api/auth/resend-verification", "{\"email\":\"" + EMAIL + "\"}").andExpect(status().isAccepted());
        String newToken = tokenFromLatestEmail("Verify your ServiceLog email");

        postJson("/api/auth/verify-email", "{\"token\":\"" + oldToken + "\"}").andExpect(status().isBadRequest());
        postJson("/api/auth/verify-email", "{\"token\":\"" + newToken + "\"}").andExpect(status().isOk());
    }

    @Test
    void resendForUnknownEmail_looksTheSame_andSendsNothing() throws Exception {
        createOwner(false);

        postJson("/api/auth/resend-verification", "{\"email\":\"nobody@example.com\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If that email has an account, we've sent a link. Check your inbox."));

        verify(mailSender, after(300).never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void tokensAreStoredOnlyAsHashes() throws Exception {
        AppUser owner = createOwner(false);
        authService.sendVerificationEmail(owner);
        String token = tokenFromLatestEmail("Verify your ServiceLog email");

        assertTrue(authTokens.findByTokenHashAndPurpose(token, AuthToken.Purpose.VERIFY_EMAIL).isEmpty());
        assertTrue(authTokens.findByTokenHashAndPurpose(AuthTokenService.hash(token), AuthToken.Purpose.VERIFY_EMAIL).isPresent());
    }

    // --- Forgot / reset password ---

    @Test
    void passwordReset_changesPassword() throws Exception {
        createOwner(true);
        String token = requestResetAndGetToken();

        postJson("/api/auth/reset-password", resetBody(token, NEW_PASSWORD)).andExpect(status().isOk());

        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordReset_logsOutExistingSessions() throws Exception {
        createOwner(true);
        String oldJwt = loginAndGetJwt(PASSWORD);
        String token = requestResetAndGetToken();

        postJson("/api/auth/reset-password", resetBody(token, NEW_PASSWORD)).andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldJwt))
                .andExpect(status().isUnauthorized());
        String newJwt = loginAndGetJwt(NEW_PASSWORD);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newJwt))
                .andExpect(status().isOk());
    }

    @Test
    void resetLink_worksOnlyOnce() throws Exception {
        createOwner(true);
        String token = requestResetAndGetToken();

        postJson("/api/auth/reset-password", resetBody(token, NEW_PASSWORD)).andExpect(status().isOk());
        postJson("/api/auth/reset-password", resetBody(token, "yet-another-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredResetLink_isRejected() throws Exception {
        AppUser owner = createOwner(true);
        String token = authTokenService.issue(owner, AuthToken.Purpose.RESET_PASSWORD, Duration.ofSeconds(-1));

        postJson("/api/auth/reset-password", resetBody(token, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void verificationToken_cannotBeUsedToResetPassword() throws Exception {
        AppUser owner = createOwner(false);
        authService.sendVerificationEmail(owner);
        String verifyToken = tokenFromLatestEmail("Verify your ServiceLog email");

        postJson("/api/auth/reset-password", resetBody(verifyToken, NEW_PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shortNewPassword_isRejected() throws Exception {
        createOwner(true);
        String token = requestResetAndGetToken();

        postJson("/api/auth/reset-password", resetBody(token, "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void forgotPassword_forUnknownEmail_looksTheSame_andSendsNothing() throws Exception {
        createOwner(true);

        postJson("/api/auth/forgot-password", "{\"email\":\"nobody@example.com\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If that email has an account, we've sent a link. Check your inbox."));

        verify(mailSender, after(300).never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void forgotPassword_forUnverifiedEmail_sendsNothing() throws Exception {
        createOwner(false);

        postJson("/api/auth/forgot-password", "{\"email\":\"" + EMAIL + "\"}").andExpect(status().isAccepted());

        verify(mailSender, after(300).never()).send(any(SimpleMailMessage.class));
    }

    // --- helpers ---

    private AppUser createOwner(boolean verified) {
        AppUser owner = new AppUser(EMAIL, passwordEncoder.encode(PASSWORD));
        if (verified) {
            owner.markEmailVerified();
        }
        return users.save(owner);
    }

    private String requestResetAndGetToken() throws Exception {
        postJson("/api/auth/forgot-password", "{\"email\":\"" + EMAIL + "\"}").andExpect(status().isAccepted());
        return tokenFromLatestEmail("Reset your ServiceLog password");
    }

    // Emails are sent in the background, so wait briefly for the send
    private String tokenFromLatestEmail(String expectedSubject) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000).atLeastOnce()).send(captor.capture());
        SimpleMailMessage email = captor.getValue();

        assertEquals(expectedSubject, email.getSubject());
        assertEquals(EMAIL, email.getTo()[0]);
        Matcher matcher = TOKEN_IN_LINK.matcher(email.getText());
        assertTrue(matcher.find(), "email should contain a link with a token");
        return matcher.group(1);
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private String loginAndGetJwt(String password) throws Exception {
        String body = login(EMAIL, password).andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private String resetBody(String token, String newPassword) {
        return "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}";
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
