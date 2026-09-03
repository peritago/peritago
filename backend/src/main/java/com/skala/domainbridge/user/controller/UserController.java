package com.skala.domainbridge.user.controller;

import com.skala.domainbridge.common.response.ApiResponse;
import com.skala.domainbridge.user.dto.request.UserCreateRequestDto;
import com.skala.domainbridge.user.dto.request.UserPersonaRequestDto;
import com.skala.domainbridge.user.dto.response.UserPersonaResponseDto;
import com.skala.domainbridge.user.dto.response.UserResponseDto;
import com.skala.domainbridge.user.service.PersonaService;
import com.skala.domainbridge.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PersonaService personaService;

    @PostMapping
    public ApiResponse<Long> createUser(@Valid @RequestBody UserCreateRequestDto request) {
        return ApiResponse.created(userService.createUser(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponseDto> findMe(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(userService.findUser(userId));
    }

    @GetMapping("/me/persona")
    public ApiResponse<UserPersonaResponseDto> findMyPersona(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(personaService.findPersona(userId));
    }

    @PutMapping("/me/persona")
    public ApiResponse<UserPersonaResponseDto> updateMyPersona(
            Authentication authentication, @RequestBody UserPersonaRequestDto request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(personaService.upsertPersona(userId, request));
    }
}