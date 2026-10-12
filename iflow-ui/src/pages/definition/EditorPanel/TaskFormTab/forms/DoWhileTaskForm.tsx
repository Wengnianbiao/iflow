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
          <label><input type="radio" name={`loop-strategy-${task.taskReferenceName}`} checked={!conflicting && strategy === 'array'} onChange={() => chooseStrategy('array')} />遍历数组</label>
          <label><input type="radio" name={`loop-strategy-${task.taskReferenceName}`} checked={!conflicting && strategy === 'js'} onChange={() => chooseStrategy('js')} />JS 条件循环</label>
        </div>
      </WorkflowCollapsibleSection>
      {strategy === 'array' ? (
        <WorkflowCollapsibleSection title="数组表达式">
          <ValidationField path="/loopItems">
            <label className="inspector-field full">
              <span>数组表达式</span>
              <VariableExpressionInput value={task.loopItems ?? ''} options={inputVariableOptions} onChange={(loopItems) => onChange({ ...task, loopItems })} />
            </label>
          </ValidationField>
          <p className="field-help">填写返回数组的表达式，例如 <code>{'${workflow.input.orders}'}</code>。进入循环时确定数组，空数组执行零轮。</p>
          <p className="field-help">当前元素：<code>{`\${${task.taskReferenceName}.output.loopItem}`}</code>；从 0 开始的索引：<code>{`\${${task.taskReferenceName}.output.loopIndex}`}</code>。</p>
        </WorkflowCollapsibleSection>
      ) : (
        <>
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
          <WorkflowCollapsibleSection title="循环条件">
            <ValidationField path="/loopCondition">
              <InlineScriptEditor value={task.loopCondition ?? ''} onChange={(loopCondition) => onChange({ ...task, loopCondition })} minHeight={190} codeLabel="JS 循环条件" modalTitle="DO WHILE 循环条件" />
            </ValidationField>
            <p className="field-help">使用 GraalJS，每轮循环体完成后判断：返回 true 继续，返回 false 结束。填写 false 仍会先执行一轮；可使用 $.iteration、$.workflow.input 和本轮任务输出。</p>
          </WorkflowCollapsibleSection>
        </>
      )}
      <ValidationField path="/loopOver">
        <p className="field-help" tabIndex={0}>循环体当前包含 {task.loopOver?.length ?? 0} 个任务；从画布循环节点下方的 + 添加。</p>
      </ValidationField>
    </div>
  )
}
