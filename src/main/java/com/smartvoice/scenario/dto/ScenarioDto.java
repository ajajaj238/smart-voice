package com.smartvoice.scenario.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class ScenarioDto {
    private String id;
    private String category;
    private String title;
    private String titleCn;
    private String description;
    private String aiRole;
    private String userRole;
    private List<String> suggestedPrompts;
    private String difficulty;
    private String iconUrl;
}
