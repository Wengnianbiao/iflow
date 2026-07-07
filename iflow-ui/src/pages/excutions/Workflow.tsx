import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Helmet } from 'react-helmet'
import { Link, useSearchParams } from 'react-router-dom'
import { ArrowClockwise, MagnifyingGlass } from '@phosphor-icons/react'
import {
  Button,
  Empty,
  Input,
  Select,
  Space,
  Tag,
  type TableColumnsType,
} from 'antd'
import { SectionContainer } from '@/components/layout/SectionContainer'
import { SectionHeader } from '@/components/layout/SectionHeader'
import { useMessageContext } from '@/components/providers/messageContext'
import {
  DataTable,
  DEFAULT_PAGE_SIZE_OPTIONS,
} from '@/components/ui/DataTable'
import type {
  WorkflowExecutionSearchParams,
  WorkflowExecutionSummary,
} from '@/types/workflowExecution'
import { useSearchWorkflowExecutions } from '@/utils/query'
import styles from './Workflow.module.css'

const STATUS_OPTIONS = [
  { value: 'RUNNING', label: '运行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'FAILED', label: '失败' },
  { value: 'TIMED_OUT', label: '已超时' },
  { value: 'TERMINATED', label: '已终止' },
  { value: 'PAUSED', label: '已暂停' },
]

const STATUS_LABELS = Object.fromEntries(
  STATUS_OPTIONS.map(({ value, label }) => [value, label]),
)

const STATUS_COLORS: Record<string, string> = {
  RUNNING: 'processing',
  COMPLETED: 'success',
  FAILED: 'error',
  TIMED_OUT: 'error',
  TERMINATED: 'default',
  PAUSED: 'warning',
}

function buildSearchParams(
  workflowName: string,
  workflowId: string,
  status: string,
  pageNum: number,
  pageSize: number,
): WorkflowExecutionSearchParams {
  return {
    workflowName: workflowName.trim() || undefined,
    workflowId: workflowId.trim() || undefined,
    status: status ? [status] : undefined,
    pageNum,
    pageSize,
  }
}

export default function WorkflowExcutions() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { setMessage } = useMessageContext()
  const [workflowName, setWorkflowName] = useState(
    searchParams.get('workflowType') ?? searchParams.get('workflowName') ?? '',
  )
  const [workflowId, setWorkflowId] = useState(searchParams.get('workflowId') ?? '')
  const [status, setStatus] = useState(searchParams.get('status') ?? '')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE_OPTIONS[0])
  const [applied, setApplied] = useState(() =>
    buildSearchParams(
      searchParams.get('workflowType') ?? searchParams.get('workflowName') ?? '',
      searchParams.get('workflowId') ?? '',
      searchParams.get('status') ?? '',
      1,
      DEFAULT_PAGE_SIZE_OPTIONS[0],
    ),
  )

  const { data, isFetching, error, refetch } = useSearchWorkflowExecutions(applied)
  const rows = data?.rows ?? []
  const total = data?.total ?? 0

  useEffect(() => {
    if (!error) return
    setMessage({
      severity: 'error',
      text: error.message || '搜索执行记录失败',
    })
  }, [error, setMessage])

  const columns = useMemo<TableColumnsType<WorkflowExecutionSummary>>(
    () => [
      {
        title: '工作流名称',
        dataIndex: 'workflowName',
        key: 'workflowName',
        width: 190,
        render: (name: string, execution) => (
          <Link
            className={styles.link}
            to={`/executions/${encodeURIComponent(execution.workflowId)}`}
          >
            {name}
          </Link>
        ),
      },
      {
        title: '版本',
        dataIndex: 'workflowVersion',
        key: 'workflowVersion',
        width: 80,
        align: 'center',
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 110,
        render: (value: string) => (
          <Tag color={STATUS_COLORS[value] ?? 'default'}>
            {STATUS_LABELS[value] ?? value}
          </Tag>
        ),
      },
      {
        title: '开始时间',
        dataIndex: 'startTime',
        key: 'startTime',
        width: 180,
        render: (value?: number) => {
          if (!value) return '-'
          const date = new Date(value)
          if (Number.isNaN(date.getTime())) return '-'

          const pad = (part: number) => String(part).padStart(2, '0')
          return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
        },
      },
      {
        title: '耗时',
        dataIndex: 'durationMs',
        key: 'duration',
        width: 130,
        render: (durationMs: number | null | undefined, execution) => {
          const value =
            durationMs ?? ((execution.endTime ?? Date.now()) - execution.startTime)
          if (value < 1000) return `${value} 毫秒`

          const seconds = Math.floor(value / 1000)
          if (seconds < 60) return `${seconds} 秒`

          const minutes = Math.floor(seconds / 60)
          if (minutes < 60) return `${minutes} 分 ${seconds % 60} 秒`

          const hours = Math.floor(minutes / 60)
          return `${hours} 小时 ${minutes % 60} 分`
        },
      },
      {
        title: '关键字',
        dataIndex: 'correlationId',
        key: 'correlationId',
        width: 190,
        ellipsis: true,
        render: (value?: string) => (
          <span className={styles.monospace}>{value || '-'}</span>
        ),
      },
      {
        title: '工作流 ID',
        dataIndex: 'workflowId',
        key: 'workflowId',
        width: 280,
        render: (value: string) => (
          <Link
            className={`${styles.link} ${styles.monospace}`}
            to={`/executions/${encodeURIComponent(value)}`}
          >
            {value}
          </Link>
        ),
      },
    ],
    [],
  )

  const applySearch = (nextPage: number, nextPageSize: number) => {
    setPageNum(nextPage)
    setPageSize(nextPageSize)
    setApplied(buildSearchParams(workflowName, workflowId, status, nextPage, nextPageSize))
  }

  const submitSearch = (event: FormEvent) => {
    event.preventDefault()
    const nextParams = new URLSearchParams()
    if (workflowName.trim()) nextParams.set('workflowType', workflowName.trim())
    if (workflowId.trim()) nextParams.set('workflowId', workflowId.trim())
    if (status) nextParams.set('status', status)
    setSearchParams(nextParams, { replace: true })
    applySearch(1, pageSize)
  }

  return (
    <>
      <Helmet>
        <title>工作流执行记录</title>
      </Helmet>

      <SectionHeader
        title="工作流执行记录"
        actions={
          <Button
            icon={<ArrowClockwise size={17} />}
            loading={isFetching}
            onClick={() => void refetch()}
          >
            刷新
          </Button>
        }
      />

      <SectionContainer>
        <DataTable<WorkflowExecutionSummary>
          id="workflow-executions-table-wrapper"
          header={
            <>
              <form className={styles.searchPanel} onSubmit={submitSearch}>
                <Input
                  allowClear
                  aria-label="工作流名称"
                  placeholder="工作流名称"
                  value={workflowName}
                  onChange={(event) => setWorkflowName(event.target.value)}
                />
                <Input
                  allowClear
                  aria-label="工作流 ID"
                  placeholder="工作流 ID"
                  value={workflowId}
                  onChange={(event) => setWorkflowId(event.target.value)}
                />
                <Select
                  allowClear
                  aria-label="状态"
                  placeholder="全部状态"
                  value={status || undefined}
                  options={STATUS_OPTIONS}
                  onChange={(value) => setStatus(value ?? '')}
                />
                <Button
                  type="primary"
                  htmlType="submit"
                  icon={<MagnifyingGlass size={17} />}
                >
                  搜索
                </Button>
              </form>

              <div className={styles.resultsHeader}>
                <Space size={6}>
                  <strong>{rows.length} 条结果</strong>
                  <span>共 {total} 条</span>
                </Space>
              </div>
            </>
          }
          columns={columns}
          dataSource={rows}
          loading={isFetching}
          rowKey="workflowId"
          scroll={{ x: 1160, y: 'calc(100vh - 410px)' }}
          locale={{
            emptyText: <Empty description="没有匹配的执行记录" />,
          }}
          pagination={{
            current: pageNum,
            pageSize,
            total,
            pageSizeOptions: DEFAULT_PAGE_SIZE_OPTIONS,
            showSizeChanger: true,
            showTotal: (count, range) =>
              count ? `${range[0]}-${range[1]} / ${count}` : '0-0 / 0',
            placement: ['bottomEnd'],
            onChange: (nextPage, nextPageSize) => {
              const targetPage = nextPageSize === pageSize ? nextPage : 1
              applySearch(targetPage, nextPageSize)
            },
          }}
        />
      </SectionContainer>
    </>
  )
}
