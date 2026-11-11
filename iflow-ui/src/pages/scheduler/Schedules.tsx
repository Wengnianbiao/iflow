import { useState } from 'react'
import { Helmet } from 'react-helmet'
import { Link as RouterLink } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControlLabel, IconButton, Link, Radio, RadioGroup, Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import { ArrowClockwiseIcon, PauseCircleIcon, PlayCircleIcon, PlusCircleIcon, TrashIcon } from '@phosphor-icons/react'
import { deleteWorkflowSchedule, listWorkflowSchedules, saveWorkflowSchedule, type WorkflowSchedule } from '@/api/workflowSchedule'
import { SectionContainer } from '@/components/layout/SectionContainer'
import { SectionHeader } from '@/components/layout/SectionHeader'
import { useMessageContext } from '@/components/providers/messageContext'
import { DataTable, DEFAULT_PAGE_SIZE_OPTIONS, type DataTableColumn } from '@/components/ui/DataTable'

const SCHEDULES_QUERY = ['workflowSchedules'] as const

function formatNextRun(schedule: WorkflowSchedule) {
  if (!schedule.nextRunAt) return '—'
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      timeZone: schedule.zoneId,
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
    }).format(new Date(schedule.nextRunAt))
  } catch {
    return schedule.nextRunAt
  }
}

type ScheduleAction = { kind: 'toggle' | 'delete'; schedule: WorkflowSchedule }

