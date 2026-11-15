/** Formatting helpers for dates, durations, enum labels and ids. */
const relativeFormatter = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
const absoluteFormatter = new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "medium" });

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ["year", 365 * 24 * 3600],
  ["month", 30 * 24 * 3600],
  ["week", 7 * 24 * 3600],
  ["day", 24 * 3600],
  ["hour", 3600],
  ["minute", 60],
  ["second", 1],
];

export function formatRelative(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return "—";
  const time = Date.parse(iso);
  if (Number.isNaN(time)) return "—";
  const diffSeconds = Math.round((time - now) / 1000);
  if (Math.abs(diffSeconds) < 10) return "just now";
  for (const [unit, seconds] of UNITS) {
    if (Math.abs(diffSeconds) >= seconds || unit === "second") {
      return relativeFormatter.format(Math.round(diffSeconds / seconds), unit);
    }
  }
  return "—";
}

export function formatAbsolute(iso: string | null | undefined): string {
  if (!iso) return "—";
  const time = Date.parse(iso);
  return Number.isNaN(time) ? "—" : absoluteFormatter.format(time);
}

export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || Number.isNaN(ms)) return "—";
  if (ms < 1000) return `${Math.round(ms)} ms`;
  const seconds = ms / 1000;
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 2 : 1)} s`;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  if (minutes < 60) return `${minutes}m ${rest}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
}

export function formatSeconds(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds <= 0) return "";
  const d = Math.floor(totalSeconds / 86400);
  const h = Math.floor((totalSeconds % 86400) / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = Math.floor(totalSeconds % 60);
  return [d && `${d}d`, h && `${h}h`, m && `${m}m`, s && `${s}s`].filter(Boolean).join(" ");
}

export function humanize(value: string | null | undefined): string {
  if (!value) return "—";
  return value
    .toLowerCase()
    .split("_")
    .map((part, i) => (i === 0 ? part.charAt(0).toUpperCase() + part.slice(1) : part))
    .join(" ");
}

export function shortId(id: string | null | undefined): string {
  return id ? id.slice(0, 8) : "—";
}
