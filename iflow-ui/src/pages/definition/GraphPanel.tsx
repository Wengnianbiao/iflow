import { useSelector } from '@xstate/react'
import { ConductorFlowCanvas } from '@/components/flow/ConductorFlowCanvas'
import type { ActorRefFrom } from 'xstate'
import type { WorkflowDef } from '@/types/workflow'
import { workflowFlowMachine, type WorkflowDefinitionActor } from './state'

function DerivedFlowCanvas({
  actor,
  fallbackWorkflow,
}: {
  actor: ActorRefFrom<typeof workflowFlowMachine>
  fallbackWorkflow: WorkflowDef
}) {
  const flowState = useSelector(actor, (snapshot) => snapshot)
  const workflow = flowState.context.workflow ?? fallbackWorkflow
  const graph = {
    nodes: flowState.context.nodes,
    edges: flowState.context.edges,
  }

  return (
    <ConductorFlowCanvas
      workflow={workflow}
      graph={graph}
      selectedTaskRef={flowState.context.selectedTaskRef}
      onWorkflowChange={(nextWorkflow) => actor.send({ type: 'WORKFLOW_CHANGED', workflow: nextWorkflow })}
      onSelectTask={(task) => actor.send({ type: 'SELECT_TASK', task })}
    />
  )
}

export function GraphPanel({ actor }: { actor: WorkflowDefinitionActor }) {
  const state = useSelector(actor, (snapshot) => snapshot)
  const { workflow, error, flowActor } = state.context
  const loading = state.matches('loading')
  const loadFailed = state.matches('loadFailed')

  return (
    <section className="workflow-canvas-shell">
      {loading ? <div className="workflow-loading">正在加载工作流...</div> : null}

      {loadFailed ? (
        <div className="workflow-loading workflow-load-failed">
          <span>{error || '加载工作流失败'}</span>
          <button type="button" onClick={() => actor.send({ type: 'RETRY' })}>
            重新加载
          </button>
        </div>
      ) : null}

      {workflow && flowActor ? <DerivedFlowCanvas actor={flowActor} fallbackWorkflow={workflow} /> : null}
    </section>
  )
}
