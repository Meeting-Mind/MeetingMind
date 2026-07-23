import { useEffect, useRef, type ReactNode } from "react";

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = "확인",
  cancelLabel = "취소",
  tone = "danger",
  busy = false,
  onConfirm,
  onCancel,
  children
}: {
  open: boolean;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: "danger" | "default";
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  children?: ReactNode;
}) {
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }

    const previousActiveElement = document.activeElement as HTMLElement | null;
    cancelButtonRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) {
        event.preventDefault();
        onCancel();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      previousActiveElement?.focus();
    };
  }, [busy, onCancel, open]);

  if (!open) {
    return null;
  }

  return (
    <div className="mm-confirm-dialog-backdrop" role="presentation">
      <section
        aria-describedby="mm-confirm-dialog-description"
        aria-labelledby="mm-confirm-dialog-title"
        aria-modal="true"
        className="mm-confirm-dialog"
        data-tone={tone}
        role="dialog"
      >
        <p className="mm-confirm-dialog-label">Confirm action</p>
        <h2 id="mm-confirm-dialog-title">{title}</h2>
        <p id="mm-confirm-dialog-description">{description}</p>
        {children ? <div className="mm-confirm-dialog-detail">{children}</div> : null}
        <div className="mm-confirm-dialog-actions">
          <button
            ref={cancelButtonRef}
            className="mm-common-button mm-common-button--secondary"
            disabled={busy}
            onClick={onCancel}
            type="button"
          >
            {cancelLabel}
          </button>
          <button
            className={`mm-common-button ${tone === "danger" ? "mm-common-button--danger" : "mm-common-button--primary"}`}
            disabled={busy}
            onClick={onConfirm}
            type="button"
          >
            {busy ? "처리 중..." : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  );
}
