package com.smartvoice.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartvoice.chat.ChatService;
import com.smartvoice.correction.CorrectionService;
import com.smartvoice.correction.dto.CorrectionResult;
import com.smartvoice.pronunciation.PronunciationService;
import com.smartvoice.pronunciation.dto.PronunciationResult;
import com.smartvoice.session.ConversationTurn;
import com.smartvoice.session.ConversationTurnMapper;
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
    private final ObjectMapper objectMapper;

    public VoiceDialogueResponse process(
            String sessionId,
            MultipartFile audio,
            String transcriptHint,
            String language,
            Integer durationMs,
            String referenceText,
            String voice
    ) {
        AsrResponse asr = asrService.transcribe(audio, transcriptHint, language, durationMs);
        PronunciationResult pronunciation = pronunciationService.evaluate(
                asr.text(),
                referenceText,
                asr.durationMs()
        );
        CorrectionResult correction = correctionService.correct(asr.text(), "voice dialogue", "intermediate");
        ConversationTurn turn = chatService.processMessage(sessionId, asr.text());
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
