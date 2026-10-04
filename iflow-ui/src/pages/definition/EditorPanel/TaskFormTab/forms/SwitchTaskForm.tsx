import { useState } from 'react'
import { PlusCircleIcon, TrashIcon } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'
import { editableParameterRows, parameterValues } from '@/flow/parameterValues'
import {
  ParameterRows,
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  valueText,
} from '../../InspectorFields'
import { InlineScriptEditor } from './InlineScriptEditor'
import { TaskPanelHeader } from './TaskPanelHeader'
import { ValidationField, useTaskFieldDraft } from '../../../validation/ValidationScope'
import { pointerKey } from '../../../validation/validateWorkflow'

function SwitchCaseRow({
  name,
  taskCount,
  caseNames,
  onRename,
  onDelete,
}: {
  name: string
  taskCount: number
  caseNames: string[]
  onRename: (nextName: string) => void
  onDelete: () => void
}) {
  const [draft, setDraft] = useState(name)
  const path = `/decisionCases/${pointerKey(name)}`
  const fieldDraft = useTaskFieldDraft(path, name)
  const error = fieldDraft.error
  const value = error ? fieldDraft.value : draft

  const commit = () => {
    const nextName = value.trim()
    if (!nextName) {
      fieldDraft.invalid(value, '分支名称不能为空')
      return
    }
    if (nextName !== name && caseNames.includes(nextName)) {
      fieldDraft.invalid(value, '分支名称不能重复')
      return
    }
    fieldDraft.clear()
    if (nextName !== name) onRename(nextName)
  }

  return (
    <ValidationField path={path}><div className={`switch-case-row ${error ? 'has-error' : ''}`}>
      <label>
        <span>匹配值</span>
        <input
          value={value}
          aria-invalid={Boolean(error)}
          onChange={(event) => {
            setDraft(event.target.value)
            if (event.target.value !== name) fieldDraft.invalid(event.target.value, '请完成分支名称编辑；离开字段后应用')
            else fieldDraft.clear()
          }}
          onBlur={commit}
          onKeyDown={(event) => {
            if (event.key === 'Enter') event.currentTarget.blur()
            if (event.key === 'Escape') {
              event.preventDefault()
              setDraft(name)
              fieldDraft.clear()
            }
          }}
        />
      </label>
      <span className="switch-case-count">{taskCount} 个任务</span>
      <button type="button" title="删除分支" aria-label={`删除分支 ${name}`} disabled={Boolean(error)} onClick={onDelete}>
        <TrashIcon size={18} />
      </button>
    </div></ValidationField>
  )
}

