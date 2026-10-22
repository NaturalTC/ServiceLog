package dev.servicelog.assistant;

import dev.servicelog.assistant.AssistantDtos.AssistantReply;
import dev.servicelog.assistant.AssistantDtos.FillRequest;
import dev.servicelog.assistant.AssistantDtos.MessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Login required (see SecurityConfig). Nothing here saves anything: the assistant looks
// things up and prepares cards; the owner confirms through the app's normal endpoints.
@RestController
public class AssistantController {

    private final AssistantService assistant;
    private final FormFiller formFiller;

    public AssistantController(AssistantService assistant, FormFiller formFiller) {
        this.assistant = assistant;
        this.formFiller = formFiller;
    }

    // "Describe it" on the customer, job and equipment forms: fields for the owner to check; nothing is saved
    @PostMapping("/api/assistant/fill")
    public Map<String, Object> fill(@Valid @RequestBody FillRequest request) {
        return formFiller.fill(request.form(), request.text());
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
