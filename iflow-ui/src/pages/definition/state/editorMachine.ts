import { assign, createMachine, sendParent } from 'xstate'
import { buildVariableOptions, findTask } from '@/flow/workflowEditor'
import type { WorkflowDef, WorkflowTask } from '@/types/workflow'
import type { InspectorTab } from './types'

export type WorkflowEditorMachineContext = {
  workflow: WorkflowDef | null
  selectedTaskRef?: string
  selectedTask?: WorkflowTask
  selectedTaskEditorKey?: string
  variableOptions: string[]
}

export type WorkflowEditorMachineEvent =
  | {
      type: 'SYNC'
      workflow: WorkflowDef
      selectedTaskRef?: string
      selectedTaskEditorKey?: string
      activeTab: InspectorTab
    }
  | { type: 'SELECT_TASK'; task?: WorkflowTask }
  | { type: 'CHANGE_TAB'; tab: InspectorTab }
  | { type: 'TASK_UPDATED'; task: WorkflowTask }
  | { type: 'WORKFLOW_CHANGED'; workflow: WorkflowDef }

export const workflowEditorMachine = createMachine<WorkflowEditorMachineContext, WorkflowEditorMachineEvent>(
  {
    predictableActionArguments: true,
    id: 'workflowEditorPanel',
    initial: 'workflow',
    context: {
      workflow: null,
      selectedTaskRef: undefined,
      selectedTask: undefined,
      selectedTaskEditorKey: undefined,
      variableOptions: [],
    },
    on: {
      SYNC: [
        { cond: 'syncsTaskTab', target: '.task', actions: 'assignDerivedState' },
        { cond: 'syncsCodeTab', target: '.code', actions: 'assignDerivedState' },
        { target: '.workflow', actions: 'assignDerivedState' },
      ],
      SELECT_TASK: { actions: 'notifySelection' },
      CHANGE_TAB: { actions: 'notifyTabChange' },
      TASK_UPDATED: { actions: 'notifyTaskUpdate' },
      WORKFLOW_CHANGED: { actions: 'notifyWorkflowChange' },
    },
    states: {
      workflow: {},
      task: {},
      code: {},
    },
  },
  {
    actions: {
      assignDerivedState: assign((context, event) => {
        if (event.type !== 'SYNC') return context
        return {
          ...context,
          workflow: event.workflow,
          selectedTaskRef: event.selectedTaskRef,
          selectedTask: event.selectedTaskRef
            ? findTask(event.workflow.tasks, event.selectedTaskRef)
            : undefined,
          selectedTaskEditorKey: event.selectedTaskEditorKey,
          variableOptions: buildVariableOptions(event.workflow),
        }
      }),
      notifySelection: sendParent((_context, event) => event.type === 'SELECT_TASK'
        ? { type: 'TASK_SELECTED', task: event.task }
        : { type: 'TASK_SELECTED', task: undefined }),
      notifyTabChange: sendParent((_context, event) => event.type === 'CHANGE_TAB'
        ? { type: 'CHANGE_TAB', tab: event.tab }
        : { type: 'CHANGE_TAB', tab: 'workflow' }),
      notifyTaskUpdate: sendParent((_context, event) => event.type === 'TASK_UPDATED'
        ? { type: 'TASK_UPDATED', task: event.task }
        : { type: 'TASK_UPDATED' }),
      notifyWorkflowChange: sendParent((_context, event) => event.type === 'WORKFLOW_CHANGED'
        ? { type: 'WORKFLOW_CHANGED', workflow: event.workflow }
        : { type: 'WORKFLOW_CHANGED' }),
    },
    guards: {
      syncsTaskTab: (_context, event) => event.type === 'SYNC' && event.activeTab === 'task',
      syncsCodeTab: (_context, event) => event.type === 'SYNC' && event.activeTab === 'code',
    },
  },
)
