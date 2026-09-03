package com.skala.domainbridge.translate.port;

import java.util.Optional;

/**
 * 폴백 1단계 — 은어 사전 Exact Match (F-05).
 *
 * REST로 노출하지 않고 같은 서버 내 서비스 메서드로 호출한다(모듈러 모놀리스).
 * 실제 구현은 glossary 도메인(강주현) 담당이며, 그때까지는 Mock 구현이 주입된다.
 */
public interface GlossaryMatcher {

    Optional<GlossaryMatch> match(String term);

    record GlossaryMatch(Long glossaryId, String term, String officialDefinition) {}
}
