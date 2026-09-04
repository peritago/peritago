package com.skala.domainbridge.translate.port.mock;

import com.skala.domainbridge.translate.port.TranslationGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenAI 실연동 전까지 사용하는 임시 구현.
 * 인터페이스가 동일하므로 교체 시 TranslateService는 수정되지 않는다.
 */
@Component
@ConditionalOnProperty(name = "peritago.translate.mock.generator", havingValue = "true", matchIfMissing = true)
public class MockTranslationGenerator implements TranslationGenerator {

    @Override
    public String promptVersion() {
        return "mock";
    }

    @Override
    public Result generate(Command command) {
        String official = switch (command.sourceType()) {
            case GLOSSARY, WIKI -> command.evidence();
            case GENERAL -> "'%s'은(는) 사내 위키와 은어 사전에 등록되어 있지 않습니다. 일반적인 의미로 안내합니다. (Mock)"
                    .formatted(command.term());
        };

        String personalized = command.personalizationEnabled()
                ? buildPersonalized(command)
                : null;

        return new Result(official, personalized, null, null, null, null);
    }

    /**
     * ANALOGY 후보(WikiEvidenceFinder가 찾아준 타 도메인 비유)가 있으면 그걸 그대로 반영한다.
     * 실제 OpenAiTranslationGenerator가 "후보 중 닮은 것 하나를 골라 [분야] 쪽 사례에 빗대면 처럼
     * 쓰라"고 지시받는 것과 같은 모양을 흉내낸다 — 다만 LLM 없이 첫 번째 후보를 그대로 쓴다.
     */
    private String buildPersonalized(Command command) {
        if (command.analogies() != null && !command.analogies().isEmpty()) {
            TranslationGenerator.Analogy analogy = command.analogies().getFirst();
            return "[Mock] '%s'을(를) [%s] 쪽 사례에 빗대면: %s"
                    .formatted(command.term(), analogy.domain(), analogy.content());
        }

        return "[Mock] %s 도메인 기준으로 '%s'을(를) 풀어 설명합니다. 응답 분량 설정: %s."
                .formatted(
                        command.domainTags().isEmpty() ? "일반" : String.join("/", command.domainTags()),
                        command.term(),
                        command.personalizedExpLength());
    }
}
