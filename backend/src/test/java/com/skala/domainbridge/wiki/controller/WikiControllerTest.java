package com.skala.domainbridge.wiki.controller;

import com.skala.domainbridge.auth.jwt.JwtTokenProvider;
import com.skala.domainbridge.auth.jwt.TokenService;
import com.skala.domainbridge.wiki.dto.AnalogySearchResult;
import com.skala.domainbridge.wiki.dto.Chunk;
import com.skala.domainbridge.wiki.dto.IndexResult;
import com.skala.domainbridge.wiki.dto.WikiSearchResponse;
import com.skala.domainbridge.wiki.service.AnalogySearchService;
import com.skala.domainbridge.wiki.service.WikiDocumentNotFoundException;
import com.skala.domainbridge.wiki.service.WikiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WIKI_SPEC_1.md "인터페이스 계약" 확인 — GET /api/wiki/search는 더 이상 "은어 조회"/"비유 검색"을
 * 구분하지 않고 항상 AnalogySearchResult(targetResult/structuralDescription/analogyResult) 3파트를
 * 반환한다 (2026-09-03 통합).
 */
// peritago에는 SecurityConfig(JWT 필터 체인)가 이미 있어 슬라이스 테스트에도 그대로 적용된다.
// 이 테스트는 컨트롤러 동작만 검증하므로 addFilters=false로 시큐리티 필터 적용을 건너뛴다
// (SecurityConfig 빈 자체는 컨텍스트에 남아있어 JwtAuthenticationFilter 생성엔 의존성 목이 필요).
@WebMvcTest(WikiController.class)
@AutoConfigureMockMvc(addFilters = false)
class WikiControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WikiService wikiService;

	@MockitoBean
	private AnalogySearchService analogySearchService;

	// SecurityConfig는 excludeFilters로 뺐지만, JwtAuthenticationFilter는 Filter 타입이라 @WebMvcTest
	// 슬라이스에 그대로 딸려 들어온다(스프링부트 기본 포함 대상). 생성자 의존성만 목으로 채워준다.
	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private TokenService tokenService;

	@Test
	void search_delegatesToServiceAndReturnsAllThreeParts() throws Exception {
		AnalogySearchResult result = new AnalogySearchResult(
				new WikiSearchResponse(true, List.of(
						new Chunk("포토공정은 감광→노광→현상 단계를 거칩니다.", 0.9, "https://wiki.internal/포토공정")
				)),
				"여러 단계를 순차적으로 거치며 이전 단계 결과물에 의존해 정제하는 과정",
				new WikiSearchResponse(true, List.of(
						new Chunk("배열 순회는 인덱스를 하나씩 증가시키며...", 0.0, "https://wiki.internal/배열순회")
				))
		);
		when(analogySearchService.search("포토공정", "개발")).thenReturn(result);

		mockMvc.perform(get("/api/wiki/search")
						.param("query", "포토공정")
						.param("userIndustry", "개발"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstSearchResult.found").value(true))
				.andExpect(jsonPath("$.llmResult").value("여러 단계를 순차적으로 거치며 이전 단계 결과물에 의존해 정제하는 과정"))
				.andExpect(jsonPath("$.secondSearchResult.chunks[0].sourceUrl").value("https://wiki.internal/배열순회"));
	}

	@Test
	void search_blankQuery_returnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/wiki/search").param("query", " ").param("userIndustry", "개발"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void search_blankUserIndustry_returnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/wiki/search").param("query", "포토공정").param("userIndustry", " "))
				.andExpect(status().isBadRequest());
	}

	@Test
	void search_missingUserIndustry_returnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/wiki/search").param("query", "포토공정"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void search_userIndustryWithInjectionChars_returnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/wiki/search")
						.param("query", "포토공정")
						.param("userIndustry", "개발' || '1'=='1"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void registerWikiDoc_returnsIndexResult() throws Exception {
		when(wikiService.index(any())).thenReturn(new IndexResult("indexed", 4));

		mockMvc.perform(post("/api/wiki/admin")
						.contentType("application/json")
						.content("""
								{ "title": "EDS 검사", "content": "EDS 검사는 반도체 공정에서...", "industry": "반도체", "sourceUrl": "https://wiki.internal/EDS검사" }
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("indexed"))
				.andExpect(jsonPath("$.chunkCount").value(4));
	}

	@Test
	void registerWikiDoc_blankIndustry_returnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/wiki/admin")
						.contentType("application/json")
						.content("""
								{ "title": "EDS 검사", "content": "본문", "industry": "", "sourceUrl": "https://wiki.internal/x" }
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void registerWikiDoc_blankTitle_returnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/wiki/admin")
						.contentType("application/json")
						.content("""
								{ "title": "", "content": "본문", "sourceUrl": "https://wiki.internal/x" }
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deleteWikiDoc_existingId_returnsNoContent() throws Exception {
		mockMvc.perform(delete("/api/wiki/admin/{id}", 1L))
				.andExpect(status().isNoContent());

		verify(wikiService).delete(1L);
	}

	@Test
	void deleteWikiDoc_unknownId_returnsNotFound() throws Exception {
		doThrow(new WikiDocumentNotFoundException(999L)).when(wikiService).delete(999L);

		mockMvc.perform(delete("/api/wiki/admin/{id}", 999L))
				.andExpect(status().isNotFound());
	}
}
