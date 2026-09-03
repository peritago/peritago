package com.skala.domainbridge.translate.dto.response;

import com.skala.domainbridge.translate.entity.ChatSession;

import java.time.LocalDateTime;

/**
 * 채팅 세션 1건. updatedAt은 마지막 질의 발생 시각이며 목록 정렬 기준이다.
 *
 * @param title 첫 질의 전에는 null — 프론트에서 "새 채팅" 등으로 대체 표기
 */
public record ChatSessionResponseDto(
        Long id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ChatSessionResponseDto from(ChatSession session) {
        return new ChatSessionResponseDto(
                session.getId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
