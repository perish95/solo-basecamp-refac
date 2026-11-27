import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Sparkles, X } from "lucide-react";
import { AxiosError } from "axios";
import CampCard from "../common/CampCard";
import { aiSearchCamps, isAiDisabledError, type AiSearchResponse } from "../../api/ai";
import { getApiErrorMessage } from "../../lib/apiError";
import type { Camp } from "../../types";

const EXAMPLE_QUERIES = [
  "강원도 글램핑 중 평점 높은 곳",
  "10만원 이하 오토캠핑장, 리뷰 많은 순",
  "화장실 3개 이상이고 물놀이장 있는 캠핑장",
];

const MAX_QUERY_LENGTH = 200;

type SearchState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "done"; result: AiSearchResponse }
  | { kind: "error"; message: string };

/** 422(표현 불가 질의/검증 실패)는 서버 메시지를, 그 외에는 상황별 안내를 보여준다. */
const toErrorMessage = (err: unknown): string => {
  if (isAiDisabledError(err)) return "AI 검색이 현재 꺼져 있어요. 일반 검색을 이용해주세요.";
  if (err instanceof AxiosError && err.response?.status === 422) {
    return getApiErrorMessage(err, "이 질문은 검색 조건으로 바꾸기 어려워요. 다르게 물어봐 주세요.");
  }
  if (err instanceof AxiosError && err.response?.status === 429) {
    return "요청이 많아요. 잠시 후 다시 시도해주세요.";
  }
  return getApiErrorMessage(err, "AI 검색 중 문제가 생겼어요. 잠시 후 다시 시도해주세요.");
};

/**
 * 자연어 캠핑장 검색 (POST /v1/camps/ai-search).
 * 호출마다 LLM 비용이 들어 입력 중 자동 검색은 하지 않고, 엔터나 버튼으로 제출할 때만 요청한다.
 */
export default function AiCampSearch() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [state, setState] = useState<SearchState>({ kind: "idle" });

  const loading = state.kind === "loading";
  const onCampClick = (camp: Camp) => navigate(`/campsites/${camp.campId ?? camp.contentId}`);

  const submit = async (text: string) => {
    const trimmed = text.trim();
    if (!trimmed || loading) return;
    setQuery(trimmed);
    setState({ kind: "loading" });
    try {
      setState({ kind: "done", result: await aiSearchCamps(trimmed) });
    } catch (err) {
      setState({ kind: "error", message: toErrorMessage(err) });
    }
  };

  return (
    <div>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit(query);
        }}
        className="flex gap-2 mb-3"
      >
        <div className="flex-1 flex items-center gap-3 bg-card border border-primary/40 rounded-xl px-4 focus-within:border-primary">
          <Sparkles size={16} className="text-primary flex-shrink-0" />
          <input
            type="text"
            value={query}
            maxLength={MAX_QUERY_LENGTH}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="원하는 캠핑장을 문장으로 설명해보세요"
            className="flex-1 bg-transparent py-3 text-sm outline-none placeholder:text-muted-foreground"
            disabled={loading}
          />
          {query && !loading && (
            <button type="button" onClick={() => setQuery("")} aria-label="검색어 지우기">
              <X size={14} className="text-muted-foreground" />
            </button>
          )}
        </div>
        <button
          type="submit"
          disabled={loading || !query.trim()}
          className="px-4 py-2 rounded-xl bg-primary text-primary-foreground text-sm font-bold disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? "찾는 중..." : "AI 검색"}
        </button>
      </form>

      {/* Example queries */}
      <div className="flex flex-wrap gap-2 mb-6">
        {EXAMPLE_QUERIES.map((q) => (
          <button
            key={q}
            type="button"
            onClick={() => submit(q)}
            disabled={loading}
            className="px-3 py-1 rounded-full text-xs bg-muted text-muted-foreground hover:bg-secondary disabled:opacity-50"
          >
            {q}
          </button>
        ))}
      </div>

      {state.kind === "idle" && (
        <div className="py-16 text-center text-muted-foreground">
          <Sparkles size={28} className="mx-auto mb-3 text-primary" />
          <p className="text-sm">지역, 유형, 가격, 시설 조건을 자유롭게 섞어서 물어보세요.</p>
          <p className="text-xs mt-1">AI가 질문을 검색 조건으로 바꿔 캠핑장을 찾아드려요.</p>
        </div>
      )}

      {loading && (
        <div className="flex flex-col items-center gap-3 py-16 text-muted-foreground">
          <div className="loading-spinner" />
          <p className="text-sm">질문을 검색 조건으로 바꾸는 중이에요...</p>
        </div>
      )}

      {state.kind === "error" && (
        <div className="rounded-2xl border border-destructive/30 bg-destructive/5 px-5 py-4 text-sm text-destructive">
          {state.message}
        </div>
      )}

      {state.kind === "done" && (
        <>
          <div className="flex items-start gap-2 rounded-2xl bg-primary/5 border border-primary/20 px-4 py-3 mb-4">
            <Sparkles size={14} className="text-primary mt-0.5 flex-shrink-0" />
            <p className="text-sm">
              <span className="text-muted-foreground">AI 해석: </span>
              {state.result.explanation}
            </p>
          </div>
          <p className="text-sm text-muted-foreground mb-4">
            <span className="text-foreground font-bold">{state.result.camps.length}개</span>의 캠핑장
          </p>
          {state.result.camps.length > 0 ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {state.result.camps.map((camp, i) => (
                <div
                  key={camp.campId ?? camp.contentId}
                  className="camp-grid-item"
                  style={{ animationDelay: `${(i % 6) * 0.05}s` }}
                >
                  <CampCard camp={camp} onClick={() => onCampClick(camp)} />
                </div>
              ))}
            </div>
          ) : (
            <div className="py-16 text-center text-muted-foreground">
              <p className="text-4xl mb-3">🏕️</p>
              <p>조건에 맞는 캠핑장이 없어요. 조건을 조금 넓혀보세요.</p>
            </div>
          )}
        </>
      )}
    </div>
  );
}
