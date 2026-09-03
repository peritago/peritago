package com.skala.domainbridge.glossary.dto.response;

import com.skala.domainbridge.glossary.entity.Glossary;

import java.time.LocalDateTime;

public record GlossaryResponseDto(
        Long id,
        String term,
        String officialDefinition,
        LocalDateTime createdAt
) {
    public static GlossaryResponseDto from(Glossary glossary) {
        return new GlossaryResponseDto(
                glossary.getId(), glossary.getTerm(), glossary.getOfficialDefinition(), glossary.getCreatedAt());
    }
}
