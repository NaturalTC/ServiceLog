package dev.servicelog.tuneup;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.Set;

// When equipment is due for a tune-up. Set in application.properties (servicelog.tune-ups.*)
// so the owner's seasons and interval can change without a code change.
@ConfigurationProperties("servicelog.tune-ups")
public record TuneUpRules(
        Set<Integer> heatingMonths, // months (1-12) when heating equipment is due
        Set<Integer> coolingMonths, // months when cooling equipment is due
        int intervalMonths) {       // due again this many months after the last tune-up

    public boolean isHeatingSeason(LocalDate date) {
        return heatingMonths.contains(date.getMonthValue());
    }

    public boolean isCoolingSeason(LocalDate date) {
        return coolingMonths.contains(date.getMonthValue());
    }
}
