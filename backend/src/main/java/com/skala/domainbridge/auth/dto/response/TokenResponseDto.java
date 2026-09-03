package com.skala.domainbridge.auth.dto.response;

public record TokenResponseDto(
        String accessToken,
        String refreshToken
) {}