package com.skala.domainbridge.translate.dto.response;

/**
 * 새 채팅 생성 응답.
 *
 * 페르소나 최초 설정은 "로그인 시 무조건"이 아니라 "새 채팅 진입 시 조건부"다.
 * 그래서 이 시점에 페르소나 존재 여부를 함께 내려주고, 프론트가 페르소나 설정 화면으로 유도한다.
 *
 * @param personaRequired true면 페르소나 미설정 상태 — 페르소나 설정(UC-02)으로 보낸다
 */
public record ChatSessionCreateResponseDto(
        ChatSessionResponseDto session,
        boolean personaRequired
) {}
