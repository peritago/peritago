package com.skala.domainbridge.glossary.service;

import com.skala.domainbridge.common.exception.CustomException;
import com.skala.domainbridge.common.exception.ErrorCode;
import com.skala.domainbridge.glossary.entity.Glossary;
import com.skala.domainbridge.glossary.repository.GlossaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlossaryService implements GlossaryMatcher {

    private final GlossaryRepository glossaryRepository;

    @Transactional
    public Glossary register(String term, String officialDefinition, Long adminId) {
        if (glossaryRepository.existsByTerm(term)) {
            throw new CustomException(ErrorCode.GLOSSARY_TERM_DUPLICATED);
        }
        Glossary glossary = Glossary.builder()
                .term(term)
                .officialDefinition(officialDefinition)
                .createdBy(adminId)
                .build();
        return glossaryRepository.save(glossary);
    }

    @Override
    public Optional<GlossaryMatchResult> match(String term) {
        return glossaryRepository.findByTerm(term)
                .map(g -> new GlossaryMatchResult(g.getId(), g.getTerm(), g.getOfficialDefinition()));
    }
}
