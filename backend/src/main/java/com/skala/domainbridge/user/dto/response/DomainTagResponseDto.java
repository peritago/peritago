package com.skala.domainbridge.user.dto.response;

import com.skala.domainbridge.user.entity.DomainTag;

public record DomainTagResponseDto(
        Long id,
        String name
) {
    public static DomainTagResponseDto from(DomainTag domainTag) {
        return new DomainTagResponseDto(domainTag.getId(), domainTag.getName());
    }
}
