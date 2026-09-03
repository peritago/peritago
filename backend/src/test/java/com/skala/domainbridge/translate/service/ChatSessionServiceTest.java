package com.skala.domainbridge.translate.service;

import com.skala.domainbridge.common.exception.CustomException;
import com.skala.domainbridge.common.exception.ErrorCode;
import com.skala.domainbridge.translate.dto.response.ChatSessionCreateResponseDto;
import com.skala.domainbridge.translate.dto.response.ChatSessionResponseDto;
import com.skala.domainbridge.translate.entity.ChatSession;
import com.skala.domainbridge.translate.repository.ChatSessionRepository;
import com.skala.domainbridge.user.dto.response.UserPersonaResponseDto;
import com.skala.domainbridge.user.entity.ExplanationLength;
import com.skala.domainbridge.user.entity.User;
import com.skala.domainbridge.user.repository.UserRepository;
import com.skala.domainbridge.user.service.PersonaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @Mock private UserRepository userRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private PersonaService personaService;

    @InjectMocks private ChatSessionService chatSessionService;

    @Test
    void 새_채팅_생성_시_페르소나가_없으면_설정이_필요하다고_알린다() {
        생성_스텁();
        when(personaService.findPersona(USER_ID)).thenReturn(페르소나_없음());

        ChatSessionCreateResponseDto result = chatSessionService.createSession(USER_ID);

        assertThat(result.personaRequired()).isTrue();
        assertThat(result.session().title()).isNull();
    }

    @Test
    void 페르소나가_이미_있으면_설정이_필요하지_않다() {
        생성_스텁();
        when(personaService.findPersona(USER_ID)).thenReturn(페르소나_있음());

        ChatSessionCreateResponseDto result = chatSessionService.createSession(USER_ID);

        assertThat(result.personaRequired()).isFalse();
    }

    @Test
    void 타인_세션을_조회하면_SESSION_NOT_FOUND를_던진다() {
        when(chatSessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatSessionService.findMySession(USER_ID, SESSION_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    @Test
    void 제목을_수정하면_앞뒤_공백이_제거된다() {
        ChatSession session = ChatSession.builder().user(사용자()).title("이전 제목").build();
        when(chatSessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));

        ChatSessionResponseDto result = chatSessionService.rename(USER_ID, SESSION_ID, "  결제 장애 회의  ");

        assertThat(result.title()).isEqualTo("결제 장애 회의");
        assertThat(session.getTitle()).isEqualTo("결제 장애 회의");
    }

    @Test
    void 사용자가_지정한_제목은_첫_질의_때_덮어쓰지_않는다() {
        ChatSession session = ChatSession.builder().user(사용자()).title("내가 붙인 제목").build();

        session.initTitleIfAbsent("TF");

        assertThat(session.getTitle()).isEqualTo("내가 붙인 제목");
    }

    private void 생성_스텁() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(사용자()));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(i -> i.getArgument(0));
    }

    private UserPersonaResponseDto 페르소나_있음() {
        return new UserPersonaResponseDto(true, List.of("개발"), "백엔드 3년차",
                ExplanationLength.SHORT, ExplanationLength.MEDIUM);
    }

    private UserPersonaResponseDto 페르소나_없음() {
        return new UserPersonaResponseDto(false, List.of(), null, null, null);
    }

    private User 사용자() {
        return User.builder().email("me@peritago.dev").password("encoded").name("김영민").build();
    }
}
