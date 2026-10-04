import { useSelector } from '@xstate/react'
import { ConductorFlowCanvas } from '@/components/flow/ConductorFlowCanvas'
import type { ActorRefFrom } from 'xstate'
import type { WorkflowDef } from '@/types/workflow'
import { workflowFlowMachine, type WorkflowDefinitionActor } from './state'

function DerivedFlowCanvas({
  actor,
  fallbackWorkflow,
  readOnly,
}: {
  actor: ActorRefFrom<typeof workflowFlowMachine>
  fallbackWorkflow: WorkflowDef
  readOnly: boolean
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
      readOnly={readOnly}
      selectedTaskRef={flowState.context.selectedTaskRef}
      onWorkflowChange={(nextWorkflow) => actor.send({ type: 'WORKFLOW_CHANGED', workflow: nextWorkflow })}
      onSelectTask={(task) => actor.send({ type: 'SELECT_TASK', task })}
    />
  )
}

export function GraphPanel({ actor }: { actor: WorkflowDefinitionActor }) {
  const state = useSelector(actor, (snapshot) => snapshot)
  const { workflow, error, flowActor } = state.context
  const frozen = state.context.sourceBlocked || state.context.diagramWorkflow !== workflow
  const readOnly = frozen || Object.values(state.context.fieldDrafts).some((draft) => Boolean(draft.message)) || !state.matches('ready.lifecycle.editing')
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

      {frozen && state.matches('ready') ? <div className="workflow-canvas-validation-banner">定义存在结构或引用错误，画布保留最近可展示的版本。请在右侧修复。</div> : null}
      {workflow && flowActor && state.matches('ready') ? <DerivedFlowCanvas actor={flowActor} fallbackWorkflow={state.context.diagramWorkflow ?? workflow} readOnly={readOnly} /> : null}
    </section>
  )
}
