package com.basecamp.backend.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.ai.config.AiProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiRateLimiterTest {

  private static final Instant START = Instant.parse("2026-11-29T00:00:00Z");

  private final AiProperties properties = new AiProperties();
  private MutableClock clock;
  private AiRateLimiter limiter;

  @BeforeEach
  void setUp() {
    properties.getSearchRateLimit().setMaxRequests(2);
    properties.getSearchRateLimit().setWindowMinutes(60);
    clock = new MutableClock(START);
    limiter = new AiRateLimiter(properties, clock);
  }

  @Test
  void acquire_한도까지허용_초과시AI_RATE_LIMITED() {
    limiter.acquire(1L);
    limiter.acquire(1L);

    assertThatThrownBy(() -> limiter.acquire(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_RATE_LIMITED);
  }

  @Test
  void acquire_사용자별로따로센다() {
    limiter.acquire(1L);
    limiter.acquire(1L);

    assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();
  }

  @Test
  void acquire_윈도우가지나면다시허용() {
    limiter.acquire(1L);
    limiter.acquire(1L);

    clock.advance(Duration.ofMinutes(60));

    assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("Asia/Seoul");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
