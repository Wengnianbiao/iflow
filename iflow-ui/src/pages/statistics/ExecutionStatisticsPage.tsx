import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Helmet } from 'react-helmet'
import { useSearchParams } from 'react-router-dom'
import { Alert, Button, LinearProgress, MenuItem, Paper, TextField, useTheme } from '@mui/material'
import { ArrowClockwiseIcon } from '@phosphor-icons/react'
import {
  CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { getStatisticsOverview } from '@/api/executionStatistics'
import { listWorkflowDefinitions } from '@/api/workflowDefinition'
import { SectionHeader } from '@/components/layout/SectionHeader'
import { SectionContainer } from '@/components/layout/SectionContainer'
import type { StatisticsGranularity, StatisticsMetrics, StatisticsOverviewParams, StatisticsTrendPoint } from '@/types/executionStatistics'
import {
  localDateTime, statisticsDistribution, statisticsDuration, statisticsRate, statisticsSelection, type TimePreset,
} from './statisticsState'
import styles from './ExecutionStatisticsPage.module.css'

const count = (value?: number) => value == null ? '—' : value.toLocaleString('zh-CN')
const GRANULARITY_LABELS = { hour: '小时', day: '天', month: '月' }

export function ExecutionStatisticsPage() {
  const [url, setUrl] = useSearchParams()
  const { preset, range, validRange, workflowRoute } = statisticsSelection(url)
  const params: StatisticsOverviewParams = {
    ...range, workflowRoute, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  }
  const [workflowDraft, setWorkflowDraft] = useState(workflowRoute ?? '')
  const [startDraft, setStartDraft] = useState(localDateTime(range.startTime))
  const [endDraft, setEndDraft] = useState(localDateTime(range.endTime))
  const definitions = useQuery({ queryKey: ['statistics-workflow-options'], queryFn: listWorkflowDefinitions })
  const query = useQuery({
    queryKey: ['statistics-overview', params],
    queryFn: ({ signal }) => getStatisticsOverview(params, signal),
    enabled: validRange, retry: false,
  })
  const data = validRange && !query.isError ? query.data : undefined
  const runtime = data?.runtime
  const pending = validRange && query.isPending
  const unavailable = pending ? '正在加载统计…' : '统计暂不可用'

  useEffect(() => { setWorkflowDraft(workflowRoute ?? '') }, [workflowRoute])
  useEffect(() => {
    setStartDraft(localDateTime(range.startTime))
    setEndDraft(localDateTime(range.endTime))
  }, [range.startTime, range.endTime])

  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams()
    for (const key of ['range', 'startTime', 'endTime', 'workflowRoute']) {
      const value = changes[key] ?? url.get(key)
      if (value) next.set(key, value)
    }
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
  const refresh = () => {
    void query.refetch()
    void definitions.refetch()
  }

  return <>
    <Helmet><title>调度看板</title></Helmet>
    <SectionHeader title="调度看板" actions={
      <Button variant="outlined" startIcon={<ArrowClockwiseIcon size={16} />} disabled={query.isFetching || !validRange}
        onClick={refresh}>刷新</Button>
    } />
    <SectionContainer>
      <div className={styles.dashboard} aria-busy={query.isFetching}>
        <section className={styles.configuration} aria-label="配置总量">
          <MetricCard label="工作流总数" value={count(data?.workflowCount)} note="当前已配置工作流 · 按 route 计数" />
          <MetricCard label="任务节点总数" value={count(data?.taskCount)} note="各工作流最新版本 · 包含分支、循环体和控制节点" />
        </section>
        <Paper variant="outlined" className={styles.filterPanel}>
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
              className={styles.workflowFilter} onChange={(event) => setWorkflowDraft(event.target.value)}
              slotProps={{ htmlInput: { list: 'statistics-workflows' } }} />
            <datalist id="statistics-workflows">{definitions.data?.map((def) =>
              <option key={def.route} value={def.route}>{def.name}</option>)}</datalist>
            <Button variant="contained" type="submit">查询</Button>
          </form>
          <div className={styles.filterNote}>筛选条件作用于运行统计；配置总量保持全局口径。</div>
          <div className={styles.progress}>{query.isFetching && <LinearProgress aria-label="加载统计" />}</div>
        </Paper>
        {!validRange && <Alert severity="error">请选择有效的统计时间范围，结束时间不能早于开始时间。</Alert>}
        {query.isError && validRange && <Alert severity="error">{query.error.message}</Alert>}
        {definitions.isError && <Alert severity="warning">工作流选项加载失败，可直接输入工作流 route 查询。</Alert>}
        <div className={styles.pair}>
          <RuntimeMetrics title="工作流运行" metrics={runtime?.workflows} task={false} />
          <RuntimeMetrics title="任务运行" metrics={runtime?.tasks} task />
        </div>
        <Paper variant="outlined" className={styles.chartPanel}>
          <div className={styles.panelHeading}>
            <h2>执行次数趋势</h2>
            <span className={styles.hint}>{runtime ? `按${GRANULARITY_LABELS[runtime.granularity]}汇总` : unavailable}</span>
          </div>
          {runtime ? <ExecutionTrend points={runtime.trend} granularity={runtime.granularity} />
            : <div className={styles.chartPlaceholder}>{unavailable}</div>}
        </Paper>
        <div className={styles.pair}>
          <StatusDistribution title="工作流状态分布" metrics={runtime?.workflows} unavailable={unavailable} />
          <StatusDistribution title="任务状态分布" metrics={runtime?.tasks} unavailable={unavailable} />
        </div>
        <p className={styles.hint}>按起始时间筛选，各版本合并统计。平均耗时包含任务执行与等待，仅统计时间有效的已结束实例；成功率以已结束实例为分母。未结束为所选范围内开始且尚未结束的实例。</p>
      </div>
    </SectionContainer>
  </>
}

function MetricCard({ label, value, note }: { label: string; value: string; note: string }) {
  return <Paper variant="outlined" className={styles.configurationCard}>
    <div className={styles.cardLabel}>{label}</div>
    <div className={styles.configurationValue}>{value}</div>
    <div className={styles.cardNote}>{note}</div>
  </Paper>
}

function RuntimeMetrics({ title, metrics, task }: { title: string; metrics?: StatisticsMetrics; task: boolean }) {
  const cards = [
    ['执行次数', count(metrics?.totalCount), task ? '每次重试、循环尝试分别计数' : '每个工作流实例计一次'],
    ['未结束', count(metrics?.runningCount), '所选范围内开始且尚未结束'],
    ['成功率', statisticsRate(metrics?.successRate), `成功 ${count(metrics?.successCount)} / 失败 ${count(metrics?.failureCount)}`],
    ['平均耗时', statisticsDuration(metrics?.avgDurationMs), `有效终态样本 ${count(metrics?.durationCount)}`],
  ]
  return <Paper component="section" variant="outlined" className={styles.runtimePanel} aria-label={title}>
    <div className={styles.panelHeading}><h2>{title}</h2></div>
    <div className={styles.metrics}>
      {cards.map(([label, value, note]) => <div className={styles.metric} key={label}>
        <div className={styles.cardLabel}>{label}</div><div className={styles.cardValue}>{value}</div>
        <div className={styles.cardNote}>{note}</div>
      </div>)}
    </div>
  </Paper>
}

function ExecutionTrend({ points, granularity }: { points: StatisticsTrendPoint[]; granularity: StatisticsGranularity }) {
  const theme = useTheme()
  const label = (value: number) => new Date(value).toLocaleString('zh-CN', granularity === 'hour'
    ? { hour: '2-digit', minute: '2-digit' }
    : granularity === 'day' ? { month: 'numeric', day: 'numeric' } : { year: 'numeric', month: 'numeric' })
  return <div className={styles.trend} role="img" aria-label="工作流与任务执行次数趋势">
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={points} margin={{ left: 0, right: 20, top: 16, bottom: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} vertical={false} />
        <XAxis dataKey="startTime" tickFormatter={label} stroke={theme.palette.text.secondary}
          tick={{ fontSize: 12 }} minTickGap={40} />
        <YAxis allowDecimals={false} stroke={theme.palette.text.secondary} tick={{ fontSize: 12 }} width={48} />
        <Tooltip labelFormatter={(value) => new Date(Number(value)).toLocaleString('zh-CN', { timeZoneName: 'short' })}
          formatter={(value) => count(Number(value))}
          contentStyle={{ backgroundColor: theme.palette.background.paper, borderColor: theme.palette.divider }} />
        <Legend />
        <Line type="monotone" dataKey="workflowCount" name="工作流执行次数" stroke={theme.palette.primary.main}
          strokeWidth={2} dot={points.length === 1} activeDot={{ r: 4 }} isAnimationActive={false} />
        <Line type="monotone" dataKey="taskCount" name="任务执行次数" stroke={theme.palette.success.main}
          strokeWidth={2} dot={points.length === 1} activeDot={{ r: 4 }} isAnimationActive={false} />
      </LineChart>
    </ResponsiveContainer>
  </div>
}

function StatusDistribution({ title, metrics, unavailable }: { title: string; metrics?: StatisticsMetrics; unavailable: string }) {
  const theme = useTheme()
  const colors = [theme.palette.success.main, theme.palette.error.main, theme.palette.primary.main]
  const segments = metrics ? statisticsDistribution(metrics) : []
  return <Paper component="section" variant="outlined" className={styles.chartPanel} aria-label={title}>
    <div className={styles.panelHeading}><h2>{title}</h2></div>
    {metrics && metrics.totalCount > 0 ? <div className={styles.distribution}>
      <div className={styles.donut} role="img" aria-label={`${title}：${segments.map((item) => `${item.name} ${count(item.value)}`).join('，')}`}>
        <ResponsiveContainer width="100%" height={220}>
          <PieChart>
            <Pie data={segments} dataKey="value" nameKey="name" innerRadius={62} outerRadius={84}
              paddingAngle={2} stroke="none" isAnimationActive={false}>
              {segments.map((item, index) => <Cell key={item.name} fill={colors[index]} />)}
            </Pie>
            <text x="50%" y="47%" textAnchor="middle" dominantBaseline="middle" fill={theme.palette.text.primary} fontSize={24} fontWeight={600}>{count(metrics.totalCount)}</text>
            <text x="50%" y="59%" textAnchor="middle" fill={theme.palette.text.secondary} fontSize={12}>执行次数</text>
            <Tooltip formatter={(value) => count(Number(value))}
              contentStyle={{ backgroundColor: theme.palette.background.paper, borderColor: theme.palette.divider }} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className={styles.distributionLegend}>
        {segments.map((item, index) => <li key={item.name}>
          <span className={styles.legendName}><i style={{ backgroundColor: colors[index] }} />{item.name}</span>
          <strong>{count(item.value)}</strong><span>{statisticsRate(item.value / metrics.totalCount)}</span>
        </li>)}
      </ul>
    </div> : <div className={styles.statusPlaceholder}>{metrics ? '暂无执行数据' : unavailable}</div>}
  </Paper>
}
