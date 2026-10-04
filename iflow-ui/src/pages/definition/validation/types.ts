import type { WorkflowDef, WorkflowTask } from '@/types/workflow'

export type TaskValidationProblem = {
  code: string
  /** JSON pointer relative to the task, e.g. /inputParameters/uri. */
  path: string
  message: string
  severity?: 'error' | 'warning'
}

export type TaskValidator = (task: WorkflowTask, workflow: WorkflowDef) => TaskValidationProblem[]

export type WorkflowProblem = TaskValidationProblem & {
  id: string
  severity: 'error' | 'warning'
  source: 'definition' | 'draft' | 'server'
  taskPath?: string
  taskReferenceName?: string
  /** Structural problems must be repaired in Code before forms can edit safely. */
  structural?: boolean
  offset?: number
}

export type FieldDraft = {
  path: string
  taskPath: string
  taskReferenceName: string
  value: string
  message: string
}

export type ProblemFocus = { problem: WorkflowProblem; tab: 'workflow' | 'task' | 'code'; sequence: number }
