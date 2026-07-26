import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";

/**
 * SMK-002 매체(media) 축: 두 참가자가 같은 회의에 실제로 접속해 publish/subscribe가 성립하는지 본다.
 *
 * `LiveKitRealServerSmokeIntegrationTest`(T444)는 **서버 도달성**만 덮는다. room create/list/delete와
 * token 스코프는 확인되지만 브라우저 client가 없어 매체 경로는 검증되지 않는다. 이 스펙이 그 축이다.
 *
 * opt-in인 이유: 실제 LiveKit 자격증명이 필요하다. `LiveKitTokenService`는 CWD의 `.env`를 읽고
 * Playwright backend webServer의 cwd가 `backend/`이므로 로컬에서는 `backend/.env`가 쓰인다.
 * CI에는 그 파일이 없어 토큰 발급이 `LIVEKIT_NOT_CONFIGURED`로 실패한다. 게이트 없이 두면 CI가
 * 항상 실패하고, 조건부 skip으로 두면 "통과처럼 보이는 skip"이 된다. 그래서 **명시적 opt-in**으로 둔다.
 *
 *   cd frontend && RUN_LIVEKIT_MEDIA_E2E=true BFF_REDIS_PORT=6380 npx playwright test live-media
 *
 * fake media로 덮지 못하는 것: 실제 마이크/카메라 권한 프롬프트, prejoin 장치 선택 UX, 실제 오디오
 * 품질. 이 세 가지는 여전히 사람이 확인해야 한다.
 */

const password = "CiPass123!";
const backendBaseUrl = `http://127.0.0.1:${process.env.PLAYWRIGHT_BACKEND_PORT ?? "8080"}`;
const enabled = process.env.RUN_LIVEKIT_MEDIA_E2E === "true";

