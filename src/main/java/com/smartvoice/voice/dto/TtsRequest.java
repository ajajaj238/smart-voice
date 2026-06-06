package com.smartvoice.voice.dto;

public record TtsRequest(
        String text,
        String voice,
        String format
) {
}
