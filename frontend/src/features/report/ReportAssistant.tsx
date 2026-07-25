import { useState } from "react";
import { describeChanges, type ReportDiff } from "./diff";

/**
 * 회의록을 고치는 대화창.
 *
 * AI 제안을 그냥 "적용/거절"로 묻지 않는다. 무엇이 바뀌는지 보여준 뒤에 묻는다.
 * 바뀌지 않는 영역도 함께 말한다 — 안 바뀐다는 확인이 있어야 안심하고 적용한다.
 */

export type AssistantMessage = { id: string; role: "user" | "ai"; text: string };

type Props = {
  messages: AssistantMessage[];
  diff: ReportDiff | null;
  pending: boolean;
  /** 확정된 회의록은 고칠 수 없다. 잠근 이유를 함께 보여준다. */
  lockedReason: string | null;
  onAsk: (instruction: string) => void;
  onApply: () => void;
  onDiscard: () => void;
};

const QUICK_ASKS = ["요약을 짧게", "결정만 남기기", "할 일 더 찾기", "근거 약한 곳 찾기"];

function ChangePreview({ diff, onApply, onDiscard }: { diff: ReportDiff; onApply: () => void; onDiscard: () => void }) {
  const changes = describeChanges(diff);

  if (!diff.changed) {
    // 서버가 새 회의록을 줬지만 실제로 달라진 게 없을 수 있다. 그때 "적용"을 권하면
    // 사용자는 바뀐 줄 알고 누른다. 달라진 게 없다는 사실 자체를 말한다.
    return (
      <div className="report-change report-change-empty">
        <p>바뀐 내용이 없습니다. 다르게 요청해 보세요.</p>
        <button onClick={onDiscard} type="button">
          닫기
        </button>
      </div>
    );
  }

  return (
    <div className="report-change">
      <div className="report-change-head">{changes.join(" · ")}이 바뀝니다</div>

      {diff.summary.changed ? (
        <div className="report-change-body">
          <span className="report-was">{diff.summary.before || "(비어 있음)"}</span>
          <span className="report-now">{diff.summary.after || "(비어 있음)"}</span>
        </div>
      ) : null}

      {diff.decisions.changed ? (
        <div className="report-change-body">
          {diff.decisions.removed.map((decision) => (
            <span className="report-was" key={decision.id}>
              {decision.title}
            </span>
          ))}
          {diff.decisions.added.map((decision) => (
            <span className="report-now" key={decision.id}>
              {decision.title}
            </span>
          ))}
        </div>
      ) : null}

      {diff.actionItems.changed ? (
        <div className="report-change-body">
          {diff.actionItems.removed.map((item) => (
            <span className="report-was" key={item.id}>
              {item.title}
            </span>
          ))}
          {diff.actionItems.added.map((item) => (
            <span className="report-now" key={item.id}>
              {item.title}
            </span>
          ))}
        </div>
      ) : null}

      {/* 안 바뀌는 영역을 말해줘야 확인 없이 적용하는 일이 줄어든다. */}
      {!diff.decisions.changed && !diff.actionItems.changed ? (
        <p className="report-change-kept">결정과 할 일은 그대로입니다.</p>
      ) : null}

      <div className="report-change-foot">
        <button className="report-apply" onClick={onApply} type="button">
          적용
        </button>
        <button onClick={onDiscard} type="button">
          되돌리기
        </button>
      </div>
    </div>
  );
}

export function ReportAssistant({ messages, diff, pending, lockedReason, onAsk, onApply, onDiscard }: Props) {
  const [draft, setDraft] = useState("");

  function send(instruction: string) {
    const trimmed = instruction.trim();
    if (!trimmed || pending || lockedReason) {
      return;
    }
    onAsk(trimmed);
    setDraft("");
  }

  if (lockedReason) {
    return (
      <aside className="report-side report-side-off">
        <div className="report-side-head">
          <b>회의록 고치기</b>
          <span>확정 상태에서는 쓸 수 없습니다</span>
        </div>
        <p className="report-side-off-note">{lockedReason}</p>
      </aside>
    );
  }

  return (
    <aside className="report-side">
      <div className="report-side-head">
        <b>회의록 고치기</b>
        <span>이 회의의 전사와 회의록만 씁니다</span>
      </div>

      <div className="report-thread">
        <div className="report-quick">
          {QUICK_ASKS.map((ask) => (
            <button disabled={pending} key={ask} onClick={() => send(ask)} type="button">
              {ask}
            </button>
          ))}
        </div>

        {messages.map((message) => (
          <div className={`report-msg report-msg-${message.role}`} key={message.id}>
            <span className="report-msg-who">{message.role === "user" ? "나" : "AI"}</span>
            <p>{message.text}</p>
          </div>
        ))}

        {diff ? <ChangePreview diff={diff} onApply={onApply} onDiscard={onDiscard} /> : null}
      </div>

      <div className="report-composer">
        <textarea
          aria-label="회의록 수정 요청"
          disabled={pending}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              send(draft);
            }
          }}
          placeholder="고칠 내용을 적어주세요"
          value={draft}
        />
        <button aria-label="보내기" disabled={pending || !draft.trim()} onClick={() => send(draft)} type="button">
          ↑
        </button>
      </div>
    </aside>
  );
}
