package com.smartvoice.user;

import com.baomidou.mybatisplus.annotation.*;
import com.smartvoice.shared.enums.EnglishLevel;
import lombok.*;
import java.time.Instant;

@TableName("users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String username;
    private String passwordHash;
    private String email;
    @Builder.Default
    private EnglishLevel englishLevel = EnglishLevel.INTERMEDIATE;
    private String avatarUrl;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
