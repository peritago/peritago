package com.skala.domainbridge.wiki.service;

import com.skala.domainbridge.wiki.dto.AnalogySearchResult;
import com.skala.domainbridge.wiki.dto.Chunk;
import com.skala.domainbridge.wiki.dto.WikiSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 순수 Mockito 단위 테스트 — Spring 컨텍스트/DB/OPENAI_API_KEY 없이도 ①②③ 오케스트레이션
 * 로직(1차 검색 → 프롬프트 구성 → 2차 검색)이 맞게 연결되는지 검증한다. 실제 LLM이 만드는
 * 구조 서술의 "품질"은 이 테스트로 확인할 수 없다 — 그건 실키가 있어야 가능하다.
 */
class AnalogySearchServiceTest {

	private final WikiService wikiService = mock(WikiService.class);
	private final ChatClient chatClient = mock(ChatClient.class);
	private final AnalogySearchService service = new AnalogySearchService(wikiService, chatClient);

	@Test
	void search_found_generatesStructuralDescriptionAndRunsSecondSearch() {
		WikiSearchResponse targetResult = new WikiSearchResponse(true, List.of(
				new Chunk("포토공정은 감광→노광→현상 단계를 거쳐 회로 패턴을 새기는 공정입니다.",
						0.9, "https://wiki.internal/포토공정")
		));
		// 1차 검색은 산업으로 안 좁힌다 — translate가 타겟이 어느 산업 얘기인지 모르기 때문.
		when(wikiService.search("포토공정", null)).thenReturn(targetResult);

		ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content())
				.thenReturn("여러 단계를 순차적으로 거치며 이전 단계 결과물에 의존해 정제하는 과정");

		WikiSearchResponse analogyResult = new WikiSearchResponse(true, List.of(
				new Chunk("배열 순회는 인덱스를 하나씩 증가시키며 이전 원소 처리 결과를 기반으로...",
						0.0, "https://wiki.internal/배열순회")
		));
		// 2차 검색은 키워드 leg 없는 벡터 전용 메서드로 나간다.
		when(wikiService.searchVectorOnly(
				eq("여러 단계를 순차적으로 거치며 이전 단계 결과물에 의존해 정제하는 과정"),
				eq("개발")
		)).thenReturn(analogyResult);

		AnalogySearchResult result = service.search("포토공정", "개발");

		assertThat(result.firstSearchResult()).isEqualTo(targetResult);
		assertThat(result.llmResult()).contains("순차적");
		assertThat(result.secondSearchResult()).isEqualTo(analogyResult);

		// LLM 프롬프트에 1차 검색 결과(그라운딩된 사실)가 실제로 포함됐는지 — 근거 없이 지어내지 않았는지 확인
		verify(requestSpec).user(contains("포토공정은 감광"));
	}

	@Test
	void search_targetNotFound_skipsLlmAndSecondSearch() {
		when(wikiService.search("아무도모르는용어", null)).thenReturn(WikiSearchResponse.notFound());

		AnalogySearchResult result = service.search("아무도모르는용어", "개발");

		assertThat(result.firstSearchResult().found()).isFalse();
		assertThat(result.llmResult()).isNull();
		assertThat(result.secondSearchResult().found()).isFalse();

		verify(chatClient, never()).prompt();
		verify(wikiService, times(1)).search(anyString(), any());
		verify(wikiService, never()).searchVectorOnly(anyString(), any());
	}
}
