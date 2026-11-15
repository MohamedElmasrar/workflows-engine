# Web app

The web app for the project: dashboard, workflow list, visual editor (React Flow), execution
monitoring with live updates, execution history and settings. Every screen talks to the real API gateway.

Stack: Next.js 16 (App Router, Turbopack) · React 19 · TypeScript (strict) · Tailwind CSS 4 · shadcn/ui (Radix) ·
`@xyflow/react` 12 · TanStack Query 5 · Zustand 5 · React Hook Form 7 + Zod 4 · keycloak-js 26 · `@dagrejs/dagre` ·
Vitest 5 + Testing Library · Playwright · ESLint 10.

Node 24 or newer is required (Vitest 5 and jsdom 31 no longer support Node 20); the Docker image uses `node:26-alpine`.

## Quick start

```bash
cp .env.example .env.local      # optional, defaults match docker compose
npm install
npm run dev                     # http://localhost:3000 (port 3000 is required by the Keycloak client and CORS)
```

The backend (`docker compose up` at the repo root) must be running. Log in with `demo/demo`, `alice/alice`,
`bob/bob` or `admin/admin`.

## Scripts

| Script | What it does |
|---|---|
| `npm run dev` | Dev server on port 3000 |
| `npm run build` / `npm start` | Production build (`output: "standalone"`) / serve it on port 3000 |
| `npm run lint` | ESLint (`eslint-config-next` core-web-vitals + TypeScript) |
| `npm run typecheck` | `tsc --noEmit` |
| `npm test` | Vitest unit and component tests (jsdom) |
| `npm run test:e2e` | Playwright (Chromium) against `http://localhost:3000` and the running backend. Run `npx playwright install chromium` once. Set `E2E_START_SERVER=1` to let Playwright start `npm run dev`. |

## Environment

All values are public and inlined at build time (`NEXT_PUBLIC_*`); for Docker they are build args.

| Variable | Default |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` |
| `NEXT_PUBLIC_KEYCLOAK_URL` | `http://localhost:8180` |
| `NEXT_PUBLIC_KEYCLOAK_REALM` | `workflow-platform` |
| `NEXT_PUBLIC_KEYCLOAK_CLIENT_ID` | `workflow-web` |

## Structure

```
src/
  app/                    thin routes: (app)/dashboard, workflows, workflows/[id], executions, executions/[id], settings; /healthz
  components/             app shell (sidebar + top bar), status badges, JSON viewer, states, confirm dialog, pagination, relative time
  components/ui/          shadcn/ui components (generated with the shadcn CLI)
  features/
    dashboard/            stats cards + recent activity
    workflows/            list page, create dialog, TanStack Query hooks for workflows/versions/triggers/permissions
    editor/               editor page, canvas, palette, custom nodes, side panel, config forms, sheets/dialogs,
                          flow-mapping.ts (definitionToFlow / flowToDefinition), auto-layout.ts (dagre)
    executions/           list + detail pages, read-only graph, node run details, live hooks (SSE)
    settings/             account, preferences, trigger overview
  lib/
    api/                  apiFetch + ApiError (RFC 9457), endpoint functions, domain types, query keys
    auth/                 Keycloak init, AuthProvider, session bridge (token getter for non-React code)
    sse.ts                SSE-over-fetch parser and reconnecting client
    preferences.ts        localStorage UI preferences (no secrets)
  schemas/                Zod mirrors of workflow-definition.v1.json + form schemas/converters
  stores/editor-store.ts  Zustand store for the editable draft
e2e/                      Playwright specs
```

## Authentication

`AuthProvider` initialises keycloak-js once with `onLoad: "login-required"`, `pkceMethod: "S256"` and
`checkLoginIframe: false`. Until the user is authenticated only a loading screen renders. Tokens stay in the
keycloak-js instance in memory; the app never writes tokens to localStorage, sessionStorage or cookies.
(keycloak-js itself briefly keeps the OAuth `state`/PKCE verifier in localStorage during the redirect; no tokens.)

`apiFetch` calls `getAccessToken()` before each request, which runs `keycloak.updateToken(30)`. A failed refresh or a
401 from the API triggers `keycloak.login()`. Logout calls `keycloak.logout({ redirectUri: origin })`.
Every request carries `X-Correlation-Id`; error UIs show the correlation id returned by the gateway.

## Live execution updates (SSE)

`EventSource` cannot send `Authorization`, so `src/lib/sse.ts` reads `text/event-stream` with `fetch` and a
`ReadableStream`. The parser handles `id:`, `event:`, `data:` (multi-line), `retry:`, comments and chunk boundaries
(including split CRLF). `connectSse` reconnects with exponential backoff (500 ms doubling, max 15 s) and sends
`Last-Event-ID` with the last sequence seen, so the backend replays exactly the missed transitions. 401/403/404 stop
the stream.

The execution page loads the history with `GET /executions/{id}/events`, then streams
`/events/stream?after=<last sequence>`. Each `transition` is merged into the cached timeline (deduplicated by
sequence) and triggers a throttled (500 ms) refetch of the execution. `end` closes the stream. Only CORS-allowed
request headers are sent (`Authorization`, `Last-Event-ID`).

## Editor model

The draft lives in the Zustand store (`nodes`, `edges`, selection, `meta` with name/description/settings, dirty
tracking via a change counter, validation issues, read-only flag). Server data (workflow, versions, triggers,
permissions, executions) stays in TanStack Query. `definitionToFlow` and `flowToDefinition` are pure and round-trip
`scripts/order-webhook-demo.json` exactly (unit tested). Config forms validate with Zod schemas that mirror the JSON
schema and write to the store only when valid; empty optional fields are omitted.

Rules enforced client-side: one trigger per workflow (palette offers "replace trigger"), no self-loops, duplicate
edges, cycles or edges into a trigger; edges leaving a CONDITION must use the `true`/`false` handle. Node ids follow
`<prefix>_<n>` and edge ids `e_<n>`.

## Rendering model

The authenticated UI is a client component tree. `cacheComponents`/Partial Prefetching are left disabled: nothing
is rendered on the server per user, and the Activity-based route preservation of Cache Components would keep a hidden
editor mounted against the singleton editor store.

## Docker

```bash
docker build -t workflow-platform/web:dev apps/web \
  --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
docker run -p 3000:3000 workflow-platform/web:dev
```

Multi-stage on `node:26-alpine`, runs as the non-root `nextjs` user, health check `wget http://127.0.0.1:3000/healthz`.

## Known limitations

- No integration credentials store exists in the backend; header values (e.g. API keys) are part of the definition.
- The node palette is hidden below the `md` breakpoint, so graphs can be viewed but nodes cannot be added on phones.
- The workflow filter on the execution history lists the first 100 workflows (API page size limit).
- Leaving the editor through in-app navigation with unsaved changes is not blocked (only tab close/reload warns).
