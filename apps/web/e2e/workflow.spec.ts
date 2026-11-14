import { expect, test, type Page } from "@playwright/test";

/**
 * Full journey against the real backend (docker compose) and Keycloak.
 * Requires the web app on http://localhost:3000 (npm run dev).
 */

const USER = process.env.E2E_USER ?? "demo";
const PASSWORD = process.env.E2E_PASSWORD ?? "demo";

async function login(page: Page) {
  await page.goto("/dashboard");
  // keycloak-js redirects to the real Keycloak login page (PKCE, login-required).
  await page.waitForURL(/\/realms\/workflow-platform\/protocol\/openid-connect\/auth/);
  await page.locator("#username").fill(USER);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
  await page.waitForURL(/localhost:3000\/dashboard/);
  await expect(page.getByRole("heading", { name: /welcome back/i })).toBeVisible();
}

async function createWorkflow(page: Page, name: string) {
  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "Workflows" }).click();
  await page.waitForURL(/\/workflows$/);
  await page.getByTestId("create-workflow").first().click();
  const dialog = page.getByRole("dialog", { name: "Create workflow" });
  await dialog.getByLabel("Name").fill(name);
  await dialog.getByLabel(/description/i).fill("Created by the Playwright suite");
  await dialog.getByRole("button", { name: /create and open editor/i }).click();
  await page.waitForURL(/\/workflows\/[0-9a-f-]{36}$/);
  await expect(page.locator('.react-flow__node[data-id="trigger"]')).toBeVisible();
}

async function addNode(page: Page, type: string, expectedId: string) {
  await page.getByTestId(`palette-${type}`).click();
  await expect(page.locator(`.react-flow__node[data-id="${expectedId}"]`)).toBeVisible();
}

/** Drags from a node's source handle to another node's target handle with the mouse. */
async function connect(page: Page, from: string, to: string) {
  const edgesBefore = await page.locator(".react-flow__edge").count();
  const source = page.locator(`.react-flow__node[data-id="${from}"] .react-flow__handle.source`).first();
  const target = page.locator(`.react-flow__node[data-id="${to}"] .react-flow__handle.target`).first();
  const s = await source.boundingBox();
  const t = await target.boundingBox();
  if (!s || !t) throw new Error(`handles for ${from} → ${to} not visible`);
  await page.mouse.move(s.x + s.width / 2, s.y + s.height / 2);
  await page.mouse.down();
  await page.mouse.move((s.x + t.x) / 2, (s.y + t.y) / 2, { steps: 8 });
  await page.mouse.move(t.x + t.width / 2, t.y + t.height / 2, { steps: 8 });
  await page.mouse.up();
  await expect(page.locator(".react-flow__edge")).toHaveCount(edgesBefore + 1);
}

async function selectNode(page: Page, id: string) {
  await page.locator(`.react-flow__node[data-id="${id}"]`).click();
  await expect(page.getByTestId("node-config-panel").getByText(`id: ${id}`)).toBeVisible();
}

async function saveValidatePublish(page: Page) {
  await page.getByTestId("save-button").click();
  await expect(page.getByTestId("saved-indicator")).toBeVisible();

  await page.getByTestId("validate-button").click();
  await expect(page.getByTestId("validation-panel")).toContainText("Workflow is valid");

  await page.getByTestId("publish-button").click();
  await expect(page.getByText(/Published version 1/)).toBeVisible();
}

async function run(page: Page, input: string) {
  const runButton = page.getByTestId("run-button");
  await expect(runButton).toBeEnabled({ timeout: 20_000 });
  await runButton.click();
  const dialog = page.getByRole("dialog", { name: "Run workflow" });
  await dialog.getByTestId("run-input").fill(input);
  await dialog.getByTestId("run-submit").click();
  await page.waitForURL(/\/executions\/[0-9a-f-]{36}$/, { timeout: 30_000 });
}

test.describe.configure({ mode: "serial" });

test("design, publish and run a workflow; watch it complete live", async ({ page }) => {
  const name = `E2E happy path ${Date.now()}`;
  await login(page);
  await createWorkflow(page, name);
  await expect(page.getByTestId("palette-MANUAL_TRIGGER")).toBeDisabled(); // single trigger rule

  await addNode(page, "TRANSFORM", "transform_1");
  await addNode(page, "LOG", "log_1");
  await expect(page.getByTestId("dirty-indicator")).toBeVisible();

  await connect(page, "trigger", "transform_1");
  await connect(page, "transform_1", "log_1");

  await selectNode(page, "transform_1");
  await page.getByLabel("Set fields").fill('{"greeting": "Hello {{ trigger.name }}"}');

  await selectNode(page, "log_1");
  const message = page.getByLabel("Message");
  await message.fill("Greeting: {{ nodes.transform_1.output.greeting }}");
  await page.getByLabel("Name", { exact: true }).fill("Log greeting");
  await expect(page.locator('.react-flow__node[data-id="log_1"]')).toContainText("Log greeting");

  await saveValidatePublish(page);
  await run(page, '{"name": "Playwright"}');

  await expect(page.getByTestId("execution-status")).toHaveText("Completed", { timeout: 60_000 });
  const timeline = page.getByTestId("timeline");
  await expect(timeline.getByTestId("timeline-entry").first()).toBeVisible();
  await expect(timeline).toContainText("Execution status changed");
  await expect(page.locator('[data-testid="flow-node-log_1"]')).toHaveAttribute("data-status", "COMPLETED");

  await page.locator('.react-flow__node[data-id="log_1"]').click();
  const details = page.getByTestId("node-run-details");
  await expect(details).toContainText("Greeting: Hello Playwright");
});

test("a failing HTTP request is retried and reported with its attempts", async ({ page }) => {
  const name = `E2E failing HTTP ${Date.now()}`;
  await login(page);
  await createWorkflow(page, name);

  await addNode(page, "HTTP_REQUEST", "http_1");
  await connect(page, "trigger", "http_1");

  await selectNode(page, "http_1");
  await page.getByLabel("URL").fill("http://mock-api:8080/unavailable");
  await page.getByRole("button", { name: /advanced/i }).click();
  await page.getByRole("switch", { name: /custom retry policy/i }).click();
  await page.getByLabel("Max attempts").fill("2");
  await page.getByLabel(/initial interval/i).fill("500");
  await expect(page.locator('.react-flow__node[data-id="http_1"]')).toContainText("mock-api:8080/unavailable");

  await saveValidatePublish(page);
  await run(page, "{}");

  await expect(page.getByTestId("execution-status")).toHaveText("Failed", { timeout: 60_000 });
  await page.locator('.react-flow__node[data-id="http_1"]').click();
  const details = page.getByTestId("node-run-details");
  await expect(details.getByTestId("node-error-code")).toHaveText("HTTP_5XX");
  await expect(details.getByTestId("node-attempt-count")).toHaveText("2");
  await expect(details.getByTestId("attempt-row")).toHaveCount(2);
  await page.getByRole("tab", { name: "Timeline" }).click();
  await expect(page.getByTestId("timeline")).toContainText("Node retry scheduled");
  await expect(page.getByTestId("timeline")).toContainText("HTTP_5XX");
});
