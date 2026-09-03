import { useState } from 'react'
import type { WorkflowTask } from '@/types/workflow'
import { getTaskPluginLabel } from '@/tasks/taskPluginRegistry'
import { WorkflowCollapsibleSection, prettyJson } from '../../InspectorFields'
import { TaskPanelHeader } from './TaskPanelHeader'

export function UnknownTaskForm({ task, onChange }: { task: WorkflowTask; onChange: (task: WorkflowTask) => void }) {
  const [inputJson, setInputJson] = useState(prettyJson(task.inputParameters))
  const [jsonError, setJsonError] = useState('')

  const applyInputJson = () => {
    try {
      const parsed = JSON.parse(inputJson) as Record<string, unknown>
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('请输入 JSON 对象')
      onChange({ ...task, inputParameters: parsed })
      setJsonError('')
    } catch (error) {
      setJsonError(error instanceof Error ? error.message : 'JSON 格式不正确')
    }
  }

  return (
    <div className="workflow-form-panel generic-task-inspector">
      <TaskPanelHeader task={task} onChange={onChange} />
      <WorkflowCollapsibleSection title="输入参数">
        <div className="json-editor-block generic-json-editor">
          <div className="json-editor-heading">
            <div><span className="section-kicker">JSON</span><h3>{getTaskPluginLabel(task.type)}参数</h3></div>
            <button type="button" onClick={applyInputJson}>应用参数</button>
          </div>
          <textarea value={inputJson} onChange={(event) => setInputJson(event.target.value)} spellCheck={false} />
          {jsonError ? <p className="inline-error">{jsonError}</p> : <p className="field-help">支持 Conductor 表达式，例如 <code>${'{workflow.input.id}'}</code></p>}
        </div>
      </WorkflowCollapsibleSection>
    </div>
  )
}

