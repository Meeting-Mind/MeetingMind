import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

/**
 * SMK-005 브라우저 축: UI가 서버 권한 경계를 우회하지 않는지 확인한다.
 *
 * 서버 경계 자체는 `GuestSpaceAclNegativeIntegrationTest`(T446)가 실 PostgreSQL로 이미 덮었다.
 * 이 스펙이 덮는 것은 **클라이언트 계층**이다. 즉 회의 전용 GUEST가 URL을 직접 입력해도
 * Space 범위 화면이 렌더되지 않고, 목록에도 노출되지 않는지를 본다.
 *
 * 주의: playwright.config.ts는 backend를 `SPRING_PROFILES_ACTIVE=test`로 띄우므로
 * in-memory adapter가 쓰인다. 따라서 이 스펙은 SQL 계층 결함을 잡지 못한다. 그 축은 T446이
 * 담당한다. 두 축을 섞어 생각하면 "guest 테스트가 있으니 안전하다"는 잘못된 결론에 이른다.
 */

const password = "CiPass123!";
const backendBaseUrl = `http://127.0.0.1:${process.env.PLAYWRIGHT_BACKEND_PORT ?? "8080"}`;

type AuthSession = {
  accessToken: string;
  user: { id: string; email: string };
};

async function signup(request: APIRequestContext, label: string): Promise<AuthSession> {
  const response = await request.post(`${backendBaseUrl}/api/v1/auth/signup`, {
    data: {
      displayName: `CI ${label}`,
      email: `${label}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`,
      password
    }
  });

  expect(response.ok()).toBeTruthy();
  return (await response.json()) as AuthSession;
}

async function signIn(page: Page, session: AuthSession, targetPath = "/spaces") {
  await page.goto(targetPath);

  const signInPage = page.getByTestId("sign-in-page");
  await expect(signInPage).toBeVisible();
  await signInPage.getByTestId("sign-in-email").fill(session.user.email);
  await signInPage.getByTestId("sign-in-password").fill(password);
  await signInPage.getByTestId("sign-in-submit").click();
  await expect(signInPage).toBeHidden();
}

function authorized(session: AuthSession) {
  return { Authorization: `Bearer ${session.accessToken}` };
}

type Fixture = {
  host: AuthSession;
  guest: AuthSession;
  spaceId: string;
  spaceName: string;
  invitedMeetingId: string;
  excludedMeetingId: string;
  excludedMeetingTitle: string;
};

/**
 * 호스트가 Space와 회의 2개를 만들고, guest를 **한쪽 회의에만** 참가자로 넣는다.
 * guest를 Space 멤버로는 넣지 않는다. 이것이 "회의 전용 guest"다.
 */
async function buildFixture(request: APIRequestContext, label: string): Promise<Fixture> {
  const host = await signup(request, `${label}-host`);
  const guest = await signup(request, `${label}-guest`);

  const stamp = Date.now();
  const spaceName = `Guest ACL UI Space ${stamp}`;
  const excludedMeetingTitle = `Excluded Meeting ${stamp}`;

  const spaceResponse = await request.post(`${backendBaseUrl}/api/v1/spaces`, {
    headers: authorized(host),
    data: { name: spaceName, description: "SMK-005 UI boundary check" }
  });
  expect(spaceResponse.ok()).toBeTruthy();
  const spaceId = (await spaceResponse.json()).id as string;

  const createMeeting = async (title: string, hourOffset: number) => {
    const scheduledAt = new Date(stamp + hourOffset * 60 * 60 * 1000).toISOString();
    const scheduledEndAt = new Date(stamp + (hourOffset + 1) * 60 * 60 * 1000).toISOString();
    const response = await request.post(`${backendBaseUrl}/api/v1/spaces/${spaceId}/meetings`, {
      headers: authorized(host),
      data: { title, description: title, scheduledAt, scheduledEndAt, participantUserIds: [] }
    });
    expect(response.ok(), `회의 생성 실패: ${await response.text()}`).toBeTruthy();
    return (await response.json()).id as string;
  };

  const invitedMeetingId = await createMeeting(`Invited Meeting ${stamp}`, 1);
  const excludedMeetingId = await createMeeting(excludedMeetingTitle, 2);

  const participantResponse = await request.post(
    `${backendBaseUrl}/api/v1/meetings/${invitedMeetingId}/participants`,
    {
      headers: authorized(host),
      data: { userId: guest.user.id, role: "VIEWER", participantType: "guest" }
    }
  );
  expect(participantResponse.ok()).toBeTruthy();

  return { host, guest, spaceId, spaceName, invitedMeetingId, excludedMeetingId, excludedMeetingTitle };
}

