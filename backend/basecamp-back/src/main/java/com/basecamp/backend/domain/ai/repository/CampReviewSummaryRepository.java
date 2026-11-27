package com.basecamp.backend.domain.ai.repository;

import com.basecamp.backend.domain.ai.entity.CampReviewSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampReviewSummaryRepository extends JpaRepository<CampReviewSummary, Long> {}
