package com.smartvoice.report.dto;

import com.smartvoice.report.ProgressSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProgressOverviewResponse(
        String userId,
        String period,
        LocalDate periodStart,
        LocalDate periodEnd,
        int sessionCount,
        BigDecimal avgOverall,
        BigDecimal avgPronunciation,
        BigDecimal avgFluency,
        BigDecimal avgGrammar,
        BigDecimal avgVocabulary,
        List<String> commonWeaknesses,
        List<ProgressSnapshot> recentSnapshots
) {
}
