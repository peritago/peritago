package com.skala.domainbridge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yml의 spring.config.import: optional:file:.env[.properties] 메커니즘이 실제로
 * 동작하는지 확인한다. 진짜 .env 파일(실제 OPENAI_API_KEY가 들어갈 파일)은 이 테스트가 절대
 * 만들거나 읽지 않는다 — 대신 src/test/resources/dotenv-mechanism-check.properties라는 완전히
 * 별도의, 비밀 아닌 파일로만 "파일을 읽어서 프로퍼티로 노출하는 동작 자체"를 검증한다.
 */
@SpringBootTest(properties = {
		"spring.config.import=optional:file:src/test/resources/dotenv-mechanism-check.properties",
		// peritago의 docker-compose.yml 기준 접속 정보로 맞춤 (miniproject_1에서는 5433/slangtranslator였음).
		"spring.datasource.url=jdbc:postgresql://localhost:5432/domainbridge",
		"spring.datasource.username=domainbridge",
		"spring.datasource.password=domainbridge",
		"spring.ai.openai.api-key=sk-test-dummy-key-for-boot-check"
})
class DotenvConfigImportTest {

	@Value("${DOTENV_MECHANISM_CHECK}")
	private String value;

	@Test
	void loadsValueFromImportedPropertiesFile() {
		assertThat(value).isEqualTo("it-works");
	}
}
