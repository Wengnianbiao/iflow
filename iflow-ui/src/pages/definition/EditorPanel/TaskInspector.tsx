import { Switch } from '@mui/material'
import { isBuiltInTask } from '@/flow/taskType'
import { getTaskPlugin } from '@/tasks'
import type { TaskFormProps } from './TaskFormTab/forms'
import { UnknownTaskForm } from './TaskFormTab/forms'
import type { WorkflowTask } from '@/types/workflow'
import { WorkflowCollapsibleSection } from './InspectorFields'
import { useValidationReadOnly, ValidationField } from '../validation/ValidationScope'

const RETRY_NUMBERS = [
  { key: 'retryCount', label: '重试次数', help: '首次执行之外的重试次数，0 表示不重试' },
  { key: 'retryDelaySeconds', label: '重试间隔（秒）', help: '基础等待间隔，0 表示立即重试' },
] as const

function TaskPolicyFields({ task, onChange }: Pick<TaskFormProps, 'task' | 'onChange'>) {
  const readOnly = useValidationReadOnly()
  const canRetry = !isBuiltInTask(task.type)
  const retryHelp = {
    FIXED: '每次使用相同的基础间隔。',
    LINEAR_BACKOFF: '间隔依次为基础间隔的 1、2、3… 倍。',
    EXPONENTIAL_BACKOFF: '间隔依次为基础间隔的 1、2、4… 倍。',
  }
  const changeNumber = (key: 'startDelay' | typeof RETRY_NUMBERS[number]['key'], value: string) => {
    const next = { ...task }
    if (value === '') delete next[key]
    else next[key] = Number(value)
    onChange(next)
  }

  return (
    <div className="workflow-form-panel">
      <WorkflowCollapsibleSection title="执行策略">
        <div className="inspector-form-grid">
          <ValidationField path="/startDelay"><label className="inspector-field">
            <span>启动延迟（秒）</span>
            <input type="number" min={0} step={1} max={2147483647}
              aria-label="启动延迟（秒）" value={task.startDelay ?? ''} placeholder="继承框架配置"
              onChange={(event) => changeNumber('startDelay', event.target.value)} />
            <small>满足调度条件后等待；0 表示无延迟</small>
          </label></ValidationField>
        </div>
        <ValidationField path="/optional"><div className="task-policy-optional">
          <label>
            <Switch checked={task.optional ?? false} disabled={readOnly}
              slotProps={{ input: { role: 'switch', 'aria-label': '可选任务' } }}
              onChange={(_, checked) => onChange({ ...task, optional: checked })} />
            <span>可选任务</span>
          </label>
          <small>任务失败时继续执行流程</small>
        </div></ValidationField>
        {canRetry ? (
          <div className="task-retry-settings">
            <h3>失败重试</h3>
            <p className="field-help">可重试的失败状态按此配置重试，调用超时也视为失败；终止错误不重试。留空继承框架配置。</p>
            <div className="inspector-form-grid">
              {RETRY_NUMBERS.map(({ key, label, help }) => (
                <ValidationField path={`/${key}`} key={key}><label className="inspector-field">
                  <span>{label}</span>
                  <input type="number" min={0} step={1} max={2147483647} aria-label={label}
                    value={task[key] ?? ''} placeholder="继承框架配置"
                    onChange={(event) => changeNumber(key, event.target.value)} />
                  <small>{help}</small>
                </label></ValidationField>
              ))}
            </div>
            <ValidationField path="/retryLogic"><label className="inspector-field">
              <span>重试方式</span>
              <select aria-label="重试方式" value={task.retryLogic ?? ''}
                onChange={(event) => onChange({ ...task, retryLogic: (event.target.value || undefined) as WorkflowTask['retryLogic'] })}>
                <option value="">继承框架配置</option>
                <option value="FIXED">固定间隔</option>
                <option value="LINEAR_BACKOFF">线性增长</option>
                <option value="EXPONENTIAL_BACKOFF">指数增长</option>
              </select>
              <small>{task.retryLogic ? retryHelp[task.retryLogic] : '启动时采用框架配置的重试方式。'}</small>
            </label></ValidationField>
          </div>
        ) : null}
        <p className="task-policy-note" role="note">
          {canRetry ? '启动延迟和失败重试当前仅保存配置，暂未生效。' : '启动延迟当前仅保存配置，暂未生效。'}
        </p>
      </WorkflowCollapsibleSection>
    </div>
  )
}

export function getTaskForm(type: string) {
  return getTaskPlugin(type)?.form ?? UnknownTaskForm
}

export function TaskInspector(props: TaskFormProps) {
  const TaskForm = getTaskForm(props.task.type)
  return <><TaskForm {...props} /><TaskPolicyFields task={props.task} onChange={props.onChange} /></>
}
