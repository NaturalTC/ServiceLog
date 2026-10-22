package dev.servicelog.tuneup;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The rules come from configuration, so a different shop can use different seasons
class TuneUpRulesTest {

    @Test
    void seasonsFollowTheConfiguredMonths() {
        TuneUpRules rules = new TuneUpRules(Set.of(9, 10), Set.of(5), 12);

        assertTrue(rules.isHeatingSeason(LocalDate.of(2026, 9, 1)));
        assertFalse(rules.isHeatingSeason(LocalDate.of(2026, 8, 31)));
        assertTrue(rules.isCoolingSeason(LocalDate.of(2026, 5, 20)));
        assertFalse(rules.isCoolingSeason(LocalDate.of(2026, 4, 30)));
    }
}
