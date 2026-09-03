package com.skala.domainbridge.user.controller;

import com.skala.domainbridge.common.response.ApiResponse;
import com.skala.domainbridge.user.dto.response.DomainTagResponseDto;
import com.skala.domainbridge.user.repository.DomainTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 페르소나 설정(UC-02)과 세션 렌즈(UC-16)에서 고를 수 있는 도메인 태그 목록.
 *
 * SecurityConfig가 "/api/domain-tags"를 이미 permitAll로 열어두고 있었으나
 * 컨트롤러가 없어 404가 나던 상태였다. PersonaService.upsertPersona()는 DB에 없는
 * 태그명을 받으면 예외를 던지므로, 프론트가 고를 수 있는 값을 여기서 내려줘야 한다.
 */
@RestController
@RequestMapping("/api/domain-tags")
@RequiredArgsConstructor
public class DomainTagController {

    private final DomainTagRepository domainTagRepository;

    @GetMapping
    public ApiResponse<List<DomainTagResponseDto>> getAll() {
        List<DomainTagResponseDto> tags = domainTagRepository.findAll().stream()
                .map(DomainTagResponseDto::from)
                .toList();
        return ApiResponse.success(tags);
    }
}
