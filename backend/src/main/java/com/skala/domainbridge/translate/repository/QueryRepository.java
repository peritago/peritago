package com.skala.domainbridge.translate.repository;

import com.skala.domainbridge.translate.entity.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 파생 쿼리만 사용한다. 같은 패키지에 Query 엔티티가 있어 Spring Data의 @Query 애너테이션이
 * 그대로는 해석되지 않으므로, JPQL이 꼭 필요하면 @org.springframework.data.jpa.repository.Query 로 완전수식할 것.
 */
public interface QueryRepository extends JpaRepository<Query, Long> {

    /** 질의 이력 조회(F-08) — 최신순 페이징. (user_id, created_at desc) 인덱스 사용. */
    Page<Query> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 세션 단위 대화 재생 — 오래된 순. */
    List<Query> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /** 질의 상세 조회 시 타인 질의 접근 차단. */
    Optional<Query> findByIdAndUserId(Long id, Long userId);
}
