import { useEffect, useMemo, useState } from 'react'
import { Alert, Box, Button, IconButton, Tooltip, Typography } from '@mui/material'
import { ArrowClockwiseIcon, ArrowCounterClockwiseIcon, LinkIcon } from '@phosphor-icons/react'
import { DataTable, DEFAULT_PAGE_SIZE_OPTIONS, type DataTableColumn } from '@/components/ui/DataTable'
import type { WorkflowExecutionSummary } from '@/types/workflowExecution'
import { useSearchWorkflowExecutions } from '@/utils/query'
import { formatExecutionTime } from '@/pages/executions/formatters'

const HISTORY_PAGE_SIZE = DEFAULT_PAGE_SIZE_OPTIONS[0]

function executionHref(workflowId: string) {
  return `${window.location.pathname}${window.location.search}#/executions/${encodeURIComponent(workflowId)}`
}

type InvokeWorkflowHistoryProps = {
  workflowName: string | null
  reloadKey: number
  restoringId: string | null
  onRestore: (execution: WorkflowExecutionSummary) => void
}

export function InvokeWorkflowHistory({
  workflowName,
  reloadKey,
  restoringId,
  onRestore,
}: InvokeWorkflowHistoryProps) {
  const [pageState, setPageState] = useState({ name: workflowName, page: 1, pageSize: HISTORY_PAGE_SIZE })
  const page = pageState.name === workflowName ? pageState.page : 1
  const pageSize = pageState.name === workflowName ? pageState.pageSize : HISTORY_PAGE_SIZE
  const params = useMemo(() => ({
    workflowName: workflowName ?? undefined,
    // 搜索接口不传开始时间时只查近 7 天，这里要列出该工作流的全部执行记录。
    startTime: 0,
    pageNum: page,
    pageSize,
  }), [page, pageSize, workflowName])
  const history = useSearchWorkflowExecutions(params, { enabled: Boolean(workflowName) })
  const { refetch } = history

  useEffect(() => {
    if (!workflowName || reloadKey === 0) return
    void refetch()
  }, [refetch, reloadKey, workflowName])

  const rows = history.data?.rows ?? []
  const total = history.data?.total ?? 0
  const visiblePage = Math.min(page, Math.max(1, Math.ceil(total / pageSize) || 1))

  const columns = useMemo<DataTableColumn<WorkflowExecutionSummary>[]>(() => [
    {
      title: '执行名称',
      key: 'name',
      dataIndex: 'displayName',
      ellipsis: true,
      render: (_value, execution) => (
        <a
          href={executionHref(execution.workflowId)}
          target="_blank"
          rel="noreferrer"
          style={{ fontWeight: 600, color: 'inherit' }}
        >
          {execution.displayName || execution.workflowName}
        </a>
      ),
    },
    {
      title: '链接',
      key: 'link',
      width: 72,
      align: 'center',
      render: (_value, execution) => (
        <a href={executionHref(execution.workflowId)} target="_blank" rel="noreferrer" aria-label="打开执行详情" style={{ color: 'inherit' }}>
          <LinkIcon size={20} />
        </a>
      ),
    },
    {
      title: '执行时间',
      key: 'startTime',
      dataIndex: 'startTime',
      width: 168,
      render: (_value, execution) => formatExecutionTime(execution.startTime),
    },
    {
      title: '恢复入参',
      key: 'restore',
      width: 96,
      align: 'right',
      render: (_value, execution) => (
        <Tooltip title="把这次执行的入参填回左侧">
          <span>
            <IconButton
              size="small"
              aria-label={`恢复 ${execution.displayName || execution.workflowName} 的入参`}
              disabled={restoringId === execution.workflowId}
              onClick={() => onRestore(execution)}
            >
              <ArrowCounterClockwiseIcon size={20} />
            </IconButton>
          </span>
        </Tooltip>
      ),
    },
  ], [onRestore, restoringId])

  return (
    <DataTable<WorkflowExecutionSummary>
      id="invoke-workflow-history"
      toolbar={(
        <Typography fontSize={14} fontWeight={600}>执行历史</Typography>
      )}
      customActions={(
        <Button
          size="small"
          startIcon={<ArrowClockwiseIcon size={18} />}
          disabled={!workflowName || history.isFetching}
          onClick={() => void history.refetch()}
        >
          刷新
        </Button>
      )}
      header={!workflowName || !history.error ? undefined : (
        <Alert
          severity="error"
          action={<Button color="inherit" disabled={history.isFetching} onClick={() => void history.refetch()}>重试</Button>}
        >
          {history.error.message || '加载执行历史失败'}
        </Alert>
      )}
      columns={columns}
      dataSource={workflowName ? rows : []}
      loading={Boolean(workflowName) && history.isFetching}
      rowKey="workflowId"
      scroll={{ x: 560, y: 'max(320px, calc(100vh - 280px))' }}
      locale={{
        emptyText: (
          <Box sx={{ py: 2 }}>
            <Typography color="text.secondary">
              {workflowName ? '暂无执行记录' : '选择工作流后显示执行历史'}
            </Typography>
          </Box>
        ),
      }}
      pagination={workflowName && history.data ? {
        current: visiblePage,
        pageSize,
        total,
        pageSizeOptions: DEFAULT_PAGE_SIZE_OPTIONS,
        showSizeChanger: true,
        showTotal: (count, range) => count ? `${range[0]}-${range[1]} / ${count}` : '0-0 / 0',
        onChange: (nextPage, nextPageSize) => setPageState({ name: workflowName, page: nextPage, pageSize: nextPageSize }),
      } : false}
    />
  )
}
