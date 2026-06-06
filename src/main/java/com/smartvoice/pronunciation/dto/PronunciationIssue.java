package com.smartvoice.pronunciation.dto;

public record PronunciationIssue(
        String type,
        String target,
        String message
) {
}
