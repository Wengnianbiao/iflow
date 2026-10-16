import { assign, createMachine, spawn } from 'xstate'
import type { WorkflowDef } from '@/types/workflow'
import { syncJoinOnAfterTaskRemoval } from '@/flow/workflowEditor'
import {
  hasWorkflowChanges,
  type LoadedWorkflow,
  type WorkflowDefinitionMachineContext,
  type WorkflowDefinitionMachineEvent,
} from './types'
import { workflowFlowMachine } from './flowMachine'
import { workflowEditorMachine } from './editorMachine'
import { workflowValidationMachine } from './validationMachine'
import { blockingProblems, currentProblems, draftProblems, focusProblem, validatedEdit, workflowEdit } from './definitionValidation'
import { findTaskPath, pointerValue, replaceTaskAtPath } from '../validation/validateWorkflow'

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
  workflowRoute: '',
  workflowVersion: undefined,
  isNewWorkflow: false,
  draftId: undefined,
  workflow: null,
  baseline: null,
  source: '',
  sourceBlocked: false,
  diagramWorkflow: null,
  problems: [],
  serverProblems: [],
  fieldDrafts: {},
  saveAttempted: false,
  saveVersion: null,
  problemFocus: null,
  versions: [],
  selectedTaskRef: undefined,
  selectedTaskPath: undefined,
  selectedTaskEditorKey: undefined,
  activeTab: 'workflow',
  inspectorWidth: 50,
  pendingVersion: null,
  deleteConfirmationValue: '',
  deleteError: null,
  error: null,
  flowActor: undefined,
  editorActor: undefined,
  validationActor: undefined,
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
        actions: ['assignRouteAttributes', 'resetValidation'],
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
                cond: 'canEditDefinition',
                target: '.rendering',
                actions: ['assignWorkflow', 'syncDerivedActors'],
              },
              TASK_UPDATED: {
                cond: 'canEditDefinition',
                target: '.rendering',
                actions: ['assignUpdatedTask', 'syncDerivedActors'],
              },
              SOURCE_CHANGED: {
                cond: 'canEditSource',
                target: '.rendering',
                actions: ['assignSource', 'syncDerivedActors'],
              },
              FIELD_DRAFT_CHANGED: {
                cond: 'canEditDefinition',
                actions: ['assignFieldDraft', 'syncValidation'],
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
              REQUEST_SAVE: '.opened',
              FOCUS_PROBLEM: {
                target: '.opened',
                actions: ['assignProblemFocus', 'syncDerivedActors'],
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
                      cond: 'canRequestNewVersion',
                      target: 'preparingNewVersion',
                      actions: ['clearSaveVersion', 'clearErrors'],
                    },
                    {
                      cond: 'canRequestSave',
                      target: 'confirmSave',
                      actions: ['clearSaveVersion', 'clearErrors'],
                    },
                    {
                      cond: 'hasBlockingProblems',
                      actions: ['rejectInvalidSave', 'syncDerivedActors', 'expandValidation'],
                    },
                  ],
                  SAVE_BEFORE_LEAVE: [
                    {
                      cond: 'canRequestSave',
                      target: 'savingBeforeLeave',
                      actions: ['clearSaveVersion', 'clearServerProblems', 'syncValidation'],
                    },
                    {
                      cond: 'hasBlockingProblems',
                      actions: ['rejectInvalidSave', 'syncDerivedActors', 'expandValidation'],
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
                      actions: ['assignPendingVersion', 'navigateToPendingVersion'],
                    },
                  ],
                },
              },
              preparingNewVersion: {
                invoke: {
                  id: 'loadVersionsForSave',
                  src: 'loadVersionsForSave',
                  onDone: {
                    target: 'confirmSave',
                    actions: 'assignNewSaveVersion',
                  },
                  onError: {
                    target: 'editing',
                    actions: ['assignSaveError', 'syncValidation', 'expandValidation'],
                  },
                },
              },
              confirmSave: {
                on: {
                  CONFIRM_SAVE: [
                    { cond: 'hasBlockingProblems', target: 'editing', actions: ['clearSaveVersion', 'rejectInvalidSave', 'syncDerivedActors', 'expandValidation'] },
                    { target: 'saving', actions: ['clearServerProblems', 'syncValidation'] },
                  ],
                  CANCEL_DIALOG: { target: 'editing', actions: 'clearSaveVersion' },
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
                    actions: ['assignSaveError', 'syncValidation', 'expandValidation'],
                  },
                },
              },
              savingBeforeLeave: {
                invoke: {
                  id: 'saveWorkflowBeforeLeave',
                  src: 'saveWorkflowDefinition',
                  onDone: {
                    target: 'editing',
                    actions: ['assignSavedWorkflow', 'syncDerivedActors', 'notifyWorkflowSaved'],
                  },
                  onError: {
                    target: 'editing',
                    actions: ['assignSaveError', 'syncValidation', 'expandValidation'],
                  },
                },
              },
              confirmReset: {
                on: {
                  CONFIRM_RESET: {
                    target: 'editing',
                    actions: ['restoreBaseline', 'resetValidation', 'syncDerivedActors', 'notifyWorkflowReset'],
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
        validationActor: context.validationActor ?? spawn(workflowValidationMachine, 'validationMachine'),
      })),
      assignRouteAttributes: assign((context, event) => {
        if (event.type !== 'INITIALIZE') return context
        return {
          ...initialContext,
          inspectorWidth: context.inspectorWidth,
          flowActor: context.flowActor,
          editorActor: context.editorActor,
          validationActor: context.validationActor,
          workflowRoute: event.workflowRoute,
          workflowVersion: event.workflowVersion,
          isNewWorkflow: event.isNewWorkflow,
          draftId: event.draftId,
        }
      }),
      assignLoadedWorkflow: assign((context, event) => {
        const loaded = invokeData(event) as LoadedWorkflow
        return {
          ...context,
          ...workflowEdit({ ...context, baseline: null, diagramWorkflow: null }, cloneWorkflow(loaded.workflow)),
          baseline: cloneWorkflow(loaded.baseline ?? loaded.workflow),
          versions: loaded.versions,
          selectedTaskRef: undefined,
          selectedTaskPath: undefined,
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
          ...workflowEdit(context, context.workflow ? syncJoinOnAfterTaskRemoval(context.workflow, event.workflow) : event.workflow),
          fieldDrafts: Object.fromEntries(Object.entries(context.fieldDrafts).filter(([, draft]) => draft.message)),
        }
      }),
      assignUpdatedTask: assign((context, event) => {
        if (event.type !== 'TASK_UPDATED' || !context.workflow || !context.selectedTaskPath) return context
        const workflow = replaceTaskAtPath(context.workflow, context.selectedTaskPath, event.task)
        const previousReference = pointerValue(context.workflow, `${context.selectedTaskPath}/taskReferenceName`)
        return {
          ...context,
          ...workflowEdit(context, previousReference === event.task.taskReferenceName
            ? syncJoinOnAfterTaskRemoval(context.workflow, workflow)
            : workflow),
          selectedTaskPath: context.selectedTaskPath,
          fieldDrafts: Object.fromEntries(Object.entries(context.fieldDrafts).filter(([, draft]) => draft.message)),
          selectedTaskRef: event.task.taskReferenceName,
          error: null,
        }
      }),
      assignSource: assign((context, event) => event.type === 'SOURCE_CHANGED'
        ? { ...context, ...validatedEdit(context, event.source), fieldDrafts: {} } : context),
      assignFieldDraft: assign((context, event) => {
        if (event.type !== 'FIELD_DRAFT_CHANGED') return context
        const fieldDrafts = { ...context.fieldDrafts }
        if (event.draft) fieldDrafts[event.path] = event.draft
        else delete fieldDrafts[event.path]
        return { ...context, fieldDrafts }
      }),
      assignProblemFocus: assign((context, event) => event.type === 'FOCUS_PROBLEM'
        ? { ...context, ...focusProblem(context, event.problem, event.tab) } : context),
      rejectInvalidSave: assign((context) => {
        const first = blockingProblems(context)[0]
        return { ...context, saveAttempted: true, ...(first ? focusProblem(context, first) : {}) }
      }),
      assignSelectedTask: assign((context, event) => {
        if (event.type !== 'TASK_SELECTED') return context
        return {
          ...context,
          selectedTaskRef: event.task?.taskReferenceName,
          selectedTaskPath: event.task && context.workflow ? findTaskPath(context.workflow, event.task.taskReferenceName) : undefined,
          selectedTaskEditorKey: event.task && context.workflow ? findTaskPath(context.workflow, event.task.taskReferenceName) : undefined,
          activeTab: event.task ? 'task' : 'workflow',
          problemFocus: null,
        }
      }),
      assignActiveTab: assign((context, event) => event.type === 'CHANGE_TAB'
        ? { ...context, activeTab: event.tab, problemFocus: null } : context),
      assignInspectorWidth: assign({
        inspectorWidth: (context, event) => event.type === 'RESIZE_PANEL' ? event.width : context.inspectorWidth,
      }),
      assignPendingVersion: assign({
        pendingVersion: (context, event) => event.type === 'REQUEST_VERSION_CHANGE' ? event.version : context.pendingVersion,
      }),
      clearErrors: assign({
        error: null,
        deleteError: null,
      }),
      clearSaveVersion: assign({ saveVersion: null }),
      assignNewSaveVersion: assign((context, event) => {
        const versions = invokeData(event) as number[]
        return {
          saveVersion: Math.max(context.workflow?.version ?? 1, ...versions) + 1,
          versions: context.baseline && context.workflow?.route === context.baseline.route
            ? [...new Set([...versions, context.baseline.version])].sort((a, b) => a - b)
            : context.versions,
        }
      }),
      assignSavedWorkflow: assign((context, event) => {
        const saved = invokeData(event) as WorkflowDef
        return {
          ...context,
          ...workflowEdit(context, cloneWorkflow(saved)),
          baseline: cloneWorkflow(saved),
          workflowRoute: saved.route,
          workflowVersion: saved.version,
          isNewWorkflow: false,
          versions: context.baseline?.route === saved.route
            ? [...new Set([...context.versions, saved.version])].sort((a, b) => a - b)
            : [saved.version],
          error: null,
          serverProblems: [],
          fieldDrafts: {},
          saveAttempted: false,
          saveVersion: null,
          problemFocus: null,
        }
      }),
      assignSaveError: assign((context, event) => {
        const message = errorMessage(invokeData(event), '保存失败')
        return { ...context, error: message, serverProblems: [{ id: 'SERVER_SAVE', code: 'SERVER_SAVE', path: '', message, source: 'server' as const, severity: 'error' as const }] }
      }),
      clearServerProblems: assign({ serverProblems: [], error: null }),
      restoreBaseline: assign((context) => ({
        ...context,
        workflow: context.baseline ? cloneWorkflow(context.baseline) : context.workflow,
        ...(context.baseline ? workflowEdit(context, cloneWorkflow(context.baseline)) : {}),
        selectedTaskRef: undefined,
        selectedTaskPath: undefined,
        selectedTaskEditorKey: undefined,
        activeTab: 'workflow' as const,
        error: null,
        fieldDrafts: {},
        serverProblems: [],
        saveAttempted: false,
        problemFocus: null,
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
      resetValidation: (context) => context.validationActor?.send({ type: 'RESET' }),
      expandValidation: (context) => context.validationActor?.send({ type: 'EXPAND' }),
      syncValidation: (context) => context.validationActor?.send({ type: 'SYNC', problems: [...currentProblems(context), ...context.serverProblems] }),
      syncDerivedActors: (context) => {
        context.validationActor?.send({ type: 'SYNC', problems: [...currentProblems(context), ...context.serverProblems] })
        if (!context.workflow) return
        if (context.diagramWorkflow) context.flowActor?.send({
          type: 'SYNC',
          workflow: context.diagramWorkflow,
          selectedTaskRef: context.selectedTaskRef,
        })
        context.editorActor?.send({
          type: 'SYNC',
          workflow: context.workflow,
          selectedTaskRef: context.selectedTaskRef,
          selectedTaskEditorKey: context.selectedTaskEditorKey,
          selectedTaskPath: context.selectedTaskPath,
          activeTab: context.activeTab,
        })
      },
    },
    guards: {
      hasUnsavedChanges: hasWorkflowChanges,
      hasBlockingProblems: (context) => blockingProblems(context).length > 0,
      canEditDefinition: (context, event, meta) => {
        if (context.sourceBlocked || !meta.state.matches('ready.lifecycle.editing')) return false
        if (!draftProblems(context).length) return true
        if (event.type === 'TASK_UPDATED') return draftProblems(context).every((problem) => problem.taskPath === context.selectedTaskPath)
        if (event.type === 'WORKFLOW_CHANGED') return JSON.stringify(event.workflow.tasks) === JSON.stringify(context.workflow?.tasks)
        return true
      },
      canEditSource: (context, _event, meta) => draftProblems(context).length === 0 && meta.state.matches('ready.lifecycle.editing'),
      canRequestNewVersion: (context, event) => Boolean(
        event.type === 'REQUEST_SAVE' && event.isNewVersion && !context.isNewWorkflow
          && hasWorkflowChanges(context) && blockingProblems(context).length === 0,
      ),
      canRequestSave: (context, event) => Boolean(
        hasWorkflowChanges(context)
          && (event.type !== 'REQUEST_SAVE' || !event.isNewVersion)
          && blockingProblems(context).length === 0,
      ),
      canDelete: ({ baseline, isNewWorkflow }) => Boolean(baseline && !isNewWorkflow),
      canConfirmDelete: ({ baseline, deleteConfirmationValue }) => Boolean(
        baseline && deleteConfirmationValue === baseline.route,
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
      loadVersionsForSave: async () => {
        throw new Error('未配置工作流版本加载服务')
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
