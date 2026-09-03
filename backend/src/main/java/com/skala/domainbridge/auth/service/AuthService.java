package com.skala.domainbridge.auth.service;

import com.skala.domainbridge.auth.dto.request.LoginRequestDto;
import com.skala.domainbridge.auth.dto.response.TokenResponseDto;
import com.skala.domainbridge.auth.jwt.JwtTokenProvider;
import com.skala.domainbridge.auth.jwt.TokenService;
import com.skala.domainbridge.common.exception.CustomException;
import com.skala.domainbridge.common.exception.ErrorCode;
import com.skala.domainbridge.user.entity.User;
import com.skala.domainbridge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public TokenResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        tokenService.saveRefreshToken(user.getId(), refreshToken);

        return new TokenResponseDto(accessToken, refreshToken);
    }

    public TokenResponseDto reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        if (!tokenService.isValidRefreshToken(userId, refreshToken)) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtTokenProvider.createAccessToken(userId, user.getRole());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        tokenService.saveRefreshToken(userId, newRefreshToken);

        return new TokenResponseDto(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId, String accessToken, long remainingMillis) {
        tokenService.deleteRefreshToken(userId);
        tokenService.blacklistAccessToken(accessToken, remainingMillis);
    }
}