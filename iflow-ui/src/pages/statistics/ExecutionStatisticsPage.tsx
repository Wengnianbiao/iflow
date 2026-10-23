import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Helmet } from 'react-helmet'
import { Link, useSearchParams } from 'react-router-dom'
import {
  Alert, Button, Chip, Dialog, DialogContent, DialogTitle, LinearProgress, MenuItem, Paper,
  Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
  TableSortLabel, TextField, ToggleButton, ToggleButtonGroup,
} from '@mui/material'
import { ArrowClockwiseIcon } from '@phosphor-icons/react'
import { getExecutionStatistics, getTaskStatisticsInstances } from '@/api/executionStatistics'
import { listWorkflowDefinitions } from '@/api/workflowDefinition'
import { SectionHeader } from '@/components/layout/SectionHeader'
import { SectionContainer } from '@/components/layout/SectionContainer'
import type { StatisticsMode, StatisticsParams, StatisticsRow, StatisticsSort } from '@/types/executionStatistics'
import { formatExecutionTime } from '@/pages/executions/formatters'
import { statusText } from '@/pages/executions/ExecutionExplorerPage'
import { localDateTime, presetRange, statisticsDuration, statisticsRate, type TimePreset } from './statisticsState'
import styles from './ExecutionStatisticsPage.module.css'

const SORTS: StatisticsSort[] = ['totalCount', 'avgDurationMs', 'minDurationMs', 'maxDurationMs', 'successRate']
const PRESETS: TimePreset[] = ['today', 'yesterday', 'last7Days', 'custom']
const count = (value?: number) => value == null ? '—' : value.toLocaleString('zh-CN')

