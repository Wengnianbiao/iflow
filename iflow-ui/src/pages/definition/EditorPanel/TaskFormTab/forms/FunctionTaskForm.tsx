import {
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  valueText,
} from '../../InspectorFields'
import { TaskPanelHeader } from './TaskPanelHeader'
import type { TaskFormProps } from './types'

const FUNCTION_OPTIONS = [
  { value: 'JSON_PARSE', label: 'JSON.parse' },
  { value: 'XML_PARSE', label: 'XML.parse' },
] as const

export function FunctionTaskForm({ task, variableOptions, onChange }: TaskFormProps) {
  const inputParameters = task.inputParameters ?? {}
  const functionName = String(inputParameters.function ?? 'JSON_PARSE')
  const value = valueText(inputParameters.value)

  const updateInputParameters = (changes: Record<string, unknown>) => {
    onChange({
      ...task,
      inputParameters: {
        ...inputParameters,
        ...changes,
      },
    })
  }

  return (
    <div className="workflow-form-panel function-task-inspector">
      <TaskPanelHeader task={task} label="函数转换" onChange={onChange} />

      <WorkflowCollapsibleSection title="函数配置">
        <label className="inspector-field full">
          <span>转换函数</span>
          <select
            value={functionName}
            onChange={(event) => updateInputParameters({ function: event.target.value })}
          >
            {FUNCTION_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
        <label className="inspector-field full">
          <span>待解析字符串</span>
          <VariableExpressionInput
            value={value}
            options={variableOptions}
            onChange={(nextValue) => updateInputParameters({ value: nextValue })}
          />
        </label>
        <p className="inspector-empty">
          转换结果会直接写入任务输出根节点；JSON 顶层必须是对象。
        </p>
      </WorkflowCollapsibleSection>
    </div>
  )
}
