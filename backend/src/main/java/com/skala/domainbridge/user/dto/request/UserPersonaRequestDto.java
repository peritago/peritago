package com.skala.domainbridge.user.dto.request;

import com.skala.domainbridge.user.entity.ExplanationLength;

import java.util.List;

public record UserPersonaRequestDto(
        List<String> domainTags,
        String personaDescription,
        ExplanationLength officialDefLength,
        ExplanationLength personalizedExpLength
) {}