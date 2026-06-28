import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { WorkspaceData } from "../types";

const catalogLabels = ["DB 설계", "아키텍처", "보안 정책"] as const;
const catalogDurations = ["32분", "18분", "41분"] as const;
const catalogDates = ["2일 전", "1일 전", "2일 전"] as const;
const catalogMembers = ["4명", "12명", "16명"] as const;
const catalogSummaries = [
  "3회차 회의에서 논의된 구조 점검과 수정 포인트를 정리한 회의 카드입니다.",
  "Meeting AI와 Project AI의 검색 범위 및 권한 분리 구조를 정리한 논의입니다.",
  "자주 사용하는 기술 용어를 프로젝트 사전에 어떻게 축적할지 결정한 회의입니다."
] as const;

function getMemberInitials(index: number) {
  const sets = [
    ["김", "이", "박"],
    ["정", "김"],
    ["박", "이"]
  ];

  return sets[index % sets.length];
}

function getTotalMeetings(spaces: WorkspaceData["workspaceHome"]["spaces"]) {
  return spaces.reduce((total, space) => {
    const match = space.meetings.match(/\d+/);
    return total + Number(match?.[0] ?? 0);
  }, 0);
}

function parseMemberCount(value: string) {
  const match = value.match(/\d+/);
  return Number(match?.[0] ?? 0);
}

function parseUpdatedRank(value: string) {
  if (value.includes("오늘")) {
    return 300;
  }
  if (value.includes("어제")) {
    return 200;
  }

  const match = value.match(/(\d{2})\.(\d{2})/);
  if (!match) {
    return 0;
  }

  return Number(match[1]) * 100 + Number(match[2]);
}

