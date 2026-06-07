package com.smartvoice.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 100)
    private String email;

    private String englishLevel;

    @Size(max = 500)
    private String avatarUrl;
}
