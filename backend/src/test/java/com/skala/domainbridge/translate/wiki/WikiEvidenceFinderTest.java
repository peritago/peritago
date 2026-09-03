package com.skala.domainbridge.translate.wiki;

import com.skala.domainbridge.translate.wiki.WikiEvidenceFinder.WikiEvidence;
import com.skala.domainbridge.wiki.dto.AnalogySearchResult;
import com.skala.domainbridge.wiki.dto.Chunk;
import com.skala.domainbridge.wiki.dto.WikiSearchResponse;
import com.skala.domainbridge.wiki.service.AnalogySearchService;
import com.skala.domainbridge.wiki.service.WikiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WikiEvidenceFinderTest {

    private static final String TERM = "배포 파이프라인";
    private static final String LLM_RESULT = "네 단계로 이루어진 순차적 파이프라인";

    @Mock private AnalogySearchService analogySearchService;
    @Mock private WikiService wikiService;

    @InjectMocks private WikiEvidenceFinder wikiEvidenceFinder;

    @Test
    void 위키에_없으면_빈_결과를_준다() {
        when(analogySearchService.search(TERM, "개발"))
                .thenReturn(new AnalogySearchResult(WikiSearchResponse.notFound(), null, WikiSearchResponse.notFound()));

        assertThat(wikiEvidenceFinder.find(TERM, List.of("개발"))).isEmpty();
    }

    @Test
    void 공식_정의_근거는_일차_최상위_청크를_쓴다() {
        when(analogySearchService.search(TERM, "개발")).thenReturn(new AnalogySearchResult(
                found(chunk("1등 근거", 0.42, "https://wiki/1"), chunk("2등 근거", 0.31, "https://wiki/2")),
                LLM_RESULT,
                WikiSearchResponse.notFound()));

        WikiEvidence evidence = wikiEvidenceFinder.find(TERM, List.of("개발")).orElseThrow();

        assertThat(evidence.officialSource()).isEqualTo("1등 근거");
        assertThat(evidence.officialSourceUrl()).isEqualTo("https://wiki/1");
        assertThat(evidence.keywordOnlyMatch()).isFalse();
    }

    @Test
    void 일차_score가_0이면_키워드_전용_매치로_표시한다() {
        when(analogySearchService.search(TERM, "개발")).thenReturn(new AnalogySearchResult(
                found(chunk("글자만 겹친 근거", 0.0, "")), LLM_RESULT, WikiSearchResponse.notFound()));

        WikiEvidence evidence = wikiEvidenceFinder.find(TERM, List.of("개발")).orElseThrow();

        assertThat(evidence.keywordOnlyMatch()).isTrue();
    }

    @Test
    void sourceUrl이_빈_문자열이어도_근거_없음으로_보지_않는다() {
        when(analogySearchService.search(TERM, "개발")).thenReturn(new AnalogySearchResult(
                found(chunk("출처 링크 없는 근거", 0.5, "")), LLM_RESULT, WikiSearchResponse.notFound()));

        WikiEvidence evidence = wikiEvidenceFinder.find(TERM, List.of("개발")).orElseThrow();

        assertThat(evidence.officialSource()).isEqualTo("출처 링크 없는 근거");
        assertThat(evidence.officialSourceUrl()).isEmpty();
    }

    @Test
    void 나머지_태그는_구조_요약을_재사용해_2차만_다시_검색한다() {
        when(analogySearchService.search(TERM, "개발")).thenReturn(new AnalogySearchResult(
                found(chunk("개발 근거", 0.4, "https://wiki/1")),
                LLM_RESULT,
                found(chunk("개발 비유", 0.30, "https://wiki/1a"))));
        when(wikiService.searchVectorOnly(LLM_RESULT, "영업"))
                .thenReturn(found(chunk("영업 비유", 0.52, "https://wiki/2")));

        WikiEvidence evidence = wikiEvidenceFinder.find(TERM, List.of("개발", "영업")).orElseThrow();

        // 구조 요약 LLM 호출은 한 번뿐이어야 한다.
        verify(analogySearchService).search(TERM, "개발");
        verify(wikiService).searchVectorOnly(LLM_RESULT, "영업");
        assertThat(evidence.analogies()).extracting(WikiEvidenceFinder.AnalogyCandidate::domain)
                .containsExactly("영업", "개발");
    }

    @Test
    void 비유_후보는_태그와_무관하게_score_순으로_고른다() {
        when(analogySearchService.search(TERM, "기획")).thenReturn(new AnalogySearchResult(
                found(chunk("기획 근거", 0.4, "https://wiki/1")),
                LLM_RESULT,
                found(chunk("기획 비유", 0.398, "https://wiki/a"))));
        when(wikiService.searchVectorOnly(LLM_RESULT, "영업"))
                .thenReturn(found(chunk("영업 비유", 0.521, "https://wiki/b")));
        when(wikiService.searchVectorOnly(LLM_RESULT, "경영"))
                .thenReturn(found(chunk("경영 비유", 0.463, "https://wiki/c")));

        WikiEvidence evidence = wikiEvidenceFinder
                .find(TERM, List.of("기획", "영업", "경영")).orElseThrow();

        assertThat(evidence.analogies()).extracting(WikiEvidenceFinder.AnalogyCandidate::content)
                .containsExactly("영업 비유", "경영 비유", "기획 비유");
    }

    @Test
    void 최대_세_개까지만_비유_후보로_넘긴다() {
        when(analogySearchService.search(TERM, "개발")).thenReturn(new AnalogySearchResult(
                found(chunk("근거", 0.4, "https://wiki/1")),
                LLM_RESULT,
                found(chunk("a", 0.9, ""), chunk("b", 0.8, ""), chunk("c", 0.7, ""), chunk("d", 0.6, ""))));

        WikiEvidence evidence = wikiEvidenceFinder.find(TERM, List.of("개발")).orElseThrow();

        assertThat(evidence.analogies()).hasSize(3);
    }

    @Test
    void 일차와_같은_문서는_비유_후보에서_제외한다() {
        when(analogySearchService.search(TERM, "개발")).thenReturn(new AnalogySearchResult(
                found(chunk("같은 문서 내용", 0.4, "https://wiki/1")),
                LLM_RESULT,
                found(chunk("같은 문서 내용", 0.634, "https://wiki/1"),
                        chunk("다른 문서 내용", 0.42, "https://wiki/2"))));

        WikiEvidence evidence = wikiEvidenceFinder.find(TERM, List.of("개발")).orElseThrow();

        assertThat(evidence.analogies()).extracting(WikiEvidenceFinder.AnalogyCandidate::content)
                .containsExactly("다른 문서 내용");
    }

    @Test
    void 페르소나_태그가_없으면_비유_검색을_건너뛴다() {
        when(wikiService.search(TERM, null)).thenReturn(found(chunk("근거", 0.4, "https://wiki/1")));

        WikiEvidence evidence = wikiEvidenceFinder.find(TERM, List.of()).orElseThrow();

        assertThat(evidence.analogies()).isEmpty();
        // 쓰지도 않을 구조 요약에 LLM 을 호출하면 안 된다.
        verify(analogySearchService, never()).search(any(), any());
    }

    @Test
    void 조회_실패는_삼키지_않고_그대로_던진다() {
        // 빈 결과로 바꾸면 "근거 없음"과 구분되지 않아 사용자에게 사실과 다른 안내가 나간다.
        when(analogySearchService.search(TERM, "개발")).thenThrow(new RuntimeException("벡터 스토어 장애"));

        assertThatThrownBy(() -> wikiEvidenceFinder.find(TERM, List.of("개발")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("벡터 스토어 장애");
    }

    private WikiSearchResponse found(Chunk... chunks) {
        return new WikiSearchResponse(true, List.of(chunks));
    }

    private Chunk chunk(String content, double score, String sourceUrl) {
        return new Chunk(content, score, sourceUrl);
    }
}
