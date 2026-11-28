package com.basecamp.backend.domain.ai.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.ai.dto.request.AiSearchRequest;
import com.basecamp.backend.domain.ai.dto.response.AiSearchResponse;
import com.basecamp.backend.domain.ai.service.Nl2SqlService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 자연어 캠핑장 검색 (NL2SQL)
@RestController
@RequestMapping("/api/v1/camps")
@RequiredArgsConstructor
public class AiSearchController {

  private final Nl2SqlService nl2SqlService;

  // 호출마다 LLM 비용이 들어 로그인 사용자만 쓸 수 있다(SecurityConfig 의 anyRequest().authenticated()).
  @Operation(
      summary = "AI 자연어 캠핑장 검색",
      description =
          "자연어 질문을 SQL 로 바꿔 캠핑장을 검색합니다. 로그인 필요. "
              + "표현할 수 없는 질문은 422(AI002), 생성된 SQL 이 검증을 통과하지 못하면 422(AI003), "
              + "사용자별 호출 한도를 넘으면 429(AI005), AI 기능이 꺼져 있으면 503(AI001)을 반환합니다.")
  @PostMapping("/ai-search")
  public ResponseEntity<AiSearchResponse> aiSearch(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody AiSearchRequest request) {
    return ResponseEntity.ok(nl2SqlService.search(user.id(), request.query()));
  }
}
