package dev.servicelog.ai;

import dev.servicelog.config.BusinessCalendar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// A cost guardrail: at most N AI drafts per (business) day. In memory, so it resets on
// restart, which is fine for a spending cap measured in cents.
@Component
public class AiUsageLimiter {

    private final BusinessCalendar calendar;
    private final int maxPerDay;
    private LocalDate day;
    private int used;

    public AiUsageLimiter(BusinessCalendar calendar, @Value("${servicelog.ai.max-requests-per-day:200}") int maxPerDay) {
        this.calendar = calendar;
        this.maxPerDay = maxPerDay;
    }

    public synchronized void acquire() {
        LocalDate today = calendar.today();
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
        if (used >= maxPerDay) {
            throw new AiUnavailableException("The daily limit for AI drafts is used up. Fill in the form by hand.");
        }
        used++;
    }
}
