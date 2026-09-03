package com.skala.domainbridge.wiki.service;

import com.skala.domainbridge.wiki.dto.Chunk;
import com.skala.domainbridge.wiki.dto.IndexResult;
import com.skala.domainbridge.wiki.dto.WikiSearchResponse;
import com.skala.domainbridge.wiki.dto.WikiUploadRequest;
import com.skala.domainbridge.wiki.entity.WikiDocument;
import com.skala.domainbridge.wiki.repository.VectorStoreKeywordSearchRepository;
import com.skala.domainbridge.wiki.repository.VectorStoreKeywordSearchRepository.KeywordMatch;
import com.skala.domainbridge.wiki.repository.WikiDocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG의 Retrieval 절반만 담당 (등록 + 검색). 근거를 바탕으로 답을 생성하는 쪽(Generation)은
 * translate 도메인 몫. WIKI_SPEC_1.md 1~2장 참고.
 *
 * 검색은 순수 벡터 검색이 아니라 Hybrid(벡터 + 키워드)다. wiki는 백과사전이라 부품 코드명·스펙
 * 번호처럼 임베딩이 의미로 잘 못 잡아내는 정확한 문자열이 많이 들어있어서, 벡터 검색만으로는
 * 놓치기 쉽다. 두 결과는 RRF(Reciprocal Rank Fusion)로 합친다 — 참고: "왜 Hybrid 검색을 안 썼는가"
 * 문서(초기 결정)에서 이 문제를 뒤늦게 발견해 뒤집은 것.
 */
@Service
public class WikiService {

	/** RRF 표준 상수(문헌/Elasticsearch 기본값) — 랭크가 낮은 문서의 기여도를 완만하게 깎는다. */
	private static final int RRF_K = 60;

	private final WikiDocumentRepository wikiDocumentRepository;
	private final VectorStore vectorStore;
	private final VectorStoreKeywordSearchRepository keywordSearchRepository;
	private final TokenTextSplitter textSplitter = new TokenTextSplitter();

	private final int topK;
	private final int analogyTopK;
	private final double similarityThreshold;
	private final double keywordSimilarityThreshold;

	public WikiService(
			WikiDocumentRepository wikiDocumentRepository,
			VectorStore vectorStore,
			VectorStoreKeywordSearchRepository keywordSearchRepository,
			@Value("${wiki.search.top-k:3}") int topK,
			@Value("${wiki.search.analogy-top-k:5}") int analogyTopK,
			@Value("${wiki.search.similarity-threshold:0.75}") double similarityThreshold,
			@Value("${wiki.search.keyword-similarity-threshold:0.03}") double keywordSimilarityThreshold
	) {
		this.wikiDocumentRepository = wikiDocumentRepository;
		this.vectorStore = vectorStore;
		this.keywordSearchRepository = keywordSearchRepository;
		this.topK = topK;
		this.analogyTopK = analogyTopK;
		this.similarityThreshold = similarityThreshold;
		this.keywordSimilarityThreshold = keywordSimilarityThreshold;
	}

	@Transactional
	public IndexResult index(WikiUploadRequest request) {
		WikiDocument saved = wikiDocumentRepository.save(
				new WikiDocument(request.title(), request.industry(), request.sourceUrl())
		);

		List<Document> chunks = textSplitter.apply(List.of(
				new Document(request.content(), Map.of(
						"title", request.title(),
						"industry", request.industry(),
						"sourceUrl", request.sourceUrl() == null ? "" : request.sourceUrl(),
						// wiki_documents.id를 청크 메타데이터에도 심어둬야 삭제 시 연관 청크를 찾을 수 있다.
						"wikiDocumentId", String.valueOf(saved.getId())
				))
		));
		vectorStore.add(chunks);

		return new IndexResult("indexed", chunks.size());
	}

	@Transactional
	public void delete(Long id) {
		WikiDocument document = wikiDocumentRepository.findById(id)
				.orElseThrow(() -> new WikiDocumentNotFoundException(id));

		vectorStore.delete("wikiDocumentId == '" + id + "'");
		wikiDocumentRepository.delete(document);
	}

	/**
	 * Hybrid(벡터+키워드) 검색 — 정확한 용어/코드명을 찾는 일반 검색용. industry가 있으면 그 산업
	 * 안에서만 검색한다. 은어/용어 정의 조회처럼 산업을 안 가리는 경우엔 null/blank로 호출하면
	 * 전체 산업 대상 검색이 된다.
	 *
	 * 벡터 검색(similarityThreshold로 품질 하한선)과 키워드 검색(keywordSimilarityThreshold로
	 * 품질 하한선)을 각각 돌려서 RRF로 합친다 — 한쪽이 아무것도 못 찾아도 다른 쪽 순위를 그대로
	 * 따르게 되어 안전하다.
	 *
	 * 키워드 leg에 실제 threshold가 필요하다 — "sim > 0"(조금이라도 겹치면 통과)만으로는 부족했다.
	 * 실측: "포토공정" 질의어가 정답 문서와는 유사도 0.051이 나왔는데, "차체 도장 공정"처럼 "공정"
	 * 이라는 흔한 글자만 겹치는 무관한 문서와도 0.011이 나와서 그냥 통과해버렸다(2026-09-03 확인).
	 *
	 * 구조 서술(HyDE 질의어)엔 쓰지 말 것 — {@link #searchVectorOnly} 참고.
	 */
	public WikiSearchResponse search(String query, String industry) {
		List<Document> vectorResults = vectorStore.similaritySearch(buildSearchRequest(query, industry, topK));
		List<KeywordMatch> keywordResults =
				keywordSearchRepository.search(query, industry, topK, keywordSimilarityThreshold);

		List<Chunk> chunks = mergeByReciprocalRankFusion(vectorResults, keywordResults).stream()
				.limit(topK)
				.toList();

		return chunks.isEmpty() ? WikiSearchResponse.notFound() : new WikiSearchResponse(true, chunks);
	}

