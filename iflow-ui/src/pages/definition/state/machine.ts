import { assign, createMachine, spawn } from 'xstate'
import { updateTask } from '@/flow/workflowEditor'
import type { WorkflowDef } from '@/types/workflow'
import {
  hasWorkflowChanges,
  type LoadedWorkflow,
  type WorkflowDefinitionMachineContext,
  type WorkflowDefinitionMachineEvent,
} from './types'
import { workflowFlowMachine } from './flowMachine'
import { workflowEditorMachine } from './editorMachine'

function cloneWorkflow(workflow: WorkflowDef): WorkflowDef {
  return JSON.parse(JSON.stringify(workflow)) as WorkflowDef
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function invokeData(event: unknown) {
  return (event as { data?: unknown }).data
}

const initialContext: WorkflowDefinitionMachineContext = {
  workflowName: '',
  workflowVersion: undefined,
  isNewWorkflow: false,
  draftId: undefined,
  workflow: null,
  baseline: null,
  versions: [],
  selectedTaskRef: undefined,
  selectedTaskEditorKey: undefined,
  activeTab: 'workflow',
  inspectorWidth: 50,
  pendingVersion: null,
  deleteConfirmationValue: '',
  deleteError: null,
  error: null,
  flowActor: undefined,
  editorActor: undefined,
}

export const workflowDefinitionMachine = createMachine<
  WorkflowDefinitionMachineContext,
  WorkflowDefinitionMachineEvent
>(
  {
    predictableActionArguments: true,
    id: 'workflowDefinition',
    initial: 'idle',
    context: initialContext,
    on: {
      INITIALIZE: {
        target: 'loading',
        actions: 'assignRouteAttributes',
      },
    },
    states: {
      idle: {
        entry: 'spawnDerivedActors',
      },
      loading: {
        invoke: {
          id: 'loadWorkflowDefinition',
          src: 'loadWorkflowDefinition',
          onDone: {
            target: 'ready',
            actions: ['assignLoadedWorkflow', 'syncDerivedActors'],
          },
          onError: {
            target: 'loadFailed',
            actions: 'assignLoadError',
          },
        },
      },
      loadFailed: {
        on: {
          RETRY: 'loading',
        },
      },
      ready: {
        type: 'parallel',
        states: {
          diagram: {
            initial: 'rendering',
            on: {
              WORKFLOW_CHANGED: {
                target: '.rendering',
                actions: ['assignWorkflow', 'syncDerivedActors'],
              },
              TASK_UPDATED: {
                target: '.rendering',
                actions: ['assignUpdatedTask', 'syncDerivedActors'],
              },
            },
            states: {
              rendering: {
                on: {
                  FLOW_RENDERED: 'stable',
                },
              },
              stable: {},
            },
          },
          rightPanel: {
            initial: 'opened',
            on: {
              TASK_SELECTED: {
                target: '.opened',
                actions: ['assignSelectedTask', 'syncDerivedActors'],
              },
              CHANGE_TAB: {
                actions: ['assignActiveTab', 'syncDerivedActors'],
              },
              OPEN_PANEL: '.opened',
              CLOSE_PANEL: '.closed',
              RESIZE_PANEL: {
                actions: 'assignInspectorWidth',
              },
            },
            states: {
              opened: {},
              closed: {},
            },
          },
          lifecycle: {
            initial: 'editing',
            states: {
              editing: {
                on: {
                  REQUEST_SAVE: [
                    {
                      cond: 'canRequestSave',
                      target: 'confirmSave',
                      actions: 'clearErrors',
                    },
                    {
                      cond: 'hasInvalidMetadata',
                      actions: 'assignRequiredFieldsError',
                    },
                  ],
                  REQUEST_RESET: {
                    cond: 'hasUnsavedChanges',
                    target: 'confirmReset',
                    actions: 'clearErrors',
                  },
                  REQUEST_DELETE: {
                    cond: 'canDelete',
                    target: 'confirmDelete',
                    actions: 'prepareDeleteConfirmation',
                  },
                  REQUEST_VERSION_CHANGE: [
                    {
                      cond: 'isCurrentVersion',
                    },
                    {
                      cond: 'hasUnsavedChanges',
                      target: 'confirmVersionChange',
                      actions: 'assignPendingVersion',
                    },
                    {
                      actions: ['assignPendingVersion', 'navigateToPendingVersion'],
                    },
                  ],
                },
              },
              confirmSave: {
                on: {
                  CONFIRM_SAVE: 'saving',
                  CANCEL_DIALOG: 'editing',
                },
              },
              saving: {
                invoke: {
                  id: 'saveWorkflowDefinition',
                  src: 'saveWorkflowDefinition',
                  onDone: {
                    target: 'editing',
                    actions: [
                      'assignSavedWorkflow',
                      'syncDerivedActors',
                      'notifyWorkflowSaved',
                      'navigateToSavedWorkflow',
                    ],
                  },
                  onError: {
                    target: 'confirmSave',
                    actions: 'assignSaveError',
                  },
                },
              },
              confirmReset: {
                on: {
                  CONFIRM_RESET: {
                    target: 'editing',
                    actions: ['restoreBaseline', 'syncDerivedActors', 'notifyWorkflowReset'],
                  },
                  CANCEL_DIALOG: 'editing',
                },
              },
              confirmDelete: {
                on: {
                  UPDATE_DELETE_CONFIRMATION: {
                    actions: 'assignDeleteConfirmation',
                  },
                  CONFIRM_DELETE: {
                    cond: 'canConfirmDelete',
                    target: 'deleting',
                  },
                  CANCEL_DIALOG: {
                    target: 'editing',
                    actions: 'clearDeleteConfirmation',
                  },
                },
              },
              deleting: {
                invoke: {
                  id: 'deleteWorkflowDefinition',
                  src: 'deleteWorkflowDefinition',
                  onDone: {
                    target: 'deleted',
                    actions: ['notifyWorkflowDeleted', 'navigateToDefinitionList'],
                  },
                  onError: {
                    target: 'confirmDelete',
                    actions: 'assignDeleteError',
                  },
                },
              },
              confirmVersionChange: {
                on: {
                  CONFIRM_VERSION_CHANGE: {
                    target: 'editing',
                    actions: 'navigateToPendingVersion',
                  },
                  CANCEL_DIALOG: {
                    target: 'editing',
                    actions: 'clearPendingVersion',
                  },
                },
              },
              deleted: {
                type: 'final',
              },
            },
          },
        },
        on: {
          CLEAR_ERROR: {
            actions: 'clearErrors',
          },
        },
      },
    },
  },
  {
    actions: {
      spawnDerivedActors: assign((context) => ({
        ...context,
        flowActor: context.flowActor ?? spawn(workflowFlowMachine, 'flowMachine'),
        editorActor: context.editorActor ?? spawn(workflowEditorMachine, 'editorMachine'),
      })),
      assignRouteAttributes: assign((context, event) => {
        if (event.type !== 'INITIALIZE') return context
        return {
          ...initialContext,
          inspectorWidth: context.inspectorWidth,
          flowActor: context.flowActor,
          editorActor: context.editorActor,
          workflowName: event.workflowName,
          workflowVersion: event.workflowVersion,
          isNewWorkflow: event.isNewWorkflow,
          draftId: event.draftId,
        }
      }),
      assignLoadedWorkflow: assign((context, event) => {
        const loaded = invokeData(event) as LoadedWorkflow
        return {
          ...context,
          workflow: cloneWorkflow(loaded.workflow),
          baseline: cloneWorkflow(loaded.baseline ?? loaded.workflow),
          versions: loaded.versions,
          selectedTaskRef: undefined,
          selectedTaskEditorKey: undefined,
          activeTab: 'workflow' as const,
          pendingVersion: null,
          deleteConfirmationValue: '',
          deleteError: null,
          error: null,
        }
      }),
      assignLoadError: assign({
        error: (_context, event) => errorMessage(invokeData(event), '加载工作流失败'),
      }),
      assignWorkflow: assign((context, event) => {
        if (event.type !== 'WORKFLOW_CHANGED') return context
        return {
          ...context,
          workflow: event.workflow,
          error: null,
        }
      }),
      assignUpdatedTask: assign((context, event) => {
        if (event.type !== 'TASK_UPDATED' || !context.workflow || !context.selectedTaskRef) return context
        return {
          ...context,
          workflow: updateTask(
            context.workflow,
            context.selectedTaskRef,
            () => event.task,
          ),
          selectedTaskRef: event.task.taskReferenceName,
          error: null,
        }
      }),
      assignSelectedTask: assign((context, event) => {
        if (event.type !== 'TASK_SELECTED') return context
        return {
          ...context,
          selectedTaskRef: event.task?.taskReferenceName,
          selectedTaskEditorKey: event.task?.taskReferenceName,
          activeTab: event.task ? 'task' : 'workflow',
        }
      }),
      assignActiveTab: assign({
        activeTab: (context, event) => event.type === 'CHANGE_TAB' ? event.tab : context.activeTab,
      }),
      assignInspectorWidth: assign({
        inspectorWidth: (context, event) => event.type === 'RESIZE_PANEL' ? event.width : context.inspectorWidth,
      }),
      assignPendingVersion: assign({
        pendingVersion: (context, event) => event.type === 'REQUEST_VERSION_CHANGE' ? event.version : context.pendingVersion,
      }),
      clearPendingVersion: assign({
        pendingVersion: null,
      }),
      assignRequiredFieldsError: assign({
        error: '请先填写工作流名称、路由标识和描述',
      }),
      clearErrors: assign({
        error: null,
        deleteError: null,
      }),
      assignSavedWorkflow: assign((context, event) => {
        const saved = invokeData(event) as WorkflowDef
        return {
          ...context,
          workflow: cloneWorkflow(saved),
          baseline: cloneWorkflow(saved),
          workflowName: saved.name,
          workflowVersion: saved.version,
          isNewWorkflow: false,
          versions: [...new Set([...context.versions, saved.version])].sort((a, b) => a - b),
          error: null,
        }
      }),
      assignSaveError: assign({
        error: (_context, event) => errorMessage(invokeData(event), '保存失败'),
      }),
      restoreBaseline: assign((context) => ({
        ...context,
        workflow: context.baseline ? cloneWorkflow(context.baseline) : context.workflow,
        selectedTaskRef: undefined,
        selectedTaskEditorKey: undefined,
        activeTab: 'workflow' as const,
        error: null,
      })),
      prepareDeleteConfirmation: assign({
        deleteConfirmationValue: '',
        deleteError: null,
        error: null,
      }),
      assignDeleteConfirmation: assign((context, event) => {
        if (event.type !== 'UPDATE_DELETE_CONFIRMATION') return context
        return {
          ...context,
          deleteConfirmationValue: event.value,
          deleteError: null,
        }
      }),
      clearDeleteConfirmation: assign({
        deleteConfirmationValue: '',
        deleteError: null,
      }),
      assignDeleteError: assign({
        deleteError: (_context, event) => errorMessage(invokeData(event), '删除失败，请稍后重试'),
      }),
      syncDerivedActors: (context) => {
        if (!context.workflow) return
        context.flowActor?.send({
          type: 'SYNC',
          workflow: context.workflow,
          selectedTaskRef: context.selectedTaskRef,
        })
        context.editorActor?.send({
          type: 'SYNC',
          workflow: context.workflow,
          selectedTaskRef: context.selectedTaskRef,
          selectedTaskEditorKey: context.selectedTaskEditorKey,
          activeTab: context.activeTab,
        })
      },
    },
    guards: {
      hasUnsavedChanges: hasWorkflowChanges,
      hasInvalidMetadata: ({ workflow }) => Boolean(
        workflow
          && (
            !workflow.displayName?.trim()
            || !workflow.name.trim()
            || !workflow.description?.trim()
          ),
      ),
      canRequestSave: (context) => Boolean(
        hasWorkflowChanges(context)
          && context.workflow?.displayName?.trim()
          && context.workflow.name.trim()
          && context.workflow.description?.trim(),
      ),
      canDelete: ({ workflow, isNewWorkflow }) => Boolean(workflow && !isNewWorkflow),
      canConfirmDelete: ({ workflow, deleteConfirmationValue }) => Boolean(
        workflow && deleteConfirmationValue === workflow.name,
      ),
      isCurrentVersion: (context, event) => {
        if (event.type !== 'REQUEST_VERSION_CHANGE') return false
        if (event.version === 'latest') return context.workflowVersion == null
        return event.version === context.workflow?.version && context.workflowVersion != null
      },
    },
    services: {
      loadWorkflowDefinition: async () => {
        throw new Error('未配置工作流加载服务')
      },
      saveWorkflowDefinition: async () => {
        throw new Error('未配置工作流保存服务')
      },
      deleteWorkflowDefinition: async () => {
        throw new Error('未配置工作流删除服务')
      },
    },
  },
)
