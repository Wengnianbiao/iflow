import { ParamMapTaskInspector } from '@/components/workflow/ParamMapTaskInspector'
import { VariableExpressionInput } from '../../InspectorFields'
import { TaskPanelHeader } from './TaskPanelHeader'
import type { TaskFormProps } from './types'

export function ParamMapTaskForm({ task, variableOptions, onChange }: TaskFormProps) {
  return (
    <ParamMapTaskInspector
      task={task}
      header={<TaskPanelHeader task={task} label="参数映射" onChange={onChange} />}
      renderInputValue={(value, updateValue) => (
        <VariableExpressionInput value={value} options={variableOptions} onChange={updateValue} />
      )}
      onChange={onChange}
    />
  )
}