type AuthSession = {
  accessToken: string;
  user: { id: string; email: string; displayName: string };
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

function authorized(session: AuthSession) {
  return { Authorization: `Bearer ${session.accessToken}` };
}

async function signIn(page: Page, session: AuthSession, targetPath: string) {
  await page.goto(targetPath);
  const signInPage = page.getByTestId("sign-in-page");
  await expect(signInPage).toBeVisible();
  await signInPage.getByTestId("sign-in-email").fill(session.user.email);
  await signInPage.getByTestId("sign-in-password").fill(password);
  await signInPage.getByTestId("sign-in-submit").click();
  await expect(signInPage).toBeHidden();
}

async function joinLiveRoom(browser: Browser, session: AuthSession, spaceId: string, meetingId: string) {
  const context = await browser.newContext({ permissions: ["camera", "microphone"] });
  const page = await context.newPage();
  await page.route("https://accounts.google.com/**", (route) => route.abort());

  const prejoinPath = `/spaces/${spaceId}/meetings/${meetingId}/live/prejoin`;
  await signIn(page, session, prejoinPath);
  await page.goto(prejoinPath);

  const joinButton = page.getByRole("button", { name: "Join Now" });
  await expect(joinButton, "prejoin에서 Join Now가 활성화돼야 한다").toBeEnabled({ timeout: 20_000 });
  await joinButton.click();
  await expect(page).toHaveURL(new RegExp(`/meetings/${meetingId}/live$`));

  return { context, page };
}

// fake device가 없으면 getUserMedia가 권한 프롬프트에서 멈춘다.
// describe 안에 두면 worker를 새로 강제해 Playwright가 거부하므로 top-level에 둔다.
test.use({
  launchOptions: {
    args: [
      "--use-fake-device-for-media-stream",
      "--use-fake-ui-for-media-stream",
      "--autoplay-policy=no-user-gesture-required"
    ]
  }
});

test.describe("SMK-002 media publish/subscribe", () => {
  test.skip(!enabled, "RUN_LIVEKIT_MEDIA_E2E=true 와 실제 LiveKit 자격증명이 필요하다");

  test("two participants see each other as connected remote participants", async ({ browser, request }) => {
    const host = await signup(request, "media-host");
    const guest = await signup(request, "media-guest");
    const stamp = Date.now();

    // signup은 auth store에만 사용자를 만든다. workspace store에는 인증된 API를 한 번
    // 호출할 때 `ensureUser`로 등록되므로, 참가자로 추가하기 전에 먼저 등록시킨다.
    const guestBootstrap = await request.get(`${backendBaseUrl}/api/v1/spaces`, {
      headers: authorized(guest)
    });
    expect(guestBootstrap.ok(), "guest workspace 등록 실패").toBeTruthy();

    const spaceResponse = await request.post(`${backendBaseUrl}/api/v1/spaces`, {
      headers: authorized(host),
      data: { name: `Media Space ${stamp}`, description: "SMK-002 media axis" }
    });
    expect(spaceResponse.ok()).toBeTruthy();
    const spaceId = (await spaceResponse.json()).id as string;

    const meetingResponse = await request.post(`${backendBaseUrl}/api/v1/spaces/${spaceId}/meetings`, {
      headers: authorized(host),
      data: {
        title: `Media Meeting ${stamp}`,
        description: "SMK-002",
        scheduledAt: new Date(stamp).toISOString(),
        scheduledEndAt: new Date(stamp + 60 * 60 * 1000).toISOString(),
        participantUserIds: []
      }
    });
    expect(meetingResponse.ok(), `회의 생성 실패: ${await meetingResponse.text()}`).toBeTruthy();
    const meetingId = (await meetingResponse.json()).id as string;

    const participantResponse = await request.post(
      `${backendBaseUrl}/api/v1/meetings/${meetingId}/participants`,
      {
        headers: authorized(host),
        // `member` participantType은 SpaceMember를 요구한다. 회의 초대만 받은 참가자는 `guest`다.
        data: { userId: guest.user.id, role: "VIEWER", participantType: "guest" }
      }
    );
    expect(participantResponse.ok(), `참가자 등록 실패: ${await participantResponse.text()}`).toBeTruthy();

    // 자격증명이 실제로 살아 있는지 먼저 확인한다. 여기서 실패하면 UI 단정은 의미가 없다.
    const tokenResponse = await request.post(`${backendBaseUrl}/api/v1/meetings/${meetingId}/livekit-token`, {
      headers: authorized(host)
    });
    expect(
      tokenResponse.ok(),
      `LiveKit 토큰 발급 실패(자격증명 확인 필요): ${await tokenResponse.text()}`
    ).toBeTruthy();

    const first = await joinLiveRoom(browser, host, spaceId, meetingId);
    try {
      // 음성 기준선: 혼자 있을 때는 원격 참가자가 없어야 한다. 이 단정이 없으면 뒤의 양성
      // 단정이 "원래부터 떠 있던 것"을 본 것인지 구분할 수 없다.
      await expect(
        first.page.getByText("No other participants"),
        "혼자 있을 때는 원격 참가자가 없어야 한다"
      ).toBeVisible({ timeout: 30_000 });

      const second = await joinLiveRoom(browser, guest, spaceId, meetingId);
      try {
        // 원격 참가자 목록은 `isConnected && !isLocal`로 걸러진다. 즉 여기 보인다는 것은
        // 상대가 실제로 접속해 구독됐다는 뜻이다.
        await expect(
          first.page.getByLabel(`${guest.user.displayName} volume`),
          "호스트가 게스트를 원격 참가자로 봐야 한다"
        ).toBeVisible({ timeout: 30_000 });
        await expect(
          second.page.getByLabel(`${host.user.displayName} volume`),
          "게스트가 호스트를 원격 참가자로 봐야 한다"
        ).toBeVisible({ timeout: 30_000 });
      } finally {
        await second.context.close();
      }
    } finally {
      await first.context.close();
    }
  });
});
