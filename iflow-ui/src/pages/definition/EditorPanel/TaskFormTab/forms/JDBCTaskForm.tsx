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
  const request = task.inputParameters ?? {}
  const payload = request.payload
  const payloadMode = isRecord(payload) ? 'parameters' : 'expression'
  const parameterEntries = Object.entries(isRecord(payload) ? payload : {})
  const parameterRows = parameterEntries.map(([key, value]) => ({ key, value: value === null ? 'null' : valueText(value) }))

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
          <label className="inspector-field">
            <span>数据库厂商 <strong>*</strong></span>
            <select value={String(request.vendor ?? 'MYSQL')} onChange={(event) => updateJdbcRequest({ vendor: event.target.value })}>
              {['MYSQL', 'POSTGRES', 'SQLSERVER', 'ORACLE', 'OPENGAUSS', 'KINGBASE'].map((vendor) => (
                <option value={vendor} key={vendor}>{vendor}</option>
              ))}
            </select>
          </label>
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
          value={String(request.sqlTemplate ?? '')}
          onChange={(sqlTemplate) => updateJdbcRequest({ sqlTemplate })}
          minHeight={280}
          language="sql"
          languageLabel="SQL"
          codeLabel="Statement"
          modalTitle="JDBC SQL 编辑器"
        />
        <p className="field-help">使用 <code>$.字段</code> 读取 payload 并绑定参数。<code>IN $.barcodes</code> 和 <code>NOT IN $.barcodes</code> 自动补齐括号、展开数组；已有括号时直接沿用。空数组会报错。UPDATE 同时适用于 INSERT、UPDATE 和 DELETE。</p>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="任务输入">
        <div className="switch-evaluator-heading">
          <span>入参方式</span>
          <div className="switch-evaluator-options" role="radiogroup" aria-label="JDBC 输入方式">
            <label>
              <input type="radio" name={`jdbc-payload-mode-${task.taskReferenceName}`} value="expression"
                checked={payloadMode === 'expression'}
                onChange={() => updateJdbcRequest({ payload: '${workflow.input.data}' })} />
              <span>表达式</span>
            </label>
            <label>
              <input type="radio" name={`jdbc-payload-mode-${task.taskReferenceName}`} value="parameters"
                checked={payloadMode === 'parameters'}
                onChange={() => updateJdbcRequest({ payload: {} })} />
              <span>Key / Value</span>
            </label>
          </div>
        </div>
        {payloadMode === 'expression' ? (
          <label className="inspector-field">
            <span>payload</span>
            <VariableExpressionInput value={typeof payload === 'string' ? payload : ''}
              options={variableOptions} onChange={(value) => updateJdbcRequest({ payload: value })} />
          </label>
        ) : (
          <>
            <ParameterRows items={parameterRows} keyLabel="字段名" valueLabel="值"
              variableOptions={variableOptions} onChange={updateParameter}
              onDelete={(index) => updateJdbcRequest({ payload: Object.fromEntries(parameterEntries.filter((_, row) => row !== index)) })} />
            <button type="button" className="add-parameter-button" onClick={addParameter}>
              <PlusCircleIcon size={15} />
              添加字段
            </button>
            <p className="field-help">值支持文本、JSON 和 <code>{'${...}'}</code> 表达式；数组、对象和数值保留类型。纯字符串可用 JSON 双引号包裹。</p>
          </>
        )}
        <p className="field-help">两种方式都写入 payload。用 <code>{'${workflow.input.data}'}</code> 传入业务数据后，SQL 中直接用 <code>$.barcode</code> 读取字段。SQL 引号内的路径保留原文。</p>
      </WorkflowCollapsibleSection>
    </div>
  )
}
