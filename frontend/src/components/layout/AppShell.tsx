import type { ReactNode } from "react";

export function AppShell({
  sidebar,
  children,
  contentClassName = ""
}: {
  sidebar: ReactNode;
  children: ReactNode;
  contentClassName?: string;
}) {
  return (
    <div className="mm-app-shell">
      {sidebar}
      <main className={`mm-app-shell-content ${contentClassName}`.trim()}>{children}</main>
    </div>
  );
}
