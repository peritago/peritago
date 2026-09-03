package com.skala.domainbridge.wiki.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AnalogySearchService가 구조 서술 생성(HyDE)에 쓰는 ChatClient 빈.
 * spring-ai-starter-model-openai가 이미 ChatClient.Builder 자동 구성을 제공하므로 별도 의존성
 * 추가 없이 build()만 하면 된다.
 */
@Configuration
public class AnalogyChatClientConfig {

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder) {
		return builder.build();
	}
}
