package dev.servicelog.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyRedactorTest {

    @Test
    void removesTheCustomersKnownDetails() {
        String note = "Pat Smith at 12 Oak St said Pat noticed the noise. Replaced the inducer.";

        String safe = PrivacyRedactor.redact(note, List.of("Pat Smith", "12 Oak St", "Smith", "Pat"));

        assertFalse(safe.contains("Pat"));
        assertFalse(safe.contains("Smith"));
        assertFalse(safe.contains("Oak St"));
        assertTrue(safe.contains("Replaced the inducer."));
    }

    @Test
    void removesPhoneNumbersAndEmails() {
        String safe = PrivacyRedactor.redact("Call back at (413) 555-0123 or 413.555.0199, email pat@example.com",
                List.of());

        assertEquals("Call back at [phone] or [phone], email [email]", safe);
    }

    @Test
    void leavesTechnicalNumbersAlone() {
        String note = "Replaced 45/5 MFD cap, vacuum 400 microns, model MXZ-3C24NA, 16x25x1 filter, CO 12 ppm";

        assertEquals(note, PrivacyRedactor.redact(note, List.of()));
    }

    @Test
    void matchesWholeWordsOnly_andSkipsVeryShortDetails() {
        // "Al" is too short to replace safely; "Ray" must not hit "spray"
        String safe = PrivacyRedactor.redact("Al asked about the valve; used coil spray.", List.of("Al", "Ray"));

        assertEquals("Al asked about the valve; used coil spray.", safe);
    }
}
