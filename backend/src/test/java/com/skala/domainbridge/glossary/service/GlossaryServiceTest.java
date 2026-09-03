package com.skala.domainbridge.glossary.service;

import com.skala.domainbridge.common.exception.CustomException;
import com.skala.domainbridge.common.exception.ErrorCode;
import com.skala.domainbridge.glossary.entity.Glossary;
import com.skala.domainbridge.glossary.repository.GlossaryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlossaryServiceTest {

    @Mock
    private GlossaryRepository glossaryRepository;

    @InjectMocks
    private GlossaryService glossaryService;

    @Test
    void 정상적으로_용어를_등록한다() {
        when(glossaryRepository.existsByTerm("TF")).thenReturn(false);
        when(glossaryRepository.save(any(Glossary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Glossary result = glossaryService.register("TF", "태스크포스", 1L);

        assertThat(result.getTerm()).isEqualTo("TF");
        assertThat(result.getOfficialDefinition()).isEqualTo("태스크포스");
        assertThat(result.getCreatedBy()).isEqualTo(1L);
    }

    @Test
    void 이미_등록된_용어면_예외를_던진다() {
        when(glossaryRepository.existsByTerm("TF")).thenReturn(true);

        assertThatThrownBy(() -> glossaryService.register("TF", "태스크포스", 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.GLOSSARY_TERM_DUPLICATED));
    }

    @Test
    void 등록된_용어를_매칭하면_결과를_반환한다() throws Exception {
        Glossary glossary = newGlossary(1L, "TF", "태스크포스");
        when(glossaryRepository.findByTerm("TF")).thenReturn(Optional.of(glossary));

        Optional<GlossaryMatchResult> result = glossaryService.match("TF");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(new GlossaryMatchResult(1L, "TF", "태스크포스"));
    }

    @Test
    void 매칭되는_용어가_없으면_빈_결과를_반환한다() {
        when(glossaryRepository.findByTerm("UNKNOWN")).thenReturn(Optional.empty());

        Optional<GlossaryMatchResult> result = glossaryService.match("UNKNOWN");

        assertThat(result).isEmpty();
    }

    private Glossary newGlossary(Long id, String term, String officialDefinition) throws Exception {
        Glossary glossary = Glossary.builder()
                .term(term)
                .officialDefinition(officialDefinition)
                .createdBy(1L)
                .build();
        Field idField = Glossary.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(glossary, id);
        return glossary;
    }
}
