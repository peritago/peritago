package com.skala.domainbridge.glossary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GlossaryRequestDto(
        @NotBlank @Size(max = 50) String term,
        @NotBlank String officialDefinition
) {}
