package com.smartvoice.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
        }

        var turn = chatService.processMessage(sessionId, message, auth.getPrincipal().toString());
        return ResponseEntity.ok(Map.of(
                "turnIndex", turn.getTurnIndex(),
                "userText", turn.getUserText(),
                "aiText", turn.getAiText()
        ));
    }

    @PostMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String message = body.get("message");
        String userId = auth.getPrincipal().toString();
        SseEmitter emitter = new SseEmitter(120000L); // 2 min timeout

        CompletableFuture.runAsync(() -> {
            try {
                var turn = chatService.processMessage(sessionId, message, userId);
                String aiText = turn.getAiText();
                // Simulate streaming by sending chunks
                for (int i = 0; i < aiText.length(); i += 3) {
                    int end = Math.min(i + 3, aiText.length());
                    String chunk = aiText.substring(i, end);
                    emitter.send(SseEmitter.event().data(chunk));
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<Map<String, Object>> endSession(@PathVariable String sessionId, Authentication auth) {
        var session = chatService.endSession(sessionId, auth.getPrincipal().toString());
        return ResponseEntity.ok(Map.of(
                "sessionId", session.getId(),
                "status", session.getStatus().name(),
                "durationSec", session.getDurationSec()
        ));
    }
}