test.beforeEach(async ({ page }) => {
  await page.route("https://accounts.google.com/**", (route) => route.abort());
});

test("meeting-only guest sees the invited meeting but not the space it belongs to", async ({
  page,
  request
}) => {
  const fixture = await buildFixture(request, "acl-positive");

  // 양성 대조를 먼저 둔다. 셋업이 깨져 모든 접근이 실패하는 상태도 거부 단정만으로는
  // 통과처럼 보인다. guest 토큰으로 초대된 회의를 실제로 읽을 수 있어야 셋업이 유효하다.
  const invitedDetail = await request.get(
    `${backendBaseUrl}/api/v1/meetings/${fixture.invitedMeetingId}`,
    { headers: authorized(fixture.guest) }
  );
  expect(invitedDetail.ok(), "guest는 초대된 회의를 읽을 수 있어야 한다 (셋업 유효성)").toBeTruthy();

  await signIn(page, fixture.guest);

  // Space 멤버가 아니므로 Space 목록에 호스트의 Space가 나타나면 안 된다.
  await expect(page.getByText(fixture.spaceName, { exact: true })).toHaveCount(0);
});

test("space owner does see the space screens the guest is denied", async ({ page, request }) => {
  // 이 스펙의 음성 단정은 전부 `toHaveCount(0)`이다. 페이지가 아무것도 렌더하지 않아도
  // (예: 라우트 오타, 로그인 실패, 앱 크래시) 그대로 통과한다. 그래서 같은 URL에서
  // **소유자는 보인다**는 것을 함께 고정한다. 이 테스트가 깨지면 음성 단정은 무의미해진다.
  const fixture = await buildFixture(request, "acl-control");

  await signIn(page, fixture.host);

  await page.goto(`/spaces/${fixture.spaceId}/meetings`);
  await expect(
    page.getByText(fixture.excludedMeetingTitle, { exact: true }).first(),
    "소유자에게는 회의 목록이 보여야 한다 (음성 단정의 유효성 근거)"
  ).toBeVisible();
});

test("meeting-only guest cannot reach space-scoped screens by typing the URL", async ({
  page,
  request
}) => {
  const fixture = await buildFixture(request, "acl-url");

  await signIn(page, fixture.guest);

  // URL 직접 입력은 UI 내비게이션을 우회하는 가장 흔한 경로다.
  const spaceScopedPaths = [
    `/spaces/${fixture.spaceId}`,
    `/spaces/${fixture.spaceId}/meetings`,
    `/spaces/${fixture.spaceId}/members`,
    `/spaces/${fixture.spaceId}/knowledge`,
    `/spaces/${fixture.spaceId}/meetings/${fixture.excludedMeetingId}`
  ];

  for (const path of spaceScopedPaths) {
    await page.goto(path);
    await expect(
      page.getByText(fixture.spaceName, { exact: true }),
      `${path}에서 Space 이름이 노출되면 안 된다`
    ).toHaveCount(0);
    await expect(
      page.getByText(fixture.excludedMeetingTitle, { exact: true }),
      `${path}에서 초대되지 않은 회의 제목이 노출되면 안 된다`
    ).toHaveCount(0);
  }
});

test("space-scoped api stays denied for a meeting-only guest", async ({ request }) => {
  const fixture = await buildFixture(request, "acl-api");

  // 화면에 안 보이는 것만으로는 부족하다. 화면이 호출하는 API 자체가 거부돼야
  // 클라이언트 필터만으로 가려둔 상태가 아님을 알 수 있다.
  const spaceScopedEndpoints = [
    `/api/v1/spaces/${fixture.spaceId}`,
    `/api/v1/spaces/${fixture.spaceId}/meetings`,
    `/api/v1/spaces/${fixture.spaceId}/members`,
    `/api/v1/meetings/${fixture.excludedMeetingId}`
  ];

  for (const endpoint of spaceScopedEndpoints) {
    const response = await request.get(`${backendBaseUrl}${endpoint}`, {
      headers: authorized(fixture.guest)
    });
    expect(response.ok(), `${endpoint}는 guest에게 거부돼야 한다`).toBeFalsy();
    expect(response.status(), `${endpoint} 상태 코드`).toBeGreaterThanOrEqual(400);
  }
});
