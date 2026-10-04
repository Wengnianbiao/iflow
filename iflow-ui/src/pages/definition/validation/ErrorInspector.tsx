import { useSelector } from '@xstate/react'
import { CaretUpIcon, CheckCircleIcon, WarningCircleIcon } from '@phosphor-icons/react'
import type { ActorRefFrom } from 'xstate'
import type { WorkflowDefinitionActor } from '../state'
import { workflowValidationMachine } from '../state/validationMachine'
import type { WorkflowProblem } from './types'

export function ErrorInspector({ actor, validationActor }: { actor: WorkflowDefinitionActor; validationActor: ActorRefFrom<typeof workflowValidationMachine> }) {
  const state = useSelector(validationActor, (snapshot) => snapshot)
  const problems = state.context.problems
  const expanded = state.matches('expanded')
  const errors = problems.filter((problem) => problem.severity === 'error').length
  const warnings = problems.length - errors
  const groups: Array<[string, WorkflowProblem[]]> = [
    ['保存错误', problems.filter((problem) => problem.source === 'server')],
    ['编辑草稿', problems.filter((problem) => problem.source === 'draft')],
    ['工作流定义', problems.filter((problem) => problem.source === 'definition' && !problem.taskPath)],
    ['任务配置', problems.filter((problem) => problem.source === 'definition' && problem.taskPath)],
  ]
  return <footer className={`workflow-error-inspector ${errors ? 'has-errors' : warnings ? 'has-warnings' : 'is-valid'}${expanded ? ' expanded' : ''}`}>
    <button type="button" className="workflow-error-summary" aria-expanded={expanded} aria-controls="workflow-problem-list" onClick={() => validationActor.send({ type: 'TOGGLE' })}>
      {problems.length ? <WarningCircleIcon size={22} /> : <CheckCircleIcon size={22} />}
      <span role="status">{errors ? `${errors} 个错误，修复后可保存` : warnings ? `${warnings} 个警告` : '定义校验通过'}</span>
      <CaretUpIcon size={20} className="error-inspector-caret" />
    </button>
    {expanded ? <div id="workflow-problem-list" className="workflow-problem-list">
      {groups.map(([title, issues]) => issues.length ? <section key={title}>
        <h3>{title} <span>{issues.length}</span></h3>
        <ul>{issues.map((problem) => <li key={problem.id}>
          <strong>{problem.taskReferenceName || (problem.taskPath ? '未命名任务' : problem.source === 'server' ? '保存失败' : '工作流')}</strong>
          <p>{problem.message}</p>
          {problem.path ? <code>{problem.path}</code> : null}
          {problem.source !== 'server' ? <div className="workflow-problem-actions">
            <button type="button" onClick={() => actor.send({ type: 'FOCUS_PROBLEM', problem })}>定位{problem.taskPath ? '任务' : problem.structural ? '源码' : '字段'}</button>
            {problem.source !== 'draft' ? <button type="button" onClick={() => actor.send({ type: 'FOCUS_PROBLEM', problem, tab: 'code' })}>查看源码</button> : null}
          </div> : <small>草稿已保留，可在保存确认中重试。</small>}
        </li>)}</ul>
      </section> : null)}
      {!problems.length ? <p className="workflow-validation-success">当前定义未发现问题。</p> : null}
    </div> : null}
  </footer>
}
