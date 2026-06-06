package com.smartvoice.pronunciation.dto;

public record PronunciationWordScore(
        String word,
        double score,
        String feedback
) {
}