	/**
	 * 벡터 전용 검색 — 키워드(pg_bigm) leg를 안 쓴다. 구조 서술(HyDE, 타 도메인 비유의 2차 검색)엔
	 * 이 메서드를 써야 한다. pg_bigm은 "PN-4471-B" 같은 특정 코드명엔 잘 맞지만, 구조 서술은
	 * "순서"/"단계"/"의존 관계"처럼 흔한 단어로 이루어져 있어서 키워드 leg를 태우면 그 흔한 단어가
	 * 겹치는 아무 문서나 새어 들어온다(실측 확인, 2026-09-03 — "차체 도장 공정" 검색 시 "타이어
	 * 공기압 센서"가 score 0.0으로 섞여 들어온 사례). 구조 서술은 애초에 특정 코드를 찾는 게 아니라
	 * 의미적으로 비슷한 걸 찾는 거라, 벡터만 쓰는 게 원래 목적에 맞다.
	 *
	 * top-k는 1차 검색보다 넓게(analogyTopK, 기본 5) 잡는다 — 임베딩 유사도만으로는 "진짜 구조가
	 * 맞는 후보"를 항상 1등으로 못 뽑는다(실측 확인). 정답 판정은 어차피 translate가 후보들을 보고
	 * 다시 검증하기로 했으니(2026-09-03), wiki는 정확한 1등을 맞히기보다 후보군 자체를 넓혀서
	 * "진짜 정답이 후보에서 아예 빠지는" 상황을 줄이는 쪽을 택했다.
	 */
	public WikiSearchResponse searchVectorOnly(String query, String industry) {
		List<Document> vectorResults = vectorStore.similaritySearch(buildSearchRequest(query, industry, analogyTopK));

		List<Chunk> chunks = vectorResults.stream()
				.map(this::toChunk)
				.limit(analogyTopK)
				.toList();

		return chunks.isEmpty() ? WikiSearchResponse.notFound() : new WikiSearchResponse(true, chunks);
	}

	private SearchRequest buildSearchRequest(String query, String industry, int limit) {
		SearchRequest.Builder searchRequest = SearchRequest.builder()
				.query(query)
				.topK(limit)
				.similarityThreshold(similarityThreshold);

		if (StringUtils.hasText(industry)) {
			searchRequest.filterExpression("industry == '" + industry + "'");
		}

		return searchRequest.build();
	}

	private Chunk toChunk(Document document) {
		return new Chunk(
				document.getText(),
				document.getScore() == null ? 0.0 : document.getScore(),
				(String) document.getMetadata().get("sourceUrl")
		);
	}

	private List<Chunk> mergeByReciprocalRankFusion(List<Document> vectorResults, List<KeywordMatch> keywordResults) {
		record Candidate(String content, double vectorScore, String sourceUrl) {
		}

		Map<String, Candidate> candidatesById = new LinkedHashMap<>();
		Map<String, Double> rrfScoreById = new LinkedHashMap<>();

		for (int rank = 0; rank < vectorResults.size(); rank++) {
			Document document = vectorResults.get(rank);
			rrfScoreById.merge(document.getId(), 1.0 / (RRF_K + rank + 1), Double::sum);
			candidatesById.putIfAbsent(document.getId(), new Candidate(
					document.getText(),
					document.getScore() == null ? 0.0 : document.getScore(),
					(String) document.getMetadata().get("sourceUrl")
			));
		}

		for (int rank = 0; rank < keywordResults.size(); rank++) {
			KeywordMatch match = keywordResults.get(rank);
			rrfScoreById.merge(match.id(), 1.0 / (RRF_K + rank + 1), Double::sum);
			// 벡터 결과에도 이미 있으면(둘 다 찾음) 벡터 쪽 score를 우선한다 — score 필드는
			// 원래 코사인 유사도 의미라, 키워드로만 찾은 문서에 한해서만 0.0으로 대체한다.
			candidatesById.putIfAbsent(match.id(), new Candidate(match.content(), 0.0, match.sourceUrl()));
		}

		return rrfScoreById.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.map(entry -> candidatesById.get(entry.getKey()))
				.map(candidate -> new Chunk(candidate.content(), candidate.vectorScore(), candidate.sourceUrl()))
				.toList();
	}
}
