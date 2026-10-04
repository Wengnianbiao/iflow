import type { WorkflowTask } from '@/types/workflow'
import { getTaskPluginLabel } from '@/tasks/taskPluginRegistry'
import { WorkflowCollapsibleSection, prettyJson } from '../../InspectorFields'
import { TaskPanelHeader } from './TaskPanelHeader'
import { ValidationField, useTaskFieldDraft } from '../../../validation/ValidationScope'
import { jsonErrorMessage } from '../../../validation/jsonErrorMessage'

export function UnknownTaskForm({ task, onChange }: { task: WorkflowTask; onChange: (task: WorkflowTask) => void }) {
  const draft = useTaskFieldDraft('/inputParameters', prettyJson(task.inputParameters))

  const applyInputJson = (inputJson: string) => {
    try {
      const parsed = JSON.parse(inputJson) as Record<string, unknown>
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('请输入 JSON 对象')
      onChange({ ...task, inputParameters: parsed })
      draft.applied(inputJson)
    } catch (error) {
      draft.invalid(inputJson, jsonErrorMessage(error, '输入参数'))
    }
  }

  return (
    <div className="workflow-form-panel generic-task-inspector">
      <TaskPanelHeader task={task} onChange={onChange} />
      <WorkflowCollapsibleSection title="输入参数">
        <div className="json-editor-block generic-json-editor">
          <div className="json-editor-heading">
            <div><span className="section-kicker">JSON</span><h3>{getTaskPluginLabel(task.type)}参数</h3></div>
            <small>格式正确后自动应用</small>
          </div>
          <ValidationField path="/inputParameters"><textarea value={draft.value} onChange={(event) => applyInputJson(event.target.value)} spellCheck={false} /></ValidationField>
          {!draft.error ? <p className="field-help">支持 Conductor 表达式，例如 <code>${'{workflow.input.id}'}</code></p> : null}
        </div>
      </WorkflowCollapsibleSection>
    </div>
  )
}
