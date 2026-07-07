import type { MouseEvent } from 'react'
import {
  BracketsCurly,
  ArrowArcRight,
  Check,
  CircleNotch,
  Database,
  Diamond,
  FileJs,
  Globe,
  GitBranch,
  Lightning,
  Link as LinkIcon,
  PlugsConnected,
  Prohibit,
  Repeat,
  SlidersHorizontal,
} from '@phosphor-icons/react'
import { TaskType } from '@/flow/taskType'
import { conductorTheme, getCardVariant, TaskStatus } from '@/flow/conductorTheme'
import type { NodeTaskData } from '@/flow/conductorMapper'
import type { WorkflowTask } from '@/types/workflow'
import { InlineHighlightedCode } from './InlineScriptCode'

type TaskShapeProps = {
  nodeData: NodeTaskData
  width?: number
  height?: number
  portsVisible?: boolean
  nodeId: string
  onAddSwitchCase?: (event: MouseEvent<HTMLButtonElement>) => void
  onAddDoWhileTask?: (event: MouseEvent<HTMLButtonElement>) => void
  onDelete?: (event: MouseEvent<HTMLButtonElement>) => void
  displayDescription?: boolean
}

function text(value: unknown) {
  if (value == null) return ''
  return typeof value === 'string' ? value : JSON.stringify(value)
}

function truncate(value: string, max = 110) {
  return value.length > max ? `${value.slice(0, max)}...` : value
}

function StarShape() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width="100%" height="100%" viewBox="0 0 278 276" preserveAspectRatio="none">
      <defs>
        <path
          id="iflow-switch-star"
          d="M153.079 11.089l113.137 113.137c4.668 4.588 7.118 9.272 7.35 14.053.231 4.78-2.219 9.525-7.351 14.232L153.077 265.648c-5.185 4.12-9.7 6.34-13.543 6.662-3.843.322-8.756-1.898-14.74-6.662L11.655 152.511c-4.73-6.095-7.14-11.2-7.237-15.313-.09-4.114 2.321-8.437 7.237-12.97L124.793 11.09c5.348-4.67 10.073-7.14 14.173-7.41 4.1-.271 8.804 2.198 14.113 7.409z"
        />
        <filter id="iflow-switch-star-shadow" width="104.5%" height="104.5%" x="-2.2%" y="-2.2%" filterUnits="objectBoundingBox">
          <feOffset in="SourceAlpha" result="shadowOffsetOuter1" />
          <feGaussianBlur in="shadowOffsetOuter1" result="shadowBlurOuter1" stdDeviation="2" />
          <feColorMatrix in="shadowBlurOuter1" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.5 0" />
        </filter>
      </defs>
      <g fill="none" fillRule="evenodd" stroke="none" strokeWidth="1">
        <use fill="#000" filter="url(#iflow-switch-star-shadow)" href="#iflow-switch-star" />
        <use fill="#FFF" href="#iflow-switch-star" />
      </g>
    </svg>
  )
}

function TerminalTask({ nodeData, portsVisible }: { nodeData: NodeTaskData; portsVisible?: boolean }) {
  const task = nodeData.task
  const isStart = task.taskReferenceName === '__start__'
  return (
    <div
      className="iflow-terminal-task"
      style={{
        color: conductorTheme.terminalTask.color,
        background: conductorTheme.terminalTask.background,
        border: conductorTheme.terminalTask.border,
        marginTop: isStart && portsVisible ? -10 : 0,
      }}
      onClick={(event) => event.stopPropagation()}
    >
      {isStart ? '开始' : '结束'}
    </div>
  )
}

function SwitchJoin({ nodeData }: { nodeData: NodeTaskData }) {
  return (
    <div className="iflow-switch-join" onClick={(event) => event.stopPropagation()}>
      <span>{'// 条件分支结束'}</span>
      <strong>{nodeData.task.taskReferenceName}</strong>
    </div>
  )
}

function TaskIcon({ type }: { type: string }) {
  const size = 24
  switch (type) {
    case TaskType.HTTP:
      return <Globe size={size} />
    case TaskType.INLINE:
      return <FileJs size={size} />
    case TaskType.JSON_JQ_TRANSFORM:
      return <BracketsCurly size={size} />
    case TaskType.JDBC:
      return <Database size={size} />
    case TaskType.WEBSERVICE:
      return <PlugsConnected size={size} />
    case TaskType.PARAM_MAP:
      return <SlidersHorizontal size={size} />
    case TaskType.SPI:
      return <PlugsConnected size={size} />
    case TaskType.SWITCH:
    case TaskType.DECISION:
    case TaskType.FORK_JOIN:
      return <GitBranch size={size} />
    case TaskType.DO_WHILE:
      return <Repeat size={size} />
    default:
      return <Lightning size={size} />
  }
}

