import { useEffect } from 'react'
import { PlusCircleIcon } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'
import {
  InspectorSelect,
  ParameterRows,
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  numberOrUndefined,
  valueText,
} from '../../InspectorFields'
import { InlineScriptEditor } from './InlineScriptEditor'
import { TaskPanelHeader } from './TaskPanelHeader'
import { isRecord } from './formUtils'

const JDBC_URL_TEMPLATES: Record<string, string> = {
  MYSQL: 'jdbc:mysql://localhost:3306/demo',
  POSTGRES: 'jdbc:postgresql://localhost:5432/demo',
  SQLSERVER: 'jdbc:sqlserver://localhost:1433;databaseName=demo',
  ORACLE: 'jdbc:oracle:thin:@//localhost:1521/orcl',
  OPENGAUSS: 'jdbc:opengauss://localhost:5432/demo',
  KINGBASE: 'jdbc:kingbase8://localhost:54321/demo',
}

const JDBC_VENDOR_OPTIONS = Object.keys(JDBC_URL_TEMPLATES)
  .map((vendor) => ({ value: vendor, label: vendor }))

export function JDBCTaskForm({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const request = task.inputParameters ?? {}
  const payload = isRecord(request.payload) ? request.payload : {}
  const parameterEntries = Object.entries(payload)
  const parameterRows = parameterEntries.map(([key, value]) => ({ key, value: value === null ? 'null' : valueText(value) }))

  useEffect(() => {
    if (isRecord(task.inputParameters?.payload)) return
    onChange({
      ...task,
      inputParameters: { ...task.inputParameters, payload: {} },
    })
  }, [task.taskReferenceName])

  const updateJdbcRequest = (next: Record<string, unknown>) => {
    onChange({
      ...task,
      inputParameters: { ...request, ...next },
    })
  }

  const updateParameter = (index: number, next: { key: string; value: string }) => {
    let value: unknown = parameterEntries[index][1]
    if (next.value !== parameterRows[index].value) {
      try { value = JSON.parse(next.value) } catch { value = next.value }
    }
    const entries = [...parameterEntries]
    entries[index] = [next.key, value]
    updateJdbcRequest({ payload: Object.fromEntries(entries) })
  }

  const addParameter = () => {
    let index = parameterRows.length + 1
    let key = `parameter_${index}`
    while (parameterEntries.some(([name]) => name === key)) {
      index += 1
      key = `parameter_${index}`
    }
    updateJdbcRequest({ payload: Object.fromEntries([...parameterEntries, [key, '']]) })
  }

  return (
    <div className="workflow-form-panel jdbc-task-inspector">
      <TaskPanelHeader task={task} label="数据库" onChange={onChange} />

      <WorkflowCollapsibleSection title="连接信息">
        <div className="inspector-form-grid jdbc-connection-grid">
          <label className="inspector-field jdbc-url-field">
            <span>JDBC URL <strong>*</strong></span>
            <VariableExpressionInput value={String(request.url ?? '')} options={variableOptions} onChange={(value) => updateJdbcRequest({ url: value })} />
          </label>
          <div className="inspector-field">
            <span>数据库厂商 <strong>*</strong></span>
            <InspectorSelect
              value={String(request.vendor ?? 'MYSQL')}
              options={JDBC_VENDOR_OPTIONS}
              ariaLabel="数据库厂商"
              onChange={(vendor) => {
                if (vendor === request.vendor) return
                updateJdbcRequest({ vendor, url: JDBC_URL_TEMPLATES[vendor] })
              }}
            />
          </div>
          <label className="inspector-field">
            <span>用户名</span>
            <VariableExpressionInput value={String(request.username ?? '')} options={variableOptions} onChange={(value) => updateJdbcRequest({ username: value })} />
          </label>
          <label className="inspector-field jdbc-password-field">
            <span>密码</span>
            <VariableExpressionInput value={String(request.password ?? '')} options={variableOptions} onChange={(value) => updateJdbcRequest({ password: value })} />
          </label>
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

      <WorkflowCollapsibleSection title="SQL 模板参数">
        <ParameterRows
          items={parameterRows}
          keyLabel="占位符名称"
          valueLabel="参数值"
          variableOptions={variableOptions}
          onChange={updateParameter}
          onDelete={(index) => updateJdbcRequest({ payload: Object.fromEntries(parameterEntries.filter((_, row) => row !== index)) })}
        />
        <button type="button" className="add-parameter-button" onClick={addParameter}>
          <PlusCircleIcon size={15} />
          添加模板参数
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="SQL 模板">
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
          value={String(request.sqlTemplate ?? '')}
          onChange={(sqlTemplate) => updateJdbcRequest({ sqlTemplate })}
          minHeight={280}
          language="sql"
          languageLabel="SQL"
          codeLabel="Statement"
          modalTitle="JDBC SQL 编辑器"
        />
      </WorkflowCollapsibleSection>
    </div>
  )
}
