import type { WorkflowTask } from '@/types/workflow'

export type TaskFormProps = {
  task: WorkflowTask
  variableOptions: string[]
  inputVariableOptions?: string[]
  onChange: (task: WorkflowTask) => void
}
