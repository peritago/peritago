package com.skala.domainbridge.translate.service;

import com.skala.domainbridge.common.exception.CustomException;
import com.skala.domainbridge.common.exception.ErrorCode;
import com.skala.domainbridge.context.service.ContextService;
import com.skala.domainbridge.glossary.service.GlossaryMatcher;
import com.skala.domainbridge.translate.cache.TranslationCache;
import com.skala.domainbridge.translate.dto.request.TranslateRequestDto;
import com.skala.domainbridge.translate.dto.response.TranslateResponseDto;
import com.skala.domainbridge.translate.entity.AiResponse;
import com.skala.domainbridge.translate.entity.ChatSession;
import com.skala.domainbridge.translate.entity.Query;
import com.skala.domainbridge.translate.entity.SourceType;
import com.skala.domainbridge.translate.port.TranslationGenerator;
import com.skala.domainbridge.translate.port.WikiSearcher;
import com.skala.domainbridge.translate.repository.AiResponseRepository;
import com.skala.domainbridge.translate.repository.ChatSessionRepository;
import com.skala.domainbridge.translate.repository.QueryRepository;
import com.skala.domainbridge.user.dto.response.UserPersonaResponseDto;
import com.skala.domainbridge.user.entity.User;
import com.skala.domainbridge.user.repository.UserRepository;
import com.skala.domainbridge.translate.dto.response.PageResponseDto;
import com.skala.domainbridge.user.service.PersonaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 용어 번역 오케스트레이션 (F-04 ~ F-07).
 *
 * 자체 Entity 없이 glossary / wiki / user(persona) / context 를 조합해 최종 응답을 만든다.
 * 근거 확보는 3단계 폴백: Glossary Exact Match → 위키 벡터 검색(RAG) → LLM 일반 지식.
 * 미등록 용어도 예외가 아니라 sourceType=GENERAL 로 200 정상 응답한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TranslateService {

    /** 페르소나 미설정 상태에서는 공식 정의만 제공하고, 개인화 파트는 이 안내로 대체한다. */
    private static final String PERSONA_REQUIRED_NOTICE =
            "페르소나가 설정되지 않아 개인화 설명을 제공할 수 없습니다. 페르소나를 설정하면 본인 도메인 눈높이에 맞춘 설명을 받을 수 있습니다.";

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final QueryRepository queryRepository;
    private final AiResponseRepository aiResponseRepository;
    private final PersonaService personaService;
    private final ContextService contextService;
    private final GlossaryMatcher glossaryMatcher;
    private final WikiSearcher wikiSearcher;
    private final TranslationGenerator translationGenerator;
    private final TranslationCache translationCache;

    @Transactional
    public TranslateResponseDto translate(Long userId, TranslateRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 세션은 사용자 소유 자원 — 소유자까지 함께 조회해 타인 세션 접근을 차단한다.
        ChatSession session = chatSessionRepository.findByIdAndUserId(request.sessionId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        String term = request.normalizedTerm();
        String contextSnapshot = contextService.snapshot(session.getId());

        Query query = queryRepository.save(Query.builder()
                .user(user)
                .session(session)
                .term(term)
                .contextSnapshot(contextSnapshot)
                .build());

        // 첫 질의면 세션 제목을 채우고, 세션 목록의 최근 대화순 정렬을 위해 updatedAt을 갱신한다.
        session.initTitleIfAbsent(term);
        session.touch();

        UserPersonaResponseDto persona = personaService.findPersona(userId);
        Evidence evidence = resolveEvidence(term);

        TranslationGenerator.Result generated = generate(term, contextSnapshot, evidence, persona);

        AiResponse response = aiResponseRepository.save(AiResponse.builder()
                .query(query)
                .officialDefinition(generated.officialDefinition())
                .personalizedExplanation(
                        persona.exists() ? generated.personalizedExplanation() : PERSONA_REQUIRED_NOTICE)
                .sourceType(evidence.sourceType())
                .sourceRef(evidence.sourceRef())
                .model(generated.model())
                .promptVersion(generated.promptVersion())
                .tokenUsage(generated.tokenUsage())
                .latencyMs(generated.latencyMs())
                .build());

        return TranslateResponseDto.of(query, response);
    }

    /**
     * 질의 이력 조회 (F-08) — 본인 질의만 최신순 페이징.
     *
     * 질의 건마다 응답을 단건 조회하면 N+1이 되므로 queryId 목록으로 한 번에 조회해 맵으로 조립한다.
     */
    public PageResponseDto<TranslateResponseDto> findHistory(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Page<Query> queries = queryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);

        if (queries.isEmpty()) {
            return PageResponseDto.of(List.of(), queries);
        }

        List<Long> queryIds = queries.getContent().stream().map(Query::getId).toList();
        Map<Long, AiResponse> responses = aiResponseRepository.findByQueryIdIn(queryIds).stream()
                .collect(Collectors.toMap(response -> response.getQuery().getId(), Function.identity()));

        // 질의와 응답은 같은 트랜잭션에서 함께 저장되므로 짝이 없는 질의는 정상 흐름에서 나오지 않는다. 방어적으로 제외한다.
        List<TranslateResponseDto> content = queries.getContent().stream()
                .filter(query -> responses.containsKey(query.getId()))
                .map(query -> TranslateResponseDto.of(query, responses.get(query.getId())))
                .toList();

        return PageResponseDto.of(content, queries);
    }

    /**
     * 응답 생성 위임. 맥락이 없는 질의는 캐시를 먼저 조회하고, 생성 결과를 캐시에 넣는다(F-14).
     * 맥락이 있는 질의는 캐싱하지 않는다 - 사유는 TranslationCache 주석 참고.
     *
     * 외부 LLM 호출은 rate limit·네트워크 오류·응답 시간 초과로 실패할 수 있으므로
     * 여기서 한 번 감싸 CustomException 으로 바꾼다. 그래야 GlobalExceptionHandler 가
     * 팀 공통 ApiResponse 봉투로 503 을 내려주고, 프론트가 기본 500 본문을 파싱하다 깨지지 않는다.
     *
     * 이 메서드가 던지면 트랜잭션이 롤백되어 방금 저장한 Query 도 사라진다.
     * 질의와 응답은 1:1 이어야 하므로, 응답 없는 질의가 이력에 남지 않는 편이 맞다.
     */
    private TranslationGenerator.Result generate(String term, String contextSnapshot,
                                                 Evidence evidence, UserPersonaResponseDto persona) {
        TranslationGenerator.Command command = new TranslationGenerator.Command(
                term,
                contextSnapshot,
                evidence.sourceType(),
                evidence.officialDefinition(),
                persona.domainTags(),
                persona.personaDescription(),
                persona.officialDefLength(),
                persona.personalizedExpLength(),
                persona.exists());

        boolean cacheable = translationCache.isCacheable(command);
        if (cacheable) {
            Optional<TranslationGenerator.Result> cached = translationCache.find(command);
            if (cached.isPresent()) {
                log.debug("번역 캐시 적중. term={}", term);
                return cached.get();
            }
        }

        TranslationGenerator.Result result;
        try {
            result = translationGenerator.generate(command);
        } catch (Exception e) {
            log.error("응답 생성 실패. term={}, sourceType={}", term, evidence.sourceType(), e);
            throw new CustomException(ErrorCode.TRANSLATION_FAILED);
        }

        if (cacheable) {
            translationCache.put(command, result);
        }
        return result;
    }

    /**
     * 3단계 폴백. Glossary에 걸리면 위키를 조회하지 않고, 위키에도 없으면 일반 지식으로 내려간다.
     */
    private Evidence resolveEvidence(String term) {
        return glossaryMatcher.match(term)
                .map(match -> new Evidence(
                        SourceType.GLOSSARY,
                        match.officialDefinition(),
                        String.valueOf(match.glossaryId())))
                .or(() -> wikiSearcher.searchTop(term)
                        .map(match -> new Evidence(
                                SourceType.WIKI,
                                match.excerpt(),
                                match.sourceUrl())))
                .orElseGet(() -> new Evidence(SourceType.GENERAL, null, null));
    }

    /** 폴백으로 확보한 근거. GENERAL이면 근거 원문과 출처가 없다. */
    private record Evidence(SourceType sourceType, String officialDefinition, String sourceRef) {}
}
