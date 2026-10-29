import { Switch } from '@mui/material'
import type { WorkflowDef } from '@/types/workflow'
import { buildWorkflowInputOptions } from '@/flow/workflowEditor'
import { WorkflowTagsInput } from '@/components/ui/WorkflowTagsInput'
import { useWorkflowDefs } from '@/utils/query'
import { collectWorkflowTags, normalizeWorkflowTags } from '@/utils/workflowTags'
import { useFieldDraft, useValidationReadOnly, ValidationField } from '../validation/ValidationScope'
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
  const { data: definitions = [] } = useWorkflowDefs()
  const outputParameters = Object.entries(workflow.outputParameters ?? {})
  const readOnly = useValidationReadOnly()
  const timeoutSecondsDraft = useFieldDraft('/timeoutSeconds', String(workflow.timeoutSeconds ?? ''))
  const timeoutPolicyDraft = useFieldDraft('/timeoutPolicy', workflow.timeoutPolicy ?? 'TIME_OUT_WF')
  const timeoutEnabled = (workflow.timeoutSeconds ?? 0) !== 0
    || (workflow.timeoutPolicy != null && workflow.timeoutPolicy !== 'TIME_OUT_WF')
    || Boolean(timeoutSecondsDraft.error || timeoutPolicyDraft.error)

  const toggleTimeout = (checked: boolean) => {
    if (checked) {
      timeoutSecondsDraft.invalid('', '请填写超时时间（秒）')
      timeoutPolicyDraft.invalid('', '请选择超时策略')
    } else {
      onChange({ ...workflow, timeoutSeconds: 0, timeoutPolicy: 'TIME_OUT_WF' })
      timeoutSecondsDraft.clear()
      timeoutPolicyDraft.clear()
    }
  }

  return (
    <div className="workflow-form-panel">
      <WorkflowCollapsibleSection title="工作流信息">
        <div className="inspector-form-grid">
          <ValidationField path="/name"><label className="inspector-field">
            <span>工作流名称 <strong>*</strong></span>
            <input required value={workflow.name ?? ''} onChange={(event) => onChange({ ...workflow, name: event.target.value })} placeholder="用于界面展示，例如：创建档案" />
          </label></ValidationField>
          <ValidationField path="/route"><label className="inspector-field">
            <span>路由 <strong>*</strong></span>
            <input required value={workflow.route} onChange={(event) => onChange({ ...workflow, route: event.target.value })} placeholder="外部系统调用工作流唯一标识" />
          </label></ValidationField>
          <label className="inspector-field full">
            <span>关键字表达式</span>
            <VariableExpressionInput
              value={workflow.correlationExpression ?? ''}
              options={buildWorkflowInputOptions(workflow)}
              onChange={(value) => onChange({ ...workflow, correlationExpression: value })}
            />
          </label>
          <ValidationField path="/description"><label className="inspector-field full">
            <span>描述 <strong>*</strong></span>
            <textarea required value={workflow.description ?? ''} onChange={(event) => onChange({ ...workflow, description: event.target.value })} placeholder="说明该工作流的业务用途" />
          </label></ValidationField>
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

      <WorkflowCollapsibleSection title="执行策略">
        <div className="task-policy-field">
          <div className="task-policy-toggle">
            <label>
              <Switch checked={timeoutEnabled} disabled={readOnly}
                slotProps={{ input: { role: 'switch', 'aria-label': '工作流超时' } }}
                onChange={(_, checked) => toggleTimeout(checked)} />
              <span>工作流超时</span>
            </label>
            <small>{timeoutEnabled ? '配置整个工作流的超时时间及处理策略' : '继承系统超时时间，超时后终止工作流'}</small>
          </div>
          {timeoutEnabled ? <div className="inspector-form-grid">
            <ValidationField path="/timeoutSeconds"><label className="inspector-field">
              <span>超时时间（秒） <strong>*</strong></span>
              <input type="number" min={0} step={1} required disabled={readOnly} aria-label="超时时间（秒）"
                value={timeoutSecondsDraft.value} placeholder="请输入非负整数"
                onChange={(event) => {
                  if (event.target.value === '') timeoutSecondsDraft.invalid('', '请填写超时时间（秒）')
                  else {
                    onChange({ ...workflow, timeoutSeconds: Number(event.target.value) })
                    timeoutSecondsDraft.clear()
                  }
                }} />
              <small>0 表示继承系统配置</small>
            </label></ValidationField>
            <ValidationField path="/timeoutPolicy"><label className="inspector-field">
              <span>超时策略 <strong>*</strong></span>
              <select value={timeoutPolicyDraft.value} required disabled={readOnly} aria-label="超时策略"
                onChange={(event) => {
                  onChange({ ...workflow, timeoutPolicy: event.target.value })
                  timeoutPolicyDraft.clear()
                }}>
                <option value="" disabled>请选择超时策略</option>
                <option value="TIME_OUT_WF">超时终止</option>
                <option value="ALERT_ONLY">仅告警</option>
              </select>
              {workflow.timeoutPolicy === 'ALERT_ONLY' ? <small>超时仅记录告警，工作流继续执行，同步调用继续等待。</small> : null}
            </label></ValidationField>
          </div> : null}
        </div>
      </WorkflowCollapsibleSection>
    </div>
  )
}
