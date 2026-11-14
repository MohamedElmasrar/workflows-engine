/**
 * Bridge between the Keycloak session (owned by AuthProvider) and code that needs a token
 * outside React, such as `apiFetch` and the SSE client. Tokens never leave memory.
 */
export interface SessionHandlers {
  /** Returns a fresh access token (refreshing it when it expires within 30 s). */
  getAccessToken: () => Promise<string | undefined>;
  /** Called when the API rejects the token (401) or a refresh fails. */
  onUnauthorized: () => void;
}

let handlers: SessionHandlers | null = null;

export function setSessionHandlers(next: SessionHandlers | null): void {
  handlers = next;
}

export async function getAccessToken(): Promise<string | undefined> {
  return handlers ? handlers.getAccessToken() : undefined;
}

export function notifyUnauthorized(): void {
  handlers?.onUnauthorized();
}
