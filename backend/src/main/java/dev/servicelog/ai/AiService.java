package dev.servicelog.ai;

import dev.servicelog.customer.Customer;
import dev.servicelog.customer.Property;
import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentRepository;
import dev.servicelog.equipment.EquipmentType;
import dev.servicelog.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Drafts service records from notes (S13). Customer details are removed before anything
// reaches the AI provider, and a daily limit caps the cost.
@Service
public class AiService {

    private final NoteExtractor extractor;
    private final EquipmentRepository equipment;
    private final AiUsageLimiter limiter;

    public AiService(NoteExtractor extractor, EquipmentRepository equipment, AiUsageLimiter limiter) {
        this.extractor = extractor;
        this.equipment = equipment;
        this.limiter = limiter;
    }

    // From the service record form: the server knows whose unit this is, so it can strip
    // that customer's name, street, phone and email from the note
    @Transactional(readOnly = true)
    public NoteExtraction draftFor(long equipmentId, String note) {
        Equipment unit = equipment.findDetailedById(equipmentId)
                .orElseThrow(() -> new NotFoundException("Equipment not found"));
        String safeNote = PrivacyRedactor.redact(note, knownDetails(unit.getProperty()));
        limiter.acquire();
        return extractor.extract(safeNote, unit.getType());
    }

    // For the evaluation script (ai-eval/evaluate.py --endpoint): same extraction, no customer
    @Transactional(readOnly = true)
    public NoteExtraction extract(String note, EquipmentType type) {
        limiter.acquire();
        return extractor.extract(PrivacyRedactor.redact(note, List.of()), type);
    }

    private static List<String> knownDetails(Property property) {
        Customer customer = property.getCustomer();
        List<String> details = new ArrayList<>();
        details.add(customer.getName());
        details.addAll(Arrays.asList(customer.getName().split("\\s+")));
        details.add(customer.getEmail());
        for (Property p : customer.getProperties()) {
            details.add(p.getStreet());
        }
        // Longest first, so "Pat Smith" is replaced as a whole before "Pat"
        details.removeIf(d -> d == null || d.isBlank());
        details.sort((a, b) -> b.length() - a.length());
        return details;
    }
}
