package com.skala.domainbridge.context.service;

import com.skala.domainbridge.context.dto.response.DetectedTermDto;
import com.skala.domainbridge.glossary.dto.response.GlossaryResponseDto;
import com.skala.domainbridge.glossary.service.GlossaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlangDetectorTest {

    @Mock private GlossaryService glossaryService;

    @InjectMocks private SlangDetector slangDetector;

    @Test
    void 등록된_은어가_문장에_있으면_감지한다() {
        사전(용어(1L, "TF", "태스크포스"));

        List<DetectedTermDto> result = slangDetector.detect(List.of("그럼 TF로 굴리는 게 맞겠네요."));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().term()).isEqualTo("TF");
        assertThat(result.getFirst().glossaryId()).isEqualTo(1L);
        assertThat(result.getFirst().officialDefinition()).isEqualTo("태스크포스");
    }

    @Test
    void 영문_용어가_다른_단어_안에_들어있으면_감지하지_않는다() {
        사전(용어(1L, "TF", "태스크포스"));

        List<DetectedTermDto> result = slangDetector.detect(List.of("PLATFORM 팀과 협의했습니다."));

        assertThat(result).isEmpty();
    }

    @Test
    void 영문_용어는_대소문자를_가리지_않는다() {
        사전(용어(1L, "TF", "태스크포스"));

        assertThat(slangDetector.detect(List.of("tf 구성 얘기가 나왔어요."))).hasSize(1);
    }

    @Test
    void 한글_은어는_조사가_붙어도_감지한다() {
        사전(용어(2L, "알잘딱깔센", "알아서 잘 딱 깔끔하고 센스 있게"));

        List<DetectedTermDto> result = slangDetector.detect(List.of("이번 건은 알잘딱깔센으로 부탁드립니다."));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().term()).isEqualTo("알잘딱깔센");
    }

    @Test
    void 같은_용어가_여러_문장에_나와도_한_번만_보고한다() {
        사전(용어(1L, "TF", "태스크포스"));

        List<DetectedTermDto> result = slangDetector.detect(
                List.of("TF 구성이 필요합니다.", "TF 인원은 몇 명인가요?"));

        assertThat(result).hasSize(1);
    }

    @Test
    void 여러_용어가_섞여_있으면_모두_감지한다() {
        사전(용어(1L, "TF", "태스크포스"), 용어(2L, "알잘딱깔센", "알아서 잘"));

        List<DetectedTermDto> result = slangDetector.detect(List.of("TF는 알잘딱깔센으로 갑시다."));

        assertThat(result).extracting(DetectedTermDto::term)
                .containsExactlyInAnyOrder("TF", "알잘딱깔센");
    }

    @Test
    void 사전이_비어_있으면_감지하지_않는다() {
        when(glossaryService.getAll()).thenReturn(List.of());

        assertThat(slangDetector.detect(List.of("TF로 갑시다."))).isEmpty();
    }

    @Test
    void 문장이_없으면_사전을_조회하지도_않는다() {
        assertThat(slangDetector.detect(List.of())).isEmpty();
        assertThat(slangDetector.detect(null)).isEmpty();

        verify(glossaryService, never()).getAll();
    }

    @Test
    void 사전_조회가_실패해도_문장_적재를_막지_않는다() {
        when(glossaryService.getAll()).thenThrow(new RuntimeException("DB 장애"));

        assertThat(slangDetector.detect(List.of("TF로 갑시다."))).isEmpty();
    }

    private void 사전(GlossaryResponseDto... terms) {
        when(glossaryService.getAll()).thenReturn(List.of(terms));
    }

    private GlossaryResponseDto 용어(Long id, String term, String definition) {
        return new GlossaryResponseDto(id, term, definition, LocalDateTime.now());
    }
}
