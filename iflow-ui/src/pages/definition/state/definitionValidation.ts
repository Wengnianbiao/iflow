import type { WorkflowDef } from '@/types/workflow'
import type { WorkflowDefinitionMachineContext } from './types'
import type { WorkflowProblem } from '../validation/types'
import { findTaskPath, pointerValue, validateSource } from '../validation/validateWorkflow'

export function draftProblems(context: WorkflowDefinitionMachineContext): WorkflowProblem[] {
  return Object.values(context.fieldDrafts).filter((draft) => draft.message).map((draft) => ({
    id: `DRAFT:${draft.path}`, code: 'FIELD_DRAFT', source: 'draft', severity: 'error',
    path: draft.path, taskPath: draft.taskPath, taskReferenceName: draft.taskPath ? String(pointerValue(context.workflow, `${draft.taskPath}/taskReferenceName`) ?? draft.taskReferenceName) : undefined, message: draft.message,
  }))
}

export function currentProblems(context: WorkflowDefinitionMachineContext) {
  const drafts = draftProblems(context)
  return [...context.problems.filter((problem) => !drafts.some((draft) => problem.path === draft.path || problem.path.startsWith(`${draft.path}/`))), ...drafts]
}

export const blockingProblems = (context: WorkflowDefinitionMachineContext) => currentProblems(context).filter((problem) => problem.severity === 'error').sort((a, b) => Number(Boolean(b.structural)) - Number(Boolean(a.structural)))

/** The raw source is authoritative. Only structurally safe candidates reach forms. */
export function validatedEdit(context: WorkflowDefinitionMachineContext, source: string) {
  const result = validateSource(source)
  const empty: WorkflowDef = { route: context.workflowRoute, name: '', version: context.workflowVersion ?? 1, tasks: [] }
  return {
    source,
    sourceBlocked: !result.workflow,
    workflow: result.workflow ?? context.workflow ?? empty,
    diagramWorkflow: result.diagramSafe && result.workflow ? result.workflow : context.diagramWorkflow ?? empty,
    problems: result.problems,
    selectedTaskPath: result.workflow && context.selectedTaskRef !== undefined ? findTaskPath(result.workflow, context.selectedTaskRef) : context.selectedTaskPath,
    error: null,
  }
}

export function workflowEdit(context: WorkflowDefinitionMachineContext, workflow: WorkflowDef) {
  return validatedEdit(context, JSON.stringify(workflow, null, 2))
}

export function focusProblem(context: WorkflowDefinitionMachineContext, problem: WorkflowProblem, preferredTab?: 'code' | 'task' | 'workflow') {
  const tab: 'code' | 'task' | 'workflow' = context.sourceBlocked || problem.structural || problem.code === 'DUPLICATE_REF'
    ? 'code' : preferredTab ?? (problem.taskPath ? 'task' : 'workflow')
  return {
    activeTab: tab,
    selectedTaskRef: tab === 'task' ? problem.taskReferenceName : context.selectedTaskRef,
    selectedTaskPath: tab === 'task' ? problem.taskPath : context.selectedTaskPath,
    selectedTaskEditorKey: tab === 'task' ? problem.taskPath : context.selectedTaskEditorKey,
    problemFocus: { problem, tab, sequence: (context.problemFocus?.sequence ?? 0) + 1 },
  }
}
