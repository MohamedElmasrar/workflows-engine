import type Keycloak from "keycloak-js";
import { env } from "@/lib/env";

let instance: Keycloak | null = null;
let initPromise: Promise<boolean> | null = null;

/**
 * Lazily creates the single Keycloak instance and runs `init` exactly once (React Strict Mode
 * mounts effects twice; keycloak-js throws if initialised twice). Browser only.
 */
export async function initKeycloak(): Promise<Keycloak> {
  if (!initPromise) {
    initPromise = (async () => {
      const { default: KeycloakCtor } = await import("keycloak-js");
      instance = new KeycloakCtor({
        url: env.keycloakUrl,
        realm: env.keycloakRealm,
        clientId: env.keycloakClientId,
      });
      return instance.init({
        onLoad: "login-required",
        pkceMethod: "S256",
        checkLoginIframe: false,
        // Tokens are kept in memory by keycloak-js; nothing is persisted by the app.
      });
    })();
  }
  await initPromise;
  if (!instance) throw new Error("Keycloak failed to initialise");
  return instance;
}

export function getKeycloak(): Keycloak | null {
  return instance;
}
