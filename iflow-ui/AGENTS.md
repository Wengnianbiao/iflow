# iFlow UI Agent Instructions

Frontend rules for `iflow-ui`. See root `AGENTS.md` for repo-wide context.

## Core Rule

`iflow-ui` must track Netflix Conductor UI behavior and styling. When changing workflow definition, workflow execution, canvas, task cards, task inspector panels, toolbar, sidebars, routing, or interaction behavior, treat `conductor-3.30.2/ui-next` as the primary reference implementation.

## Required Workflow For UI Changes

1. Inspect the relevant Conductor source before editing iFlow.
   - Conductor source root: `conductor-3.30.2/ui-next/src`
   - iFlow UI root: `iflow-ui/src`
2. Compare both implementation and rendered DOM/CSS behavior.
3. Preserve Conductor layout, spacing, card shapes, labels, icons, tabs, side panels, and interaction order unless the user explicitly asks for a product-specific deviation.
4. Keep visible iFlow text Chinese where the existing iFlow UI is Chinese, but keep Conductor technical labels such as task types (`INLINE`, `HTTP`, `SWITCH`, `JSON JQ`) in their original technical form.
5. Verify changes in the browser:
   - UI dev server: `http://127.0.0.1:5174` (`pnpm dev`)
   - API (proxied): `IFLOW_API_TARGET` or `http://127.0.0.1:8099`
6. Run at least `pnpm exec tsc --noEmit` in `iflow-ui`; run `pnpm build` for broader UI changes.

## Common Conductor Files To Check

- Flow theme and node sizes: `components/features/flow/theme.ts`
- Card visual rules: `components/features/flow/components/shapes/TaskCard/TaskCard.tsx`
- Task labels/icons/content: `components/features/flow/components/shapes/TaskCard/`
- Switch/decision nodes: `components/features/flow/components/shapes/DecisionOperator.tsx`
- Canvas toolbar: `components/features/flow/components/graphs/PanAndZoomWrapper/ZoomControls.tsx`
- Task editor forms: `pages/definition/EditorPanel/TaskFormTab/forms/`
- Inline task editor: `pages/definition/EditorPanel/TaskFormTab/forms/INLINETaskForm/`
- Execution detail layout: `pages/execution/` and related `components/features/execution/`

Paths above are relative to `conductor-3.30.2/ui-next/src` (and the mirrored tree under `iflow-ui/src` where it exists).

## Important UI Parity Notes

- Canvas task cards should match Conductor card dimensions, shadows, technical labels, icon placement, and selected/status styling.
- INLINE task cards on the canvas show a short highlighted code preview only; they must not become scrollable editors.
- INLINE task editor panels should expose Script Parameters and Script Code like Conductor, using `inputParameters.expression` and `inputParameters.evaluatorType`.
- HTTP task cards should use Conductor's link icon, method chip, and single-line URI style.
- SWITCH/DECISION nodes should use Conductor's star/diamond operator shape and switch add control positioning.
- Collapsed editor panels and canvas toolbars should be positioned relative to the current canvas area, not the global page header.

## Guardrails

- Do not invent a new design language for workflow/canvas/editor UI.
- Do not replace Conductor-like dense operational UI with marketing-style or decorative layouts.
- Keep changes scoped to the requested parity gap unless the user asks for a wider cleanup.
