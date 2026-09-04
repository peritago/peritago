package com.skala.domainbridge.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI(springdoc-openapi) 설정.
 *
 * 이 프로젝트는 로그인/회원가입 등 일부를 제외하면 대부분 JWT 액세스 토큰이 필요하므로
 * (SecurityConfig 참고), Bearer 스킴을 전역 보안 요구사항으로 등록해 Swagger UI 상단의
 * "Authorize" 버튼으로 토큰 하나만 넣으면 모든 API에 자동으로 실린다.
 * 인증이 필요 없는 엔드포인트는 각 컨트롤러 메서드에 @SecurityRequirements() (빈 값)를 붙여
 * 이 전역 요구사항을 무시하도록 표시한다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DomainBridge API")
                        .description("사내 용어 통역 서비스 API 문서. "
                                + "인증이 필요한 API는 우측 상단 Authorize 버튼에 로그인으로 발급받은 "
                                + "액세스 토큰을 입력하면 된다 (Bearer 접두사 없이 토큰 값만 입력).")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
