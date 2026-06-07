package com.smartvoice.session.dto;

import com.smartvoice.session.ConversationTurn;

import java.math.BigDecimal;
import java.time.Instant;

public record ConversationTurnResponse(
        String id,
        Integer turnIndex,
        String userText,
        String aiText,
        BigDecimal asrConfidence,
        Integer asrDurationMs,
        BigDecimal pronunciationScore,
        BigDecimal fluencyScore,
        String grammarIssues,
        Instant createdAt
) {
    public static ConversationTurnResponse from(ConversationTurn turn) {
        return new ConversationTurnResponse(
                turn.getId(),
                turn.getTurnIndex(),
                turn.getUserText(),
                turn.getAiText(),
                turn.getAsrConfidence(),
                turn.getAsrDurationMs(),
                turn.getPronunciationScore(),
                turn.getFluencyScore(),
                turn.getGrammarIssues(),
                turn.getCreatedAt()
        );
    }
}
