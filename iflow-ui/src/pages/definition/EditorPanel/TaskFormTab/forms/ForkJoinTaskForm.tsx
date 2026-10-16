import { PlusCircleIcon } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'
import { WorkflowCollapsibleSection } from '../../InspectorFields'
import { TaskPanelHeader } from './TaskPanelHeader'

export function ForkJoinTaskForm({ task, onChange }: { task: WorkflowTask; onChange: (task: WorkflowTask) => void }) {
  const forkTasks = task.forkTasks ?? []
  return (
    <div className="workflow-form-panel fork-join-task-inspector">
      <TaskPanelHeader task={task} label="并行分支" onChange={onChange} />
      <WorkflowCollapsibleSection title="并行路径">
        <div className="operator-branch-list">
          {forkTasks.map((branch, index) => (
            <section className="fork-path-card" key={index} aria-label={`分支 ${index + 1}`}>
              <div className="fork-path-heading">
                <h3>分支 {index + 1}</h3>
                <span>{branch.length} 个任务</span>
              </div>
              {branch.length ? (
                <ol className="fork-path-steps">
                  {branch.map((item, taskIndex) => (
                    <li className="fork-path-step" key={item.taskReferenceName}>
                      <span className="fork-path-step-number" aria-hidden="true">{taskIndex + 1}</span>
                      <div className="fork-path-task">
                        <strong>{item.name || '未命名任务'}</strong>
                        <code>{item.taskReferenceName}</code>
                      </div>
                      <div className="fork-path-task-meta">
                        <span className="fork-path-task-type">{item.type}</span>
                        {taskIndex === branch.length - 1 ? <small>分支末尾</small> : null}
                      </div>
                    </li>
                  ))}
                </ol>
              ) : (
                <p className="fork-path-empty">暂无任务，请在画布中点击该分支的 + 添加任务。</p>
              )}
            </section>
          ))}
          {!forkTasks.length ? <p className="inspector-empty">尚未创建并行分支</p> : null}
        </div>
        <button
          type="button"
          className="add-parameter-button"
          onClick={() => onChange({ ...task, forkTasks: [...forkTasks, []] })}
        >
          <PlusCircleIcon size={16} />
          添加并行分支
        </button>
      </WorkflowCollapsibleSection>
    </div>
  )
}

