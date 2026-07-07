import type { WorkflowDef, WorkflowTask } from '@/types/workflow'
import type { ActorRefFrom } from 'xstate'
import type { workflowFlowMachine } from './flowMachine'
import type { workflowEditorMachine } from './editorMachine'

export type InspectorTab = 'workflow' | 'task' | 'code'
export type VersionChoice = number | 'latest'

export type WorkflowRouteAttributes = {
  workflowName: string
  workflowVersion?: number
  isNewWorkflow: boolean
}

export type LoadedWorkflow = {
  workflow: WorkflowDef
  versions: number[]
}

export type WorkflowDefinitionMachineContext = WorkflowRouteAttributes & {
  workflow: WorkflowDef | null
  baseline: WorkflowDef | null
  versions: number[]
  selectedTaskRef?: string
  selectedTaskEditorKey?: string
  activeTab: InspectorTab
  inspectorWidth: number
  pendingVersion: VersionChoice | null
  deleteConfirmationValue: string
  deleteError: string | null
  error: string | null
  flowActor?: ActorRefFrom<typeof workflowFlowMachine>
  editorActor?: ActorRefFrom<typeof workflowEditorMachine>
}

export type WorkflowDefinitionMachineEvent =
  | ({ type: 'INITIALIZE' } & WorkflowRouteAttributes)
  | { type: 'RETRY' }
  | { type: 'WORKFLOW_CHANGED'; workflow: WorkflowDef }
  | { type: 'TASK_SELECTED'; task?: WorkflowTask }
  | { type: 'TASK_UPDATED'; task: WorkflowTask }
  | { type: 'CHANGE_TAB'; tab: InspectorTab }
  | { type: 'OPEN_PANEL' }
  | { type: 'CLOSE_PANEL' }
  | { type: 'RESIZE_PANEL'; width: number }
  | { type: 'REQUEST_SAVE' }
  | { type: 'CONFIRM_SAVE' }
  | { type: 'REQUEST_RESET' }
  | { type: 'CONFIRM_RESET' }
  | { type: 'REQUEST_DELETE' }
  | { type: 'UPDATE_DELETE_CONFIRMATION'; value: string }
  | { type: 'CONFIRM_DELETE' }
  | { type: 'REQUEST_VERSION_CHANGE'; version: VersionChoice }
  | { type: 'CONFIRM_VERSION_CHANGE' }
  | { type: 'CANCEL_DIALOG' }
  | { type: 'CLEAR_ERROR' }
  | { type: 'FLOW_RENDERED'; revision: number }

export function hasWorkflowChanges(context: WorkflowDefinitionMachineContext) {
  return Boolean(
    context.workflow
      && context.baseline
      && JSON.stringify(context.workflow) !== JSON.stringify(context.baseline),
  )
}
