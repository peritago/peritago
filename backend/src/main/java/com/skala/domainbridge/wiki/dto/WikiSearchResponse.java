package com.skala.domainbridge.wiki.dto;

import java.util.List;

/**
 * translate 도메인이 그대로 의존하는 응답 스키마 — 절대 깨면 안 됨 (WIKI_SPEC_1.md "인터페이스 계약").
 */
public record WikiSearchResponse(boolean found, List<Chunk> chunks) {

	public static WikiSearchResponse notFound() {
		return new WikiSearchResponse(false, List.of());
	}
}
