package com.skala.domainbridge.glossary.controller;

import com.skala.domainbridge.common.response.ApiResponse;
import com.skala.domainbridge.glossary.dto.request.GlossaryRequestDto;
import com.skala.domainbridge.glossary.dto.response.GlossaryResponseDto;
import com.skala.domainbridge.glossary.service.GlossaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * ADMIN 권한 여부는 SecurityConfig의 "/api/glossary/admin/**" 경로 매칭(hasRole("ADMIN"))으로 처리된다.
 */
@Tag(name = "Glossary (Admin)", description = "사내 은어 사전 등록/조회 - ADMIN 전용. 번역 3단계 폴백의 1순위 근거.")
@RestController
@RequestMapping("/api/glossary/admin")
@RequiredArgsConstructor
public class GlossaryController {

    private final GlossaryService glossaryService;

    @Operation(summary = "은어 등록", description = "사내 은어와 공식 정의를 사전에 등록한다. ADMIN 권한 필요.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GlossaryResponseDto> register(
            Authentication authentication, @Valid @RequestBody GlossaryRequestDto request) {
        Long adminId = (Long) authentication.getPrincipal();
        var glossary = glossaryService.register(request.term(), request.officialDefinition(), adminId);
        return ApiResponse.created(GlossaryResponseDto.from(glossary));
    }

    @Operation(summary = "은어 전체 조회", description = "등록된 사내 은어 목록을 모두 조회한다. ADMIN 권한 필요.")
    @GetMapping
    public ApiResponse<List<GlossaryResponseDto>> getAll() {
        return ApiResponse.success(glossaryService.getAll());
    }
}
