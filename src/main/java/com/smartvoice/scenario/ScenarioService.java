package com.smartvoice.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartvoice.scenario.dto.ScenarioDto;
import com.smartvoice.shared.enums.EnglishLevel;
import com.smartvoice.shared.enums.ScenarioCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScenarioService {

    private static final Map<String, List<String>> USER_PERSPECTIVE_PROMPTS = Map.of(
            "sc001", List.of(
                    "Sure. I am a software engineer with experience building backend services and solving production problems.",
                    "One technical challenge I handled was improving API performance under heavy traffic.",
                    "When I disagree with teammates, I try to understand their reasoning first and compare options with data."
            ),
            "sc002", List.of(
                    "I am interested in this role because it matches my skills and long-term career goals.",
                    "In the next five years, I hope to grow into a stronger professional and take on more responsibility.",
                    "In my last project, I helped coordinate the team and kept everyone aligned on the deadline."
            )
    );

    private final ScenarioMapper scenarioMapper;

    public List<ScenarioDto> getByCategory(ScenarioCategory category) {
        var wrapper = new LambdaQueryWrapper<Scenario>();
        wrapper.eq(Scenario::getCategory, category).eq(Scenario::getIsActive, true);
        return scenarioMapper.selectList(wrapper).stream().map(this::toDto).toList();
    }

    public List<ScenarioDto> getAllActive() {
        var wrapper = new LambdaQueryWrapper<Scenario>();
        wrapper.eq(Scenario::getIsActive, true);
        return scenarioMapper.selectList(wrapper).stream().map(this::toDto).toList();
    }

    public ScenarioDto getById(String id) {
        Scenario s = scenarioMapper.selectById(id);
        if (s == null) throw new RuntimeException("Scenario not found");
        return toDto(s);
    }

    public String buildSystemPrompt(Scenario scenario, EnglishLevel level) {
        String levelGuidance = switch (level) {
            case BEGINNER -> "Speak slowly and use simple vocabulary. Be patient and encouraging.";
            case INTERMEDIATE -> "Speak at a normal pace with everyday vocabulary.";
            case ADVANCED -> "Speak naturally with idiomatic expressions. Challenge the user.";
        };
        return scenario.getAiRole() + "\n\nLanguage level: " + levelGuidance;
    }

    private ScenarioDto toDto(Scenario s) {
        return ScenarioDto.builder()
                .id(s.getId()).category(s.getCategory().name())
                .title(s.getTitle()).titleCn(s.getTitleCn())
                .description(s.getDescription())
                .aiRole(s.getAiRole()).userRole(s.getUserRole())
                .suggestedPrompts(resolveUserPerspectivePrompts(s))
                .difficulty(s.getDifficulty().name()).iconUrl(s.getIconUrl())
                .build();
    }

    private List<String> resolveUserPerspectivePrompts(Scenario scenario) {
        List<String> prompts = scenario.getSuggestedPrompts();
        if (prompts == null || prompts.isEmpty()) {
            return USER_PERSPECTIVE_PROMPTS.getOrDefault(scenario.getId(), prompts);
        }
        if (scenario.getCategory() == ScenarioCategory.INTERVIEW && prompts.stream().anyMatch(this::looksLikeInterviewerPrompt)) {
            return USER_PERSPECTIVE_PROMPTS.getOrDefault(scenario.getId(), prompts);
        }
        return prompts;
    }

    private boolean looksLikeInterviewerPrompt(String prompt) {
        if (prompt == null) {
            return false;
        }
        String normalized = prompt.toLowerCase();
        return normalized.contains("tell me about")
                || normalized.startsWith("why do you")
                || normalized.startsWith("where do you")
                || normalized.startsWith("what is your")
                || normalized.startsWith("how do you");
    }
}
