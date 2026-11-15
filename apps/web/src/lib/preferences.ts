"use client";
/** UI preferences stored in localStorage, with a hook that keeps tabs in sync. */
import { useCallback, useSyncExternalStore } from "react";

/** Non-sensitive UI preferences persisted in localStorage. Never store tokens or secrets here. */
export interface Preferences {
  layoutDirection: "LR" | "TB";
  showMinimap: boolean;
  pageSize: 10 | 20 | 50 | 100;
}

export const DEFAULT_PREFERENCES: Preferences = { layoutDirection: "LR", showMinimap: true, pageSize: 20 };
const KEY = "workflow-platform.preferences.v1";
const listeners = new Set<() => void>();
let cache: Preferences | null = null;

function read(): Preferences {
  if (cache) return cache;
  if (typeof window === "undefined") return DEFAULT_PREFERENCES;
  try {
    const raw = window.localStorage.getItem(KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : {};
    const p = (typeof parsed === "object" && parsed !== null ? parsed : {}) as Partial<Record<keyof Preferences, unknown>>;
    cache = {
      layoutDirection: p.layoutDirection === "TB" ? "TB" : "LR",
      showMinimap: typeof p.showMinimap === "boolean" ? p.showMinimap : DEFAULT_PREFERENCES.showMinimap,
      pageSize: [10, 20, 50, 100].includes(Number(p.pageSize)) ? (Number(p.pageSize) as Preferences["pageSize"]) : 20,
    };
  } catch {
    cache = DEFAULT_PREFERENCES;
  }
  return cache;
}

export function getPreferences(): Preferences {
  return read();
}

export function setPreferences(patch: Partial<Preferences>): void {
  cache = { ...read(), ...patch };
  try {
    window.localStorage.setItem(KEY, JSON.stringify(cache));
  } catch {
    // Storage may be unavailable (private mode); keep the in-memory value.
  }
  listeners.forEach((l) => l());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key === KEY) {
      cache = null;
      listener();
    }
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

export function usePreferences(): [Preferences, (patch: Partial<Preferences>) => void] {
  const prefs = useSyncExternalStore(subscribe, read, () => DEFAULT_PREFERENCES);
  const update = useCallback((patch: Partial<Preferences>) => setPreferences(patch), []);
  return [prefs, update];
}
