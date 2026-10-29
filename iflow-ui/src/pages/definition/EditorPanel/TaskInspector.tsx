import { Switch } from '@mui/material'
import { isBuiltInTask } from '@/flow/taskType'
import { getTaskPlugin } from '@/tasks'
import type { TaskFormProps } from './TaskFormTab/forms'
import { UnknownTaskForm } from './TaskFormTab/forms'
import type { WorkflowTask } from '@/types/workflow'
import { WorkflowCollapsibleSection } from './InspectorFields'
import { useTaskFieldDraft, useValidationReadOnly, ValidationField } from '../validation/ValidationScope'

const RETRY_NUMBERS = [
  { key: 'retryCount', label: '重试次数', help: '首次执行之外的重试次数，0 表示不重试' },
  { key: 'retryDelaySeconds', label: '重试间隔（秒）', help: '基础等待间隔，0 表示立即重试' },
] as const

function TaskPolicyFields({ task, onChange }: Pick<TaskFormProps, 'task' | 'onChange'>) {
  const readOnly = useValidationReadOnly()
  const canRetry = !isBuiltInTask(task.type)
  const drafts = {
    startDelay: useTaskFieldDraft('/startDelay', String(task.startDelay ?? '')),
    retryCount: useTaskFieldDraft('/retryCount', String(task.retryCount ?? '')),
    retryDelaySeconds: useTaskFieldDraft('/retryDelaySeconds', String(task.retryDelaySeconds ?? '')),
    retryLogic: useTaskFieldDraft('/retryLogic', task.retryLogic ?? ''),
  }
  const retryHelp = {
    FIXED: '每次使用相同的基础间隔。',
    LINEAR_BACKOFF: '间隔依次为基础间隔的 1、2、3… 倍。',
    EXPONENTIAL_BACKOFF: '间隔依次为基础间隔的 1、2、4… 倍。',
  }
  const changeNumber = (key: 'startDelay' | typeof RETRY_NUMBERS[number]['key'], value: string) => {
    if (value === '') {
      drafts[key].invalid('', '请填写非负整数')
      return
    }
    onChange({ ...task, [key]: Number(value) })
    drafts[key].clear()
  }
  const togglePolicy = (key: keyof typeof drafts, checked: boolean, message: string) => {
    if (checked) drafts[key].invalid('', message)
    else {
      const next = { ...task }
      delete next[key]
      onChange(next)
      drafts[key].clear()
    }
  }
  const numberPolicy = ({ key, label, help }: { key: 'startDelay' | typeof RETRY_NUMBERS[number]['key']; label: string; help: string }) => {
    const enabled = task[key] != null || Boolean(drafts[key].error)
    return <ValidationField path={`/${key}`} key={key}><div className="task-policy-field">
      <div className="task-policy-toggle">
        <label>
          <Switch checked={enabled} disabled={readOnly}
            slotProps={{ input: { role: 'switch', 'aria-label': label } }}
            onChange={(_, checked) => togglePolicy(key, checked, `请填写${label}`)} />
          <span>{label}</span>
        </label>
        <small>{enabled ? help : '继承框架配置'}</small>
      </div>
      {enabled ? <label className="inspector-field">
        <span>{label} *</span>
        <input type="number" min={0} step={1} max={2147483647} required disabled={readOnly}
          aria-label={label} value={drafts[key].value} placeholder="请输入非负整数"
          onChange={(event) => changeNumber(key, event.target.value)} />
      </label> : null}
    </div></ValidationField>
  }
  const retryLogicEnabled = task.retryLogic != null || Boolean(drafts.retryLogic.error)

  return (
    <div className="workflow-form-panel">
      <WorkflowCollapsibleSection title="执行策略">
        <div className="inspector-form-grid">
          {numberPolicy({ key: 'startDelay', label: '启动延迟（秒）', help: '满足调度条件后等待；0 表示无延迟' })}
        </div>
        <ValidationField path="/optional"><div className="task-policy-toggle task-policy-optional">
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
            <p className="field-help">可重试的失败状态按此配置重试，调用超时也视为失败；终止错误不重试。关闭各项开关时继承框架配置，开启后必须填写或选择。</p>
            <div className="inspector-form-grid">
              {RETRY_NUMBERS.map(numberPolicy)}
            </div>
            <ValidationField path="/retryLogic"><div className="task-policy-field">
              <div className="task-policy-toggle">
                <label>
                  <Switch checked={retryLogicEnabled} disabled={readOnly}
                    slotProps={{ input: { role: 'switch', 'aria-label': '重试方式' } }}
                    onChange={(_, checked) => togglePolicy('retryLogic', checked, '请选择重试方式')} />
                  <span>重试方式</span>
                </label>
                <small>{retryLogicEnabled ? '请选择当前任务使用的重试方式' : '继承框架配置'}</small>
              </div>
              {retryLogicEnabled ? <label className="inspector-field">
                <span>重试方式 *</span>
                <select aria-label="重试方式" value={drafts.retryLogic.value} required disabled={readOnly}
                  onChange={(event) => {
                    onChange({ ...task, retryLogic: event.target.value as WorkflowTask['retryLogic'] })
                    drafts.retryLogic.clear()
                  }}>
                  <option value="" disabled>请选择重试方式</option>
                  <option value="FIXED">固定间隔</option>
                  <option value="LINEAR_BACKOFF">线性增长</option>
                  <option value="EXPONENTIAL_BACKOFF">指数增长</option>
                </select>
                <small>{task.retryLogic ? retryHelp[task.retryLogic] : ''}</small>
              </label> : null}
            </div></ValidationField>
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
