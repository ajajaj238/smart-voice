package com.smartvoice.pronunciation;

import com.smartvoice.pronunciation.dto.PronunciationIssue;
import com.smartvoice.pronunciation.dto.PronunciationResult;
import com.smartvoice.pronunciation.dto.PronunciationWordScore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PronunciationService {

    private static final Set<String> DIFFICULT_WORDS = Set.of(
            "through", "three", "think", "this", "that", "world", "girl", "work",
            "experience", "interview", "comfortable", "restaurant", "schedule"
    );

    public PronunciationResult evaluate(String text, String referenceText, Integer durationMs) {
        String normalizedText = normalize(text);
        if (normalizedText.isBlank()) {
            return emptyResult();
        }

        List<String> words = tokenize(normalizedText);
        List<String> referenceWords = tokenize(normalize(referenceText));
        double similarityScore = referenceWords.isEmpty() ? 86.0 : calculateSimilarityScore(words, referenceWords);
        double paceWpm = calculateWordsPerMinute(words.size(), durationMs);
        double paceScore = calculatePaceScore(paceWpm);
        double fluencyScore = calculateFluencyScore(normalizedText, words.size(), paceScore);
        double pronunciationScore = clamp((similarityScore * 0.65) + (wordDifficultyScore(words) * 0.35));
        double overallScore = clamp((pronunciationScore * 0.45) + (fluencyScore * 0.35) + (paceScore * 0.20));

        List<PronunciationWordScore> wordScores = buildWordScores(words, referenceWords);
        List<PronunciationIssue> issues = buildIssues(words, paceWpm, normalizedText, referenceWords);
        List<String> suggestions = buildSuggestions(issues, paceWpm);

        return new PronunciationResult(
                round(pronunciationScore),
                round(fluencyScore),
                round(paceScore),
                round(overallScore),
                words.size(),
                round(paceWpm),
                wordScores,
                issues,
                suggestions
        );
    }

    private PronunciationResult emptyResult() {
        return new PronunciationResult(0, 0, 0, 0, 0, 0, List.of(),
                List.of(new PronunciationIssue("EMPTY_TEXT", "", "No speech text was recognized.")),
                List.of("Please speak again or provide transcriptHint for API testing."));
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-zA-Z']+"))
                .filter(word -> !word.isBlank())
                .toList();
    }

    private double calculateSimilarityScore(List<String> words, List<String> referenceWords) {
        if (words.isEmpty()) {
            return 0;
        }
        int matched = 0;
        int limit = Math.min(words.size(), referenceWords.size());
        for (int i = 0; i < limit; i++) {
            if (words.get(i).equals(referenceWords.get(i))) {
                matched++;
            }
        }
        double lengthPenalty = Math.abs(words.size() - referenceWords.size()) * 2.5;
        return clamp(70 + (matched * 30.0 / Math.max(referenceWords.size(), 1)) - lengthPenalty);
    }

    private double calculateWordsPerMinute(int wordCount, Integer durationMs) {
        if (durationMs == null || durationMs <= 0) {
            return Math.min(170, Math.max(90, wordCount * 18.0));
        }
        return wordCount / (durationMs / 60000.0);
    }

    private double calculatePaceScore(double wordsPerMinute) {
        if (wordsPerMinute <= 0) {
            return 0;
        }
        double ideal = 135.0;
        double distance = Math.abs(wordsPerMinute - ideal);
        return clamp(100 - distance * 0.65);
    }

    private double calculateFluencyScore(String text, int wordCount, double paceScore) {
        long fillerCount = Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(word -> Set.of("um", "uh", "er", "ah").contains(word))
                .count();
        double fillerPenalty = fillerCount * 7.5;
        double lengthBonus = Math.min(8, wordCount * 0.4);
        return clamp(78 + lengthBonus + (paceScore - 75) * 0.25 - fillerPenalty);
    }

    private double wordDifficultyScore(List<String> words) {
        if (words.isEmpty()) {
            return 0;
        }
        double score = 88;
        for (String word : words) {
            if (DIFFICULT_WORDS.contains(word)) {
                score -= 2.8;
            }
            if (word.contains("th") || word.contains("r")) {
                score -= 0.8;
            }
        }
        return clamp(score);
    }

    private List<PronunciationWordScore> buildWordScores(List<String> words, List<String> referenceWords) {
        List<PronunciationWordScore> scores = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            double score = 88;
            String feedback = "Good";
            if (i < referenceWords.size() && !word.equals(referenceWords.get(i))) {
                score -= 12;
                feedback = "Check this word against the reference text.";
            }
            if (DIFFICULT_WORDS.contains(word)) {
                score -= 6;
                feedback = "Practice the stress and key sounds in this word.";
            }
            scores.add(new PronunciationWordScore(word, round(clamp(score)), feedback));
        }
        return scores;
    }

    private List<PronunciationIssue> buildIssues(List<String> words, double paceWpm, String text, List<String> referenceWords) {
        List<PronunciationIssue> issues = new ArrayList<>();
        for (String word : words) {
            if (word.contains("th")) {
                issues.add(new PronunciationIssue("PHONEME", word, "Pay attention to the /th/ sound."));
            } else if (word.contains("r")) {
                issues.add(new PronunciationIssue("PHONEME", word, "Make the /r/ sound clearer and more stable."));
            }
        }
        if (paceWpm > 175) {
            issues.add(new PronunciationIssue("PACE", "speech_rate", "The speech rate is fast; slow down slightly for clarity."));
        } else if (paceWpm > 0 && paceWpm < 90) {
            issues.add(new PronunciationIssue("PACE", "speech_rate", "The speech rate is slow; try speaking in fuller phrases."));
        }
        if (text.toLowerCase(Locale.ROOT).matches(".*\\b(um|uh|er|ah)\\b.*")) {
            issues.add(new PronunciationIssue("FLUENCY", "filler_words", "Reduce filler words and pause silently instead."));
        }
        if (!referenceWords.isEmpty() && Math.abs(words.size() - referenceWords.size()) >= 3) {
            issues.add(new PronunciationIssue("COMPLETENESS", "reference_text", "The spoken content differs from the reference length."));
        }
        return issues.stream().limit(8).toList();
    }

    private List<String> buildSuggestions(List<PronunciationIssue> issues, double paceWpm) {
        List<String> suggestions = new ArrayList<>();
        if (issues.stream().anyMatch(issue -> "PHONEME".equals(issue.type()))) {
            suggestions.add("Repeat the highlighted words slowly, then speak the whole sentence again.");
        }
        if (paceWpm > 175) {
            suggestions.add("Add short pauses after commas or phrase boundaries.");
        } else if (paceWpm > 0 && paceWpm < 90) {
            suggestions.add("Practice shadowing one full sentence at a natural pace.");
        }
        if (issues.stream().anyMatch(issue -> "FLUENCY".equals(issue.type()))) {
            suggestions.add("Replace filler words with a short silent pause.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Your delivery is stable. Try a longer answer with more natural intonation.");
        }
        return suggestions;
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