export function WorkspaceHomePage({
  data,
  onCreateProject
}: {
  data: WorkspaceData["workspaceHome"];
  onCreateProject?: (payload: { name: string; description: string }) => void;
}) {
  useEffect(() => {
    document.body.className = "app-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  const [searchQuery, setSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState<"recent" | "name" | "members">("recent");
  const [filterBy, setFilterBy] = useState<"all" | (typeof catalogLabels)[number]>("all");
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 6;
  const totalMeetings = getTotalMeetings(data.spaces);
  const filterOptions: Array<"all" | (typeof catalogLabels)[number]> = ["all", ...catalogLabels];

  const catalogSpaces = useMemo(
    () =>
      data.spaces.map((space, index) => ({
        ...space,
        catalogLabel: catalogLabels[index % catalogLabels.length],
        catalogDuration: catalogDurations[index % catalogDurations.length],
        catalogDate: catalogDates[index % catalogDates.length],
        catalogMembers: catalogMembers[index % catalogMembers.length],
        catalogSummary: catalogSummaries[index % catalogSummaries.length],
        initials: getMemberInitials(index),
        isFeaturedAction: index === 1
      })),
    [data.spaces]
  );

  const filteredSpaces = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase();

    const searched = normalizedQuery
      ? catalogSpaces.filter((space) =>
          `${space.name} ${space.description} ${space.members} ${space.meetings} ${space.catalogLabel} ${space.catalogSummary}`
            .toLowerCase()
            .includes(normalizedQuery)
        )
      : catalogSpaces;

    const filtered = filterBy === "all" ? searched : searched.filter((space) => space.catalogLabel === filterBy);

    const next = [...filtered];

    if (sortBy === "name") {
      next.sort((a, b) => a.name.localeCompare(b.name, "ko"));
    } else if (sortBy === "members") {
      next.sort((a, b) => parseMemberCount(b.members) - parseMemberCount(a.members));
    } else {
      next.sort((a, b) => parseUpdatedRank(b.updatedAt) - parseUpdatedRank(a.updatedAt));
    }

    return next;
  }, [catalogSpaces, filterBy, searchQuery, sortBy]);

  const totalPages = Math.max(1, Math.ceil(filteredSpaces.length / itemsPerPage));
  const currentPageSafe = Math.min(currentPage, totalPages);
  const pagedSpaces = filteredSpaces.slice((currentPageSafe - 1) * itemsPerPage, currentPageSafe * itemsPerPage);

  useEffect(() => {
    setCurrentPage(1);
  }, [filterBy, searchQuery, sortBy]);

  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages);
    }
  }, [currentPage, totalPages]);

  const handleCycleFilter = () => {
    const currentIndex = filterOptions.indexOf(filterBy);
    const nextIndex = (currentIndex + 1) % filterOptions.length;
    setFilterBy(filterOptions[nextIndex]);
  };

  return (
    <div className="workspace-catalog-shell workspace-catalog-home-shell">
      <WorkspaceSidebar activeItem="catalog" disableMembers onCreateProject={onCreateProject} />

      <main className="workspace-catalog-main workspace-catalog-home-main">
        <div className="workspace-catalog-topbar">
          <div className="workspace-catalog-top-actions" aria-hidden="true">
            <button className="workspace-catalog-icon-button">🔔</button>
          </div>
        </div>

        <section className="workspace-catalog-controls">
          <div className="workspace-catalog-search">
            <span className="workspace-catalog-search-icon">⌕</span>
            <input
              type="text"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="회의 제목, 프로젝트로 검색"
              aria-label="회의 검색"
            />
          </div>

          <div className="workspace-catalog-filters">
            <span className="workspace-catalog-filter-label">정렬:</span>
            <select
              className="workspace-catalog-sort workspace-catalog-sort-select"
              value={sortBy}
              onChange={(event) => setSortBy(event.target.value as "recent" | "name" | "members")}
              aria-label="정렬 기준"
            >
              <option value="recent">최근 회의순</option>
              <option value="name">이름순</option>
              <option value="members">멤버 많은순</option>
            </select>
            <button
              className={`workspace-catalog-filter-button ${filterBy !== "all" ? "is-active" : ""}`}
              type="button"
              onClick={handleCycleFilter}
              aria-label={`필터 변경: 현재 ${filterBy === "all" ? "전체" : filterBy}`}
              title="클릭하면 필터가 변경됩니다"
            >
              {filterBy === "all" ? "전체" : filterBy}
            </button>
          </div>
        </section>

        <div className="workspace-catalog-heading">
          <strong>전체 회의 {totalMeetings}건</strong>
        </div>

        <section className="workspace-catalog-grid">
          {pagedSpaces.map((space, index) => (
            <Link
              key={space.name}
              className="workspace-catalog-card"
              to={`/project-overview?project=${encodeURIComponent(space.name)}`}
            >
              <div className="workspace-catalog-card-time">
                <span className="workspace-catalog-time-dot" />
                <strong>{space.catalogDuration}</strong>
              </div>

              <h2>{space.name}</h2>

              <div className={`workspace-catalog-tag tone-${(index % 3) + 1}`}>{space.catalogLabel}</div>

              <p>{space.catalogSummary}</p>

              <div className="workspace-catalog-card-footer">
                <div className="workspace-catalog-avatars" aria-hidden="true">
                  {space.initials.map((initial, avatarIndex) => (
                    <span
                      key={`${space.name}-${initial}-${avatarIndex}`}
                      className={`workspace-catalog-avatar tone-${((index + avatarIndex) % 3) + 1}`}
                    >
                      {initial}
                    </span>
                  ))}
                  <strong>{space.catalogMembers}</strong>
                </div>
                <span className="workspace-catalog-date">{space.catalogDate}</span>
              </div>

              {space.isFeaturedAction ? <div className="workspace-catalog-card-action">+ 보고서에 추가</div> : null}
            </Link>
          ))}
        </section>

        <section className="workspace-catalog-footer">
          <span>
            검색 결과 {filteredSpaces.length}개 · 페이지 {currentPageSafe}/{totalPages}
          </span>
          <div className="workspace-catalog-pagination">
            <button type="button" onClick={() => setCurrentPage(1)} disabled={currentPageSafe === 1}>
              {"‹‹"}
            </button>
            <button type="button" onClick={() => setCurrentPage((page) => Math.max(1, page - 1))} disabled={currentPageSafe === 1}>
              {"‹"}
            </button>
            {Array.from({ length: totalPages }, (_, index) => index + 1).map((page) => (
              <button
                key={page}
                className={page === currentPageSafe ? "active" : ""}
                type="button"
                onClick={() => setCurrentPage(page)}
              >
                {page}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))}
              disabled={currentPageSafe === totalPages}
            >
              {"›"}
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}
