package dev.servicelog.ai;

import dev.servicelog.servicerecord.ServiceKind;

import java.util.List;

// What an extractor pulls out of a technician's free-text note: a draft service record.
// The owner reviews and edits it before anything is saved.
public record NoteExtraction(
        ServiceKind kind,
        String workDone,
        List<String> partsUsed,
        List<Reading> readings,
        boolean followUpNeeded,
        String followUpNote) {

    public record Reading(String name, double value, String unit) {
    }
}
