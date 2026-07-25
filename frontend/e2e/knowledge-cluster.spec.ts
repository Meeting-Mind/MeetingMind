import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

/**
 * 묶어보기 버튼이 화면에 뜨고 눌러도 깨지지 않는지 확인한다.
 *
 * 노드가 없는 환경(e2e에는 AI 서버와 색인 worker가 없다)에서도 버튼 자체는
 * 있어야 하고, 눌렀을 때 런타임 오류가 나면 안 된다. 실제 모션은 계산 규칙을
 * clustering.test.ts가 따로 고정한다.
 */

const password = "CiPass123!";
const backendBaseUrl = `http://127.0.0.1:${process.env.PLAYWRIGHT_BACKEND_PORT ?? "8080"}`;

type AuthSession = { accessToken: string; user: { id: string; email: string } };

async function signup(request: APIRequestContext, label: string): Promise<AuthSession> {
  const response = await request.post(`${backendBaseUrl}/api/v1/auth/signup`, {
    data: { displayName: `CI ${label}`, email: `${label}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`, password }
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as AuthSession;
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

test("묶어보기 버튼이 동작한다", async ({ page, request }) => {
  await page.route("https://accounts.google.com/**", (route) => route.abort());
  const owner = await signup(request, "cluster");
  const space = await (await request.post(`${backendBaseUrl}/api/v1/spaces`, {
    headers: { Authorization: `Bearer ${owner.accessToken}` },
    data: { name: `묶음 ${Date.now()}`, description: "x" }
  })).json();

  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));

  await signIn(page, owner, `/spaces/${space.id}/knowledge`);
  await page.goto(`/spaces/${space.id}/knowledge`);

  const button = page.getByRole("button", { name: "묶어보기" });
  await expect(button).toBeVisible({ timeout: 15000 });
  await expect(button).toHaveAttribute("aria-pressed", "false");

  await button.click();
  // 눌린 상태가 되고 문구가 바뀐다.
  const release = page.getByRole("button", { name: "묶음 해제" });
  await expect(release).toBeVisible();
  await expect(release).toHaveAttribute("aria-pressed", "true");
  // 묶은 동안에는 재배치를 막는다. 힘이 덩어리를 흩어 놓기 때문이다.
  await expect(page.getByRole("button", { name: "재배치" })).toBeDisabled();

  await release.click();
  await expect(page.getByRole("button", { name: "묶어보기" })).toBeVisible();
  await expect(page.getByRole("button", { name: "재배치" })).toBeEnabled();

  await page.waitForTimeout(1500);
  expect(errors, `런타임 오류: ${errors.join(" / ")}`).toEqual([]);
});
