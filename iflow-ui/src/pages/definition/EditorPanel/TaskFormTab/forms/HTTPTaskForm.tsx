import { useState } from 'react'
import { PlusCircleIcon } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'
import {
  ParameterRows,
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  numberOrUndefined,
  prettyJson,
  valueText,
} from '../../InspectorFields'
import { InlineScriptEditor } from './InlineScriptEditor'
import { TaskPanelHeader } from './TaskPanelHeader'
import { isRecord } from './formUtils'

export function HTTPTaskForm({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const hasNestedRequest = isRecord(inputParameters.http_request)
  const request = hasNestedRequest ? inputParameters.http_request as Record<string, unknown> : inputParameters
  const headers = isRecord(request.headers) ? request.headers : {}
  const headerRows = Object.entries(headers).map(([key, value]) => ({ key, value: valueText(value) }))
  const bodyParameters = isRecord(request.body) ? request.body : {}
  const bodyParameterRows = Object.entries(bodyParameters).map(([key, value]) => ({ key, value: valueText(value) }))
  const [bodyJson, setBodyJson] = useState(prettyJson(request.body ?? {}))
  const [bodyError, setBodyError] = useState('')
  const [bodyViewType, setBodyViewType] = useState<'JSON' | 'PARAMETERS'>('JSON')

  const updateHttpRequest = (next: Record<string, unknown>) => {
    const nextRequest = { ...request, ...next }
    onChange({
      ...task,
      inputParameters: hasNestedRequest ? { ...inputParameters, http_request: nextRequest } : nextRequest,
    })
  }

  const updateHeader = (index: number, next: { key: string; value: string }) => {
    const rows = [...headerRows]
    rows[index] = next
    updateHttpRequest({ headers: Object.fromEntries(rows.map((item) => [item.key, item.value])) })
  }

  const addHeader = () => {
    updateHttpRequest({
      headers: {
        ...headers,
        [`header_${headerRows.length + 1}`]: '',
      },
    })
  }

  const deleteHeader = (index: number) => {
    updateHttpRequest({ headers: Object.fromEntries(headerRows.filter((_, itemIndex) => itemIndex !== index).map((item) => [item.key, item.value])) })
  }

  const updateBodyJson = (nextValue: string) => {
    setBodyJson(nextValue)
    try {
      const parsed = nextValue.trim() ? JSON.parse(nextValue) : {}
      updateHttpRequest({ body: parsed })
      setBodyError('')
    } catch (error) {
      setBodyError(error instanceof Error ? error.message : 'JSON 格式不正确')
    }
  }

  const updateBodyParameters = (rows: Array<{ key: string; value: string }>) => {
    const nextBody = Object.fromEntries(rows.map((item) => [item.key, item.value]))
    setBodyJson(prettyJson(nextBody))
    setBodyError('')
    updateHttpRequest({ body: nextBody })
  }

  const updateBodyParameter = (index: number, next: { key: string; value: string }) => {
    const rows = [...bodyParameterRows]
    rows[index] = next
    updateBodyParameters(rows)
  }

  const addBodyParameter = () => {
    let index = bodyParameterRows.length + 1
    let key = `parameter_${index}`
    while (Object.prototype.hasOwnProperty.call(bodyParameters, key)) {
      index += 1
      key = `parameter_${index}`
    }
    updateBodyParameters([...bodyParameterRows, { key, value: '' }])
  }

  const deleteBodyParameter = (index: number) => {
    updateBodyParameters(bodyParameterRows.filter((_, itemIndex) => itemIndex !== index))
  }

  return (
    <div className="workflow-form-panel http-task-inspector">
      <TaskPanelHeader task={task} label="HTTP" onChange={onChange} />

      <WorkflowCollapsibleSection title="请求详情">
        <div className="inspector-form-grid http-request-grid">
          <label className="inspector-field">
            <span>Method</span>
            <select value={String(request.method ?? 'GET')} onChange={(event) => updateHttpRequest({ method: event.target.value })}>
              {['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map((method) => (
                <option value={method} key={method}>{method}</option>
              ))}
            </select>
          </label>
          <label className="inspector-field">
            <span>URL</span>
            <VariableExpressionInput value={String(request.uri ?? '')} options={variableOptions} onChange={(value) => updateHttpRequest({ uri: value })} />
          </label>
          <label className="inspector-field">
            <span>Content-Type</span>
            <input value={String(request.contentType ?? '')} onChange={(event) => updateHttpRequest({ contentType: event.target.value })} placeholder="application/json" />
          </label>
          <label className="inspector-field">
            <span>Accept</span>
            <input value={String(request.accept ?? '')} onChange={(event) => updateHttpRequest({ accept: event.target.value })} placeholder="application/json" />
          </label>
          <label className="inspector-field">
            <span>connectionTimeout</span>
            <input value={String(request.connectionTimeOut ?? '')} onChange={(event) => updateHttpRequest({ connectionTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
          <label className="inspector-field">
            <span>readTimeout</span>
            <input value={String(request.readTimeOut ?? '')} onChange={(event) => updateHttpRequest({ readTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
        </div>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="请求头">
        <ParameterRows
          items={headerRows}
          keyLabel="参数名"
          valueLabel="映射值"
          variableOptions={variableOptions}
          onChange={updateHeader}
          onDelete={deleteHeader}
        />
        <button type="button" className="add-parameter-button" onClick={addHeader}>
          <PlusCircleIcon size={15} />
          添加请求头
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="请求体">
        <div className="http-body-editor">
          <div className="http-body-view-options" role="radiogroup" aria-label="请求体编辑方式">
            <label>
              <input
                type="radio"
                name="http-body-view-type"
                value="JSON"
                checked={bodyViewType === 'JSON'}
                onChange={() => setBodyViewType('JSON')}
              />
              <span>JSON</span>
            </label>
            <label>
              <input
                type="radio"
                name="http-body-view-type"
                value="PARAMETERS"
                checked={bodyViewType === 'PARAMETERS'}
                onChange={() => setBodyViewType('PARAMETERS')}
              />
              <span>参数</span>
            </label>
          </div>
          {bodyViewType === 'JSON' ? (
            <>
              <InlineScriptEditor
                value={bodyJson}
                onChange={updateBodyJson}
                minHeight={300}
                language="json"
                languageLabel="JSON"
                codeLabel="Code"
                modalTitle="请求体 JSON 编辑器"
              />
              {bodyError ? <p className="inline-error">{bodyError}</p> : null}
            </>
          ) : (
            <div className="http-body-parameters">
              <ParameterRows
                items={bodyParameterRows}
                keyLabel="参数名"
                valueLabel="参数值"
                variableOptions={variableOptions}
                onChange={updateBodyParameter}
                onDelete={deleteBodyParameter}
              />
              <button type="button" className="add-parameter-button" onClick={addBodyParameter}>
                <PlusCircleIcon size={15} />
                添加参数
              </button>
            </div>
          )}
        </div>
      </WorkflowCollapsibleSection>
    </div>
  )
}

