package com.skala.domainbridge.wiki.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WikiService가 등록 시 사용하는 청킹 로직 자체(TokenTextSplitter)는 순수 라이브러리 호출이라
 * 네트워크/DB 없이도 검증 가능하다 (WIKI_SPEC_1.md 7장 "청크 크기 조정" 관련 회귀 방지용).
 */
class TokenTextSplitterChunkingTest {

	private final TokenTextSplitter splitter = new TokenTextSplitter();

	@Test
	void shortDocument_producesAtLeastOneChunkAndKeepsMetadata() {
		Document source = new Document(
				"EDS 검사는 반도체 공정에서 개별 칩의 전기적 특성을 검사하는 공정입니다.",
				Map.of("title", "EDS 검사", "sourceUrl", "https://wiki.internal/EDS검사")
		);

		List<Document> chunks = splitter.apply(List.of(source));

		assertThat(chunks).isNotEmpty();
		assertThat(chunks).allSatisfy(chunk -> {
			assertThat(chunk.getMetadata()).containsEntry("title", "EDS 검사");
			assertThat(chunk.getText()).isNotBlank();
		});
	}

	@Test
	void longDocument_splitsIntoMultipleChunks() {
		String longContent = "EDS 검사 관련 설명 문단입니다. ".repeat(400);
		Document source = new Document(longContent, Map.of("title", "EDS 검사", "sourceUrl", "https://wiki.internal/EDS검사"));

		List<Document> chunks = splitter.apply(List.of(source));

		assertThat(chunks.size()).isGreaterThan(1);
	}
}
