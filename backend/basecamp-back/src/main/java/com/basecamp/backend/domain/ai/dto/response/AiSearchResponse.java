package com.basecamp.backend.domain.ai.dto.response;

import com.basecamp.backend.domain.camp.dto.response.CampResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** POST /api/v1/camps/ai-search 응답. 생성된 SQL 은 노출하지 않는다(서버 로그에만 남김). */
public record AiSearchResponse(
    @Schema(description = "검색된 캠핑장 (관련도 순)") List<CampResponseDto> camps,
    @Schema(description = "AI 가 질문을 어떻게 해석했는지 한 문장 설명") String explanation) {}
