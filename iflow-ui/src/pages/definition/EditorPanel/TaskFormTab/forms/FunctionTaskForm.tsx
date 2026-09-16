import {
  InspectorSelect,
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  valueText,
} from '../../InspectorFields'
import { TaskPanelHeader } from './TaskPanelHeader'
import type { TaskFormProps } from './types'

const FUNCTION_OPTIONS = [
  { value: 'JSON_PARSE', label: 'JSON.parse' },
  { value: 'JSON_STRINGIFY', label: 'JSON.stringify' },
  { value: 'XML_PARSE', label: 'XML.parse' },
  { value: 'XML_STRINGIFY', label: 'XML.stringify' },
] as const

export function FunctionTaskForm({ task, variableOptions, onChange }: TaskFormProps) {
  const inputParameters = task.inputParameters ?? {}
  const functionName = String(inputParameters.function ?? 'JSON_PARSE')
  const value = valueText(inputParameters.value)
  const rootName = valueText(inputParameters.rootName)
  const isXmlStringify = functionName === 'XML_STRINGIFY'
  const isStringify = functionName === 'JSON_STRINGIFY' || isXmlStringify

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

      <WorkflowCollapsibleSection title="转换配置">
        <div className="inspector-field full">
          <span>转换函数</span>
          <InspectorSelect
            value={functionName}
            options={FUNCTION_OPTIONS}
            ariaLabel="转换函数"
            onChange={(nextFunction) => {
              const nextInputParameters: Record<string, unknown> = {
                ...inputParameters,
                function: nextFunction,
              }
              if (nextFunction !== 'XML_STRINGIFY') {
                delete nextInputParameters.rootName
              }
              onChange({
                ...task,
                inputParameters: nextInputParameters,
              })
            }}
          />
        </div>
        {isXmlStringify ? (
          <label className="inspector-field full">
            <span>根元素名（可选）</span>
            <VariableExpressionInput
              value={rootName}
              options={variableOptions}
              onChange={(nextRootName) => updateInputParameters({ rootName: nextRootName })}
            />
          </label>
        ) : null}
        <label className="inspector-field full">
          <span>{isStringify ? '序列化表达式' : '解析表达式'}</span>
          <VariableExpressionInput
            value={value}
            options={variableOptions}
            onChange={(nextValue) => updateInputParameters({ value: nextValue })}
          />
        </label>
        <p className="inspector-empty">
          {isXmlStringify
            ? '将对象写成 XML 字符串。可指定根元素名，或直接传入 XML.parse 那种带单一根节点的对象。'
            : isStringify
              ? '将对象、数组或标量写成 JSON 字符串；结果直接作为任务输出。'
              : '将 JSON 或 XML 字符串解析为对象、数组或标量；结果直接作为任务输出。'}
        </p>
      </WorkflowCollapsibleSection>
    </div>
  )
}
