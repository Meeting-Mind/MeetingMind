import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const password = "CiPass123!";
const backendBaseUrl = `http://127.0.0.1:${process.env.PLAYWRIGHT_BACKEND_PORT ?? "8080"}`;

type AuthSession = {
  user: {
    email: string;
  };
};

async function signup(request: APIRequestContext, label: string): Promise<AuthSession> {
  const response = await request.post(`${backendBaseUrl}/api/v1/auth/signup`, {
    data: {
      displayName: `CI ${label}`,
      email: `${label}-${Date.now()}@example.com`,
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

test.beforeEach(async ({ page }) => {
  await page.route("https://accounts.google.com/**", (route) => route.abort());
});

test("protected workspace route redirects to sign-in and restores the requested page", async ({ page, request }) => {
  const session = await signup(request, "login");

  await signIn(page, session);

  await expect(page).toHaveURL(/\/spaces$/);
  await expect(page.getByRole("heading", { name: "Your Spaces" })).toBeVisible();
});

test("creates a project space through the Browser to BFF to Core path", async ({ page, request }) => {
  const session = await signup(request, "space-create");
  const spaceName = `CI Space ${Date.now()}`;

  await signIn(page, session);
  await page.getByTestId("create-space-trigger").click();

  const dialog = page.getByTestId("create-space-dialog");
  await expect(dialog).toBeVisible();
  await dialog.getByTestId("create-space-name").fill(spaceName);
  await dialog.getByTestId("create-space-description").fill("Playwright workspace creation verification");

  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().endsWith("/api/v1/spaces")
  );
  await dialog.getByTestId("create-space-submit").click();
  expect((await responsePromise).ok()).toBeTruthy();

  await expect(dialog).toBeHidden();
  await expect(page.getByText(spaceName, { exact: true })).toBeVisible();
});

test("keeps the create-space dialog open when the request fails", async ({ page, request }) => {
  const session = await signup(request, "space-create-failure");

  await page.route("**/api/v1/spaces", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ message: "Intentional space creation failure" })
      });
      return;
    }
    await route.continue();
  });

  await signIn(page, session);
  await page.getByTestId("create-space-trigger").click();

  const dialog = page.getByTestId("create-space-dialog");
  await dialog.getByTestId("create-space-name").fill("Rejected CI Space");
  await dialog.getByTestId("create-space-submit").click();

  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("alert")).toBeVisible();
  await expect(page.getByText("Rejected CI Space", { exact: true })).toHaveCount(0);
});
