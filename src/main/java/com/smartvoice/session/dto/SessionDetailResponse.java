package com.smartvoice.session.dto;

import com.smartvoice.session.Session;

import java.time.Instant;
import java.util.List;

public record SessionDetailResponse(
        String id,
        String scenarioId,
        String status,
        String difficulty,
        Instant startedAt,
        Instant endedAt,
        Integer durationSec,
        List<ConversationTurnResponse> turns
) {
    public static SessionDetailResponse from(Session session, List<ConversationTurnResponse> turns) {
        return new SessionDetailResponse(
                session.getId(),
                session.getScenarioId(),
                session.getStatus().name(),
                session.getDifficulty().name(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getDurationSec(),
                turns
        );
    }
}
