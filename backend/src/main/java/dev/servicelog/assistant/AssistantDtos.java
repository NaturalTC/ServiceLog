package dev.servicelog.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    // conversationId: null to start a new chat
    public record MessageRequest(String conversationId, @NotBlank @Size(max = 2000) String message) {
    }

    // The answer with real details back in, plus a card for each customer it mentions
    public record AssistantReply(String conversationId, String reply, List<CustomerCard> customers) {
    }

    public record CustomerCard(Long id, String name, String phone, String address) {
    }
}
