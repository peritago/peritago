package com.skala.domainbridge.user.dto.response;

import com.skala.domainbridge.user.entity.ExplanationLength;

import java.util.List;

public record UserPersonaResponseDto(
        boolean exists,          // 첫 채팅 전이면 false → 프론트에서 페르소나 생성 유도
        List<String> domainTags,
        String personaDescription,
        ExplanationLength officialDefLength,
        ExplanationLength personalizedExpLength
) {}