package com.basecamp.backend.domain.ai.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.ai.client.LlmClient;
import com.basecamp.backend.domain.ai.config.AiProperties;
import com.basecamp.backend.domain.ai.dto.response.AiSearchResponse;
import com.basecamp.backend.domain.camp.dto.response.CampResponseDto;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 자연어 캠핑장 검색 (NL2SQL).
 *
 * <p>흐름: 질문 → Claude 가 SQL 생성 → {@link SqlValidator} 검증 → 읽기 전용 계정으로 실행 → camp_id 로 기존 캠핑장 조회.
 *
 * <p>LLM 은 camp_id 만 고르고, 응답 데이터는 기존 Repository/DTO 로 만든다 — 응답 형식이 일반 검색과 같고, 모델이 임의 컬럼을 노출할 수도 없다.
 * LLM 호출(수 초)은 DB 트랜잭션 밖에서 하고, 캠핑장 조회만 짧은 읽기 트랜잭션으로 묶는다.
 */
@Slf4j
@Service
public class Nl2SqlService {

  private final AiProperties properties;
  private final ObjectProvider<LlmClient> llmClient;
  private final SqlValidator sqlValidator;
  private final AiReadOnlyQueryExecutor queryExecutor;
  private final CampRepository campRepository;
  private final TransactionTemplate readOnlyTx;

  public Nl2SqlService(
      AiProperties properties,
      ObjectProvider<LlmClient> llmClient,
      SqlValidator sqlValidator,
      AiReadOnlyQueryExecutor queryExecutor,
      CampRepository campRepository,
      PlatformTransactionManager transactionManager) {
    this.properties = properties;
    this.llmClient = llmClient;
    this.sqlValidator = sqlValidator;
    this.queryExecutor = queryExecutor;
    this.campRepository = campRepository;
    this.readOnlyTx = new TransactionTemplate(transactionManager);
    this.readOnlyTx.setReadOnly(true);
  }

  public AiSearchResponse search(String query) {
    LlmClient client = llmClient.getIfAvailable();
    if (!properties.isEnabled() || client == null) {
      throw new BusinessException(ErrorCode.AI_DISABLED);
    }

    // 사용자 입력은 태그로 감싸 지시문과 구분한다(프롬프트 인젝션 완화). 실제 방어는 검증기와 DB 권한이 맡는다.
    SqlGeneration generation =
        client.generateStructured(
            Nl2SqlPrompt.SYSTEM, "<question>\n" + query + "\n</question>", SqlGeneration.class);

    if (generation.unsupported()) {
      log.info("[NL2SQL] 표현 불가 질의: query={}, reason={}", query, generation.reason());
      String reason = generation.reason();
      throw StringUtils.hasText(reason)
          ? new BusinessException(ErrorCode.AI_QUERY_UNSUPPORTED, reason)
          : new BusinessException(ErrorCode.AI_QUERY_UNSUPPORTED);
    }

    String sql = sqlValidator.validate(generation.sql());
    log.info("[NL2SQL] query={}, sql={}", query, sql);

    List<Long> campIds;
    try {
      campIds = queryExecutor.findCampIds(sql);
    } catch (DataAccessException e) {
      // 존재하지 않는 컬럼, 타임아웃, 권한 부족 등. 모델이 만든 SQL 의 문제라 사용자에게는 재질문을 안내한다.
      log.warn("[NL2SQL] SQL 실행 실패: sql={}, cause={}", sql, e.getMostSpecificCause().getMessage());
      throw new BusinessException(ErrorCode.AI_SQL_REJECTED);
    }

    return new AiSearchResponse(loadCampsInOrder(campIds), generation.explanation());
  }

  /** findAllById 는 순서를 보장하지 않으므로 SQL 결과(정렬) 순서대로 다시 맞춘다. */
  private List<CampResponseDto> loadCampsInOrder(List<Long> campIds) {
    if (campIds.isEmpty()) {
      return List.of();
    }
    return readOnlyTx.execute(
        status -> {
          Map<Long, Camp> byId =
              campRepository.findAllById(campIds).stream()
                  .collect(Collectors.toMap(Camp::getCampId, Function.identity()));
          return campIds.stream()
              .distinct()
              .map(byId::get)
              .filter(Objects::nonNull)
              .map(CampResponseDto::from)
              .toList();
        });
  }
}
