import { defineConfig, devices } from "@playwright/test";

const backendPort = process.env.PLAYWRIGHT_BACKEND_PORT ?? "8080";
const bffPort = process.env.PLAYWRIGHT_BFF_PORT ?? "8081";
const frontendPort = process.env.PLAYWRIGHT_FRONTEND_PORT ?? "5173";

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
      command: "./gradlew bootRun",
      cwd: "../bff",
      env: {
        BFF_SERVER_PORT: bffPort,
        BFF_REDIS_HOST: "127.0.0.1",
        BFF_REDIS_PORT: process.env.BFF_REDIS_PORT ?? "6379",
        BFF_BACKEND_BASE_URL: `http://127.0.0.1:${backendPort}`,
        BFF_TOKEN_VAULT_LOCAL_KEY_BASE64: "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        SPRING_PROFILES_ACTIVE: "local"
      },
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      url: `http://127.0.0.1:${bffPort}/actuator/health/readiness`
    },
    {
      command: `npm run dev -- --host 127.0.0.1 --port ${frontendPort}`,
      env: {
        VITE_BFF_PROXY_TARGET: `http://127.0.0.1:${bffPort}`
      },
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
      url: `http://127.0.0.1:${frontendPort}`
    }
  ]
});
