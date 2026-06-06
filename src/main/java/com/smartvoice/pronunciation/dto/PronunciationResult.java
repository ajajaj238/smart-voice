package com.smartvoice.pronunciation.dto;

import java.util.List;

public record PronunciationResult(
        double pronunciationScore,
        double fluencyScore,
        double paceScore,
        double overallScore,
        int wordCount,
        double wordsPerMinute,
        List<PronunciationWordScore> wordScores,
        List<PronunciationIssue> issues,
        List<String> suggestions
) {
}
