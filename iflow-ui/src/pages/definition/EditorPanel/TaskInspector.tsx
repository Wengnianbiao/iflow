import { Switch } from '@mui/material'
import { isBuiltInTask } from '@/flow/taskType'
import { getTaskPlugin } from '@/tasks'
import type { TaskFormProps } from './TaskFormTab/forms'
import { UnknownTaskForm } from './TaskFormTab/forms'
import type { WorkflowTask } from '@/types/workflow'
import { InspectorNumberInput, InspectorSelect, WorkflowCollapsibleSection } from './InspectorFields'
import { useTaskFieldDraft, useValidationReadOnly, ValidationField } from '../validation/ValidationScope'

const RETRY_NUMBERS = [
  { key: 'retryCount', label: '重试次数' },
  { key: 'retryDelaySeconds', label: '重试间隔（秒）' },
] as const

function TaskPolicyFields({ task, onChange }: Pick<TaskFormProps, 'task' | 'onChange'>) {
  const readOnly = useValidationReadOnly()
  const canRetry = !isBuiltInTask(task.type)
  const drafts = {
    startDelay: useTaskFieldDraft('/startDelay', String(task.startDelay ?? 0)),
    retryCount: useTaskFieldDraft('/retryCount', String(task.retryCount ?? 0)),
    retryDelaySeconds: useTaskFieldDraft('/retryDelaySeconds', String(task.retryDelaySeconds ?? 0)),
    retryLogic: useTaskFieldDraft('/retryLogic', task.retryLogic ?? 'FIXED'),
  }
  const delayEnabled = (task.startDelay ?? 0) !== 0 || Boolean(drafts.startDelay.error)
  const retryEnabled = (task.retryCount ?? 0) !== 0
    || Boolean(drafts.retryCount.error || drafts.retryDelaySeconds.error || drafts.retryLogic.error)

  const toggleDelay = (checked: boolean) => {
    if (checked) drafts.startDelay.invalid('', '请填写启动延迟（秒）')
    else {
      onChange({ ...task, startDelay: 0 })
      drafts.startDelay.clear()
    }
  }
  const toggleRetry = (checked: boolean) => {
    if (checked) drafts.retryCount.invalid('', '请填写重试次数')
    else {
      onChange({ ...task, retryCount: 0, retryDelaySeconds: 0, retryLogic: 'FIXED' })
      drafts.retryCount.clear()
      drafts.retryDelaySeconds.clear()
      drafts.retryLogic.clear()
    }
  }
  const changeNumber = (key: 'startDelay' | typeof RETRY_NUMBERS[number]['key'], value: string) => {
    if (value === '') {
      drafts[key].invalid('', '请填写非负整数')
      return
    }
    if (!/^\d+$/.test(value)) {
      drafts[key].invalid(value, '请输入非负整数')
      return
    }
    onChange({ ...task, [key]: Number(value) })
    drafts[key].clear()
  }
  const numberPolicy = ({ key, label }: { key: 'startDelay' | typeof RETRY_NUMBERS[number]['key']; label: string }) => (
    <ValidationField path={`/${key}`} key={key}>
      <div className="inspector-field">
        <span>{label} *</span>
        <InspectorNumberInput required disabled={readOnly} max={2147483647}
          ariaLabel={label} value={drafts[key].value}
          onChange={(value) => changeNumber(key, value)} />
      </div>
    </ValidationField>
  )

  return (
    <div className="workflow-form-panel">
      <WorkflowCollapsibleSection title="执行策略">
        <div className="task-policy-list">
          <ValidationField path="/optional"><div className="task-policy-toggle">
            <label>
              <Switch checked={task.optional ?? false} disabled={readOnly}
                slotProps={{ input: { role: 'switch', 'aria-label': '可选任务' } }}
                onChange={(_, checked) => onChange({ ...task, optional: checked })} />
              <span>可选任务</span>
            </label>
          </div></ValidationField>
          <div className="task-policy-field">
            <div className="task-policy-toggle">
              <label>
                <Switch checked={delayEnabled} disabled={readOnly}
                  slotProps={{ input: { role: 'switch', 'aria-label': '启动延迟' } }}
                  onChange={(_, checked) => toggleDelay(checked)} />
                <span>启动延迟</span>
              </label>
            </div>
            {delayEnabled ? <div className="inspector-form-grid">
              {numberPolicy({ key: 'startDelay', label: '启动延迟（秒）' })}
            </div> : null}
          </div>
          {canRetry ? (
            <div className="task-policy-field">
              <div className="task-policy-toggle">
                <label>
                  <Switch checked={retryEnabled} disabled={readOnly}
                    slotProps={{ input: { role: 'switch', 'aria-label': '失败重试' } }}
                    onChange={(_, checked) => toggleRetry(checked)} />
                  <span>失败重试</span>
                </label>
              </div>
              {retryEnabled ? <>
                <div className="inspector-form-grid">
                  {RETRY_NUMBERS.map(numberPolicy)}
                </div>
                <ValidationField path="/retryLogic">
                  <div className="inspector-field">
                    <span>重试策略 *</span>
                    <InspectorSelect ariaLabel="重试策略" value={drafts.retryLogic.value} required disabled={readOnly}
                      options={[
                        { value: 'FIXED', label: '固定间隔' },
                        { value: 'LINEAR_BACKOFF', label: '线性增长' },
                        { value: 'EXPONENTIAL_BACKOFF', label: '指数增长' },
                      ]}
                      onChange={(value) => {
                        onChange({ ...task, retryLogic: value as WorkflowTask['retryLogic'] })
                        drafts.retryLogic.clear()
                      }} />
                  </div>
                </ValidationField>
              </> : null}
            </div>
          ) : null}
        </div>
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
