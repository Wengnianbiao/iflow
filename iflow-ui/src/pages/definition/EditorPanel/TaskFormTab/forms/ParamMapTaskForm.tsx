import { ParamMapTaskInspector } from '@/components/workflow/ParamMapTaskInspector'
import { TaskPanelHeader } from './TaskPanelHeader'
import type { TaskFormProps } from './types'

export function ParamMapTaskForm({ task, variableOptions, onChange }: TaskFormProps) {
  return (
    <ParamMapTaskInspector
      task={task}
      header={<TaskPanelHeader task={task} label="参数映射" onChange={onChange} />}
      variableOptions={variableOptions}
      onChange={onChange}
    />
  )
}
