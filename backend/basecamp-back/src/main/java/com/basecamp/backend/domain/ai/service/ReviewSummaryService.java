package com.basecamp.backend.domain.ai.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.ai.client.LlmClient;
import com.basecamp.backend.domain.ai.config.AiProperties;
import com.basecamp.backend.domain.ai.dto.response.ReviewSummaryResponse;
import com.basecamp.backend.domain.ai.entity.CampReviewSummary;
import com.basecamp.backend.domain.ai.repository.CampReviewSummaryRepository;
import com.basecamp.backend.domain.ai.service.ReviewSummaryGroundingValidator.GroundedSummary;
import com.basecamp.backend.domain.ai.service.ReviewSummaryGroundingValidator.ReviewInput;
import com.basecamp.backend.domain.ai.service.ReviewSummaryGroundingValidator.UngroundedSummaryException;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.review.repository.ReviewRepository;
import com.basecamp.backend.domain.review.repository.ReviewStats;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 캠핑장 리뷰 AI 요약.
 *
 * <p>요약은 저장해 두고, 리뷰 지문(리뷰 수 + 마지막 작성/수정 시각 + 프롬프트 버전)이 바뀌었을 때만 다시 만든다. 리뷰 쓰기 로직(ReviewService)은
 * 건드리지 않는다. 엔드포인트가 공개여도 LLM 호출 수는 리뷰 변경 횟수로 제한된다.
 *
 * <p>모델 출력은 {@link ReviewSummaryGroundingValidator} 로 근거를 확인하고, 무효면 한 번 다시 만든다. 그래도 무효면 이전 요약을
 * 돌려주거나 (없으면) AI_UPSTREAM_ERROR 로 응답한다. LLM 호출은 트랜잭션 밖에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSummaryService {

  static final int MIN_REVIEWS = 3;
  private static final int MAX_ATTEMPTS = 2;
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final AiProperties properties;
  private final ObjectProvider<LlmClient> llmClient;
  private final ReviewSummaryGroundingValidator groundingValidator;
  private final CampReviewSummaryRepository summaryRepository;
  private final ReviewRepository reviewRepository;
  private final CampRepository campRepository;

  public ReviewSummaryResponse getSummary(Long campId) {
    LlmClient client = llmClient.getIfAvailable();
    if (!properties.isEnabled() || client == null) {
      throw new BusinessException(ErrorCode.AI_DISABLED);
    }
    if (!campRepository.existsById(campId)) {
      throw new BusinessException(ErrorCode.CAMP_NOT_FOUND);
    }

    ReviewStats stats = reviewRepository.findStatsByCampId(campId);
    long reviewCount = stats == null || stats.count() == null ? 0 : stats.count();
    if (reviewCount < MIN_REVIEWS) {
      return ReviewSummaryResponse.insufficient((int) reviewCount);
    }

    String fingerprint =
        reviewCount + "|" + stats.lastModifiedAt() + "|" + ReviewSummaryPrompt.VERSION;
    Optional<CampReviewSummary> cached = summaryRepository.findById(campId);
    if (cached.isPresent() && fingerprint.equals(cached.get().getSourceFingerprint())) {
      return ReviewSummaryResponse.from(cached.get());
    }

    List<ReviewInput> reviews =
        reviewRepository.findTop100ByCamp_CampIdOrderByCreatedAtDescReviewIdDesc(campId).stream()
            .map(r -> new ReviewInput(r.getReviewId(), r.getRating(), r.getContent()))
            .toList();

    Optional<GroundedSummary> grounded = generate(client, campId, reviews);
    if (grounded.isEmpty()) {
      // 새 요약을 만들지 못했으면, 조금 오래됐더라도 검증을 통과했던 이전 요약이 없는 것보다 낫다.
      return cached
          .map(ReviewSummaryResponse::from)
          .orElseThrow(() -> new BusinessException(ErrorCode.AI_UPSTREAM_ERROR));
    }

    CampReviewSummary summary =
        CampReviewSummary.builder()
            .campId(campId)
            .summary(grounded.get().summary())
            .pros(grounded.get().pros())
            .cons(grounded.get().cons())
            .basedOnReviewCount(reviews.size())
            .sourceFingerprint(fingerprint)
            .model(properties.resolveSummaryModel())
            .promptVersion(ReviewSummaryPrompt.VERSION)
            .generatedAt(LocalDateTime.now(SEOUL))
            .build();
    try {
      summaryRepository.save(summary);
    } catch (DataIntegrityViolationException e) {
      // 같은 캠핑장 요약을 동시에 만들어 먼저 저장된 경우. 이번 결과는 응답만 하고 저장은 건너뛴다.
      log.info("[리뷰요약] 동시 생성으로 저장 건너뜀 campId={}", campId);
    }
    return ReviewSummaryResponse.from(summary);
  }

  private Optional<GroundedSummary> generate(
      LlmClient client, Long campId, List<ReviewInput> reviews) {
    String userMessage = toReviewsBlock(reviews);
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      ReviewSummaryGeneration generation =
          client.generateStructured(
              properties.resolveSummaryModel(),
              ReviewSummaryPrompt.SYSTEM,
              userMessage,
              ReviewSummaryGeneration.class);
      try {
        return Optional.of(groundingValidator.validate(generation, reviews));
      } catch (UngroundedSummaryException e) {
        log.warn(
            "[리뷰요약] 근거 검증 실패 campId={}, attempt={}, reason={}", campId, attempt, e.getMessage());
      }
    }
    return Optional.empty();
  }

  /** 리뷰 본문은 개인정보를 가리고 태그 문자를 무력화해 넣는다. 다른 후기나 지시문을 흉내 내지 못하게 한다. */
  private static String toReviewsBlock(List<ReviewInput> reviews) {
    return reviews.stream()
        .map(
            r ->
                "<review id=\"%d\" rating=\"%s\">\n%s\n</review>"
                    .formatted(r.reviewId(), r.rating(), AiTextGuard.sanitizeInput(r.content())))
        .collect(Collectors.joining("\n", "<reviews>\n", "\n</reviews>"));
  }
}
