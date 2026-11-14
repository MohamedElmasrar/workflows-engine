/**
 * Public configuration. `process.env.NEXT_PUBLIC_*` references must stay literal so that
 * Next.js can inline them into the client bundle at build time.
 */
export const env = {
  apiBaseUrl: (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080").replace(/\/+$/, ""),
  keycloakUrl: (process.env.NEXT_PUBLIC_KEYCLOAK_URL ?? "http://localhost:8180").replace(/\/+$/, ""),
  keycloakRealm: process.env.NEXT_PUBLIC_KEYCLOAK_REALM ?? "workflow-platform",
  keycloakClientId: process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? "workflow-web",
} as const;
