package com.skala.domainbridge.translate.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * AI 응답 — 질의별 2파트 응답(공식 정의 / 개인화 설명)을 영속 저장. 질의와 1:1.
 *
 * 응답도 불변 레코드이므로 updatedAt이 없다. (BaseEntity 미상속 이유는 Query와 동일)
 */
@Getter
@Entity
@Table(name = "ai_response")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_id", nullable = false, unique = true)
    private Query query;

    /** 카드 상단 파트 — 위키/Glossary 근거 공식 정의 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String officialDefinition;

    /** 카드 하단 파트 — 사용자 페르소나 눈높이 설명 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String personalizedExplanation;

    /** 근거 유형. @Enumerated(STRING) 이므로 Hibernate가 CHECK 제약을 함께 생성한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceType sourceType;

    /** Glossary id 또는 위키 문서 링크. GENERAL이면 NULL. */
    @Column(length = 255)
    private String sourceRef;

    // --- LLM 관측성(Observability) 확장 컬럼: Mock 단계에서는 NULL, OpenAI 실연동 시 기록 시작 ---

    /** 응답 생성에 사용한 LLM 모델 */
    @Column(length = 50)
    private String model;

    /** 프롬프트 템플릿 버전 — 품질 추적용 */
    @Column(length = 20)
    private String promptVersion;

    /** 호출당 토큰 수 — 비용 모니터링용 */
    private Integer tokenUsage;

    /** LLM 응답 소요 시간(ms) */
    private Integer latencyMs;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public AiResponse(Query query, String officialDefinition, String personalizedExplanation,
                      SourceType sourceType, String sourceRef,
                      String model, String promptVersion, Integer tokenUsage, Integer latencyMs) {
        this.query = query;
        this.officialDefinition = officialDefinition;
        this.personalizedExplanation = personalizedExplanation;
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.model = model;
        this.promptVersion = promptVersion;
        this.tokenUsage = tokenUsage;
        this.latencyMs = latencyMs;
    }
}
