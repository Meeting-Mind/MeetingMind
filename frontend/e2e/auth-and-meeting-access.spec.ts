import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const password = "CiPass123!";
const backendBaseURL = `http://127.0.0.1:${process.env.PLAYWRIGHT_BACKEND_PORT ?? "8080"}`;

function apiPath(path: string) {
  return `${backendBaseURL}${path}`;
}

type AuthSession = {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  refreshExpiresIn: number;
  user: {
    id: string;
    email: string;
    displayName: string;
    status: string;
  };
};

async function signup(request: APIRequestContext, label: string): Promise<AuthSession> {
  const response = await request.post(apiPath("/api/v1/auth/signup"), {
    data: {
      displayName: `CI ${label}`,
      email: `${label}-${Date.now()}@example.com`,
      password
    }
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as AuthSession;
}

async function loginThroughBff(page: Page, session: AuthSession, path: string) {
  await page.goto(path);
  const dialog = page.getByRole("dialog", { name: "로그인 후 이용할 수 있습니다" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("이메일").fill(session.user.email);
  await dialog.getByLabel("비밀번호").fill(password);
  await dialog.locator("form").getByRole("button", { name: "로그인", exact: true }).click();
  await expect(dialog).toBeHidden();
}

test.beforeEach(async ({ page }) => {
  await page.route("https://accounts.google.com/**", (route) => route.abort());
});

test("password login opens the requested protected route", async ({ page, request }) => {
  const session = await signup(request, "login");

  await loginThroughBff(page, session, "/spaces");

  await expect(page).toHaveURL(/\/spaces$/);
  await expect(page.getByRole("textbox", { name: "프로젝트 검색" })).toBeVisible();
});

test("current-session logout clears the authenticated UI and blocks reuse", async ({ page, request }) => {
  const session = await signup(request, "logout-success");
  await loginThroughBff(page, session, "/spaces");

  const logoutResponsePromise = page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().endsWith("/api/v1/auth/logout")
  );
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  const logoutResponse = await logoutResponsePromise;

  expect(logoutResponse.status()).toBe(204);
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("complementary", { name: "사용자 세션" })).toHaveCount(0);

  await page.goto("/spaces");
  await expect(page.getByRole("dialog", { name: "로그인 후 이용할 수 있습니다" })).toBeVisible();
});

test("logout network failure keeps the session visible and offers retry feedback", async ({ page, request }) => {
  const session = await signup(request, "logout-failure");
  await page.route("**/api/v1/auth/logout", (route) => route.abort("failed"));
  await loginThroughBff(page, session, "/spaces");

  await page.getByRole("button", { name: "로그아웃", exact: true }).click();

  await expect(page.getByRole("alert")).toHaveText(
    "로그아웃 서버에 연결하지 못했습니다. 연결을 확인하고 다시 시도해 주세요."
  );
  await expect(page).toHaveURL(/\/spaces$/);
  await expect(page.getByRole("complementary", { name: "사용자 세션" })).toBeVisible();
});

test("final SESSION_INVALID redirects once and returns to the requested route after login", async ({ page, request }) => {
  const session = await signup(request, "session-invalid");
  let finalSessionInvalidSent = false;
  let finalSessionInvalidCount = 0;

  await page.route("**/api/v1/auth/session", async (route) => {
    if (finalSessionInvalidSent) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ authenticated: false, user: null, session: null })
      });
      return;
    }
    await route.continue();
  });
  await page.route("**/api/v1/spaces", async (route) => {
    if (route.request().method() === "GET" && !finalSessionInvalidSent) {
      finalSessionInvalidSent = true;
      finalSessionInvalidCount += 1;
      await route.fulfill({
        status: 401,
        contentType: "application/json",
        body: JSON.stringify({
          code: "SESSION_INVALID",
          message: "로그인이 만료되었습니다. 다시 로그인해 주세요.",
          fieldErrors: [],
          traceId: "playwright-session-invalid"
        })
      });
      return;
    }
    await route.continue();
  });

  await loginThroughBff(page, session, "/spaces");

  const expiredDialog = page.getByRole("dialog", { name: "로그인 후 이용할 수 있습니다" });
  await expect(page).toHaveURL(/\/?auth=session-expired&returnTo=%2Fspaces$/);
  await expect(expiredDialog).toBeVisible();
  await expect(expiredDialog.getByRole("status")).toHaveText("로그인이 만료되었습니다. 다시 로그인해 주세요.");
  await expect(page.getByRole("complementary", { name: "사용자 세션" })).toHaveCount(0);
  expect(finalSessionInvalidCount).toBe(1);

  await expiredDialog.getByLabel("이메일").fill(session.user.email);
  await expiredDialog.getByLabel("비밀번호").fill(password);
  await expiredDialog.locator("form").getByRole("button", { name: "로그인", exact: true }).click();

  await expect(page).toHaveURL(/\/spaces$/);
  await expect(page.getByRole("textbox", { name: "프로젝트 검색" })).toBeVisible();
});

