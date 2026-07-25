import { CheckSquare, FileText, Mic, Share2 } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { useAppPreferences } from "../../app/preferences";

/*
 * Swap point for the real product video.
 *
 * Drop the file into `frontend/public/` and set this to its public path
 * (e.g. "/meetingmind-flow.webm"). While it is null the animated mock below
 * plays instead, so the section is never an empty placeholder. Both render
 * inside the same window chrome and at the same aspect ratio, so replacing one
 * with the other does not shift the page.
 */
const VIDEO_SRC: string | null = null;
const VIDEO_POSTER: string | null = null;

const STEP_DURATION_MS = 5200;

type Step = {
  id: string;
  icon: typeof Mic;
  label: { ko: string; en: string };
  caption: { ko: string; en: string };
};

const STEPS: Step[] = [
  {
    id: "transcribe",
    icon: Mic,
    label: { ko: "실시간 전사", en: "Live transcript" },
    caption: {
      ko: "회의가 진행되는 동안 화자를 구분해 받아씁니다.",
      en: "Speech is captured and attributed to each speaker as the meeting runs."
    }
  },
  {
    id: "report",
    icon: FileText,
    label: { ko: "AI 회의록", en: "AI report" },
    caption: {
      ko: "요약과 결정사항이 근거가 되는 발언과 함께 정리됩니다.",
      en: "Summaries and decisions are written up with the lines they came from."
    }
  },
  {
    id: "tasks",
    icon: CheckSquare,
    label: { ko: "태스크 추출", en: "Task extraction" },
    caption: {
      ko: "해야 할 일이 담당자와 기한까지 후보로 올라옵니다.",
      en: "Action items surface as candidates, with an owner and a due date."
    }
  },
  {
    id: "knowledge",
    icon: Share2,
    label: { ko: "지식 그래프", en: "Knowledge graph" },
    caption: {
      ko: "결정과 용어가 프로젝트 지식으로 연결돼 쌓입니다.",
      en: "Decisions and terms connect into a knowledge base that keeps growing."
    }
  }
];

function usePrefersReducedMotion() {
  const [reduced, setReduced] = useState(() =>
    typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );

  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const onChange = () => setReduced(query.matches);
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);

  return reduced;
}

// The demo window keeps a 16:9 video ratio, which leaves a short transcript
// stranded on desktop. Lines past `wideOnly` fill that height on large screens
// and are dropped on phones, where the box is much shorter.
const TRANSCRIPT_LINES = [
  { speaker: "김서연", tone: "text-primary", wideOnly: false, ko: "이번 스프린트에 온보딩 개편까지 넣는 건 무리 같아요.", en: "Fitting the onboarding revamp into this sprint feels like too much." },
  { speaker: "박도현", tone: "text-emerald-600", wideOnly: false, ko: "동의합니다. 결제 마이그레이션이 먼저 끝나야 해요.", en: "Agreed. The billing migration has to land first." },
  { speaker: "이지훈", tone: "text-amber-600", wideOnly: true, ko: "결제 쪽은 이번 주 안에 끝날 것 같아요?", en: "Is billing realistically done inside this week?" },
  { speaker: "박도현", tone: "text-emerald-600", wideOnly: true, ko: "게이트웨이 연동만 남아서 수요일이면 됩니다.", en: "Only the gateway hookup is left, so Wednesday." },
  { speaker: "이지훈", tone: "text-amber-600", wideOnly: false, ko: "그럼 온보딩은 다음 스프린트로 미루죠.", en: "Then let's push onboarding to the next sprint." },
  { speaker: "김서연", tone: "text-primary", wideOnly: false, ko: "좋아요. 대신 이번 주에 디자인 리뷰는 끝내둘게요.", en: "Works for me. I'll wrap the design review this week either way." },
  { speaker: "박도현", tone: "text-emerald-600", wideOnly: true, ko: "백로그 순서는 제가 오늘 정리해서 공유할게요.", en: "I'll re-order the backlog today and share it." }
];

