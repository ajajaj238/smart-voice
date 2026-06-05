package com.smartvoice.report;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@TableName("session_reports")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SessionReport {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String sessionId;
    private BigDecimal overallScore;
    private BigDecimal pronunciationScore;
    private BigDecimal fluencyScore;
    private BigDecimal grammarScore;
    private BigDecimal vocabularyScore;
    private BigDecimal comprehensionScore;
    private Integer totalTurns;
    private Integer totalWords;
    private Integer uniqueWords;
    private BigDecimal avgSentenceLength;
    private String strengths;
    private String weaknesses;
    private String grammarDetail;
    private String pronunciationDetail;
    private String vocabularySuggestions;
    private String teacherComment;
    private BigDecimal previousAvgScore;
    private BigDecimal scoreChange;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
