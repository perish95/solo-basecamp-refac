package com.basecamp.backend.domain.review.repository;

import java.time.LocalDateTime;

/**
 * 캠핑장 리뷰 집계: 리뷰 수와 마지막 작성/수정 시각. 리뷰가 없으면 lastModifiedAt 은 null.
 *
 * @see ReviewRepository#findStatsByCampId(Long)
 */
public record ReviewStats(Long count, LocalDateTime lastModifiedAt) {}
