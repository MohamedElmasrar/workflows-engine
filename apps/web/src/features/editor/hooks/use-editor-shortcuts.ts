/** Browser-level editor behaviour: Ctrl/Cmd+S saves, and closing the tab with unsaved changes asks first. */
import { useEffect } from "react";
import { useEditorStore } from "@/stores/editor/editor-store";

export function useEditorShortcuts(save: () => Promise<boolean>): void {
  const dirty = useEditorStore((s) => s.dirty);

  useEffect(() => {
    if (!dirty) return;
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        if (!useEditorStore.getState().readOnly) void save();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [save]);
}
