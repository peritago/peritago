package com.skala.domainbridge.translate.port.openai;

import com.skala.domainbridge.translate.entity.SourceType;
import com.skala.domainbridge.translate.port.TranslationGenerator;
import com.skala.domainbridge.user.entity.ExplanationLength;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OpenAI 실연동 구현 (F-06).
 *
 * 환경변수 PERITAGO_TRANSLATE_MOCK_GENERATOR=false 일 때만 활성화된다(기본값은 Mock).
 * 공용 application.yml 을 수정하지 않도록 스위치를 환경변수로 두었다.
 * TranslationGenerator 인터페이스가 동일하므로 TranslateService 는 이 교체를 알지 못한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "peritago.translate.mock.generator", havingValue = "false")
public class OpenAiTranslationGenerator implements TranslationGenerator {

    /** 프롬프트를 고칠 때마다 올린다. ai_response.prompt_version 에 기록되어 품질 추적에 쓰인다. */
    private static final String PROMPT_VERSION = "v3";

    /**
     * 사내 근거가 없을 때 공식 정의가 반드시 이 문장으로 시작해야 한다.
     * v1 에서는 "사내 기준이 아님을 밝히라"고만 지시했더니 모델이 이를 무시하고
     * 일반 정의만 출력했다. 지시를 '첫 문장을 이 문구로 시작하라'는 결정적 형태로 바꿔 강제한다.
     */
    private static final String GENERAL_DISCLAIMER =
            "사내 위키와 용어집에 등록된 정의가 없어 일반적인 의미로 안내합니다.";

    /**
     * 모델 설정은 공용 application.yml 을 건드리지 않기 위해 이 클래스 안에서 지정한다.
     * 용어 설명은 창의성보다 근거 충실도가 중요하므로 temperature 를 낮게 잡는다.
     */
    private static final String MODEL = "gpt-4o-mini";
    private static final double TEMPERATURE = 0.3;

    /**
     * LLM 호출 상한. 실측 응답이 1.5~3.7초라 30초면 정상 호출을 자르지 않으면서
     * 장애 시 요청이 무한정 매달리는 것을 막는다.
     *
     * HTTP 클라이언트 레벨이 아니라 호출 스레드 레벨의 상한이다. 클라이언트 레벨로 걸려면
     * 공용 RestClient 설정을 건드려야 해서, 우선 이 클래스 안에서 처리한다.
     * 타임아웃 시 요청 스레드는 즉시 풀려나지만 이미 나간 HTTP 요청 자체는 끝날 때까지 남는다.
     */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    private static final String SYSTEM_PROMPT = """
            당신은 사내 용어 통역사입니다. 회의 중 낯선 용어를 만난 직원에게 두 파트로 설명합니다.

            [공식 정의] officialDefinition
            - 근거(EVIDENCE)가 주어지면 그 내용만을 토대로 작성하고, 근거에 없는 사실을 덧붙이지 마십시오.
            - 확실하지 않으면 아는 척하지 말고 모른다고 쓰십시오.

            [EVIDENCE_TYPE 별 처리] 아래 세 경우는 서로 배타적입니다. 해당하는 항목의 지시만 따르고,
            다른 항목에 지정된 문구를 가져다 쓰지 마십시오.

            (1) "사내 은어 사전"
                관리자가 확정한 공식 값입니다. 그대로 신뢰하고 근거를 토대로 작성하십시오.

            (2) "사내 위키 문서 발췌"
                검색으로 찾아온 것이라 질문과 맞지 않을 수 있습니다. 근거가 이 용어를 설명하는 내용이
                맞는지 먼저 판단하십시오.
                - 맞으면 그 내용을 토대로 작성하십시오. 이 경우 고지 문구를 붙이지 마십시오.
                - 명백히 다른 내용이면 근거를 버리고 officialDefinition 을 다음 문장으로 시작하십시오.
                  "사내 위키에서 이 용어에 대한 설명을 찾지 못했습니다."
                EVIDENCE_KEYWORD_ONLY 가 true 이면 그 근거는 의미가 아니라 글자 겹침으로 찾아온
                것이므로 특히 신중하게 판단하십시오.

            (3) "사내 근거 없음"
                사용자 메시지에 REQUIRED_PREFIX 가 주어집니다. officialDefinition 을 그 문장으로 그대로
                시작한 뒤 한 칸 띄고 일반적인 의미를 이어서 설명하십시오. 문구를 바꾸거나 생략하지 마십시오.
                사용자가 사내 공식 기준으로 오해하는 것을 막기 위한 필수 고지입니다.

            REQUIRED_PREFIX 가 주어지지 않았다면 어떤 고지 문구도 붙이지 마십시오.

            [개인화 설명] personalizedExplanation
            - 사용자의 도메인과 배경지식에 맞춰, 그 사람이 이미 아는 개념에 빗대어 풀어 쓰십시오.
            - ANALOGY 후보가 주어지면 그중 이 용어의 구조와 실제로 닮은 것을 하나 골라 비유로 쓰고,
              "[분야] 쪽 사례에 빗대면" 처럼 어느 분야 이야기인지 밝히십시오.
              닮은 것이 없으면 비유를 억지로 만들지 말고 후보를 무시하십시오.
            - 대화 맥락(CONTEXT)이 주어지면 그 대화에서 이 용어가 어떤 의미로 쓰였는지를 반영하십시오.
            - 공식 정의를 그대로 반복하지 마십시오.

            공통 규칙
            - 한국어로 작성합니다.
            - 사족(인사말, "물론이죠" 등)을 붙이지 마십시오.
            - 분량 지시(SHORT/MEDIUM/DETAILED)를 지키십시오.
            """;

