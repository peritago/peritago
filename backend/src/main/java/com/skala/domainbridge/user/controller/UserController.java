package com.skala.domainbridge.user.controller;

import com.skala.domainbridge.common.response.ApiResponse;
import com.skala.domainbridge.user.dto.request.UserCreateRequestDto;
import com.skala.domainbridge.user.dto.request.UserPersonaRequestDto;
import com.skala.domainbridge.user.dto.response.UserPersonaResponseDto;
import com.skala.domainbridge.user.dto.response.UserResponseDto;
import com.skala.domainbridge.user.service.PersonaService;
import com.skala.domainbridge.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "회원가입 · 내 정보 · 페르소나(도메인/설명 눈높이) 설정")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PersonaService personaService;

    @Operation(summary = "회원가입", description = "새 사용자를 등록한다.")
    @SecurityRequirements
    @PostMapping
    public ApiResponse<Long> createUser(@Valid @RequestBody UserCreateRequestDto request) {
        return ApiResponse.created(userService.createUser(request));
    }

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자 본인의 정보를 조회한다.")
    @GetMapping("/me")
    public ApiResponse<UserResponseDto> findMe(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(userService.findUser(userId));
    }

    @Operation(summary = "내 페르소나 조회", description = "번역 개인화에 쓰이는 본인의 페르소나(도메인 태그, 배경 설명, 설명 분량)를 조회한다.")
    @GetMapping("/me/persona")
    public ApiResponse<UserPersonaResponseDto> findMyPersona(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(personaService.findPersona(userId));
    }

    @Operation(summary = "내 페르소나 설정/수정", description = "번역 개인화에 쓰일 페르소나를 등록하거나 수정한다. 페르소나가 없으면 공식 정의만 제공된다.")
    @PutMapping("/me/persona")
    public ApiResponse<UserPersonaResponseDto> updateMyPersona(
            Authentication authentication, @RequestBody UserPersonaRequestDto request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(personaService.upsertPersona(userId, request));
    }
}