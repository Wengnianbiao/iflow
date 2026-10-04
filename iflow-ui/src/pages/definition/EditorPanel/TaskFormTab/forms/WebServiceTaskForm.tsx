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
import { templateParameterText, updateTemplateParameter } from './templateParameters'

export function WebServiceTaskForm({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const headers = isRecord(inputParameters.headers) ? inputParameters.headers : {}
  const templateParameters = isRecord(inputParameters.templateParameters) ? inputParameters.templateParameters : {}
  const headerRows = Object.entries(headers).map(([key, value]) => ({ key, value: valueText(value) }))
  const templateParameterEntries = Object.entries(templateParameters)
  const templateParameterRows = templateParameterEntries.map(([key, value]) => ({ key, value: templateParameterText(value) }))

  const updateInputParameters = (next: Record<string, unknown>) => {
    onChange({ ...task, inputParameters: { ...inputParameters, ...next } })
  }

  const updateRows = (
    rows: Array<{ key: string; value: string }>,
  ) => {
    updateInputParameters({ headers: Object.fromEntries(rows.map((item) => [item.key, item.value])) })
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
    updateInputParameters({ [field]: { ...values, [key]: '' } })
  }

  return (
    <div className="workflow-form-panel webservice-task-inspector">
      <TaskPanelHeader task={task} label="WEBSERVICE" onChange={onChange} />

      <WorkflowCollapsibleSection title="请求详情">
        <div className="inspector-form-grid webservice-request-grid">
          <label className="inspector-field">
            <span>Method</span>
            <input value="POST" readOnly title="SOAP 请求使用 POST" />
          </label>
          <label className="inspector-field webservice-uri-field">
            <span>URL <strong>*</strong></span>
            <VariableExpressionInput value={String(inputParameters.uri ?? '')} options={variableOptions} onChange={(uri) => updateInputParameters({ uri })} />
          </label>
          <div className="inspector-form-grid webservice-request-options-grid">
            <label className="inspector-field webservice-content-type-field">
              <span>Content-Type</span>
              <input value={String(inputParameters.contentType ?? '')} onChange={(event) => updateInputParameters({ contentType: event.target.value })} placeholder="text/xml;charset=UTF-8" />
            </label>
            <label className="inspector-field">
              <span>ConnectionTimeout(ms)</span>
              <input value={String(inputParameters.connectionTimeOut ?? '')} onChange={(event) => updateInputParameters({ connectionTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
            </label>
            <label className="inspector-field">
              <span>ReadTimeout(ms)</span>
              <input value={String(inputParameters.readTimeOut ?? '')} onChange={(event) => updateInputParameters({ readTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
            </label>
          </div>
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
            updateRows(rows)
          }}
          onDelete={(index) => updateRows(headerRows.filter((_, itemIndex) => itemIndex !== index))}
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
          onChange={(index, next) => updateInputParameters({ templateParameters: updateTemplateParameter(templateParameters, index, next) })}
          onDelete={(index) => updateInputParameters({ templateParameters: Object.fromEntries(templateParameterEntries.filter((_, itemIndex) => itemIndex !== index)) })}
        />
        <button type="button" className="add-parameter-button" onClick={() => addRow('templateParameters')}>
          <PlusCircleIcon size={15} />
          添加模板参数
        </button>
        <p className="field-help">参数值先由框架解析 <code>{'${...}'}</code>；SOAP 模板通过 <code>$.payload.xxx</code> 读取。支持 JSON 对象、数组、数字、布尔值和 null；改名保留原类型。</p>
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
        <p className="field-help">使用 <code>$.patient.name</code>、<code>{"$.items[0]['display name']"}</code> 读取具体字段；文本和属性自动进行 XML 转义，CDATA 保留文本。缺失路径会使任务失败，null 输出空文本；<code>$$.path</code> 输出字面量 <code>$.path</code>。</p>
        <p className="field-help">条件、循环或完整 XML 由前置 INLINE / MAPPING 准备；整体模板可使用 <code>{'${prepare.output.xml}'}</code>。不支持旧语法 <code>{'${$.xxx}'}</code>。</p>
      </WorkflowCollapsibleSection>
    </div>
  )
}
