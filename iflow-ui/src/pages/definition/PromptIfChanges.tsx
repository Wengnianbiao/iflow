import { useCallback, useEffect } from 'react'
import { useSelector } from '@xstate/react'
import { useBlocker, type BlockerFunction } from 'react-router-dom'
import { hasWorkflowChanges, type WorkflowDefinitionActor } from './state'
import { blockingProblems } from './state/definitionValidation'
import { UnsavedWorkflowDialog } from './dialogs/UnsavedWorkflowDialog'

type PromptIfChangesProps = {
  actor: WorkflowDefinitionActor
}

export function PromptIfChanges({ actor }: PromptIfChangesProps) {
  const state = useSelector(actor, (snapshot) => snapshot)
  const dirty = hasWorkflowChanges(state.context) && !state.matches('ready.lifecycle.deleted')
  const saving = state.matches('ready.lifecycle.savingBeforeLeave') || state.matches('ready.lifecycle.saving')
  const hasErrors = blockingProblems(state.context).length > 0
  const shouldBlock = useCallback<BlockerFunction>(({ currentLocation, nextLocation }) => {
    const snapshot = actor.getSnapshot()
    if (!snapshot) return false
    return hasWorkflowChanges(snapshot.context)
      && !snapshot.matches('ready.lifecycle.deleted')
      && (currentLocation.pathname !== nextLocation.pathname
        || currentLocation.search !== nextLocation.search
        || currentLocation.hash !== nextLocation.hash)
  }, [actor])
  const blocker = useBlocker(shouldBlock)

  useEffect(() => {
    if (blocker.state === 'blocked' && !dirty && !saving) blocker.proceed()
  }, [blocker, dirty, saving])

  useEffect(() => {
    if (!dirty) return

    const preventUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }

    window.addEventListener('beforeunload', preventUnload)
    return () => window.removeEventListener('beforeunload', preventUnload)
  }, [dirty])

  return (
    <UnsavedWorkflowDialog
      open={blocker.state === 'blocked' && dirty}
      saving={saving}
      hasErrors={hasErrors}
      saveError={state.context.serverProblems[0]?.message}
      onCancel={() => { if (blocker.state === 'blocked') blocker.reset() }}
      onDiscard={() => { if (blocker.state === 'blocked') blocker.proceed() }}
      onSave={() => actor.send({ type: 'SAVE_BEFORE_LEAVE' })}
    />
  )
}

