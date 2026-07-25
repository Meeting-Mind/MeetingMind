import type { AiSource, ReportCandidateActionItem, ReportCandidateDecision } from "../../types";
import { buildCitationIndex } from "./citations";

/**
 * 회의록 본문. 요약, 결정, 할 일, 근거를 한 편의 문서로 보여준다.
 *
 * 근거는 각주 방식이다. 각 결정과 할 일의 `sourceIds`가 문서 끝 근거 목록의 번호로
 * 연결된다. 전사 전체를 나열하지 않고 실제로 인용된 것만 번호를 받는다.
 */

type Props = {
  summary: string;
  decisions: ReportCandidateDecision[];
  actionItems: ReportCandidateActionItem[];
  sources: AiSource[];
  /** 이번 AI 수정으로 요약이 바뀌는 경우 표시한다. */
  summaryChanged?: boolean;
  onSendToTasks?: (item: ReportCandidateActionItem) => void;
};

function Citations({ sourceIds, index }: { sourceIds: string[]; index: Map<string, number> }) {
  const numbers = sourceIds.map((sourceId) => index.get(sourceId)).filter((value): value is number => value !== undefined);
  if (!numbers.length) {
    return null;
  }
  return (
    <sup className="report-cite" aria-label={`근거 ${numbers.join(", ")}번`}>
      {numbers.join(",")}
    </sup>
  );
}

export function ReportDocument({
  summary,
  decisions,
  actionItems,
  sources,
  summaryChanged = false,
  onSendToTasks,
}: Props) {
  const citations = buildCitationIndex(decisions, actionItems);
  const cited = sources
    .filter((source) => citations.has(source.sourceId))
    .sort((left, right) => (citations.get(left.sourceId) ?? 0) - (citations.get(right.sourceId) ?? 0));

  return (
    <article className="report-doc">
      <section>
        <p className={summaryChanged ? "report-lede report-lede-changed" : "report-lede"}>
          {summary || "요약이 만들어지지 않았습니다."}
        </p>
      </section>

      <section>
        <h3 className="report-eyebrow">
          결정 <em>{decisions.length ? `${decisions.length}건` : "없음"}</em>
        </h3>
        {decisions.length ? (
          <ol className="report-calls">
            {decisions.map((decision, position) => (
              <li key={decision.id}>
                <span className="report-idx">{String(position + 1).padStart(2, "0")}</span>
                <div>
                  <b>
                    {decision.title}
                    <Citations sourceIds={decision.sourceIds} index={citations} />
                  </b>
                  {decision.rationale ? <small>{decision.rationale}</small> : null}
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <p className="report-none">전사에서 확정된 결정을 찾지 못했습니다.</p>
        )}
      </section>

      <section>
        <h3 className="report-eyebrow">
          다음 할 일 <em>{actionItems.length ? `${actionItems.length}건` : "없음"}</em>
        </h3>
        {actionItems.length ? (
          <div className="report-todo">
            {actionItems.map((item) => (
              <div className="report-todo-row" key={item.id}>
                <b>
                  {item.title}
                  <Citations sourceIds={item.sourceIds} index={citations} />
                </b>
                <span className="report-owner">
                  {item.assignee ?? "담당 미정"}
                  {item.dueDate ? ` · ${item.dueDate}` : ""}
                </span>
                {onSendToTasks ? (
                  <button className="report-send" onClick={() => onSendToTasks(item)} type="button">
                    할 일로 보내기
                  </button>
                ) : null}
              </div>
            ))}
          </div>
        ) : (
          <p className="report-none">전사에서 할 일을 찾지 못했습니다.</p>
        )}
      </section>

      <section>
        <h3 className="report-eyebrow">
          근거 <em>이 회의의 전사에서만</em>
        </h3>
        {cited.length ? (
          <div className="report-refs">
            {cited.map((source) => (
              <div className="report-ref" key={source.sourceId}>
                <span className="report-ref-n">{citations.get(source.sourceId)}</span>
                <div>
                  <span className="report-ref-who">{source.speaker ?? source.title}</span>
                  {source.time ? <span className="report-ref-at">{source.time}</span> : null}
                  <span className="report-ref-said">{source.text}</span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          // 근거 없이 만들어진 회의록은 확정 대상이 아니다. 조용히 비워두지 않는다.
          <p className="report-none">인용된 전사 근거가 없습니다. 확정하기 전에 확인하세요.</p>
        )}
      </section>
    </article>
  );
}
