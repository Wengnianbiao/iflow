import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Helmet } from 'react-helmet'
import {
  Button,
  Empty,
  Space,
  Tooltip,
  type TableColumnsType,
} from 'antd'
import {
  CopyIcon,
  PencilSimpleIcon,
  PlayIcon,
  PlusIcon,
  TrashIcon,
  ArrowClockwiseIcon,
} from '@phosphor-icons/react'
import type { UseQueryResult } from '@tanstack/react-query'
import { SectionContainer } from '@/components/layout/SectionContainer'
import { SectionHeader } from '@/components/layout/SectionHeader'
import { useMessageContext } from '@/components/providers/messageContext'
import {
  DataTable,
  DEFAULT_PAGE_SIZE_OPTIONS,
} from '@/components/ui/DataTable'
import { NavLink } from '@/components/ui/NavLink'
import type { WorkflowDef } from '@/types/workflow'
import { useCustomPagination } from '@/utils/hooks/useCustomPagination'
import { useWorkflowDefs } from '@/utils/query'
import { CloneWorkflowDialog } from './dialog/CloneWorkflowDialog'
import { DeleteWorkflowDialog } from './dialog/DeleteWorkflowDialog'
import styles from './Workflow.module.css'

export default function WorkflowDefinitions() {
  const navigate = useNavigate()
  const { setMessage } = useMessageContext()
  const { data, isFetching, error, refetch }: UseQueryResult<WorkflowDef[]> =
    useWorkflowDefs()
  const [
    { pageParam, searchParam },
    { handlePageChange, setSearchParam },
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

  const filteredDefinitions = useMemo(() => {
    const keyword = searchParam.trim().toLocaleLowerCase()
    if (!keyword) return data ?? []

    return (data ?? []).filter((definition) =>
      [definition.name, definition.displayName, definition.description]
        .filter((value): value is string => Boolean(value))
        .some((value) => value.toLocaleLowerCase().includes(keyword)),
    )
  }, [data, searchParam])

  const columns = useMemo<TableColumnsType<WorkflowDef>>(
    () => [
      {
        title: '工作流名称',
        dataIndex: 'displayName',
        key: 'workflowName',
        width: 200,
        render: (_value, definition) => (
          <NavLink
            className={styles.workflowLink}
            path={`/workflowDef/${encodeURIComponent(definition.name)}/${definition.version}`}
            id={`${definition.name}-link-btn`}
          >
            {definition.displayName}
          </NavLink>
        ),
      },
      {
        title: '路由',
        dataIndex: 'name',
        key: 'route',
        width: 210,
        render: (name: string) => <code className={styles.route}>{name}</code>,
      },
      {
        title: '描述',
        dataIndex: 'description',
        key: 'description',
        ellipsis: true,
        render: (description?: string) => description || '-',
      },
      {
        title: '创建时间',
        dataIndex: 'createTime',
        key: 'createTime',
        width: 185,
        render: (value?: number | string) => {
          if (value == null || value === '') return '-'
          const date = new Date(value)
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
        render: (version?: number) => version ?? '-',
      },
      {
        title: '操作',
        key: 'actions',
        width: 136,
        fixed: 'right',
        render: (_value, definition: WorkflowDef) => (
          <Space size={2}>
            <Tooltip title="复制工作流定义">
              <Button
                type="text"
                aria-label={`复制 ${definition.name}`}
                icon={<CopyIcon size={18} />}
                onClick={() =>
                  setSelectedWorkflowWithAction({
                    selectedWorkflow: definition,
                    action: 'clone',
                  })
                }
              />
            </Tooltip>
            <Tooltip title="编辑工作流">
              <Button
                type="text"
                aria-label={`编辑 ${definition.name}`}
                icon={<PencilSimpleIcon size={18} />}
                onClick={() =>
                  navigate(
                    `/workflowDef/${encodeURIComponent(definition.name)}/${definition.version}`,
                  )
                }
              />
            </Tooltip>
            <Tooltip title="删除工作流">
              <Button
                type="text"
                danger
                aria-label={`删除 ${definition.name}`}
                icon={<TrashIcon size={18} />}
                onClick={() =>
                  setSelectedWorkflowWithAction({
                    selectedWorkflow: definition,
                    action: 'delete',
                  })
                }
              />
            </Tooltip>
          </Space>
        ),
      },
    ],
    [navigate],
  )

  return (
    <>
      <Helmet>
        <title>工作流配置</title>
      </Helmet>

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
        actions={
          <Space size={12} wrap>
            <Button icon={<PlayIcon size={17} />}>运行工作流</Button>
            <Button
              type="primary"
              icon={<PlusIcon size={17} />}
              onClick={() =>
                navigate('/workflowDef/edit?name=new_workflow&version=1&new=true')
              }
            >
              新建工作流
            </Button>
          </Space>
        }
      />

      <SectionContainer>
        <DataTable<WorkflowDef>
          id="workflow-definitions-table-wrapper"
          quickSearchEnabled
          quickSearchPlaceholder="搜索工作流配置"
          searchTerm={searchParam}
          onSearchTermChange={setSearchParam}
          toolbar={
            <strong className={styles.resultCount}>
              {filteredDefinitions.length} 条结果
            </strong>
          }
          customActions={
            <Button
              type="text"
              icon={<ArrowClockwiseIcon size={17} />}
              loading={isFetching}
              onClick={() => void refetch()}
            >
              刷新
            </Button>
          }
          columns={columns}
          dataSource={filteredDefinitions}
          loading={isFetching}
          rowKey={(definition) => `${definition.name}-${definition.version}`}
          scroll={{ x: 970 }}
          locale={{
            emptyText: <Empty description="没有匹配的工作流配置" />,
          }}
          pagination={{
            current: currentPage,
            defaultPageSize: DEFAULT_PAGE_SIZE_OPTIONS[0],
            pageSizeOptions: DEFAULT_PAGE_SIZE_OPTIONS,
            showSizeChanger: true,
            showTotal: (total, range) =>
              total ? `${range[0]}-${range[1]} / ${total}` : '0-0 / 0',
            placement: ['bottomEnd'],
            onChange: (page) => handlePageChange(page),
          }}
        />
      </SectionContainer>
    </>
  )
}
