package com.skala.domainbridge.context.dto.response;

import java.util.List;

/**
 * 현재 세션이 들고 있는 대화 맥락 윈도우.
 *
 * 적재 직후 상태를 그대로 돌려주어, 프론트가 "지금 서버가 무엇을 맥락으로 보고 있는지"를
 * 확인하거나 화면에 노출할 수 있게 한다. 시연에서 맥락 반영을 보여줄 때도 쓰인다.
 */
public record ContextWindowResponseDto(
        Long sessionId,
        List<String> window,
        int size
) {
    public static ContextWindowResponseDto of(Long sessionId, List<String> window) {
        return new ContextWindowResponseDto(sessionId, window, window.size());
    }
}
