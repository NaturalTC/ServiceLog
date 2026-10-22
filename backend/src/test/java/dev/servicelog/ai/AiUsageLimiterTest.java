package dev.servicelog.ai;

import dev.servicelog.config.BusinessCalendar;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiUsageLimiterTest {

    @Test
    void capsDraftsPerDay_andResetsTheNextDay() {
        BusinessCalendar calendar = mock(BusinessCalendar.class);
        when(calendar.today()).thenReturn(LocalDate.of(2026, 10, 22));
        AiUsageLimiter limiter = new AiUsageLimiter(calendar, 2);

        limiter.acquire();
        limiter.acquire();
        assertThrows(AiUnavailableException.class, limiter::acquire);

        when(calendar.today()).thenReturn(LocalDate.of(2026, 10, 23));
        assertDoesNotThrow(limiter::acquire);
    }
}
