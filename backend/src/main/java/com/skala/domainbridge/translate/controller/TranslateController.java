package com.skala.domainbridge.translate.controller;

import com.skala.domainbridge.common.response.ApiResponse;
import com.skala.domainbridge.translate.dto.request.TranslateRequestDto;
import com.skala.domainbridge.translate.dto.response.PageResponseDto;
import com.skala.domainbridge.translate.dto.response.TranslateResponseDto;
import com.skala.domainbridge.translate.service.TranslateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Translate", description = "용어 번역 질의 - 3단계 폴백(Glossary → Wiki RAG → LLM 일반 지식)으로 근거를 확보해 "
        + "공식 정의 + 개인화 설명을 생성한다 (F-04~F-08)")
@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final TranslateService translateService;

    /**
     * 용어 번역 질의 (F-04, F-06).
     * 미등록 용어도 오류가 아니라 sourceType=GENERAL 로 200 정상 응답한다.
     */
    @Operation(summary = "용어 번역 질의",
            description = "세션 맥락 안에서 용어를 번역한다. 근거는 Glossary(사내 은어 사전) → Wiki 벡터/키워드 검색(RAG) → "
                    + "LLM 일반 지식 순으로 폴백하며, 미등록 용어도 오류가 아니라 sourceType=GENERAL 로 200 정상 응답한다.")
    @PostMapping
    public ApiResponse<TranslateResponseDto> translate(
            Authentication authentication, @Valid @RequestBody TranslateRequestDto request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(translateService.translate(userId, request));
    }

    /** 질의 이력 조회 (F-08) — 본인 질의만 최신순 페이징. */
    @Operation(summary = "질의 이력 조회", description = "본인이 질의했던 번역 이력을 최신순으로 페이징 조회한다.")
    @GetMapping("/history")
    public ApiResponse<PageResponseDto<TranslateResponseDto>> findMyHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(translateService.findHistory(userId, page, size));
    }
}
