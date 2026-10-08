import { useMemo } from 'react'
import { useSelector } from '@xstate/react'
import { Helmet } from 'react-helmet'
import { Navigate, useLocation, useParams } from 'react-router-dom'
import {
  WorkflowEditProvider,
  useWorkflowDefinition,
  type WorkflowRouteAttributes,
} from './state'
import { WorkflowDialogs } from './dialogs'
import { WorkflowMetaBar } from './WorkflowMetaBar'
import { PromptIfChanges } from './PromptIfChanges'
import { WorkflowWorkspace } from './WorkflowWorkspace'

function numberOrUndefined(value: string | null | undefined) {
  if (value == null || value === '') return undefined
  const number = Number(value)
  return Number.isFinite(number) ? number : undefined
}

export function Workflow() {
  const location = useLocation()
  const params = useParams()
  const attributes = useMemo<WorkflowRouteAttributes>(() => {
    const search = new URLSearchParams(location.search)
    return {
      workflowRoute: search.get('route') ?? params.route ?? '',
      workflowVersion: numberOrUndefined(search.get('version') ?? params.version),
      isNewWorkflow: search.get('new') === 'true',
      draftId: search.get('draft') ?? undefined,
    }
  }, [location.search, params.route, params.version])
  const actor = useWorkflowDefinition(attributes)
  const title = useSelector(
    actor,
    (state) => state.context.workflow?.name
  )
  const isReady = useSelector(actor, (state) => state.matches('ready'))

  if (!attributes.workflowRoute) return <Navigate to="/workflowDef" replace />

  return (
    <WorkflowEditProvider actor={actor}>
      <div className="workflow-page">
        <Helmet>
          <title>{title ? `工作流配置 - ${title}` : '工作流配置'}</title>
        </Helmet>
        <PromptIfChanges actor={actor} />
        {isReady ? <WorkflowMetaBar actor={actor} /> : null}
        <WorkflowWorkspace actor={actor} />
        <WorkflowDialogs actor={actor} />
      </div>
    </WorkflowEditProvider>
  )
}
