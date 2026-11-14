export const dynamic = "force-static";

/** Liveness probe used by the container HEALTHCHECK (no auth, no backend calls). */
export function GET() {
  return new Response("ok", { headers: { "content-type": "text/plain" } });
}
