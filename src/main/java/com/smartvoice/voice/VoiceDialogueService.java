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
import com.smartvoice.voice.tts.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceDialogueService {

    private final AsrService asrService;
    private final TtsService ttsService;
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
        PronunciationResult pronunciation = pronunciationService.evaluate(
                asr.text(),
                referenceText,
                asr.durationMs()
        );
        CorrectionResult correction = correctionService.correct(asr.text(), "voice dialogue", "intermediate");
        ConversationTurn turn = chatService.processMessage(sessionId, asr.text(), userId);
        TtsResponse tts = ttsService.synthesize(turn.getAiText(), voice, "wav");
        enrichTurn(turn, pronunciation, correction);

        return new VoiceDialogueResponse(
                sessionId,
                turn.getTurnIndex(),
                asr,
                turn.getAiText(),
                tts,
                pronunciation,
                correction
        );
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
