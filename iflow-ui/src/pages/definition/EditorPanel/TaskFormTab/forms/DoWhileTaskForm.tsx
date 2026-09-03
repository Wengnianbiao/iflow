import { PlusCircleIcon } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'
import {
  ParameterRows,
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  valueText,
} from '../../InspectorFields'
import { InlineScriptEditor } from './InlineScriptEditor'
import { TaskPanelHeader } from './TaskPanelHeader'

export function DoWhileTaskForm({
  task,
  variableOptions,
  onChange,
}: {
  task: WorkflowTask
  variableOptions: string[]
  onChange: (task: WorkflowTask) => void
}) {
  const inputParameters = task.inputParameters ?? {}
  const evaluatorType = String(inputParameters.evaluatorType ?? task.evaluatorType ?? 'graaljs')
  const parameters = Object.entries(inputParameters)
    .filter(([key]) => key !== 'evaluatorType')
    .map(([key, value]) => ({ key, value: valueText(value) }))

  const updateParameters = (items: Array<{ key: string; value: string }>) => {
    onChange({
      ...task,
      evaluatorType,
      inputParameters: { evaluatorType, ...Object.fromEntries(items.map((item) => [item.key, item.value])) },
    })
  }

  return (
    <div className="workflow-form-panel do-while-task-inspector">
      <TaskPanelHeader task={task} label="循环" onChange={onChange} />
      <WorkflowCollapsibleSection title="循环条件">
        <label className="inspector-field full">
          <span>求值器</span>
          <select
            value={evaluatorType}
            onChange={(event) => {
              const nextEvaluator = event.target.value
              onChange({
                ...task,
                evaluatorType: nextEvaluator,
                inputParameters: { ...inputParameters, evaluatorType: nextEvaluator },
              })
            }}
          >
            <option value="graaljs">GraalJS</option>
            <option value="value-param">参数值</option>
          </select>
        </label>
        {evaluatorType === 'value-param' ? (
          <label className="inspector-field full">
            <span>Loop Condition</span>
            <VariableExpressionInput
              value={task.loopCondition ?? ''}
              options={variableOptions}
              onChange={(loopCondition) => onChange({ ...task, loopCondition })}
            />
          </label>
        ) : (
          <InlineScriptEditor
            value={task.loopCondition ?? ''}
            onChange={(loopCondition) => onChange({ ...task, loopCondition })}
            minHeight={190}
            codeLabel="Loop Condition"
            modalTitle="DO WHILE 循环条件"
          />
        )}
        <p className="field-help">循环体每次完成后求值；返回 true 继续下一轮，返回 false 结束循环。上下文包含 <code>iteration</code> 与 <code>workflow.input</code>。</p>
      </WorkflowCollapsibleSection>
      <WorkflowCollapsibleSection title="条件参数">
        <ParameterRows
          items={parameters}
          keyLabel="参数名"
          valueLabel="映射值"
          variableOptions={variableOptions}
          onChange={(index, item) => updateParameters(parameters.map((current, currentIndex) => currentIndex === index ? item : current))}
          onDelete={(index) => updateParameters(parameters.filter((_, currentIndex) => currentIndex !== index))}
        />
        <button
          type="button"
          className="add-parameter-button"
          onClick={() => updateParameters([...parameters, { key: `parameter_${parameters.length + 1}`, value: '' }])}
        >
          <PlusCircleIcon size={16} />
          添加条件参数
        </button>
        <p className="field-help">循环体当前包含 {task.loopOver?.length ?? 0} 个任务；从画布循环节点下方的 + 添加。</p>
      </WorkflowCollapsibleSection>
    </div>
  )
}
