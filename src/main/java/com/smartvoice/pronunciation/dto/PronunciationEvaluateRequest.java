package com.smartvoice.pronunciation.dto;

public record PronunciationEvaluateRequest(
        String text,
        String referenceText,
        Integer durationMs
) {
}
