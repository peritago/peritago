package com.skala.domainbridge.context.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 대화 맥락 (F-10) — RDB가 아니라 Redis LIST 슬라이딩 윈도우.
 *
 * 키를 사용자가 아니라 세션 기준(context:{sessionId})으로 잡는 이유:
 * 한 사용자가 여러 회의를 병행해도 맥락이 섞이지 않게 하기 위함.
 *
 * 적재 경로는 ContextController(POST /api/context/messages)이며, 프론트의 STT(F-15)가
 * 확정 문장을 보낼 때마다 호출된다. 문장이 한 건도 없으면 snapshot()은 null을 반환하고,
 * query.context_snapshot 도 NULL 로 남는다.
 */
@Service
@RequiredArgsConstructor
public class ContextService {

    private static final String KEY_PREFIX = "context:";
    private static final long WINDOW_SIZE = 5;
    private static final Duration TTL = Duration.ofHours(1);

    private final RedisTemplate<String, String> redisTemplate;

    /** STT 전사 문장 여러 건을 순서대로 적재한다. */
    public List<String> appendAll(Long sessionId, List<String> sentences) {
        sentences.forEach(sentence -> append(sessionId, sentence));
        return window(sessionId);
    }

    /** STT 전사 문장 1건 적재. 윈도우를 최근 WINDOW_SIZE 문장으로 유지하고 TTL을 갱신한다. */
    public void append(Long sessionId, String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return;
        }
        String key = key(sessionId);
        redisTemplate.opsForList().rightPush(key, sentence.trim());
        redisTemplate.opsForList().trim(key, -WINDOW_SIZE, -1);
        redisTemplate.expire(key, TTL);
    }

    public List<String> window(Long sessionId) {
        List<String> sentences = redisTemplate.opsForList().range(key(sessionId), 0, -1);
        return sentences == null ? List.of() : sentences;
    }

    /**
     * 질의 시점 맥락 스냅샷. 맥락이 없으면 null을 반환한다
     * (query.contextSnapshot 은 nullable 이며 Mock 단계에서는 항상 null).
     */
    public String snapshot(Long sessionId) {
        List<String> sentences = window(sessionId);
        return sentences.isEmpty() ? null : String.join("\n", sentences);
    }

    public void clear(Long sessionId) {
        redisTemplate.delete(key(sessionId));
    }

    private String key(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