function TranscriptFrame({ korean }: { korean: boolean }) {
  return (
    <div className="mm-frame">
      <div className="flex items-center gap-2">
        <span className="mm-live-dot h-2 w-2 rounded-full bg-red-500" />
        <span className="text-xs font-semibold uppercase tracking-wider text-red-500 lg:text-sm">
          {korean ? "회의 중" : "Live"}
        </span>
        <span className="text-xs text-muted-foreground lg:text-sm">
          {korean ? "스프린트 계획 회의 · 12:04" : "Sprint planning · 12:04"}
        </span>
      </div>
      <div className="flex flex-col gap-2.5 lg:gap-4">
        {TRANSCRIPT_LINES.map((line, index) => (
          <div
            className={`mm-rise gap-2.5 lg:gap-4 ${line.wideOnly ? "hidden lg:flex" : "flex"}`}
            key={line.speaker + line.en}
            style={{ animationDelay: `${index * 0.4}s` }}
          >
            <span className={`w-14 shrink-0 text-xs font-semibold lg:w-20 lg:text-base ${line.tone}`}>
              {line.speaker}
            </span>
            <span className="text-xs leading-relaxed text-foreground/80 lg:text-base">
              {korean ? line.ko : line.en}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function ReportFrame({ korean }: { korean: boolean }) {
  const decisions = [
    {
      ko: "온보딩 개편은 다음 스프린트로 이동",
      en: "Onboarding revamp moves to the next sprint",
      sourceKo: "이지훈 12:07",
      sourceEn: "Jihoon 12:07"
    },
    {
      ko: "결제 마이그레이션을 선행 작업으로 확정",
      en: "Billing migration confirmed as the blocking task",
      sourceKo: "박도현 12:06",
      sourceEn: "Dohyun 12:06"
    }
  ];

  return (
    <div className="mm-frame">
      <div className="mm-rise rounded-lg border border-border bg-muted/40 p-3 lg:p-4">
        <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground lg:text-xs">
          {korean ? "요약" : "Summary"}
        </p>
        <p className="mt-1 text-xs leading-relaxed text-foreground/80 lg:text-base">
          {korean
            ? "이번 스프린트 범위를 결제 마이그레이션으로 좁히고, 온보딩 개편은 다음 스프린트로 미뤘습니다."
            : "The team narrowed this sprint to the billing migration and deferred the onboarding revamp."}
        </p>
      </div>
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground lg:text-xs">
        {korean ? "결정사항" : "Decisions"}
      </p>
      <div className="flex flex-col gap-2 lg:gap-3">
        {decisions.map((decision, index) => (
          <div
            // Two decisions overflow the squarer phone box; the second one is
            // illustrative, so it only shows once there is room for it.
            className={`mm-rise rounded-lg border border-border bg-card p-3 lg:p-4 ${index > 0 ? "hidden sm:block" : ""}`}
            key={decision.en}
            style={{ animationDelay: `${0.35 + index * 0.35}s` }}
          >
            <p className="text-xs font-medium text-foreground lg:text-base">{korean ? decision.ko : decision.en}</p>
            <span className="mt-1.5 inline-flex items-center gap-1 rounded-md bg-primary/10 px-1.5 py-0.5 text-[10px] font-medium text-primary lg:text-xs">
              {korean ? "근거" : "Source"} · {korean ? decision.sourceKo : decision.sourceEn}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function TasksFrame({ korean }: { korean: boolean }) {
  const tasks = [
    { wideOnly: false, ko: "결제 마이그레이션 스크립트 작성", en: "Write the billing migration script", owner: "박도현", due: korean ? "3월 8일" : "Mar 8" },
    { wideOnly: false, ko: "온보딩 디자인 리뷰 마무리", en: "Finish the onboarding design review", owner: "김서연", due: korean ? "3월 5일" : "Mar 5" },
    { wideOnly: false, ko: "다음 스프린트 백로그 재정렬", en: "Re-order the next sprint backlog", owner: "이지훈", due: korean ? "3월 6일" : "Mar 6" },
    { wideOnly: true, ko: "결제 게이트웨이 연동 확인", en: "Verify the payment gateway hookup", owner: "박도현", due: korean ? "3월 4일" : "Mar 4" },
    { wideOnly: true, ko: "온보딩 개편 범위 다시 정리", en: "Re-scope the onboarding revamp", owner: "김서연", due: korean ? "3월 11일" : "Mar 11" }
  ];

  return (
    <div className="mm-frame">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground lg:text-xs">
        {korean ? "태스크 후보" : "Task candidates"}
      </p>
      <div className="flex flex-col gap-2 lg:gap-3">
        {tasks.map((task, index) => (
          <div
            className={`mm-rise items-center gap-2.5 rounded-lg border border-border bg-card p-3 lg:gap-4 lg:p-4 ${task.wideOnly ? "hidden lg:flex" : "flex"}`}
            key={task.en}
            style={{ animationDelay: `${index * 0.35}s` }}
          >
            <span
              className="mm-pop flex h-4 w-4 shrink-0 items-center justify-center rounded border border-primary bg-primary lg:h-5 lg:w-5"
              style={{ animationDelay: `${0.25 + index * 0.4}s` }}
            >
              <CheckSquare className="h-3 w-3 text-primary-foreground lg:h-3.5 lg:w-3.5" />
            </span>
            <span className="flex-1 text-xs font-medium text-foreground lg:text-base">{korean ? task.ko : task.en}</span>
            <span className="shrink-0 text-[10px] text-muted-foreground lg:text-sm">{task.owner}</span>
            <span className="shrink-0 rounded-md bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground lg:text-sm">
              {task.due}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

const GRAPH_NODES = [
  { x: 150, y: 44, ko: "결제 마이그레이션", en: "Billing migration", accent: true },
  { x: 52, y: 118, ko: "온보딩 개편", en: "Onboarding revamp", accent: false },
  { x: 246, y: 116, ko: "스프린트 24", en: "Sprint 24", accent: false },
  { x: 148, y: 176, ko: "결제 게이트웨이", en: "Payment gateway", accent: false }
];

const GRAPH_EDGES = [
  { from: 0, to: 1 },
  { from: 0, to: 2 },
  { from: 0, to: 3 },
  { from: 2, to: 1 }
];

function KnowledgeFrame({ korean }: { korean: boolean }) {
  return (
    <div className="mm-frame">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground lg:text-xs">
        {korean ? "프로젝트 지식 그래프" : "Project knowledge graph"}
      </p>
      {/* Everything inside scales with the viewBox, so an unconstrained width
          blows the labels and nodes up on desktop. The wrapper takes the flex
          height; the svg fills it but stops widening past a readable scale. */}
      <div className="flex min-h-0 flex-1 items-center justify-center">
        <svg
          className="h-full w-full max-w-[520px]"
          preserveAspectRatio="xMidYMid meet"
          role="presentation"
          viewBox="0 0 300 210"
        >
          {GRAPH_EDGES.map((edge, index) => {
            const from = GRAPH_NODES[edge.from];
            const to = GRAPH_NODES[edge.to];
            const length = Math.hypot(to.x - from.x, to.y - from.y);
            return (
              <line
                className="mm-draw stroke-border"
                key={`${edge.from}-${edge.to}`}
                stroke="currentColor"
                strokeWidth={1.5}
                style={{ "--mm-draw-length": length, animationDelay: `${0.3 + index * 0.18}s` } as React.CSSProperties}
                x1={from.x}
                x2={to.x}
                y1={from.y}
                y2={to.y}
              />
            );
          })}
          {GRAPH_NODES.map((node, index) => (
            <g className="mm-pop" key={node.en} style={{ animationDelay: `${index * 0.16}s`, transformOrigin: `${node.x}px ${node.y}px` }}>
              <circle
                className={node.accent ? "fill-primary" : "fill-card stroke-border"}
                cx={node.x}
                cy={node.y}
                r={node.accent ? 9 : 7}
                strokeWidth={1.5}
              />
              <text
                className="fill-current text-[9px] font-medium text-foreground"
                textAnchor="middle"
                x={node.x}
                y={node.y + 22}
              >
                {korean ? node.ko : node.en}
              </text>
            </g>
          ))}
        </svg>
      </div>
    </div>
  );
}

const FRAMES = [TranscriptFrame, ReportFrame, TasksFrame, KnowledgeFrame];

export function ProductFlowDemo() {
  const { locale } = useAppPreferences();
  const korean = locale === "ko";
  const reducedMotion = usePrefersReducedMotion();
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [visible, setVisible] = useState(false);

  // Only run the loop once the demo is actually on screen — otherwise the
  // sequence has already cycled past by the time a visitor scrolls down.
  useEffect(() => {
    const element = containerRef.current;
    if (!element) {
      return;
    }
    const observer = new IntersectionObserver(
      ([entry]) => setVisible(entry.isIntersecting),
      { threshold: 0.35 }
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (reducedMotion || paused || !visible || VIDEO_SRC) {
      return;
    }
    const timer = window.setTimeout(
      () => setActive((current) => (current + 1) % STEPS.length),
      STEP_DURATION_MS
    );
    return () => window.clearTimeout(timer);
  }, [active, paused, reducedMotion, visible]);

  const Frame = FRAMES[active];
  const step = STEPS[active];

  return (
    <div className="mx-auto w-full max-w-5xl" ref={containerRef}>
      {/* Window chrome — shared by the video and the animated mock. */}
      <div className="overflow-hidden rounded-2xl border border-border bg-card shadow-2xl shadow-foreground/5">
        <div className="flex items-center gap-2 border-b border-border bg-muted/50 px-4 py-2.5">
          <span className="h-2.5 w-2.5 rounded-full bg-red-400" />
          <span className="h-2.5 w-2.5 rounded-full bg-amber-400" />
          <span className="h-2.5 w-2.5 rounded-full bg-emerald-400" />
          <span className="ml-3 text-xs font-medium text-muted-foreground">MeetingMind</span>
        </div>
        {/* 16:9 clips the taller frames on phones, so give narrow screens a
            squarer box and only settle into video aspect from `sm` up. */}
        <div className="aspect-[4/3] w-full bg-background sm:aspect-[16/9]">
          {VIDEO_SRC ? (
            <video
              autoPlay
              className="h-full w-full object-cover"
              loop
              muted
              playsInline
              poster={VIDEO_POSTER ?? undefined}
              preload="metadata"
              src={VIDEO_SRC}
            />
          ) : (
            <div className="h-full" key={step.id}>
              <Frame korean={korean} />
            </div>
          )}
        </div>
      </div>

      {/* Step tabs. Hidden once a real video takes over — the video carries its
          own narration of the same four steps. */}
      {VIDEO_SRC ? null : (
        <div
          className="mt-6 grid gap-2 sm:grid-cols-4"
          onBlur={() => setPaused(false)}
          onFocus={() => setPaused(true)}
          onMouseEnter={() => setPaused(true)}
          onMouseLeave={() => setPaused(false)}
        >
          {STEPS.map((item, index) => {
            const Icon = item.icon;
            const isActive = index === active;
            return (
              <button
                aria-current={isActive}
                className={`rounded-lg border p-3 text-left transition-colors ${
                  isActive
                    ? "border-primary/40 bg-primary/5"
                    : "border-transparent hover:bg-muted/60"
                }`}
                key={item.id}
                onClick={() => setActive(index)}
                type="button"
              >
                <span className="flex items-center gap-1.5">
                  <Icon className={`h-3.5 w-3.5 ${isActive ? "text-primary" : "text-muted-foreground"}`} />
                  <span className={`text-xs font-semibold ${isActive ? "text-foreground" : "text-muted-foreground"}`}>
                    {korean ? item.label.ko : item.label.en}
                  </span>
                </span>
                <span className="mt-2 block h-0.5 w-full overflow-hidden rounded-full bg-border">
                  {isActive ? (
                    <span
                      className="mm-progress block h-full w-full rounded-full bg-primary"
                      key={`${item.id}-${paused}`}
                      style={{
                        animationDuration: `${STEP_DURATION_MS}ms`,
                        animationPlayState: paused ? "paused" : "running"
                      }}
                    />
                  ) : null}
                </span>
                <span className="mt-2 block text-[11px] leading-relaxed text-muted-foreground">
                  {korean ? item.caption.ko : item.caption.en}
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
