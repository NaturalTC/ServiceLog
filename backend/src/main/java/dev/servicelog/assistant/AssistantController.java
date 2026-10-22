package dev.servicelog.assistant;

import dev.servicelog.assistant.AssistantDtos.AssistantReply;
import dev.servicelog.assistant.AssistantDtos.MessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Login required (see SecurityConfig). Read-only: the assistant looks things up, it doesn't change anything.
@RestController
public class AssistantController {

    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/api/assistant/messages")
    public AssistantReply message(@Valid @RequestBody MessageRequest request) {
        return assistant.reply(request.conversationId(), request.message());
    }

    // "New chat": forget this conversation and its placeholders
    @DeleteMapping("/api/assistant/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@PathVariable String id) {
        assistant.end(id);
    }
}
