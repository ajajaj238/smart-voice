package com.smartvoice.voice.dto;

import com.smartvoice.correction.dto.CorrectionResult;
import com.smartvoice.pronunciation.dto.PronunciationResult;

public record VoiceDialogueResponse(
        String sessionId,
        Integer turnIndex,
        AsrResponse asr,
        String aiText,
        TtsResponse tts,
        PronunciationResult pronunciation,
        CorrectionResult correction
) {
}
