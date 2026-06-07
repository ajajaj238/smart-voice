package com.smartvoice.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartvoice.scenario.Scenario;
import com.smartvoice.scenario.ScenarioMapper;
import com.smartvoice.session.ConversationTurn;
import com.smartvoice.session.ConversationTurnMapper;
import com.smartvoice.session.Session;
import com.smartvoice.session.SessionMapper;
import com.smartvoice.shared.enums.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_RECENT_CONTEXT_TURNS = 8;

    private final SessionMapper sessionMapper;
    private final ScenarioMapper scenarioMapper;
    private final ConversationTurnMapper turnMapper;
    private final ChatClient chatClient;

    private List<Message> buildMessages(String sessionId, String userText, String userId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) throw new RuntimeException("Session not found");
        if (userId != null && !session.getUserId().equals(userId)) {
            throw new SecurityException("You do not have permission to access this session.");
        }
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new RuntimeException("Session already ended");
        }

        Scenario scenario = scenarioMapper.selectById(session.getScenarioId());
        if (scenario == null) throw new RuntimeException("Scenario not found");

        String systemPrompt = buildSystemPrompt(scenario, session.getDifficulty().name());

        var wrapper = new LambdaQueryWrapper<ConversationTurn>();
        wrapper.eq(ConversationTurn::getSessionId, sessionId)
               .orderByAsc(ConversationTurn::getTurnIndex);
        List<ConversationTurn> previousTurns = turnMapper.selectList(wrapper);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        List<ConversationTurn> contextTurns = previousTurns;
        if (previousTurns.size() > MAX_RECENT_CONTEXT_TURNS) {
            messages.add(new SystemMessage(buildConversationSummary(previousTurns.subList(
                    0,
                    previousTurns.size() - MAX_RECENT_CONTEXT_TURNS
            ))));
            contextTurns = previousTurns.subList(previousTurns.size() - MAX_RECENT_CONTEXT_TURNS, previousTurns.size());
        }
        for (ConversationTurn turn : contextTurns) {
            if (turn.getUserText() != null) {
                messages.add(new UserMessage(turn.getUserText()));
            }
            if (turn.getAiText() != null) {
                messages.add(new AssistantMessage(turn.getAiText()));
            }
        }
        messages.add(new UserMessage(userText));

        return messages;
    }

    @Transactional
    public ConversationTurn processMessage(String sessionId, String userText) {
        return processMessage(sessionId, userText, null);
    }

    @Transactional
    public ConversationTurn processMessage(String sessionId, String userText, String userId) {
        List<Message> messages = buildMessages(sessionId, userText, userId);

        String aiResponse = chatClient.prompt()
                .messages(messages)
                .call()
                .content();

        int turnIndex = turnMapper.selectCount(
                new LambdaQueryWrapper<ConversationTurn>().eq(ConversationTurn::getSessionId, sessionId))
                .intValue();

        ConversationTurn turn = ConversationTurn.builder()
                .sessionId(sessionId)
                .turnIndex(turnIndex)
                .userText(userText)
                .aiText(aiResponse)
                .build();
        turnMapper.insert(turn);

        return turn;
    }

    @Transactional
    public ConversationTurn saveTurn(String sessionId, String userText, String aiText) {
        int turnIndex = turnMapper.selectCount(
                new LambdaQueryWrapper<ConversationTurn>().eq(ConversationTurn::getSessionId, sessionId))
                .intValue();

        ConversationTurn turn = ConversationTurn.builder()
                .sessionId(sessionId)
                .turnIndex(turnIndex)
                .userText(userText)
                .aiText(aiText)
                .build();
        turnMapper.insert(turn);
        return turn;
    }

    @Transactional
    public Session endSession(String sessionId) {
        return endSession(sessionId, null);
    }

    @Transactional
    public Session endSession(String sessionId, String userId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) throw new RuntimeException("Session not found");
        if (userId != null && !session.getUserId().equals(userId)) {
            throw new SecurityException("You do not have permission to access this session.");
        }
        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(Instant.now());
        session.setDurationSec((int) Duration.between(session.getStartedAt(), Instant.now()).getSeconds());
        sessionMapper.updateById(session);
        return session;
    }

    private String buildSystemPrompt(Scenario scenario, String level) {
        String levelGuidance = switch (level) {
            case "BEGINNER" -> "Use simple vocabulary and short sentences. Be patient and encouraging.";
            case "ADVANCED" -> "Speak naturally with idiomatic expressions. Challenge the user appropriately.";
            default -> "Speak at a normal pace with everyday vocabulary. Correct major errors gently.";
        };
        return scenario.getAiRole() + "\n\n" +
               "You are roleplaying as described above. Stay in character at all times.\n" +
               "Language level adjustment: " + levelGuidance + "\n" +
               "Keep your responses concise (1-3 sentences). When the user makes a grammar or expression error, " +
               "gently incorporate the correct form in your response rather than explicitly pointing it out.";
    }

    private String buildConversationSummary(List<ConversationTurn> olderTurns) {
        StringBuilder summary = new StringBuilder("Earlier conversation summary for continuity:\n");
        for (ConversationTurn turn : olderTurns) {
            if (turn.getUserText() != null && !turn.getUserText().isBlank()) {
                summary.append("- User: ").append(shorten(turn.getUserText())).append('\n');
            }
            if (turn.getAiText() != null && !turn.getAiText().isBlank()) {
                summary.append("- AI: ").append(shorten(turn.getAiText())).append('\n');
            }
        }
        summary.append("Continue naturally from the latest turns and do not repeat old questions.");
        return summary.toString();
    }

    private String shorten(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 140 ? normalized : normalized.substring(0, 140) + "...";
    }
}
