package com.smartvoice.correction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartvoice.correction.dto.CorrectionItem;
import com.smartvoice.correction.dto.CorrectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectionService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public CorrectionResult correct(String text, String scenario, String level) {
        if (text == null || text.isBlank()) {
            return new CorrectionResult("", "", 0, 0, List.of(), List.of(),
                    "No text was provided for correction.");
        }

        try {
            String response = chatClient.prompt()
                    .system(buildSystemPrompt(scenario, level))
                    .user(text)
                    .call()
                    .content();
            CorrectionResult result = parseLlmResult(text, response);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.warn("LLM correction failed, fallback to local rules. text={}", text, e);
        }

        return fallbackCorrect(text);
    }

    private String buildSystemPrompt(String scenario, String level) {
        return """
                You are an English speaking coach. Correct the user's spoken English for grammar,
                vocabulary choice, natural expression, and scenario appropriateness.
                Scenario: %s
                Learner level: %s

                Return strict JSON only, with this schema:
                {
                  "correctedText": "string",
                  "grammarScore": 0-100,
                  "expressionScore": 0-100,
                  "corrections": [
                    {
                      "type": "grammar|vocabulary|expression|logic",
                      "original": "string",
                      "corrected": "string",
                      "explanation": "string",
                      "severity": "low|medium|high"
                    }
                  ],
                  "betterExpressions": ["string"],
                  "overallFeedback": "string"
                }
                Keep explanations concise and useful for a Chinese English learner.
                """.formatted(defaultText(scenario, "general conversation"), defaultText(level, "intermediate"));
    }

    private CorrectionResult parseLlmResult(String originalText, String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        try {
            String json = extractJson(rawResponse);
            JsonNode root = objectMapper.readTree(json);
            List<CorrectionItem> corrections = new ArrayList<>();
            JsonNode correctionNodes = root.path("corrections");
            if (correctionNodes.isArray()) {
                for (JsonNode node : correctionNodes) {
                    corrections.add(new CorrectionItem(
                            textValue(node, "type", "expression"),
                            textValue(node, "original", ""),
                            textValue(node, "corrected", ""),
                            textValue(node, "explanation", ""),
                            textValue(node, "severity", "low")
                    ));
                }
            }

            List<String> betterExpressions = new ArrayList<>();
            JsonNode expressionNodes = root.path("betterExpressions");
            if (expressionNodes.isArray()) {
                for (JsonNode node : expressionNodes) {
                    if (node.isTextual()) {
                        betterExpressions.add(node.asText());
                    }
                }
            }

            return new CorrectionResult(
                    originalText,
                    textValue(root, "correctedText", originalText),
                    root.path("grammarScore").asDouble(calculateScore(corrections, "grammar")),
                    root.path("expressionScore").asDouble(calculateScore(corrections, "expression")),
                    corrections,
                    betterExpressions,
                    textValue(root, "overallFeedback", "Good attempt. Review the suggested expressions and try again.")
            );
        } catch (Exception e) {
            log.warn("Failed to parse LLM correction JSON. response={}", rawResponse, e);
            return null;
        }
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private CorrectionResult fallbackCorrect(String text) {
        String corrected = text.trim();
        List<CorrectionItem> corrections = new ArrayList<>();
        List<String> betterExpressions = new ArrayList<>();

        corrected = replace(corrected, corrections, "three year", "three years",
                "grammar", "Use a plural noun after a number greater than one.", "medium");
        corrected = replace(corrected, corrections, "two year", "two years",
                "grammar", "Use a plural noun after a number greater than one.", "medium");
        corrected = replace(corrected, corrections, "I has", "I have",
                "grammar", "Use 'have' with the subject 'I'.", "high");
        corrected = replace(corrected, corrections, "I am work", "I work",
                "grammar", "Use the base verb after the subject for a simple present statement.", "high");
        corrected = replace(corrected, corrections, "want order", "would like to order",
                "expression", "This sounds more polite and natural in ordering scenarios.", "medium");
        corrected = replace(corrected, corrections, "I want a coffee", "I would like a coffee",
                "expression", "This is a more polite way to order.", "low");

        if (text.toLowerCase(Locale.ROOT).contains("interview")) {
            betterExpressions.add("I would be happy to walk you through my experience.");
        }
        if (text.toLowerCase(Locale.ROOT).contains("coffee") || text.toLowerCase(Locale.ROOT).contains("order")) {
            betterExpressions.add("Could I get a medium latte, please?");
        }
        if (betterExpressions.isEmpty()) {
            betterExpressions.add("A more natural version: " + corrected);
        }

        double grammarScore = calculateScore(corrections, "grammar");
        double expressionScore = calculateScore(corrections, "expression");
        String feedback = corrections.isEmpty()
                ? "Your sentence is clear. Try adding more specific details to sound more natural."
                : "Review the corrections and repeat the improved sentence aloud.";

        return new CorrectionResult(text, corrected, grammarScore, expressionScore, corrections, betterExpressions, feedback);
    }

    private String replace(String text, List<CorrectionItem> corrections, String original, String corrected,
                           String type, String explanation, String severity) {
        if (text.toLowerCase(Locale.ROOT).contains(original.toLowerCase(Locale.ROOT))) {
            corrections.add(new CorrectionItem(type, original, corrected, explanation, severity));
            return text.replaceAll("(?i)" + java.util.regex.Pattern.quote(original), corrected);
        }
        return text;
    }

    private double calculateScore(List<CorrectionItem> corrections, String type) {
        double score = 92;
        for (CorrectionItem correction : corrections) {
            if (type.equals(correction.type())) {
                score -= switch (correction.severity()) {
                    case "high" -> 18;
                    case "medium" -> 10;
                    default -> 5;
                };
            }
        }
        return Math.max(0, Math.min(100, score));
    }

    private String textValue(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : defaultValue;
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
