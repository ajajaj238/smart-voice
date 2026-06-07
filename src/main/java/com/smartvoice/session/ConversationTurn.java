package com.smartvoice.session;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@TableName("conversation_turns")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConversationTurn {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String sessionId;
    private Integer turnIndex;
    private String userText;
    private String aiText;
    private String userAudioUrl;
    private String aiAudioUrl;
    private BigDecimal asrConfidence;
    private Integer asrDurationMs;
    private BigDecimal pronunciationScore;
    private BigDecimal fluencyScore;
    private String grammarIssues;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
