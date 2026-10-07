import { Navigate, type RouteObject } from 'react-router-dom'
import { App } from '@/components/App'
import { WorkflowDefinitions } from '@/pages/definitions'
import { Workflow } from '@/pages/definition'
import { ExecutionDetailPage, WorkflowSearch } from '@/pages/executions'
import { RunWorkflow } from '@/pages/runWorkflow'

export const routes: RouteObject[] = [
  {
    element: <App />,
    children: [
      { index: true, element: <Navigate to="/workflowDef" replace /> },
      { path: '/workflowDef', element: <WorkflowDefinitions /> },
      { path: '/workflowDef/edit', element: <Workflow /> },
      { path: '/workflowDef/:name/:version?', element: <Workflow /> },
      { path: '/runWorkflow', element: <RunWorkflow /> },
      { path: '/executions', element: <WorkflowSearch /> },
      { path: '/executions/:workflowId', element: <ExecutionDetailPage /> },
      { path: '/execution/:workflowId', element: <ExecutionDetailPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/workflowDef" replace /> },
]
