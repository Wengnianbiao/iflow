import { createContext, useContext } from 'react'
import type { ActorRefFrom } from 'xstate'
import type { workflowDefinitionMachine } from '../machine'

export type WorkflowDefinitionActor = ActorRefFrom<typeof workflowDefinitionMachine>

export const WorkflowEditContext = createContext<WorkflowDefinitionActor | null>(null)

export function useWorkflowEditContext() {
  const actor = useContext(WorkflowEditContext)
  if (!actor) throw new Error('useWorkflowEditContext 必须在 WorkflowEditProvider 中使用')
  return actor
}

