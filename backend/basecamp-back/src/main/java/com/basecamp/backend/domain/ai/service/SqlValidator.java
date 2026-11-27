package com.basecamp.backend.domain.ai.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.ai.config.AiProperties;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

/**
 * LLM 이 생성한 SQL 을 실행 전에 검증한다 (NL2SQL 안전장치 2번째 겹).
 *
 * <p>문자열 정규식이 아니라 파서로 구조를 확인해야 주석·대소문자·서브쿼리 같은 우회를 막을 수 있다. 규칙:
 *
 * <ul>
 *   <li>단일 SELECT 문 (UNION, WITH, 다중 statement 불가)
 *   <li>조회 대상은 {@code v_ai_camps} 뿐 (스키마 지정, 다른 테이블 불가 — 서브쿼리 포함)
 *   <li>SELECT 절은 {@code camp_id} 하나
 *   <li>SLEEP/BENCHMARK/LOAD_FILE 같은 위험 함수, INTO OUTFILE, 잠금 절 불가
 *   <li>LIMIT 이 없거나 max-rows 보다 크면 max-rows 로 바꾼다
 * </ul>
 *
 * <p>이 검증을 통과해도 실행은 VIEW SELECT 권한만 있는 읽기 전용 계정으로 한다 — 검증기에 버그가 있어도 DB 권한이 마지막으로 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlValidator {

  static final String ALLOWED_TABLE = "v_ai_camps";
  private static final String ALLOWED_COLUMN = "camp_id";

  private static final Pattern FORBIDDEN =
      Pattern.compile(
          "\\b(SLEEP|BENCHMARK|LOAD_FILE|GET_LOCK|RELEASE_LOCK|SYSTEM_USER|USER|CURRENT_USER|"
              + "DATABASE|SCHEMA|VERSION|OUTFILE|DUMPFILE|LOCK|UPDATE|SHARE)\\b",
          Pattern.CASE_INSENSITIVE);

  private final AiProperties properties;

  /**
   * @return 검증을 통과하고 LIMIT 이 보정된 SQL
   * @throws BusinessException AI_SQL_REJECTED — 규칙 위반
   */
  public String validate(String sql) {
    if (sql == null || sql.isBlank()) {
      throw reject("빈 SQL", sql);
    }

    Statements statements;
    try {
      statements = CCJSqlParserUtil.parseStatements(sql);
    } catch (JSQLParserException e) {
      throw reject("파싱 실패", sql);
    }
    if (statements.size() != 1) {
      throw reject("statement 가 1개가 아님", sql);
    }

    Statement statement = statements.get(0);
    // PlainSelect 가 아니면 UNION/INTERSECT(SetOperationList), 괄호 SELECT, INSERT/UPDATE/DELETE/DDL 등이다.
    if (!(statement instanceof PlainSelect select)) {
      throw reject("단일 SELECT 가 아님", sql);
    }
    if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
      throw reject("WITH 절 사용", sql);
    }
    if (select.getIntoTables() != null && !select.getIntoTables().isEmpty()) {
      throw reject("SELECT INTO 사용", sql);
    }
    if (select.getForMode() != null) {
      throw reject("잠금 절 사용", sql);
    }

    validateTables(statement, sql);
    validateSelectItems(select.getSelectItems(), sql);

    // 함수/키워드 차단은 주석이 제거된 재조립 SQL 에 적용한다.
    if (FORBIDDEN.matcher(select.toString()).find()) {
      throw reject("금지된 함수/키워드", sql);
    }

    enforceLimit(select);
    return select.toString();
  }

  private void validateTables(Statement statement, String sql) {
    Set<String> tables = new TablesNamesFinder<>().getTables(statement);
    if (tables.isEmpty()) {
      throw reject("조회 대상 테이블 없음", sql);
    }
    for (String table : tables) {
      String normalized = table.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
      if (!ALLOWED_TABLE.equals(normalized)) {
        throw reject("허용되지 않은 테이블: " + table, sql);
      }
    }
  }

  private void validateSelectItems(List<SelectItem<?>> items, String sql) {
    if (items == null || items.size() != 1) {
      throw reject("SELECT 절이 camp_id 하나가 아님", sql);
    }
    if (!(items.get(0).getExpression() instanceof Column column)
        || !ALLOWED_COLUMN.equalsIgnoreCase(column.getColumnName().replace("`", ""))) {
      throw reject("SELECT 절이 camp_id 하나가 아님", sql);
    }
  }

  private void enforceLimit(PlainSelect select) {
    int maxRows = properties.getSql().getMaxRows();
    Limit limit = select.getLimit();
    if (limit == null) {
      select.setLimit(new Limit().withRowCount(new LongValue(maxRows)));
      return;
    }
    if (!(limit.getRowCount() instanceof LongValue rowCount) || rowCount.getValue() > maxRows) {
      limit.setRowCount(new LongValue(maxRows));
    }
    if (limit.getOffset() != null && !(limit.getOffset() instanceof LongValue)) {
      limit.setOffset(null);
    }
  }

  private BusinessException reject(String reason, String sql) {
    log.warn("[NL2SQL] SQL 거부 - {}: {}", reason, sql);
    return new BusinessException(ErrorCode.AI_SQL_REJECTED);
  }
}
