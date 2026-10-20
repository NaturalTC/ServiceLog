package dev.servicelog.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerAccountInitializerTest {

    private AppUserRepository users;
    private AuthService authService;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        authService = mock(AuthService.class);
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsOwnerAndSendsVerification_whenNoAccountExists() {
        when(users.count()).thenReturn(0L);

        initializer("owner@example.com", "a-long-enough-password").run(new DefaultApplicationArguments());

        verify(users).save(any(AppUser.class));
        verify(authService).sendVerificationEmail(any(AppUser.class));
    }

    @Test
    void doesNothing_whenAnAccountAlreadyExists() {
        when(users.count()).thenReturn(1L);

        initializer("owner@example.com", "a-long-enough-password").run(new DefaultApplicationArguments());

        verify(users, never()).save(any());
    }

    @Test
    void doesNothing_whenSettingsAreMissing() {
        when(users.count()).thenReturn(0L);

        initializer("", "").run(new DefaultApplicationArguments());

        verify(users, never()).save(any());
    }

    @Test
    void rejectsShortPassword() {
        when(users.count()).thenReturn(0L);

        var initializer = initializer("owner@example.com", "short");

        assertThrows(IllegalStateException.class, () -> initializer.run(new DefaultApplicationArguments()));
    }

    private OwnerAccountInitializer initializer(String email, String password) {
        return new OwnerAccountInitializer(users, encoder, authService, email, password);
    }
}
