-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V28
--  DB      : MySQL 8.0+
--  변경     : camp_review_summaries 테이블 추가 (캠핑장 리뷰 AI 요약 캐시)
--  사유     : 요약은 LLM 호출 비용이 들어 매 조회마다 만들지 않고 저장해 재사용한다.
--            source_fingerprint(리뷰 수 + 마지막 작성/수정 시각 + 프롬프트 버전)가 현재 리뷰와 다를 때만 다시 만든다.
-- =============================================================

CREATE TABLE camp_review_summaries
(
    camp_id               BIGINT       NOT NULL COMMENT '요약 대상 캠핑장 (1캠핑장 1요약)',
    summary               TEXT         NOT NULL COMMENT '요약 문단',
    pros                  TEXT         NOT NULL COMMENT '좋았던 점 (JSON 문자열 배열)',
    cons                  TEXT         NOT NULL COMMENT '아쉬운 점 (JSON 문자열 배열)',
    based_on_review_count INT          NOT NULL COMMENT '요약에 사용한 리뷰 수',
    source_fingerprint    VARCHAR(100) NOT NULL COMMENT '요약 시점의 리뷰 지문. 현재 지문과 다르면 재생성',
    model                 VARCHAR(100) NOT NULL COMMENT '요약을 만든 모델 ID',
    prompt_version        VARCHAR(20)  NOT NULL COMMENT '요약 프롬프트 버전',
    generated_at          DATETIME     NOT NULL COMMENT '생성 일시',

    PRIMARY KEY (camp_id),
    CONSTRAINT fk_camp_review_summaries_camp FOREIGN KEY (camp_id) REFERENCES camps (camp_id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='캠핑장 리뷰 AI 요약 캐시';
