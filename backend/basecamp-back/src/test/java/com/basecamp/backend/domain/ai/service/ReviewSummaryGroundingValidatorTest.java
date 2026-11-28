package com.basecamp.backend.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basecamp.backend.domain.ai.service.ReviewSummaryGeneration.Item;
import com.basecamp.backend.domain.ai.service.ReviewSummaryGeneration.Sentiment;
import com.basecamp.backend.domain.ai.service.ReviewSummaryGroundingValidator.GroundedSummary;
import com.basecamp.backend.domain.ai.service.ReviewSummaryGroundingValidator.ReviewInput;
import com.basecamp.backend.domain.ai.service.ReviewSummaryGroundingValidator.UngroundedSummaryException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class ReviewSummaryGroundingValidatorTest {

  private final ReviewSummaryGroundingValidator validator = new ReviewSummaryGroundingValidator();

  /** id 1..count, 모두 같은 평점인 리뷰 목록 */
  private static List<ReviewInput> reviews(int count, String rating) {
    return LongStream.rangeClosed(1, count)
        .mapToObj(id -> new ReviewInput(id, new BigDecimal(rating), "리뷰 " + id))
        .toList();
  }

  private static ReviewSummaryGeneration generation(
      String summary, List<Item> pros, List<Item> cons, Sentiment sentiment) {
    return new ReviewSummaryGeneration(summary, pros, cons, sentiment);
  }

  @Test
  void validate_근거있는항목_통과() {
    // given
    ReviewSummaryGeneration gen =
        generation(
            "전망이 좋다는 평이 많아요.",
            List.of(new Item("탁 트인 전망", List.of(1L, 2L))),
            List.of(new Item("주말 소음", List.of(3L, 4L))),
            Sentiment.POSITIVE);

    // when
    GroundedSummary result = validator.validate(gen, reviews(5, "4.5"));

    // then
    assertThat(result.summary()).isEqualTo("전망이 좋다는 평이 많아요.");
    assertThat(result.pros()).containsExactly("탁 트인 전망");
    assertThat(result.cons()).containsExactly("주말 소음");
  }

  @Test
  void validate_입력에없는리뷰ID근거_항목제거() {
    // given
    ReviewSummaryGeneration gen =
        generation(
            "요약입니다.",
            List.of(new Item("수영장이 넓어요", List.of(99L, 100L)), new Item("친절함", List.of(1L, 2L))),
            List.of(),
            Sentiment.POSITIVE);

    // when
    GroundedSummary result = validator.validate(gen, reviews(5, "4.0"));

    // then
    assertThat(result.pros()).containsExactly("친절함");
  }

  @Test
  void validate_리뷰5개이상에서근거1개뿐_항목제거() {
    // given
    ReviewSummaryGeneration gen =
        generation(
            "요약입니다.",
            List.of(new Item("한 사람만 말한 장점", List.of(1L))),
            List.of(new Item("근거 없음", List.of())),
            Sentiment.MIXED);

    // when
    GroundedSummary result = validator.validate(gen, reviews(5, "3.0"));

    // then
    assertThat(result.pros()).isEmpty();
    assertThat(result.cons()).isEmpty();
  }

  @Test
  void validate_리뷰5개미만은근거1개로충분() {
    // given
    ReviewSummaryGeneration gen =
        generation("요약입니다.", List.of(new Item("조용함", List.of(2L))), List.of(), Sentiment.MIXED);

    // when
    GroundedSummary result = validator.validate(gen, reviews(3, "3.0"));

    // then
    assertThat(result.pros()).containsExactly("조용함");
  }

  @Test
  void validate_같은ID중복은한번만센다() {
    // given
    ReviewSummaryGeneration gen =
        generation(
            "요약입니다.", List.of(new Item("깨끗함", List.of(1L, 1L, 1L))), List.of(), Sentiment.MIXED);

    // when
    GroundedSummary result = validator.validate(gen, reviews(5, "3.0"));

    // then
    assertThat(result.pros()).isEmpty();
  }

  @Test
  void validate_숫자있는항목_제거_최대3개() {
    // given
    List<Item> pros =
        List.of(
            new Item("화장실 5곳", List.of(1L, 2L)),
            new Item("전망", List.of(1L, 2L)),
            new Item("친절", List.of(1L, 2L)),
            new Item("깨끗함", List.of(1L, 2L)),
            new Item("조용함", List.of(1L, 2L)));
    ReviewSummaryGeneration gen = generation("요약입니다.", pros, List.of(), Sentiment.POSITIVE);

    // when
    GroundedSummary result = validator.validate(gen, reviews(5, "4.5"));

    // then
    assertThat(result.pros()).containsExactly("전망", "친절", "깨끗함");
  }

  @Test
  void validate_요약에숫자_무효() {
    ReviewSummaryGeneration gen =
        generation("평점 4.8로 80%가 만족했어요.", List.of(), List.of(), Sentiment.POSITIVE);

    assertThatThrownBy(() -> validator.validate(gen, reviews(5, "4.5")))
        .isInstanceOf(UngroundedSummaryException.class);
  }

  @Test
  void validate_빈요약_무효() {
    ReviewSummaryGeneration gen = generation(" ", List.of(), List.of(), Sentiment.MIXED);

    assertThatThrownBy(() -> validator.validate(gen, reviews(5, "3.0")))
        .isInstanceOf(UngroundedSummaryException.class);
  }

  @Test
  void validate_평점높은데부정_무효() {
    ReviewSummaryGeneration gen = generation("별로예요.", List.of(), List.of(), Sentiment.NEGATIVE);

    assertThatThrownBy(() -> validator.validate(gen, reviews(5, "4.5")))
        .isInstanceOf(UngroundedSummaryException.class);
  }

  @Test
  void validate_평점낮은데긍정_무효() {
    ReviewSummaryGeneration gen = generation("최고예요.", List.of(), List.of(), Sentiment.POSITIVE);

    assertThatThrownBy(() -> validator.validate(gen, reviews(5, "1.5")))
        .isInstanceOf(UngroundedSummaryException.class);
  }

  @Test
  void validate_요약길이초과_무효() {
    ReviewSummaryGeneration gen =
        generation("가".repeat(301), List.of(), List.of(), Sentiment.MIXED);

    assertThatThrownBy(() -> validator.validate(gen, reviews(5, "3.0")))
        .isInstanceOf(UngroundedSummaryException.class);
  }

  @Test
  void validate_요약에이메일_무효() {
    ReviewSummaryGeneration gen =
        generation("문의는 camp@example.com 으로 하래요.", List.of(), List.of(), Sentiment.MIXED);

    assertThatThrownBy(() -> validator.validate(gen, reviews(5, "3.0")))
        .isInstanceOf(UngroundedSummaryException.class);
  }

  @Test
  void validate_긴항목과개인정보항목_제거() {
    // given
    ReviewSummaryGeneration gen =
        generation(
            "요약입니다.",
            List.of(
                new Item("가".repeat(41), List.of(1L, 2L)),
                new Item("사장님 메일 owner@camp.kr", List.of(1L, 2L)),
                new Item("친절함", List.of(1L, 2L))),
            List.of(),
            Sentiment.MIXED);

    // when
    GroundedSummary result = validator.validate(gen, reviews(5, "3.0"));

    // then
    assertThat(result.pros()).containsExactly("친절함");
  }

  @Test
  void validate_분위기누락_무효() {
    ReviewSummaryGeneration gen = generation("요약입니다.", List.of(), List.of(), null);

    assertThatThrownBy(() -> validator.validate(gen, reviews(5, "3.0")))
        .isInstanceOf(UngroundedSummaryException.class);
  }
}
