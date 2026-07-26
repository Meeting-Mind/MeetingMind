import {
  ArrowRight,
  BookOpen,
  Calendar,
  CheckSquare,
  FileText,
  Mic,
  Search,
  Share2,
  Sparkles,
  Users
} from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { DisplayPreferences } from "../../app/DisplayPreferences";
import { useAppPreferences } from "../../app/preferences";
import { ProductFlowDemo } from "./ProductFlowDemo";
import "./landing.css";

const NAV_LINKS = [
  { href: "#flow", ko: "작동 방식", en: "How it works" },
  { href: "#features", ko: "기능", en: "Features" },
  { href: "#use-cases", ko: "활용 사례", en: "Use cases" }
];

const CONNECT_POINTS = [
  {
    icon: Mic,
    titleKo: "회의 중에",
    titleEn: "During the meeting",
    bodyKo: "화자를 구분한 실시간 전사가 회의가 끝나기 전에 이미 쌓입니다.",
    bodyEn: "A speaker-attributed transcript builds up before the meeting is even over."
  },
  {
    icon: Sparkles,
    titleKo: "회의 직후에",
    titleEn: "Right after",
    bodyKo: "요약, 결정사항, 태스크 후보가 근거 발언과 함께 정리됩니다.",
    bodyEn: "Summary, decisions, and task candidates are written up with their sources."
  },
  {
    icon: Share2,
    titleKo: "다음 회의까지",
    titleEn: "Until the next one",
    bodyKo: "결정과 용어가 프로젝트 지식 그래프로 연결돼 남습니다.",
    bodyEn: "Decisions and terms connect into a project knowledge graph that persists."
  },
  {
    icon: Search,
    titleKo: "필요할 때마다",
    titleEn: "Whenever you need it",
    bodyKo: "프로젝트 AI에게 물어보면 근거가 되는 회의를 짚어 답합니다.",
    bodyEn: "Ask the project AI and it answers by pointing at the meetings behind it."
  }
];

const FEATURES = [
  {
    icon: FileText,
    titleKo: "근거를 남기는 AI 회의록",
    titleEn: "AI reports that show their work",
    bodyKo: "요약과 결정사항마다 어떤 발언에서 나왔는지 표시됩니다. 회의에 없었던 사람도 그 자리에서 검증할 수 있습니다.",
    bodyEn: "Every summary line and decision points back to the utterance it came from, so people who missed the meeting can verify it on the spot."
  },
  {
    icon: CheckSquare,
    titleKo: "말로 끝나지 않는 액션 아이템",
    titleEn: "Action items that don't evaporate",
    bodyKo: "해야 할 일이 담당자와 기한까지 붙은 태스크 후보로 올라옵니다. 확인만 하면 프로젝트 보드로 넘어갑니다.",
    bodyEn: "Follow-ups surface as task candidates with an owner and a due date. Confirm them and they move straight onto the project board."
  },
  {
    icon: Share2,
    titleKo: "저절로 자라는 프로젝트 지식",
    titleEn: "Project knowledge that grows itself",
    bodyKo: "회의마다 나온 결정과 용어가 그래프로 연결됩니다. 누가 정리하지 않아도 프로젝트의 맥락이 남습니다.",
    bodyEn: "Decisions and terms from each meeting link into a graph, so context survives without anyone maintaining a wiki."
  }
];

const USE_CASES = [
  {
    icon: Users,
    titleKo: "제품 · 개발",
    titleEn: "Product & engineering",
    bodyKo: "스프린트 계획과 회고에서 나온 결정을 백로그까지 끊기지 않게 잇습니다.",
    bodyEn: "Carry sprint planning and retro decisions through to the backlog without a gap."
  },
  {
    icon: BookOpen,
    titleKo: "리서치 · 기획",
    titleEn: "Research & planning",
    bodyKo: "인터뷰와 기획 회의에서 반복되는 용어와 인사이트를 지식으로 모읍니다.",
    bodyEn: "Collect the terms and insights that keep recurring across interviews and planning calls."
  },
  {
    icon: Calendar,
    titleKo: "고객 미팅",
    titleEn: "Customer meetings",
    bodyKo: "약속한 내용과 후속 조치를 놓치지 않고 다음 미팅 전에 다시 꺼내 봅니다.",
    bodyEn: "Keep every commitment and follow-up recoverable before the next call."
  },
  {
    icon: Sparkles,
    titleKo: "조직 운영",
    titleEn: "Operations",
    bodyKo: "주간 회의가 반복되어도 무엇이 왜 결정됐는지 추적할 수 있습니다.",
    bodyEn: "Weekly meetings pile up, but what was decided and why stays traceable."
  }
];