function HTTPTaskContent({ task }: { task: WorkflowTask }) {
  const ip = task.inputParameters ?? {}
  const request = (ip.http_request as Record<string, unknown> | undefined) ?? ip
  const method = String(request.method ?? 'GET')
  const uri = String(request.uri ?? '')
  const clickable = method.toUpperCase() === 'GET' && /^https?:\/\//i.test(uri)
  return (
    <div className="iflow-card-content iflow-http-content">
      <LinkIcon className="iflow-http-link-icon" size={18} />
      <span className={`iflow-method-badge method-${method.toLowerCase()}`}>{method}</span>
      {clickable ? (
        <a className="iflow-uri" href={uri} target="_blank" rel="noreferrer" onClick={(event) => event.stopPropagation()}>
          {uri}
        </a>
      ) : (
        <span className="iflow-uri">{uri || '-'}</span>
      )}
    </div>
  )
}

function InlineTaskContent({ task }: { task: WorkflowTask }) {
  const inputParameters = task.inputParameters ?? {}
  const expression = String(inputParameters.expression ?? '')

  return (
    <code className="iflow-card-content iflow-inline-content language-js">
      <InlineHighlightedCode code={expression} />
    </code>
  )
}

function JDBCTaskContent({ task }: { task: WorkflowTask }) {
  const ip = task.inputParameters ?? {}
  const nestedRequest = ip.jdbc_request
  const request = nestedRequest && typeof nestedRequest === 'object' && !Array.isArray(nestedRequest)
    ? nestedRequest as Record<string, unknown>
    : ip
  const operation = String(request.operation ?? 'QUERY').toUpperCase()
  const sql = String(request.sql ?? '').replace(/\s+/g, ' ').trim()
  const vendor = String(request.vendor ?? '').toUpperCase().trim()
  const url = String(request.url ?? '').trim()
  const summary = [vendor, sql || url].filter(Boolean).join(' · ')
  return (
    <div className="iflow-card-content iflow-http-content" title={[url, sql].filter(Boolean).join('\n')}>
      <Database className="iflow-http-link-icon" size={18} />
      <span className={`iflow-method-badge jdbc-operation-${operation.toLowerCase()}`}>{operation}</span>
      <span className="iflow-uri">{summary ? truncate(summary) : '-'}</span>
    </div>
  )
}

function WebServiceTaskContent({ task }: { task: WorkflowTask }) {
  const ip = task.inputParameters ?? {}
  const uri = String(ip.uri ?? '')
  return (
    <div className="iflow-card-content iflow-http-content">
      <LinkIcon className="iflow-http-link-icon" size={18} />
      <span className="iflow-method-badge">SOAP</span>
      <span className="iflow-uri">{uri || '-'}</span>
    </div>
  )
}

function SpiTaskContent({ task }: { task: WorkflowTask }) {
  const inputParameters = task.inputParameters ?? {}
  const remoteExtension = String(inputParameters.remoteExtension ?? '').trim()
  return (
    <div className="iflow-card-content iflow-http-content">
      <PlugsConnected className="iflow-http-link-icon" size={18} />
      <span className="iflow-method-badge">SPI</span>
      <span className="iflow-uri">{remoteExtension || 'default'}</span>
    </div>
  )
}

function GenericTaskContent({ task }: { task: WorkflowTask }) {
  const ip = task.inputParameters ?? {}
  const candidates: Array<[string, unknown]> = [
    ['topic', ip.topic],
    ['event', ip.eventType ?? ip.eventName],
    ['workflow', ip.name],
    ['duration', ip.duration],
  ]
  const values = candidates.filter(([, value]) => value != null && value !== '')

  if (!values.length) return null
  return (
    <div className="iflow-card-content iflow-generic-content">
      {values.slice(0, 2).map(([label, value]) => (
        <div key={label}>
          <span>{label}</span>
          <strong>{truncate(text(value), 80)}</strong>
        </div>
      ))}
    </div>
  )
}

