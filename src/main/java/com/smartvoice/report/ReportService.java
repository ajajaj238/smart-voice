package com.smartvoice.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartvoice.report.dto.ProgressOverviewResponse;
import com.smartvoice.session.ConversationTurn;
import com.smartvoice.session.ConversationTurnMapper;
import com.smartvoice.session.Session;
import com.smartvoice.session.SessionMapper;
import com.smartvoice.shared.enums.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final SessionMapper sessionMapper;
    private final ConversationTurnMapper turnMapper;
    private final SessionReportMapper reportMapper;
    private final ProgressSnapshotMapper progressSnapshotMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public SessionReport generateSessionReport(String sessionId) {
        Session session = requireSession(sessionId);
        List<ConversationTurn> turns = listTurns(sessionId);
        if (turns.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate report because this session has no conversation turns.");
        }

        SessionReport existing = findBySessionId(sessionId);
        ReportStats stats = calculateStats(turns);
        BigDecimal previousAvg = calculatePreviousAverage(session);
        BigDecimal scoreChange = previousAvg == null ? null : stats.overallScore().subtract(previousAvg).setScale(1, RoundingMode.HALF_UP);

        SessionReport report = existing == null ? new SessionReport() : existing;
        report.setSessionId(sessionId);
        report.setOverallScore(stats.overallScore());
        report.setPronunciationScore(stats.pronunciationScore());
        report.setFluencyScore(stats.fluencyScore());
        report.setGrammarScore(stats.grammarScore());
        report.setVocabularyScore(stats.vocabularyScore());
        report.setComprehensionScore(stats.comprehensionScore());
        report.setTotalTurns(turns.size());
        report.setTotalWords(stats.totalWords());
        report.setUniqueWords(stats.uniqueWords());
        report.setAvgSentenceLength(stats.avgSentenceLength());
        report.setStrengths(toJson(stats.strengths()));
        report.setWeaknesses(toJson(stats.weaknesses()));
        report.setGrammarDetail(toJson(stats.grammarDetails()));
        report.setPronunciationDetail(toJson(stats.pronunciationDetails()));
        report.setVocabularySuggestions(toJson(stats.vocabularySuggestions()));
        report.setTeacherComment(buildTeacherComment(stats));
        report.setPreviousAvgScore(previousAvg);
        report.setScoreChange(scoreChange);

        if (existing == null) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }

        completeSessionIfNeeded(session);
        upsertWeeklyProgress(session.getUserId());
        return report;
    }

    public SessionReport getSessionReport(String sessionId) {
        SessionReport report = findBySessionId(sessionId);
        if (report == null) {
            throw new IllegalArgumentException("Session report not found. Generate it first.");
        }
        return report;
    }

    @Transactional
    public ProgressOverviewResponse getProgressOverview(String userId, String period) {
        String resolvedPeriod = period == null || period.isBlank() ? "WEEKLY" : period.toUpperCase(Locale.ROOT);
        PeriodRange range = resolvePeriodRange(resolvedPeriod, LocalDate.now(DEFAULT_ZONE));
        ProgressSnapshot snapshot = upsertProgress(userId, resolvedPeriod, range.start(), range.end());
        List<ProgressSnapshot> recentSnapshots = listRecentSnapshots(userId, resolvedPeriod, 8);
        List<String> commonWeaknesses = collectCommonWeaknesses(userId);

        return new ProgressOverviewResponse(
                userId,
                resolvedPeriod,
                range.start(),
                range.end(),
                nullToZero(snapshot.getSessionCount()),
                snapshot.getAvgOverall(),
                snapshot.getAvgPronunciation(),
                snapshot.getAvgFluency(),
                snapshot.getAvgGrammar(),
                snapshot.getAvgVocabulary(),
                commonWeaknesses,
                recentSnapshots
        );
    }

    private Session requireSession(String sessionId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found.");
        }
        return session;
    }

    private List<ConversationTurn> listTurns(String sessionId) {
        return turnMapper.selectList(new LambdaQueryWrapper<ConversationTurn>()
                .eq(ConversationTurn::getSessionId, sessionId)
                .orderByAsc(ConversationTurn::getTurnIndex));
    }

    private SessionReport findBySessionId(String sessionId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<SessionReport>()
                .eq(SessionReport::getSessionId, sessionId)
                .last("LIMIT 1"));
    }

    private ReportStats calculateStats(List<ConversationTurn> turns) {
        List<String> userTexts = turns.stream()
                .map(ConversationTurn::getUserText)
                .filter(Objects::nonNull)
                .filter(text -> !text.isBlank())
                .toList();
        List<String> words = userTexts.stream()
                .flatMap(text -> Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-zA-Z']+")))
                .filter(word -> !word.isBlank())
                .toList();
        Set<String> uniqueWords = new LinkedHashSet<>(words);

        BigDecimal pronunciation = avgScore(turns.stream().map(ConversationTurn::getPronunciationScore).toList(), BigDecimal.valueOf(82));
        BigDecimal fluency = avgScore(turns.stream().map(ConversationTurn::getFluencyScore).toList(), estimateFluency(words.size(), turns.size()));
        BigDecimal grammar = estimateGrammarScore(turns);
        BigDecimal vocabulary = estimateVocabularyScore(words.size(), uniqueWords.size());
        BigDecimal comprehension = estimateComprehensionScore(turns);
        BigDecimal overall = average(List.of(pronunciation, fluency, grammar, vocabulary, comprehension));
        BigDecimal avgSentenceLength = turns.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(words.size() * 1.0 / turns.size()).setScale(1, RoundingMode.HALF_UP);

        return new ReportStats(
                overall,
                pronunciation,
                fluency,
                grammar,
                vocabulary,
                comprehension,
                turns.size(),
                words.size(),
                uniqueWords.size(),
                avgSentenceLength,
                buildStrengths(pronunciation, fluency, grammar, vocabulary, comprehension, words.size()),
                buildWeaknesses(pronunciation, fluency, grammar, vocabulary, turns),
                collectGrammarDetails(turns),
                collectPronunciationDetails(turns),
                buildVocabularySuggestions(uniqueWords)
        );
    }

    private BigDecimal avgScore(List<BigDecimal> scores, BigDecimal fallback) {
        List<BigDecimal> valid = scores.stream().filter(Objects::nonNull).toList();
        if (valid.isEmpty()) {
            return fallback.setScale(1, RoundingMode.HALF_UP);
        }
        return average(valid);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal estimateFluency(int totalWords, int turns) {
        if (turns == 0) {
            return BigDecimal.valueOf(70);
        }
        double avgWords = totalWords * 1.0 / turns;
        return score(Math.min(90, 68 + avgWords * 1.4));
    }

    private BigDecimal estimateGrammarScore(List<ConversationTurn> turns) {
        long issueTurns = turns.stream()
                .map(ConversationTurn::getGrammarIssues)
                .filter(Objects::nonNull)
                .filter(text -> !text.isBlank() && !"[]".equals(text.trim()))
                .count();
        return score(92 - issueTurns * 7.5);
    }

    private BigDecimal estimateVocabularyScore(int totalWords, int uniqueWords) {
        if (totalWords == 0) {
            return BigDecimal.valueOf(60);
        }
        double ratio = uniqueWords * 1.0 / totalWords;
        return score(68 + ratio * 35 + Math.min(8, uniqueWords * 0.25));
    }

    private BigDecimal estimateComprehensionScore(List<ConversationTurn> turns) {
        long answeredTurns = turns.stream()
                .filter(turn -> hasText(turn.getUserText()) && hasText(turn.getAiText()))
                .count();
        if (turns.isEmpty()) {
            return BigDecimal.valueOf(60);
        }
        return score(72 + answeredTurns * 1.8);
    }

    private BigDecimal score(double value) {
        return BigDecimal.valueOf(Math.max(0, Math.min(100, value))).setScale(1, RoundingMode.HALF_UP);
    }

    private List<String> buildStrengths(BigDecimal pronunciation, BigDecimal fluency, BigDecimal grammar,
                                        BigDecimal vocabulary, BigDecimal comprehension, int totalWords) {
        List<String> strengths = new ArrayList<>();
        if (pronunciation.compareTo(BigDecimal.valueOf(80)) >= 0) strengths.add("Pronunciation is stable and understandable.");
        if (fluency.compareTo(BigDecimal.valueOf(80)) >= 0) strengths.add("Responses are fluent enough for natural roleplay.");
        if (grammar.compareTo(BigDecimal.valueOf(85)) >= 0) strengths.add("Grammar accuracy is strong in this session.");
        if (vocabulary.compareTo(BigDecimal.valueOf(80)) >= 0) strengths.add("Vocabulary variety is developing well.");
        if (comprehension.compareTo(BigDecimal.valueOf(80)) >= 0) strengths.add("The user responded well to the AI roleplay context.");
        if (totalWords >= 80) strengths.add("The user produced enough speech for meaningful practice.");
        if (strengths.isEmpty()) strengths.add("The user completed the practice and built useful speaking momentum.");
        return strengths;
    }

    private List<String> buildWeaknesses(BigDecimal pronunciation, BigDecimal fluency, BigDecimal grammar,
                                         BigDecimal vocabulary, List<ConversationTurn> turns) {
        List<String> weaknesses = new ArrayList<>();
        if (pronunciation.compareTo(BigDecimal.valueOf(75)) < 0) weaknesses.add("Pronunciation needs more focused repetition.");
        if (fluency.compareTo(BigDecimal.valueOf(75)) < 0) weaknesses.add("Fluency can improve by answering in fuller phrases.");
        if (grammar.compareTo(BigDecimal.valueOf(80)) < 0) weaknesses.add("Grammar mistakes should be reviewed after class.");
        if (vocabulary.compareTo(BigDecimal.valueOf(75)) < 0) weaknesses.add("Vocabulary range is limited; add more scenario-specific phrases.");
        if (turns.size() < 4) weaknesses.add("The session is short; practice more turns for a stronger evaluation.");
        if (weaknesses.isEmpty()) weaknesses.add("Keep practicing longer answers and more natural transitions.");
        return weaknesses;
    }

    private List<String> collectGrammarDetails(List<ConversationTurn> turns) {
        return turns.stream()
                .map(ConversationTurn::getGrammarIssues)
                .filter(this::hasText)
                .filter(text -> !"[]".equals(text.trim()))
                .limit(10)
                .toList();
    }

    private List<String> collectPronunciationDetails(List<ConversationTurn> turns) {
        List<String> details = new ArrayList<>();
        for (ConversationTurn turn : turns) {
            if (turn.getPronunciationScore() != null && turn.getPronunciationScore().compareTo(BigDecimal.valueOf(75)) < 0) {
                details.add("Turn " + turn.getTurnIndex() + ": pronunciation score " + turn.getPronunciationScore());
            }
            if (turn.getFluencyScore() != null && turn.getFluencyScore().compareTo(BigDecimal.valueOf(75)) < 0) {
                details.add("Turn " + turn.getTurnIndex() + ": fluency score " + turn.getFluencyScore());
            }
        }
        if (details.isEmpty()) {
            details.add("No major pronunciation issue was detected from available scores.");
        }
        return details;
    }

    private List<String> buildVocabularySuggestions(Set<String> uniqueWords) {
        List<String> suggestions = new ArrayList<>();
        if (!uniqueWords.contains("could")) suggestions.add("Could I clarify one point?");
        if (!uniqueWords.contains("recommend")) suggestions.add("What would you recommend?");
        if (!uniqueWords.contains("experience")) suggestions.add("I have experience handling similar situations.");
        if (!uniqueWords.contains("follow")) suggestions.add("Let me follow up on that.");
        return suggestions.stream().limit(5).toList();
    }

    private String buildTeacherComment(ReportStats stats) {
        return "Overall score " + stats.overallScore() + ". "
                + "Review the listed weaknesses, then repeat the same scenario with longer answers and fewer pauses.";
    }

    private BigDecimal calculatePreviousAverage(Session session) {
        List<Session> sessions = sessionMapper.selectList(new LambdaQueryWrapper<Session>()
                .eq(Session::getUserId, session.getUserId())
                .ne(Session::getId, session.getId())
                .orderByDesc(Session::getStartedAt)
                .last("LIMIT 5"));
        List<String> sessionIds = sessions.stream().map(Session::getId).toList();
        if (sessionIds.isEmpty()) {
            return null;
        }
        List<SessionReport> reports = reportMapper.selectList(new LambdaQueryWrapper<SessionReport>()
                .in(SessionReport::getSessionId, sessionIds)
                .orderByDesc(SessionReport::getCreatedAt));
        List<BigDecimal> scores = reports.stream().map(SessionReport::getOverallScore).filter(Objects::nonNull).toList();
        return scores.isEmpty() ? null : average(scores);
    }

    private void completeSessionIfNeeded(Session session) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return;
        }
        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(Instant.now());
        if (session.getStartedAt() != null) {
            session.setDurationSec((int) Duration.between(session.getStartedAt(), Instant.now()).getSeconds());
        }
        sessionMapper.updateById(session);
    }

    private void upsertWeeklyProgress(String userId) {
        PeriodRange range = resolvePeriodRange("WEEKLY", LocalDate.now(DEFAULT_ZONE));
        upsertProgress(userId, "WEEKLY", range.start(), range.end());
    }

    private ProgressSnapshot upsertProgress(String userId, String period, LocalDate start, LocalDate end) {
        List<Session> sessions = sessionMapper.selectList(new LambdaQueryWrapper<Session>()
                .eq(Session::getUserId, userId)
                .ge(Session::getStartedAt, start.atStartOfDay(DEFAULT_ZONE).toInstant())
                .lt(Session::getStartedAt, end.plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant()));
        List<String> sessionIds = sessions.stream().map(Session::getId).toList();
        List<SessionReport> reports = sessionIds.isEmpty()
                ? List.of()
                : reportMapper.selectList(new LambdaQueryWrapper<SessionReport>().in(SessionReport::getSessionId, sessionIds));

        ProgressSnapshot snapshot = progressSnapshotMapper.selectOne(new LambdaQueryWrapper<ProgressSnapshot>()
                .eq(ProgressSnapshot::getUserId, userId)
                .eq(ProgressSnapshot::getPeriod, period)
                .eq(ProgressSnapshot::getPeriodStart, start)
                .eq(ProgressSnapshot::getPeriodEnd, end)
                .last("LIMIT 1"));
        if (snapshot == null) {
            snapshot = ProgressSnapshot.builder()
                    .userId(userId)
                    .period(period)
                    .periodStart(start)
                    .periodEnd(end)
                    .build();
        }

        snapshot.setSessionCount(reports.size());
        snapshot.setAvgOverall(avgOrNull(reports.stream().map(SessionReport::getOverallScore).toList()));
        snapshot.setAvgPronunciation(avgOrNull(reports.stream().map(SessionReport::getPronunciationScore).toList()));
        snapshot.setAvgFluency(avgOrNull(reports.stream().map(SessionReport::getFluencyScore).toList()));
        snapshot.setAvgGrammar(avgOrNull(reports.stream().map(SessionReport::getGrammarScore).toList()));
        snapshot.setAvgVocabulary(avgOrNull(reports.stream().map(SessionReport::getVocabularyScore).toList()));

        if (snapshot.getId() == null) {
            progressSnapshotMapper.insert(snapshot);
        } else {
            progressSnapshotMapper.updateById(snapshot);
        }
        return snapshot;
    }

    private BigDecimal avgOrNull(List<BigDecimal> values) {
        List<BigDecimal> valid = values.stream().filter(Objects::nonNull).toList();
        return valid.isEmpty() ? null : average(valid);
    }

    private List<ProgressSnapshot> listRecentSnapshots(String userId, String period, int limit) {
        return progressSnapshotMapper.selectList(new LambdaQueryWrapper<ProgressSnapshot>()
                .eq(ProgressSnapshot::getUserId, userId)
                .eq(ProgressSnapshot::getPeriod, period)
                .orderByDesc(ProgressSnapshot::getPeriodStart)
                .last("LIMIT " + limit));
    }

    private List<String> collectCommonWeaknesses(String userId) {
        List<Session> sessions = sessionMapper.selectList(new LambdaQueryWrapper<Session>()
                .eq(Session::getUserId, userId)
                .orderByDesc(Session::getStartedAt)
                .last("LIMIT 20"));
        List<String> sessionIds = sessions.stream().map(Session::getId).toList();
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        List<SessionReport> reports = reportMapper.selectList(new LambdaQueryWrapper<SessionReport>()
                .in(SessionReport::getSessionId, sessionIds));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SessionReport report : reports) {
            for (String weakness : parseJsonList(report.getWeaknesses())) {
                counts.merge(weakness, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(5)
                .toList();
    }

    private PeriodRange resolvePeriodRange(String period, LocalDate today) {
        return switch (period) {
            case "DAILY" -> new PeriodRange(today, today);
            case "MONTHLY" -> new PeriodRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
            default -> {
                LocalDate start = today.with(DayOfWeek.MONDAY);
                yield new PeriodRange(start, start.plusDays(6));
            }
        };
    }

    private List<String> parseJsonList(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of(json);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize report detail.", e);
        }
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record PeriodRange(LocalDate start, LocalDate end) {
    }

    private record ReportStats(
            BigDecimal overallScore,
            BigDecimal pronunciationScore,
            BigDecimal fluencyScore,
            BigDecimal grammarScore,
            BigDecimal vocabularyScore,
            BigDecimal comprehensionScore,
            int totalTurns,
            int totalWords,
            int uniqueWords,
            BigDecimal avgSentenceLength,
            List<String> strengths,
            List<String> weaknesses,
            List<String> grammarDetails,
            List<String> pronunciationDetails,
            List<String> vocabularySuggestions
    ) {
    }
}
