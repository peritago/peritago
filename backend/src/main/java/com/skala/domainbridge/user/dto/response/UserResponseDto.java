package com.skala.domainbridge.user.dto.response;

public record UserResponseDto(
        Long id,
        String email,
        String name
) {}