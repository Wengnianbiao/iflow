import { PlusCircleIcon } from '@phosphor-icons/react'
import { changeDoWhileStrategy, doWhileStrategy } from '@/tasks/doWhile'
import { editableParameterRows, parameterValues, type EditableParameter } from '@/flow/parameterValues'
import { ParameterRows, VariableExpressionInput, WorkflowCollapsibleSection } from '../../InspectorFields'
import { ValidationField } from '../../../validation/ValidationScope'
import { InlineScriptEditor } from './InlineScriptEditor'
import { TaskPanelHeader } from './TaskPanelHeader'
import type { TaskFormProps } from './types'

export function DoWhileTaskForm({ task, variableOptions, inputVariableOptions = variableOptions, onChange }: TaskFormProps) {
  const inputParameters = task.inputParameters ?? {}
  const strategy = doWhileStrategy(task)
  const conflicting = task.loopItems != null && task.loopCondition != null
  const parameters = editableParameterRows(inputParameters, ['evaluatorType'])
  const updateParameters = (items: EditableParameter[]) => onChange({ ...task, inputParameters: parameterValues(items) })
  const chooseStrategy = (next: 'array' | 'js') => onChange(changeDoWhileStrategy(task, next))

  return (
    <div className="workflow-form-panel do-while-task-inspector">
      <TaskPanelHeader task={task} label="循环" onChange={onChange} />
      <WorkflowCollapsibleSection title="循环方式">
        <div className="do-while-strategy-options" role="radiogroup" aria-label="循环方式">
          <label><input type="radio" name={`loop-strategy-${task.taskReferenceName}`} checked={!conflicting && strategy === 'array'} onChange={() => chooseStrategy('array')} />数组遍历</label>
          <label><input type="radio" name={`loop-strategy-${task.taskReferenceName}`} checked={!conflicting && strategy === 'js'} onChange={() => chooseStrategy('js')} />JS 条件循环</label>
        </div>
        {strategy === 'array' ? (
          <ValidationField path="/loopItems">
            <label className="inspector-field full">
              <span>数组表达式</span>
              <VariableExpressionInput value={task.loopItems ?? ''} options={inputVariableOptions} onChange={(loopItems) => onChange({ ...task, loopItems })} />
            </label>
          </ValidationField>
        ) : (
          <ValidationField path="/loopCondition">
            <InlineScriptEditor value={task.loopCondition ?? ''} onChange={(loopCondition) => onChange({ ...task, loopCondition })} modalTitle="JS 循环条件" />
          </ValidationField>
        )}
      </WorkflowCollapsibleSection>
      {strategy === 'js' ? (
        <WorkflowCollapsibleSection title="脚本参数">
          <ValidationField path="/inputParameters">
            <ParameterRows
              items={parameters}
              keyLabel="参数名"
              valueLabel="映射值"
              variableOptions={variableOptions}
              onChange={(index, item) => updateParameters(parameters.map((current, currentIndex) => currentIndex === index ? { ...current, ...item } : current))}
              onDelete={(index) => updateParameters(parameters.filter((_, currentIndex) => currentIndex !== index))}
            />
          </ValidationField>
          <button type="button" className="add-parameter-button" onClick={() => updateParameters([...parameters, { key: `parameter_${parameters.length + 1}`, value: '' }])}>
            <PlusCircleIcon size={16} />添加脚本参数
          </button>
        </WorkflowCollapsibleSection>
      ) : null}
    </div>
  )
}
