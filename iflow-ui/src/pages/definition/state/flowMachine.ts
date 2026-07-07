import { assign, createMachine, sendParent } from 'xstate'
import type { EdgeData, NodeData } from 'reaflow'
import { workflowToReaflow, type NodeTaskData } from '@/flow/conductorMapper'
import type { WorkflowDef, WorkflowTask } from '@/types/workflow'

export type WorkflowFlowMachineContext = {
  workflow: WorkflowDef | null
  nodes: NodeData<NodeTaskData>[]
  edges: EdgeData[]
  selectedTaskRef?: string
  renderRevision: number
}

export type WorkflowFlowMachineEvent =
  | { type: 'SYNC'; workflow: WorkflowDef; selectedTaskRef?: string }
  | { type: 'SELECT_TASK'; task?: WorkflowTask }
  | { type: 'WORKFLOW_CHANGED'; workflow: WorkflowDef }

export const workflowFlowMachine = createMachine<WorkflowFlowMachineContext, WorkflowFlowMachineEvent>(
  {
    predictableActionArguments: true,
    id: 'workflowFlow',
    initial: 'idle',
    context: {
      workflow: null,
      nodes: [],
      edges: [],
      selectedTaskRef: undefined,
      renderRevision: 0,
    },
    on: {
      SELECT_TASK: {
        actions: ['assignSelection', 'notifySelection'],
      },
      WORKFLOW_CHANGED: {
        actions: 'notifyWorkflowChange',
      },
    },
    states: {
      idle: {
        on: {
          SYNC: {
            target: 'rendering',
            actions: 'assignSource',
          },
        },
      },
      rendering: {
        entry: 'deriveDiagram',
        always: {
          target: 'ready',
          actions: 'notifyRendered',
        },
      },
      ready: {
        on: {
          SYNC: {
            target: 'rendering',
            actions: 'assignSource',
          },
        },
      },
    },
  },
  {
    actions: {
      assignSource: assign((context, event) => event.type === 'SYNC'
        ? {
            ...context,
            workflow: event.workflow,
            selectedTaskRef: event.selectedTaskRef,
          }
        : context),
      deriveDiagram: assign((context) => {
        if (!context.workflow) return context
        const { nodes, edges } = workflowToReaflow(context.workflow)
        return {
          ...context,
          nodes,
          edges,
          renderRevision: context.renderRevision + 1,
        }
      }),
      assignSelection: assign({
        selectedTaskRef: (context, event) => event.type === 'SELECT_TASK'
          ? event.task?.taskReferenceName
          : context.selectedTaskRef,
      }),
      notifySelection: sendParent((_context, event) => event.type === 'SELECT_TASK'
        ? { type: 'TASK_SELECTED', task: event.task }
        : { type: 'TASK_SELECTED', task: undefined }),
      notifyWorkflowChange: sendParent((_context, event) => event.type === 'WORKFLOW_CHANGED'
        ? { type: 'WORKFLOW_CHANGED', workflow: event.workflow }
        : { type: 'WORKFLOW_CHANGED' }),
      notifyRendered: sendParent((context) => ({
        type: 'FLOW_RENDERED',
        revision: context.renderRevision,
      })),
    },
  },
)
