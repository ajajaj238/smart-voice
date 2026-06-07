package com.smartvoice.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartvoice.scenario.Scenario;
import com.smartvoice.scenario.ScenarioMapper;
import com.smartvoice.session.dto.ConversationTurnResponse;
import com.smartvoice.session.dto.CreateSessionRequest;
import com.smartvoice.session.dto.SessionDetailResponse;
import com.smartvoice.shared.enums.EnglishLevel;
import com.smartvoice.shared.enums.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionMapper sessionMapper;
    private final ScenarioMapper scenarioMapper;
    private final ConversationTurnMapper turnMapper;

    @Transactional
    public Session create(String userId, CreateSessionRequest request) {
        Scenario scenario = scenarioMapper.selectById(request.getScenarioId());
        if (scenario == null) throw new RuntimeException("Scenario not found");

        EnglishLevel difficulty = request.getDifficulty() != null
                ? EnglishLevel.valueOf(request.getDifficulty().toUpperCase())
                : scenario.getDifficulty();

        Session session = Session.builder()
                .userId(userId).scenarioId(scenario.getId())
                .difficulty(difficulty).build();
        sessionMapper.insert(session);
        return session;
    }

    public Session getById(String id) {
        Session session = sessionMapper.selectById(id);
        if (session == null) throw new RuntimeException("Session not found");
        return session;
    }

    public Session getOwnedById(String userId, String id) {
        Session session = getById(id);
        if (!session.getUserId().equals(userId)) {
            throw new SecurityException("You do not have permission to access this session.");
        }
        return session;
    }

    public SessionDetailResponse getDetail(String userId, String id) {
        Session session = getOwnedById(userId, id);
        var wrapper = new LambdaQueryWrapper<ConversationTurn>();
        wrapper.eq(ConversationTurn::getSessionId, id).orderByAsc(ConversationTurn::getTurnIndex);
        List<ConversationTurnResponse> turns = turnMapper.selectList(wrapper).stream()
                .map(ConversationTurnResponse::from)
                .toList();
        return SessionDetailResponse.from(session, turns);
    }

    public IPage<Session> listByUser(String userId, int page, int size) {
        var wrapper = new LambdaQueryWrapper<Session>();
        wrapper.eq(Session::getUserId, userId).orderByDesc(Session::getStartedAt);
        return sessionMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Transactional
    public Session end(String userId, String id) {
        Session session = getOwnedById(userId, id);
        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(Instant.now());
        session.setDurationSec((int) Duration.between(session.getStartedAt(), Instant.now()).getSeconds());
        sessionMapper.updateById(session);
        return session;
    }
}