export function SwitchTaskForm({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const parameterRows = editableParameterRows(inputParameters)
  const evaluatorType = task.evaluatorType ?? 'value-param'
  const expression = task.expression ?? ''
  const decisionCases = task.decisionCases ?? {}
  const isScriptEvaluator = evaluatorType === 'graaljs'

  const updateParameter = (index: number, next: { key: string; value: string }) => {
    const rows = [...parameterRows]
    const previous = rows[index]
    rows[index] = { ...rows[index], ...next }
    onChange({
      ...task,
      expression: isScriptEvaluator && expression === `$.${previous.key}` ? `$.${next.key}` : expression,
      inputParameters: parameterValues(rows),
    })
  }

  const addParameter = () => {
    let index = parameterRows.length + 1
    let key = index === 1 ? 'switchCaseValue' : `parameter_${index}`
    while (key in inputParameters) {
      index += 1
      key = `parameter_${index}`
    }
    onChange({
      ...task,
      expression: isScriptEvaluator && !expression ? `$.${key}` : expression,
      inputParameters: { ...inputParameters, [key]: '' },
    })
  }

  const deleteParameter = (index: number) => {
    const deletedKey = parameterRows[index]?.key
    const nextRows = parameterRows.filter((_, itemIndex) => itemIndex !== index)
    onChange({
      ...task,
      expression: isScriptEvaluator && expression === `$.${deletedKey}` ? (nextRows[0]?.key ? `$.${nextRows[0].key}` : '') : expression,
      inputParameters: parameterValues(nextRows),
    })
  }

  const changeEvaluatorType = (nextType: 'value-param' | 'graaljs') => {
    if (nextType === 'value-param') {
      const referencedKey = /^\$\.([A-Za-z_$][\w$]*)$/.exec(expression.trim())?.[1]
      const mappedValue = referencedKey && Object.prototype.hasOwnProperty.call(inputParameters, referencedKey)
        ? inputParameters[referencedKey]
        : parameterRows[0]?.value
      onChange({ ...task, evaluatorType: nextType, expression: mappedValue == null ? '' : valueText(mappedValue) })
      return
    }

    const existingKey = parameterRows.find((item) => item.key)?.key
    const parameterKey = existingKey ?? 'switchCaseValue'
    onChange({
      ...task,
      evaluatorType: nextType,
      expression: isScriptEvaluator ? expression : `$.${parameterKey}`,
      inputParameters: existingKey ? inputParameters : { ...inputParameters, [parameterKey]: expression },
    })
  }

  const renameCase = (previousName: string, nextName: string) => {
    const entries = Object.entries(decisionCases).map(([name, tasks]) => [name === previousName ? nextName : name, tasks] as const)
    onChange({ ...task, decisionCases: Object.fromEntries(entries) })
  }

  const addCase = () => {
    let index = Object.keys(decisionCases).length + 1
    let name = `switch_case_${index}`
    while (name in decisionCases) {
      index += 1
      name = `switch_case_${index}`
    }
    onChange({ ...task, decisionCases: { ...decisionCases, [name]: [] } })
  }

  const deleteCase = (name: string) => {
    const { [name]: _removed, ...nextCases } = decisionCases
    onChange({ ...task, decisionCases: nextCases })
  }

  return (
    <div className="workflow-form-panel switch-task-inspector">
      <TaskPanelHeader task={task} label="分支路由" onChange={onChange} />

      <WorkflowCollapsibleSection title="求值规则">
        <div className="switch-evaluator-heading">
          <span>求值方式</span>
          <div className="switch-evaluator-options" role="radiogroup" aria-label="求值方式">
            <label>
              <input
                type="radio"
                name="switch-evaluator-type"
                value="value-param"
                checked={evaluatorType === 'value-param'}
                onChange={() => changeEvaluatorType('value-param')}
              />
              <span>参数值</span>
            </label>
            <label>
              <input
                type="radio"
                name="switch-evaluator-type"
                value="graaljs"
                checked={isScriptEvaluator}
                onChange={() => changeEvaluatorType('graaljs')}
              />
              <span>脚本</span>
            </label>
          </div>
        </div>
        {isScriptEvaluator ? (
          <div className="switch-script-evaluator">
            <div className="switch-script-parameters">
              <h3>脚本入参</h3>
              <ParameterRows
                items={parameterRows}
                keyLabel="参数名"
                valueLabel="映射值"
                variableOptions={variableOptions}
                onChange={updateParameter}
                onDelete={deleteParameter}
              />
              <button type="button" className="add-parameter-button" onClick={addParameter}>
                <PlusCircleIcon size={16} />
                添加脚本入参
              </button>
            </div>
            <InlineScriptEditor value={expression} onChange={(value) => onChange({ ...task, expression: value })} minHeight={190} />
            <p className="field-help">通过 <code>$.参数名</code> 读取上方脚本入参，脚本返回值用于匹配分支条件。</p>
          </div>
        ) : (
          <label className="inspector-field switch-expression-field">
            <span>路由取值</span>
            <VariableExpressionInput
              value={expression}
              options={variableOptions}
              onChange={(value) => onChange({ ...task, expression: value })}
            />
            <small>直接填写 <code>${'{...}'}</code> 表达式，表达式结果用于匹配下方分支条件。</small>
          </label>
        )}
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="分支条件">
        <div className="switch-case-list">
          {Object.entries(decisionCases).map(([name, tasks]) => (
            <SwitchCaseRow
              key={name}
              name={name}
              taskCount={tasks.length}
              caseNames={Object.keys(decisionCases)}
              onRename={(nextName) => renameCase(name, nextName)}
              onDelete={() => deleteCase(name)}
            />
          ))}
          <div className="switch-default-case">
            <span>默认分支</span>
            <strong>{task.defaultCase?.length ?? 0} 个任务</strong>
          </div>
          {!Object.keys(decisionCases).length ? <p className="inspector-empty">暂未配置分支条件</p> : null}
        </div>
        <button type="button" className="add-parameter-button switch-add-case" onClick={addCase}>
          <PlusCircleIcon size={16} />
          添加分支条件
        </button>
      </WorkflowCollapsibleSection>
    </div>
  )
}
