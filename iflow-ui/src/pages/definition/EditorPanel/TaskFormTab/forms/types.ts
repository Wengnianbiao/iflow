import type { WorkflowTask } from '@/types/workflow'

export type TaskFormProps = {
  task: WorkflowTask
  variableOptions: string[]
  inputVariableOptions?: string[]
  joinTaskOptions?: Pick<WorkflowTask, 'name' | 'taskReferenceName'>[]
  onChange: (task: WorkflowTask) => void
}
