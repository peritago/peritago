package com.skala.domainbridge.translate.service;

import com.skala.domainbridge.common.exception.CustomException;
import com.skala.domainbridge.common.exception.ErrorCode;
import com.skala.domainbridge.context.service.ContextService;
import com.skala.domainbridge.glossary.service.GlossaryMatchResult;
import com.skala.domainbridge.glossary.service.GlossaryMatcher;
import com.skala.domainbridge.translate.cache.TranslationCache;
import com.skala.domainbridge.translate.dto.request.TranslateRequestDto;
import com.skala.domainbridge.translate.dto.response.TranslateResponseDto;
import com.skala.domainbridge.translate.entity.AiResponse;
import com.skala.domainbridge.translate.entity.ChatSession;
import com.skala.domainbridge.translate.entity.Query;
import com.skala.domainbridge.translate.entity.SourceType;
import com.skala.domainbridge.translate.port.TranslationGenerator;
import com.skala.domainbridge.translate.wiki.WikiEvidenceFinder;
import com.skala.domainbridge.translate.repository.AiResponseRepository;
import com.skala.domainbridge.translate.repository.ChatSessionRepository;
import com.skala.domainbridge.translate.repository.QueryRepository;
import com.skala.domainbridge.user.dto.response.UserPersonaResponseDto;
import com.skala.domainbridge.user.entity.ExplanationLength;
import com.skala.domainbridge.user.entity.User;
import com.skala.domainbridge.user.repository.UserRepository;
import com.skala.domainbridge.user.service.PersonaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslateServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final String TERM = "TF";

    @Mock private UserRepository userRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private QueryRepository queryRepository;
    @Mock private AiResponseRepository aiResponseRepository;
    @Mock private PersonaService personaService;
    @Mock private ContextService contextService;
    @Mock private GlossaryMatcher glossaryMatcher;
    @Mock private WikiEvidenceFinder wikiEvidenceFinder;
    @Mock private TranslationGenerator translationGenerator;
    @Mock private TranslationCache translationCache;

    @InjectMocks private TranslateService translateService;

    // --- 폴백 3단계 -------------------------------------------------------

    @Test
    void Glossary에_매칭되면_위키를_조회하지_않는다() {
        기본_스텁();
        when(glossaryMatcher.match(TERM))
                .thenReturn(Optional.of(new GlossaryMatchResult(7L, TERM, "태스크포스")));
        when(translationGenerator.generate(any())).thenReturn(생성결과("태스크포스", "쉬운 설명"));

        TranslateResponseDto result = translateService.translate(USER_ID, 요청());

        assertThat(result.sourceType()).isEqualTo(SourceType.GLOSSARY);
        assertThat(result.sourceRef()).isEqualTo("7");
        assertThat(result.outsideCompanyStandard()).isFalse();
        verify(wikiEvidenceFinder, never()).find(any(), any());
    }

    @Test
    void Glossary에_없으면_위키를_조회한다() {
        기본_스텁();
        when(glossaryMatcher.match(TERM)).thenReturn(Optional.empty());
        when(wikiEvidenceFinder.find(eq(TERM), any())).thenReturn(Optional.of(
                new WikiEvidenceFinder.WikiEvidence("TF는 한시 조직이다", "https://wiki/3", false,
                        List.of(new WikiEvidenceFinder.AnalogyCandidate("영업", "영업 파이프라인은 다섯 단계다", 0.52)))));
        when(translationGenerator.generate(any())).thenReturn(생성결과("위키 발췌", "쉬운 설명"));

        TranslateResponseDto result = translateService.translate(USER_ID, 요청());

        assertThat(result.sourceType()).isEqualTo(SourceType.WIKI);
        assertThat(result.sourceRef()).isEqualTo("https://wiki/3");
    }

    @Test
    void 근거가_전혀_없으면_GENERAL로_정상_응답한다() {
        기본_스텁();
        근거_없음();
        when(translationGenerator.generate(any())).thenReturn(생성결과("일반적인 의미", "쉬운 설명"));

        TranslateResponseDto result = translateService.translate(USER_ID, 요청());

        assertThat(result.sourceType()).isEqualTo(SourceType.GENERAL);
        assertThat(result.sourceRef()).isNull();
        assertThat(result.outsideCompanyStandard()).isTrue();
    }

    // --- 근거 전달 --------------------------------------------------------

    @Test
    void Glossary_근거는_프롬프트로_전달되고_공식정의는_LLM이_생성한다() {
        기본_스텁();
        when(glossaryMatcher.match(TERM))
                .thenReturn(Optional.of(new GlossaryMatchResult(7L, TERM, "등록된 공식 정의")));
        when(translationGenerator.generate(any()))
                .thenReturn(생성결과("LLM 이 다듬은 공식 정의", "쉬운 설명"));

        TranslateResponseDto result = translateService.translate(USER_ID, 요청());

        assertThat(result.officialDefinition()).isEqualTo("LLM 이 다듬은 공식 정의");

        ArgumentCaptor<TranslationGenerator.Command> captor =
                ArgumentCaptor.forClass(TranslationGenerator.Command.class);
        verify(translationGenerator).generate(captor.capture());
        assertThat(captor.getValue().evidence()).isEqualTo("등록된 공식 정의");
        assertThat(captor.getValue().sourceType()).isEqualTo(SourceType.GLOSSARY);
    }

    @Test
    void 페르소나가_없고_Glossary_근거가_있으면_LLM을_호출하지_않는다() {
        기본_스텁();
        when(personaService.findPersona(USER_ID)).thenReturn(페르소나_없음());
        when(glossaryMatcher.match(TERM))
                .thenReturn(Optional.of(new GlossaryMatchResult(7L, TERM, "등록된 공식 정의")));

        TranslateResponseDto result = translateService.translate(USER_ID, 요청());

        assertThat(result.officialDefinition()).isEqualTo("등록된 공식 정의");
        assertThat(result.personalizedExplanation()).contains("페르소나가 설정되지 않아");
        verify(translationGenerator, never()).generate(any());
        verify(translationCache, never()).find(any(), any());
        verify(wikiEvidenceFinder, never()).find(any(), any());
    }

    @Test
    void 페르소나가_없어도_Glossary_근거가_없으면_공식정의는_LLM이_생성한다() {
        기본_스텁();
        when(personaService.findPersona(USER_ID)).thenReturn(페르소나_없음());
        근거_없음();
        when(translationGenerator.generate(any())).thenReturn(생성결과("일반적인 의미", null));

        TranslateResponseDto result = translateService.translate(USER_ID, 요청());

        assertThat(result.officialDefinition()).isEqualTo("일반적인 의미");
        assertThat(result.personalizedExplanation()).contains("페르소나를 설정하면");
    }

    // --- 캐싱 (PM 결정: 맥락 없는 질의만) ---------------------------------

    @Test
    void 맥락이_없으면_캐시를_조회하고_적중하면_LLM을_호출하지_않는다() {
        기본_스텁();
        근거_없음();
        when(translationCache.isCacheable(any())).thenReturn(true);
        when(translationCache.find(any(), any()))
                .thenReturn(Optional.of(생성결과("캐시된 정의", "캐시된 설명")));

        TranslateResponseDto result = translateService.translate(USER_ID, 요청());

        assertThat(result.officialDefinition()).isEqualTo("캐시된 정의");
        verify(translationGenerator, never()).generate(any());
        verify(translationCache, never()).put(any(), any(), any());
    }

    @Test
    void 맥락이_있으면_캐시를_조회하지도_저장하지도_않는다() {
        기본_스텁();
        when(contextService.snapshot(SESSION_ID)).thenReturn("직전 대화 세 문장");
        when(translationCache.isCacheable(any())).thenReturn(false);
        근거_없음();
        when(translationGenerator.generate(any())).thenReturn(생성결과("일반적인 의미", "쉬운 설명"));

        translateService.translate(USER_ID, 요청());

        verify(translationCache, never()).find(any(), any());
        verify(translationCache, never()).put(any(), any(), any());
        verify(translationGenerator).generate(any());
    }

    // --- 실패 처리 --------------------------------------------------------

    @Test
    void LLM_호출이_실패하면_TRANSLATION_FAILED를_던진다() {
        질의까지_스텁(세션());
        근거_없음();
        when(translationGenerator.generate(any())).thenThrow(new RuntimeException("rate limit"));

        assertThatThrownBy(() -> translateService.translate(USER_ID, 요청()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSLATION_FAILED));

        verify(aiResponseRepository, never()).save(any());
    }

    @Test
    void 위키_조회가_실패하면_번역을_막지_않고_GENERAL로_떨어진다() {
        기본_스텁();
        when(glossaryMatcher.match(TERM)).thenReturn(Optional.empty());
        when(wikiEvidenceFinder.find(eq(TERM), any())).thenThrow(new RuntimeException("벡터 스토어 장애"));
        when(translationGenerator.generate(any())).thenReturn(생성결과("일반적인 의미", "쉬운 설명"));

        TranslateResponseDto result = translateService.translate(USER_ID, 요청());

        assertThat(result.sourceType()).isEqualTo(SourceType.GENERAL);

        // 조회 실패는 "근거 없음"과 구분되어야 한다 - 안내 문구가 달라진다.
        ArgumentCaptor<TranslationGenerator.Command> captor =
                ArgumentCaptor.forClass(TranslationGenerator.Command.class);
        verify(translationGenerator).generate(captor.capture());
        assertThat(captor.getValue().evidenceLookupFailed()).isTrue();
    }

    @Test
    void 조회_실패한_응답은_캐싱하지_않는다() {
        기본_스텁();
        when(glossaryMatcher.match(TERM)).thenReturn(Optional.empty());
        when(wikiEvidenceFinder.find(eq(TERM), any())).thenThrow(new RuntimeException("벡터 스토어 장애"));
        when(translationCache.isCacheable(any())).thenReturn(false);
        when(translationGenerator.generate(any())).thenReturn(생성결과("일반적인 의미", "쉬운 설명"));

        translateService.translate(USER_ID, 요청());

        verify(translationCache, never()).put(any(), any(), any());
    }

    @Test
    void 타인_세션이면_SESSION_NOT_FOUND를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(사용자()));
        when(chatSessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> translateService.translate(USER_ID, 요청()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    // --- 세션 상태 갱신 ---------------------------------------------------

    @Test
    void 첫_질의면_세션_제목이_채워지고_updatedAt이_갱신된다() {
        ChatSession session = 세션();
        기본_스텁(session);
        근거_없음();
        when(translationGenerator.generate(any())).thenReturn(생성결과("일반적인 의미", "쉬운 설명"));

        translateService.translate(USER_ID, 요청());

        assertThat(session.getTitle()).isEqualTo(TERM);
        assertThat(session.getUpdatedAt()).isNotNull();
    }

    // --- 헬퍼 -------------------------------------------------------------

    private TranslateRequestDto 요청() {
        return new TranslateRequestDto(SESSION_ID, TERM);
    }

    /** 세션 조회 ~ 질의 저장까지. 응답 저장 스텁은 포함하지 않는다(실패 경로에서는 호출되지 않으므로). */
    private void 질의까지_스텁(ChatSession session) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(사용자()));
        when(chatSessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        when(queryRepository.save(any(Query.class))).thenAnswer(i -> i.getArgument(0));
        when(personaService.findPersona(USER_ID)).thenReturn(페르소나_있음());
    }

    private void 기본_스텁() {
        기본_스텁(세션());
    }

    private void 기본_스텁(ChatSession session) {
        질의까지_스텁(session);
        when(aiResponseRepository.save(any(AiResponse.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** Glossary·위키 모두 미검색 상태로 만든다. */
    private void 근거_없음() {
        when(glossaryMatcher.match(TERM)).thenReturn(Optional.empty());
        when(wikiEvidenceFinder.find(eq(TERM), any())).thenReturn(Optional.empty());
    }

    private TranslationGenerator.Result 생성결과(String official, String personalized) {
        return new TranslationGenerator.Result(official, personalized, "gpt-4o-mini", "v2", 100, 500);
    }

    private UserPersonaResponseDto 페르소나_있음() {
        return new UserPersonaResponseDto(true, List.of("개발"), "백엔드 3년차",
                ExplanationLength.SHORT, ExplanationLength.MEDIUM);
    }

    private UserPersonaResponseDto 페르소나_없음() {
        return new UserPersonaResponseDto(false, List.of(), null, null, null);
    }

    private User 사용자() {
        return User.builder().email("me@peritago.dev").password("encoded").name("김영민").build();
    }

    private ChatSession 세션() {
        ChatSession session = ChatSession.builder().user(사용자()).build();
        setId(session, SESSION_ID);
        return session;
    }

    private void setId(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
