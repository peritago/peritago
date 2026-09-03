package com.skala.domainbridge.translate.port.mock;

import com.skala.domainbridge.translate.port.WikiSearcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * wiki 도메인(RAG) 구현 전까지 사용하는 임시 구현.
 * 항상 미검색으로 응답해 GENERAL 폴백 경로가 동작하는지 확인할 수 있게 한다.
 */
@Component
@ConditionalOnProperty(name = "peritago.translate.mock", havingValue = "true", matchIfMissing = true)
public class MockWikiSearcher implements WikiSearcher {

    @Override
    public Optional<WikiMatch> searchTop(String term) {
        return Optional.empty();
    }
}
