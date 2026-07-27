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

test("refreshes the Google callback when the sign-in page remounts", async ({ page }) => {
  await page.addInitScript(() => {
    const browserWindow = window as Window & {
      google: {
        accounts: {
          id: {
            initialize: (config: {
              callback: (response: { credential?: string }) => void;
            }) => void;
            renderButton: (element: HTMLElement) => void;
          };
        };
      };
      meetingMindGoogleCallback?: (response: { credential?: string }) => void;
      meetingMindGoogleInitializeCount?: number;
    };
    browserWindow.meetingMindGoogleInitializeCount = 0;
    browserWindow.google = {
      accounts: {
        id: {
          initialize: (config) => {
            browserWindow.meetingMindGoogleInitializeCount =
              (browserWindow.meetingMindGoogleInitializeCount ?? 0) + 1;
            browserWindow.meetingMindGoogleCallback = config.callback;
          },
          renderButton: (element) => {
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = "Mock Google sign-in";
            button.addEventListener("click", () => {
              browserWindow.meetingMindGoogleCallback?.({ credential: "mock-google-credential" });
            });
            element.appendChild(button);
          }
        }
      }
    };
  });

  await page.route("**/api/v1/auth/session", (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ authenticated: false, user: null, session: null })
  }));
  await page.route("**/api/v1/auth/csrf", (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ token: "mock-csrf", headerName: "X-CSRF-TOKEN" })
  }));

  let googleRequestCount = 0;
  let releaseFirstGoogleRequest!: () => void;
  let markFirstGoogleRequestSeen!: () => void;
  let markFirstGoogleRequestDone!: () => void;
  const firstGoogleRequestReleased = new Promise<void>((resolve) => {
    releaseFirstGoogleRequest = resolve;
  });
  const firstGoogleRequestSeen = new Promise<void>((resolve) => {
    markFirstGoogleRequestSeen = resolve;
  });
  const firstGoogleRequestDone = new Promise<void>((resolve) => {
    markFirstGoogleRequestDone = resolve;
  });
  await page.route("**/api/v1/auth/google", async (route) => {
    googleRequestCount += 1;
    if (googleRequestCount === 1) {
      markFirstGoogleRequestSeen();
      await firstGoogleRequestReleased;
    }
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({ code: "GOOGLE_CREDENTIAL_INVALID", message: "Mock credential rejected" })
    });
    if (googleRequestCount === 1) {
      markFirstGoogleRequestDone();
    }
  });

  await page.goto("/login");
  await page.getByRole("button", { name: "Mock Google sign-in" }).click();
  await firstGoogleRequestSeen;

  await page.evaluate(() => {
    window.history.pushState({}, "", "/");
    window.dispatchEvent(new PopStateEvent("popstate"));
  });
  await expect(page.getByTestId("sign-in-page")).toBeHidden();
  releaseFirstGoogleRequest();
  await firstGoogleRequestDone;

  await page.evaluate(() => {
    window.history.pushState({}, "", "/login");
    window.dispatchEvent(new PopStateEvent("popstate"));
  });
  await expect(page.getByTestId("sign-in-page")).toBeVisible();
  await page.getByRole("button", { name: "Mock Google sign-in" }).click();

  await expect(page.getByTestId("sign-in-error")).toHaveText("Mock credential rejected");
  expect(googleRequestCount).toBe(2);
  await expect.poll(() => page.evaluate(() => (
    window as Window & { meetingMindGoogleInitializeCount?: number }
  ).meetingMindGoogleInitializeCount)).toBe(1);
});
