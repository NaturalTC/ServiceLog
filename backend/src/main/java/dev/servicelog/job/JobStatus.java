package dev.servicelog.job;

// SCHEDULED -> IN_PROGRESS (optional) -> DONE, or CANCELLED before it's done
public enum JobStatus {
    SCHEDULED, IN_PROGRESS, DONE, CANCELLED;

    // Still on the calendar: blocks its time slot and can be changed
    public boolean isOpen() {
        return this == SCHEDULED || this == IN_PROGRESS;
    }
}
