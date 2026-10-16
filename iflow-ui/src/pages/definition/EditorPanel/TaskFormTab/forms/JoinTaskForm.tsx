import { TaskPanelHeader } from './TaskPanelHeader'
import { ValidationField } from '../../../validation/ValidationScope'
import type { TaskFormProps } from './types'

export function JoinTaskForm({ task, joinTaskOptions = [], onChange }: TaskFormProps) {
  const joinOn = task.joinOn ?? []
  const references = joinTaskOptions.map((option) => option.taskReferenceName)
  const allSelected = references.length > 0
    && joinOn.length === references.length
    && references.every((reference) => joinOn.includes(reference))
  const unavailableReferences = joinOn.filter((reference) => !references.includes(reference))
  const clearSelection = allSelected || (references.length === 0 && joinOn.length > 0)

  return (
    <div className="workflow-form-panel join-task-inspector">
      <TaskPanelHeader task={task} label="汇聚" onChange={onChange} />
      <section className="inspector-section join-inputs-section">
        <div className="join-inputs-heading">
          <h2>等待任务</h2>
          <button
            type="button"
            className="join-select-all"
            disabled={joinTaskOptions.length === 0 && joinOn.length === 0}
            onClick={() => onChange({ ...task, joinOn: clearSelection ? [] : references })}
          >
            {clearSelection ? '取消全选' : '全选'}
          </button>
        </div>
        <ValidationField path="/joinOn">
          <div className="join-task-options" role="group" aria-label="等待任务">
            {joinTaskOptions.map(({ taskReferenceName: reference, name }) => (
              <label className="join-task-option" key={reference}>
                <input
                  type="checkbox"
                  name={reference}
                  aria-label={`${name || '未命名任务'}（${reference}）`}
                  checked={joinOn.includes(reference)}
                  onChange={(event) => {
                    const checked = event.target.checked
                    onChange({
                      ...task,
                      joinOn: references.filter((option) => option === reference ? checked : joinOn.includes(option)),
                    })
                  }}
                />
                <span className="join-task-label">
                  <strong>{name || '未命名任务'}</strong>
                  <small>{reference}</small>
                </span>
              </label>
            ))}
            {!joinTaskOptions.length ? <p className="inspector-empty">当前没有可等待的分支末尾任务</p> : null}
          </div>
          {unavailableReferences.length ? (
            <p className="field-help">当前配置包含不在可选分支末尾中的引用：{unavailableReferences.join('、')}。请重新选择等待任务。</p>
          ) : null}
        </ValidationField>
      </section>
    </div>
  )
}
