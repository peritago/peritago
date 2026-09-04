package com.skala.domainbridge.context.controller;

import com.skala.domainbridge.common.response.ApiResponse;
import com.skala.domainbridge.context.dto.request.ContextAppendRequestDto;
import com.skala.domainbridge.context.dto.response.ContextWindowResponseDto;
import com.skala.domainbridge.context.dto.response.DetectedTermDto;
import com.skala.domainbridge.context.service.ContextService;
import com.skala.domainbridge.context.service.SlangDetector;
import com.skala.domainbridge.translate.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 대화 맥락 적재/조회 (F-10).
 *
 * 맥락은 세션에 종속된 사용자 소유 자원이므로, 적재·조회 전에 반드시 세션 소유자를 검증한다.
 * 검증하지 않으면 타인 세션에 임의 문장을 밀어 넣어 그 사람의 번역 결과를 조작할 수 있다.
 */
@Tag(name = "Context", description = "대화 맥락(슬라이딩 윈도우) 적재/조회 및 은어 자동 감지 (F-10, F-11)")
@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextController {

    private final ContextService contextService;
    private final SlangDetector slangDetector;
    private final ChatSessionService chatSessionService;

    /**
     * STT 전사 문장 적재. 적재 후의 윈도우와, 이번 문장에서 자동 감지된 사내 은어(F-11)를 돌려준다.
     * 감지는 은어 사전과의 문자열 대조라 LLM 을 호출하지 않는다.
     */
    @Operation(summary = "전사 문장 적재",
            description = "STT로 전사된 문장을 세션의 맥락 윈도우에 추가한다. "
                    + "적재 후의 윈도우와, 이번 문장에서 은어 사전과의 문자열 대조로 자동 감지된 사내 은어 목록을 함께 돌려준다.")
    @PostMapping("/messages")
    public ApiResponse<ContextWindowResponseDto> appendMessages(
            Authentication authentication, @Valid @RequestBody ContextAppendRequestDto request) {
        Long userId = (Long) authentication.getPrincipal();
        chatSessionService.findMySession(userId, request.sessionId());

        List<String> window = contextService.appendAll(request.sessionId(), request.sentences());
        List<DetectedTermDto> detected = slangDetector.detect(request.sentences());

        return ApiResponse.success(
                ContextWindowResponseDto.of(request.sessionId(), window, detected));
    }

    /** 현재 맥락 윈도우 조회. 프론트 확인용이자 시연에서 맥락 반영을 보여주는 용도. */
    @Operation(summary = "맥락 윈도우 조회", description = "세션에 현재 적재된 맥락 윈도우를 조회한다.")
    @GetMapping("/{sessionId}")
    public ApiResponse<ContextWindowResponseDto> findWindow(
            Authentication authentication, @Parameter(description = "세션 ID") @PathVariable Long sessionId) {
        Long userId = (Long) authentication.getPrincipal();
        chatSessionService.findMySession(userId, sessionId);

        return ApiResponse.success(ContextWindowResponseDto.of(sessionId, contextService.window(sessionId)));
    }

    /** 회의 종료 등으로 맥락을 비운다. TTL(1시간) 만료를 기다리지 않고 즉시 정리하는 경로. */
    @Operation(summary = "맥락 윈도우 비우기", description = "회의 종료 등으로 세션의 맥락을 즉시 비운다. TTL(1시간) 만료를 기다리지 않는다.")
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> clearWindow(
            Authentication authentication, @Parameter(description = "세션 ID") @PathVariable Long sessionId) {
        Long userId = (Long) authentication.getPrincipal();
        chatSessionService.findMySession(userId, sessionId);

        contextService.clear(sessionId);
        return ApiResponse.noContent();
    }
}
