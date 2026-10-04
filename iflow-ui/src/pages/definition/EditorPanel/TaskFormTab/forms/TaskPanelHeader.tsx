import { RocketLaunchIcon } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'
import { ValidationField } from '../../../validation/ValidationScope'

export function TaskPanelHeader({
  task,
  label,
  onChange,
}: {
  task: WorkflowTask
  label?: string
  onChange: (task: WorkflowTask) => void
}) {
  return (
    <section className="inspector-section task-summary-section">
      <div className="inline-task-tabs">
        <div className="inline-task-tab-group">
          <button type="button" className="active">{label ?? task.type}</button>
        </div>
        <button type="button" className="inline-test-task">
          <RocketLaunchIcon size={16} weight="fill" />
          MOCK
        </button>
      </div>

      <div className="inspector-form-grid inline-task-definition-card">
        <label className="inspector-field">
          <span>任务名称 <strong>*</strong></span>
          <input
            required
            value={task.name ?? ''}
            onChange={(event) => {
              const name = event.target.value
              onChange({ ...task, name })
            }}
          />
        </label>
        <ValidationField path="/taskReferenceName"><label className="inspector-field">
          <span>引用名称 <strong>*</strong></span>
          <input
            required
            value={task.taskReferenceName}
            onBlur={() => { if (task.taskReferenceName !== task.taskReferenceName.trim()) onChange({ ...task, taskReferenceName: task.taskReferenceName.trim() }) }}
            onChange={(event) => onChange({ ...task, taskReferenceName: event.target.value })}
            onKeyDown={(event) => {
              if (event.key === 'Enter') event.currentTarget.blur()
            }}
          />
        </label></ValidationField>
      </div>
    </section>
  )
}
