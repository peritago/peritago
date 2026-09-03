package com.skala.domainbridge.translate.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.domainbridge.translate.port.TranslationGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 번역 응답 캐시 (F-14).
 *
 * 맥락(contextSnapshot)이 있는 질의는 캐싱하지 않는다. 슬라이딩 윈도우는 문장이 한 건만
 * 들어와도 통째로 바뀌어 재사용이 사실상 일어나지 않는 반면, 맥락을 키에서 빼면
 * 다른 대화의 답변이 잘못 재사용된다(예: ML 회의의 "TF" 질문에 조직 회의의 Task Force 답변).
 * 그래서 맥락 없는 질의로 대상을 한정한다. (PM 결정 사항)
 *
 * 캐시 장애가 번역 실패로 번지면 안 되므로 모든 Redis 접근은 실패를 삼키고 미스로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranslationCache {

    private static final String KEY_PREFIX = "translate:";

    /** 관리자가 은어/위키를 수정하면 캐시가 낡는다. 짧게 잡아 최신성과 절감 사이를 절충한다. */
    private static final Duration TTL = Duration.ofHours(1);

    /**
     * 이 프로젝트에는 ObjectMapper 빈이 노출돼 있지 않다. 캐시 직렬화는 내부 저장 형식일 뿐
     * API 응답 규약과 무관하므로, 앱 설정에 의존하지 않도록 전용 인스턴스를 쓴다.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 맥락이 없는 질의만 캐싱 대상이다.
     * 사내 자료 조회에 실패한 응답은 캐싱하지 않는다 - 장애가 회복돼도 TTL 동안 실패 문구가 남는다.
     */
    public boolean isCacheable(TranslationGenerator.Command command) {
        boolean noContext = command.contextSnapshot() == null || command.contextSnapshot().isBlank();
        return noContext && !command.evidenceLookupFailed();
    }

    public Optional<TranslationGenerator.Result> find(TranslationGenerator.Command command, String promptVersion) {
        long startedAt = System.nanoTime();
        try {
            String cached = redisTemplate.opsForValue().get(key(command, promptVersion));
            if (cached == null) {
                return Optional.empty();
            }
            CachedTranslation value = OBJECT_MAPPER.readValue(cached, CachedTranslation.class);
            int latencyMs = (int) ((System.nanoTime() - startedAt) / 1_000_000);

            // 토큰을 실제로 쓰지 않았으므로 0 으로 기록한다.
            // 원본 호출의 토큰 수를 그대로 남기면 비용 집계가 과대계상된다.
            return Optional.of(new TranslationGenerator.Result(
                    value.officialDefinition(),
                    value.personalizedExplanation(),
                    value.model(),
                    value.promptVersion(),
                    0,
                    latencyMs));
        } catch (Exception e) {
            log.warn("번역 캐시 조회 실패 - 미스로 처리하고 계속 진행합니다. term={}", command.term(), e);
            return Optional.empty();
        }
    }

    public void put(TranslationGenerator.Command command, String promptVersion, TranslationGenerator.Result result) {
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(new CachedTranslation(
                    result.officialDefinition(),
                    result.personalizedExplanation(),
                    result.model(),
                    result.promptVersion()));
            redisTemplate.opsForValue().set(key(command, promptVersion), payload, TTL);
        } catch (Exception e) {
            log.warn("번역 캐시 저장 실패 - 무시하고 계속 진행합니다. term={}", command.term(), e);
        }
    }

    /**
     * 응답 내용을 바꾸는 모든 입력을 키에 넣는다.
     * 특히 personaDescription 은 자유 서술이라 같은 도메인 태그라도 설명이 달라지므로 반드시 포함한다.
     * 명세서의 "용어+도메인" 보다 좁은 기준이지만, 넓히면 다른 사람의 눈높이 설명이 재사용된다.
     */
    private String key(TranslationGenerator.Command command, String promptVersion) {
        List<String> tags = command.domainTags() == null
                ? List.of()
                : command.domainTags().stream().sorted().toList();
        String raw = String.join("|",
                // 프롬프트가 바뀌면 같은 입력도 다른 응답이 나온다. 버전을 키에 넣어 자동 무효화한다.
                promptVersion,
                command.term(),
                String.valueOf(command.sourceType()),
                String.valueOf(command.evidence()),
                String.join(",", tags),
                String.valueOf(command.personaDescription()),
                String.valueOf(command.officialDefLength()),
                String.valueOf(command.personalizedExpLength()),
                String.valueOf(command.personalizationEnabled()),
                String.valueOf(command.evidenceKeywordOnly()),
                // 비유 후보가 다르면 개인화 설명도 달라진다. 키에 넣지 않으면 다른 후보에 같은 답을 준다.
                command.analogies() == null ? "" : command.analogies().stream()
                        .map(a -> a.domain() + ":" + a.content())
                        .collect(java.util.stream.Collectors.joining("\u001f")));
        return KEY_PREFIX + sha256(raw);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
        }
    }

    /** Redis 에 저장하는 형태. 토큰/지연은 호출마다 달라지므로 저장하지 않는다. */
    private record CachedTranslation(
            String officialDefinition,
            String personalizedExplanation,
            String model,
            String promptVersion
    ) {}
}
