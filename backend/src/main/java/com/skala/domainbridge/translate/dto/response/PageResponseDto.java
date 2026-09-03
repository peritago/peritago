package com.skala.domainbridge.translate.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이징 응답 봉투.
 *
 * Spring의 Page를 그대로 직렬화하면 JSON 구조가 버전에 따라 흔들리므로 필요한 필드만 고정해 내려준다.
 * 다른 도메인에서도 페이징이 필요해지면 common/response 로 승격시키면 된다.
 */
public record PageResponseDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageResponseDto<T> of(List<T> content, Page<?> page) {
        return new PageResponseDto<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
