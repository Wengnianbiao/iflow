import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react'
import { Helmet } from 'react-helmet'
import {
  ArrowsOutIcon,
  ArrowClockwiseIcon,
  CheckIcon,
  CopyIcon,
  EyeIcon,
  GridFourIcon,
  ListIcon,
  PlayCircleIcon,
  StopCircleIcon,
  XIcon,
} from '@phosphor-icons/react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { getWorkflowExecution, terminateWorkflow } from '@/api/workflowExecution'
import { ConductorFlowCanvas } from '@/components/flow/ConductorFlowCanvas'
import { useMessageContext } from '@/components/providers/messageContext'
import { JsonViewer, serializeJson } from '@/components/ui/JsonViewer'
import type { WorkflowTask } from '@/types/workflow'
import type { TaskExecution, WorkflowExecution } from '@/types/workflowExecution'
import { formatDuration, formatTime, statusText } from './ExecutionExplorerPage'
import styles from './ExecutionDetailPage.module.css'

type MainTab = 'diagram' | 'tasks' | 'timeline' | 'summary' | 'io' | 'json'
type TaskTab = 'summary' | 'input' | 'output' | 'logs' | 'json' | 'definition'
type JsonLayout = 'grid' | 'list'

const MAIN_TABS: Array<[MainTab, string]> = [
  ['diagram', '执行图'],
  ['tasks', '任务列表'],
  ['timeline', '时间线'],
  ['summary', '摘要'],
  ['io', '工作流输入/输出'],
  ['json', 'JSON'],
]

const TASK_TABS: Array<[TaskTab, string]> = [
  ['summary', '摘要'],
  ['input', '输入'],
  ['output', '输出'],
  ['logs', '日志'],
  ['json', 'JSON'],
  ['definition', '定义'],
]

const TERMINAL_WORKFLOW_STATUSES = new Set(['COMPLETED', 'FAILED', 'TIMED_OUT', 'TERMINATED'])