function TaskCardContent({ task }: { task: WorkflowTask }) {
  switch (task.type) {
    case TaskType.HTTP:
      return <HTTPTaskContent task={task} />
    case TaskType.INLINE:
    case TaskType.JSON_JQ_TRANSFORM:
      return <InlineTaskContent task={task} />
    case TaskType.JDBC:
      return <JDBCTaskContent task={task} />
    case TaskType.WEBSERVICE:
      return <WebServiceTaskContent task={task} />
    case TaskType.SPI:
      return <SpiTaskContent task={task} />
    case TaskType.PARAM_MAP:
      return null
    default:
      return <GenericTaskContent task={task} />
  }
}

function DeleteButton({ onDelete }: { onDelete?: (event: MouseEvent<HTMLButtonElement>) => void }) {
  return (
    <button
      type="button"
      className="DeleteButton iflow-delete-button"
      title="删除任务"
      onClick={(event) => {
        event.preventDefault()
        event.stopPropagation()
        onDelete?.(event)
      }}
    >
      ×
    </button>
  )
}

function CardStatusBadge({ status }: { status?: string }) {
  const failure = [TaskStatus.CANCELED, TaskStatus.FAILED, TaskStatus.FAILED_WITH_TERMINAL_ERROR, TaskStatus.TIMED_OUT]
  const background =
    status === TaskStatus.COMPLETED ? '#18b264'
      : status === TaskStatus.COMPLETED_WITH_ERRORS ? '#eeaa00'
        : status === TaskStatus.SKIPPED ? '#f5bf42'
          : status === TaskStatus.CANCELED ? '#fba404'
            : failure.includes(status as never) ? '#dd2222'
              : [TaskStatus.IN_PROGRESS, TaskStatus.SCHEDULED].includes(status as never) ? '#ffffff'
                : ''
  if (!background) return null
  return (
    <span className="iflow-task-status-badge" style={{ background }} title={status}>
      {[TaskStatus.IN_PROGRESS, TaskStatus.SCHEDULED].includes(status as never) ? <CircleNotch className="spin" size={16} weight="bold" /> : null}
      {status === TaskStatus.COMPLETED ? <Check size={18} weight="bold" /> : null}
      {[TaskStatus.COMPLETED_WITH_ERRORS, TaskStatus.SKIPPED].includes(status as never) ? <ArrowArcRight size={17} weight="bold" /> : null}
      {failure.includes(status as never) ? <Prohibit size={17} weight="bold" /> : null}
    </span>
  )
}

function cardTypeLabel(type: string) {
  if (type === TaskType.JSON_JQ_TRANSFORM) return 'JSON JQ'
  if (type === TaskType.FORK_JOIN) return 'FORK JOIN'
  if (type === TaskType.INLINE) return '内联脚本'
  return type
}

function TaskCard({
  nodeData,
  onDelete,
  onAddBranch,
  displayDescription,
}: {
  nodeData: NodeTaskData
  onDelete?: (event: MouseEvent<HTMLButtonElement>) => void
  onAddBranch?: (event: MouseEvent<HTMLButtonElement>) => void
  displayDescription?: boolean
}) {
  const { task, selected } = nodeData
  return (
    <div
      className={`iflow-task-card ${selected ? 'selected' : ''}`}
      style={getCardVariant(task.type, nodeData.status ?? TaskStatus.PENDING, selected)}
    >
      <div className="iflow-task-card-inner">
        <CardStatusBadge status={nodeData.status} />
        {onDelete ? <DeleteButton onDelete={onDelete} /> : null}
        {nodeData.task.type === TaskType.FORK_JOIN && onAddBranch ? (
          <button
            type="button"
            className="iflow-fork-add"
            title="添加并行分支"
            aria-label="添加并行分支"
            onClick={(event) => {
              event.preventDefault()
              event.stopPropagation()
              onAddBranch(event)
            }}
          >
            <GitBranch size={14} weight="bold" />
            <span>添加分支</span>
          </button>
        ) : null}
        <div className="iflow-task-card-header">
          <div className="iflow-task-card-icon">
            <TaskIcon type={task.type} />
          </div>
          <div className="iflow-task-card-title">
            <div>{task.name || task.taskReferenceName}</div>
            <span>{task.taskReferenceName}</span>
          </div>
          <div className="iflow-task-card-label" title={task.type}>{cardTypeLabel(task.type)}</div>
        </div>
        <TaskCardContent task={task} />
        {displayDescription && task.description ? <p className="iflow-task-description">{task.description}</p> : null}
      </div>
    </div>
  )
}

