package com.basecamp.backend.domain.ai.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.ai.config.AiProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 검색의 사용자별 호출 제한 (고정 윈도우).
 *
 * <p>호출마다 LLM 비용이 들어 한 사용자가 짧은 시간에 많이 부르지 못하게 한다. 서버 메모리에 세므로 인스턴스마다 따로 센다 — 여러 대로 늘리면 Redis 같은 공유
 * 저장소로 옮겨야 한다.
 */
@Component
@RequiredArgsConstructor
public class AiRateLimiter {

  /** 오래된 윈도우를 정리하기 시작하는 사용자 수. */
  private static final int CLEANUP_THRESHOLD = 10_000;

  private final AiProperties properties;
  private final Clock clock;
  private final Map<Long, Window> windows = new ConcurrentHashMap<>();

  private record Window(Instant start, int count) {}

  /** 한 번 쓴 것으로 센다. 한도를 넘으면 AI_RATE_LIMITED(429). */
  public void acquire(Long userId) {
    AiProperties.SearchRateLimit limit = properties.getSearchRateLimit();
    Duration size = Duration.ofMinutes(limit.getWindowMinutes());
    Instant now = clock.instant();

    Window window =
        windows.compute(
            userId,
            (id, current) ->
                current == null || !now.isBefore(current.start().plus(size))
                    ? new Window(now, 1)
                    : new Window(current.start(), current.count() + 1));

    if (windows.size() > CLEANUP_THRESHOLD) {
      windows.values().removeIf(w -> !now.isBefore(w.start().plus(size)));
    }
    if (window.count() > limit.getMaxRequests()) {
      throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
    }
  }
}
