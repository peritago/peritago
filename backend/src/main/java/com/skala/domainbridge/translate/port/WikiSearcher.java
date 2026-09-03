package com.skala.domainbridge.translate.port;

import java.util.Optional;

/**
 * 폴백 2단계 — 사내 위키 벡터 검색(RAG, F-13).
 *
 * 실제 구현은 wiki 도메인(정웅기) 담당. 명세서상 MVP에서는 Mock 응답으로 대체 가능하다.
 */
public interface WikiSearcher {

    Optional<WikiMatch> searchTop(String term);

    record WikiMatch(Long documentId, String title, String excerpt, String sourceUrl) {}
}
