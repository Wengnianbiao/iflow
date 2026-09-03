import { useEffect, useRef, useState } from 'react'
import { RocketLaunchIcon } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'

export function TaskPanelHeader({
  task,
  label,
  onChange,
}: {
  task: WorkflowTask
  label?: string
  onChange: (task: WorkflowTask) => void
}) {
  const [draftName, setDraftName] = useState(task.name ?? '')
  const [draftReferenceName, setDraftReferenceName] = useState(task.taskReferenceName)
  const editingFieldRef = useRef<'name' | 'reference' | null>(null)

  useEffect(() => {
    if (editingFieldRef.current !== 'name') setDraftName(task.name ?? '')
    if (editingFieldRef.current !== 'reference') setDraftReferenceName(task.taskReferenceName)
  }, [task.name, task.taskReferenceName])

  const commitReferenceName = () => {
    editingFieldRef.current = null
    const taskReferenceName = draftReferenceName.trim()
    if (!taskReferenceName) {
      setDraftReferenceName(task.taskReferenceName)
      return
    }
    if (taskReferenceName !== task.taskReferenceName) {
      onChange({ ...task, taskReferenceName })
    }
  }

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
            value={draftName}
            onFocus={() => { editingFieldRef.current = 'name' }}
            onBlur={() => {
              editingFieldRef.current = null
              setDraftName(task.name ?? '')
            }}
            onChange={(event) => {
              const name = event.target.value
              setDraftName(name)
              onChange({ ...task, name })
            }}
          />
        </label>
        <label className="inspector-field">
          <span>引用名称 <strong>*</strong></span>
          <input
            required
            value={draftReferenceName}
            onFocus={() => { editingFieldRef.current = 'reference' }}
            onBlur={commitReferenceName}
            onChange={(event) => setDraftReferenceName(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') event.currentTarget.blur()
            }}
          />
        </label>
      </div>
    </section>
  )
}

