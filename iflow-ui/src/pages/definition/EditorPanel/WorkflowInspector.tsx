import type { WorkflowDef } from '@/types/workflow'
import {
  ParameterRows,
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  valueText,
} from './InspectorFields'

export function WorkflowInspector({
  workflow,
  variableOptions,
  onChange,
}: {
  workflow: WorkflowDef
  variableOptions: string[]
  onChange: (workflow: WorkflowDef) => void
}) {
  const inputParameters = workflow.inputParameters ?? []
  const outputParameters = Object.entries(workflow.outputParameters ?? {})

  return (
    <div className="workflow-form-panel">
      <WorkflowCollapsibleSection title="工作流信息">
        <div className="inspector-form-grid">
          <label className="inspector-field">
            <span>工作流名称 <strong>*</strong></span>
            <input required value={workflow.displayName ?? ''} onChange={(event) => onChange({ ...workflow, displayName: event.target.value })} placeholder="用于界面展示，例如：创建档案" />
          </label>
          <label className="inspector-field">
            <span>路由标识 <strong>*</strong></span>
            <input required value={workflow.name} onChange={(event) => onChange({ ...workflow, name: event.target.value })} placeholder="外部系统调用工作流唯一标识" />
          </label>
          <label className="inspector-field full">
            <span>关键字表达式</span>
            <VariableExpressionInput
              value={workflow.correlationExpression ?? ''}
              options={variableOptions}
              onChange={(value) => onChange({ ...workflow, correlationExpression: value })}
            />
          </label>
          <label className="inspector-field full">
            <span>描述 <strong>*</strong></span>
            <textarea required value={workflow.description ?? ''} onChange={(event) => onChange({ ...workflow, description: event.target.value })} placeholder="说明该工作流的业务用途" />
          </label>
        </div>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="输入参数">
        <ParameterRows
          items={inputParameters.map((key) => ({ key, value: '' }))}
          keyLabel="参数名"
          onChange={(index, next) => {
            const values = [...inputParameters]
            values[index] = next.key
            onChange({ ...workflow, inputParameters: values })
          }}
          onDelete={(index) => onChange({ ...workflow, inputParameters: inputParameters.filter((_, itemIndex) => itemIndex !== index) })}
        />
        <button type="button" className="add-parameter-button" onClick={() => onChange({ ...workflow, inputParameters: [...inputParameters, `input_${inputParameters.length + 1}`] })}>
          + 添加输入参数
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="输出参数">
        <ParameterRows
          items={outputParameters.map(([key, value]) => ({ key, value: valueText(value) }))}
          keyLabel="参数名"
          valueLabel="映射值"
          onChange={(index, next) => {
            const entries = [...outputParameters]
            entries[index] = [next.key, next.value]
            onChange({ ...workflow, outputParameters: Object.fromEntries(entries) })
          }}
          onDelete={(index) => onChange({ ...workflow, outputParameters: Object.fromEntries(outputParameters.filter((_, itemIndex) => itemIndex !== index)) })}
          variableOptions={variableOptions}
        />
        <button type="button" className="add-parameter-button" onClick={() => onChange({ ...workflow, outputParameters: { ...(workflow.outputParameters ?? {}), [`output_${outputParameters.length + 1}`]: '' } })}>
          + 添加输出参数
        </button>
      </WorkflowCollapsibleSection>
    </div>
  )
}
