import { useState } from 'react'
import { PlusCircleIcon } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'
import { editableParameterRows, parameterValues, type EditableParameter } from '@/flow/parameterValues'
import {
  ParameterRows,
  WorkflowCollapsibleSection,
  numberOrUndefined,
} from '../../InspectorFields'
import { InlineScriptEditor } from './InlineScriptEditor'
import { HttpRequestAutocomplete } from './HttpRequestAutocomplete'
import { TaskPanelHeader } from './TaskPanelHeader'
import { isRecord } from './formUtils'
import { ValidationField, useTaskFieldDraft } from '../../../validation/ValidationScope'
import { jsonErrorMessage } from '../../../validation/jsonErrorMessage'

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'TRACE']
const CONTENT_TYPES = [
  'application/json',
  'application/xml',
  'text/plain',
  'text/html',
  'text/xml',
  'application/x-www-form-urlencoded',
  'multipart/form-data',
  'application/octet-stream',
  'application/pdf',
  'application/javascript',
  'application/ld+json',
  'application/xhtml+xml',
  'application/zip',
  'image/jpeg',
  'image/png',
  'image/svg+xml',
]
const ACCEPT_TYPES = [...CONTENT_TYPES, '*/*']

export function HTTPTaskForm({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const hasNestedRequest = isRecord(inputParameters.http_request)
  const request = hasNestedRequest ? inputParameters.http_request as Record<string, unknown> : inputParameters
  const headers = isRecord(request.headers) ? request.headers : {}
  const headerRows = editableParameterRows(headers)
  const bodyParameters = isRecord(request.body) ? request.body : {}
  const bodyParameterRows = editableParameterRows(bodyParameters)
  const requestPath = hasNestedRequest ? '/inputParameters/http_request' : '/inputParameters'
  const bodyDraft = useTaskFieldDraft(`${requestPath}/body`, JSON.stringify(request.body === undefined ? {} : request.body, null, 2))
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
    rows[index] = { ...rows[index], ...next }
    updateHttpRequest({ headers: parameterValues(rows) })
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
    updateHttpRequest({ headers: parameterValues(headerRows.filter((_, itemIndex) => itemIndex !== index)) })
  }

  const updateBodyJson = (nextValue: string) => {
    try {
      const parsed = nextValue.trim() ? JSON.parse(nextValue) : {}
      updateHttpRequest({ body: parsed })
      bodyDraft.applied(nextValue)
    } catch (error) {
      bodyDraft.invalid(nextValue, jsonErrorMessage(error, '请求体'))
    }
  }

  const updateBodyParameters = (rows: EditableParameter[]) => {
    const nextBody = parameterValues(rows)
    bodyDraft.clear()
    updateHttpRequest({ body: nextBody })
  }

  const updateBodyParameter = (index: number, next: { key: string; value: string }) => {
    const rows = [...bodyParameterRows]
    rows[index] = { ...rows[index], ...next }
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
          <ValidationField path={`${requestPath}/method`}><HttpRequestAutocomplete
            label="Method"
            value={String(request.method ?? 'GET')}
            options={HTTP_METHODS}
            variableOptions={variableOptions}
            onChange={(value) => updateHttpRequest({ method: value })}
          /></ValidationField>
          <ValidationField path={`${requestPath}/uri`}><HttpRequestAutocomplete
            label="URL"
            value={String(request.uri ?? '')}
            variableOptions={variableOptions}
            onChange={(value) => updateHttpRequest({ uri: value })}
          /></ValidationField>
          <div className="inspector-form-grid http-request-options-grid">
            <HttpRequestAutocomplete
              label="Content-Type"
              value={String(request.contentType ?? '')}
              options={CONTENT_TYPES}
              variableOptions={variableOptions}
              placeholder="application/json"
              onChange={(value) => updateHttpRequest({ contentType: value })}
            />
            <HttpRequestAutocomplete
              label="Accept"
              value={String(request.accept ?? '')}
              options={ACCEPT_TYPES}
              variableOptions={variableOptions}
              placeholder="application/json"
              onChange={(value) => updateHttpRequest({ accept: value })}
            />
            <label className="inspector-field">
              <span>ConnectionTimeout(ms)</span>
              <input value={String(request.connectionTimeOut ?? '')} onChange={(event) => updateHttpRequest({ connectionTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
            </label>
            <label className="inspector-field">
              <span>ReadTimeout(ms)</span>
              <input value={String(request.readTimeOut ?? '')} onChange={(event) => updateHttpRequest({ readTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
            </label>
          </div>
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
                disabled={Boolean(bodyDraft.error)}
                onChange={() => setBodyViewType('PARAMETERS')}
              />
              <span>参数</span>
            </label>
          </div>
          {bodyViewType === 'JSON' ? (
            <ValidationField path={`${requestPath}/body`}>
              <InlineScriptEditor
                value={bodyDraft.value}
                onChange={updateBodyJson}
                minHeight={300}
                language="json"
                languageLabel="JSON"
                codeLabel="Code"
                modalTitle="请求体 JSON 编辑器"
              />
            </ValidationField>
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
