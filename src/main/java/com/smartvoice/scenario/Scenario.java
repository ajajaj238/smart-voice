package com.smartvoice.scenario;

import com.baomidou.mybatisplus.annotation.*;
import com.smartvoice.shared.enums.EnglishLevel;
import com.smartvoice.shared.enums.ScenarioCategory;
import com.smartvoice.shared.handler.StringListTypeHandler;
import lombok.*;
import java.time.Instant;
import java.util.List;

@TableName(value = "scenarios", autoResultMap = true)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Scenario {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private ScenarioCategory category;
    private String title;
    private String titleCn;
    private String description;
    private String aiRole;
    private String userRole;

    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> suggestedPrompts;

    private EnglishLevel difficulty;
    private String iconUrl;
    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
