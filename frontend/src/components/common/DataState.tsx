import type { ReactNode } from "react";

export type DataStateKind = "loading" | "empty" | "error" | "forbidden" | "notFound" | "conflict";

type DataStateCopy = {
  label: string;
  title: string;
  description: string;
};

const DATA_STATE_COPY: Record<DataStateKind, DataStateCopy> = {
  loading: {
    label: "Loading",
    title: "데이터를 불러오는 중입니다",
    description: "현재 정보가 준비되면 이 영역에 표시됩니다."
  },
  empty: {
    label: "Empty",
    title: "아직 표시할 내용이 없습니다",
    description: "첫 항목을 만들면 이 화면에서 바로 확인할 수 있습니다."
  },
  error: {
    label: "Error",
    title: "데이터를 불러오지 못했습니다",
    description: "잠시 후 다시 시도해 주세요. 입력한 내용은 유지됩니다."
  },
  forbidden: {
    label: "Permission",
    title: "이 정보에 접근할 권한이 없습니다",
    description: "현재 계정에 허용된 프로젝트와 회의만 확인할 수 있습니다."
  },
  notFound: {
    label: "Not found",
    title: "요청한 내용을 찾을 수 없습니다",
    description: "삭제되었거나 주소가 바뀌었을 수 있습니다. 상위 화면으로 이동해 주세요."
  },
  conflict: {
    label: "Conflict",
    title: "최신 정보와 충돌이 있습니다",
    description: "최신 데이터를 다시 확인한 뒤 원하는 변경을 선택해 주세요."
  }
};

export function DataState({
  state,
  title,
  description,
  actionLabel,
  onAction,
  secondaryActionLabel,
  onSecondaryAction,
  children,
  className = ""
}: {
  state: DataStateKind;
  title?: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
  secondaryActionLabel?: string;
  onSecondaryAction?: () => void;
  children?: ReactNode;
  className?: string;
}) {
  const copy = DATA_STATE_COPY[state];
  const isLoading = state === "loading";
  const isAlert = state === "error" || state === "conflict";

  return (
    <section
      aria-busy={isLoading}
      aria-live={isAlert ? "assertive" : "polite"}
      className={`mm-data-state mm-data-state--${state} ${className}`.trim()}
      role={isAlert ? "alert" : "status"}
    >
      <span aria-hidden="true" className="mm-data-state-mark">
        <span />
      </span>
      <div className="mm-data-state-content">
        <p className="mm-data-state-label">{copy.label}</p>
        <h3>{title ?? copy.title}</h3>
        <p className="mm-data-state-description">{description ?? copy.description}</p>
        {children ? <div className="mm-data-state-detail">{children}</div> : null}
        {actionLabel || secondaryActionLabel ? (
          <div className="mm-data-state-actions">
            {actionLabel && onAction ? (
              <button className="mm-common-button mm-common-button--primary" disabled={isLoading} onClick={onAction} type="button">
                {actionLabel}
              </button>
            ) : null}
            {secondaryActionLabel && onSecondaryAction ? (
              <button className="mm-common-button mm-common-button--secondary" disabled={isLoading} onClick={onSecondaryAction} type="button">
                {secondaryActionLabel}
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
    </section>
  );
}
