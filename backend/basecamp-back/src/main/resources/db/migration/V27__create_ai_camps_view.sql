-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V27
--  변경     : 자연어 검색(NL2SQL) 전용 VIEW v_ai_camps 추가
--  사유     : LLM 이 생성한 SQL 은 이 VIEW 하나만 조회할 수 있다.
--            - 운영 중이고 삭제되지 않은 캠핑장의 공개 정보만 담는다.
--            - owner_id, 사용자/예약/결제 테이블은 VIEW 에 없으므로 LLM 이 알 수도, 조회할 수도 없다.
--            - 컬럼명은 LLM 이 이해하기 쉬운 snake_case 영문으로 바꿔 노출한다.
--  권한     : 운영에서는 이 VIEW 에 SELECT 권한만 가진 전용 계정(basecamp_ai)으로 실행한다.
--            계정 생성 SQL 은 docs/guides/ai-nl2sql-guide.md 참고.
-- =============================================================

CREATE VIEW v_ai_camps AS
SELECT c.camp_id                       AS camp_id,
       c.faclt_nm                      AS name,
       c.addr1                         AS address,
       c.induty                        AS camp_types,
       c.lineIntro                     AS intro,
       c.price                         AS price,
       c.average_rating                AS average_rating,
       COALESCE(r.review_count, 0)     AS review_count,
       c.gnrl_site_co                  AS general_sites,
       c.auto_site_co                  AS auto_sites,
       c.glamp_site_co                 AS glamping_sites,
       c.toiletCo                      AS toilets,
       c.swrmCo                        AS showers,
       c.wtrplCo                       AS water_play_areas,
       c.sbrsCl                        AS amenities,
       c.glampInnerFclty               AS glamping_inner_facilities,
       c.caravInnerFclty               AS caravan_inner_facilities,
       c.operDeCl                      AS operating_days,
       c.created_at                    AS created_at
FROM camps c
LEFT JOIN (SELECT camp_id, COUNT(*) AS review_count
           FROM reviews
           GROUP BY camp_id) r ON r.camp_id = c.camp_id
WHERE c.manage_sttus = '운영'
  AND c.deleted_at IS NULL;
