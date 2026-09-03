package com.skala.domainbridge.translate.repository;

import com.skala.domainbridge.translate.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /** 세션은 사용자 소유 자원이므로 조회 시 항상 소유자까지 함께 검증한다. */
    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    /** 사이드바 세션 목록 — 최근 대화순. (user_id, updated_at desc) 인덱스 사용. */
    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
