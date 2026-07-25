import { Languages } from "lucide-react";

import { AnimatedThemeToggler } from "../components/common/AnimatedThemeToggler";
import { useAppPreferences } from "./preferences";

export function DisplayPreferences({ compact = false }: { compact?: boolean }) {
  const { locale, setLocale, setTheme, theme } = useAppPreferences();
  const korean = locale === "ko";
  const nextThemeLabel = theme === "dark"
    ? (korean ? "라이트 모드로 전환" : "Switch to light mode")
    : (korean ? "다크 모드로 전환" : "Switch to dark mode");

  return (
    <div className={`flex items-center ${compact ? "gap-1" : "gap-2"}`}>
      <AnimatedThemeToggler
        dark={theme === "dark"}
        label={nextThemeLabel}
        onToggle={() => setTheme((current) => current === "dark" ? "light" : "dark")}
      />
      <button
        aria-label={korean ? "Switch language to English" : "언어를 한국어로 전환"}
        className="inline-flex h-8 items-center gap-1 rounded-md border border-border bg-card px-2 text-xs font-semibold text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
        onClick={() => setLocale((current) => current === "ko" ? "en" : "ko")}
        title={korean ? "Switch language to English" : "한국어로 전환"}
        type="button"
      >
        <Languages className="h-3.5 w-3.5" />
        {korean ? "KO" : "EN"}
      </button>
    </div>
  );
}
