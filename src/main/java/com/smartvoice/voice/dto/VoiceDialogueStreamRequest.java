package com.smartvoice.voice.dto;

public record VoiceDialogueStreamRequest(
        String transcriptHint,
        String referenceText,
        Integer durationMs,
        String voice,
        String language
) {
}
