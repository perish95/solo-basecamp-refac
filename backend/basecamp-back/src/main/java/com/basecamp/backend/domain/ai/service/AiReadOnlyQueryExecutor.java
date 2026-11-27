package com.basecamp.backend.domain.ai.service;

import com.basecamp.backend.domain.ai.config.AiProperties;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 검증된 NL2SQL 쿼리를 읽기 전용 커넥션으로 실행한다 (안전장치 3·4번째 겹).
 *
 * <p>운영에서는 v_ai_camps SELECT 권한만 가진 계정(ai.datasource.*)으로 붙는다. 검증기를 우회하는 SQL 이 있어도 DB 권한이 막는다.
 * ai.datasource.username 이 비어 있으면 로컬 개발 편의를 위해 spring.datasource 계정을 쓴다.
 *
 * <p>DataSource/JdbcTemplate 를 빈으로 노출하지 않는 이유: 빈으로 등록하면 Spring Boot 의 기본 DataSource·JdbcTemplate 자동
 * 구성이 꺼져(@ConditionalOnMissingBean) JPA 가 이 읽기 전용 커넥션을 쓰게 된다. 그래서 이 클래스 안에만 둔다. 커넥션 풀은 첫 쿼리 때
 * 만들어진다.
 */
@Component
public class AiReadOnlyQueryExecutor implements DisposableBean {

  private final HikariDataSource dataSource;
  private final JdbcTemplate jdbcTemplate;

  public AiReadOnlyQueryExecutor(AiProperties properties, DataSourceProperties mainDataSource) {
    AiProperties.Datasource ai = properties.getDatasource();
    boolean dedicated = StringUtils.hasText(ai.getUsername());

    this.dataSource = new HikariDataSource();
    dataSource.setPoolName("ai-readonly");
    dataSource.setJdbcUrl(
        dedicated && StringUtils.hasText(ai.getUrl()) ? ai.getUrl() : mainDataSource.getUrl());
    dataSource.setUsername(dedicated ? ai.getUsername() : mainDataSource.getUsername());
    dataSource.setPassword(dedicated ? ai.getPassword() : mainDataSource.getPassword());
    dataSource.setReadOnly(true);
    dataSource.setMaximumPoolSize(2);
    dataSource.setMinimumIdle(0);

    this.jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.setQueryTimeout(properties.getSql().getQueryTimeoutSeconds());
    jdbcTemplate.setMaxRows(properties.getSql().getMaxRows());
  }

  /** 검증된 SQL 을 실행해 camp_id 목록을 결과 순서대로 돌려준다. */
  public List<Long> findCampIds(String validatedSql) {
    return jdbcTemplate.queryForList(validatedSql, Long.class);
  }

  @Override
  public void destroy() {
    dataSource.close();
  }
}
