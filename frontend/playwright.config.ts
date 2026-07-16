import { defineConfig, devices } from "@playwright/test";

const backendPort = process.env.PLAYWRIGHT_BACKEND_PORT ?? "8080";
const frontendPort = process.env.PLAYWRIGHT_FRONTEND_PORT ?? "5173";
const backendBaseURL = `http://127.0.0.1:${backendPort}`;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [["line"], ["html", { open: "never" }]],
  use: {
    baseURL: `http://127.0.0.1:${frontendPort}`,
    trace: "on-first-retry",
    screenshot: "only-on-failure"
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ],
  webServer: [
    {
      command: "./gradlew bootRun",
      cwd: "../backend",
      env: {
        MEETINGMIND_JWT_SECRET: "ci-e2e-jwt-secret-not-for-production",
        SERVER_PORT: backendPort,
        SPRING_PROFILES_ACTIVE: "test"
      },
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      url: `http://127.0.0.1:${backendPort}/api/workspace`
    },
    {
      command: `npm run dev -- --host 127.0.0.1 --port ${frontendPort}`,
      env: {
        VITE_API_BASE_URL: backendBaseURL
      },
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
      url: `http://127.0.0.1:${frontendPort}`
    }
  ]
});
