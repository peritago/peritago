package com.skala.domainbridge.auth.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String REFRESH_PREFIX = "refresh:";
    private static final String BLACKLIST_PREFIX = "blacklist:";

    public void saveRefreshToken(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(REFRESH_PREFIX + userId, refreshToken, Duration.ofDays(14));
    }

    public boolean isValidRefreshToken(Long userId, String refreshToken) {
        String saved = redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
        return saved != null && saved.equals(refreshToken);
    }

    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId);
    }

    public void blacklistAccessToken(String accessToken, long remainingMillis) {
        if (remainingMillis <= 0) return;
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + accessToken, "logout", Duration.ofMillis(remainingMillis));
    }

    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + accessToken));
    }
}