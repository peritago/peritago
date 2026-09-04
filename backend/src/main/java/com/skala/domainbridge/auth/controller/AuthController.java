package com.skala.domainbridge.auth.controller;

import com.skala.domainbridge.auth.dto.request.LoginRequestDto;
import com.skala.domainbridge.auth.dto.request.ReissueRequestDto;
import com.skala.domainbridge.auth.dto.response.TokenResponseDto;
import com.skala.domainbridge.auth.jwt.JwtTokenProvider;
import com.skala.domainbridge.auth.service.AuthService;
import com.skala.domainbridge.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "로그인 · 토큰 재발급 · 로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하고 액세스/리프레시 토큰을 발급받는다.")
    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 액세스 토큰을 재발급받는다.")
    @SecurityRequirements
    @PostMapping("/reissue")
    public ApiResponse<TokenResponseDto> reissue(@Valid @RequestBody ReissueRequestDto request) {
        return ApiResponse.success(authService.reissue(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "현재 액세스 토큰을 블랙리스트에 등록해 만료 전에도 무효화한다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String token = httpRequest.getHeader("Authorization").substring(7);
        Long userIdFromToken = jwtTokenProvider.getUserId(token);
        long remaining = 1000L * 60 * 30; // 단순화: 액세스 토큰 만료까지 남은 시간 계산 로직은 추후 보완
        authService.logout(userId, token, remaining);
        return ApiResponse.noContent();
    }
}