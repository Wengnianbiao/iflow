import { useEffect, useMemo, useState } from 'react'
import { Helmet } from 'react-helmet'
import {
  Autocomplete,
  Button,
  CircularProgress,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import {
  CopyIcon,
  TrashIcon,
  ArrowClockwiseIcon,
  TagIcon,
} from '@phosphor-icons/react'
import type { UseQueryResult } from '@tanstack/react-query'
import { SectionContainer } from '@/components/layout/SectionContainer'
import { SectionHeader } from '@/components/layout/SectionHeader'
import { useMessageContext } from '@/components/providers/messageContext'
import {
  DataTable,
  DEFAULT_PAGE_SIZE_OPTIONS,
  type DataTableColumn,
} from '@/components/ui/DataTable'
import { NavLink } from '@/components/ui/NavLink'
import { WorkflowTagChip } from '@/components/ui/WorkflowTagsInput'
import type { WorkflowDef } from '@/types/workflow'
import { useCustomPagination } from '@/utils/hooks/useCustomPagination'
import { useWorkflowDefs } from '@/utils/query'
import { collectWorkflowTags, filterWorkflowDefinitions, normalizeWorkflowTags } from '@/utils/workflowTags'
import { CloneWorkflowDialog } from './dialog/CloneWorkflowDialog'
import { DeleteWorkflowDialog } from './dialog/DeleteWorkflowDialog'
import { EditWorkflowTagsDialog } from './dialog/EditWorkflowTagsDialog'
import { WorkflowListActions } from './WorkflowListActions'
import styles from './Workflow.module.css'

export default function WorkflowDefinitions() {
  const { setMessage } = useMessageContext()
  const { data, isFetching, error, refetch }: UseQueryResult<WorkflowDef[]> =
    useWorkflowDefs()
  const [
    { pageParam, searchParam, tagsParam },
    { handlePageChange, setSearchParam, setTagsParam },
  ] = useCustomPagination()
  const currentPage = pageParam ? Number(pageParam) || 1 : 1

  /** 监听用户选择的工作流和操作 */
  const [selectedWorkflowWithAction, setSelectedWorkflowWithAction] = useState<{
    selectedWorkflow: WorkflowDef | null
    action: string
  }>({
    selectedWorkflow: null,
    action: '',
  })

  useEffect(() => {
    if (!error) return
    setMessage({
      severity: 'error',
      text: error.message || '加载工作流列表失败',
    })
  }, [error, setMessage])

  const availableTags = useMemo(() => collectWorkflowTags(data ?? []), [data])
  const filteredDefinitions = filterWorkflowDefinitions(data ?? [], searchParam, tagsParam)

  const columns = useMemo<DataTableColumn<WorkflowDef>[]>(
    () => [
      {
        title: '工作流名称',
        dataIndex: 'name',
        key: 'workflowRoute',
        width: 200,
        render: (_value, definition) => (
          <NavLink
            className={styles.workflowLink}
            path={`/workflowDef/${encodeURIComponent(definition.route)}/${definition.version}`}
            id={`${definition.route}-link-btn`}
          >
            {definition.name}
          </NavLink>
        ),
      },
      {
        title: '路由',
        dataIndex: 'route',
        key: 'route',
        width: 210,
        render: (route) => <code className={styles.route}>{String(route)}</code>,
      },
      {
        title: '描述',
        dataIndex: 'description',
        key: 'description',
        render: (description) => (
          <span className={styles.description}>
            {typeof description === 'string' && description ? description : '-'}
          </span>
        ),
      },
      {
        title: '标签',
        dataIndex: 'tags',
        key: 'tags',
        width: 200,
        render: (_value, definition) => (
          <div className={styles.tagList}>
            {normalizeWorkflowTags(definition.tags).map((tag) => (
              <WorkflowTagChip key={tag} label={tag} title={tag} />
            ))}
            {!definition.tags?.length ? '-' : null}
          </div>
        ),
      },
      {
        title: '创建时间',
        dataIndex: 'createTime',
        key: 'createTime',
        width: 185,
        render: (value) => {
          if (value == null || value === '') return '-'
          const date = new Date(value as number | string)
          if (Number.isNaN(date.getTime())) return '-'

          const pad = (part: number) => String(part).padStart(2, '0')
          return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
        },
      },
      {
        title: '最新版本',
        dataIndex: 'version',
        key: 'version',
        width: 105,
        align: 'center',
        render: (version) => typeof version === 'number' ? version : '-',
      },
      {
        title: '操作',
        key: 'actions',
        width: 136,
        fixed: 'right',
        render: (_value, definition: WorkflowDef) => (
          <Stack direction="row" spacing={0.25}>
            <Tooltip title="编辑标签">
              <IconButton
                size="small"
                aria-label={`编辑 ${definition.route} 的标签`}
                onClick={() => setSelectedWorkflowWithAction({
                  selectedWorkflow: definition,
                  action: 'tags',
                })}
              >
                <TagIcon size={18} />
              </IconButton>
            </Tooltip>
            <Tooltip title="复制工作流定义">
              <IconButton
                size="small"
                aria-label={`复制 ${definition.route}`}
                onClick={() =>
                  setSelectedWorkflowWithAction({
                    selectedWorkflow: definition,
                    action: 'clone',
                  })
                }
              >
                <CopyIcon size={18} />
              </IconButton>
            </Tooltip>
            <Tooltip title="删除工作流">
              <IconButton
                size="small"
                color="error"
                aria-label={`删除 ${definition.route}`}
                onClick={() =>
                  setSelectedWorkflowWithAction({
                    selectedWorkflow: definition,
                    action: 'delete',
                  })
                }
              >
                <TrashIcon size={18} />
              </IconButton>
            </Tooltip>
          </Stack>
        ),
      },
    ],
    [],
  )

  return (
    <>
      <Helmet>
        <title>工作流配置</title>
      </Helmet>

      {selectedWorkflowWithAction.selectedWorkflow && selectedWorkflowWithAction.action === 'tags' ? (
        <EditWorkflowTagsDialog
          key={`${selectedWorkflowWithAction.selectedWorkflow.route}-${selectedWorkflowWithAction.selectedWorkflow.version}`}
          definition={selectedWorkflowWithAction.selectedWorkflow}
          options={availableTags}
          onClose={() => setSelectedWorkflowWithAction({ selectedWorkflow: null, action: '' })}
          onSuccess={() => {
            setSelectedWorkflowWithAction({ selectedWorkflow: null, action: '' })
            handlePageChange(1)
            void refetch()
            setMessage({ severity: 'success', text: '工作流标签已保存' })
          }}
        />
      ) : null}

      {selectedWorkflowWithAction &&
        selectedWorkflowWithAction?.selectedWorkflow &&
        selectedWorkflowWithAction?.action === 'clone' && (
          <CloneWorkflowDialog
            onClose={() =>
              setSelectedWorkflowWithAction({
                selectedWorkflow: null,
                action: '',
              })
            }
            onSuccess={() => {
              setSelectedWorkflowWithAction({
                selectedWorkflow: null,
                action: '',
              })
              void refetch()
              setMessage({
                severity: 'success',
                text: '工作流复制成功',
              })
            }}
            selectedWorkflow={selectedWorkflowWithAction?.selectedWorkflow}
            workflowList={data ?? []}
          />
        )}
      {selectedWorkflowWithAction.selectedWorkflow &&
        selectedWorkflowWithAction.action === 'delete' && (
          <DeleteWorkflowDialog
            definition={selectedWorkflowWithAction.selectedWorkflow}
            onClose={() =>
              setSelectedWorkflowWithAction({
                selectedWorkflow: null,
                action: '',
              })
            }
            onSuccess={() => {
              setSelectedWorkflowWithAction({
                selectedWorkflow: null,
                action: '',
              })
              void refetch()
              setMessage({
                severity: 'success',
                text: '工作流删除成功',
              })
            }}
          />
        )}

      <SectionHeader
        title="工作流配置"
        actions={<WorkflowListActions />}
      />

      <SectionContainer>
        <DataTable<WorkflowDef>
          id="workflow-definitions-table-wrapper"
          quickSearchEnabled
          quickSearchPlaceholder="搜索工作流配置"
          searchTerm={searchParam}
          onSearchTermChange={setSearchParam}
          quickSearchFilters={
            <Autocomplete
              multiple
              openOnFocus
              forcePopupIcon={false}
              disableCloseOnSelect
              options={availableTags}
              value={tagsParam}
              onChange={(_event, tags) => setTagsParam(tags)}
              noOptionsText="暂无标签"
              className={styles.tagFilter}
              renderOption={(props, tag) => {
                const { key, ...rest } = props
                return <li key={key} {...rest}><WorkflowTagChip label={tag} sx={{ cursor: 'pointer' }} /></li>
              }}
              renderTags={(tags, getTagProps) => tags.map((tag, index) => {
                const { key, ...props } = getTagProps({ index })
                return <WorkflowTagChip key={key} label={tag} {...props} />
              })}
              renderInput={(params) => (
                <TextField
                  {...params}
                  size="small"
                  label="标签筛选"
                  placeholder={tagsParam.length ? undefined : '全部标签'}
                  slotProps={{ inputLabel: { shrink: true } }}
                />
              )}
            />
          }
          toolbar={
            <strong className={styles.resultCount}>
              {filteredDefinitions.length} 条结果
            </strong>
          }
          customActions={
            <Button
              variant="text"
              startIcon={isFetching
                ? <CircularProgress color="inherit" size={16} />
                : <ArrowClockwiseIcon size={17} />}
              disabled={isFetching}
              onClick={() => void refetch()}
            >
              刷新
            </Button>
          }
          columns={columns}
          dataSource={filteredDefinitions}
          loading={isFetching}
          rowKey={(definition) => `${definition.route}-${definition.version}`}
          scroll={{ x: 1200 }}
          locale={{
            emptyText: <Typography color="text.secondary">没有匹配的工作流配置</Typography>,
          }}
          pagination={{
            current: currentPage,
            defaultPageSize: DEFAULT_PAGE_SIZE_OPTIONS[0],
            pageSizeOptions: DEFAULT_PAGE_SIZE_OPTIONS,
            showSizeChanger: true,
            showTotal: (total, range) =>
              total ? `${range[0]}-${range[1]} / ${total}` : '0-0 / 0',
            onChange: (page) => handlePageChange(page),
          }}
        />
      </SectionContainer>
    </>
  )
}
