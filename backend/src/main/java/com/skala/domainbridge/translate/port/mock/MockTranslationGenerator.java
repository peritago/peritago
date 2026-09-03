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
                ? "[Mock] %s 도메인 기준으로 '%s'을(를) 풀어 설명합니다. 응답 분량 설정: %s."
                        .formatted(
                                command.domainTags().isEmpty() ? "일반" : String.join("/", command.domainTags()),
                                command.term(),
                                command.personalizedExpLength())
                : null;

        return new Result(official, personalized, null, null, null, null);
    }
}
