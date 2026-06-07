package com.smartvoice.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartvoice.chat.ChatService;
import com.smartvoice.correction.CorrectionService;
import com.smartvoice.correction.dto.CorrectionResult;
import com.smartvoice.pronunciation.PronunciationService;
import com.smartvoice.pronunciation.dto.PronunciationResult;
import com.smartvoice.session.ConversationTurn;
import com.smartvoice.session.ConversationTurnMapper;
import com.smartvoice.session.Session;
import com.smartvoice.session.SessionMapper;
import com.smartvoice.shared.enums.SessionStatus;
import com.smartvoice.voice.asr.AsrService;
import com.smartvoice.voice.dto.AsrResponse;
import com.smartvoice.voice.dto.TtsResponse;
import com.smartvoice.voice.dto.VoiceDialogueResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceDialogueService {

    private final AsrService asrService;
    private final ChatService chatService;
    private final PronunciationService pronunciationService;
    private final CorrectionService correctionService;
    private final ConversationTurnMapper turnMapper;
    private final SessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    public VoiceDialogueResponse process(
            String sessionId,
            String userId,
            MultipartFile audio,
            String transcriptHint,
            String language,
            Integer durationMs,
            String referenceText,
            String voice
    ) {
        validateSessionCanAcceptVoice(sessionId, userId);
        AsrResponse asr = asrService.transcribe(audio, transcriptHint, language, durationMs);
        ConversationTurn turn = chatService.processMessage(sessionId, asr.text(), userId);
        enrichTurnWithAsr(turn, asr);
        runAsyncAnalysis(turn, asr, referenceText);

        return new VoiceDialogueResponse(
                sessionId,
                turn.getTurnIndex(),
                asr,
                turn.getAiText(),
                pendingTts(turn.getAiText(), voice),
                pendingPronunciation(),
                pendingCorrection(asr.text())
        );
    }

    private void runAsyncAnalysis(ConversationTurn turn, AsrResponse asr, String referenceText) {
        CompletableFuture.runAsync(() -> {
            PronunciationResult pronunciation = pendingPronunciation();
            CorrectionResult correction = pendingCorrection(asr.text());
            try {
                pronunciation = pronunciationService.evaluate(
                        asr.text(),
                        referenceText,
                        asr.durationMs()
                );
            } catch (Exception e) {
                log.warn("Async pronunciation analysis failed. turnId={}", turn.getId(), e);
            }
            try {
                correction = correctionService.correct(asr.text(), "voice dialogue", "intermediate");
            } catch (Exception e) {
                log.warn("Async correction analysis failed. turnId={}", turn.getId(), e);
            }
            try {
                enrichTurn(turn, pronunciation, correction);
            } catch (Exception e) {
                log.warn("Async turn enrichment failed. turnId={}", turn.getId(), e);
            }
        });

    }

    private TtsResponse pendingTts(String text, String voice) {
        return new TtsResponse(text == null ? "" : text, voice, "wav", "audio/wav", 0, "");
    }

    private PronunciationResult pendingPronunciation() {
        return new PronunciationResult(0, 0, 0, 0, 0, 0, List.of(), List.of(),
                List.of("发音反馈生成中，稍后会自动更新。"));
    }

    private CorrectionResult pendingCorrection(String text) {
        String safeText = text == null ? "" : text;
        return new CorrectionResult(safeText, safeText, 0, 0, List.of(), List.of(),
                "纠错与表达反馈生成中，稍后会自动更新。");
    }

    private void enrichTurnWithAsr(ConversationTurn turn, AsrResponse asr) {
        try {
            turn.setAsrConfidence(BigDecimal.valueOf(asr.confidence()));
            turn.setAsrDurationMs(asr.durationMs());
            turnMapper.updateById(turn);
        } catch (Exception e) {
            log.warn("Failed to enrich conversation turn ASR metadata. turnId={}", turn.getId(), e);
        }
    }

    public void validateSessionCanAcceptVoice(String sessionId, String userId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found.");
        }
        if (!session.getUserId().equals(userId)) {
            throw new SecurityException("You do not have permission to access this session.");
        }
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new IllegalStateException("This session has already generated a report. Please create a new session before recording again.");
        }
    }

    private void enrichTurn(ConversationTurn turn, PronunciationResult pronunciation, CorrectionResult correction) {
        try {
            turn.setPronunciationScore(BigDecimal.valueOf(pronunciation.pronunciationScore()));
            turn.setFluencyScore(BigDecimal.valueOf(pronunciation.fluencyScore()));
            turn.setGrammarIssues(objectMapper.writeValueAsString(correction.corrections()));
            turnMapper.updateById(turn);
        } catch (Exception e) {
            log.warn("Failed to enrich conversation turn analysis. turnId={}", turn.getId(), e);
        }
    }
}
