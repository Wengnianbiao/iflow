import { VariableExpressionInput, WorkflowCollapsibleSection, valueText } from '../../InspectorFields'
import { ValidationField } from '../../../validation/ValidationScope'
import { TaskPanelHeader } from './TaskPanelHeader'
import type { TaskFormProps } from './types'

export function SetKeywordTaskForm({ task, variableOptions, inputVariableOptions = variableOptions, onChange }: TaskFormProps) {
  const inputParameters = task.inputParameters ?? {}

  return (
    <div className="workflow-form-panel">
      <TaskPanelHeader task={task} label="设置关键字" onChange={onChange} />
      <WorkflowCollapsibleSection title="输入参数">
        <ValidationField path="/inputParameters/keyword">
          <label className="inspector-field full">
            <span>关键字 <strong>*</strong></span>
            <VariableExpressionInput
              value={valueText(inputParameters.keyword)}
              options={inputVariableOptions}
              onChange={(keyword) => onChange({ ...task, inputParameters: { ...inputParameters, keyword } })}
            />
          </label>
        </ValidationField>
      </WorkflowCollapsibleSection>
    </div>
  )
}
