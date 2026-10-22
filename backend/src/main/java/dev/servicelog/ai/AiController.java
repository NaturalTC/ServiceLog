package dev.servicelog.ai;

import dev.servicelog.equipment.EquipmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Login required (see SecurityConfig). Drafts only: nothing here saves a service record.
@RestController
public class AiController {

    private final AiService ai;

    public AiController(AiService ai) {
        this.ai = ai;
    }

    public record DraftRequest(@NotBlank @Size(max = 4000) String note) {
    }

    public record ExtractRequest(@NotBlank @Size(max = 4000) String note, @NotNull EquipmentType equipmentType) {
    }

    // The service record form: a note in, a draft out, for the owner to review
    @PostMapping("/api/equipment/{equipmentId}/service-records/draft")
    public NoteExtraction draft(@PathVariable long equipmentId, @Valid @RequestBody DraftRequest request) {
        return ai.draftFor(equipmentId, request.note());
    }

    // Used by the evaluation script to measure accuracy (see docs/AI-EVALUATION.md)
    @PostMapping("/api/ai/extract")
    public NoteExtraction extract(@Valid @RequestBody ExtractRequest request) {
        return ai.extract(request.note(), request.equipmentType());
    }
}
