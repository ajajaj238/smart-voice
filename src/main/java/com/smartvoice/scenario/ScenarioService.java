package com.smartvoice.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartvoice.scenario.dto.ScenarioDto;
import com.smartvoice.shared.enums.EnglishLevel;
import com.smartvoice.shared.enums.ScenarioCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScenarioService {

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
                .suggestedPrompts(s.getSuggestedPrompts())
                .difficulty(s.getDifficulty().name()).iconUrl(s.getIconUrl())
                .build();
    }
}
