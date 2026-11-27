package com.basecamp.backend.domain.ai.service;

import com.basecamp.backend.domain.ai.service.ReviewSummaryGeneration.Item;
import com.basecamp.backend.domain.ai.service.ReviewSummaryGeneration.Sentiment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 리뷰 요약의 환각을 걸러낸다. 모델 출력이 실제 입력 리뷰에 근거하는지 서버가 확인한다.
 *
 * <ul>
 *   <li>근거 확인: 장단점 항목의 근거 리뷰 ID 가 실제로 넣은 리뷰에 있어야 한다 → 없으면 항목 제거
 *   <li>과장 방지: 리뷰가 5개 이상이면 근거가 2개 이상인 항목만 남긴다(한 사람 의견을 "많은 분들이"로 부풀리지 않게)
 *   <li>숫자 금지: 모델이 쓴 숫자(평점, 비율, 개수)는 틀리기 쉽다 → 요약에 숫자가 있으면 무효, 항목은 제거. 통계는 서버가 계산한다
 *   <li>감정 일치: 서버가 계산한 평균 평점과 모델이 판단한 분위기가 정반대면 무효
 * </ul>
 */
@Slf4j
@Component
public class ReviewSummaryGroundingValidator {

  static final int MAX_ITEMS = 3;
  private static final int STRICT_SUPPORT_REVIEW_COUNT = 5;
  private static final BigDecimal HIGH_RATING = new BigDecimal("4.0");
  private static final BigDecimal LOW_RATING = new BigDecimal("2.0");
  private static final Pattern DIGIT = Pattern.compile("\\d");

  /** 요약 입력으로 넣은 리뷰 한 건. */
  public record ReviewInput(long reviewId, BigDecimal rating, String content) {}

  /** 검증을 통과한 요약. 장단점은 근거가 확인된 항목만 남는다. */
  public record GroundedSummary(String summary, List<String> pros, List<String> cons) {}

  /** 요약 전체를 버려야 하는 경우. 서비스가 한 번 다시 생성한다. */
  public static class UngroundedSummaryException extends RuntimeException {
    public UngroundedSummaryException(String reason) {
      super(reason);
    }
  }

  public GroundedSummary validate(ReviewSummaryGeneration generation, List<ReviewInput> reviews) {
    if (generation == null || generation.summary() == null || generation.summary().isBlank()) {
      throw new UngroundedSummaryException("요약이 비어 있음");
    }
    if (DIGIT.matcher(generation.summary()).find()) {
      throw new UngroundedSummaryException("요약에 숫자 포함");
    }
    validateSentiment(generation.overallSentiment(), averageRating(reviews));

    Set<Long> inputIds =
        reviews.stream().map(ReviewInput::reviewId).collect(Collectors.toUnmodifiableSet());
    int minSupport = reviews.size() >= STRICT_SUPPORT_REVIEW_COUNT ? 2 : 1;

    return new GroundedSummary(
        generation.summary().trim(),
        groundedItems("pros", generation.pros(), inputIds, minSupport),
        groundedItems("cons", generation.cons(), inputIds, minSupport));
  }

  private void validateSentiment(Sentiment sentiment, BigDecimal average) {
    if (sentiment == null) {
      throw new UngroundedSummaryException("분위기 판단 없음");
    }
    boolean contradicts =
        (average.compareTo(HIGH_RATING) >= 0 && sentiment == Sentiment.NEGATIVE)
            || (average.compareTo(LOW_RATING) <= 0 && sentiment == Sentiment.POSITIVE);
    if (contradicts) {
      throw new UngroundedSummaryException("평균 평점 " + average + " 과 분위기 " + sentiment + " 불일치");
    }
  }

  private List<String> groundedItems(
      String label, List<Item> items, Set<Long> inputIds, int minSupport) {
    if (items == null) {
      return List.of();
    }
    List<String> kept =
        items.stream()
            .filter(Objects::nonNull)
            .filter(item -> item.text() != null && !item.text().isBlank())
            .filter(item -> !DIGIT.matcher(item.text()).find())
            .filter(item -> supportCount(item, inputIds) >= minSupport)
            .map(item -> item.text().trim())
            .distinct()
            .limit(MAX_ITEMS)
            .toList();
    if (kept.size() < items.size()) {
      log.info("[리뷰요약] {} {}개 중 {}개만 근거 확인", label, items.size(), kept.size());
    }
    return kept;
  }

  /** 실제 입력 리뷰에 있는 근거 ID 의 개수 (중복 제외). 없는 ID 는 세지 않는다. */
  private long supportCount(Item item, Set<Long> inputIds) {
    if (item.supportingReviewIds() == null) {
      return 0;
    }
    return item.supportingReviewIds().stream()
        .filter(Objects::nonNull)
        .filter(inputIds::contains)
        .distinct()
        .count();
  }

  private BigDecimal averageRating(List<ReviewInput> reviews) {
    if (reviews.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal sum =
        reviews.stream()
            .map(ReviewInput::rating)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(reviews.size()), 1, java.math.RoundingMode.HALF_UP);
  }
}
