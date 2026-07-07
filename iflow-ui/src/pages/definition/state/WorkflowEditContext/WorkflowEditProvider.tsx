import type { ReactNode } from 'react'
import { WorkflowEditContext, type WorkflowDefinitionActor } from './WorkflowEditContext'

type WorkflowEditProviderProps = {
  actor: WorkflowDefinitionActor
  children: ReactNode
}

export function WorkflowEditProvider({ actor, children }: WorkflowEditProviderProps) {
  return (
    <WorkflowEditContext.Provider value={actor}>
      {children}
    </WorkflowEditContext.Provider>
  )
}

