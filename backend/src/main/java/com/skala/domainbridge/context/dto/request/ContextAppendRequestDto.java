package com.skala.domainbridge.context.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * STT 전사 문장 적재 요청 (F-10, F-15).
 *
 * Web Speech API 는 확정된 문장을 하나씩 내보내므로 보통 1건이지만,
 * 빠르게 연속 확정된 문장을 묶어 보낼 수 있도록 리스트로 받는다.
 */
public record ContextAppendRequestDto(

        @NotNull(message = "sessionId는 필수입니다.")
        Long sessionId,

        @NotEmpty(message = "적재할 문장이 없습니다.")
        @Size(max = 20, message = "한 번에 최대 20문장까지 보낼 수 있습니다.")
        List<@Size(max = 500, message = "문장은 500자를 넘을 수 없습니다.") String> sentences
) {}
