package dev.servicelog.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    // conversationId: null to start a new chat
    public record MessageRequest(String conversationId, @NotBlank @Size(max = 2000) String message) {
    }

    // The answer with real details back in, a card for each customer it mentions, and
    // anything it prepared for the owner to confirm
    public record AssistantReply(String conversationId, String reply, List<CustomerCard> customers,
                                 List<Proposal> proposals) {
    }

    // Something prepared for the owner to confirm (S34). type: JOB, CUSTOMER or SERVICE_RECORD.
    // payload is exactly what the app's own form would send; missing lists required fields
    // the owner still has to fill in (Confirm is replaced by Edit until they are).
    public record Proposal(String type, String title, List<Detail> details, Map<String, Object> payload,
                           List<String> missing, Long customerId, Long equipmentId) {
    }

    public record Detail(String label, String value) {
    }

    public record CustomerCard(Long id, String name, String phone, String address) {
    }
}