export function ExecutionDetailPage() {
  const { workflowId = '' } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { setMessage } = useMessageContext()
  const [execution, setExecution] = useState<WorkflowExecution | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [terminating, setTerminating] = useState(false)
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [error, setError] = useState('')
  const [taskTab, setTaskTab] = useState<TaskTab>('summary')
  const [selectedTaskRef, setSelectedTaskRef] = useState('')
  const [selectedTaskId, setSelectedTaskId] = useState('')
  const [taskStatusFilter, setTaskStatusFilter] = useState('ALL')
  const [leftPanePercent, setLeftPanePercent] = useState(50)
  const [resizing, setResizing] = useState(false)
  const splitRef = useRef<HTMLDivElement>(null)

  const requestedTab = searchParams.get('tab')
  const mainTab: MainTab = isMainTab(requestedTab) ? requestedTab : 'diagram'

  const load = useCallback(async (background = false) => {
    if (!workflowId) return
    if (background) setRefreshing(true)
    else setLoading(true)
    setError('')
    try {
      setExecution(await getWorkflowExecution(workflowId, true))
    } catch (requestError) {
      if (!background) setExecution(null)
      const message = requestError instanceof Error ? requestError.message : '加载工作流实例失败'
      setError(message)
      if (background) setMessage({ severity: 'error', text: message })
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [setMessage, workflowId])

  useEffect(() => {
    setSelectedTaskRef('')
    setSelectedTaskId('')
    setTaskTab('summary')
    setTaskStatusFilter('ALL')
    setLeftPanePercent(50)
    void load()
  }, [load, workflowId])

  useEffect(() => {
    if (!autoRefresh || !execution || TERMINAL_WORKFLOW_STATUSES.has(execution.status)) return
    const timer = window.setInterval(() => void load(true), 10_000)
    return () => window.clearInterval(timer)
  }, [autoRefresh, execution, load])

  useEffect(() => {
    if (!resizing) return

    const handlePointerMove = (event: PointerEvent) => {
      const container = splitRef.current
      if (!container) return
      const rect = container.getBoundingClientRect()
      const minLeft = Math.min(400, rect.width * 0.45)
      const minRight = Math.min(260, rect.width * 0.35)
      const left = Math.min(Math.max(event.clientX - rect.left, minLeft), rect.width - minRight)
      setLeftPanePercent((left / rect.width) * 100)
    }
    const stopResizing = () => setResizing(false)
    const previousCursor = document.body.style.cursor
    const previousUserSelect = document.body.style.userSelect
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', stopResizing, { once: true })
    return () => {
      document.body.style.cursor = previousCursor
      document.body.style.userSelect = previousUserSelect
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', stopResizing)
    }
  }, [resizing])

  const definitionTasks = useMemo(
    () => flattenTasks(execution?.workflowDefinition?.tasks ?? []),
    [execution?.workflowDefinition],
  )
  const taskDefinitionByRef = useMemo(
    () => new Map(definitionTasks.map((task) => [task.taskReferenceName, task])),
    [definitionTasks],
  )
  const taskRows = useMemo(
    () => [...(execution?.tasks ?? [])].sort((left, right) => (left.seq ?? 0) - (right.seq ?? 0)),
    [execution?.tasks],
  )
  const latestTaskByRef = useMemo(() => {
    const result = new Map<string, TaskExecution>()
    for (const task of taskRows) {
      if (!task.referenceTaskName) continue
      const current = result.get(task.referenceTaskName)
      if (!current || compareTaskAttempts(task, current) > 0) result.set(task.referenceTaskName, task)
    }
    return result
  }, [taskRows])
  const taskById = useMemo(
    () => new Map(taskRows.filter((task) => task.taskId).map((task) => [task.taskId, task])),
    [taskRows],
  )
  const taskStatuses = useMemo(() => {
    const result: Record<string, string | undefined> = {}
    latestTaskByRef.forEach((task, ref) => { result[ref] = task.status })
    return result
  }, [latestTaskByRef])

  const selectedRuntime = selectedTaskId
    ? taskById.get(selectedTaskId)
    : selectedTaskRef
      ? latestTaskByRef.get(selectedTaskRef)
      : undefined
  const activeTaskRef = selectedRuntime?.referenceTaskName || selectedTaskRef
  const selectedDefinition = activeTaskRef ? taskDefinitionByRef.get(activeTaskRef) : undefined
  const taskAttempts = useMemo(
    () => taskRows
      .filter((task) => task.referenceTaskName === activeTaskRef)
      .sort((left, right) => compareTaskAttempts(right, left)),
    [activeTaskRef, taskRows],
  )
  const availableStatuses = useMemo(
    () => Array.from(new Set(taskRows.map((task) => task.status).filter(Boolean))).sort(),
    [taskRows],
  )
  const visibleTaskRows = taskStatusFilter === 'ALL'
    ? taskRows
    : taskRows.filter((task) => task.status === taskStatusFilter)

  const displayName = execution?.workflowDefinition?.displayName?.trim() || '工作流实例'
  const duration = execution?.startTime
    ? formatDuration(((execution.endTime && execution.endTime > 0) ? execution.endTime : Date.now()) - execution.startTime)
    : '-'
  const failedTask = execution?.failedTaskId
    ? taskById.get(execution.failedTaskId)
    : taskRows.find((task) => isFailedStatus(task.status) && task.reasonForIncompletion)
  const taskPanelOpen = Boolean(activeTaskRef)

  const changeMainTab = (tab: MainTab) => {
    const next = new URLSearchParams(searchParams)
    if (tab === 'diagram') next.delete('tab')
    else next.set('tab', tab)
    setSearchParams(next, { replace: true })
  }

  const selectTaskByRef = (ref?: string) => {
    if (!ref) {
      setSelectedTaskRef('')
      setSelectedTaskId('')
      return
    }
    setSelectedTaskRef(ref)
    setSelectedTaskId(latestTaskByRef.get(ref)?.taskId || '')
    setTaskTab('summary')
  }

  const selectTaskExecution = (task: TaskExecution) => {
    setSelectedTaskRef(task.referenceTaskName)
    setSelectedTaskId(task.taskId)
    setTaskTab('summary')
  }

  const jumpToFailedTask = () => {
    if (!failedTask) return
    changeMainTab('diagram')
    selectTaskExecution(failedTask)
  }

  const copyText = async (value: string, label: string) => {
    const notify = (ok: boolean) => {
      setMessage({ severity: ok ? 'success' : 'error', text: ok ? `${label}已复制` : `无法复制${label}` })
      return ok
    }
    // Clipboard API 只在 HTTPS / localhost 可用。局域网 HTTP 要在这次点击里同步走 execCommand。
    if (!window.isSecureContext || !navigator.clipboard?.writeText) {
      return notify(copyWithExecCommand(value))
    }
    try {
      await navigator.clipboard.writeText(value)
      return notify(true)
    } catch {
      return notify(copyWithExecCommand(value))
    }
  }

  const terminate = async () => {
    if (!execution || !window.confirm('确定终止这个工作流实例吗？')) return
    setTerminating(true)
    try {
      await terminateWorkflow(execution.workflowId, '从 iFlow 控制台终止')
      setMessage({ severity: 'success', text: '工作流实例已终止' })
      await load(true)
    } catch (requestError) {
      setMessage({
        severity: 'error',
        text: requestError instanceof Error ? requestError.message : '终止工作流失败',
      })
    } finally {
      setTerminating(false)
    }
  }

  if (loading) return <div className={styles.centerState}>正在加载工作流实例…</div>
  if (error || !execution) {
    return (
      <div className={`${styles.centerState} ${styles.errorState}`}>
        <strong>{error || '找不到工作流实例'}</strong>
        <Link to="/executions">返回执行记录</Link>
      </div>
    )
  }

  const splitStyle = {
    '--execution-left-pane': `${leftPanePercent}%`,
  } as CSSProperties

  return (
    <main className={styles.page}>
      <Helmet><title>执行详情 - {displayName}</title></Helmet>

      <header className={styles.header}>
        <div className={styles.headerCopy}>
          <div className={styles.breadcrumb}>
            <Link to="/executions">工作流执行记录</Link>
            <span>/</span>
            <span title={displayName}>{displayName}</span>
            <CopyButton
              label="复制工作流名称"
              onClick={() => void copyText(displayName, '工作流名称')}
            />
          </div>
          <div className={styles.titleLine}>
            <h1 title={displayName}>{displayName}</h1>
            <CopyButton
              label="复制工作流名称"
              onClick={() => void copyText(displayName, '工作流名称')}
            />
            <StatusBadge status={execution.status} />
          </div>
        </div>

        <div className={styles.headerActions}>
          <button
            type="button"
            className={styles.textAction}
            disabled={!execution.workflowDefinition}
            onClick={() => {
              const definition = execution.workflowDefinition
              if (definition) navigate('/runWorkflow', {
                state: {
                  execution: {
                    workflowName: definition.name,
                    workflowVersion: definition.version,
                    input: execution.input,
                  },
                },
              })
            }}
          >
            <PlayCircleIcon size={16} />执行工作流
          </button>
          <button
            type="button"
            className={styles.textAction}
            disabled={!execution.workflowDefinition}
            onClick={() => {
              const definition = execution.workflowDefinition
              if (definition) navigate(`/workflowDef/edit?name=${encodeURIComponent(definition.name)}&version=${definition.version}`)
            }}
          >
            <EyeIcon size={16} />查看定义
          </button>
          <button type="button" className={styles.textAction} disabled={refreshing} onClick={() => void load(true)}>
            <ArrowClockwiseIcon size={16} className={refreshing ? styles.spinning : ''} />刷新
          </button>
          {!TERMINAL_WORKFLOW_STATUSES.has(execution.status) ? (
            <label className={styles.autoRefresh} title="每 10 秒刷新一次">
              <input type="checkbox" checked={autoRefresh} onChange={(event) => setAutoRefresh(event.target.checked)} />
              <span />自动刷新
            </label>
          ) : null}
          {execution.status === 'RUNNING' ? (
            <button type="button" className={styles.dangerAction} disabled={terminating} onClick={() => void terminate()}>
              <StopCircleIcon size={16} />{terminating ? '终止中…' : '终止'}
            </button>
          ) : null}
        </div>
      </header>

      {execution.reasonForIncompletion && execution.status !== 'COMPLETED' ? (
        <div className={styles.executionAlert} role="alert">
          <span title={execution.reasonForIncompletion}>{execution.reasonForIncompletion}</span>
          {mainTab === 'diagram' && failedTask ? (
            <button type="button" onClick={jumpToFailedTask}>跳转到任务</button>
          ) : null}
        </div>
      ) : null}

      <nav className={styles.contextTabs} aria-label="工作流实例详情">
        {MAIN_TABS.map(([id, label]) => (
          <button key={id} type="button" className={mainTab === id ? styles.activeTab : ''} onClick={() => changeMainTab(id)}>
            {label}
          </button>
        ))}
      </nav>

      <div ref={splitRef} className={`${styles.split} ${taskPanelOpen ? styles.panelOpen : ''}`} style={splitStyle}>
        <section className={styles.mainPane}>
          {mainTab === 'diagram' ? (
            execution.workflowDefinition ? (
              <ConductorFlowCanvas
                workflow={execution.workflowDefinition}
                selectedTaskRef={activeTaskRef}
                taskStatusByRef={taskStatuses}
                readOnly
                onSelectTask={(task) => selectTaskByRef(task?.taskReferenceName)}
              />
            ) : <EmptyPane text="该实例没有工作流配置快照" />
          ) : null}

          {mainTab === 'tasks' ? (
            <TaskListView
              rows={visibleTaskRows}
              allRowsCount={taskRows.length}
              selectedTaskId={selectedRuntime?.taskId}
              definitionByRef={taskDefinitionByRef}
              statusFilter={taskStatusFilter}
              availableStatuses={availableStatuses}
              onStatusFilterChange={setTaskStatusFilter}
              onSelect={selectTaskExecution}
              onCopy={(taskId) => void copyText(taskId, '任务 ID')}
            />
          ) : null}

          {mainTab === 'timeline' ? (
            <TimelineView rows={taskRows} selectedTaskId={selectedRuntime?.taskId} onSelect={selectTaskExecution} />
          ) : null}

          {mainTab === 'summary' ? (
            <div className={styles.scrollPane}>
              <KeyValueTable rows={workflowSummaryRows(execution, displayName, duration, failedTask, selectTaskExecution)} />
            </div>
          ) : null}

          {mainTab === 'io' ? (
            <JsonPairView
              workflowName={displayName}
              input={execution.input}
              output={execution.output}
              onCopy={copyText}
            />
          ) : null}

          {mainTab === 'json' ? (
            <div className={styles.jsonSinglePane}>
              <JsonCard title="完整工作流 JSON" value={execution} onCopy={copyText} />
            </div>
          ) : null}
        </section>

        {taskPanelOpen ? (
          <>
            <div
              className={`${styles.resizer} ${resizing ? styles.resizing : ''}`}
              role="separator"
              aria-label="调整任务详情面板宽度"
              aria-orientation="vertical"
              onPointerDown={(event) => {
                event.preventDefault()
                setResizing(true)
              }}
            />
            <aside className={styles.taskPanel}>
              <header className={styles.taskHeader}>
                <button type="button" className={styles.closeTaskPanel} aria-label="关闭任务详情" onClick={() => selectTaskByRef()}>
                  <XIcon size={20} />
                </button>
                <div className={styles.taskHeaderContent}>
                  <div className={styles.taskTitleLine}>
                    <h2 title={selectedDefinition?.name || selectedRuntime?.taskDefName || activeTaskRef}>
                      {selectedDefinition?.name || selectedRuntime?.taskDefName || activeTaskRef}
                    </h2>
                    <StatusBadge status={selectedRuntime?.status || 'NOT_SCHEDULED'} />
                  </div>
                  {selectedRuntime?.taskId ? (
                    <div className={styles.taskIdLine}>
                      <span title={selectedRuntime.taskId}>{selectedRuntime.taskId}</span>
                      <CopyButton label="复制任务 ID" onClick={() => void copyText(selectedRuntime.taskId, '任务 ID')} />
                    </div>
                  ) : <span className={styles.notScheduled}>该任务尚未调度</span>}

                  {taskAttempts.length > 1 ? (
                    <label className={styles.attemptSelector}>
                      <span>执行尝试</span>
                      <select value={selectedRuntime?.taskId || ''} onChange={(event) => {
                        const task = taskById.get(event.target.value)
                        if (task) selectTaskExecution(task)
                      }}>
                        {taskAttempts.map((task) => (
                          <option key={task.taskId} value={task.taskId}>
                            #{task.retryCount ?? 0} · {statusText(task.status)} · {shortId(task.taskId)}
                          </option>
                        ))}
                      </select>
                    </label>
                  ) : null}
                </div>
              </header>

              <nav className={`${styles.contextTabs} ${styles.taskTabs}`} aria-label="任务详情">
                {TASK_TABS.map(([id, label]) => {
                  const disabled = !selectedRuntime && !['summary', 'definition'].includes(id)
                  return (
                    <button
                      key={id}
                      type="button"
                      disabled={disabled}
                      className={taskTab === id ? styles.activeTab : ''}
                      onClick={() => setTaskTab(id)}
                    >
                      {label}
                    </button>
                  )
                })}
              </nav>

              <div className={styles.taskBody}>
                {taskTab === 'summary' ? (
                  <KeyValueTable rows={taskSummaryRows(selectedRuntime, selectedDefinition, activeTaskRef)} compact />
                ) : null}
                {taskTab === 'input' ? <JsonCard title="任务输入" value={selectedRuntime?.inputData} onCopy={copyText} compact /> : null}
                {taskTab === 'output' ? <JsonCard title="任务输出" value={selectedRuntime?.outputData} onCopy={copyText} compact /> : null}
                {taskTab === 'logs' ? <EmptyPane text="当前 iFlow 执行接口未提供任务日志" compact /> : null}
                {taskTab === 'json' ? <JsonCard title="任务执行 JSON" value={selectedRuntime} onCopy={copyText} compact /> : null}
                {taskTab === 'definition' ? <JsonCard title="任务定义 / 运行配置" value={selectedDefinition} onCopy={copyText} compact /> : null}
              </div>
            </aside>
          </>
        ) : null}
      </div>
    </main>
  )
}

function TaskListView({
  rows,
  allRowsCount,
  selectedTaskId,
  definitionByRef,
  statusFilter,
  availableStatuses,
  onStatusFilterChange,
  onSelect,
  onCopy,
}: {
  rows: TaskExecution[]
  allRowsCount: number
  selectedTaskId?: string
  definitionByRef: Map<string, WorkflowTask>
  statusFilter: string
  availableStatuses: string[]
  onStatusFilterChange: (status: string) => void
  onSelect: (task: TaskExecution) => void
  onCopy: (taskId: string) => void
}) {
  return (
    <div className={styles.tablePane}>
      <div className={styles.tableToolbar}>
        <span>共 {allRowsCount} 个任务{rows.length !== allRowsCount ? `，当前显示 ${rows.length} 个` : ''}</span>
        <label>
          <span>状态</span>
          <select value={statusFilter} onChange={(event) => onStatusFilterChange(event.target.value)}>
            <option value="ALL">全部状态</option>
            {availableStatuses.map((status) => <option key={status} value={status}>{statusText(status)}</option>)}
          </select>
        </label>
      </div>
      <div className={styles.tableScroll}>
        <table className={styles.taskTable}>
          <thead>
            <tr>
              <th title="任务的执行序号">Seq.</th>
              <th title="任务执行的唯一标识">任务 ID</th>
              <th>任务名称</th>
              <th title="任务引用名称">Ref</th>
              <th>Type</th>
              <th>调度时间</th>
              <th>开始时间</th>
              <th>结束时间</th>
              <th>执行耗时</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((task) => (
              <tr
                key={task.taskId || `${task.referenceTaskName}-${task.seq ?? 'task'}`}
                className={selectedTaskId === task.taskId ? styles.selectedRow : ''}
                onClick={() => onSelect(task)}
              >
                <td>{task.seq ?? '-'}</td>
                <td>
                  <span className={styles.taskIdCell}>
                    <button type="button" className={styles.inlineLink} title={task.taskId}>{shortId(task.taskId)}</button>
                    {task.taskId ? (
                      <CopyButton label="复制任务 ID" onClick={(event) => { event.stopPropagation(); onCopy(task.taskId) }} />
                    ) : null}
                  </span>
                </td>
                <td>{task.taskDefName || definitionByRef.get(task.referenceTaskName)?.name || '-'}</td>
                <td title={task.referenceTaskName}>{task.referenceTaskName || '-'}</td>
                <td>{task.taskType || definitionByRef.get(task.referenceTaskName)?.type || '-'}</td>
                <td>{formatTime(task.scheduledTime)}</td>
                <td>{formatTime(task.startTime)}</td>
                <td>{formatTime(task.endTime)}</td>
                <td>{taskDuration(task)}</td>
                <td><StatusBadge status={task.status} /></td>
              </tr>
            ))}
            {!rows.length ? <tr><td colSpan={10} className={styles.emptyTable}>暂无符合条件的任务</td></tr> : null}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function TimelineView({
  rows,
  selectedTaskId,
  onSelect,
}: {
  rows: TaskExecution[]
  selectedTaskId?: string
  onSelect: (task: TaskExecution) => void
}) {
  const now = Date.now()
  const timedRows = rows.filter((task) => task.scheduledTime || task.startTime)
  const minTime = Math.min(...timedRows.map((task) => task.scheduledTime || task.startTime || now), now)
  const maxTaskTime = Math.max(...timedRows.map((task) => task.endTime || task.updateTime || task.startTime || task.scheduledTime || now), minTime + 1)
  const maxTime = maxTaskTime <= minTime ? minTime + 1 : maxTaskTime
  const span = maxTime - minTime
  const ticks = Array.from({ length: 5 }, (_, index) => minTime + (span * index) / 4)

  if (!timedRows.length) return <EmptyPane text="暂无任务时间线" />

  const position = (time: number) => Math.max(0, Math.min(100, ((time - minTime) / span) * 100))
  const width = (start: number, end: number) => Math.max(0.6, position(end) - position(start))

  return (
    <div className={styles.timelinePane}>
      <div className={styles.timelineCard}>
        <div className={styles.timelineAxis}>
          <span />
          <div>{ticks.map((tick) => <time key={tick}>{formatTimelineTick(tick)}</time>)}</div>
        </div>
        <div className={styles.timelineRows}>
          {timedRows.map((task) => {
            const scheduled = task.scheduledTime || task.startTime || minTime
            const started = task.startTime || scheduled
            const ended = task.endTime || task.updateTime || now
            return (
              <button
                key={task.taskId || `${task.referenceTaskName}-${task.seq ?? 'timeline'}`}
                type="button"
                className={`${styles.timelineRow} ${selectedTaskId === task.taskId ? styles.selectedTimelineRow : ''}`}
                onClick={() => onSelect(task)}
              >
                <span className={styles.timelineLabel}>
                  <strong>{task.referenceTaskName || task.taskDefName || '-'}</strong>
                  <small>{task.taskType || '-'} · {statusText(task.status)}</small>
                </span>
                <span className={styles.timelineTrack}>
                  {started > scheduled ? (
                    <span
                      className={styles.queueBar}
                      style={{ left: `${position(scheduled)}%`, width: `${width(scheduled, started)}%` }}
                      title={`排队 ${formatDuration(started - scheduled)}`}
                    />
                  ) : null}
                  <span
                    className={`${styles.executionBar} ${styles[statusTone(task.status)] || ''}`}
                    style={{ left: `${position(started)}%`, width: `${width(started, Math.max(started + 1, ended))}%` }}
                    title={`执行 ${taskDuration(task)}`}
                  />
                </span>
              </button>
            )
          })}
        </div>
      </div>
      <div className={styles.timelineLegend}>
        <span><i className={styles.queueLegend} />任务排队时间</span>
        <span><i className={styles.executionLegend} />任务执行耗时</span>
        <span><i className={styles.failedLegend} />失败 / 超时</span>
      </div>
    </div>
  )
}

function JsonPairView({
  workflowName,
  input,
  output,
  onCopy,
}: {
  workflowName: string
  input?: Record<string, unknown>
  output?: unknown
  onCopy: (value: string, label: string) => Promise<boolean>
}) {
  const [layout, setLayout] = useState<JsonLayout>('grid')
  const [fullScreen, setFullScreen] = useState<'input' | 'output' | null>(null)
  const cards = [
    { id: 'input' as const, title: '输入', value: input },
    { id: 'output' as const, title: '输出', value: output },
  ].filter((item) => !fullScreen || item.id === fullScreen)

  return (
    <div className={styles.ioPane}>
      <div className={styles.jsonLayoutActions} aria-label="JSON 布局">
        <span>{workflowName}</span>
        <button type="button" className={layout === 'list' ? styles.activeLayout : ''} aria-label="列表布局" onClick={() => setLayout('list')}><ListIcon size={18} /></button>
        <button type="button" className={layout === 'grid' ? styles.activeLayout : ''} aria-label="网格布局" onClick={() => setLayout('grid')}><GridFourIcon size={18} /></button>
      </div>
      <div className={`${styles.jsonPair} ${layout === 'list' ? styles.jsonList : ''}`}>
        {cards.map((item) => (
          <JsonCard
            key={item.id}
            title={item.title}
            value={item.value}
            onCopy={onCopy}
            onToggleFullScreen={() => setFullScreen(fullScreen ? null : item.id)}
            fullScreen={fullScreen === item.id}
          />
        ))}
      </div>
    </div>
  )
}

function JsonCard({
  title,
  value,
  onCopy,
  onToggleFullScreen,
  fullScreen = false,
  compact = false,
}: {
  title: string
  value: unknown
  onCopy: (value: string, label: string) => Promise<boolean>
  onToggleFullScreen?: () => void
  fullScreen?: boolean
  compact?: boolean
}) {
  const content = serializeJson(value)
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    const ok = await onCopy(content, title)
    if (!ok) return
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }
  return (
    <section className={`${styles.jsonCard} ${compact ? styles.compactJsonCard : ''}`}>
      <header>
        <h3>{title}</h3>
        <div>
          <button type="button" aria-label={`复制${title}`} title={`复制${title}`} onClick={() => void copy()}>
            {copied ? <CheckIcon size={16} /> : <CopyIcon size={16} />}
          </button>
          {onToggleFullScreen ? (
            <button type="button" aria-label={fullScreen ? '退出全屏' : '全屏显示'} title={fullScreen ? '退出全屏' : '全屏显示'} onClick={onToggleFullScreen}>
              {fullScreen ? <XIcon size={16} /> : <ArrowsOutIcon size={16} />}
            </button>
          ) : null}
        </div>
      </header>
      <JsonViewer value={value} ariaLabel={`${title} JSON`} />
    </section>
  )
}

function KeyValueTable({ rows, compact = false }: { rows: KeyValueRow[]; compact?: boolean }) {
  return (
    <dl className={`${styles.keyValueTable} ${compact ? styles.compactKeyValue : ''}`}>
      {rows.map((row, index) => (
        <div key={`${row.label}-${index}`}>
          <dt>{row.label}</dt>
          <dd className={row.monospace ? styles.monospace : ''}>{row.value ?? '-'}</dd>
        </div>
      ))}
    </dl>
  )
}

type KeyValueRow = { label: string; value: ReactNode; monospace?: boolean }

function workflowSummaryRows(
  execution: WorkflowExecution,
  title: string,
  duration: string,
  failedTask: TaskExecution | undefined,
  onSelectTask: (task: TaskExecution) => void,
): KeyValueRow[] {
  const rows: KeyValueRow[] = [
    { label: '状态', value: <StatusBadge status={execution.status} /> },
  ]
  if (execution.reasonForIncompletion) {
    rows.push({ label: '未完成原因', value: <span className={styles.errorValue}>{execution.reasonForIncompletion}</span> })
  }
  rows.push(
    { label: '工作流名称', value: title },
    { label: '版本', value: execution.workflowDefinition?.version ?? '-' },
    { label: '开始时间', value: formatTime(execution.startTime) },
    { label: '结束时间', value: formatTime(execution.endTime) },
    { label: '耗时', value: duration },
  )
  if (execution.correlationId) rows.push({ label: '关键字', value: execution.correlationId, monospace: true })
  if (failedTask) {
    rows.push({
      label: '失败任务 ID',
      value: <button type="button" className={styles.inlineLink} onClick={() => onSelectTask(failedTask)}>{failedTask.taskId}</button>,
      monospace: true,
    })
  }
  return rows
}

function taskSummaryRows(runtime: TaskExecution | undefined, definition: WorkflowTask | undefined, ref: string): KeyValueRow[] {
  const rows: KeyValueRow[] = [
    { label: '任务类型', value: definition?.type || runtime?.taskType || '-' },
    { label: '状态', value: <StatusBadge status={runtime?.status || 'NOT_SCHEDULED'} /> },
  ]
  if (runtime?.reasonForIncompletion) {
    rows.push({ label: '未完成原因', value: <span className={styles.errorValue}>{runtime.reasonForIncompletion}</span> })
  }
  rows.push(
    { label: '任务名称', value: definition?.name || runtime?.taskDefName || ref },
    { label: '任务引用', value: ref, monospace: true },
  )
  if (!runtime) return rows
  if (runtime.taskId) rows.push({ label: '任务执行 ID', value: runtime.taskId, monospace: true })
  rows.push({ label: '重试次数', value: runtime.retryCount ?? 0 })
  if (runtime.scheduledTime) rows.push({ label: '调度时间', value: formatTime(runtime.scheduledTime) })
  if (runtime.startTime) rows.push({ label: '开始时间', value: formatTime(runtime.startTime) })
  if (runtime.endTime) rows.push({ label: '结束时间', value: formatTime(runtime.endTime) })
  if (runtime.startTime || runtime.scheduledTime) rows.push({ label: '耗时', value: taskDuration(runtime) })
  if (runtime.workerId) rows.push({ label: '执行节点', value: runtime.workerId })
  if (runtime.seq != null) rows.push({ label: '执行序号', value: runtime.seq })
  const output = runtime.outputData
  const evaluatedCase = output && typeof output === 'object' && !Array.isArray(output)
    && 'caseOutput' in output ? output.caseOutput : undefined
  if (['DECISION', 'SWITCH'].includes(runtime.taskType || definition?.type || '') && Array.isArray(evaluatedCase)) {
    rows.push({ label: '匹配分支', value: String(evaluatedCase[0] ?? '-') })
  }
  return rows
}

function StatusBadge({ status }: { status?: string }) {
  return <span className={`${styles.statusBadge} ${styles[statusTone(status)] || ''}`}>{statusText(status)}</span>
}

function copyWithExecCommand(value: string) {
  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '0'
  textarea.style.left = '0'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  try {
    textarea.focus()
    textarea.select()
    textarea.setSelectionRange(0, textarea.value.length)
    return document.execCommand('copy')
  } finally {
    textarea.remove()
  }
}

function CopyButton({ label, onClick }: { label: string; onClick: (event: React.MouseEvent<HTMLButtonElement>) => void }) {
  return <button type="button" className={styles.copyButton} aria-label={label} title={label} onClick={onClick}><CopyIcon size={14} /></button>
}

function EmptyPane({ text, compact = false }: { text: string; compact?: boolean }) {
  return <div className={`${styles.emptyPane} ${compact ? styles.compactEmptyPane : ''}`}>{text}</div>
}

function isMainTab(value: string | null): value is MainTab {
  return MAIN_TABS.some(([tab]) => tab === value)
}

function compareTaskAttempts(left: TaskExecution, right: TaskExecution) {
  const retryDifference = (left.retryCount ?? 0) - (right.retryCount ?? 0)
  if (retryDifference !== 0) return retryDifference
  return taskTime(left) - taskTime(right)
}

function taskTime(task: TaskExecution) {
  return task.updateTime ?? task.endTime ?? task.startTime ?? task.scheduledTime ?? task.seq ?? 0
}

function taskDuration(task: TaskExecution) {
  const start = task.startTime || task.scheduledTime
  if (!start) return '-'
  return formatDuration(Math.max(0, ((task.endTime && task.endTime > 0) ? task.endTime : Date.now()) - start))
}

function shortId(value?: string) {
  if (!value) return '-'
  return value.length > 12 ? `${value.slice(0, 4)}…${value.slice(-4)}` : value
}

function formatTimelineTick(value: number) {
  const date = new Date(value)
  const pad = (part: number, length = 2) => String(part).padStart(length, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`
}

function statusTone(status?: string) {
  if (status === 'COMPLETED') return 'statusCompleted'
  if (status === 'COMPLETED_WITH_ERRORS') return 'statusWarning'
  if (status === 'RUNNING' || status === 'IN_PROGRESS' || status === 'SCHEDULED') return 'statusRunning'
  if (status === 'FAILED' || status === 'FAILED_WITH_TERMINAL_ERROR' || status === 'TIMED_OUT') return 'statusFailed'
  if (status === 'TERMINATED' || status === 'CANCELED' || status === 'SKIPPED') return 'statusStopped'
  return 'statusPending'
}

function isFailedStatus(status?: string) {
  return status?.includes('FAILED') || status === 'TIMED_OUT'
}

function taskList(value: unknown): WorkflowTask[] {
  return Array.isArray(value) ? value : []
}

function flattenTasks(tasks: WorkflowTask[]): WorkflowTask[] {
  const result: WorkflowTask[] = []
  for (const task of tasks) {
    result.push(task)
    for (const branch of Object.values(task.decisionCases ?? {})) result.push(...flattenTasks(taskList(branch)))
    result.push(...flattenTasks(taskList(task.defaultCase)))
    for (const branch of task.forkTasks ?? []) result.push(...flattenTasks(taskList(branch)))
    result.push(...flattenTasks(taskList(task.loopOver)))
  }
  return result
}
