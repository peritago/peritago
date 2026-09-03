package com.skala.domainbridge.wiki.service;

import com.skala.domainbridge.wiki.dto.Chunk;
import com.skala.domainbridge.wiki.dto.IndexResult;
import com.skala.domainbridge.wiki.dto.WikiSearchResponse;
import com.skala.domainbridge.wiki.dto.WikiUploadRequest;
import com.skala.domainbridge.wiki.entity.WikiDocument;
import com.skala.domainbridge.wiki.repository.WikiDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OPENAI_API_KEY 없이도 검증 가능한 범위: 실제 로컬 Postgres(wiki_documents 테이블, 트랜잭션)와
 * 실제 청킹(TokenTextSplitter)은 그대로 태우고, 네트워크가 필요한 VectorStore(임베딩 호출)만
 * Mockito mock으로 대체한다. docker(pgvector) 컨테이너가 localhost:5433에 떠 있어야 한다.
 *
 * VectorStore를 mock으로 대체하면 실제 PgVectorStore 빈이 안 만들어져서 vector_store 테이블의
 * 자동 스키마 초기화도 같이 건너뛴다. Hybrid 검색의 키워드 절반(VectorStoreKeywordSearchRepository)은
 * 그 테이블에 직접 SQL을 날리므로, 이 테스트가 자체적으로 최소 스키마(id/content/metadata)를
 * 만들어준다 — embedding 컬럼은 여기서 안 건드리니 생략.
 *
 * 키워드 leg가 pg_bigm(문자 2-gram 유사도)을 쓰므로, docker/postgres-pgbigm 이미지로 띄운
 * Postgres여야 한다(기본 pgvector/pgvector 이미지엔 pg_bigm이 없음). "왜 mecab-ko 대신
 * pg_bigm인가" 문서 참고.
 */
@SpringBootTest
@TestPropertySource(properties = {
		// peritago의 docker-compose.yml 기준 접속 정보로 맞춤 (miniproject_1에서는 5433/slangtranslator였음).
		"spring.datasource.url=jdbc:postgresql://localhost:5432/domainbridge",
		"spring.datasource.username=domainbridge",
		"spring.datasource.password=domainbridge",
		"spring.ai.openai.api-key=sk-test-dummy-key-for-boot-check"
})
class WikiServiceIntegrationTest {

	@Autowired
	private WikiService wikiService;

	@Autowired
	private WikiDocumentRepository wikiDocumentRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private VectorStore vectorStore;

