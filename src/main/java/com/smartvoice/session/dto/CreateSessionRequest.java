package com.smartvoice.session.dto;

import lombok.Data;

@Data
public class CreateSessionRequest {
    private String scenarioId;
    private String difficulty;
}
