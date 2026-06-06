package com.smartvoice.correction.dto;

import java.util.List;

public record CorrectionResult(
        String originalText,
        String correctedText,
        double grammarScore,
        double expressionScore,
        List<CorrectionItem> corrections,
        List<String> betterExpressions,
        String overallFeedback
) {
}
