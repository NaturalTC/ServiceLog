package dev.servicelog.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// There is no sign-up page: the owner's account is created once, on first startup,
// from OWNER_EMAIL and OWNER_PASSWORD, and a verification email is sent. After that these
// settings are ignored, so the password can (and should) be removed from the environment.
@Component
public class OwnerAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OwnerAccountInitializer.class);
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final String ownerEmail;
    private final String ownerPassword;

    public OwnerAccountInitializer(AppUserRepository users,
                                   PasswordEncoder passwordEncoder,
                                   AuthService authService,
                                   @Value("${app.owner.email:}") String ownerEmail,
                                   @Value("${app.owner.password:}") String ownerPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.ownerEmail = ownerEmail;
        this.ownerPassword = ownerPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (ownerEmail.isBlank() || ownerPassword.isBlank()) {
            log.warn("No owner account exists yet. Set OWNER_EMAIL and OWNER_PASSWORD to create it.");
            return;
        }
        if (ownerPassword.length() < AuthDtos.MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "OWNER_PASSWORD must be at least " + AuthDtos.MIN_PASSWORD_LENGTH + " characters");
        }

        AppUser owner = users.save(new AppUser(ownerEmail.trim(), passwordEncoder.encode(ownerPassword)));
        authService.sendVerificationEmail(owner);
        log.info("Owner account created; verification email sent");
    }
}
