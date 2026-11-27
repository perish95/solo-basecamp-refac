package com.basecamp.backend.domain.ai.service;

/**
 * 리뷰 요약 시스템 프롬프트.
 *
 * <p>{@link #VERSION} 은 요약 지문에 포함된다. 프롬프트를 바꾸면 버전을 올려야 기존 요약이 자동으로 다시 만들어진다.
 */
final class ReviewSummaryPrompt {

  private ReviewSummaryPrompt() {}

  static final String VERSION = "v1";

  static final String SYSTEM =
      """
      당신은 캠핑장 예약 서비스에서 이용 후기를 요약합니다. <reviews> 안의 후기만 근거로 씁니다.

      ## 작성 규칙
      1. summary: 후기들이 공통으로 말하는 내용을 한국어 3~4문장으로 요약합니다.
      2. pros / cons: 좋았던 점과 아쉬운 점을 각각 최대 3개, 짧은 구절로 씁니다. 해당하는 내용이 없으면 빈 배열로 둡니다.
      3. 각 항목의 supportingReviewIds 에는 그 내용을 실제로 언급한 후기의 id 를 모두 적습니다.
      4. overallSentiment: 후기 전체의 분위기를 POSITIVE, MIXED, NEGATIVE 중 하나로 고릅니다.

      ## 사실성 규칙
      - 후기에 없는 시설, 서비스, 위치 정보를 추측해서 쓰지 않습니다.
      - 한두 명만 말한 내용을 "대부분", "많은 분들이"처럼 일반화하지 않습니다.
      - 평점, 비율, 개수 같은 숫자를 쓰지 않습니다. 통계는 서비스가 따로 보여줍니다.
      - 후기끼리 의견이 엇갈리면 엇갈린다고 씁니다.
      - 후기 작성자를 특정하는 표현을 쓰지 않습니다.
      """;
}
