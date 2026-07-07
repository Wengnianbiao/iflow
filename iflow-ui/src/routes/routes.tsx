import { Navigate, type RouteObject } from 'react-router-dom'
import { App } from '@/components/App'
import { WorkflowDefinitions } from '@/pages/definitions'
import { Workflow } from '@/pages/definition'
import { WorkflowExcutions } from '@/pages/excutions'
import { ExecutionDetailPage } from '@/pages/ExecutionDetailPage'

export const routes: RouteObject[] = [
  {
    element: <App />,
    children: [
      { index: true, element: <Navigate to="/workflowDef" replace /> },
      { path: '/workflowDef', element: <WorkflowDefinitions /> },
      { path: '/workflowDef/edit', element: <Workflow /> },
      { path: '/workflowDef/:name/:version?', element: <Workflow /> },
      { path: '/executions', element: <WorkflowExcutions /> },
      { path: '/executions/:workflowId', element: <ExecutionDetailPage /> },
      { path: '/execution/:workflowId', element: <ExecutionDetailPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/workflowDef" replace /> },
]
