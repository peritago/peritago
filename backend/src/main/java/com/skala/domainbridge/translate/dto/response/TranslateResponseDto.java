package com.skala.domainbridge.translate.dto.response;

import com.skala.domainbridge.translate.entity.AiResponse;
import com.skala.domainbridge.translate.entity.Query;
import com.skala.domainbridge.translate.entity.SourceType;

import java.time.LocalDateTime;

/**
 * POST /api/translate 응답 — 2파트 카드 (F-06, F-07).
 * 미등록 용어도 sourceType=GENERAL 로 200 정상 응답한다.
 *
 * @param officialDefinition       📖 공식 정의
 * @param personalizedExplanation  💡 당신을 위한 설명
 * @param outsideCompanyStandard   true면 프론트에서 "사내 기준 아님" 표시
 */
public record TranslateResponseDto(
        Long queryId,
        Long sessionId,
        String term,
        String officialDefinition,
        String personalizedExplanation,
        SourceType sourceType,
        String sourceRef,
        boolean outsideCompanyStandard,
        LocalDateTime createdAt
) {
    public static TranslateResponseDto of(Query query, AiResponse response) {
        return new TranslateResponseDto(
                query.getId(),
                query.getSession().getId(),
                query.getTerm(),
                response.getOfficialDefinition(),
                response.getPersonalizedExplanation(),
                response.getSourceType(),
                response.getSourceRef(),
                response.getSourceType().isOutsideCompanyStandard(),
                response.getCreatedAt()
        );
    }
}
