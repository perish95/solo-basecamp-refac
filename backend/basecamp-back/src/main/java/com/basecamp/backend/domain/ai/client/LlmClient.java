package com.basecamp.backend.domain.ai.client;

/**
 * LLM 호출 추상화. 서비스는 이 인터페이스에만 의존하므로 테스트에서 목으로 바꾸거나 모델/벤더를 교체하기 쉽다.
 *
 * <p>구현체는 ai.enabled=true 일 때만 빈으로 등록된다.
 */
public interface LlmClient {

  /**
   * 응답을 JSON 스키마에 맞춘 구조화 출력으로 받는다.
   *
   * @param systemPrompt 서버가 소유하는 고정 지시문
   * @param userMessage 사용자 입력
   * @param outputType 응답을 매핑할 타입 (스키마 자동 생성)
   * @throws com.basecamp.backend.common.exception.BusinessException AI_QUERY_UNSUPPORTED — 모델이 요청을
   *     거절함 / AI_UPSTREAM_ERROR — API 호출 실패나 응답 잘림
   */
  <T> T generateStructured(String systemPrompt, String userMessage, Class<T> outputType);
}
