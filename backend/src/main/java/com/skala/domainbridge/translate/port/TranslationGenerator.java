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
            boolean personalizationEnabled
    ) {}

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
