import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TooltipProvider } from "@/components/ui/tooltip";
import type { Page, WorkflowSummary } from "@/lib/api/types";

const replace = vi.fn();
const push = vi.fn();
let search = "status=ACTIVE&sort=name&direction=asc";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push }),
  usePathname: () => "/workflows",
  useSearchParams: () => new URLSearchParams(search),
}));

const listWorkflows = vi.fn();
vi.mock("@/lib/api/endpoints", () => ({
  api: { listWorkflows: (...args: unknown[]) => listWorkflows(...args) },
}));

const { WorkflowListPage } = await import("./workflow-list-page");

function summary(overrides: Partial<WorkflowSummary>): WorkflowSummary {
  return {
    id: "w1",
    name: "Order fraud check",
    description: "Webhook → HTTP → Notification",
    status: "ACTIVE",
    ownerId: "u1",
    role: "OWNER",
    latestVersion: 2,
    hasUnpublishedChanges: false,
    triggerType: "WEBHOOK_TRIGGER",
    nodeCount: 6,
    createdAt: new Date(Date.now() - 3600_000).toISOString(),
    updatedAt: new Date(Date.now() - 120_000).toISOString(),
    ...overrides,
  };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <TooltipProvider>
        <WorkflowListPage />
      </TooltipProvider>
    </QueryClientProvider>,
  );
}

describe("WorkflowListPage", () => {
  beforeEach(() => {
    listWorkflows.mockReset();
    search = "status=ACTIVE&sort=name&direction=asc";
  });

  it("renders rows from the query and passes URL filters to the API", async () => {
    const page: Page<WorkflowSummary> = {
      items: [summary({}), summary({ id: "w2", name: "Nightly report", role: "VIEWER", triggerType: "SCHEDULE_TRIGGER", latestVersion: null, status: "DRAFT", description: null })],
      page: 0,
      size: 20,
      totalItems: 2,
      totalPages: 1,
    };
    listWorkflows.mockResolvedValue(page);
    renderPage();

    const rows = await screen.findAllByTestId("workflow-row");
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByRole("link", { name: "Order fraud check" })).toHaveAttribute("href", "/workflows/w1");
    expect(within(rows[0]).getByText("Webhook")).toBeInTheDocument();
    expect(within(rows[0]).getByText("v2")).toBeInTheDocument();
    expect(within(rows[0]).getByText("Owner")).toBeInTheDocument();
    expect(within(rows[1]).getByText("Schedule")).toBeInTheDocument();
    expect(within(rows[1]).getByText("Viewer")).toBeInTheDocument();
    expect(screen.getByText(/Showing/)).toHaveTextContent("Showing 1–2 of 2");
    expect(listWorkflows).toHaveBeenCalledWith(
      expect.objectContaining({ status: "ACTIVE", sort: "name", direction: "asc", page: 0, size: 20 }),
      expect.anything(),
    );
    expect(screen.getByRole("columnheader", { name: /name/i })).toHaveAttribute("aria-sort", "ascending");
  });

  it("shows an empty state for filters without results", async () => {
    listWorkflows.mockResolvedValue({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    renderPage();
    expect(await screen.findByText("No workflows match your filters")).toBeInTheDocument();
  });

  it("shows the problem detail and correlation id on errors", async () => {
    const { ApiError } = await import("@/lib/api/api-error");
    listWorkflows.mockRejectedValue(new ApiError({ status: 403, title: "Forbidden", detail: "Nope", correlationId: "corr-42" }));
    renderPage();
    expect(await screen.findByText("Nope")).toBeInTheDocument();
    expect(screen.getByText(/corr-42/)).toBeInTheDocument();
  });
});
