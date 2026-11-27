package com.basecamp.backend.domain.ai.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** NL2SQL 에서 Claude 가 돌려주는 구조화 출력. 이 레코드에서 JSON 스키마가 자동 생성된다. */
public record SqlGeneration(
    @JsonPropertyDescription("v_ai_camps 로 표현할 수 없는 질문이면 true") boolean unsupported,
    @JsonPropertyDescription("unsupported 가 true 일 때 사용자에게 보여줄 한국어 이유. 아니면 빈 문자열") String reason,
    @JsonPropertyDescription("SELECT camp_id FROM v_ai_camps ... 형태의 MySQL 쿼리. unsupported 면 빈 문자열")
        String sql,
    @JsonPropertyDescription("질문을 어떤 조건으로 해석했는지 사용자에게 보여줄 한국어 한 문장") String explanation) {}
