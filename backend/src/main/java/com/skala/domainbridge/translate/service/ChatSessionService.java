package com.skala.domainbridge.translate.service;

import com.skala.domainbridge.common.exception.CustomException;
import com.skala.domainbridge.common.exception.ErrorCode;
import com.skala.domainbridge.translate.dto.response.ChatSessionCreateResponseDto;
import com.skala.domainbridge.translate.dto.response.ChatSessionResponseDto;
import com.skala.domainbridge.translate.entity.ChatSession;
import com.skala.domainbridge.translate.repository.ChatSessionRepository;
import com.skala.domainbridge.user.entity.User;
import com.skala.domainbridge.user.repository.UserRepository;
import com.skala.domainbridge.user.service.PersonaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 채팅 세션 관리.
 *
 * 세션은 질의(query)와 대화 맥락(Redis)의 그룹핑 기준이며, 사용자 소유 자원이다.
 * 명세서상 MVP에서는 세션 삭제를 구현하지 않는다 — 이력 조회(F-08)가 과거 질의를 계속 참조하므로,
 * 도입 시에도 연쇄 하드 삭제가 아니라 soft delete로 가야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatSessionService {

    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final PersonaService personaService;

    /**
     * 새 채팅 생성. 이 시점에 페르소나 존재 여부를 확인해 함께 내려준다
     * (페르소나 설정은 로그인 직후가 아니라 새 채팅 진입 시 조건부로 유도된다).
     * 제목은 첫 질의의 term으로 채워지므로 생성 시점에는 null이다.
     */
    @Transactional
    public ChatSessionCreateResponseDto createSession(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .user(user)
                .build());

        boolean personaRequired = !personaService.findPersona(userId).exists();

        return new ChatSessionCreateResponseDto(ChatSessionResponseDto.from(session), personaRequired);
    }

    /** 사이드바 세션 목록 — 최근 대화순. */
    public List<ChatSessionResponseDto> findMySessions(Long userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(ChatSessionResponseDto::from)
                .toList();
    }

    public ChatSessionResponseDto findMySession(Long userId, Long sessionId) {
        return ChatSessionResponseDto.from(getOwnedSession(userId, sessionId));
    }

    @Transactional
    public ChatSessionResponseDto rename(Long userId, Long sessionId, String title) {
        ChatSession session = getOwnedSession(userId, sessionId);
        session.rename(title.trim());
        return ChatSessionResponseDto.from(session);
    }

    /** 소유자까지 함께 조회해 타인 세션 접근을 차단한다. */
    private ChatSession getOwnedSession(Long userId, Long sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
    }
}