    /** 사내 자료 조회 자체가 실패한 경우. "등록된 정의가 없다"고 말하면 사실과 다르다. */
    private static final String LOOKUP_FAILED_DISCLAIMER =
            "사내 자료를 조회하지 못해 일반적인 의미로 안내합니다.";

    private final ChatClient chatClient;

    /** 가상 스레드라 호출당 하나씩 만들어도 비용이 거의 없다. 타임아웃 부과 용도로만 쓴다. */
    private final ExecutorService callExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public OpenAiTranslationGenerator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(MODEL)
                        .temperature(TEMPERATURE))
                .build();
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }

    @Override
    public Result generate(Command command) {
        long startedAt = System.nanoTime();

        ResponseEntity<ChatResponse, GeneratedContent> response = callWithTimeout(command);

        int latencyMs = (int) ((System.nanoTime() - startedAt) / 1_000_000);
        GeneratedContent content = response.entity();
        ChatResponse chatResponse = response.response();

        if (content == null) {
            log.warn("LLM 응답을 구조화하지 못했습니다. term={}", command.term());
            throw new IllegalStateException("LLM 응답 파싱 실패");
        }

        return new Result(
                content.officialDefinition(),
                command.personalizationEnabled() ? content.personalizedExplanation() : null,
                resolveModel(chatResponse),
                PROMPT_VERSION,
                resolveTokenUsage(chatResponse),
                latencyMs);
    }

    private ResponseEntity<ChatResponse, GeneratedContent> callWithTimeout(Command command) {
        Future<ResponseEntity<ChatResponse, GeneratedContent>> future = callExecutor.submit(
                () -> chatClient.prompt()
                        .user(buildUserMessage(command))
                        .call()
                        .responseEntity(GeneratedContent.class));
        try {
            return future.get(CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("LLM 응답 시간 초과 (%d초)".formatted(CALL_TIMEOUT.toSeconds()), e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM 호출이 중단되었습니다", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("LLM 호출 실패", e.getCause());
        }
    }

    @PreDestroy
    void shutdown() {
        callExecutor.shutdownNow();
    }

    private String buildUserMessage(Command command) {
        StringBuilder message = new StringBuilder();
        message.append("TERM: ").append(command.term()).append('\n');
        message.append("EVIDENCE_TYPE: ").append(describeSource(command.sourceType())).append('\n');

        if (command.evidence() != null && !command.evidence().isBlank()) {
            message.append("EVIDENCE:\n").append(command.evidence()).append('\n');
        } else {
            message.append("EVIDENCE: (없음 — 사내 근거 자료가 없습니다)\n");
            message.append("REQUIRED_PREFIX: officialDefinition 은 반드시 다음 문장으로 시작할 것 -> ")
                    .append(command.evidenceLookupFailed() ? LOOKUP_FAILED_DISCLAIMER : GENERAL_DISCLAIMER)
                    .append('\n');
        }

        if (command.evidenceKeywordOnly()) {
            message.append("EVIDENCE_KEYWORD_ONLY: true (글자 겹침으로 찾아온 근거 - 관련성 직접 판단 필요)\n");
        }

        if (command.analogies() != null && !command.analogies().isEmpty()) {
            message.append("ANALOGY 후보 (사용자가 아는 분야의 문서):\n");
            command.analogies().forEach(analogy -> message
                    .append("  [").append(analogy.domain()).append("] ")
                    .append(analogy.content()).append('\n'));
        }

        if (command.contextSnapshot() != null && !command.contextSnapshot().isBlank()) {
            message.append("CONTEXT (직전 대화):\n").append(command.contextSnapshot()).append('\n');
        }

        message.append("USER_DOMAIN: ")
                .append(command.domainTags() == null || command.domainTags().isEmpty()
                        ? "지정되지 않음"
                        : String.join(", ", command.domainTags()))
                .append('\n');

        if (command.personaDescription() != null && !command.personaDescription().isBlank()) {
            message.append("USER_BACKGROUND: ").append(command.personaDescription()).append('\n');
        }

        message.append("OFFICIAL_DEFINITION_LENGTH: ").append(describeLength(command.officialDefLength())).append('\n');

        if (command.personalizationEnabled()) {
            message.append("PERSONALIZED_EXPLANATION_LENGTH: ")
                    .append(describeLength(command.personalizedExpLength())).append('\n');
        } else {
            message.append("PERSONALIZED_EXPLANATION_LENGTH: 생성하지 않음 — personalizedExplanation 은 빈 문자열로 두십시오.\n");
        }

        return message.toString();
    }

    private String describeSource(SourceType sourceType) {
        return switch (sourceType) {
            case GLOSSARY -> "사내 은어 사전에 등록된 공식 정의";
            case WIKI -> "사내 위키 문서 발췌";
            case GENERAL -> "사내 근거 없음 (일반 지식으로 답변)";
        };
    }

    private String describeLength(ExplanationLength length) {
        if (length == null) {
            return "MEDIUM (2~3문장)";
        }
        return switch (length) {
            case SHORT -> "SHORT (1문장)";
            case MEDIUM -> "MEDIUM (2~3문장)";
            case DETAILED -> "DETAILED (4~6문장)";
        };
    }

    private String resolveModel(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        String model = chatResponse.getMetadata().getModel();
        return (model == null || model.isBlank()) ? null : model;
    }

    private Integer resolveTokenUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null
                || chatResponse.getMetadata().getUsage() == null) {
            return null;
        }
        Integer total = chatResponse.getMetadata().getUsage().getTotalTokens();
        return total;
    }

    /** LLM 구조화 출력 대상. 필드명이 그대로 JSON 스키마가 되므로 이름을 바꾸지 말 것. */
    public record GeneratedContent(String officialDefinition, String personalizedExplanation) {}
}
