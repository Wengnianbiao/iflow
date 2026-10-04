import type { WorkflowTask } from '@/types/workflow'
import { WorkflowCollapsibleSection, uniqueValues } from '../../InspectorFields'
import { TaskPanelHeader } from './TaskPanelHeader'
import { ValidationField } from '../../../validation/ValidationScope'

export function JoinTaskForm({ task, onChange }: { task: WorkflowTask; onChange: (task: WorkflowTask) => void }) {
  const joinOn = task.joinOn ?? []
  return (
    <div className="workflow-form-panel join-task-inspector">
      <TaskPanelHeader task={task} label="汇聚" onChange={onChange} />
      <WorkflowCollapsibleSection title="等待任务">
        <ValidationField path="/joinOn"><label className="inspector-field full">
          <span>Join On</span>
          <textarea
            rows={5}
            value={joinOn.join('\n')}
            placeholder="每行填写一个分支末端任务引用名；留空表示直接汇聚"
            onChange={(event) => onChange({
              ...task,
              joinOn: uniqueValues(event.target.value.split(/[\n,]/).map((item) => item.trim())),
            })}
          />
          <small>列出的任务全部进入终态后，JOIN 才会继续后续流程。</small>
        </label></ValidationField>
      </WorkflowCollapsibleSection>
    </div>
  )
}
