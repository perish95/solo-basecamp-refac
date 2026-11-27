package com.basecamp.backend.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.ai.client.LlmClient;
import com.basecamp.backend.domain.ai.config.AiProperties;
import com.basecamp.backend.domain.ai.dto.response.ReviewSummaryResponse;
import com.basecamp.backend.domain.ai.entity.CampReviewSummary;
import com.basecamp.backend.domain.ai.repository.CampReviewSummaryRepository;
import com.basecamp.backend.domain.ai.service.ReviewSummaryGeneration.Item;
import com.basecamp.backend.domain.ai.service.ReviewSummaryGeneration.Sentiment;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.review.entity.Review;
import com.basecamp.backend.domain.review.repository.ReviewRepository;
import com.basecamp.backend.domain.review.repository.ReviewStats;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewSummaryServiceTest {

  private static final Long CAMP_ID = 7L;
  private static final LocalDateTime LAST_MODIFIED = LocalDateTime.of(2026, 11, 27, 10, 0);
  private static final String CURRENT_FINGERPRINT =
      "5|" + LAST_MODIFIED + "|" + ReviewSummaryPrompt.VERSION;

  private final AiProperties properties = new AiProperties();
  private final LlmClient llmClient = mock(LlmClient.class);
  private final CampReviewSummaryRepository summaryRepository =
      mock(CampReviewSummaryRepository.class);
  private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
  private final CampRepository campRepository = mock(CampRepository.class);

  private ReviewSummaryService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    properties.setEnabled(true);
    ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);
    given(provider.getIfAvailable()).willReturn(llmClient);
    service =
        new ReviewSummaryService(
            properties,
            provider,
            new ReviewSummaryGroundingValidator(),
            summaryRepository,
            reviewRepository,
            campRepository);
    given(campRepository.existsById(CAMP_ID)).willReturn(true);
  }

  /** 리뷰 5건(id 1..5, 평점 4)이 있고 마지막 수정 시각이 LAST_MODIFIED 인 상태 */
  private void givenFiveReviews() {
    given(reviewRepository.findStatsByCampId(CAMP_ID))
        .willReturn(new ReviewStats(5L, LAST_MODIFIED));
    List<Review> reviews =
        LongStream.rangeClosed(1, 5)
            .mapToObj(
                id -> {
                  Review review = Review.builder().rating(4).content("좋았어요 " + id).build();
                  ReflectionTestUtils.setField(review, "reviewId", id);
                  return review;
                })
            .toList();
    given(reviewRepository.findTop100ByCamp_CampIdOrderByCreatedAtDescReviewIdDesc(CAMP_ID))
        .willReturn(reviews);
  }

  private void givenLlmReturns(ReviewSummaryGeneration first, ReviewSummaryGeneration... rest) {
    given(
            llmClient.generateStructured(
                anyString(), anyString(), anyString(), eq(ReviewSummaryGeneration.class)))
        .willReturn(first, rest);
  }

  private static ReviewSummaryGeneration grounded() {
    return new ReviewSummaryGeneration(
        "전망과 청결이 좋다는 후기가 이어져요.",
        List.of(new Item("탁 트인 전망", List.of(1L, 2L))),
        List.of(new Item("주말 소음", List.of(3L, 4L))),
        Sentiment.POSITIVE);
  }

  private static ReviewSummaryGeneration withNumber() {
    return new ReviewSummaryGeneration(
        "평점 4.8로 만족도가 높아요.", List.of(), List.of(), Sentiment.POSITIVE);
  }

  private static CampReviewSummary cachedSummary(String fingerprint) {
    return CampReviewSummary.builder()
        .campId(CAMP_ID)
        .summary("이전 요약")
        .pros(List.of("이전 장점"))
        .cons(List.of())
        .basedOnReviewCount(4)
        .sourceFingerprint(fingerprint)
        .model("claude-opus-5-5")
        .promptVersion(ReviewSummaryPrompt.VERSION)
        .generatedAt(LAST_MODIFIED.minusDays(1))
        .build();
  }

  @Test
  void getSummary_리뷰3개미만_INSUFFICIENT_LLM호출안함() {
    // given
    given(reviewRepository.findStatsByCampId(CAMP_ID))
        .willReturn(new ReviewStats(2L, LAST_MODIFIED));

    // when
    ReviewSummaryResponse response = service.getSummary(CAMP_ID);

    // then
    assertThat(response.status()).isEqualTo(ReviewSummaryResponse.Status.INSUFFICIENT_REVIEWS);
    assertThat(response.basedOnReviewCount()).isEqualTo(2);
    verify(llmClient, never()).generateStructured(any(), any(), any(), any());
  }

  @Test
  void getSummary_지문같음_저장된요약반환_LLM호출안함() {
    // given
    givenFiveReviews();
    given(summaryRepository.findById(CAMP_ID))
        .willReturn(Optional.of(cachedSummary(CURRENT_FINGERPRINT)));

    // when
    ReviewSummaryResponse response = service.getSummary(CAMP_ID);

    // then
    assertThat(response.summary()).isEqualTo("이전 요약");
    verify(llmClient, never()).generateStructured(any(), any(), any(), any());
  }

  @Test
  void getSummary_지문다름_재생성후저장() {
    // given
    givenFiveReviews();
    given(summaryRepository.findById(CAMP_ID)).willReturn(Optional.of(cachedSummary("4|old|v1")));
    givenLlmReturns(grounded());

    // when
    ReviewSummaryResponse response = service.getSummary(CAMP_ID);

    // then
    assertThat(response.status()).isEqualTo(ReviewSummaryResponse.Status.READY);
    assertThat(response.pros()).containsExactly("탁 트인 전망");
    assertThat(response.basedOnReviewCount()).isEqualTo(5);

    ArgumentCaptor<CampReviewSummary> saved = ArgumentCaptor.forClass(CampReviewSummary.class);
    verify(summaryRepository).save(saved.capture());
    assertThat(saved.getValue().getSourceFingerprint()).isEqualTo(CURRENT_FINGERPRINT);
    assertThat(saved.getValue().getModel()).isEqualTo("claude-opus-5-5");
  }

  @Test
  void getSummary_요약전용모델설정_그모델로호출() {
    // given
    properties.setSummaryModel("claude-haiku-5-5");
    givenFiveReviews();
    given(summaryRepository.findById(CAMP_ID)).willReturn(Optional.empty());
    givenLlmReturns(grounded());

    // when
    service.getSummary(CAMP_ID);

    // then
    verify(llmClient)
        .generateStructured(
            eq("claude-haiku-5-5"), anyString(), anyString(), eq(ReviewSummaryGeneration.class));
  }

  @Test
  void getSummary_첫생성근거검증실패_재시도성공() {
    // given
    givenFiveReviews();
    given(summaryRepository.findById(CAMP_ID)).willReturn(Optional.empty());
    givenLlmReturns(withNumber(), grounded());

    // when
    ReviewSummaryResponse response = service.getSummary(CAMP_ID);

    // then
    assertThat(response.summary()).isEqualTo("전망과 청결이 좋다는 후기가 이어져요.");
    verify(llmClient, times(2)).generateStructured(any(), any(), any(), any());
  }

  @Test
  void getSummary_두번모두실패_이전요약반환_저장안함() {
    // given
    givenFiveReviews();
    given(summaryRepository.findById(CAMP_ID)).willReturn(Optional.of(cachedSummary("4|old|v1")));
    givenLlmReturns(withNumber(), withNumber());

    // when
    ReviewSummaryResponse response = service.getSummary(CAMP_ID);

    // then
    assertThat(response.summary()).isEqualTo("이전 요약");
    verify(summaryRepository, never()).save(any());
  }

  @Test
  void getSummary_두번모두실패_이전요약없음_AI_UPSTREAM_ERROR() {
    // given
    givenFiveReviews();
    given(summaryRepository.findById(CAMP_ID)).willReturn(Optional.empty());
    givenLlmReturns(withNumber(), withNumber());

    // when / then
    assertThatThrownBy(() -> service.getSummary(CAMP_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_UPSTREAM_ERROR);
  }

  @Test
  void getSummary_AI비활성_AI_DISABLED() {
    // given
    properties.setEnabled(false);

    // when / then
    assertThatThrownBy(() -> service.getSummary(CAMP_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_DISABLED);
  }

  @Test
  void getSummary_없는캠핑장_CAMP_NOT_FOUND() {
    // given
    given(campRepository.existsById(99L)).willReturn(false);

    // when / then
    assertThatThrownBy(() -> service.getSummary(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CAMP_NOT_FOUND);
  }
}
