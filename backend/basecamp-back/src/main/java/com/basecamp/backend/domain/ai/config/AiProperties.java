package com.basecamp.backend.domain.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 연동 설정. {@code ai.*} 프로퍼티로 주입된다.
 *
 * <p>API 키는 여기 두지 않는다. Claude SDK 가 환경변수 {@code ANTHROPIC_API_KEY} 를 직접 읽는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

  /** false 면 Claude 클라이언트 빈을 만들지 않고, AI 엔드포인트는 503(AI_DISABLED)을 돌려준다. */
  private boolean enabled = false;

  /** 사용할 Claude 모델 ID. */
  private String model = "claude-opus-5-5";

  /** 리뷰 요약에 쓸 모델 ID. 비어 있으면 {@link #model} 을 쓴다. */
  private String summaryModel;

  /** Claude API 호출 타임아웃(초). */
  private int timeoutSeconds = 30;

  private Datasource datasource = new Datasource();

  /** 리뷰 요약에 실제로 쓸 모델 ID. */
  public String resolveSummaryModel() {
    return summaryModel == null || summaryModel.isBlank() ? model : summaryModel;
  }

  private Sql sql = new Sql();

  private SearchRateLimit searchRateLimit = new SearchRateLimit();

  /** LLM 이 생성한 SQL 을 실행할 읽기 전용 계정. username 이 비어 있으면 spring.datasource 계정을 쓴다. */
  @Getter
  @Setter
  public static class Datasource {
    private String url;
    private String username;
    private String password;
  }

  /** 생성된 SQL 실행 제한. */
  @Getter
  @Setter
  public static class Sql {
    /** 결과 최대 행 수. LIMIT 이 없거나 이보다 크면 이 값으로 바꾼다. */
    private int maxRows = 50;

    /** 쿼리 타임아웃(초). */
    private int queryTimeoutSeconds = 3;
  }

  /** AI 검색 사용자별 호출 제한. windowMinutes 동안 maxRequests 번까지 허용한다. */
  @Getter
  @Setter
  public static class SearchRateLimit {
    private int maxRequests = 20;
    private int windowMinutes = 60;
  }
}
