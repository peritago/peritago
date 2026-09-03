package com.skala.domainbridge.wiki.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * industry: 반도체/통신/자동차 등 산업 분류. 타 도메인 비유 설명에서 산업별로 검색을 좁히는 데 쓰인다
 * (WikiController의 GET /api/wiki/search?industry= 참고).
 */
public record WikiUploadRequest(
		@NotBlank(message = "title은 비어 있을 수 없습니다.") String title,
		@NotBlank(message = "content는 비어 있을 수 없습니다.") String content,
		@NotBlank(message = "industry는 비어 있을 수 없습니다.") String industry,
		String sourceUrl
) {
}
