package com.skala.domainbridge.translate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 세션 제목 수정. 첫 질의 때 자동 생성된 제목을 사용자가 바꾸는 경로. */
public record ChatSessionRenameRequestDto(

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.")
        String title
) {}
