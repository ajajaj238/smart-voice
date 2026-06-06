package com.smartvoice.voice.dto;

public record AsrResponse(
        String text,
        String language,
        double confidence,
        Integer durationMs,
        boolean finalResult
) {
}
