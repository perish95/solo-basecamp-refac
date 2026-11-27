package com.basecamp.backend.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.ai.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlValidatorTest {

  private final SqlValidator validator = new SqlValidator(new AiProperties());

  @ParameterizedTest
  @ValueSource(
      strings = {
        "SELECT camp_id FROM v_ai_camps WHERE address LIKE '%강원%' AND camp_types LIKE '%글램핑%' ORDER BY average_rating DESC LIMIT 10",
        "select camp_id from v_ai_camps where price <= 100000 order by review_count desc limit 20",
        "SELECT c.camp_id FROM v_ai_camps c WHERE c.toilets >= 3 AND c.water_play_areas > 0 LIMIT 5",
        "SELECT `camp_id` FROM `v_ai_camps` WHERE amenities LIKE '%장작판매%' LIMIT 10",
        "SELECT camp_id FROM v_ai_camps WHERE average_rating >= (SELECT AVG(average_rating) FROM v_ai_camps) LIMIT 10",
        "SELECT camp_id FROM v_ai_camps WHERE intro LIKE '%바다%' OR intro LIKE '%오션뷰%' LIMIT 10;",
      })
  void validate_허용된쿼리_통과(String sql) {
    // when
    String result = validator.validate(sql);

    // then
    assertThat(result).containsIgnoringCase("v_ai_camps").containsIgnoringCase("LIMIT");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // 다른 테이블 / 스키마
        "SELECT camp_id FROM camps LIMIT 10",
        "SELECT user_id FROM users",
        "SELECT camp_id FROM basecamp.v_ai_camps LIMIT 10",
        "SELECT camp_id FROM v_ai_camps WHERE camp_id IN (SELECT camp_id FROM reservations) LIMIT 10",
        "SELECT camp_id FROM v_ai_camps v JOIN users u ON u.user_id = v.camp_id LIMIT 10",
        "SELECT camp_id FROM information_schema.tables",
        // UNION / WITH / 다중 statement
        "SELECT camp_id FROM v_ai_camps UNION SELECT user_id FROM users",
        "WITH x AS (SELECT camp_id FROM v_ai_camps) SELECT camp_id FROM x",
        "SELECT camp_id FROM v_ai_camps; DROP TABLE camps",
        "SELECT camp_id FROM v_ai_camps; SELECT camp_id FROM v_ai_camps",
        // 쓰기 / DDL
        "UPDATE camps SET price = 0",
        "DELETE FROM camps",
        "DROP VIEW v_ai_camps",
        "INSERT INTO camps (camp_id) VALUES (1)",
        // SELECT 절 제한
        "SELECT * FROM v_ai_camps LIMIT 10",
        "SELECT camp_id, name FROM v_ai_camps LIMIT 10",
        "SELECT name FROM v_ai_camps LIMIT 10",
        // 위험 함수 / 파일 쓰기 / 잠금
        "SELECT camp_id FROM v_ai_camps WHERE SLEEP(10) = 0 LIMIT 10",
        "SELECT camp_id FROM v_ai_camps WHERE BENCHMARK(100000000, MD5('a')) = 0",
        "SELECT camp_id FROM v_ai_camps WHERE name = LOAD_FILE('/etc/passwd')",
        "SELECT camp_id FROM v_ai_camps INTO OUTFILE '/tmp/x'",
        "SELECT camp_id FROM v_ai_camps FOR UPDATE",
        // 파싱 불가 / 빈 값
        "camp_id 를 전부 보여줘",
        "",
      })
  void validate_위험하거나허용되지않은쿼리_거부(String sql) {
    assertThatThrownBy(() -> validator.validate(sql))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_SQL_REJECTED);
  }

  @Test
  void validate_LIMIT없음_최대행수로추가() {
    // when
    String result = validator.validate("SELECT camp_id FROM v_ai_camps WHERE price < 50000");

    // then
    assertThat(result).endsWith("LIMIT 50");
  }

  @Test
  void validate_LIMIT초과_최대행수로축소() {
    // when
    String result = validator.validate("SELECT camp_id FROM v_ai_camps LIMIT 100000");

    // then
    assertThat(result).endsWith("LIMIT 50");
  }

  @Test
  void validate_주석으로숨긴구문_제거된SQL반환() {
    // when
    String result =
        validator.validate("SELECT camp_id FROM v_ai_camps /* DROP TABLE camps */ LIMIT 10");

    // then
    assertThat(result).doesNotContain("DROP");
  }
}
