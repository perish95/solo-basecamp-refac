package com.basecamp.backend.domain.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/camps/ai-search 요청. */
public record AiSearchRequest(
    @Schema(description = "자연어 검색 질문", example = "강원도 글램핑 중 평점 높은 곳")
        @NotBlank(message = "검색어를 입력해 주세요.")
        @Size(max = 200, message = "검색어는 200자 이하로 입력해 주세요.")
        String query) {}
