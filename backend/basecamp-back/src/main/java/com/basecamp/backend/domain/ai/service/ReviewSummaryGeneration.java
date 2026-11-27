package com.basecamp.backend.domain.ai.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * 리뷰 요약에서 Claude 가 돌려주는 구조화 출력. 이 레코드에서 JSON 스키마가 자동 생성된다.
 *
 * <p>장단점마다 근거 리뷰 ID 를 함께 받아, 서버가 실제 입력 리뷰에 근거한 항목만 남긴다(환각 방지).
 */
public record ReviewSummaryGeneration(
    @JsonPropertyDescription("리뷰 전체를 요약한 한국어 3~4문장. 숫자를 쓰지 않는다") String summary,
    @JsonPropertyDescription("좋았던 점. 최대 3개") List<Item> pros,
    @JsonPropertyDescription("아쉬운 점. 최대 3개") List<Item> cons,
    @JsonPropertyDescription("리뷰 전체의 분위기") Sentiment overallSentiment) {

  public record Item(
      @JsonPropertyDescription("짧은 한국어 구절. 숫자를 쓰지 않는다") String text,
      @JsonPropertyDescription("이 내용을 실제로 언급한 리뷰의 id 목록") List<Long> supportingReviewIds) {}

  public enum Sentiment {
    POSITIVE,
    MIXED,
    NEGATIVE
  }
}
