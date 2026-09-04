package com.skala.domainbridge.wiki.controller;

import com.skala.domainbridge.wiki.dto.AnalogySearchResult;
import com.skala.domainbridge.wiki.dto.IndexResult;
import com.skala.domainbridge.wiki.dto.WikiUploadRequest;
import com.skala.domainbridge.wiki.service.AnalogySearchService;
import com.skala.domainbridge.wiki.service.WikiDocumentNotFoundException;
import com.skala.domainbridge.wiki.service.WikiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 담당 API — 등록/삭제(CLAUDE_1.md 원 스펙 + 내부 요청으로 확장) + 검색.
 *
 * 검색은 "은어 정의 조회"와 "타 도메인 비유 설명"을 더 이상 구분하지 않는다 (2026-09-03 결정).
 * 원래 스펙 문구 자체가 모든 응답이 "공식 정의 + 개인화 설명" 2파트라고 했었는데, 그동안 이걸
 * 임의로 Type1/Type2로 나눠서 비유 쪽에만 개인화를 붙였었다 — 실사용에서는 은어 질문도 항상
 * 문장으로 오고, 개인화가 항상 필요하다는 피드백을 반영해 하나의 흐름으로 합쳤다. Wiki 1차 검색 →
 * LLM 구조 서술 생성 → Wiki 2차 검색(userIndustry)이 모든 검색 요청에 적용된다.
 *
 * 주의: 권한은 컨트롤러가 아니라 SecurityConfig의 URL 패턴 매칭으로 처리한다 — 이 프로젝트는
 * @EnableMethodSecurity가 꺼져 있어 @PreAuthorize를 붙여도 조용히 무시된다.
 * 관리자 전용이라 경로에 /admin/을 반드시 포함시켰다 (/api/wiki/admin/**).
 * TODO: auth 담당자에게 SecurityConfig 한 줄 추가 요청 —
 *   .requestMatchers("/api/wiki/admin/**").hasRole("ADMIN")
 *   .requestMatchers("/api/wiki/search").authenticated()
 */
@Tag(name = "Wiki (RAG)", description = "사내 위키 문서 등록/삭제(ADMIN) 및 하이브리드(벡터+키워드) 검색 - 번역 RAG 파이프라인의 근거 저장소")
@RestController
@Validated
public class WikiController {

	private static final String INDUSTRY_PATTERN = "^[\\p{IsHangul}A-Za-z0-9 ]*$";
	private static final String INDUSTRY_PATTERN_MESSAGE = "industry에는 문자/숫자/공백만 허용됩니다.";

	private final WikiService wikiService;
	private final AnalogySearchService analogySearchService;

	public WikiController(WikiService wikiService, AnalogySearchService analogySearchService) {
		this.wikiService = wikiService;
		this.analogySearchService = analogySearchService;
	}

	@Operation(summary = "위키 문서 등록", description = "위키 문서를 청크로 분할해 벡터 저장소에 임베딩/색인한다. ADMIN 권한 필요.")
	@PostMapping("/api/wiki/admin")
	public ResponseEntity<IndexResult> registerWikiDoc(@Valid @RequestBody WikiUploadRequest request) {
		return ResponseEntity.ok(wikiService.index(request));
	}

	/**
	 * 모든 검색이 거치는 단일 경로 — Wiki 1차 검색(전체 산업 대상) → LLM 구조 서술 생성 →
	 * Wiki 2차 검색(userIndustry)까지 항상 실행하고 세 결과를 다 돌려준다
	 * (firstSearchResult/llmResult/secondSearchResult). translate는 firstSearchResult만 써도
	 * 되고(용어 정의만 필요한 경우), secondSearchResult까지 같이 써서 개인화 설명을 만들어도 된다 —
	 * 선택은 translate 몫이지만, wiki는 매번 셋 다 준비해서 넘긴다.
	 *
	 * targetIndustry는 안 받는다 — translate는 질문이 어느 산업 얘기인지 미리 모른다(그게 질문의
	 * 일부다). 1차 검색은 항상 전체 산업 대상이고, 벡터+키워드 랭킹이 알아서 구분한다.
	 */
	@Operation(summary = "위키 검색 (RAG)",
			description = "① 전체 산업 대상 1차 검색(벡터+키워드 하이브리드, RRF 결합) → ② LLM 구조 서술 생성(HyDE) → "
					+ "③ 사용자 산업(userIndustry) 대상 2차 벡터 검색까지 수행하고 세 결과를 모두 반환한다.")
	@GetMapping("/api/wiki/search")
	public ResponseEntity<AnalogySearchResult> search(
			@Parameter(description = "검색할 용어/질의어") @RequestParam @NotBlank(message = "query는 비어 있을 수 없습니다.") String query,
			// userIndustry는 채팅 시작 시 설정한 사용자 페르소나에서 옴 — 항상 필수.
			@Parameter(description = "비유 근거를 찾을 사용자 자신의 산업(도메인)")
			@RequestParam @NotBlank(message = "userIndustry는 비어 있을 수 없습니다.")
			@Pattern(regexp = INDUSTRY_PATTERN, message = INDUSTRY_PATTERN_MESSAGE)
			String userIndustry
	) {
		return ResponseEntity.ok(analogySearchService.search(query, userIndustry));
	}

	@Operation(summary = "위키 문서 삭제", description = "위키 문서와 연관된 모든 청크를 벡터 저장소에서 함께 삭제한다. ADMIN 권한 필요.")
	@DeleteMapping("/api/wiki/admin/{id}")
	public ResponseEntity<Void> deleteWikiDoc(@Parameter(description = "위키 문서 ID") @PathVariable Long id) {
		wikiService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<String> handleBlankQuery(ConstraintViolationException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
	}

	@ExceptionHandler(WikiDocumentNotFoundException.class)
	public ResponseEntity<String> handleNotFound(WikiDocumentNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
	}
}
