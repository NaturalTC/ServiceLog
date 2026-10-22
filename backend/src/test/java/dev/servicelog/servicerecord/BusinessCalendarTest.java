package dev.servicelog.servicerecord;

import dev.servicelog.config.BusinessCalendar;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessCalendarTest {

    @Test
    void lateEveningInMassachusetts_isStillToday_evenThoughItsTomorrowInUtc() {
        // 9:30 pm Eastern on Oct 21 is 01:30 UTC on Oct 22
        Clock utcClock = Clock.fixed(Instant.parse("2026-10-22T01:30:00Z"), ZoneOffset.UTC);

        BusinessCalendar calendar = new BusinessCalendar(utcClock, "America/New_York");

        assertEquals(LocalDate.of(2026, 10, 21), calendar.today());
    }
}
