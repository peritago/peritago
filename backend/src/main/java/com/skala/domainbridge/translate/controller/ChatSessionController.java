package com.skala.domainbridge.translate.controller;

import com.skala.domainbridge.common.response.ApiResponse;
import com.skala.domainbridge.translate.dto.request.ChatSessionRenameRequestDto;
import com.skala.domainbridge.translate.dto.response.ChatSessionCreateResponseDto;
import com.skala.domainbridge.translate.dto.response.ChatSessionResponseDto;
import com.skala.domainbridge.translate.service.ChatSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @PostMapping
    public ApiResponse<ChatSessionCreateResponseDto> createSession(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.created(chatSessionService.createSession(userId));
    }

    @GetMapping
    public ApiResponse<List<ChatSessionResponseDto>> findMySessions(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(chatSessionService.findMySessions(userId));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<ChatSessionResponseDto> findMySession(
            Authentication authentication, @PathVariable Long sessionId) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(chatSessionService.findMySession(userId, sessionId));
    }

    @PutMapping("/{sessionId}/title")
    public ApiResponse<ChatSessionResponseDto> renameSession(
            Authentication authentication,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatSessionRenameRequestDto request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(chatSessionService.rename(userId, sessionId, request.title()));
    }
}
