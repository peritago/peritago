package com.skala.domainbridge.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserCreateRequestDto(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String name
) {}