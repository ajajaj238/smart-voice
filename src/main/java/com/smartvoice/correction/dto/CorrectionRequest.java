package com.smartvoice.correction.dto;

public record CorrectionRequest(
        String text,
        String scenario,
        String level
) {
}