function DoWhileOperator({
  nodeData,
  onDelete,
  onAddLoopTask,
}: {
  nodeData: NodeTaskData
  onDelete?: (event: MouseEvent<HTMLButtonElement>) => void
  onAddLoopTask?: (event: MouseEvent<HTMLButtonElement>) => void
}) {
  const { task, selected } = nodeData
  return (
    <div className={`iflow-do-while ${selected ? 'selected' : ''}`}>
      {onDelete ? <DeleteButton onDelete={onDelete} /> : null}
      <div className="iflow-do-while-header">
        <Repeat size={22} />
        <div>
          <strong>{task.name || task.taskReferenceName}</strong>
          <span>{task.taskReferenceName}</span>
        </div>
        <em>DO WHILE</em>
      </div>
      <div className="iflow-do-while-condition" title={task.loopCondition || ''}>
        条件：{task.loopCondition || '未配置'}
      </div>
      {!task.loopOver?.length && onAddLoopTask ? (
        <button
          type="button"
          className="iflow-do-while-add"
          title="添加循环体任务"
          aria-label="添加循环体任务"
          onClick={(event) => {
            event.preventDefault()
            event.stopPropagation()
            onAddLoopTask(event)
          }}
        >
          +
        </button>
      ) : null}
    </div>
  )
}

function DecisionOperator({
  nodeData,
  nodeWidth,
  portsVisible,
  onAddSwitchCase,
  onDelete,
  displayDescription,
}: {
  nodeData: NodeTaskData
  nodeWidth: number
  portsVisible: boolean
  onAddSwitchCase?: (event: MouseEvent<HTMLButtonElement>) => void
  onDelete?: (event: MouseEvent<HTMLButtonElement>) => void
  displayDescription?: boolean
}) {
  const { task, selected } = nodeData
  const branchCount = Object.keys(task.decisionCases ?? {}).length + 1
  const switchAddPosition =
    branchCount === 1 ? { bottom: '-12px', right: '110px' }
      : branchCount === 2 || branchCount === 3 ? { bottom: '-12px', right: '7px' }
        : { bottom: '15px', right: '-10px' }

  return (
    <div className="iflow-decision-outer">
      <div
        className={`iflow-decision-card ${selected ? 'selected' : ''}`}
        style={{
          width: `${nodeWidth - 100}px`,
          height: `${portsVisible ? 190 : 200}px`,
          ...getCardVariant(task.type, nodeData.status ?? TaskStatus.PENDING, selected),
        }}
      >
        {onDelete ? <DeleteButton onDelete={onDelete} /> : null}
        <div className="iflow-decision-star">
          <StarShape />
        </div>
        <div className="iflow-decision-content">
          <Diamond size={24} />
          <div>{task.name || task.taskReferenceName}</div>
          <span>{task.taskReferenceName}</span>
        </div>
        {onAddSwitchCase ? (
          <button
            type="button"
            className="iflow-switch-add"
            style={switchAddPosition}
            title="添加分支"
            onClick={(event) => {
              event.preventDefault()
              event.stopPropagation()
              onAddSwitchCase(event)
            }}
          >
            +
          </button>
        ) : null}
        {displayDescription && task.description ? <p className="iflow-decision-description">{task.description}</p> : null}
      </div>
    </div>
  )
}

export function TaskShape({
  nodeData,
  width = 0,
  height = 0,
  portsVisible = false,
  nodeId,
  onAddSwitchCase,
  onAddDoWhileTask,
  onDelete,
  displayDescription,
}: TaskShapeProps) {
  const taskType = nodeData.task.type
  const bottomMargin = portsVisible ? 10 : 0

  return (
    <div
      className="iflow-shape"
      data-node-id={nodeId}
      style={{
        width,
        height: Math.max(0, height - bottomMargin),
        opacity: 1,
      }}
    >
      {taskType === TaskType.TERMINAL ? (
        <TerminalTask nodeData={nodeData} portsVisible={portsVisible} />
      ) : taskType === TaskType.SWITCH || taskType === TaskType.DECISION ? (
        <DecisionOperator
          nodeData={nodeData}
          nodeWidth={width}
          portsVisible={portsVisible}
          onAddSwitchCase={onAddSwitchCase}
          onDelete={onDelete}
          displayDescription={displayDescription}
        />
      ) : taskType === TaskType.SWITCH_JOIN ? (
        <SwitchJoin nodeData={nodeData} />
      ) : taskType === TaskType.DO_WHILE ? (
        <DoWhileOperator nodeData={nodeData} onDelete={onDelete} onAddLoopTask={onAddDoWhileTask} />
      ) : (
        <TaskCard
          nodeData={nodeData}
          onDelete={onDelete}
          onAddBranch={onAddSwitchCase}
          displayDescription={displayDescription}
        />
      )}
    </div>
  )
}
