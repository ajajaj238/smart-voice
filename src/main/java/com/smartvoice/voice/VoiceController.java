package com.smartvoice.voice;

import com.smartvoice.chat.ChatService;
import com.smartvoice.correction.CorrectionService;
import com.smartvoice.pronunciation.PronunciationService;
import com.smartvoice.voice.asr.AsrService;
import com.smartvoice.voice.config.AliyunNlsProperties;
import com.smartvoice.voice.dto.AsrResponse;
import com.smartvoice.voice.dto.NlsConfigStatusResponse;
import com.smartvoice.voice.dto.TtsRequest;
import com.smartvoice.voice.dto.TtsResponse;
import com.smartvoice.voice.dto.VoiceDialogueResponse;
import com.smartvoice.voice.dto.VoiceDialogueStreamRequest;
import com.smartvoice.voice.tts.TtsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final AsrService asrService;
    private final TtsService ttsService;
    private final VoiceDialogueService voiceDialogueService;
    private final ChatService chatService;
    private final PronunciationService pronunciationService;
    private final CorrectionService correctionService;
    private final AliyunNlsProperties nlsProperties;

    @GetMapping("/nls/status")
    public ResponseEntity<NlsConfigStatusResponse> nlsStatus() {
        return ResponseEntity.ok(new NlsConfigStatusResponse(
                nlsProperties.isEnabled(),
                nlsProperties.isFallbackEnabled(),
                StringUtils.hasText(nlsProperties.getAppKey()),
                StringUtils.hasText(nlsProperties.getAccessKeyId()),
                StringUtils.hasText(nlsProperties.getAccessKeySecret()),
                nlsProperties.missingCredentialNames(),
                nlsProperties.getGatewayUrl(),
                nlsProperties.getSampleRate(),
                nlsProperties.getTtsVoice(),
                nlsProperties.getTtsFormat()
        ));
    }

    @PostMapping(value = "/asr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AsrResponse> transcribe(
            @RequestPart(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "transcriptHint", required = false) String transcriptHint,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "durationMs", required = false) Integer durationMs) {
        return ResponseEntity.ok(asrService.transcribe(audio, transcriptHint, language, durationMs));
    }

    @PostMapping(value = "/tts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TtsResponse> synthesize(@RequestBody TtsRequest request) {
        return ResponseEntity.ok(ttsService.synthesize(request.text(), request.voice(), request.format()));
    }

    @PostMapping(value = "/tts", consumes = {
            MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE
    })
    public ResponseEntity<TtsResponse> synthesizeByForm(
            @RequestParam String text,
            @RequestParam(value = "voice", required = false) String voice,
            @RequestParam(value = "format", required = false) String format) {
        return ResponseEntity.ok(ttsService.synthesize(text, voice, format));
    }

    @PostMapping(value = "/dialogue/{sessionId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VoiceDialogueResponse> dialogue(
            @PathVariable String sessionId,
            @RequestPart(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "transcriptHint", required = false) String transcriptHint,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "durationMs", required = false) Integer durationMs,
            @RequestParam(value = "referenceText", required = false) String referenceText,
            @RequestParam(value = "voice", required = false) String voice,
            Authentication auth) {
        return ResponseEntity.ok(voiceDialogueService.process(
                sessionId,
                auth.getPrincipal().toString(),
                audio,
                transcriptHint,
                language,
                durationMs,
                referenceText,
                voice
        ));
    }

    @PostMapping(value = "/dialogue/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter dialogueStream(
            @PathVariable String sessionId,
            @RequestBody VoiceDialogueStreamRequest request,
            Authentication auth) {
        SseEmitter emitter = new SseEmitter(120000L);
        String userId = auth.getPrincipal().toString();

        CompletableFuture.runAsync(() -> {
            try {
                voiceDialogueService.validateSessionCanAcceptVoice(sessionId, userId);
                AsrResponse asr = asrService.transcribe(
                        null,
                        request.transcriptHint(),
                        request.language(),
                        request.durationMs()
                );
                emitter.send(SseEmitter.event().name("asr").data(asr));

                var pronunciation = pronunciationService.evaluate(
                        asr.text(),
                        request.referenceText(),
                        asr.durationMs()
                );
                emitter.send(SseEmitter.event().name("pronunciation").data(pronunciation));
                emitter.send(SseEmitter.event().name("correction")
                        .data(correctionService.correct(asr.text(), "voice dialogue", "intermediate")));

                var turn = chatService.processMessage(sessionId, asr.text(), userId);
                String aiText = turn.getAiText() == null ? "" : turn.getAiText();
                for (int i = 0; i < aiText.length(); i += 4) {
                    int end = Math.min(i + 4, aiText.length());
                    emitter.send(SseEmitter.event().name("ai_delta").data(aiText.substring(i, end)));
                }
                emitter.send(SseEmitter.event().name("ai_done").data(aiText));

                TtsResponse tts = ttsService.synthesize(aiText, request.voice(), "wav");
                emitter.send(SseEmitter.event().name("tts").data(tts));
                emitter.send(SseEmitter.event().name("done").data("completed"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
