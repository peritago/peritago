package com.skala.domainbridge.context.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextServiceTest {

    private static final Long SESSION_ID = 10L;
    private static final String KEY = "context:10";

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ListOperations<String, String> listOperations;

    private ContextService contextService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        contextService = new ContextService(redisTemplate);
    }

    @Test
    void 문장을_적재하면_최근_5문장으로_자르고_TTL을_한_시간으로_갱신한다() {
        when(listOperations.range(KEY, 0, -1)).thenReturn(List.of("문장"));

        contextService.appendAll(SESSION_ID, List.of("문장"));

        verify(listOperations).rightPush(KEY, "문장");
        verify(listOperations).trim(KEY, -5, -1);
        verify(redisTemplate).expire(KEY, Duration.ofHours(1));
    }

    @Test
    void 세션마다_키가_분리된다() {
        when(listOperations.range("context:99", 0, -1)).thenReturn(List.of());

        contextService.appendAll(99L, List.of("다른 회의 문장"));

        verify(listOperations).rightPush("context:99", "다른 회의 문장");
    }

    @Test
    void 빈_문장은_적재하지_않는다() {
        when(listOperations.range(KEY, 0, -1)).thenReturn(List.of());

        contextService.appendAll(SESSION_ID, java.util.Arrays.asList("   ", null));

        verify(listOperations, never()).rightPush(any(), any());
        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    void 적재된_문장이_없으면_스냅샷은_null이다() {
        when(listOperations.range(KEY, 0, -1)).thenReturn(List.of());

        assertThat(contextService.snapshot(SESSION_ID)).isNull();
    }

    @Test
    void 키가_아예_없어도_스냅샷은_null이다() {
        when(listOperations.range(KEY, 0, -1)).thenReturn(null);

        assertThat(contextService.snapshot(SESSION_ID)).isNull();
        assertThat(contextService.window(SESSION_ID)).isEmpty();
    }

    @Test
    void 스냅샷은_문장을_개행으로_이어_붙인다() {
        when(listOperations.range(KEY, 0, -1)).thenReturn(List.of("첫 문장", "둘째 문장"));

        assertThat(contextService.snapshot(SESSION_ID)).isEqualTo("첫 문장\n둘째 문장");
    }
}
