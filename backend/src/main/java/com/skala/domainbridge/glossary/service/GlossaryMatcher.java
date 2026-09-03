package com.skala.domainbridge.glossary.service;

import java.util.Optional;

/**
 * translate 도메인 등 다른 도메인이 같은 서버 내에서 직접 주입받아 호출하는 사내 API.
 * REST로 노출하지 않는다.
 */
public interface GlossaryMatcher {
    Optional<GlossaryMatchResult> match(String term);
}
