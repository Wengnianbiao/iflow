import { useEffect } from 'react'
import { useSelector } from '@xstate/react'
import { hasWorkflowChanges, type WorkflowDefinitionActor } from './state'

type PromptIfChangesProps = {
  actor: WorkflowDefinitionActor
}

export function PromptIfChanges({ actor }: PromptIfChangesProps) {
  const dirty = useSelector(actor, (state) => hasWorkflowChanges(state.context))

  useEffect(() => {
    if (!dirty) return

    const preventUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }

    window.addEventListener('beforeunload', preventUnload)
    return () => window.removeEventListener('beforeunload', preventUnload)
  }, [dirty])

  return null
}

