import { AxiosError } from "axios";
import instance from "./instance";
import type { Camp } from "../types";

/**
 * LLM 기반 기능 API - 자연어 캠핑장 검색(NL2SQL)과 캠핑장 리뷰 요약.
 *
 * LLM 호출은 수 초가 걸리므로 axios 기본 타임아웃(10s) 대신 30초를 쓴다.
 * 백엔드가 AI 기능을 꺼 두면(ai.enabled=false) 503 AI_DISABLED 로 응답한다.
 */
const AI_TIMEOUT_MS = 30000;

/** POST /v1/camps/ai-search 응답 */
export interface AiSearchResponse {
  camps: Camp[];
  /** 모델이 질의를 어떻게 해석했는지 한 줄 설명 */
  explanation: string;
}

/** GET /v1/camps/{campId}/review-summary 응답 */
export interface ReviewSummaryResponse {
  /** READY: 요약 있음 / INSUFFICIENT_REVIEWS: 리뷰가 적어 요약하지 않음 */
  status: "READY" | "INSUFFICIENT_REVIEWS";
  summary: string | null;
  pros: string[];
  cons: string[];
  /** 요약에 사용한 리뷰 수 (최근 리뷰 기준) */
  basedOnReviewCount: number;
  generatedAt: string | null;
}

// 자연어 질의로 캠핑장 검색. 비용이 드는 호출이라 디바운스 없이 사용자가 제출할 때만 부른다.
export const aiSearchCamps = (query: string) =>
  instance.post<AiSearchResponse>(
    "/v1/camps/ai-search",
    { query },
    { timeout: AI_TIMEOUT_MS }
  ) as unknown as Promise<AiSearchResponse>;

// 캠핑장 리뷰 요약. 백엔드가 저장된 요약을 돌려주고, 리뷰가 바뀌었을 때만 새로 생성한다.
export const getReviewSummary = (campId: number) =>
  instance.get<ReviewSummaryResponse>(`/v1/camps/${campId}/review-summary`, {
    timeout: AI_TIMEOUT_MS,
  }) as unknown as Promise<ReviewSummaryResponse>;

/** AI 기능이 서버에서 꺼져 있는지(503 AI_DISABLED) */
export const isAiDisabledError = (err: unknown) =>
  err instanceof AxiosError &&
  (err.response?.status === 503 ||
    (err.response?.data as { code?: string } | undefined)?.code === "AI_DISABLED");
