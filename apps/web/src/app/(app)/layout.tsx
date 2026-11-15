/** Layout for the signed-in pages: wraps them in the app shell. */
import type { ReactNode } from "react";
import { AppShell } from "@/components/layout/app-shell";

export default function AppLayout({ children }: { children: ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
