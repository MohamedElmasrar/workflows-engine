"use client";
/** Browser-local preferences: auto-layout direction, minimap and default page size. */
import { useId } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { type Preferences, usePreferences } from "@/lib/preferences";

export function PreferencesCard() {
  const [prefs, setPrefs] = usePreferences();
  const directionId = useId();
  const minimapId = useId();
  const pageSizeId = useId();
  return (
    <Card>
      <CardHeader>
        <CardTitle>Preferences</CardTitle>
        <CardDescription>Stored in this browser only (localStorage). They contain no credentials.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <label htmlFor={directionId} className="text-sm">
            Auto-layout direction
          </label>
          <Select value={prefs.layoutDirection} onValueChange={(v) => setPrefs({ layoutDirection: v as Preferences["layoutDirection"] })}>
            <SelectTrigger id={directionId} className="w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="LR">Left to right</SelectItem>
              <SelectItem value="TB">Top to bottom</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center justify-between gap-4">
          <label htmlFor={minimapId} className="text-sm">
            Show minimap in graphs
          </label>
          <Switch id={minimapId} checked={prefs.showMinimap} onCheckedChange={(v) => setPrefs({ showMinimap: v })} />
        </div>
        <div className="flex items-center justify-between gap-4">
          <label htmlFor={pageSizeId} className="text-sm">
            Default page size
          </label>
          <Select value={String(prefs.pageSize)} onValueChange={(v) => setPrefs({ pageSize: Number(v) as Preferences["pageSize"] })}>
            <SelectTrigger id={pageSizeId} className="w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {[10, 20, 50, 100].map((n) => (
                <SelectItem key={n} value={String(n)}>
                  {n} rows
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </CardContent>
    </Card>
  );
}
