package com.basecamp.backend.domain.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 캠핑장 리뷰 AI 요약 캐시 (camp_review_summaries). 캠핑장당 한 행. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "camp_review_summaries")
public class CampReviewSummary {

  @Id
  @Column(name = "camp_id")
  private Long campId;

  @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
  private String summary;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "pros", columnDefinition = "TEXT", nullable = false)
  private List<String> pros;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "cons", columnDefinition = "TEXT", nullable = false)
  private List<String> cons;

  @Column(name = "based_on_review_count", nullable = false)
  private int basedOnReviewCount;

  /** 요약 시점의 리뷰 지문(리뷰 수 + 마지막 작성/수정 시각 + 프롬프트 버전). 현재 지문과 다르면 다시 만든다. */
  @Column(name = "source_fingerprint", nullable = false, length = 100)
  private String sourceFingerprint;

  @Column(name = "model", nullable = false, length = 100)
  private String model;

  @Column(name = "prompt_version", nullable = false, length = 20)
  private String promptVersion;

  @Column(name = "generated_at", nullable = false)
  private LocalDateTime generatedAt;

  @Builder
  private CampReviewSummary(
      Long campId,
      String summary,
      List<String> pros,
      List<String> cons,
      int basedOnReviewCount,
      String sourceFingerprint,
      String model,
      String promptVersion,
      LocalDateTime generatedAt) {
    this.campId = campId;
    this.summary = summary;
    this.pros = pros;
    this.cons = cons;
    this.basedOnReviewCount = basedOnReviewCount;
    this.sourceFingerprint = sourceFingerprint;
    this.model = model;
    this.promptVersion = promptVersion;
    this.generatedAt = generatedAt;
  }
}
