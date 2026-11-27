# AI 자연어 캠핑장 검색(NL2SQL) 가이드

> `POST /api/v1/camps/ai-search` 가 어떻게 동작하고, 로컬/운영에서 무엇을 설정해야 하는지 정리한 문서입니다.

## 1. 흐름

```
질문 ─▶ Claude 가 SQL 생성 ─▶ SqlValidator 검증 ─▶ 읽기 전용 계정으로 실행 ─▶ camp_id 로 기존 조회 ─▶ 응답
            (v_ai_camps 스키마만 앎)   (SELECT/VIEW/LIMIT)      (basecamp_ai, 3초)        (CampResponseDto)
```

- LLM 은 `camp_id` 만 고릅니다. 응답 데이터는 기존 `CampRepository` / `CampResponseDto` 로 만들어 일반 검색과 형식이 같습니다.
- LLM 호출은 DB 트랜잭션 밖에서 하고, 캠핑장 조회만 짧은 읽기 트랜잭션으로 묶습니다.
- 호출마다 비용이 들어 **로그인 사용자만** 쓸 수 있습니다.

## 2. 안전장치 (한 겹이 뚫려도 다음 겹이 막도록)

| 겹 | 내용 | 위치 |
|---|---|---|
| 1 | LLM 에게는 `v_ai_camps` VIEW 스키마만 알려줌. owner_id·사용자·결제 테이블은 VIEW 에 없음 | `V27__create_ai_camps_view.sql`, `Nl2SqlPrompt` |
| 2 | 파서로 검증: 단일 SELECT, 대상은 `v_ai_camps` 뿐, `camp_id` 만 조회, 위험 함수·INTO OUTFILE·잠금 절 차단, LIMIT 강제(최대 50) | `SqlValidator` |
| 3 | `v_ai_camps` SELECT 권한만 가진 읽기 전용 계정으로 실행 | `AiReadOnlyQueryExecutor` |
| 4 | 쿼리 타임아웃 3초, 최대 50행 | `ai.sql.*` |
| 5 | 표현할 수 없는 질문은 결과를 지어내지 않고 422 로 거절 | `Nl2SqlService` |

> 프롬프트로 "SELECT 만 써라"라고 한 것은 보안 경계가 아닙니다. 실제 방어는 2~4번입니다.

## 3. 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `AI_ENABLED` | `false` | `true` 여야 Claude 클라이언트가 만들어집니다. `false` 면 AI 엔드포인트는 503(AI001) |
| `ANTHROPIC_API_KEY` | (없음) | Claude API 키. **yml 에 적지 말고 환경변수로만** 주입 |
| `AI_MODEL` | `claude-opus-5-5` | 사용할 모델 ID |
| `AI_TIMEOUT_SECONDS` | `30` | Claude API 호출 타임아웃 |
| `AI_DB_URL` / `AI_DB_USERNAME` / `AI_DB_PASSWORD` | (비어 있음) | 읽기 전용 계정. username 이 비어 있으면 `spring.datasource` 계정을 씀(로컬 편의용) |

## 4. 읽기 전용 DB 계정 만들기 (운영 필수, 로컬 권장)

Flyway 로는 계정을 만들 수 없어 직접 실행합니다. V27 마이그레이션이 적용된 뒤에 실행하세요.

```sql
CREATE USER 'basecamp_ai'@'%' IDENTIFIED BY '<강한 비밀번호>';
GRANT SELECT ON basecamp.v_ai_camps TO 'basecamp_ai'@'%';
FLUSH PRIVILEGES;
```

VIEW 는 기본값(`SQL SECURITY DEFINER`)으로 만들어지므로, 이 계정은 `camps`/`reviews` 테이블 권한 없이도 VIEW 만 조회할 수 있습니다.
확인: `basecamp_ai` 로 접속해 `SELECT * FROM camps LIMIT 1;` 이 거부되면 정상입니다.

## 5. 에러 코드

| 코드 | HTTP | 상황 |
|---|---|---|
| AI001 | 503 | AI 기능 비활성화 (`AI_ENABLED=false`) |
| AI002 | 422 | 표현할 수 없는 질문 (날짜별 빈자리, 거리 계산 등) 또는 모델이 요청을 거절 |
| AI003 | 422 | 생성된 SQL 이 검증을 통과하지 못했거나 실행 중 오류/타임아웃 |
| AI004 | 502 | Claude API 호출 실패, 응답 잘림/파싱 실패 |

## 6. 모델 호출 설정

- 구조화 출력: 응답을 `SqlGeneration` 레코드 스키마에 맞춰 받습니다(자유 텍스트 파싱 없음).
- `effort: low`: 짧은 변환 작업이라 추론량을 줄여 지연을 낮춥니다.
- 서버 측 fallback(`server-side-fallback-2026-07-01`, `fallbacks: "default"`): 안전 분류기가 거절하면 API 가 다른 모델로 자동 재시도합니다. 그래도 거절되면 AI002 로 응답합니다.
- 생성된 SQL 은 응답에 넣지 않고 서버 로그(`[NL2SQL] query=..., sql=...`)에만 남깁니다.
