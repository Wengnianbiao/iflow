import { PlusCircleIcon, TrashIcon, XCircleIcon } from '@phosphor-icons/react'
import { formatInlineScript } from '@/components/flow/InlineScriptCode'
import type { WorkflowTask } from '@/types/workflow'
import {
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  valueText,
} from '../../InspectorFields'
import { InlineScriptEditor } from './InlineScriptEditor'
import { TaskPanelHeader } from './TaskPanelHeader'

export function INLINETaskForm({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const rawExpression = String(inputParameters.expression ?? '')
  const expression = formatInlineScript(rawExpression)
  const evaluatorType = 'graaljs'
  const scriptParameters = Object.entries(inputParameters)
    .filter(([key]) => key !== 'expression' && key !== 'evaluatorType')
    .map(([key, value]) => ({ key, value: valueText(value) }))

  const updateInputParameters = (next: Record<string, unknown>) => {
    onChange({
      ...task,
      inputParameters: {
        evaluatorType,
        ...next,
        expression,
      },
    })
  }

  const updateExpression = (nextExpression: string) => {
    onChange({
      ...task,
      inputParameters: {
        evaluatorType,
        ...Object.fromEntries(scriptParameters.map((item) => [item.key, item.value])),
        expression: nextExpression,
      },
    })
  }

  const updateScriptParameter = (index: number, next: { key: string; value: string }) => {
    const items = [...scriptParameters]
    items[index] = next
    updateInputParameters(Object.fromEntries(items.map((item) => [item.key, item.value])))
  }

  const addScriptParameter = () => {
    const key = scriptParameters.length ? `parameter_${scriptParameters.length + 1}` : 'payload'
    updateInputParameters({
      ...Object.fromEntries(scriptParameters.map((item) => [item.key, item.value])),
      [key]: '',
    })
  }

  const deleteScriptParameter = (index: number) => {
    updateInputParameters(Object.fromEntries(scriptParameters.filter((_, itemIndex) => itemIndex !== index).map((item) => [item.key, item.value])))
  }

  return (
    <div className="workflow-form-panel inline-task-inspector">
      <TaskPanelHeader task={task} label="JS脚本" onChange={onChange} />

      <WorkflowCollapsibleSection title="脚本入参">
        <div className="inline-script-parameters">
          {scriptParameters.map((item, index) => (
            <div className="parameter-row two-column inline-script-parameter-row" key={index}>
              <label>
                <span>参数名</span>
                <div className="inline-key-input">
                  <input value={item.key} onChange={(event) => updateScriptParameter(index, { ...item, key: event.target.value })} />
                  <button type="button" className="inline-clear-key" title="清空参数名" onClick={() => updateScriptParameter(index, { ...item, key: '' })}>
                    <XCircleIcon size={16} />
                  </button>
                </div>
              </label>
              <label>
                <span>映射值</span>
                <VariableExpressionInput
                  value={item.value}
                  options={variableOptions}
                  onChange={(value) => updateScriptParameter(index, { ...item, value })}
                />
              </label>
              <button type="button" className="inline-delete-parameter" title="删除参数" onClick={() => deleteScriptParameter(index)}>
                <TrashIcon size={19} />
              </button>
            </div>
          ))}
          {!scriptParameters.length ? <p className="inspector-empty">暂未配置脚本参数</p> : null}
        </div>
        <button type="button" className="inline-add-parameter" onClick={addScriptParameter}>
          <PlusCircleIcon size={16} />
          添加脚本入参
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="脚本代码">
        <InlineScriptEditor value={expression} onChange={updateExpression} />
      </WorkflowCollapsibleSection>

    </div>
  )
}
