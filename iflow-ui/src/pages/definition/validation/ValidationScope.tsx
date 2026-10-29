import { createContext, useContext, useId, useState, type ReactNode } from 'react'
import { useSelector } from '@xstate/react'
import type { WorkflowDefinitionActor } from '../state'
import { currentProblems } from '../state/definitionValidation'
import type { ProblemFocus } from './types'

const Scope = createContext<{ actor: WorkflowDefinitionActor; basePath: string; taskReferenceName?: string; focus?: ProblemFocus | null; readOnly?: boolean } | null>(null)
const FieldPath = createContext<string | undefined>(undefined)
export const ValidationScope = Scope.Provider
export const useValidationFocus = () => useContext(Scope)?.focus
export const useValidationReadOnly = () => useContext(Scope)?.readOnly ?? false
export const useValidationFieldPath = () => useContext(FieldPath)

function ScopedField({ path, children }: { path: string; children: ReactNode }) {
  const scope = useContext(Scope)!
  const context = useSelector(scope.actor, (state) => state.context)
  const [touched, setTouched] = useState(false)
  const id = useId()
  const fullPath = `${scope.basePath}${path}`
  const error = currentProblems(context).find((problem) => problem.path === fullPath || problem.path.startsWith(`${fullPath}/`))
  const visible = !context.sourceBlocked && error && (touched || context.saveAttempted || context.problemFocus?.problem.path === error.path || error.source === 'draft')
  return <div className={`validation-field${visible ? ' invalid' : ''}`} data-field-path={fullPath} role="group" aria-invalid={visible ? true : undefined} aria-describedby={visible ? id : undefined} onBlurCapture={() => setTouched(true)}>
    <FieldPath.Provider value={fullPath}>{children}</FieldPath.Provider>
    {visible ? <p id={id} className="inline-error">{error.message}</p> : null}
  </div>
}

export function ValidationField(props: { path: string; children: ReactNode }) {
  const scope = useContext(Scope)
  return scope ? <ScopedField {...props} /> : <>{props.children}</>
}

/** Unfinished field values live in the parent actor and survive form unmounts. */
export function useFieldDraft(relativePath: string, canonical: string) {
  const scope = useContext(Scope)
  if (!scope) throw new Error('字段草稿需要 ValidationScope')
  const path = `${scope.basePath}${relativePath}`
  const taskPath = scope.basePath || undefined
  const taskReferenceName = taskPath ? scope.taskReferenceName ?? '' : ''
  const draft = useSelector(scope.actor, (state) => state.context.fieldDrafts[path])
  return {
    value: draft?.value ?? canonical,
    error: draft?.message ?? '',
    invalid: (value: string, message: string) => scope.actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: { path, taskPath, taskReferenceName, value, message } }),
    applied: (value: string) => scope.actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: { path, taskPath, taskReferenceName, value, message: '' } }),
    clear: () => scope.actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: null }),
  }
}

export const useTaskFieldDraft = useFieldDraft
