package com.basecamp.backend.domain.ai.controller;

import com.basecamp.backend.domain.ai.dto.response.ReviewSummaryResponse;
import com.basecamp.backend.domain.ai.service.ReviewSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 캠핑장 리뷰 AI 요약
@RestController
@RequestMapping("/api/v1/camps")
@RequiredArgsConstructor
public class ReviewSummaryController {

  private final ReviewSummaryService reviewSummaryService;

  // GET /api/v1/camps/** 공개 규칙을 따른다. 요약은 리뷰가 바뀔 때만 새로 만들어 LLM 호출 수가 리뷰 변경 횟수로 제한된다.
  @Operation(
      summary = "캠핑장 리뷰 AI 요약",
      description =
          "최근 리뷰 최대 100건을 요약합니다. 리뷰가 3건 미만이면 status=INSUFFICIENT_REVIEWS. "
              + "장단점은 근거 리뷰가 확인된 항목만 내려갑니다. AI 기능이 꺼져 있으면 503(AI001).")
  @GetMapping("/{campId}/review-summary")
  public ResponseEntity<ReviewSummaryResponse> getReviewSummary(@PathVariable Long campId) {
    return ResponseEntity.ok(reviewSummaryService.getSummary(campId));
  }
}