export function Schedules() {
  const queryClient = useQueryClient()
  const { setMessage } = useMessageContext()
  const { data, isFetching, error, refetch } = useQuery({
    queryKey: SCHEDULES_QUERY,
    queryFn: ({ signal }) => listWorkflowSchedules(signal),
    retry: false,
  })
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('all')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE_OPTIONS[0])
  const [toDelete, setToDelete] = useState<WorkflowSchedule | null>(null)
  const [confirmation, setConfirmation] = useState('')
  const action = useMutation({
    mutationFn: async ({ kind, schedule }: ScheduleAction) => {
      if (kind === 'delete') {
        await deleteWorkflowSchedule(schedule.name)
      } else {
        await saveWorkflowSchedule({ ...schedule, enabled: !schedule.enabled }, schedule.name)
      }
    },
    onSuccess: async (_, { kind, schedule }) => {
      if (kind === 'delete') setToDelete(null)
      const text = kind === 'delete' ? '定时计划已删除' : (schedule.enabled ? '定时计划已停用' : '定时计划已启用')
      setMessage({ severity: 'success', text })
      await queryClient.invalidateQueries({ queryKey: SCHEDULES_QUERY })
    },
    onError: (reason) => setMessage({ severity: 'error', text: reason.message }),
  })

  const query = search.trim().toLowerCase()
  const rows = (data ?? []).filter((schedule) => (
    (status === 'all' || schedule.enabled === (status === 'enabled'))
    && [schedule.displayName, schedule.name, schedule.startWorkflowRequest.route, schedule.cronExpression]
      .some((value) => value.toLowerCase().includes(query))
  ))
  const currentPage = Math.min(page, Math.max(1, Math.ceil(rows.length / pageSize)))

  const columns: DataTableColumn<WorkflowSchedule>[] = [
    {
      title: '计划名称', key: 'name', width: 220,
      render: (_, schedule) => (
        <Link component={RouterLink} to={`/scheduleDef/${schedule.name}`} underline="hover"
          sx={{ display: 'flex', flexDirection: 'column' }}>
          <Typography component="span" variant="body2" sx={{ overflowWrap: 'anywhere' }}>{schedule.name}</Typography>
          <Typography component="span" variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {schedule.displayName}
          </Typography>
        </Link>
      ),
    },
    {
      title: 'Cron 表达式', key: 'cronExpression', width: 150,
      render: (_, schedule) => <code>{schedule.cronExpression}</code>,
    },
    {
      title: '工作流', key: 'workflow', width: 175,
      render: (_, { startWorkflowRequest: request }) => (
        <>
          <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{request.route}</Typography>
          <Typography variant="caption" color="text.secondary">
            {request.version == null ? '最新版本' : `版本 ${request.version}`}
          </Typography>
        </>
      ),
    },
    {
      title: '下次触发', key: 'nextRunAt', width: 175,
      render: (_, schedule) => (
        <>
          <Typography variant="body2">{formatNextRun(schedule)}</Typography>
          <Typography variant="caption" color="text.secondary">{schedule.zoneId}</Typography>
        </>
      ),
    },
    {
      title: '状态', key: 'status', width: 85,
      render: (_, schedule) => (
        <Chip size="small" color={schedule.enabled ? 'success' : 'default'}
          label={schedule.enabled ? '已启用' : '已停用'} />
      ),
    },
    {
      title: '操作', key: 'actions', width: 95, fixed: 'right',
      render: (_, schedule) => (
        <Stack direction="row" spacing={0.25}>
          <Tooltip title={schedule.enabled ? '停用' : '启用'}>
            <span>
              <IconButton size="small" aria-label={`${schedule.enabled ? '停用' : '启用'} ${schedule.name}`}
                disabled={action.isPending} onClick={() => action.mutate({ kind: 'toggle', schedule })}>
                {schedule.enabled ? <PauseCircleIcon size={19} /> : <PlayCircleIcon size={19} />}
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title="删除">
            <span>
              <IconButton size="small" color="error" aria-label={`删除 ${schedule.name}`}
                disabled={action.isPending} onClick={() => { setConfirmation(''); setToDelete(schedule) }}>
                <TrashIcon size={18} />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
      ),
    },
  ]

  const refresh = (
    <Button size="small" startIcon={<ArrowClockwiseIcon size={17} />} disabled={isFetching}
      onClick={() => void refetch()}>刷新</Button>
  )

  return (
    <>
      <Helmet><title>定时计划</title></Helmet>
      <SectionHeader title="定时计划" actions={
        <Button component={RouterLink} to="/scheduleDef/new" variant="contained"
          disabled={data == null} startIcon={<PlusCircleIcon size={19} />}>新建定时计划</Button>
      } />
      <SectionContainer>
        {error && <Alert severity="error" action={refresh} sx={{ mb: data ? 2 : 0 }}>{error.message}</Alert>}
        {(!error || data) && (
          <DataTable
            columns={columns} dataSource={rows} loading={isFetching} rowKey="name" scroll={{ x: 900 }}
            quickSearchEnabled quickSearchPlaceholder="搜索计划名称、展示名称、工作流或 Cron"
            searchTerm={search}
            onSearchTermChange={(value) => { setSearch(value); setPage(1) }}
            quickSearchFilters={
              <RadioGroup row aria-label="启用状态" value={status}
                onChange={(event) => { setStatus(event.target.value); setPage(1) }}>
                <FormControlLabel value="all" control={<Radio size="small" />} label="全部" />
                <FormControlLabel value="enabled" control={<Radio size="small" />} label="已启用" />
                <FormControlLabel value="disabled" control={<Radio size="small" />} label="已停用" />
              </RadioGroup>
            }
            toolbar={<strong>{rows.length} 条结果</strong>}
            customActions={refresh}
            pagination={{
              current: currentPage, pageSize, showSizeChanger: true,
              onChange: (nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize) },
            }}
            locale={{ emptyText: data?.length ? '没有匹配的定时计划' : '暂无定时计划' }}
          />
        )}
      </SectionContainer>
      {toDelete && (
        <Dialog open fullWidth maxWidth="sm" onClose={action.isPending ? undefined : () => setToDelete(null)}>
          <DialogTitle>删除确认</DialogTitle>
          <DialogContent>
            <Typography sx={{ mb: 2 }}>
              删除定时计划 <strong>{toDelete.name}</strong>（{toDelete.displayName}，{toDelete.startWorkflowRequest.route}）。
              请输入计划名称以确认。
            </Typography>
            <TextField autoFocus fullWidth size="small" label="计划名称" value={confirmation}
              disabled={action.isPending} onChange={(event) => setConfirmation(event.target.value)} />
          </DialogContent>
          <DialogActions>
            <Button disabled={action.isPending} onClick={() => setToDelete(null)}>取消</Button>
            <Button variant="contained" color="error" disabled={action.isPending || confirmation !== toDelete.name}
              onClick={() => action.mutate({ kind: 'delete', schedule: toDelete })}>确认删除</Button>
          </DialogActions>
        </Dialog>
      )}
    </>
  )
}
