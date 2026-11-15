/** Dashboard number card with an icon; links to the matching filtered list when `href` is set. */
import Link from "next/link";
import type { ReactNode } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export function StatCard({ title, value, icon, loading, href, tone }: {
  title: string;
  value: number | undefined;
  icon: ReactNode;
  loading: boolean;
  href?: string;
  tone?: string;
}) {
  const body = (
    <Card className="h-full transition-colors hover:border-primary/40">
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
        <span className={tone ?? "text-muted-foreground"} aria-hidden>
          {icon}
        </span>
      </CardHeader>
      <CardContent>
        {loading ? <Skeleton className="h-8 w-16" /> : <p className="text-3xl font-semibold tabular-nums" data-testid={`stat-${title}`}>{value ?? "—"}</p>}
      </CardContent>
    </Card>
  );
  return href ? (
    <Link href={href} className="rounded-xl focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none" aria-label={`${title}: ${value ?? ""}`}>
      {body}
    </Link>
  ) : (
    body
  );
}
