package com.docuchat.web;

import com.docuchat.config.CurrentUser;
import com.docuchat.service.ChatService;
import com.docuchat.web.dto.ChatAnswer;
import com.docuchat.web.dto.ChatRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatAnswer chat(@Valid @RequestBody ChatRequest request) {
        return chat.chat(request, CurrentUser.id());
    }

    @PostMapping(value = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody ChatRequest request) {
        String userId = CurrentUser.id(); // resolve on the request thread, before the reactive pipeline
        return chat.stream(request, userId)
                .map(e -> ServerSentEvent.<Object>builder().event(e.type()).data(e.data()).build());
    }
}
