package com.basecamp.backend.domain.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Claude API 기반 {@link LlmClient}.
 *
 * <ul>
 *   <li>effort=low: 자연어→SQL 변환처럼 짧은 작업은 추론량을 줄여 지연을 낮춘다.
 *   <li>서버 측 fallback: 안전 분류기가 요청을 거절(refusal)하면 API 가 다른 모델로 자동 재시도한다.
 *   <li>stop_reason 을 content 보다 먼저 확인한다 — 거절/잘린 응답을 정상 결과로 파싱하지 않기 위해.
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AnthropicLlmClient implements LlmClient {

  private static final long MAX_TOKENS = 4000L;
  private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

  private final AnthropicClient client;
  private final AiProperties properties;

  @Override
  public <T> T generateStructured(String systemPrompt, String userMessage, Class<T> outputType) {
    StructuredMessageCreateParams<T> params =
        MessageCreateParams.builder()
            .model(properties.getModel())
            .maxTokens(MAX_TOKENS)
            .system(systemPrompt)
            .addUserMessage(userMessage)
            .outputConfig(
                StructuredOutputConfig.<T>builder()
                    .format(outputType)
                    .effort(OutputConfig.Effort.LOW)
                    .build())
            .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build();

    StructuredMessage<T> response;
    try {
      response = client.messages().create(params);
    } catch (AnthropicException e) {
      log.error("[AI] Claude API 호출 실패: {}", e.getMessage());
      throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
    }

    log.debug(
        "[AI] usage input={} output={}",
        response.usage().inputTokens(),
        response.usage().outputTokens());

    StopReason stopReason = response.stopReason().orElse(null);
    if (StopReason.REFUSAL.equals(stopReason)) {
      log.warn("[AI] 모델이 요청을 거절했습니다.");
      throw new BusinessException(ErrorCode.AI_QUERY_UNSUPPORTED);
    }
    if (StopReason.MAX_TOKENS.equals(stopReason)) {
      log.error("[AI] 응답이 max_tokens 에서 잘렸습니다.");
      throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
    }

    try {
      // textBlock.text() 가 JSON 을 outputType 으로 역직렬화한다. 실패하면 AnthropicException 계열이 난다.
      return response.content().stream()
          .flatMap(block -> block.text().stream())
          .map(textBlock -> textBlock.text())
          .findFirst()
          .orElseThrow(
              () -> {
                log.error("[AI] 응답에 텍스트 블록이 없습니다. stopReason={}", stopReason);
                return new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
              });
    } catch (AnthropicException e) {
      log.error("[AI] 구조화 응답 파싱 실패: {}", e.getMessage());
      throw new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
    }
  }
}
