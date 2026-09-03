package com.skala.domainbridge.glossary.controller;

import com.skala.domainbridge.common.response.ApiResponse;
import com.skala.domainbridge.glossary.dto.request.GlossaryRequestDto;
import com.skala.domainbridge.glossary.dto.response.GlossaryResponseDto;
import com.skala.domainbridge.glossary.service.GlossaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RestController
@RequestMapping("/api/glossary/admin")
@RequiredArgsConstructor
public class GlossaryController {

    private final GlossaryService glossaryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GlossaryResponseDto> register(
            Authentication authentication, @Valid @RequestBody GlossaryRequestDto request) {
        Long adminId = (Long) authentication.getPrincipal();
        var glossary = glossaryService.register(request.term(), request.officialDefinition(), adminId);
        return ApiResponse.created(GlossaryResponseDto.from(glossary));
    }

    @GetMapping
    public ResponseEntity<List<GlossaryResponseDto>> getAll() {
        return ResponseEntity.ok(glossaryService.getAll());
    }
}
