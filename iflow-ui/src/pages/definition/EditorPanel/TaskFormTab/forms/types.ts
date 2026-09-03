import type { WorkflowTask } from '@/types/workflow'

export type TaskFormProps = {
  task: WorkflowTask
  variableOptions: string[]
  onChange: (task: WorkflowTask) => void
}
