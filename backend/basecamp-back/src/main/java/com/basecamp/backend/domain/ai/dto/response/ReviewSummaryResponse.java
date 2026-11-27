package com.basecamp.backend.domain.ai.dto.response;

import com.basecamp.backend.domain.ai.entity.CampReviewSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** GET /api/v1/camps/{campId}/review-summary 응답. */
public record ReviewSummaryResponse(
    @Schema(description = "READY: 요약 있음 / INSUFFICIENT_REVIEWS: 리뷰가 적어 요약하지 않음") Status status,
    @Schema(description = "요약 문단") String summary,
    @Schema(description = "좋았던 점 (근거 리뷰가 확인된 항목만)") List<String> pros,
    @Schema(description = "아쉬운 점 (근거 리뷰가 확인된 항목만)") List<String> cons,
    @Schema(description = "요약에 사용한 리뷰 수") int basedOnReviewCount,
    @Schema(description = "요약 생성 일시") LocalDateTime generatedAt) {

  public enum Status {
    READY,
    INSUFFICIENT_REVIEWS
  }

  public static ReviewSummaryResponse from(CampReviewSummary summary) {
    return new ReviewSummaryResponse(
        Status.READY,
        summary.getSummary(),
        summary.getPros(),
        summary.getCons(),
        summary.getBasedOnReviewCount(),
        summary.getGeneratedAt());
  }

  public static ReviewSummaryResponse insufficient(int reviewCount) {
    return new ReviewSummaryResponse(
        Status.INSUFFICIENT_REVIEWS, null, List.of(), List.of(), reviewCount, null);
  }
}