test("meeting prejoin allows an active HOST and denies an unknown meeting", async ({ page, request }) => {
  const session = await signup(request, "meeting-access");
  const authorization = { Authorization: `Bearer ${session.accessToken}` };

  const spaceResponse = await request.post(apiPath("/api/v1/spaces"), {
    data: { name: "CI access space", description: "Playwright meeting access verification" },
    headers: authorization
  });
  expect(spaceResponse.ok()).toBeTruthy();
  const space = (await spaceResponse.json()) as { id: string };

  const meetingResponse = await request.post(apiPath(`/api/v1/spaces/${space.id}/meetings`), {
    data: {
      title: "CI permission meeting",
      scheduledAt: "2030-01-01T09:00:00+09:00",
      participantUserIds: []
    },
    headers: authorization
  });
  expect(meetingResponse.ok()).toBeTruthy();
  const meeting = (await meetingResponse.json()) as { id: string };

  await loginThroughBff(
    page,
    session,
    `/live-meeting?spaceId=${space.id}&meetingId=${meeting.id}&meeting=CI+permission+meeting`
  );
  await expect(page.getByText("참여 권한 확인됨")).toBeVisible();
  await expect(page.getByText("HOST 권한으로 입장합니다")).toBeVisible();

  await page.goto("/live-meeting?meetingId=missing-meeting");
  await expect(page.getByRole("heading", { name: "회의에 접근할 수 없습니다" })).toBeVisible();
  await expect(page.getByText("default-deny")).toBeVisible();
});

test("workspace restores persisted spaces and meetings after reload", async ({ page, request }) => {
  const session = await signup(request, "workspace-hydration");
  const authorization = { Authorization: `Bearer ${session.accessToken}` };
  const spaceName = `CI persisted space ${Date.now()}`;
  const meetingTitle = "CI persisted meeting";

  const spaceResponse = await request.post(apiPath("/api/v1/spaces"), {
    data: { name: spaceName, description: "Workspace hydration verification" },
    headers: authorization
  });
  expect(spaceResponse.ok()).toBeTruthy();
  const space = (await spaceResponse.json()) as { id: string };
  const meetingResponse = await request.post(apiPath(`/api/v1/spaces/${space.id}/meetings`), {
    data: {
      title: meetingTitle,
      scheduledAt: "2030-01-02T09:00:00+09:00",
      participantUserIds: []
    },
    headers: authorization
  });
  expect(meetingResponse.ok()).toBeTruthy();

  await loginThroughBff(page, session, "/spaces");
  await expect(page.getByText("Workspace API", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: spaceName })).toBeVisible();

  await page.reload();
  await expect(page.getByRole("heading", { name: spaceName })).toBeVisible();
  expect(await page.evaluate(() => window.sessionStorage.getItem("meetingmind.auth.session"))).toBeNull();
  await page.getByRole("heading", { name: spaceName }).click();
  await expect(page.getByText(meetingTitle, { exact: true }).first()).toBeVisible();
});

test("failed project creation keeps the form open without a phantom project", async ({ page, request }) => {
  const session = await signup(request, "workspace-create-failure");
  const projectName = `CI rejected space ${Date.now()}`;

  await page.route("**/api/v1/spaces", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ message: "의도된 프로젝트 생성 실패" })
      });
      return;
    }
    await route.continue();
  });
  await loginThroughBff(page, session, "/spaces");
  await page.getByRole("button", { name: "+ 새 프로젝트 만들기" }).click();

  const dialog = page.getByRole("dialog", { name: "새 프로젝트 만들기" });
  await dialog.getByLabel("프로젝트명").fill(projectName);
  await dialog.getByRole("button", { name: "프로젝트 생성" }).click();

  await expect(dialog).toBeVisible();
  await expect(dialog.getByText("의도된 프로젝트 생성 실패")).toBeVisible();
  await expect(page.locator(".workspace-catalog-grid").getByRole("heading", { name: projectName })).toHaveCount(0);
});