export function ExecutionStatisticsPage() {
  const [url, setUrl] = useSearchParams()
  const mode: StatisticsMode = url.get('view') === 'task' ? 'task' : 'workflow'
  const preset = PRESETS.find((item) => item === url.get('range')) ?? 'today'
  const today = presetRange('today')
  const range = preset === 'custom'
    ? { startTime: Number(url.get('startTime') ?? today.startTime), endTime: Number(url.get('endTime') ?? today.endTime) }
    : presetRange(preset)
  const validRange = Number.isSafeInteger(range.startTime) && Number.isSafeInteger(range.endTime)
    && range.startTime >= 0 && range.endTime >= range.startTime
  const sort = SORTS.find((item) => item === url.get('sort')) ?? 'totalCount'
  const pageNum = Math.max(1, Math.floor(Number(url.get('page')) || 1))
  const pageSize = [20, 50, 100].find((size) => size === Number(url.get('pageSize'))) ?? 20
  const params: StatisticsParams = {
    ...range, workflowRoute: url.get('workflowRoute') || undefined,
    asyncOnly: url.get('asyncOnly') !== 'false', sort,
    descending: url.get('descending') !== 'false', pageNum, pageSize,
  }
  const [workflowDraft, setWorkflowDraft] = useState(params.workflowRoute ?? '')
  const [startDraft, setStartDraft] = useState(localDateTime(range.startTime))
  const [endDraft, setEndDraft] = useState(localDateTime(range.endTime))
  const [selected, setSelected] = useState<{ row: StatisticsRow; params: StatisticsParams } | null>(null)
  const definitions = useQuery({ queryKey: ['statistics-workflow-options'], queryFn: listWorkflowDefinitions })
  const query = useQuery({
    queryKey: ['execution-statistics', mode, params],
    queryFn: ({ signal }) => getExecutionStatistics(mode, params, signal),
    enabled: validRange, retry: false,
  })
  const data = validRange ? query.data : undefined
  const metrics = data?.summary

  useEffect(() => { setWorkflowDraft(url.get('workflowRoute') ?? '') }, [url])
  useEffect(() => {
    setStartDraft(localDateTime(range.startTime))
    setEndDraft(localDateTime(range.endTime))
  }, [range.startTime, range.endTime])

  const update = (changes: Record<string, string>, resetPage = true) => {
    const next = new URLSearchParams(url)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    if (resetPage) next.delete('page')
    setSelected(null)
    if (next.toString() === url.toString()) void query.refetch()
    else setUrl(next)
  }
  const changePreset = (value: TimePreset) => update(value === 'custom'
    ? { range: value, startTime: String(range.startTime), endTime: String(range.endTime) }
    : { range: value, startTime: '', endTime: '' })
  const applyFilters = () => {
    const changes: Record<string, string> = { workflowRoute: workflowDraft.trim() }
    if (preset === 'custom') {
      changes.startTime = String(new Date(startDraft).getTime())
      changes.endTime = String(new Date(endDraft).getTime() + 59_999)
    }
    update(changes)
  }
  const changeSort = (value: StatisticsSort) => update({
    sort: value, descending: String(sort === value ? !params.descending : true),
  })
  const workflowLink = (row: StatisticsRow) => `/executions?${new URLSearchParams({
    workflowRoute: row.workflowRoute, startFrom: String(range.startTime), startTo: String(range.endTime),
  })}`
  const sortable = (label: string, field: StatisticsSort) => (
    <TableCell align="right" sortDirection={sort === field ? (params.descending ? 'desc' : 'asc') : false}>
      <TableSortLabel active={sort === field} direction={sort === field && !params.descending ? 'asc' : 'desc'} onClick={() => changeSort(field)}>
        {label}
      </TableSortLabel>
    </TableCell>
  )

  return <>
    <Helmet><title>调用看板</title></Helmet>
    <SectionHeader title="调用看板" actions={
      <Button variant="outlined" startIcon={<ArrowClockwiseIcon size={16} />} disabled={query.isFetching || !validRange}
        onClick={() => void query.refetch()}>刷新</Button>
    } />
    <SectionContainer>
      <Paper variant="outlined">
        <div className={styles.toolbar}>
          <ToggleButtonGroup exclusive size="small" value={mode} aria-label="统计模式"
            onChange={(_event, value: StatisticsMode | null) => value && update({ view: value })}>
            <ToggleButton value="workflow">工作流</ToggleButton><ToggleButton value="task">任务</ToggleButton>
          </ToggleButtonGroup>
          {mode === 'task' && <ToggleButtonGroup exclusive size="small" value={params.asyncOnly ? 'async' : 'all'} aria-label="任务范围"
            onChange={(_event, value: string | null) => value && update({ asyncOnly: String(value === 'async') })}>
            <ToggleButton value="all">全部任务</ToggleButton><ToggleButton value="async">异步任务</ToggleButton>
          </ToggleButtonGroup>}
        </div>
        <form className={styles.filters} onSubmit={(event) => { event.preventDefault(); applyFilters() }}>
          <TextField select label="时间范围" size="small" value={preset} sx={{ minWidth: 140 }}
            onChange={(event) => changePreset(event.target.value as TimePreset)}>
            <MenuItem value="today">今天</MenuItem><MenuItem value="yesterday">昨天</MenuItem>
            <MenuItem value="last7Days">最近七天</MenuItem><MenuItem value="custom">自定义</MenuItem>
          </TextField>
          {preset === 'custom' && <>
            <TextField label="开始时间" type="datetime-local" size="small" value={startDraft}
              onChange={(event) => setStartDraft(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} required />
            <TextField label="结束时间" type="datetime-local" size="small" value={endDraft}
              onChange={(event) => setEndDraft(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} required />
          </>}
          <TextField label="工作流" placeholder="全部工作流" size="small" value={workflowDraft}
            sx={{ minWidth: 240 }} onChange={(event) => setWorkflowDraft(event.target.value)}
            slotProps={{ htmlInput: { list: 'statistics-workflows' } }} />
          <datalist id="statistics-workflows">{definitions.data?.map((def) =>
            <option key={def.route} value={def.route}>{def.name}</option>)}</datalist>
          <Button variant="contained" type="submit">查询</Button>
        </form>
      </Paper>
      {!validRange && <Alert severity="error" className={styles.notice}>请选择有效的统计时间范围，结束时间不能早于开始时间。</Alert>}
      {query.isError && <Alert severity="error" className={styles.notice}>{query.error.message}</Alert>}
      <div className={styles.cards}>
        {[
          ['调用次数', count(metrics?.totalCount), mode === 'task' ? '每次尝试分别计数' : '每个工作流实例计一次'],
          ['未结束', count(metrics?.runningCount), mode === 'task' ? '包含排队和执行中的任务' : '运行中的工作流实例'],
          ['成功率', statisticsRate(metrics?.successRate), `成功 ${count(metrics?.successCount)} / 失败 ${count(metrics?.failureCount)}`],
          ['平均耗时', statisticsDuration(metrics?.avgDurationMs), `有效终态样本 ${count(metrics?.durationCount)}`],
        ].map(([label, value, note]) => <Paper variant="outlined" className={styles.card} key={label}>
          <div className={styles.cardLabel}>{label}</div><div className={styles.cardValue}>{value}</div><div className={styles.cardNote}>{note}</div>
        </Paper>)}
      </div>
      <Paper variant="outlined">
        <div className={styles.tableHeading}>
          <h2>{mode === 'workflow' ? '工作流调用统计' : params.asyncOnly ? '异步任务调用统计' : '任务调用统计'}</h2>
          <span className={styles.hint}>{validRange ? `${formatExecutionTime(range.startTime)} — ${formatExecutionTime(range.endTime)}` : ''}</span>
        </div>
        {query.isFetching && <LinearProgress aria-label="加载统计" />}
        <TableContainer><Table size="small" aria-label="调用统计排行" sx={{ minWidth: 1000, '& th': { whiteSpace: 'nowrap' } }}>
          <TableHead><TableRow>
            <TableCell>{mode === 'workflow' ? '工作流' : '任务节点 / 所属工作流'}</TableCell>
            {mode === 'task' && <TableCell>类型</TableCell>}
            {sortable('次数', 'totalCount')}<TableCell align="right">成功 / 失败</TableCell><TableCell align="right">未结束</TableCell>
            {sortable('成功率', 'successRate')}{sortable('平均耗时', 'avgDurationMs')}
            {sortable('最快', 'minDurationMs')}{sortable('最慢', 'maxDurationMs')}
          </TableRow></TableHead>
          <TableBody>
            {data?.rows.map((row) => <TableRow hover key={JSON.stringify([row.workflowRoute, row.nodeRef, row.taskType])}>
              <TableCell sx={{ minWidth: 220 }}>
                {mode === 'workflow'
                  ? <Button component={Link} to={workflowLink(row)} size="small" sx={{ textAlign: 'left' }}>{row.workflowName || row.workflowRoute}</Button>
                  : <Button size="small" sx={{ textAlign: 'left' }} onClick={() => setSelected({ row, params })}>{row.taskName || row.nodeRef}</Button>}
                <span className={styles.subline}>{mode === 'task' ? `${row.workflowName || row.workflowRoute} / ${row.nodeRef}` : row.workflowRoute}</span>
              </TableCell>
              {mode === 'task' && <TableCell><Chip label={row.taskType} size="small" variant="outlined" /></TableCell>}
              <TableCell align="right" className={styles.number}>{count(row.metrics.totalCount)}</TableCell>
              <TableCell align="right" className={styles.number}><span className={styles.success}>{count(row.metrics.successCount)}</span> / <span className={styles.failure}>{count(row.metrics.failureCount)}</span></TableCell>
              <TableCell align="right" className={styles.number}>{count(row.metrics.runningCount)}</TableCell>
              <TableCell align="right" className={styles.number}>{statisticsRate(row.metrics.successRate)}</TableCell>
              <TableCell align="right" className={styles.number}>{statisticsDuration(row.metrics.avgDurationMs)}</TableCell>
              <TableCell align="right" className={styles.number}>{statisticsDuration(row.metrics.minDurationMs)}</TableCell>
              <TableCell align="right" className={styles.number}>{statisticsDuration(row.metrics.maxDurationMs)}</TableCell>
            </TableRow>)}
            {!data?.rows.length && <TableRow><TableCell colSpan={mode === 'task' ? 9 : 8} align="center" sx={{ py: 6 }}>
              {query.isPending && validRange ? '正在加载统计…' : query.isError || !validRange ? '统计暂不可用' : '所选范围内暂无执行记录'}
            </TableCell></TableRow>}
          </TableBody>
        </Table></TableContainer>
        <TablePagination component="div" count={data?.total ?? 0} page={pageNum - 1} rowsPerPage={pageSize} rowsPerPageOptions={[20, 50, 100]}
          labelRowsPerPage="每页" labelDisplayedRows={({ from, to, count: total }) => `${from}–${to} / ${total}`}
          onPageChange={(_event, page) => update({ page: String(page + 1) }, false)}
          onRowsPerPageChange={(event) => update({ pageSize: event.target.value })} />
      </Paper>
      <p className={styles.hint} style={{ marginTop: 12 }}>按起始时间筛选，各版本合并汇总。耗时沿用执行详情口径，统计全部已结束且时间有效的记录；成功率以已结束记录为分母。</p>
      {selected && <TaskInstancesDialog row={selected.row} params={selected.params} onClose={() => setSelected(null)} />}
    </SectionContainer>
  </>
}

function TaskInstancesDialog({ row, params, onClose }: { row: StatisticsRow; params: StatisticsParams; onClose: () => void }) {
  const [page, setPage] = useState(1)
  const filter = { ...params, workflowRoute: row.workflowRoute, nodeRef: row.nodeRef!, taskType: row.taskType!, pageNum: page, pageSize: 20 }
  const query = useQuery({ queryKey: ['statistics-task-instances', filter], queryFn: ({ signal }) => getTaskStatisticsInstances(filter, signal), retry: false })
  return <Dialog open fullWidth maxWidth="lg" onClose={onClose} aria-labelledby="task-statistics-title">
    <DialogTitle id="task-statistics-title" sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      {row.taskName || row.nodeRef} · 执行记录<Button onClick={onClose}>关闭</Button>
    </DialogTitle>
    <DialogContent>
      <p className={styles.hint}>{row.workflowName || row.workflowRoute} / {row.nodeRef} · {row.taskType}</p>
      {query.isFetching && <LinearProgress />}
      {query.isError && <Alert severity="error">{query.error.message}</Alert>}
      <TableContainer><Table size="small" aria-label="节点执行记录" sx={{ minWidth: 850, '& th': { whiteSpace: 'nowrap' } }}><TableHead><TableRow>
        <TableCell>任务实例</TableCell><TableCell>状态</TableCell><TableCell>重试次数</TableCell><TableCell>开始时间</TableCell><TableCell>耗时</TableCell><TableCell>操作</TableCell>
      </TableRow></TableHead><TableBody>
        {query.data?.rows.map((task) => <TableRow key={task.taskId}>
          <TableCell>{task.taskId}<span className={styles.subline}>{task.referenceTaskName}</span></TableCell>
          <TableCell>{statusText(task.status)}</TableCell><TableCell>{task.retryCount}</TableCell>
          <TableCell>{formatExecutionTime(task.startTime)}</TableCell><TableCell className={styles.number}>{statisticsDuration(task.durationMs)}</TableCell>
          <TableCell><Button component={Link} to={`/executions/${encodeURIComponent(task.workflowId)}?${new URLSearchParams({ taskId: task.taskId, tab: 'tasks' })}`}>查看执行</Button></TableCell>
        </TableRow>)}
        {!query.data?.rows.length && <TableRow><TableCell colSpan={6} align="center" sx={{ py: 4 }}>{query.isPending ? '加载中…' : query.isError ? '记录暂不可用' : '暂无执行记录'}</TableCell></TableRow>}
      </TableBody></Table></TableContainer>
      <TablePagination component="div" count={query.data?.total ?? 0} page={page - 1} rowsPerPage={20} rowsPerPageOptions={[20]}
        labelRowsPerPage="每页" labelDisplayedRows={({ from, to, count: total }) => `${from}–${to} / ${total}`}
        onPageChange={(_event, next) => setPage(next + 1)} />
    </DialogContent>
  </Dialog>
}
