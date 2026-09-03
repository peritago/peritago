package com.skala.domainbridge.translate.controller;

import com.skala.domainbridge.common.response.ApiResponse;
import com.skala.domainbridge.translate.dto.request.TranslateRequestDto;
import com.skala.domainbridge.translate.dto.response.PageResponseDto;
import com.skala.domainbridge.translate.dto.response.TranslateResponseDto;
import com.skala.domainbridge.translate.service.TranslateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final TranslateService translateService;

    /**
     * 용어 번역 질의 (F-04, F-06).
     * 미등록 용어도 오류가 아니라 sourceType=GENERAL 로 200 정상 응답한다.
     */
    @PostMapping
    public ApiResponse<TranslateResponseDto> translate(
            Authentication authentication, @Valid @RequestBody TranslateRequestDto request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(translateService.translate(userId, request));
    }

    /** 질의 이력 조회 (F-08) — 본인 질의만 최신순 페이징. */
    @GetMapping("/history")
    public ApiResponse<PageResponseDto<TranslateResponseDto>> findMyHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(translateService.findHistory(userId, page, size));
    }
}
