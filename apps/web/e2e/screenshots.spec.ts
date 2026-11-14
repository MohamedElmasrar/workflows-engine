import { expect, test } from "@playwright/test";

/**
 * Captures README screenshots from the running environment. Opt-in: SCREENSHOTS=1 npx playwright test screenshots
 * Requires at least one run of scripts/smoke-test.sh so the demo workflow and executions exist.
 */
test.skip(!process.env.SCREENSHOTS, "set SCREENSHOTS=1 to capture documentation screenshots");
test.use({ viewport: { width: 1440, height: 900 } });

const OUT = "../../docs/screenshots";

test("capture documentation screenshots", async ({ page }) => {
  await page.goto("/dashboard");
  await page.waitForURL(/\/realms\/workflow-platform\/protocol\/openid-connect\/auth/);
  await page.locator("#username").fill("demo");
  await page.locator("#password").fill("demo");
  await page.locator("#kc-login").click();
  await page.waitForURL(/localhost:3000\/dashboard/);
  await page.waitForLoadState("networkidle");
  await page.screenshot({ path: `${OUT}/dashboard.png` });

  await page.goto("/workflows");
  await page.waitForLoadState("networkidle");
  await page.screenshot({ path: `${OUT}/workflows.png` });

  await page.goto("/workflows?search=Order%20fraud");
  await page.getByText("Order fraud check (smoke)").first().click();
  await page.waitForURL(/\/workflows\/[0-9a-f-]{36}$/);
  await expect(page.locator('.react-flow__node[data-id="risky"]')).toBeVisible();
  await page.locator('.react-flow__node[data-id="score"]').click();
  await page.waitForTimeout(500);
  await page.screenshot({ path: `${OUT}/editor.png` });

  await page.goto("/executions");
  await page.waitForLoadState("networkidle");
  await page.screenshot({ path: `${OUT}/executions.png` });
  await page.getByText("Order fraud check (smoke)").first().click();
  await page.waitForURL(/\/executions\/[0-9a-f-]{36}$/);
  await expect(page.locator('.react-flow__node[data-id="risky"]')).toBeVisible();
  await page.locator('.react-flow__node[data-id="score"]').click();
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${OUT}/execution-detail.png`, fullPage: true });
});
