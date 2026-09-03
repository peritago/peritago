package com.skala.domainbridge.auth.controller;

import com.skala.domainbridge.auth.dto.request.LoginRequestDto;
import com.skala.domainbridge.auth.dto.request.ReissueRequestDto;
import com.skala.domainbridge.auth.dto.response.TokenResponseDto;
import com.skala.domainbridge.auth.jwt.JwtTokenProvider;
import com.skala.domainbridge.auth.service.AuthService;
import com.skala.domainbridge.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ApiResponse<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/reissue")
    public ApiResponse<TokenResponseDto> reissue(@Valid @RequestBody ReissueRequestDto request) {
        return ApiResponse.success(authService.reissue(request.refreshToken()));
    }

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