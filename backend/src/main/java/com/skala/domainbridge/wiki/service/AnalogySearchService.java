package com.skala.domainbridge.wiki.service;

import com.skala.domainbridge.wiki.dto.AnalogySearchResult;
import com.skala.domainbridge.wiki.dto.Chunk;
import com.skala.domainbridge.wiki.dto.WikiSearchResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * 타 도메인 비유 설명(F-06 개인화 설명)의 검색 파이프라인 — ①Wiki 1차 검색 → ②LLM 구조 서술
 * 생성(HyDE) → ③Wiki 2차 검색까지 wiki 도메인이 담당한다 (PM 확정, 2026-09-03). translate는
 * 이 결과를 받아 Glossary 매칭 및 최종 "공식 정의 + 개인화 설명" 답변 생성만 담당한다.
 *
 * "왜 원본 질의어를 그대로 재사용하지 않는가"는 "타 도메인 비유는 무엇으로 검색하나" 메모 참고 —
 * 요약하면, "포토공정"을 "개발" 산업으로 그대로 검색해봐야 주제가 달라 거의 안 걸린다. ①에서 이미
 * 확보한 그라운딩된 사실을 ②가 구조적으로 요약하고, 그 요약문을 ③의 질의어로 쓴다.
 *
 * ①은 산업으로 안 좁힌다 — translate는 질문이 어느 산업 얘기인지 미리 모른다(그게 애초에 질문의
 * 일부다). 좁히지 않아도 벡터+키워드 랭킹이 실측상 잘 구분한다(같은 산업 내 무관한 문서와도
 * 점수 차이가 3배 이상 났다).
 */
@Service
public class AnalogySearchService {

	private final WikiService wikiService;
	private final ChatClient chatClient;

	public AnalogySearchService(WikiService wikiService, ChatClient chatClient) {
		this.wikiService = wikiService;
		this.chatClient = chatClient;
	}

	/**
	 * @param query        타겟 개념 질의어 (예: "포토공정") — 어느 산업 얘기인지 모른 채로 전체
	 *                     산업 대상 검색한다.
	 * @param userIndustry 비유 근거를 찾을 사용자 자신의 산업 (채팅 시작 시 설정한 페르소나에서
	 *                     translate가 넘겨주는 값)
	 */
	public AnalogySearchResult search(String query, String userIndustry) {
		WikiSearchResponse firstSearchResult = wikiService.search(query, null);

		if (!firstSearchResult.found()) {
			// 근거가 없으면 구조를 요약할 대상 자체가 없다 — LLM을 호출하지 않는다(불필요한 비용/지연 방지).
			return new AnalogySearchResult(firstSearchResult, null, WikiSearchResponse.notFound());
		}

		String llmResult = describeStructure(firstSearchResult);
		// 2차 검색은 벡터 전용 — 구조 서술이 "순서"/"단계"/"의존 관계"처럼 흔한 단어라 키워드(pg_bigm)
		// leg를 태우면 그 단어가 겹치는 아무 문서나 새어 들어온다(실측 확인). WikiService.searchVectorOnly 참고.
		WikiSearchResponse secondSearchResult = wikiService.searchVectorOnly(llmResult, userIndustry);

		return new AnalogySearchResult(firstSearchResult, llmResult, secondSearchResult);
	}

	private String describeStructure(WikiSearchResponse firstSearchResult) {
		String groundedContent = firstSearchResult.chunks().stream()
				.map(Chunk::content)
				.collect(Collectors.joining("\n"));

		// 산업 용어를 빼라는 규칙이 있었는데(2026-09-03), 실측 3회에서 규칙 준수/위반 간 점수 차이가
		// 자연스러운 실행별 편차(0.20~0.24)보다 크지 않아 유의미한 효과를 확인 못 했다. 일단 빼고
		// 검증한다 — 필요해지면 "왜 mecab-ko 대신 pg_bigm인가"류 문서로 재도입 여부를 다시 본다.
		String prompt = """
				다음 내용이 설명하는 과정/개념의 구조적 특징만 한두 문장으로 요약하세요.

				규칙:
				- 아래 내용에 없는 새로운 사실을 추가하지 마세요 — 있는 내용을 구조적으로 재서술만 하세요.
				- 몇 단계로 이루어지는지, 단계 간 의존 관계가 있는지, 순차/병렬/반복 여부처럼
				  구조적 특징만 서술하세요.

				내용:
				%s
				""".formatted(groundedContent);

		return chatClient.prompt().user(prompt).call().content();
	}
}
