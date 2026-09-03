package com.skala.domainbridge.common.response;

public record ApiResponse<T>(
        int status,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "SUCCESS", data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "CREATED", data);
    }

    public static ApiResponse<Void> noContent() {
        return new ApiResponse<>(204, "NO_CONTENT", null);
    }
}