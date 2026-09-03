package com.skala.domainbridge.translate.repository;

import com.skala.domainbridge.translate.entity.AiResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiResponseRepository extends JpaRepository<AiResponse, Long> {

    /** 질의와 1:1 이므로 단건. */
    Optional<AiResponse> findByQueryId(Long queryId);

    /** 이력 목록 조립용 일괄 조회 — 질의 건수만큼 단건 조회하는 N+1을 피한다. */
    List<AiResponse> findByQueryIdIn(Collection<Long> queryIds);
}
