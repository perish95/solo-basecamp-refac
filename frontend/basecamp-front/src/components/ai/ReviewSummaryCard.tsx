import { useEffect, useState } from "react";
import { Sparkles, ThumbsDown, ThumbsUp } from "lucide-react";
import { Skeleton } from "../common/skeleton";
import { getReviewSummary, isAiDisabledError, type ReviewSummaryResponse } from "../../api/ai";

interface Props {
  campId: number;
  /** 리뷰가 추가/삭제되면 요약을 다시 받아오도록 바뀌는 값(리뷰 수 등) */
  refreshKey: number;
}

type SummaryState =
  | { kind: "loading" }
  | { kind: "ready"; data: ReviewSummaryResponse }
  | { kind: "hidden" }
  | { kind: "error" };

/**
 * 캠핑장 리뷰 AI 요약 카드 (GET /v1/camps/{campId}/review-summary).
 * 서버에서 AI 기능이 꺼져 있으면(503) 카드 자체를 숨긴다.
 */
export default function ReviewSummaryCard({ campId, refreshKey }: Props) {
  const [state, setState] = useState<SummaryState>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;
    setState({ kind: "loading" });
    getReviewSummary(campId)
      .then((data) => {
        if (!cancelled) setState({ kind: "ready", data });
      })
      .catch((err) => {
        if (!cancelled) setState({ kind: isAiDisabledError(err) ? "hidden" : "error" });
      });
    return () => {
      cancelled = true;
    };
  }, [campId, refreshKey]);

  if (state.kind === "hidden") return null;

  return (
    <div className="rounded-2xl border border-primary/20 bg-primary/5 p-4 mb-5">
      <div className="flex items-center gap-1.5 mb-3">
        <Sparkles size={14} className="text-primary" />
        <span className="text-sm font-bold">AI 리뷰 요약</span>
      </div>

      {state.kind === "loading" && (
        <div className="space-y-2">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-4/5" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      )}

      {state.kind === "error" && (
        <p className="text-sm text-muted-foreground">요약을 불러오지 못했어요. 잠시 후 다시 확인해주세요.</p>
      )}

      {state.kind === "ready" && state.data.status === "INSUFFICIENT_REVIEWS" && (
        <p className="text-sm text-muted-foreground">후기가 3개 이상 모이면 AI가 요약해드려요.</p>
      )}

      {state.kind === "ready" && state.data.status === "READY" && (
        <>
          <p className="text-sm leading-relaxed mb-3">{state.data.summary}</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <SummaryList title="좋았던 점" items={state.data.pros} positive />
            <SummaryList title="아쉬운 점" items={state.data.cons} />
          </div>
          <p className="text-[11px] text-muted-foreground mt-3">
            최근 후기 {state.data.basedOnReviewCount}개 기준
            {state.data.generatedAt && ` · ${state.data.generatedAt.slice(0, 10)} 생성`} · AI가 작성한 요약이라
            실제와 다를 수 있어요
          </p>
        </>
      )}
    </div>
  );
}

function SummaryList({ title, items, positive }: { title: string; items: string[]; positive?: boolean }) {
  if (items.length === 0) return null;
  const Icon = positive ? ThumbsUp : ThumbsDown;
  return (
    <div className="rounded-xl bg-card px-3 py-2.5">
      <p className={`flex items-center gap-1 text-xs font-bold mb-1.5 ${positive ? "text-primary" : "text-accent"}`}>
        <Icon size={12} /> {title}
      </p>
      <ul className="space-y-1">
        {items.map((item) => (
          <li key={item} className="text-xs text-muted-foreground">
            · {item}
          </li>
        ))}
      </ul>
    </div>
  );
}
