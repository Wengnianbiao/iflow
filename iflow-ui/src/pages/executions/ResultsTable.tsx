import { Alert, Box, Button, Link, Typography } from '@mui/material'
import { ArrowClockwiseIcon } from '@phosphor-icons/react'
import { Link as RouterLink } from 'react-router-dom'
import { DataTable, DEFAULT_PAGE_SIZE_OPTIONS, type DataTableColumn } from '@/components/ui/DataTable'
import type { PageResult, WorkflowExecutionSummary } from '@/types/workflowExecution'
import { formatExecutionDuration, formatExecutionTime } from './formatters'
import executionsStyles from './executionsStyles'
import WorkflowStatusBadge from './WorkflowStatusBadge'

// Keep iFlow's display name, keyword and column order while matching Conductor's ResultsTable boundary.
const executionFields: DataTableColumn<WorkflowExecutionSummary>[] = [
  {
    title: '工作流名称', key: 'workflowName', dataIndex: 'workflowName', width: 150,
    render: (_value, execution) => (
      <Link component={RouterLink} to={`/executions/${encodeURIComponent(execution.workflowId)}`} sx={executionsStyles.link} underline="hover">
        {execution.workflowName || '-'}
      </Link>
    ),
  },
  {
    title: '关键字', key: 'correlationId', dataIndex: 'correlationId', width: 150, ellipsis: true,
    render: (_value, execution) => (
      <Box component="span" sx={executionsStyles.monospace}>{execution.correlationId || '-'}</Box>
    ),
  },
  {
    title: '状态', key: 'status', dataIndex: 'status', width: 110,
    render: (_value, execution) => (
      <WorkflowStatusBadge status={execution.status} />
    ),
  },
  {
    title: '开始时间', key: 'startTime', dataIndex: 'startTime', width: 150,
    render: (_value, execution) => formatExecutionTime(execution.startTime),
  },
  {
    title: '耗时', key: 'duration', dataIndex: 'durationMs', width: 130,
    render: (_value, execution) => formatExecutionDuration(execution),
  },
  { title: '版本', key: 'workflowVersion', dataIndex: 'workflowVersion', width: 80, align: 'center' },
]

type ResultsTableProps = {
  resultObj?: PageResult<WorkflowExecutionSummary>
  error: Error | null
  busy: boolean
  page: number
  rowsPerPage: number
  onPageChange: (page: number, rowsPerPage: number) => void
  refetchExecution: () => void
  filterOn: boolean
  handleReset: () => void
}

/** Presentation only: query, URL and form state live in BasicSearch's controller. */
export default function ResultsTable({
  resultObj, error, busy, page, rowsPerPage, onPageChange,
  refetchExecution, filterOn, handleReset,
}: ResultsTableProps) {
  const rows = resultObj?.rows ?? []
  const total = resultObj?.total ?? 0
  const visiblePage = Math.min(page, Math.max(1, Math.ceil(total / rowsPerPage)))

  return (
    <DataTable<WorkflowExecutionSummary>
      id="workflow-executions-table-wrapper"
      header={error ? (
        <Alert severity="error" action={<Button color="inherit" disabled={busy} onClick={refetchExecution}>重试</Button>}>
          {error.message || '搜索执行记录失败'}
        </Alert>
      ) : undefined}
      toolbar={
        <Box sx={executionsStyles.resultCount} aria-live="polite">
          <Typography fontSize={14}>{busy && !resultObj ? '正在加载…' : `${rows.length} 条结果`}</Typography>
          {resultObj ? <Typography fontSize={12} color="text.secondary">共 {total} 条</Typography> : null}
        </Box>
      }
      customActions={
        <Button startIcon={<ArrowClockwiseIcon size={17} />} disabled={busy} onClick={refetchExecution}>
          刷新
        </Button>
      }
      columns={executionFields}
      dataSource={rows}
      loading={busy}
      rowKey="workflowId"
      scroll={{ x: 880, y: 'max(240px, calc(100vh - 450px))' }}
      locale={{
        emptyText: error ? <></> : (
          <Box sx={executionsStyles.empty}>
            <Typography color="text.secondary">{filterOn ? '没有匹配的执行记录，请调整筛选条件。' : '暂无工作流执行记录'}</Typography>
            {filterOn
              ? <Button onClick={handleReset}>清除筛选</Button>
              : <Button component={RouterLink} to="/workflowDef">查看工作流配置</Button>}
          </Box>
        ),
      }}
      pagination={resultObj ? {
        current: visiblePage,
        pageSize: rowsPerPage,
        total,
        pageSizeOptions: DEFAULT_PAGE_SIZE_OPTIONS,
        showSizeChanger: true,
        showTotal: (count, range) => count ? `${range[0]}-${range[1]} / ${count}` : '0-0 / 0',
        onChange: onPageChange,
      } : false}
    />
  )
}
