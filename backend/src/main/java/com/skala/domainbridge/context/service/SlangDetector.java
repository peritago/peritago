package com.skala.domainbridge.context.service;

import com.skala.domainbridge.context.dto.response.DetectedTermDto;
import com.skala.domainbridge.glossary.dto.response.GlossaryResponseDto;
import com.skala.domainbridge.glossary.service.GlossaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 대화 문장에서 사내 은어를 자동 감지한다 (F-11).
 *
 * 기획서의 원래 비전은 "회의 중 은어가 나오면 자동으로 잡아준다" 였다.
 * 사용자가 스스로 인지한 모름을 채우는 수동 질의(F-04)와 달리, 이쪽은 사용자가
 * 무엇을 모르는지조차 모르는 용어를 잡아 준다.
 *
 * 감지 자체는 등록된 은어 사전과의 문자열 대조라 LLM 을 호출하지 않는다.
 * 즉 토큰 비용 없이 동작하며, 사용자가 그 용어를 실제로 물어볼 때만 LLM 이 개입한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlangDetector {

    private final GlossaryService glossaryService;

    /**
     * 새로 적재된 문장에서만 감지한다. 윈도우 전체를 훑으면 같은 용어가 매번 다시 보고된다.
     *
     * 감지 실패가 문장 적재를 막아서는 안 되므로 예외는 삼키고 빈 목록을 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<DetectedTermDto> detect(List<String> sentences) {
        if (sentences == null || sentences.isEmpty()) {
            return List.of();
        }
        try {
            List<GlossaryResponseDto> glossaries = glossaryService.getAll();
            if (glossaries.isEmpty()) {
                return List.of();
            }

            // 같은 용어가 여러 문장에 나와도 한 번만 보고한다.
            Map<String, DetectedTermDto> detected = new LinkedHashMap<>();
            for (String sentence : sentences) {
                if (sentence == null || sentence.isBlank()) {
                    continue;
                }
                for (GlossaryResponseDto glossary : glossaries) {
                    if (detected.containsKey(glossary.term())) {
                        continue;
                    }
                    if (contains(sentence, glossary.term())) {
                        detected.put(glossary.term(), new DetectedTermDto(
                                glossary.id(), glossary.term(), glossary.officialDefinition()));
                    }
                }
            }
            return new ArrayList<>(detected.values());
        } catch (Exception e) {
            log.warn("은어 자동 감지 실패 - 빈 결과로 계속 진행합니다.", e);
            return List.of();
        }
    }

    /**
     * 영문/숫자로만 이루어진 용어는 단어 경계를 요구한다.
     * 그러지 않으면 "TF" 가 "PLATFORM" 안에서도 걸린다.
     *
     * 한글이 섞인 용어는 교착어 특성상 조사가 바로 붙으므로("알잘딱깔센으로") 부분 문자열로 찾는다.
     * 영문 용어에 조사가 붙는 경우("TF로")는 조사가 단어 문자가 아니라 경계가 성립해 정상 감지된다.
     */
    private boolean contains(String sentence, String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        if (isAscii(term)) {
            return Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(sentence)
                    .find();
        }
        return sentence.contains(term);
    }

    private boolean isAscii(String term) {
        return term.chars().allMatch(c -> c < 128);
    }
}
