import { PlusCircleIcon } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'
import {
  ParameterRows,
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  numberOrUndefined,
  valueText,
} from '../../InspectorFields'
import { InlineScriptEditor } from './InlineScriptEditor'
import { TaskPanelHeader } from './TaskPanelHeader'
import { isRecord } from './formUtils'

export function WebServiceTaskForm({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const headers = isRecord(inputParameters.headers) ? inputParameters.headers : {}
  const templateParameters = isRecord(inputParameters.templateParameters) ? inputParameters.templateParameters : {}
  const headerRows = Object.entries(headers).map(([key, value]) => ({ key, value: valueText(value) }))
  const templateParameterRows = Object.entries(templateParameters).map(([key, value]) => ({ key, value: valueText(value) }))

  const updateInputParameters = (next: Record<string, unknown>) => {
    onChange({ ...task, inputParameters: { ...inputParameters, ...next } })
  }

  const updateRows = (
    field: 'headers' | 'templateParameters',
    rows: Array<{ key: string; value: string }>,
  ) => {
    updateInputParameters({ [field]: Object.fromEntries(rows.map((item) => [item.key, item.value])) })
  }

  const addRow = (field: 'headers' | 'templateParameters') => {
    const rows = field === 'headers' ? headerRows : templateParameterRows
    const values = field === 'headers' ? headers : templateParameters
    let index = rows.length + 1
    let key = field === 'headers' ? `header_${index}` : `parameter_${index}`
    if (field === 'templateParameters' && rows.length === 0) key = 'payload'
    while (Object.prototype.hasOwnProperty.call(values, key)) {
      index += 1
      key = field === 'headers' ? `header_${index}` : `parameter_${index}`
    }
    updateRows(field, [...rows, { key, value: '' }])
  }

  return (
    <div className="workflow-form-panel webservice-task-inspector">
      <TaskPanelHeader task={task} label="WEBSERVICE" onChange={onChange} />

      <WorkflowCollapsibleSection title="请求详情">
        <div className="inspector-form-grid webservice-request-grid">
          <label className="inspector-field webservice-uri-field">
            <span>服务地址 <strong>*</strong></span>
            <VariableExpressionInput value={String(inputParameters.uri ?? '')} options={variableOptions} onChange={(uri) => updateInputParameters({ uri })} />
          </label>
          <label className="inspector-field">
            <span>Content-Type</span>
            <input value={String(inputParameters.contentType ?? '')} onChange={(event) => updateInputParameters({ contentType: event.target.value })} placeholder="text/xml;charset=UTF-8" />
          </label>
          <label className="inspector-field">
            <span>connectionTimeOut (ms)</span>
            <input value={String(inputParameters.connectionTimeOut ?? '')} onChange={(event) => updateInputParameters({ connectionTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
          <label className="inspector-field">
            <span>readTimeOut (ms)</span>
            <input value={String(inputParameters.readTimeOut ?? '')} onChange={(event) => updateInputParameters({ readTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
        </div>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="请求头">
        <ParameterRows
          items={headerRows}
          keyLabel="Header"
          valueLabel="映射值"
          variableOptions={variableOptions}
          onChange={(index, next) => {
            const rows = [...headerRows]
            rows[index] = next
            updateRows('headers', rows)
          }}
          onDelete={(index) => updateRows('headers', headerRows.filter((_, itemIndex) => itemIndex !== index))}
        />
        <button type="button" className="add-parameter-button" onClick={() => addRow('headers')}>
          <PlusCircleIcon size={15} />
          添加请求头
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="模板参数">
        <ParameterRows
          items={templateParameterRows}
          keyLabel="参数名"
          valueLabel="映射值"
          variableOptions={variableOptions}
          onChange={(index, next) => {
            const rows = [...templateParameterRows]
            rows[index] = next
            updateRows('templateParameters', rows)
          }}
          onDelete={(index) => updateRows('templateParameters', templateParameterRows.filter((_, itemIndex) => itemIndex !== index))}
        />
        <button type="button" className="add-parameter-button" onClick={() => addRow('templateParameters')}>
          <PlusCircleIcon size={15} />
          添加模板参数
        </button>
        <p className="field-help">映射值先解析工作流表达式；SOAP 模板通过 <code>{'${$.payload.xxx}'}</code> 读取。参数名不能使用 <code>workflow</code> 或任务引用名。</p>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="SOAP 模板">
        <InlineScriptEditor
          value={String(inputParameters.soapTemplate ?? '')}
          onChange={(soapTemplate) => updateInputParameters({ soapTemplate })}
          minHeight={360}
          language="xml"
          languageLabel="XML / SOAP"
          codeLabel="soapTemplate"
          modalTitle="SOAP XML 模板编辑器"
        />
        <p className="field-help">保留兼容语法 <code>{'${workflow.input.xxx}'}</code>；数组、重复标签或需 XML 转义的数据建议由前置 INLINE 生成完整 XML。</p>
      </WorkflowCollapsibleSection>
    </div>
  )
}

