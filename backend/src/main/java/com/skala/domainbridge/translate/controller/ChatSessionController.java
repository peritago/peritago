package com.skala.domainbridge.translate.controller;

import com.skala.domainbridge.common.response.ApiResponse;
import com.skala.domainbridge.translate.dto.request.ChatSessionRenameRequestDto;
import com.skala.domainbridge.translate.dto.response.ChatSessionCreateResponseDto;
import com.skala.domainbridge.translate.dto.response.ChatSessionResponseDto;
import com.skala.domainbridge.translate.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Chat Session", description = "채팅 세션 생성/조회/제목 변경")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @Operation(summary = "세션 생성", description = "새 채팅 세션을 생성한다.")
    @PostMapping
    public ApiResponse<ChatSessionCreateResponseDto> createSession(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.created(chatSessionService.createSession(userId));
    }

    @Operation(summary = "내 세션 목록 조회", description = "본인이 소유한 채팅 세션 목록을 최근 대화순으로 조회한다.")
    @GetMapping
    public ApiResponse<List<ChatSessionResponseDto>> findMySessions(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(chatSessionService.findMySessions(userId));
    }

    @Operation(summary = "세션 단건 조회", description = "본인이 소유한 채팅 세션을 단건 조회한다.")
    @GetMapping("/{sessionId}")
    public ApiResponse<ChatSessionResponseDto> findMySession(
            Authentication authentication, @Parameter(description = "세션 ID") @PathVariable Long sessionId) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(chatSessionService.findMySession(userId, sessionId));
    }

    @Operation(summary = "세션 제목 변경", description = "채팅 세션의 제목을 변경한다.")
    @PutMapping("/{sessionId}/title")
    public ApiResponse<ChatSessionResponseDto> renameSession(
            Authentication authentication,
            @Parameter(description = "세션 ID") @PathVariable Long sessionId,
            @Valid @RequestBody ChatSessionRenameRequestDto request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(chatSessionService.rename(userId, sessionId, request.title()));
    }
}
