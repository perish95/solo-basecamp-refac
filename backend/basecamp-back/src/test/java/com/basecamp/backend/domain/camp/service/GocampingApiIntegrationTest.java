package com.basecamp.backend.domain.camp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.basecamp.backend.domain.camp.repository.CampRepository;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 고캠핑 API 실제 호출 통합 테스트.
 *
 * <p>외부 서버·네트워크에 의존하므로 CI 에서는 제외된다 (build.gradle 의 excludeTags 참고). 로컬에서 실행: ./gradlew
 * integrationTest 또는 IDE 에서 직접 실행. 실행 조건: MySQL 컨테이너가 떠 있어야 하고, 고캠핑 API 키가 설정돼 있어야 한다.
 */
@Tag("integration")
@SpringBootTest
class GocampingApiIntegrationTest {

  @Autowired private CampService campService;
  @Autowired private CampRepository campRepository;

  @Test
  @DisplayName("고캠핑 API를 실제 호출해 캠핑장 데이터가 적재된다")
  void fetchAndSaveFromRealApi() throws Exception {
    long before = campRepository.count();

    // @Async 로 별도 스레드에서 도는 작업이라, 저장 결과를 확인하려면 완료를 기다려야 한다.
    campService.fetchAndSaveCampsFromGocampingApi().get(60, TimeUnit.SECONDS);

    long after = campRepository.count();
    // 멱등이라 재실행 시 신규 0건일 수 있으므로 "줄지 않았다"로 검증
    assertThat(after).isGreaterThanOrEqualTo(before);
    // 최소 한 건 이상 적재돼 있고, 고캠핑 연동 캠프가 존재하는지
    assertThat(after).isPositive();
  }
}
