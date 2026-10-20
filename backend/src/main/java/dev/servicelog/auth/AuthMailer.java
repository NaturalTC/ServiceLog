package dev.servicelog.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// Sends the verification and password-reset emails.
// @Async: sending happens in the background, so the API answers just as fast whether or
// not an email was sent. Otherwise response time would reveal which emails have accounts.
@Component
public class AuthMailer {

    private static final Logger log = LoggerFactory.getLogger(AuthMailer.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendUrl;

    public AuthMailer(JavaMailSender mailSender,
                      @Value("${app.mail.from}") String from,
                      @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @Async
    public void sendVerification(String to, String token) {
        send(to, "Verify your ServiceLog email",
                "Confirm this is your email address for ServiceLog:\n\n"
                        + link("/verify-email", token)
                        + "\n\nThe link works once and expires in 24 hours.");
    }

    @Async
    public void sendPasswordReset(String to, String token) {
        send(to, "Reset your ServiceLog password",
                "Someone asked to reset your ServiceLog password. If it was you, use this link:\n\n"
                        + link("/reset-password", token)
                        + "\n\nThe link works once and expires in 30 minutes. "
                        + "If you didn't ask for this, ignore this email; your password won't change.");
    }

    private String link(String path, String token) {
        return frontendUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Could not send email '{}'", subject, e);
        }
    }
}
