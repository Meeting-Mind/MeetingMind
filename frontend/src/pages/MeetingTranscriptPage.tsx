import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import { DataState } from "../components/common/DataState";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { MeetingLayout } from "../components/layout/MeetingLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { TeamMember, WorkspaceDataSource } from "../app/workspaceTypes";
import { useMeetingContext } from "../hooks/useMeetingContext";
import { useMeetingDialogue } from "../hooks/useMeetingDialogue";
import type { WorkspaceData } from "../types";

function formatTranscriptTime(startMs: number) {
  const totalSeconds = Math.max(0, Math.floor(startMs / 1_000));
  const hours = Math.floor(totalSeconds / 3_600);
  const minutes = Math.floor((totalSeconds % 3_600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds].map((value) => String(value).padStart(2, "0")).join(":");
}

function transcriptStatusLabel(status: "PROCESSING" | "COMPLETED" | "FAILED") {
  if (status === "PROCESSING") {
    return "전사 처리 중";
  }
  if (status === "FAILED") {
    return "전사 실패";
  }
  return "전사 완료";
}

export function MeetingTranscriptPage({
  currentUserEmail,
  projectMembers,
  session,
  spaces,
  workspaceDataSource,
  onCreateProject
}: {
  currentUserEmail: string;
  projectMembers: Record<string, TeamMember[]>;
  session: AuthSession | null;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
}) {
  const { spaceId = "", meetingId = "" } = useParams<{ spaceId: string; meetingId: string }>();
  const selectedSpace = spaces.find((space) => space.id === spaceId);
  const meetingContext = useMeetingContext(session, meetingId, spaceId);
  const dialogue = useMeetingDialogue(session, meetingId);
  const [query, setQuery] = useState("");
  const normalizedQuery = query.trim().toLowerCase();
  const visibleEntries = useMemo(
    () => {
      const sourceEntries = [
        ...((dialogue.data?.rows ?? []).map((row) => ({
          key: row.segmentId,
          speakerName: row.speakerName || row.speakerLabel,
          text: row.text,
          startMs: row.startMs,
          isPartial: false
        }))),
        ...((dialogue.data?.partials ?? []).map((partial, index) => ({
          key: `partial-${partial.speakerLabel}-${index}`,
          speakerName: partial.speakerName || partial.speakerLabel,
          text: partial.text,
          startMs: Number.MAX_SAFE_INTEGER,
          isPartial: true
        })))
      ];

      return sourceEntries.filter(
        (entry) => !normalizedQuery || [entry.speakerName, entry.text].join(" ").toLowerCase().includes(normalizedQuery)
      );
    },
    [dialogue.data, normalizedQuery]
  );

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="meeting-transcript-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState
          actionLabel="프로젝트 목록으로"
          onAction={() => { window.location.href = "/spaces"; }}
          state="notFound"
          title="프로젝트를 찾을 수 없습니다"
          description="전사 기록이 속한 프로젝트가 없거나 접근 권한이 없습니다."
        />
      </AppShell>
    );
  }

  const member = (projectMembers[selectedSpace.name] ?? []).find((item) => item.email === currentUserEmail);

  if (meetingContext.status === "loading" || !meetingContext.detail) {
    return (
      <MeetingLayout
        meeting={null}
        meetingId={meetingId}
        onCreateProject={onCreateProject}
        projectName={selectedSpace.name}
        spaceId={spaceId}
        spaceRole={member?.spaceRole}
        dataSource={workspaceDataSource}
      >
        <DataState state="loading" title="회의 정보를 불러오는 중입니다" description="회의 권한과 전사 범위를 확인하고 있습니다." />
      </MeetingLayout>
    );
  }

  if (meetingContext.status === "error") {
    return (
      <MeetingLayout
        meeting={null}
        meetingId={meetingId}
        onCreateProject={onCreateProject}
        projectName={selectedSpace.name}
        spaceId={spaceId}
        spaceRole={member?.spaceRole}
        dataSource={workspaceDataSource}
      >
        <DataState
          actionLabel="회의 목록으로"
          onAction={() => { window.location.href = `/spaces/${encodeURIComponent(spaceId)}/meetings`; }}
          state="error"
          title="회의 정보를 불러오지 못했습니다"
          description={meetingContext.error ?? "접근 권한을 확인한 뒤 다시 시도해 주세요."}
        />
      </MeetingLayout>
    );
  }

  const transcriptStatus = dialogue.data?.status ?? "PROCESSING";

  return (
    <MeetingLayout
      activeItem="transcript"
      meeting={meetingContext.detail}
      meetingId={meetingId}
      onCreateProject={onCreateProject}
      projectName={selectedSpace.name}
      spaceId={spaceId}
      spaceRole={member?.spaceRole}
      dataSource={workspaceDataSource}
    >
      <main className="meeting-transcript-content">
        <header className="meeting-transcript-page-header">
          <div>
            <p className="meeting-detail-section-kicker">Transcript</p>
            <h2>회의 대화 기록</h2>
            <p>현재 회의에서 수집된 발화를 시간순으로 확인합니다. 전사 기록은 Meeting AI의 현재 회의 근거로만 사용됩니다.</p>
          </div>
          <StatusBadge context="transcript" label={transcriptStatusLabel(transcriptStatus)} status={transcriptStatus} />
        </header>

        <section className="meeting-transcript-toolbar" aria-label="전사 기록 도구">
          <label>
            <span>발화 검색</span>
            <input aria-label="발화 검색" onChange={(event) => setQuery(event.target.value)} placeholder="발화자 또는 내용을 검색" type="search" value={query} />
          </label>
          <div className="meeting-transcript-toolbar-meta">
            <span>{visibleEntries.length}개 발화</span>
            <Link to={`/spaces/${encodeURIComponent(spaceId)}/meetings/${encodeURIComponent(meetingId)}/ai`}>Meeting AI로 질문하기</Link>
          </div>
        </section>

        <section className="meeting-transcript-surface" aria-labelledby="meeting-transcript-list-title">
          <div className="meeting-transcript-surface-head">
            <div>
              <p className="meeting-detail-section-kicker">Conversation</p>
              <h3 id="meeting-transcript-list-title">발화 목록</h3>
            </div>
            <span>{dialogue.data?.meetingId === meetingId ? "회의 ID 확인됨" : "회의 ID 확인 중"}</span>
          </div>
          {dialogue.status === "loading" ? (
            <DataState state="loading" title="전사 기록을 불러오는 중입니다" description="저장된 발화를 준비하고 있습니다." />
          ) : dialogue.status === "error" ? (
            <DataState state="error" title="전사 기록을 불러오지 못했습니다" description={dialogue.error ?? "잠시 후 다시 시도해 주세요."} />
          ) : visibleEntries.length ? (
            <div className="meeting-transcript-list">
              {visibleEntries.map((entry) => (
                <article className="meeting-transcript-row" key={entry.key}>
                  <time dateTime={`PT${Math.max(0, Math.floor((entry.isPartial ? 0 : entry.startMs) / 1_000))}S`}>
                    {entry.isPartial ? "LIVE" : formatTranscriptTime(entry.startMs)}
                  </time>
                  <div>
                    <strong>{entry.speakerName}{entry.isPartial ? " · 입력 중" : ""}</strong>
                    <p>{entry.text}</p>
                  </div>
                </article>
              ))}
            </div>
          ) : transcriptStatus === "PROCESSING" ? (
            <DataState state="loading" title="전사가 아직 진행 중입니다" description="새로운 발화가 저장되면 이 화면에서 확인할 수 있습니다." />
          ) : (
            <DataState
              actionLabel={query ? "검색 초기화" : undefined}
              onAction={query ? () => setQuery("") : undefined}
              state="empty"
              title={query ? "검색 결과가 없습니다" : "아직 전사 기록이 없습니다"}
              description={query ? "다른 발화자나 단어로 다시 검색해 주세요." : "회의에서 전사가 완료되면 발화가 여기에 표시됩니다."}
            />
          )}
        </section>
      </main>
    </MeetingLayout>
  );
}
