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

export function JDBCTaskForm({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const hasNestedRequest = isRecord(inputParameters.jdbc_request)
  const request = hasNestedRequest ? inputParameters.jdbc_request as Record<string, unknown> : inputParameters
  const parameters = isRecord(request.parameters) ? request.parameters : {}
  const parameterRows = Object.entries(parameters).map(([key, value]) => ({ key, value: valueText(value) }))

  const updateJdbcRequest = (next: Record<string, unknown>) => {
    const nextRequest = { ...request, ...next }
    onChange({
      ...task,
      inputParameters: hasNestedRequest ? { ...inputParameters, jdbc_request: nextRequest } : nextRequest,
    })
  }

  const updateParameters = (rows: Array<{ key: string; value: string }>) => {
    updateJdbcRequest({ parameters: Object.fromEntries(rows.map((item) => [item.key, item.value])) })
  }

  const updateParameter = (index: number, next: { key: string; value: string }) => {
    const rows = [...parameterRows]
    rows[index] = next
    updateParameters(rows)
  }

  const addParameter = () => {
    let index = parameterRows.length + 1
    let key = `parameter_${index}`
    while (Object.prototype.hasOwnProperty.call(parameters, key)) {
      index += 1
      key = `parameter_${index}`
    }
    updateParameters([...parameterRows, { key, value: '' }])
  }

  return (
    <div className="workflow-form-panel jdbc-task-inspector">
      <TaskPanelHeader task={task} label="JDBC" onChange={onChange} />

      <WorkflowCollapsibleSection title="连接信息">
        <div className="inspector-form-grid jdbc-connection-grid">
          <label className="inspector-field jdbc-url-field">
            <span>JDBC URL <strong>*</strong></span>
            <VariableExpressionInput value={String(request.url ?? '')} options={variableOptions} onChange={(value) => updateJdbcRequest({ url: value })} />
          </label>
          <label className="inspector-field">
            <span>数据库厂商</span>
            <select value={String(request.vendor ?? '')} onChange={(event) => updateJdbcRequest({ vendor: event.target.value || undefined })}>
              <option value="">自定义驱动</option>
              {['MYSQL', 'POSTGRES', 'SQLSERVER', 'ORACLE', 'OPENGAUSS', 'KINGBASE'].map((vendor) => (
                <option value={vendor} key={vendor}>{vendor}</option>
              ))}
            </select>
          </label>
          <label className="inspector-field">
            <span>用户名</span>
            <VariableExpressionInput value={String(request.username ?? '')} options={variableOptions} onChange={(value) => updateJdbcRequest({ username: value })} />
          </label>
          <label className="inspector-field">
            <span>密码</span>
            <VariableExpressionInput value={String(request.password ?? '')} options={variableOptions} onChange={(value) => updateJdbcRequest({ password: value })} />
          </label>
          <label className="inspector-field jdbc-driver-field">
            <span>driverClassName</span>
            <input value={String(request.driverClassName ?? '')} onChange={(event) => updateJdbcRequest({ driverClassName: event.target.value })} placeholder="选择厂商后可留空" />
          </label>
        </div>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="SQL 语句">
        <div className="jdbc-operation-options" role="radiogroup" aria-label="JDBC 操作类型">
          {['QUERY', 'UPDATE'].map((operation) => (
            <label key={operation}>
              <input
                type="radio"
                name={`jdbc-operation-${task.taskReferenceName}`}
                value={operation}
                checked={String(request.operation ?? 'QUERY').toUpperCase() === operation}
                onChange={() => updateJdbcRequest({ operation })}
              />
              <span>{operation}</span>
            </label>
          ))}
        </div>
        <InlineScriptEditor
          value={String(request.sql ?? '')}
          onChange={(sql) => updateJdbcRequest({ sql })}
          minHeight={280}
          language="sql"
          languageLabel="SQL"
          codeLabel="Statement"
          modalTitle="JDBC SQL 编辑器"
        />
        <p className="field-help">使用 <code>:参数名</code> 绑定下方命名参数；UPDATE 同时适用于 INSERT、UPDATE 和 DELETE。</p>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="命名参数">
        <ParameterRows
          items={parameterRows}
          keyLabel="参数名"
          valueLabel="映射值"
          variableOptions={variableOptions}
          onChange={updateParameter}
          onDelete={(index) => updateParameters(parameterRows.filter((_, itemIndex) => itemIndex !== index))}
        />
        <button type="button" className="add-parameter-button" onClick={addParameter}>
          <PlusCircleIcon size={15} />
          添加命名参数
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="高级设置" defaultOpen={false}>
        <div className="inspector-form-grid">
          <label className="inspector-field">
            <span>connectionTimeOut (ms)</span>
            <input value={String(request.connectionTimeOut ?? '')} onChange={(event) => updateJdbcRequest({ connectionTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
          <label className="inspector-field">
            <span>queryTimeOut (ms)</span>
            <input value={String(request.queryTimeOut ?? '')} onChange={(event) => updateJdbcRequest({ queryTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
        </div>
      </WorkflowCollapsibleSection>
    </div>
  )
}

