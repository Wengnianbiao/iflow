import type { MouseEvent } from 'react'
import {
  ArrowArcRightIcon,
  CheckIcon,
  CircleNotchIcon,
  DiamondIcon,
  GitForkIcon,
  LightningIcon,
  ProhibitIcon,
  RepeatIcon,
} from '@phosphor-icons/react'
import { conductorTheme, getCardVariant, TaskStatus } from '@/flow/conductorTheme'
import type { NodeTaskData } from '@/flow/conductorMapper'
import { getTaskFlowKind, getTaskPlugin } from '@/tasks'
import { UnknownTaskCard } from '@/tasks/cards'

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
  const Icon = getTaskPlugin(type)?.icon ?? LightningIcon
  return <Icon size={24} />
}

function TaskCardContent({ task }: { task: NodeTaskData['task'] }) {
  const CardContent = getTaskPlugin(task.type)?.card
  if (CardContent === null) return null
  const ResolvedCardContent = CardContent ?? UnknownTaskCard
  return <ResolvedCardContent task={task} />
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
    <span className="iflow-task-status-badge" style={{ background }} title={status === TaskStatus.SCHEDULED ? '等待执行' : status}>
      {[TaskStatus.IN_PROGRESS, TaskStatus.SCHEDULED].includes(status as never) ? <CircleNotchIcon className="spin" size={16} weight="bold" /> : null}
      {status === TaskStatus.COMPLETED ? <CheckIcon size={18} weight="bold" /> : null}
      {[TaskStatus.COMPLETED_WITH_ERRORS, TaskStatus.SKIPPED].includes(status as never) ? <ArrowArcRightIcon size={17} weight="bold" /> : null}
      {failure.includes(status as never) ? <ProhibitIcon size={17} weight="bold" /> : null}
    </span>
  )
}

function cardTypeLabel(type: string) {
  return getTaskPlugin(type)?.cardLabel ?? type
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
        {nodeData.attempts != null && nodeData.attempts > 1 ? (
          <span className="iflow-task-attempts-badge" title={`执行 ${nodeData.attempts} 次`}>{nodeData.attempts}</span>
        ) : null}
        {onDelete ? <DeleteButton onDelete={onDelete} /> : null}
        {getTaskFlowKind(nodeData.task.type) === 'fork' && onAddBranch ? (
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
            <GitForkIcon size={14} weight="bold" style={{ transform: 'rotate(180deg)' }} />
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
  displayDescription,
}: {
  nodeData: NodeTaskData
  onDelete?: (event: MouseEvent<HTMLButtonElement>) => void
  onAddLoopTask?: (event: MouseEvent<HTMLButtonElement>) => void
  displayDescription?: boolean
}) {
  const { task, selected } = nodeData
  const title = displayDescription && task.description
    ? task.description
    : task.name || task.taskReferenceName

  return (
    <div
      className="iflow-do-while"
      style={{
        ...getCardVariant(task.type, nodeData.status ?? TaskStatus.PENDING, selected),
        background: 'rgba(0, 50, 100, 0.5)',
      }}
    >
      <CardStatusBadge status={nodeData.status} />
      {onDelete ? <DeleteButton onDelete={onDelete} /> : null}
      <div className="iflow-do-while-header">
        <RepeatIcon size={24} color="white" />
        <span title={title}>{title}</span>
      </div>
      <div className="iflow-do-while-label">DO_WHILE</div>
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
          <DiamondIcon size={24} />
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
  const flowKind = getTaskFlowKind(taskType)
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
      {flowKind === 'terminal' ? (
        <TerminalTask nodeData={nodeData} portsVisible={portsVisible} />
      ) : flowKind === 'switch' ? (
        <DecisionOperator
          nodeData={nodeData}
          nodeWidth={width}
          portsVisible={portsVisible}
          onAddSwitchCase={onAddSwitchCase}
          onDelete={onDelete}
          displayDescription={displayDescription}
        />
      ) : flowKind === 'switch_join' ? (
        <SwitchJoin nodeData={nodeData} />
      ) : flowKind === 'do_while' ? (
        <DoWhileOperator
          nodeData={nodeData}
          onDelete={onDelete}
          onAddLoopTask={onAddDoWhileTask}
          displayDescription={displayDescription}
        />
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
