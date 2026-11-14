"use client";

import type Keycloak from "keycloak-js";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { FullPageMessage, FullPageSpinner } from "@/components/full-page";
import { Button } from "@/components/ui/button";
import { initKeycloak } from "./keycloak";
import { setSessionHandlers } from "./session";

export interface AuthUser {
  /** Keycloak subject; the backend uses it as user id. */
  id: string;
  username: string;
  email: string | null;
  name: string | null;
  roles: string[];
}

interface AuthContextValue {
  user: AuthUser;
  logout: () => void;
  /** For components that need raw access (e.g. account console link). */
  keycloak: Keycloak;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function userFromToken(kc: Keycloak): AuthUser {
  const t = kc.tokenParsed ?? {};
  const claims = t as Record<string, unknown>;
  return {
    id: typeof t.sub === "string" ? t.sub : "",
    username: typeof claims.preferred_username === "string" ? claims.preferred_username : "",
    email: typeof claims.email === "string" ? claims.email : null,
    name: typeof claims.name === "string" ? claims.name : null,
    roles: (t.realm_access?.roles ?? []).filter((r) => r === r.toUpperCase()),
  };
}

type State =
  | { status: "loading" }
  | { status: "authenticated"; keycloak: Keycloak; user: AuthUser }
  | { status: "error"; message: string };

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    initKeycloak()
      .then((kc) => {
        if (cancelled) return;
        if (!kc.authenticated) {
          // login-required normally redirects before we get here.
          void kc.login();
          return;
        }
        const relogin = () => {
          void kc.login({ redirectUri: window.location.href });
        };
        setSessionHandlers({
          getAccessToken: async () => {
            try {
              await kc.updateToken(30);
            } catch {
              relogin();
              throw new Error("Session expired, redirecting to login");
            }
            return kc.token;
          },
          onUnauthorized: relogin,
        });
        kc.onAuthRefreshSuccess = () => {
          setState((prev) => (prev.status === "authenticated" ? { ...prev, user: userFromToken(kc) } : prev));
        };
        kc.onTokenExpired = () => {
          kc.updateToken(30).catch(relogin);
        };
        setState({ status: "authenticated", keycloak: kc, user: userFromToken(kc) });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        const message = error instanceof Error ? error.message : "Unknown error";
        setState({ status: "error", message });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const logout = useCallback(() => {
    if (state.status !== "authenticated") return;
    setSessionHandlers(null);
    void state.keycloak.logout({ redirectUri: window.location.origin });
  }, [state]);

  const value = useMemo<AuthContextValue | null>(
    () => (state.status === "authenticated" ? { user: state.user, logout, keycloak: state.keycloak } : null),
    [state, logout],
  );

  if (state.status === "error") {
    return (
      <FullPageMessage
        title="Could not reach the identity provider"
        description={`Sign-in failed: ${state.message}. Check that Keycloak is running and try again.`}
        action={<Button onClick={() => window.location.reload()}>Retry</Button>}
      />
    );
  }
  if (!value) return <FullPageSpinner label="Signing you in…" />;
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}
