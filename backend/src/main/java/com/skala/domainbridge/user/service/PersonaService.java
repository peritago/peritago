package com.skala.domainbridge.user.service;

import com.skala.domainbridge.common.exception.CustomException;
import com.skala.domainbridge.common.exception.ErrorCode;
import com.skala.domainbridge.user.dto.request.UserPersonaRequestDto;
import com.skala.domainbridge.user.dto.response.UserPersonaResponseDto;
import com.skala.domainbridge.user.entity.*;
import com.skala.domainbridge.user.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonaService {

    private final UserRepository userRepository;
    private final UserPersonaRepository userPersonaRepository;
    private final UserDomainTagRepository userDomainTagRepository;
    private final DomainTagRepository domainTagRepository;

    public UserPersonaResponseDto findPersona(Long userId) {
        return userPersonaRepository.findByUserId(userId)
                .map(persona -> {
                    List<String> tags = userDomainTagRepository.findByUserId(userId).stream()
                            .map(t -> t.getDomainTag().getName())
                            .toList();
                    return new UserPersonaResponseDto(
                            true, tags, persona.getPersonaDescription(),
                            persona.getOfficialDefLength(), persona.getPersonalizedExpLength()
                    );
                })
                .orElse(new UserPersonaResponseDto(false, List.of(), null, null, null));
    }

    // 첫 채팅 시 생성 or 마이페이지에서 수정, 둘 다 이 메서드 하나로 처리 (upsert)
    @Transactional
    public UserPersonaResponseDto upsertPersona(Long userId, UserPersonaRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserPersona persona = userPersonaRepository.findByUserId(userId)
                .map(p -> {
                    p.update(request.personaDescription(), request.officialDefLength(), request.personalizedExpLength());
                    return p;
                })
                .orElseGet(() -> userPersonaRepository.save(
                        UserPersona.builder()
                                .user(user)
                                .personaDescription(request.personaDescription())
                                .officialDefLength(request.officialDefLength())
                                .personalizedExpLength(request.personalizedExpLength())
                                .build()
                ));

        userDomainTagRepository.deleteByUserId(userId);
        List<UserDomainTag> newTags = request.domainTags().stream()
                .map(name -> domainTagRepository.findAll().stream()
                        .filter(t -> t.getName().equals(name))
                        .findFirst()
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)))
                .map(tag -> UserDomainTag.builder().user(user).domainTag(tag).build())
                .toList();
        userDomainTagRepository.saveAll(newTags);

        return new UserPersonaResponseDto(
                true, request.domainTags(), persona.getPersonaDescription(),
                persona.getOfficialDefLength(), persona.getPersonalizedExpLength()
        );
    }
}