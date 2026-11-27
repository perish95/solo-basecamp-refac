package com.basecamp.backend.domain.ai.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

  /**
   * Claude API 클라이언트. ai.enabled=true 일 때만 만든다 — 키가 없는 환경(로컬, CI)에서도 앱이 정상 기동하도록.
   *
   * <p>API 키는 환경변수 ANTHROPIC_API_KEY 에서 읽는다. 사용자 요청 경로라 재시도는 1회로 줄인다(SDK 기본 2회).
   */
  @Bean
  @ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
  public AnthropicClient anthropicClient(AiProperties properties) {
    return AnthropicOkHttpClient.builder()
        .fromEnv()
        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
        .maxRetries(1)
        .build();
  }
}