export const LandingPage = () => {
  const navigate = useNavigate();
  const { locale } = useAppPreferences();
  const korean = locale === "ko";
  const [email, setEmail] = useState("");

  // The landing page never authenticates on its own — it hands the address to
  // /login, which owns the whole signup flow.
  const handleSignupStart = (event: React.FormEvent) => {
    event.preventDefault();
    const trimmed = email.trim();
    const params = new URLSearchParams({ mode: "signup" });
    if (trimmed) {
      params.set("email", trimmed);
    }
    navigate(`/login?${params.toString()}`);
  };

  return (
    <div className="mm-landing min-h-screen bg-background text-foreground">
      {/* ── Nav ────────────────────────────────────────────── */}
      <header className="sticky top-0 z-30 border-b border-border/60 bg-background/85 backdrop-blur">
        <nav className="mx-auto flex max-w-6xl items-center gap-6 px-6 py-3.5">
          <Link className="flex items-center gap-2" to="/">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-foreground">
              <Sparkles className="h-3.5 w-3.5 text-background" />
            </span>
            <span className="text-base font-bold">MeetingMind</span>
          </Link>
          <div className="hidden items-center gap-5 md:flex">
            {NAV_LINKS.map((link) => (
              <a
                className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
                href={link.href}
                key={link.href}
              >
                {korean ? link.ko : link.en}
              </a>
            ))}
          </div>
          <div className="ml-auto flex items-center gap-3">
            <DisplayPreferences compact />
            <Link
              className="hidden text-sm font-medium text-muted-foreground transition-colors hover:text-foreground sm:block"
              to="/login"
            >
              {korean ? "로그인" : "Sign in"}
            </Link>
            <Link
              className="rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
              to="/login?mode=signup"
            >
              {korean ? "무료로 시작" : "Get it free"}
            </Link>
          </div>
        </nav>
      </header>

      {/* ── Hero ───────────────────────────────────────────── */}
      <section className="mx-auto grid max-w-6xl gap-12 px-6 py-20 lg:grid-cols-[1.15fr_1fr] lg:items-center lg:py-28">
        <div>
          <h1 className="text-5xl font-bold leading-[1.08] tracking-tight sm:text-6xl">
            {korean ? (
              <>더 적은 받아쓰기.<br />더 적은 되묻기.<br />더 많은 실행.</>
            ) : (
              <>Less note-taking.<br />Less asking again.<br />More done.</>
            )}
          </h1>
          <p className="mt-6 max-w-lg text-lg leading-relaxed text-muted-foreground">
            {korean
              ? "MeetingMind는 회의를 전사, 회의록, 태스크, 프로젝트 지식으로 이어 붙입니다. 회의가 끝나는 순간 다음 할 일이 이미 정리돼 있습니다."
              : "MeetingMind connects meetings to transcripts, reports, tasks, and project knowledge. By the time a meeting ends, the next step is already written down."}
          </p>
        </div>

        <div className="w-full max-w-md rounded-2xl border border-border bg-card p-6 shadow-xl shadow-foreground/5 lg:justify-self-end">
          <form className="space-y-3" onSubmit={handleSignupStart}>
            <label className="block text-xs font-semibold uppercase tracking-wider text-muted-foreground" htmlFor="landing-email">
              {korean ? "업무용 이메일" : "Work email"}
            </label>
            <input
              autoComplete="email"
              className="w-full rounded-lg border border-border bg-background px-3 py-2.5 text-sm transition-all focus:outline-none focus:ring-1 focus:ring-primary/50"
              id="landing-email"
              name="email"
              onChange={(event) => setEmail(event.target.value)}
              placeholder="alex@company.com"
              spellCheck={false}
              type="email"
              value={email}
            />
            <p className="text-xs text-muted-foreground">
              {korean
                ? "같은 도메인의 동료를 찾아 워크스페이스를 추천해 드립니다."
                : "We'll use it to find teammates on your domain and suggest a workspace."}
            </p>
            <button
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-primary py-2.5 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
              type="submit"
            >
              {korean ? "무료로 시작하기" : "Get started free"}
              <ArrowRight className="h-4 w-4" />
            </button>
          </form>

          <div className="relative my-5">
            <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-border" /></div>
            <div className="relative flex justify-center">
              <span className="bg-card px-3 text-xs text-muted-foreground">
                {korean ? "또는" : "or"}
              </span>
            </div>
          </div>

          <Link
            className="flex w-full items-center justify-center rounded-lg border border-border py-2.5 text-sm font-medium transition-colors hover:bg-muted"
            to="/login"
          >
            {korean ? "이미 계정이 있어요" : "I already have an account"}
          </Link>
        </div>
      </section>

      {/* ── Product flow ───────────────────────────────────── */}
      <section className="border-y border-border/60 bg-muted/30 px-6 py-20" id="flow">
        <div className="mx-auto mb-12 max-w-2xl text-center">
          <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
            {korean ? "회의 한 번이 지나가는 경로" : "What one meeting turns into"}
          </h2>
          <p className="mt-3 text-muted-foreground">
            {korean
              ? "말이 오간 순간부터 프로젝트 지식이 되기까지, 중간에 사람이 옮겨 적는 단계가 없습니다."
              : "From the moment people start talking to the point it becomes project knowledge — with no step where someone retypes it."}
          </p>
        </div>
        <ProductFlowDemo />
      </section>

      {/* ── Connect points ─────────────────────────────────── */}
      <section className="mx-auto max-w-6xl px-6 py-20">
        <h2 className="max-w-2xl text-3xl font-bold tracking-tight sm:text-4xl">
          {korean ? "회의가 끝난 뒤에도, 일이 이어지도록" : "Meetings end. The work shouldn't stop with them."}
        </h2>
        <div className="mt-12 grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {CONNECT_POINTS.map((point) => {
            const Icon = point.icon;
            return (
              <div key={point.titleEn}>
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10">
                  <Icon className="h-4 w-4 text-primary" />
                </span>
                <h3 className="mt-4 text-base font-semibold">{korean ? point.titleKo : point.titleEn}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                  {korean ? point.bodyKo : point.bodyEn}
                </p>
              </div>
            );
          })}
        </div>
      </section>

      {/* ── Features ───────────────────────────────────────── */}
      <section className="border-t border-border/60 bg-muted/30 px-6 py-20" id="features">
        <div className="mx-auto max-w-6xl">
          <h2 className="max-w-2xl text-3xl font-bold tracking-tight sm:text-4xl">
            {korean ? "받아쓰기 도구가 아니라, 회의를 끝까지 책임지는 도구" : "Not a transcription tool. A tool that sees the meeting through."}
          </h2>
          <div className="mt-12 grid gap-6 lg:grid-cols-3">
            {FEATURES.map((feature) => {
              const Icon = feature.icon;
              return (
                <div className="rounded-2xl border border-border bg-card p-6" key={feature.titleEn}>
                  <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-foreground">
                    <Icon className="h-5 w-5 text-background" />
                  </span>
                  <h3 className="mt-5 text-lg font-semibold">{korean ? feature.titleKo : feature.titleEn}</h3>
                  <p className="mt-2.5 text-sm leading-relaxed text-muted-foreground">
                    {korean ? feature.bodyKo : feature.bodyEn}
                  </p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ── Use cases ──────────────────────────────────────── */}
      <section className="mx-auto max-w-6xl px-6 py-20" id="use-cases">
        <h2 className="max-w-2xl text-3xl font-bold tracking-tight sm:text-4xl">
          {korean ? "회의가 많은 팀일수록 차이가 큽니다" : "The more meetings a team runs, the more this shows"}
        </h2>
        <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {USE_CASES.map((useCase) => {
            const Icon = useCase.icon;
            return (
              <div className="rounded-xl border border-border p-5 transition-colors hover:bg-muted/40" key={useCase.titleEn}>
                <Icon className="h-5 w-5 text-primary" />
                <h3 className="mt-4 text-sm font-semibold">{korean ? useCase.titleKo : useCase.titleEn}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                  {korean ? useCase.bodyKo : useCase.bodyEn}
                </p>
              </div>
            );
          })}
        </div>
      </section>

      {/* ── Closing CTA ────────────────────────────────────── */}
      <section className="border-t border-border/60 px-6 py-24">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
            {korean ? "다음 회의부터 바로" : "Start with your next meeting"}
          </h2>
          <p className="mt-4 text-lg text-muted-foreground">
            {korean
              ? "설치할 것도, 옮겨 적을 것도 없습니다. 워크스페이스를 만들고 회의를 시작하면 됩니다."
              : "Nothing to install, nothing to retype. Create a workspace and start the meeting."}
          </p>
          <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
            <Link
              className="inline-flex items-center gap-2 rounded-md bg-primary px-6 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
              to="/login?mode=signup"
            >
              {korean ? "무료로 시작하기" : "Get started free"}
              <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              className="inline-flex items-center gap-2 rounded-md border border-border px-6 py-3 text-sm font-semibold transition-colors hover:bg-muted"
              to="/spaces"
            >
              {korean ? "워크스페이스로 이동" : "Go to workspaces"}
            </Link>
          </div>
        </div>
      </section>

      <footer className="border-t border-border/60 px-6 py-8">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4">
          <span className="flex items-center gap-2 text-sm font-semibold">
            <span className="flex h-5 w-5 items-center justify-center rounded bg-foreground">
              <Sparkles className="h-2.5 w-2.5 text-background" />
            </span>
            MeetingMind
          </span>
          <span className="text-xs text-muted-foreground">© 2026 MeetingMind. All rights reserved.</span>
        </div>
      </footer>
    </div>
  );
};
