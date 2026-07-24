import { Moon, Sun } from "lucide-react";
import { useEffect, useRef, useState } from "react";

type AnimatedThemeTogglerProps = {
  dark: boolean;
  label: string;
  onToggle: () => void;
};

/**
 * Local Magic UI-compatible theme toggle. It stays controlled by the app's
 * preference store instead of introducing a second theme provider.
 */
export function AnimatedThemeToggler({ dark, label, onToggle }: AnimatedThemeTogglerProps) {
  const [ripple, setRipple] = useState<{ x: number; y: number; color: string } | null>(null);
  const [rippleExpanded, setRippleExpanded] = useState(false);
  const timers = useRef<number[]>([]);

  useEffect(() => () => timers.current.forEach((timer) => window.clearTimeout(timer)), []);

  function schedule(callback: () => void, delay: number) {
    const timer = window.setTimeout(callback, delay);
    timers.current.push(timer);
  }

  function handleToggle(event: React.MouseEvent<HTMLButtonElement>) {
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reducedMotion) {
      onToggle();
      return;
    }

    const rect = event.currentTarget.getBoundingClientRect();
    document.documentElement.classList.add("theme-transitioning");
    setRipple({
      x: rect.left + rect.width / 2,
      y: rect.top + rect.height / 2,
      color: dark ? "rgba(246, 248, 252, 0.22)" : "rgba(15, 23, 42, 0.18)"
    });
    setRippleExpanded(false);
    window.requestAnimationFrame(() => setRippleExpanded(true));
    schedule(onToggle, 90);
    schedule(() => {
      setRipple(null);
      setRippleExpanded(false);
      document.documentElement.classList.remove("theme-transitioning");
    }, 1450);
  }

  return (
    <>
      {ripple ? (
        <span
          aria-hidden="true"
          className="pointer-events-none fixed z-[100] h-[160vmax] w-[160vmax] rounded-full"
          style={{
            left: ripple.x,
            top: ripple.y,
            background: `radial-gradient(circle, ${ripple.color} 0%, ${ripple.color} 16%, transparent 64%)`,
            opacity: rippleExpanded ? 0 : 1,
            transform: `translate(-50%, -50%) scale(${rippleExpanded ? 1 : 0})`,
            transition: "transform 1320ms cubic-bezier(0.22, 1, 0.36, 1), opacity 1320ms ease-out"
          }}
        />
      ) : null}
      <button
        aria-label={label}
        aria-pressed={dark}
        className="group relative flex h-8 w-8 items-center justify-center overflow-hidden rounded-md border border-border bg-card text-muted-foreground transition-colors duration-300 hover:bg-muted hover:text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
        onClick={handleToggle}
        title={label}
        type="button"
      >
        <Sun
          aria-hidden="true"
          className={`absolute h-4 w-4 transition-all duration-500 ease-out ${dark ? "rotate-90 scale-0 opacity-0" : "rotate-0 scale-100 opacity-100"}`}
        />
        <Moon
          aria-hidden="true"
          className={`absolute h-4 w-4 transition-all duration-500 ease-out ${dark ? "rotate-0 scale-100 opacity-100" : "-rotate-90 scale-0 opacity-0"}`}
        />
      </button>
    </>
  );
}
