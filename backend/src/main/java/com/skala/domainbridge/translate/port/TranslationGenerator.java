package com.skala.domainbridge.translate.port;

import com.skala.domainbridge.translate.entity.SourceType;
import com.skala.domainbridge.user.entity.ExplanationLength;

import java.util.List;

/**
 * 공식 정의 / 개인화 설명 2파트 생성 (F-06).
 * 2일차는 Mock, 3일차 이후 OpenAI 실연동으로 교체하되 이 인터페이스는 바뀌지 않는다.
 */
public interface TranslationGenerator {

    Result generate(Command command);

    /**
     * 프롬프트가 바뀌면 같은 입력이라도 응답이 달라진다. 캐시 키에 넣어 자동으로 무효화하기 위해 노출한다.
     * 이게 없으면 프롬프트를 고쳐도 캐시가 옛 응답을 계속 돌려준다.
     */
    String promptVersion();

    /**
     * @param evidence              폴백으로 확보한 근거 원문. GENERAL이면 null.
     * @param personalizationEnabled 페르소나 미설정 시 false — 개인화 파트 생성을 건너뛰어 토큰을 낭비하지 않는다.
     */
    record Command(
            String term,
            String contextSnapshot,
            SourceType sourceType,
            String evidence,
            List<String> domainTags,
            String personaDescription,
            ExplanationLength officialDefLength,
            ExplanationLength personalizedExpLength,
            boolean personalizationEnabled,
            boolean evidenceKeywordOnly,
            List<Analogy> analogies,
            boolean evidenceLookupFailed
    ) {}

    /**
     * 개인화 설명에 쓸 비유 근거. 사용자가 아는 분야의 위키 문서에서 온다.
     *
     * @param domain 어느 분야 문서인지 - 답변에서 "영업 쪽 사례에 빗대면" 처럼 출처를 밝히는 데 쓴다.
     */
    record Analogy(String domain, String content) {}

    /** model~latencyMs는 관측성 확장 필드 — Mock 단계에서는 전부 null. */
    record Result(
            String officialDefinition,
            String personalizedExplanation,
            String model,
            String promptVersion,
            Integer tokenUsage,
            Integer latencyMs
    ) {}
}
