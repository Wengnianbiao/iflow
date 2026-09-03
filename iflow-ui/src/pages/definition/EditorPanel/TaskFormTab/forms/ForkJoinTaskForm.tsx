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
            <div className="operator-branch-row" key={index}>
              <span>分支 {index + 1}</span>
              <strong>{branch.length} 个任务</strong>
              <small>{branch.map((item) => item.taskReferenceName).join(' → ') || '点击画布分支端口添加任务'}</small>
            </div>
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
        <p className="field-help">FORK 与紧随其后的 JOIN 作为一个结构整体保存；在画布分支端口的 + 中添加分支任务。</p>
      </WorkflowCollapsibleSection>
    </div>
  )
}

