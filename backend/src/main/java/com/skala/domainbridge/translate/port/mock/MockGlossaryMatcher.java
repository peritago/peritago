package com.skala.domainbridge.translate.port.mock;

import com.skala.domainbridge.translate.port.GlossaryMatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * glossary 도메인 구현 전까지 사용하는 임시 구현.
 * 실제 구현이 들어오면 application.yml 에 peritago.translate.mock=false 를 설정해 비활성화한다.
 */
@Component
@ConditionalOnProperty(name = "peritago.translate.mock", havingValue = "true", matchIfMissing = true)
public class MockGlossaryMatcher implements GlossaryMatcher {

    private static final Map<String, String> FIXTURES = Map.of(
            "tf", "Task Force. 특정 과제를 위해 부서를 가로질러 한시적으로 구성하는 조직.",
            "알잘딱깔센", "'알아서 잘 딱 깔끔하고 센스 있게'의 준말. 구체적 지시 없이 재량껏 처리하라는 의미.",
            "wbs", "Work Breakdown Structure. 프로젝트 산출물을 계층적으로 분해한 작업 명세."
    );

    @Override
    public Optional<GlossaryMatch> match(String term) {
        String key = term.toLowerCase(Locale.ROOT);
        return Optional.ofNullable(FIXTURES.get(key))
                .map(definition -> new GlossaryMatch((long) Math.abs(key.hashCode()), term, definition));
    }
}