test("meeting CRUD is persisted through the project UI", async ({ page, request }) => {
  const session = await signup(request, "meeting-crud");
  const authorization = { Authorization: `Bearer ${session.accessToken}` };
  const spaceName = `CI CRUD space ${Date.now()}`;

  const spaceResponse = await request.post(apiPath("/api/v1/spaces"), {
    data: { name: spaceName, description: "Playwright meeting CRUD verification" },
    headers: authorization
  });
  expect(spaceResponse.ok()).toBeTruthy();
  const space = (await spaceResponse.json()) as { id: string };

  await loginThroughBff(
    page,
    session,
    `/project-overview?${new URLSearchParams({ spaceId: space.id, project: spaceName }).toString()}`
  );
  await expect(page.getByRole("heading", { name: `${spaceName} 프로젝트` })).toBeVisible();
  await expect(page.getByText("PostgreSQL target API")).toBeVisible();
  await expect(page.getByLabel("새 회의 제목")).toBeEnabled();

  await page.getByLabel("새 회의 제목").fill("CI CRUD 회의");
  await page.getByLabel("새 회의 일시").fill("2030-03-01T10:00");
  await page.getByRole("button", { name: "회의 생성", exact: true }).click();
  await expect(page.getByRole("link", { name: /CI CRUD 회의/ })).toBeVisible();
  await expect(page.getByLabel("회의 참가 코드")).not.toHaveValue("");

  await page.getByLabel("회의 제목 수정").fill("CI CRUD 회의 수정");
  await page.getByLabel("예정 일시 수정").fill("2030-03-02T11:30");
  await page.getByRole("button", { name: "회의 정보 저장" }).click();
  await expect(page.getByRole("link", { name: /CI CRUD 회의 수정/ })).toBeVisible();

  await page.getByRole("button", { name: "회의 삭제", exact: true }).click();
  const deleteConfirmation = page.getByLabel("회의 삭제 확인값");
  await deleteConfirmation.fill((await deleteConfirmation.getAttribute("placeholder")) ?? "");
  await page.getByRole("button", { name: "삭제 확정" }).click();
  await expect(page.getByRole("link", { name: /CI CRUD 회의 수정/ })).toHaveCount(0);

  const meetingsResponse = await request.get(apiPath(`/api/v1/spaces/${space.id}/meetings`), { headers: authorization });
  expect(meetingsResponse.ok()).toBeTruthy();
  expect(((await meetingsResponse.json()) as { meetings: unknown[] }).meetings).toHaveLength(0);

  await page.goto("/spaces");
  await page.getByLabel("회의 생성 프로젝트").selectOption(space.id);
  await page.getByLabel("회의 제목", { exact: true }).fill("CI 캘린더 회의");
  await page.getByLabel("회의 시작 일시").fill("2030-04-03T14:00");
  await page.getByRole("button", { name: "일정 추가" }).click();
  await expect(page.getByLabel("캘린더 회의 참가 코드")).not.toHaveValue("");
  await page.getByLabel("캘린더 기준 날짜").fill("2030-04-03");
  await page.getByRole("button", { name: "일", exact: true }).click();
  await expect(page.getByRole("link", { name: /CI 캘린더 회의/ })).toBeVisible();
});

test("meeting detail participant ACL is loaded and mutated through the project UI", async ({ page, request }) => {
  const owner = await signup(request, "meeting-acl-owner");
  const guest = await signup(request, "meeting-acl-guest");
  const authorization = { Authorization: `Bearer ${owner.accessToken}` };
  const spaceName = `CI ACL space ${Date.now()}`;

  const guestWorkspaceResponse = await request.get(apiPath("/api/v1/spaces"), {
    headers: { Authorization: `Bearer ${guest.accessToken}` }
  });
  expect(guestWorkspaceResponse.ok()).toBeTruthy();

  const spaceResponse = await request.post(apiPath("/api/v1/spaces"), {
    data: { name: spaceName, description: "Playwright meeting participant ACL verification" },
    headers: authorization
  });
  expect(spaceResponse.ok()).toBeTruthy();
  const space = (await spaceResponse.json()) as { id: string };

  const meetingResponse = await request.post(apiPath(`/api/v1/spaces/${space.id}/meetings`), {
    data: {
      title: "CI ACL 회의",
      scheduledAt: "2030-05-01T09:00:00+09:00",
      participantUserIds: [guest.user.id]
    },
    headers: authorization
  });
  expect(meetingResponse.ok()).toBeTruthy();
  const meeting = (await meetingResponse.json()) as { id: string };

  await loginThroughBff(
    page,
    owner,
    `/project-overview?${new URLSearchParams({ spaceId: space.id, project: spaceName }).toString()}`
  );
  const guestRow = page.locator(".project-acl-row").filter({ hasText: guest.user.displayName });
  await expect(guestRow).toBeVisible();
  await expect(guestRow.getByRole("combobox")).toHaveValue("VIEWER");

  const roleUpdateResponsePromise = page.waitForResponse(
    (response) => response.request().method() === "PATCH" && response.url().includes(`/participants/`)
  );
  await guestRow.getByRole("combobox").selectOption("EDITOR");
  const roleUpdateResponse = await roleUpdateResponsePromise;
  expect(roleUpdateResponse.ok(), await roleUpdateResponse.text()).toBeTruthy();
  await expect(guestRow.getByRole("combobox")).toHaveValue("EDITOR");
  const revokeResponsePromise = page.waitForResponse(
    (response) => response.request().method() === "PATCH" && response.url().includes(`/participants/`)
  );
  await guestRow.getByRole("button", { name: "회수" }).click();
  const revokeResponse = await revokeResponsePromise;
  expect(revokeResponse.ok(), await revokeResponse.text()).toBeTruthy();
  await expect(guestRow.getByRole("button", { name: "복구" })).toBeVisible();

  const detailResponse = await request.get(apiPath(`/api/v1/meetings/${meeting.id}`), { headers: authorization });
  expect(detailResponse.ok()).toBeTruthy();
  const detail = (await detailResponse.json()) as {
    participants: Array<{ userId: string; role: string; accessStatus: string }>;
  };
  expect(detail.participants).toEqual(
    expect.arrayContaining([
      expect.objectContaining({ userId: guest.user.id, role: "EDITOR", accessStatus: "REVOKED" })
    ])
  );
});
