package com.smartvoice.report;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@TableName("progress_snapshots")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ProgressSnapshot {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String period;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer sessionCount;
    private BigDecimal avgOverall;
    private BigDecimal avgPronunciation;
    private BigDecimal avgFluency;
    private BigDecimal avgGrammar;
    private BigDecimal avgVocabulary;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
