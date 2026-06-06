package com.smartvoice.common.response;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK", Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now().toString());
    }
}
