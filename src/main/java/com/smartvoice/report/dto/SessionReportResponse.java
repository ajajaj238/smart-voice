package com.smartvoice.report.dto;

import com.smartvoice.report.SessionReport;

import java.math.BigDecimal;
import java.time.Instant;

public record SessionReportResponse(
        String id,
        String sessionId,
        BigDecimal overallScore,
        BigDecimal pronunciationScore,
        BigDecimal fluencyScore,
        BigDecimal grammarScore,
        BigDecimal vocabularyScore,
        BigDecimal comprehensionScore,
        Integer totalTurns,
        Integer totalWords,
        Integer uniqueWords,
        BigDecimal avgSentenceLength,
        String strengths,
        String weaknesses,
        String grammarDetail,
        String pronunciationDetail,
        String vocabularySuggestions,
        String teacherComment,
        BigDecimal previousAvgScore,
        BigDecimal scoreChange,
        Instant createdAt
) {

    public static SessionReportResponse from(SessionReport report) {
        return new SessionReportResponse(
                report.getId(),
                report.getSessionId(),
                report.getOverallScore(),
                report.getPronunciationScore(),
                report.getFluencyScore(),
                report.getGrammarScore(),
                report.getVocabularyScore(),
                report.getComprehensionScore(),
                report.getTotalTurns(),
                report.getTotalWords(),
                report.getUniqueWords(),
                report.getAvgSentenceLength(),
                report.getStrengths(),
                report.getWeaknesses(),
                report.getGrammarDetail(),
                report.getPronunciationDetail(),
                report.getVocabularySuggestions(),
                report.getTeacherComment(),
                report.getPreviousAvgScore(),
                report.getScoreChange(),
                report.getCreatedAt()
        );
    }
}
