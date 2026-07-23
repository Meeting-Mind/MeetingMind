import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import { DataState } from "../components/common/DataState";
import { ConfirmDialog } from "../components/common/ConfirmDialog";
import { PageHeader } from "../components/common/PageHeader";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { SpaceLayout } from "../components/layout/SpaceLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { ProjectTaskState, TeamMember, WorkspaceDataSource } from "../app/workspaceTypes";
import type { WorkspaceData } from "../types";

type TaskFilter = "ALL" | ProjectTaskState["status"];

function taskLabel(status: ProjectTaskState["status"]) {
  if (status === "IN_PROGRESS") {
    return "진행 중";
  }
  if (status === "DONE") {
    return "완료";
  }
  return "대기";
}

export function ProjectTasksPage({
  currentUserEmail,
  projectMembers,
  projectTasks,
  session,
  spaces,
  workspaceDataSource,
  onCreateProject,
  onCreateProjectTask,
  onMoveProjectTask,
  onDeleteProjectTask,
  meetingMutationLoading = false,
  meetingMutationError = ""
}: {
  currentUserEmail: string;
  projectMembers: Record<string, TeamMember[]>;
  projectTasks: Record<string, ProjectTaskState[]>;
  session: AuthSession | null;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  onCreateProjectTask?: (projectName: string, task: Omit<ProjectTaskState, "id" | "sourceCandidateId">) => Promise<boolean>;
  onMoveProjectTask?: (projectName: string, taskId: string, status: ProjectTaskState["status"]) => Promise<boolean>;
  onDeleteProjectTask?: (projectName: string, taskId: string) => Promise<boolean>;
  meetingMutationLoading?: boolean;
  meetingMutationError?: string;
}) {
  const { spaceId = "" } = useParams<{ spaceId: string }>();
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<TaskFilter>("ALL");
  const [createOpen, setCreateOpen] = useState(false);
  const [newTaskTitle, setNewTaskTitle] = useState("");
  const [newTaskDescription, setNewTaskDescription] = useState("");
  const [newTaskAssignee, setNewTaskAssignee] = useState("");
  const [newTaskDueDate, setNewTaskDueDate] = useState("");
  const [newTaskPriority, setNewTaskPriority] = useState<ProjectTaskState["priority"]>("MEDIUM");
  const [deleteCandidate, setDeleteCandidate] = useState<ProjectTaskState | null>(null);
  const [taskError, setTaskError] = useState("");
  const selectedSpace = spaces.find((space) => space.id === spaceId);
  const tasks = selectedSpace ? projectTasks[selectedSpace.name] ?? [] : [];
  const members = selectedSpace ? projectMembers[selectedSpace.name] ?? [] : [];
  const currentMember = members.find((member) => member.email === currentUserEmail);
  const canManageTasks = currentMember?.spaceRole === "OWNER" || currentMember?.spaceRole === "ADMIN";

  useEffect(() => {
    document.body.className = "app-theme project-tasks-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="project-tasks-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState state="notFound" title="프로젝트를 찾을 수 없습니다" description="프로젝트 목록에서 접근 가능한 프로젝트를 선택해 주세요." />
      </AppShell>
    );
  }

  const normalizedQuery = query.trim().toLowerCase();
  const visibleTasks = tasks.filter((task) => {
    const matchesStatus = statusFilter === "ALL" || task.status === statusFilter;
    const matchesQuery = !normalizedQuery || [task.title, task.description, task.assignee, task.dueDate, ...task.labels].join(" ").toLowerCase().includes(normalizedQuery);
    return matchesStatus && matchesQuery;
  });
  const columns: Array<{ status: ProjectTaskState["status"]; label: string }> = [
    { status: "TODO", label: "대기" },
    { status: "IN_PROGRESS", label: "진행 중" },
    { status: "DONE", label: "완료" }
  ];
  function resetCreateForm() {
    setNewTaskTitle("");
    setNewTaskDescription("");
    setNewTaskAssignee("");
    setNewTaskDueDate("");
    setNewTaskPriority("MEDIUM");
    setTaskError("");
  }

  async function handleCreateTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSpace || !onCreateProjectTask || !newTaskTitle.trim() || meetingMutationLoading) {
      return;
    }
    setTaskError("");
    try {
      const created = await onCreateProjectTask(selectedSpace.name, {
        title: newTaskTitle.trim(),
        description: newTaskDescription.trim(),
        status: "TODO",
        priority: newTaskPriority,
        labels: [],
        assignee: newTaskAssignee.trim(),
        dueDate: newTaskDueDate,
        meetingKey: null
      });
      if (created) {
        setCreateOpen(false);
        resetCreateForm();
      }
    } catch (error) {
      setTaskError(error instanceof Error ? error.message : "태스크를 생성하지 못했습니다.");
    }
  }

  async function handleMoveTask(task: ProjectTaskState, status: ProjectTaskState["status"]) {
    if (!selectedSpace || !onMoveProjectTask || task.status === status || meetingMutationLoading) {
      return;
    }
    setTaskError("");
    try {
      const updated = await onMoveProjectTask(selectedSpace.name, task.id, status);
      if (!updated) {
        setTaskError("태스크 상태를 변경하지 못했습니다.");
      }
    } catch (error) {
      setTaskError(error instanceof Error ? error.message : "태스크 상태를 변경하지 못했습니다.");
    }
  }

  async function handleDeleteTask() {
    if (!selectedSpace || !deleteCandidate || !onDeleteProjectTask || meetingMutationLoading) {
      return;
    }
    setTaskError("");
    try {
      const deleted = await onDeleteProjectTask(selectedSpace.name, deleteCandidate.id);
      if (!deleted) {
        setTaskError("태스크를 삭제하지 못했습니다.");
      }
    } catch (error) {
      setTaskError(error instanceof Error ? error.message : "태스크를 삭제하지 못했습니다.");
    } finally {
      setDeleteCandidate(null);
    }
  }

  return (
    <SpaceLayout
      activeItem="tasks"
      contentClassName="project-tasks-main"
      dataSource={workspaceDataSource}
      onCreateProject={onCreateProject}
      projectName={selectedSpace.name}
      spaceId={selectedSpace.id}
    >
      <PageHeader
        actions={(
          <>
          <button className="mm-common-button mm-common-button--primary" disabled={!canManageTasks || !onCreateProjectTask} onClick={() => { setTaskError(""); setCreateOpen(true); }} type="button">태스크 만들기</button>
          <Link className="mm-common-button mm-common-button--secondary" to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>프로젝트 홈</Link>
          </>
        )}
        breadcrumb={(
          <>
            <Link to="/spaces">프로젝트 목록</Link>
            <span aria-hidden="true">/</span>
            <Link to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>{selectedSpace.name}</Link>
            <span aria-hidden="true">/</span>
            <strong>Tasks</strong>
          </>
        )}
        description="회의에서 확정한 일을 상태와 담당자 기준으로 이어서 관리합니다."
        eyebrow="Action items"
        meta={currentMember ? <RoleBadge role={currentMember.spaceRole} scope="space" /> : null}
        title="프로젝트 태스크"
      />
      {!canManageTasks ? <p className="project-tasks-permission-note">태스크 생성·상태 변경·삭제는 프로젝트 오너 또는 관리자만 할 수 있습니다.</p> : null}
      {meetingMutationError || taskError ? <p aria-live="polite" className="project-tasks-error" role="alert">{taskError || meetingMutationError}</p> : null}

      <section aria-label="태스크 필터" className="project-tasks-toolbar">
        <label>
          <span>태스크 검색</span>
          <input aria-label="태스크 검색" onChange={(event) => setQuery(event.target.value)} placeholder="제목, 담당자, 라벨 검색" type="search" value={query} />
        </label>
        <label>
          <span>상태</span>
          <select aria-label="태스크 상태 필터" onChange={(event) => setStatusFilter(event.target.value as TaskFilter)} value={statusFilter}>
            <option value="ALL">전체</option>
            <option value="TODO">대기</option>
            <option value="IN_PROGRESS">진행 중</option>
            <option value="DONE">완료</option>
          </select>
        </label>
      </section>

      <section aria-labelledby="project-tasks-board-title" className="project-tasks-board-surface">
        <div className="project-tasks-board-header">
          <div>
            <p className="project-home-section-kicker">{visibleTasks.length} results</p>
            <h2 id="project-tasks-board-title">업무 흐름</h2>
          </div>
          <span>{session ? "Space 범위 태스크" : "로그인이 필요합니다"}</span>
        </div>
        <div className="project-tasks-board">
          {columns.map((column) => {
            const columnTasks = visibleTasks.filter((task) => task.status === column.status);
            return (
              <section aria-labelledby={`project-task-column-${column.status}`} className="project-task-column" key={column.status}>
                <div className="project-task-column-header">
                  <h3 id={`project-task-column-${column.status}`}>{column.label}</h3>
                  <span>{columnTasks.length}</span>
                </div>
                {columnTasks.length ? columnTasks.map((task) => (
                  <article className="project-task-card" key={task.id}>
                    <div className="project-task-card-top">
                      <StatusBadge context="task" label={taskLabel(task.status)} status={task.status} />
                      {task.priority === "HIGH" ? <span className="project-task-priority">높음</span> : null}
                    </div>
                    <strong>{task.title}</strong>
                    <p>{task.description}</p>
                    <footer><span>{task.assignee}</span><span>{task.dueDate || "마감일 미정"}</span></footer>
                    {canManageTasks ? (
                      <div className="project-task-card-actions">
                        <label>
                          <span className="sr-only">{task.title} 상태</span>
                          <select aria-label={`${task.title} 상태`} disabled={meetingMutationLoading} onChange={(event) => void handleMoveTask(task, event.target.value as ProjectTaskState["status"])} value={task.status}>
                            <option value="TODO">대기</option>
                            <option value="IN_PROGRESS">진행 중</option>
                            <option value="DONE">완료</option>
                          </select>
                        </label>
                        <button aria-label={`${task.title} 삭제`} disabled={meetingMutationLoading} onClick={() => setDeleteCandidate(task)} type="button">삭제</button>
                      </div>
                    ) : null}
                  </article>
                )) : <p className="project-task-column-empty">표시할 태스크가 없습니다.</p>}
              </section>
            );
          })}
        </div>
        {!tasks.length ? <DataState state="empty" title="아직 태스크가 없습니다" description="회의에서 확정한 태스크가 이 보드에 나타납니다." /> : null}
      </section>

      {createOpen ? (
        <div className="project-tasks-dialog-backdrop" role="presentation">
          <section aria-labelledby="project-tasks-dialog-title" aria-modal="true" className="project-tasks-dialog" role="dialog">
            <div className="project-tasks-dialog-header">
              <div><p className="project-home-section-kicker">New task</p><h2 id="project-tasks-dialog-title">태스크 만들기</h2></div>
              <button aria-label="태스크 만들기 닫기" className="project-tasks-dialog-close" onClick={() => { setCreateOpen(false); resetCreateForm(); }} type="button">×</button>
            </div>
            <form className="project-tasks-dialog-form" onSubmit={handleCreateTask}>
              <label><span>제목</span><input autoFocus onChange={(event) => setNewTaskTitle(event.target.value)} placeholder="예: 회의록을 검토하고 공유하기" required type="text" value={newTaskTitle} /></label>
              <label><span>설명 <small>선택</small></span><textarea onChange={(event) => setNewTaskDescription(event.target.value)} placeholder="완료 조건을 적어주세요." rows={3} value={newTaskDescription} /></label>
              <div className="project-tasks-dialog-grid">
                <label><span>담당자 <small>선택</small></span><select onChange={(event) => setNewTaskAssignee(event.target.value)} value={newTaskAssignee}><option value="">담당자 미정</option>{members.filter((member) => member.status === "active").map((member) => <option key={member.email} value={member.name}>{member.name}</option>)}</select></label>
                <label><span>우선순위</span><select onChange={(event) => setNewTaskPriority(event.target.value as ProjectTaskState["priority"])} value={newTaskPriority}><option value="LOW">낮음</option><option value="MEDIUM">보통</option><option value="HIGH">높음</option></select></label>
              </div>
              <label><span>마감일 <small>선택</small></span><input onChange={(event) => setNewTaskDueDate(event.target.value)} type="date" value={newTaskDueDate} /></label>
              <div className="project-tasks-dialog-actions"><button className="mm-common-button mm-common-button--secondary" onClick={() => { setCreateOpen(false); resetCreateForm(); }} type="button">취소</button><button className="mm-common-button mm-common-button--primary" disabled={meetingMutationLoading || !newTaskTitle.trim()} type="submit">{meetingMutationLoading ? "생성 중..." : "태스크 만들기"}</button></div>
            </form>
          </section>
        </div>
      ) : null}
      <ConfirmDialog
        busy={meetingMutationLoading}
        confirmLabel="삭제"
        description={deleteCandidate ? `“${deleteCandidate.title}” 태스크를 삭제하면 되돌릴 수 없습니다.` : "삭제할 태스크를 선택해 주세요."}
        onCancel={() => setDeleteCandidate(null)}
        onConfirm={() => void handleDeleteTask()}
        open={Boolean(deleteCandidate)}
        title="태스크를 삭제할까요?"
      />
    </SpaceLayout>
  );
}
