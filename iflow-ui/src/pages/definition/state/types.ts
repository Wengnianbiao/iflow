import type { WorkflowDef, WorkflowTask } from '@/types/workflow'
import type { ActorRefFrom } from 'xstate'
import type { workflowFlowMachine } from './flowMachine'
import type { workflowEditorMachine } from './editorMachine'
import type { workflowValidationMachine } from './validationMachine'
import type { FieldDraft, ProblemFocus, WorkflowProblem } from '../validation/types'

export type InspectorTab = 'workflow' | 'task' | 'code'
export type VersionChoice = number | 'latest'

export type WorkflowRouteAttributes = {
  workflowRoute: string
  workflowVersion?: number
  isNewWorkflow: boolean
  draftId?: string
}

export type LoadedWorkflow = {
  workflow: WorkflowDef
  baseline?: WorkflowDef
  versions: number[]
}

export type WorkflowDefinitionMachineContext = WorkflowRouteAttributes & {
  workflow: WorkflowDef | null
  baseline: WorkflowDef | null
  source: string
  sourceBlocked: boolean
  diagramWorkflow: WorkflowDef | null
  problems: WorkflowProblem[]
  serverProblems: WorkflowProblem[]
  fieldDrafts: Record<string, FieldDraft>
  saveAttempted: boolean
  saveVersion: number | null
  problemFocus: ProblemFocus | null
  versions: number[]
  selectedTaskRef?: string
  selectedTaskPath?: string
  selectedTaskEditorKey?: string
  activeTab: InspectorTab
  inspectorWidth: number
  pendingVersion: VersionChoice | null
  deleteConfirmationValue: string
  deleteError: string | null
  error: string | null
  flowActor?: ActorRefFrom<typeof workflowFlowMachine>
  editorActor?: ActorRefFrom<typeof workflowEditorMachine>
  validationActor?: ActorRefFrom<typeof workflowValidationMachine>
}

export type WorkflowDefinitionMachineEvent =
  | ({ type: 'INITIALIZE' } & WorkflowRouteAttributes)
  | { type: 'RETRY' }
  | { type: 'WORKFLOW_CHANGED'; workflow: WorkflowDef }
  | { type: 'SOURCE_CHANGED'; source: string }
  | { type: 'FIELD_DRAFT_CHANGED'; path: string; draft: FieldDraft | null }
  | { type: 'FOCUS_PROBLEM'; problem: WorkflowProblem; tab?: 'code' | 'task' | 'workflow' }
  | { type: 'TASK_SELECTED'; task?: WorkflowTask }
  | { type: 'TASK_UPDATED'; task: WorkflowTask }
  | { type: 'CHANGE_TAB'; tab: InspectorTab }
  | { type: 'OPEN_PANEL' }
  | { type: 'CLOSE_PANEL' }
  | { type: 'RESIZE_PANEL'; width: number }
  | { type: 'REQUEST_SAVE'; saveCurrentVersion?: boolean }
  | { type: 'SAVE_BEFORE_LEAVE' }
  | { type: 'CONFIRM_SAVE' }
  | { type: 'REQUEST_RESET' }
  | { type: 'CONFIRM_RESET' }
  | { type: 'REQUEST_DELETE' }
  | { type: 'UPDATE_DELETE_CONFIRMATION'; value: string }
  | { type: 'CONFIRM_DELETE' }
  | { type: 'REQUEST_VERSION_CHANGE'; version: VersionChoice }
  | { type: 'CANCEL_DIALOG' }
  | { type: 'CLEAR_ERROR' }
  | { type: 'FLOW_RENDERED'; revision: number }

export function getWorkflowToSave({ workflow, saveVersion }: Pick<WorkflowDefinitionMachineContext, 'workflow' | 'saveVersion'>) {
  return workflow && saveVersion != null ? { ...workflow, version: saveVersion } : workflow
}

export function hasWorkflowChanges(context: WorkflowDefinitionMachineContext) {
  return Boolean(
    context.workflow
      && context.baseline
      && (context.sourceBlocked || Object.values(context.fieldDrafts).some((draft) => Boolean(draft.message))
        || JSON.stringify(context.workflow) !== JSON.stringify(context.baseline)),
  )
}