	@BeforeEach
	void cleanUp() {
		wikiDocumentRepository.deleteAll();
		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_bigm");
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS vector_store (
					id uuid PRIMARY KEY,
					content text,
					metadata json
				)
				""");
		jdbcTemplate.update("DELETE FROM vector_store");
	}

	@Test
	void index_persistsMetadataToRealDbAndSendsChunksToVectorStore() {
		WikiUploadRequest request = new WikiUploadRequest(
				"EDS 검사",
				"EDS 검사는 반도체 공정에서 개별 칩의 전기적 특성을 검사하는 공정입니다. "
						+ "불량 여부를 조기에 걸러내기 위해 웨이퍼 단위로 수행됩니다.",
				"반도체",
				"https://wiki.internal/EDS검사"
		);

		IndexResult result = wikiService.index(request);

		assertThat(result.status()).isEqualTo("indexed");
		assertThat(result.chunkCount()).isGreaterThan(0);

		// 실제 Postgres에 정말로 저장됐는지 (mock이 아니라 real repository)
		assertThat(wikiDocumentRepository.findAll())
				.singleElement()
				.satisfies(saved -> {
					assertThat(saved.getTitle()).isEqualTo("EDS 검사");
					assertThat(saved.getIndustry()).isEqualTo("반도체");
					assertThat(saved.getSourceUrl()).isEqualTo("https://wiki.internal/EDS검사");
					assertThat(saved.getCreatedAt()).isNotNull();
				});

		@SuppressWarnings("unchecked")
		var captor = org.mockito.ArgumentCaptor.forClass(List.class);
		verify(vectorStore).add(captor.capture());
		List<Document> sentChunks = captor.getValue();
		assertThat(sentChunks).isNotEmpty();
		assertThat(sentChunks).allSatisfy(doc -> {
			assertThat(doc.getMetadata()).containsEntry("title", "EDS 검사");
			assertThat(doc.getMetadata()).containsEntry("industry", "반도체");
			assertThat(doc.getMetadata()).containsEntry("sourceUrl", "https://wiki.internal/EDS검사");
		});
	}

	@Test
	void index_rollsBackWikiDocumentRow_whenVectorStoreAddFails() {
		doThrow(new RuntimeException("simulated embedding failure")).when(vectorStore).add(anyList());

		WikiUploadRequest request = new WikiUploadRequest("실패용어", "본문 내용입니다.", "반도체", "https://wiki.internal/x");

		assertThatThrownBy(() -> wikiService.index(request)).isInstanceOf(RuntimeException.class);

		// @Transactional이 실제로 롤백하는지 — 아까 부팅 테스트에서 500이 나도 고아 row가
		// 안 남았던 걸 확인했는데, 이 테스트로 항상 보장되는지 고정한다.
		assertThat(wikiDocumentRepository.findAll()).isEmpty();
	}

	@Test
	void search_found_mapsVectorStoreResultsToContractSchema() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
				new Document(
						"EDS 검사는 반도체 공정에서 개별 칩의 전기적 특성을 검사하는 공정입니다.",
						Map.of("sourceUrl", "https://wiki.internal/EDS검사")
				)
		));

		WikiSearchResponse response = wikiService.search("EDS 검사가 뭐야", null);

		assertThat(response.found()).isTrue();
		assertThat(response.chunks()).singleElement().satisfies(chunk -> {
			assertThat(chunk.content()).contains("EDS 검사");
			assertThat(chunk.sourceUrl()).isEqualTo("https://wiki.internal/EDS검사");
		});
	}

	@Test
	void search_notFound_whenVectorStoreReturnsNoResults() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		WikiSearchResponse response = wikiService.search("아무도모르는용어", null);

		assertThat(response.found()).isFalse();
		assertThat(response.chunks()).isEmpty();
	}

	@Test
	void search_withIndustry_appliesFilterExpression() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
				new Document("반도체 SoC의 펌웨어 원격 업데이트는...", Map.of("sourceUrl", "https://wiki.internal/펌웨어OTA"))
		));

		wikiService.search("OTA", "반도체");

		@SuppressWarnings("unchecked")
		var captor = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
		verify(vectorStore).similaritySearch(captor.capture());
		assertThat(captor.getValue().getFilterExpression()).isNotNull();
		assertThat(captor.getValue().getFilterExpression().toString()).contains("반도체");
	}

	@Test
	void search_withoutIndustry_hasNoFilterExpression() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		wikiService.search("OTA", null);

		@SuppressWarnings("unchecked")
		var captor = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
		verify(vectorStore).similaritySearch(captor.capture());
		assertThat(captor.getValue().hasFilterExpression()).isFalse();
	}

	@Test
	void search_keywordOnlyMatch_isReturnedEvenWhenVectorLegFindsNothing() {
		// PN-4471-B 같은 정확한 코드명은 임베딩이 의미로 잘 못 잡아내는 대표 케이스라 벡터 leg가
		// 못 찾을 수 있다 — 키워드(전문 검색) leg가 그 경우에도 결과를 살려내는지 확인.
		jdbcTemplate.update(
				"INSERT INTO vector_store (id, content, metadata) VALUES (?::uuid, ?, ?::json)",
				"11111111-1111-1111-1111-111111111111",
				"PN-4471-B 코드명은 반도체 웨이퍼 검사 장비 모델이다.",
				"{\"sourceUrl\":\"https://wiki.internal/PN-4471-B\"}"
		);
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		WikiSearchResponse response = wikiService.search("PN-4471-B", null);

		assertThat(response.found()).isTrue();
		assertThat(response.chunks()).singleElement().satisfies(chunk -> {
			assertThat(chunk.content()).contains("PN-4471-B");
			assertThat(chunk.sourceUrl()).isEqualTo("https://wiki.internal/PN-4471-B");
		});
	}

	@Test
	void searchVectorOnly_ignoresKeywordLegMatch() {
		// 회귀 테스트: pg_bigm은 "PN-4471-B" 같은 코드명엔 잘 맞지만, 구조 서술처럼 흔한 단어로
		// 된 질의어를 태우면 그 단어가 겹치는 아무 문서나 새어 들어온다(실측 확인, 2026-09-03).
		// searchVectorOnly()는 키워드 leg를 아예 안 태우므로, 벡터 leg가 못 찾으면 정말 못 찾아야 한다.
		jdbcTemplate.update(
				"INSERT INTO vector_store (id, content, metadata) VALUES (?::uuid, ?, ?::json)",
				"66666666-6666-6666-6666-666666666666",
				"PN-4471-B 코드명은 반도체 웨이퍼 검사 장비 모델이다.",
				"{\"sourceUrl\":\"https://wiki.internal/PN-4471-B\"}"
		);
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		WikiSearchResponse response = wikiService.searchVectorOnly("PN-4471-B", null);

		assertThat(response.found()).isFalse();
	}

	@Test
	void search_keywordLeg_respectsIndustryFilterToo() {
		jdbcTemplate.update("INSERT INTO vector_store (id, content, metadata) VALUES (?::uuid, ?, ?::json)",
				"22222222-2222-2222-2222-222222222222",
				"OTA 관련 자동차 소프트웨어 업데이트 설명",
				"{\"sourceUrl\":\"https://wiki.internal/auto-ota\",\"industry\":\"자동차\"}");
		jdbcTemplate.update("INSERT INTO vector_store (id, content, metadata) VALUES (?::uuid, ?, ?::json)",
				"33333333-3333-3333-3333-333333333333",
				"OTA 유사 개념: SoC 펌웨어 원격 업데이트 설명",
				"{\"sourceUrl\":\"https://wiki.internal/semi-fw\",\"industry\":\"반도체\"}");
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		WikiSearchResponse response = wikiService.search("OTA", "반도체");

		assertThat(response.chunks())
				.extracting(Chunk::sourceUrl)
				.containsExactly("https://wiki.internal/semi-fw");
	}

	@Test
	void search_keywordLeg_matchesDespiteKoreanParticleAttachedToStoredWord() {
		// 실제로 겪은 버그의 회귀 테스트: to_tsvector('simple', ...)는 "포토공정은"(조사 포함)을
		// 통째로 한 토큰 취급해서 질의어 "포토공정"과 매치가 안 됐다(직접 SQL로 확인함).
		// pg_bigm은 문자 2-gram 유사도라 조사가 붙어도 매치돼야 한다.
		jdbcTemplate.update(
				"INSERT INTO vector_store (id, content, metadata) VALUES (?::uuid, ?, ?::json)",
				"55555555-5555-5555-5555-555555555555",
				"포토공정은 반도체 웨이퍼에 감광액을 도포하고 노광, 현상하는 단계를 거칩니다.",
				"{\"sourceUrl\":\"https://wiki.internal/포토공정\"}"
		);
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		WikiSearchResponse response = wikiService.search("포토공정", null);

		assertThat(response.found()).isTrue();
		assertThat(response.chunks()).singleElement()
				.extracting(Chunk::sourceUrl)
				.isEqualTo("https://wiki.internal/포토공정");
	}

	@Test
	void search_mergesVectorAndKeywordLegResults() {
		jdbcTemplate.update("INSERT INTO vector_store (id, content, metadata) VALUES (?::uuid, ?, ?::json)",
				"44444444-4444-4444-4444-444444444444",
				"EDS 검사 관련 키워드 전용 매치 문서",
				"{\"sourceUrl\":\"https://wiki.internal/keyword-only\"}");
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
				Document.builder()
						.text("EDS 검사는 벡터 leg가 찾은 문서")
						.metadata(Map.of("sourceUrl", "https://wiki.internal/vector-only"))
						.score(0.91)
						.build()
		));

		WikiSearchResponse response = wikiService.search("EDS 검사", null);

		assertThat(response.chunks())
				.extracting(Chunk::sourceUrl)
				.containsExactlyInAnyOrder("https://wiki.internal/vector-only", "https://wiki.internal/keyword-only");
	}

	@Test
	void delete_removesRealDbRowAndFiltersVectorStoreByWikiDocumentId() {
		WikiDocument saved = wikiDocumentRepository.save(new WikiDocument("삭제될 용어", "반도체", "https://wiki.internal/x"));

		wikiService.delete(saved.getId());

		assertThat(wikiDocumentRepository.findById(saved.getId())).isEmpty();
		verify(vectorStore).delete("wikiDocumentId == '" + saved.getId() + "'");
	}

	@Test
	void delete_unknownId_throwsWithoutTouchingVectorStore() {
		assertThatThrownBy(() -> wikiService.delete(-1L))
				.isInstanceOf(WikiDocumentNotFoundException.class);

		verify(vectorStore, org.mockito.Mockito.never()).delete(anyString());
	}
}
