# Editor

The visual workflow editor at `/workflows/[id]`. The draft lives in the Zustand store
(`src/stores/editor/`); server data comes from the workflow hooks in `features/workflows/hooks/`.

## How a node gets from the palette to the saved definition

1. `components/node-palette.tsx` adds a node (click or drag onto `components/editor-canvas.tsx`) through
   `useEditorStore().addNode`. The defaults come from `lib/node-catalog.ts`. Graph rules such as one trigger,
   no cycles and true/false handles on conditions live in `src/stores/editor/graph-rules.ts`.
2. Selecting the node opens `components/node-config-panel.tsx` inside `components/side-panel.tsx`.
3. `components/config-forms/forms/type-config-form.tsx` picks the form for the node type. Each form uses
   `hooks/use-config-form.ts` (react-hook-form + the Zod schema from `src/schemas/node-forms/`). Valid edits go
   straight back to the store through `hooks/use-live-sync.ts`.
4. Save, validate and publish (`hooks/use-editor-actions.ts`) call `toDefinition()` on the store, which uses
   `flowToDefinition` from `lib/flow-mapping.ts`, and send the result to the API. Loading goes the other way:
   the store's `load` uses `definitionToFlow`, and nodes without a position are placed with `lib/auto-layout.ts`.

## Folders

```
editor-page.tsx              loads the workflow into the store, then renders the workspace
hooks/                       save/validate/publish, keyboard shortcut + unsaved-changes guard, last-run overlay, template references
lib/                         flow-mapping (definition <-> React Flow), auto-layout (dagre), node catalog, flow types, template references
components/
  editor-workspace.tsx       layout: toolbar, palette, canvas, side panel, dialogs and sheets
  editor-toolbar.tsx         name, save state, layout, validate, save, publish, run, sheet menu
  editor-canvas.tsx          React Flow canvas
  node-palette.tsx           node types to add
  side-panel.tsx             node config, edge details or workflow settings
  nodes/                     canvas node rendering (shell, subtitle, decorations context, node types map)
  config-forms/
    fields/                  form controls (template field with reference picker, JSON, key/value, lists, selects, timezone)
    forms/                   one form per node type + the type switch
    hooks/                   form wiring and live sync to the store
  panels/                    run and conflict dialogs, validation panel, versions/sharing/triggers sheets
    triggers/                webhook and schedule sections, deliveries list, new-secret dialog, signature example
```
