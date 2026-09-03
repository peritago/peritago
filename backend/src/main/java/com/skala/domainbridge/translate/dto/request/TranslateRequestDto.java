package com.skala.domainbridge.translate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/translate 요청 (F-04).
 *
 * 대화 맥락은 요청으로 받지 않는다. 서버가 sessionId로 Redis 슬라이딩 윈도우(context:{sessionId})를
 * 조회해 채우는 구조이며, Mock 단계에서는 NULL이다.
 */
public record TranslateRequestDto(

        @NotNull(message = "sessionId는 필수입니다.")
        Long sessionId,

        @NotBlank(message = "용어를 입력해 주세요.")
        @Size(max = 100, message = "용어는 100자를 넘을 수 없습니다.")
        String term
) {
    /** 앞뒤 공백은 저장 전에 제거한다. (공백만 입력한 경우는 @NotBlank가 막는다) */
    public String normalizedTerm() {
        return term == null ? null : term.trim();
    }
}
