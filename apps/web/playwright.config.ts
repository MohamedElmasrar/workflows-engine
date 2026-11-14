import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests run against the real backend (docker compose) and a web app on
 * http://localhost:3000 (`npm run dev` or `npm start`). Set E2E_START_SERVER=1 to let
 * Playwright start `npm run dev` itself.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    viewport: { width: 1440, height: 900 },
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } } }],
  webServer: process.env.E2E_START_SERVER
    ? { command: "npm run dev", url: "http://localhost:3000/healthz", reuseExistingServer: true, timeout: 120_000 }
    : undefined,
});
