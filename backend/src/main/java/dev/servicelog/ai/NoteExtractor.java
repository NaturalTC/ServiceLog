package dev.servicelog.ai;

import dev.servicelog.equipment.EquipmentType;

// Turns a service note into a draft record. An interface so the AI provider can be swapped
// (and faked in tests); OpenAiNoteExtractor is the first implementation.
public interface NoteExtractor {

    // The note must already have customer details removed (see PrivacyRedactor)
    NoteExtraction extract(String note, EquipmentType equipmentType);
}
