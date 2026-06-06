package com.smartvoice.voice.dto;

public record TtsResponse(
        String text,
        String voice,
        String format,
        String mimeType,
        Integer durationMs,
        String audioContentBase64
) {
}
