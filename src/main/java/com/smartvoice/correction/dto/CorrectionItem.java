package com.smartvoice.correction.dto;

public record CorrectionItem(
        String type,
        String original,
        String corrected,
        String explanation,
        String severity
) {
}
