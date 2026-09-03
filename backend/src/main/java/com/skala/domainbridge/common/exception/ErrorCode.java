package com.skala.domainbridge.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    EMAIL_DUPLICATED(409, "EMAIL_DUPLICATED", "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(401, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(401, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(401, "EXPIRED_TOKEN", "만료된 토큰입니다."),
    PERSONA_NOT_FOUND(404, "PERSONA_NOT_FOUND", "설정된 페르소나가 없습니다."),
    GLOSSARY_TERM_DUPLICATED(400, "GLOSSARY_TERM_DUPLICATED", "이미 등록된 용어입니다.");

    private final int status;
    private final String code;
    private final String message;

    ErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}