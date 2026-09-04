package com.skala.domainbridge.translate.wiki;

import com.skala.domainbridge.wiki.dto.AnalogySearchResult;
import com.skala.domainbridge.wiki.dto.Chunk;
import com.skala.domainbridge.wiki.dto.WikiSearchResponse;
import com.skala.domainbridge.wiki.service.AnalogySearchService;
import com.skala.domainbridge.wiki.service.WikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 위키 검색 결과를 translate 가 쓸 형태로 해석한다 (F-13).
 *
 * wiki 도메인이 돌려주는 값에는 해석 규칙이 붙어 있다(정웅기 님 인터페이스 노트 참고).
 * 그 규칙을 TranslateService 본문에 흩어놓지 않고 여기에 모은다.
 *
 * - 1차 결과의 chunks 순서는 RRF 가 매긴 것이므로 그대로 신뢰하고 최상위를 공식 정의 근거로 쓴다.
 * - 1차의 score 0.0 은 "관련성 없음"이 아니라 키워드 전용 매치라는 뜻이다. 진짜 유사도가 아니므로
 *   프롬프트에서 과신하지 않도록 표시만 하고, 후보에서 빼지는 않는다.
 * - 2차는 벡터 전용이라 score 가 도메인 간 비교 가능한 진짜 코사인 유사도다. 그래서 사용자가 고른
 *   태그별로 검색한 뒤 한 풀에 모아 score 순으로 고른다.
 * - sourceUrl 이 빈 문자열인 것은 "출처 링크만 없는 정상 근거"다. 근거 없음으로 취급하지 않는다.
 *
 * 조회 실패는 삼키지 않고 그대로 던진다. 여기서 빈 결과로 바꾸면 "근거가 없음"과 구분되지 않아,
 * 사용자에게 "등록된 정의가 없다"고 사실과 다르게 안내하게 된다. 실패 처리는 TranslateService 가 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiEvidenceFinder {

    /** 프롬프트에 넣을 비유 후보 상한. 실측상 청크가 약 300토큰이라 3개까지는 부담이 없다. */
    private static final int MAX_ANALOGIES = 3;

    /**
     * true(기본, 미설정 포함)면 아래 {@link #findMock}의 고정 데이터를 쓰고 VectorStore/ChatClient를
     * 전혀 호출하지 않는다 — OPENAI_API_KEY 없이 "위키에서 찾아 타 도메인으로 비유" 흐름을 그대로
     * 데모할 수 있게 하기 위한 스위치다. false면 실제 임베딩 검색 + HyDE 요약을 탄다.
     * (.env.example의 peritago.translate.mock.wiki 참고)
     */
    @Value("${peritago.translate.mock.wiki:true}")
    private boolean mockWiki;

    private final AnalogySearchService analogySearchService;
    private final WikiService wikiService;

    /**
     * @param userDomains 사용자가 페르소나에서 고른 도메인 태그. 비유 근거를 이 범위 안에서만 찾는다.
     *                    비어 있으면 비유 검색을 건너뛴다 - 필터 없이 검색하면 1차와 같은 문서가
     *                    다시 올라와 "MSA 를 MSA 로 설명하는" 순환이 된다.
     */
    public Optional<WikiEvidence> find(String term, List<String> userDomains) {
        if (mockWiki) {
            return findMock(term, userDomains);
        }
        if (userDomains == null || userDomains.isEmpty()) {
            return findWithoutAnalogy(term);
        }
        return findWithAnalogy(term, userDomains);
    }

    /**
     * 고정 데모 데이터셋. 등록된 용어면 근거를 돌려주고, 사용자 도메인에 맞는 비유가 준비돼 있으면
     * 같이 채워준다 — 실구현의 "닮은 게 없으면 억지로 만들지 않는다" 원칙을 그대로 따른다(그 태그의
     * 비유가 없으면 그냥 빈 리스트).
     */
    private Optional<WikiEvidence> findMock(String term, List<String> userDomains) {
        MockWikiDoc doc = MOCK_DOCS.get(normalizeForMock(term));
        if (doc == null) {
            return Optional.empty();
        }
        List<AnalogyCandidate> analogies = (userDomains == null ? List.<String>of() : userDomains).stream()
                .map(doc.analogiesByDomain()::get)
                .filter(Objects::nonNull)
                .limit(MAX_ANALOGIES)
                .toList();
        return Optional.of(new WikiEvidence(doc.officialSource(), doc.officialSourceUrl(), false, analogies));
    }

    private static String normalizeForMock(String term) {
        return term == null ? "" : term.trim().toUpperCase(Locale.ROOT);
    }

    private record MockWikiDoc(
            String officialSource,
            String officialSourceUrl,
            Map<String, AnalogyCandidate> analogiesByDomain
    ) {}

    /**
     * 데모용 고정 데이터. 새 용어를 추가하고 싶으면 여기에 항목만 추가하면 된다
     * (term은 대소문자 무시하고 매칭됨).
     */
    private static final Map<String, MockWikiDoc> MOCK_DOCS = Map.of(
            "MSA", new MockWikiDoc(
                    "MSA(Microservice Architecture)는 하나의 애플리케이션을 독립적으로 배포 가능한 "
                            + "여러 개의 작은 서비스로 나누어 구성하는 아키텍처 스타일이다. 각 서비스는 자체 "
                            + "데이터 저장소를 가지며 API(REST/gRPC 등)로 서로 통신한다. 서비스 단위로 독립적인 "
                            + "배포·확장·장애 격리가 가능하다는 장점이 있는 반면, 서비스 간 데이터 정합성 관리와 "
                            + "운영 복잡도가 커진다는 단점이 있다.",
                    "wiki://architecture-guide/msa",
                    Map.of("반도체", new AnalogyCandidate(
                            "반도체",
                            "반도체 팹(Fab)은 전체 공정을 감광(포토)·식각·증착·세정 등 여러 개의 독립된 "
                                    + "공정 모듈로 나누고, 모듈마다 전담 장비가 따로 있다. 모듈 간에는 웨이퍼 "
                                    + "캐리어(FOUP)라는 표준 규격 용기로 웨이퍼를 주고받으며, 특정 모듈만 "
                                    + "따로 업그레이드하거나 증설해도 다른 모듈에는 영향이 없다. 전체 공정 "
                                    + "순서와 상태는 MES(제조실행시스템)가 중앙에서 조율한다.",
                            0.87))
            )
    );

    /**
     * 페르소나가 없으면 비유를 만들 기준이 없다. 1차 검색만 하고 끝낸다 -
     * AnalogySearchService 를 부르면 쓰지도 않을 구조 요약에 LLM 을 호출하게 된다.
     */
    private Optional<WikiEvidence> findWithoutAnalogy(String term) {
        WikiSearchResponse first = wikiService.search(term, null);
        return toEvidence(first, List.of());
    }

    private Optional<WikiEvidence> findWithAnalogy(String term, List<String> userDomains) {
        String primaryDomain = userDomains.getFirst();
        AnalogySearchResult result = analogySearchService.search(term, primaryDomain);

        WikiSearchResponse first = result.firstSearchResult();
        if (!first.found() || first.chunks().isEmpty()) {
            return Optional.empty();
        }

        List<AnalogyCandidate> candidates = new ArrayList<>(
                toCandidates(primaryDomain, result.secondSearchResult()));

        // 나머지 태그는 구조 요약(llmResult)을 재사용해 2차 검색만 다시 돌린다. LLM 호출은 늘지 않는다.
        String structureQuery = result.llmResult();
        if (structureQuery != null && !structureQuery.isBlank()) {
            userDomains.stream()
                    .skip(1)
                    .forEach(domain -> candidates.addAll(
                            toCandidates(domain, wikiService.searchVectorOnly(structureQuery, domain))));
        }

        return toEvidence(first, selectAnalogies(candidates, first.chunks().getFirst()));
    }

    private Optional<WikiEvidence> toEvidence(WikiSearchResponse first, List<AnalogyCandidate> analogies) {
        if (!first.found() || first.chunks().isEmpty()) {
            return Optional.empty();
        }
        Chunk top = first.chunks().getFirst();
        return Optional.of(new WikiEvidence(
                top.content(),
                top.sourceUrl(),
                top.score() == 0.0,
                analogies));
    }

    /**
     * 태그와 무관하게 순수 score 순으로 고른다. 후보는 모두 사용자가 고른 태그에서 나온 것이라
     * 이미 "이해 가능한 분야"라는 조건을 통과했다. 그다음부터 구분할 이유가 없다.
     *
     * 1차와 같은 문서는 제외한다. 사용자 도메인이 그 용어의 출신 도메인과 같으면 2차가 1차와
     * 같은 문서를 최상위로 돌려주는데(실측: 같은 도메인일 때 score 0.634), 그건 비유가 아니라
     * 원문 반복이고 토큰만 쓴다.
     */
    private List<AnalogyCandidate> selectAnalogies(List<AnalogyCandidate> candidates, Chunk officialChunk) {
        return candidates.stream()
                .filter(candidate -> !candidate.content().equals(officialChunk.content()))
                .sorted(Comparator.comparingDouble(AnalogyCandidate::score).reversed())
                .limit(MAX_ANALOGIES)
                .toList();
    }

    private List<AnalogyCandidate> toCandidates(String domain, WikiSearchResponse response) {
        if (response == null || !response.found()) {
            return List.of();
        }
        return response.chunks().stream()
                .map(chunk -> new AnalogyCandidate(domain, chunk.content(), chunk.score()))
                .toList();
    }

    /**
     * @param keywordOnlyMatch 1차 근거가 키워드로만 매치된 경우. 프롬프트에서 LLM 이 과신하지 않도록 표시한다.
     */
    public record WikiEvidence(
            String officialSource,
            String officialSourceUrl,
            boolean keywordOnlyMatch,
            List<AnalogyCandidate> analogies
    ) {}

    /** @param domain 이 근거가 어느 분야 문서인지. 선택에는 쓰지 않고 프롬프트 표기에만 쓴다. */
    public record AnalogyCandidate(String domain, String content, double score) {}
}
