package com.smartvoice.session;

import com.baomidou.mybatisplus.annotation.*;
import com.smartvoice.shared.enums.EnglishLevel;
import com.smartvoice.shared.enums.SessionStatus;
import lombok.*;
import java.time.Instant;

@TableName("sessions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Session {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String scenarioId;
    private SessionStatus status;
    private EnglishLevel difficulty;

    @TableField(fill = FieldFill.INSERT)
    private Instant startedAt;

    private Instant endedAt;
    private Integer durationSec;
    private String metadata;
}
