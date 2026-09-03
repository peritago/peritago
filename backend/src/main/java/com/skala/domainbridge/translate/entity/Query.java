package com.skala.domainbridge.translate.entity;

import com.skala.domainbridge.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 질의 이력 — 사용자가 질의한 용어와 시점 맥락 기록. 질의 이력 조회(F-08)의 기반.
 *
 * 질의는 생성 후 수정되지 않는 불변 레코드이므로 updatedAt이 없다.
 * 그래서 createdAt/updatedAt을 함께 강제하는 BaseEntity를 상속하지 않고 createdAt만 직접 선언한다.
 */
@Getter
@Entity
@Table(
        name = "query",
        indexes = @Index(name = "idx_query_user_created", columnList = "user_id, created_at desc")
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Query {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 모든 질의는 반드시 하나의 세션에 속한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    /** 사용자가 입력한 용어 (1~100자, 공백만 불가 — 검증은 요청 DTO에서) */
    @Column(nullable = false, length = 100)
    private String term;

    /** 질의 시점 슬라이딩 윈도우 3~5문장. Mock 단계에서는 NULL. */
    @Column(columnDefinition = "TEXT")
    private String contextSnapshot;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Query(User user, ChatSession session, String term, String contextSnapshot) {
        this.user = user;
        this.session = session;
        this.term = term;
        this.contextSnapshot = contextSnapshot;
    }
}
