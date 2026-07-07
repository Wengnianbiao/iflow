import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ArrowClockwise, MagnifyingGlass } from '@phosphor-icons/react'
import { Link, useSearchParams } from 'react-router-dom'
import { searchWorkflowExecutions } from '@/api/workflowExecution'
import type { WorkflowExecutionSummary } from '@/types/workflowExecution'

const STATUS_OPTIONS = ['RUNNING', 'COMPLETED', 'FAILED', 'TIMED_OUT', 'TERMINATED', 'PAUSED']

export function ExecutionExplorerPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [workflowName, setWorkflowName] = useState(searchParams.get('workflowType') ?? searchParams.get('workflowName') ?? '')
  const [workflowId, setWorkflowId] = useState(searchParams.get('workflowId') ?? '')
  const [status, setStatus] = useState(searchParams.get('status') ?? '')
  const [rows, setRows] = useState<WorkflowExecutionSummary[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async (nextPage = pageNum) => {
    setLoading(true)
    setError('')
    try {
      const result = await searchWorkflowExecutions({
        workflowName: workflowName.trim() || undefined,
        workflowId: workflowId.trim() || undefined,
        status: status ? [status] : undefined,
        pageNum: nextPage,
        pageSize: 15,
      })
      setRows(result.rows ?? [])
      setTotal(result.total ?? 0)
      setPageNum(result.pageNum ?? nextPage)
    } catch (requestError) {
      setRows([])
      setTotal(0)
      setError(requestError instanceof Error ? requestError.message : '搜索执行记录失败')
    } finally {
      setLoading(false)
    }
  }, [pageNum, status, workflowId, workflowName])

  useEffect(() => {
    void load(1)
    // URL 初始筛选只在进入页面时应用，之后由表单提交驱动。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function submit(event: FormEvent) {
    event.preventDefault()
    const next = new URLSearchParams()
    if (workflowName.trim()) next.set('workflowType', workflowName.trim())
    if (workflowId.trim()) next.set('workflowId', workflowId.trim())
    if (status) next.set('status', status)
    setSearchParams(next, { replace: true })
    setPageNum(1)
    void load(1)
  }

  const pageStart = total ? (pageNum - 1) * 15 + 1 : 0
  const pageEnd = Math.min(pageNum * 15, total)

  return (
    <main className="execution-explorer-page">
      <header className="execution-page-header">
        <div>
          <h1>工作流执行记录</h1>
          <p>搜索、查看和排查工作流实例</p>
        </div>
        <button type="button" className="outline-action" disabled={loading} onClick={() => void load()}>
          <ArrowClockwise size={18} className={loading ? 'spin' : ''} />
          刷新
        </button>
      </header>

      <form className="execution-search-panel" onSubmit={submit}>
        <label>
          <span>工作流名称</span>
          <input value={workflowName} onChange={(event) => setWorkflowName(event.target.value)} placeholder="例如：get_item_result_workflow" />
        </label>
        <label>
          <span>工作流 ID</span>
          <input value={workflowId} onChange={(event) => setWorkflowId(event.target.value)} placeholder="输入完整实例 ID" />
        </label>
        <label>
          <span>状态</span>
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="">全部状态</option>
            {STATUS_OPTIONS.map((item) => <option key={item} value={item}>{statusText(item)}</option>)}
          </select>
        </label>
        <button type="submit" className="primary-action">
          <MagnifyingGlass size={17} />
          搜索
        </button>
      </form>

      <section className="execution-results-card">
        <div className="execution-results-toolbar">
          <strong>{loading ? '正在加载…' : `共 ${total} 条记录`}</strong>
          {error ? <span className="list-error">{error}</span> : null}
        </div>
        <div className="execution-table-scroll">
          <table className="execution-table">
            <thead>
              <tr>
                <th>工作流名称</th>
                <th>版本</th>
                <th>状态</th>
                <th>开始时间</th>
                <th>耗时</th>
                <th>关键字</th>
                <th>工作流 ID</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.workflowId}>
                  <td><Link className="workflow-name-link" to={`/executions/${encodeURIComponent(row.workflowId)}`}>{row.workflowName}</Link></td>
                  <td>{row.workflowVersion}</td>
                  <td><span className={`execution-status status-${row.status.toLowerCase()}`}>{statusText(row.status)}</span></td>
                  <td>{formatTime(row.startTime)}</td>
                  <td>{formatDuration(row.durationMs ?? ((row.endTime ?? Date.now()) - row.startTime))}</td>
                  <td className="mono-cell">{row.correlationId || '-'}</td>
                  <td><Link className="query-link mono-cell" to={`/executions/${encodeURIComponent(row.workflowId)}`}>{row.workflowId}</Link></td>
                </tr>
              ))}
              {!loading && rows.length === 0 ? (
                <tr><td colSpan={7} className="empty-table">没有匹配的执行记录</td></tr>
              ) : null}
            </tbody>
          </table>
        </div>
        <footer className="execution-table-footer">
          <span>每页 15 条</span>
          <span>{pageStart}-{pageEnd} / {total}</span>
          <button type="button" disabled={pageNum <= 1 || loading} onClick={() => void load(pageNum - 1)}>‹</button>
          <button type="button" disabled={pageEnd >= total || loading} onClick={() => void load(pageNum + 1)}>›</button>
        </footer>
      </section>
    </main>
  )
}

export function statusText(status?: string) {
  const labels: Record<string, string> = {
    RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败', TIMED_OUT: '已超时', TERMINATED: '已终止', PAUSED: '已暂停',
    SCHEDULED: '已调度', IN_PROGRESS: '执行中', CANCELED: '已取消', FAILED_WITH_TERMINAL_ERROR: '终止性失败',
    COMPLETED_WITH_ERRORS: '完成但有错误', SKIPPED: '已跳过', NOT_SCHEDULED: '未调度',
  }
  return labels[status ?? ''] ?? status ?? '-'
}

export function formatTime(value?: number | null) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value))
}

export function formatDuration(value?: number | null) {
  if (value == null || value < 0) return '-'
  if (value < 1000) return `${value} 毫秒`
  const seconds = Math.floor(value / 1000)
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分 ${seconds % 60} 秒`
  const hours = Math.floor(minutes / 60)
  return `${hours} 小时 ${minutes % 60} 分`
}
