package com.skala.domainbridge.translate.entity;

import com.skala.domainbridge.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 채팅 세션 = "새 채팅" 1건.
 * 로그인 직후가 아니라 새 채팅 생성 시점에 만들어지며, 이 시점에 페르소나 존재 여부를 확인한다.
 *
 * BaseEntity를 상속하지 않는 이유: updatedAt이 "마지막 질의 발생 시각"이라는 업무적 의미를 가지며,
 * 세션 목록의 최근 대화순 정렬 기준이다. 질의가 추가돼도 세션 엔티티 자체는 변경되지 않아
 * 감사(Auditing)만으로는 갱신되지 않으므로, touch()로 명시적으로 건드려 준다.
 */
@Getter
@Entity
@Table(
        name = "chat_sessions",
        indexes = @Index(name = "idx_chat_sessions_user_updated", columnList = "user_id, updated_at desc")
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 세션 생성 직후에는 질의가 없어 제목을 만들 수 없으므로 NULL 허용 */
    @Column(length = 100)
    private String title;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public ChatSession(User user, String title) {
        this.user = user;
        this.title = title;
    }

    /** 첫 질의의 term으로 제목을 채운다. 사용자가 이미 지정한 제목은 덮어쓰지 않는다. */
    public void initTitleIfAbsent(String term) {
        if (this.title == null || this.title.isBlank()) {
            this.title = term;
        }
    }

    public void rename(String title) {
        this.title = title;
    }

    /** 질의가 추가될 때 호출. 엔티티를 dirty 상태로 만들어 updatedAt이 갱신되게 한다. */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
