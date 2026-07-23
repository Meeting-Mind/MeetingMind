import type { ReactNode } from "react";

export function PageHeader({
  breadcrumb,
  eyebrow,
  title,
  description,
  meta,
  actions
}: {
  breadcrumb?: ReactNode;
  eyebrow?: string;
  title: ReactNode;
  description?: ReactNode;
  meta?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header className="mm-page-header">
      {breadcrumb ? (
        <nav aria-label="현재 위치" className="mm-page-header-breadcrumb">
          {breadcrumb}
        </nav>
      ) : null}
      <div className="mm-page-header-main">
        <div className="mm-page-header-copy">
          {eyebrow ? <p className="mm-page-header-eyebrow">{eyebrow}</p> : null}
          <div className="mm-page-header-title-row">
            <h1>{title}</h1>
            {meta ? <div className="mm-page-header-meta">{meta}</div> : null}
          </div>
          {description ? <p className="mm-page-header-description">{description}</p> : null}
        </div>
        {actions ? <div className="mm-page-header-actions">{actions}</div> : null}
      </div>
    </header>
  );
}
