package com.smartvoice.user.dto;

import com.smartvoice.user.User;

public record UserProfileResponse(
        String id,
        String username,
        String email,
        String englishLevel,
        String avatarUrl
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getEnglishLevel().name(),
                user.getAvatarUrl()
        );
    }
}
