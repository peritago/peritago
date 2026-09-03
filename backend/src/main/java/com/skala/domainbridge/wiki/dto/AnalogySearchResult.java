package com.skala.domainbridge.wiki.dto;

/**
 * firstSearchResult: ① Wiki 1차 검색(타겟 개념) 결과.
 * llmResult: ② LLM이 firstSearchResult를 구조적으로 요약한 문장(HyDE 질의어) —
 * firstSearchResult가 found=false면 null (요약할 근거가 없으므로 LLM을 아예 호출하지 않는다).
 * secondSearchResult: ③ Wiki 2차 검색(사용자 산업, llmResult로 검색) 결과.
 *
 * translate는 이 세 필드를 그대로 받아 최종 "공식 정의 + 개인화 설명" 답변을 생성한다
 * (Generation은 여전히 translate 몫 — wiki는 여기까지만 담당).
 */
public record AnalogySearchResult(
		WikiSearchResponse firstSearchResult,
		String llmResult,
		WikiSearchResponse secondSearchResult
) {
}
