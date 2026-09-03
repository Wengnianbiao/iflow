import { useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, useNavigate } from 'react-router-dom'
import {
  CopyIcon,
  PencilSimpleIcon,
  PlayCircleIcon,
  PlusCircleIcon,
  TrashIcon,
  WarningCircleIcon,
  XIcon,
} from '@phosphor-icons/react'
import {
  deleteWorkflowDefinition,
  listWorkflowDefinitions,
  registerWorkflowDefinition,
} from '@/api/workflowDefinition'
import type { WorkflowDef } from '@/types/workflow'

const PAGE_SIZE_OPTIONS = [15, 25, 50] as const

function workflowEditorPath(def: Pick<WorkflowDef, 'name' | 'version'>) {
  const params = new URLSearchParams({ name: def.name })
  if (def.version != null) params.set('version', String(def.version))
  return `/workflowDef/edit?${params.toString()}`
}

function formatDefinitionTime(value?: number | string) {
  if (value == null || value === '') return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

function nextCopyName(name: string, definitions: WorkflowDef[]) {
  const existingNames = new Set(definitions.map((definition) => definition.name))
  const baseName = `${name}_copy`
  if (!existingNames.has(baseName)) return baseName
  let index = 2
  while (existingNames.has(`${baseName}_${index}`)) index += 1
  return `${baseName}_${index}`
}

function definitionDisplayName(definition: WorkflowDef) {
  return definition.displayName?.trim() || definition.name
}

type DefinitionDialog = {
  type: 'copy' | 'delete'
  definition: WorkflowDef
}

export function WorkflowDefinitionListPage() {
  const navigate = useNavigate()
  const [definitions, setDefinitions] = useState<WorkflowDef[]>([])
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZE_OPTIONS)[number]>(15)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dialog, setDialog] = useState<DefinitionDialog | null>(null)
  const [copyName, setCopyName] = useState('')
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [dialogBusy, setDialogBusy] = useState(false)

  const load = () => {
    setLoading(true)
    setError(null)
    listWorkflowDefinitions()
      .then(setDefinitions)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : '加载工作流列表失败'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  useEffect(() => {
    setPage(1)
  }, [query, pageSize])

  useEffect(() => {
    if (!dialog) return

    const previousOverflow = document.body.style.overflow
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !dialogBusy) setDialog(null)
    }
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [dialog, dialogBusy])

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return definitions
    return definitions.filter((item) => {
      return [item.name, item.displayName, item.description]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(keyword))
    })
  }, [definitions, query])

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const currentPage = Math.min(page, totalPages)

  const paginated = useMemo(() => {
    const start = (currentPage - 1) * pageSize
    return filtered.slice(start, start + pageSize)
  }, [currentPage, filtered, pageSize])

  const rangeStart = filtered.length ? (currentPage - 1) * pageSize + 1 : 0
  const rangeEnd = filtered.length ? Math.min(currentPage * pageSize, filtered.length) : 0

  const closeDialog = () => {
    if (dialogBusy) return
    setDialog(null)
    setDialogError(null)
  }

  const openCopyDialog = (definition: WorkflowDef) => {
    setCopyName(nextCopyName(definition.name, definitions))
    setDialogError(null)
    setDialog({ type: 'copy', definition })
  }

  const openDeleteDialog = (definition: WorkflowDef) => {
    setDialogError(null)
    setDialog({ type: 'delete', definition })
  }

  const copyDefinition = async () => {
    if (!dialog || dialog.type !== 'copy') return
    const name = copyName.trim()
    if (!name) {
      setDialogError('请输入工作流名称')
      return
    }
    if (definitions.some((item) => item.name === name)) {
      setDialogError(`工作流名称已存在：${name}`)
      return
    }
    try {
      setDialogBusy(true)
      setDialogError(null)
      const source = dialog.definition
      await registerWorkflowDefinition({
        ...source,
        name,
        version: 1,
        displayName: source.displayName ? `${source.displayName} 副本` : name,
      })
      setDialog(null)
      load()
    } catch (err) {
      setDialogError(err instanceof Error ? err.message : '复制工作流失败')
    } finally {
      setDialogBusy(false)
    }
  }

  const deleteDefinition = async () => {
    if (!dialog || dialog.type !== 'delete') return
    try {
      setDialogBusy(true)
      setDialogError(null)
      await deleteWorkflowDefinition(dialog.definition.name, dialog.definition.version)
      setDialog(null)
      load()
    } catch (err) {
      setDialogError(err instanceof Error ? err.message : '删除工作流失败')
    } finally {
      setDialogBusy(false)
    }
  }

  return (
    <>
      <div className="definition-list-page">
      <header className="definition-list-header">
        <h1>工作流配置</h1>
        <div className="definition-header-actions">
          <button type="button" className="outline-action">
            <PlayCircleIcon size={18} />
            运行工作流
          </button>
          <button
            type="button"
            className="primary-action"
            onClick={() => navigate('/workflowDef/edit?name=new_workflow&version=1&new=true')}
          >
            <PlusCircleIcon size={18} />
            新建工作流
          </button>
        </div>
      </header>

      <section className="definition-search-band">
        <label className="quick-search">
          <span>快速搜索</span>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="按工作流名称、路由或描述搜索"
          />
        </label>
      </section>

      <section className="definition-table-card">
        <div className="definition-table-toolbar">
          <strong>{loading ? '正在加载...' : `共 ${filtered.length} 条`}</strong>
          {error ? <span className="list-error">{error}</span> : null}
        </div>

        <table className="workflow-definitions-table">
          <thead>
            <tr>
              <th>工作流名称</th>
              <th>路由</th>
              <th>描述</th>
              <th>创建时间</th>
              <th>最新版本</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {paginated.map((definition) => (
              <tr key={`${definition.name}-${definition.version}`}>
                <td>
                  <Link className="workflow-name-link" to={workflowEditorPath(definition)}>
                    {definitionDisplayName(definition)}
                  </Link>
                </td>
                <td>
                  <span className="workflow-route-text">{definition.name}</span>
                </td>
                <td>{definition.description || '-'}</td>
                <td>{formatDefinitionTime(definition.createTime)}</td>
                <td>{definition.version ?? '-'}</td>
                <td>
                  <div className="row-actions">
                    <button
                      type="button"
                      data-tooltip="复制工作流定义"
                      aria-label={`复制 ${definition.name}`}
                      onClick={() => openCopyDialog(definition)}
                    >
                      <CopyIcon size={19} />
                    </button>
                    <button
                      type="button"
                      data-tooltip="编辑工作流"
                      aria-label={`编辑 ${definition.name}`}
                      onClick={() => navigate(workflowEditorPath(definition))}
                    >
                      <PencilSimpleIcon size={19} />
                    </button>
                    <button
                      type="button"
                      data-tooltip="删除工作流"
                      aria-label={`删除 ${definition.name}`}
                      onClick={() => openDeleteDialog(definition)}
                    >
                      <TrashIcon size={19} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {!loading && filtered.length === 0 ? (
              <tr>
                <td colSpan={6} className="empty-table">
                  没有匹配的工作流配置
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>

        <footer className="definition-table-footer">
          <span>每页行数：</span>
          <select
            className="definition-page-size"
            value={pageSize}
            onChange={(event) => setPageSize(Number(event.target.value) as (typeof PAGE_SIZE_OPTIONS)[number])}
            aria-label="每页行数"
          >
            {PAGE_SIZE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
          <span>{filtered.length ? `${rangeStart}-${rangeEnd} / ${filtered.length}` : '0-0 / 0'}</span>
          <button
            type="button"
            className="definition-pager-button"
            aria-label="上一页"
            disabled={currentPage <= 1}
            onClick={() => setPage((value) => Math.max(1, value - 1))}
          >
            ‹
          </button>
          <button
            type="button"
            className="definition-pager-button"
            aria-label="下一页"
            disabled={currentPage >= totalPages}
            onClick={() => setPage((value) => Math.min(totalPages, value + 1))}
          >
            ›
          </button>
        </footer>
      </section>
      </div>

      {dialog
        ? createPortal(
            <div className="definition-dialog-backdrop" role="presentation" onMouseDown={closeDialog}>
              <section
                className={`definition-dialog${dialog.type === 'delete' ? ' is-danger' : ''}`}
                role="dialog"
                aria-modal="true"
                aria-labelledby="definition-dialog-title"
                aria-describedby="definition-dialog-description"
                onMouseDown={(event) => event.stopPropagation()}
              >
                <header className="definition-dialog-header">
                  <h2 id="definition-dialog-title">
                    {dialog.type === 'copy' ? '复制工作流' : '删除工作流'}
                  </h2>
                  <button type="button" className="definition-dialog-close" aria-label="关闭弹窗" onClick={closeDialog} disabled={dialogBusy}>
                    <XIcon size={20} />
                  </button>
                </header>

                <form
                  onSubmit={(event) => {
                    event.preventDefault()
                    void (dialog.type === 'copy' ? copyDefinition() : deleteDefinition())
                  }}
                >
                  <div className="definition-dialog-content">
                    {dialog.type === 'copy' ? (
                      <>
                        <p id="definition-dialog-description" className="definition-dialog-description">
                          复制 <strong>{definitionDisplayName(dialog.definition)}</strong> 的最新定义，副本版本将从 1 开始。
                        </p>
                        <label className="definition-dialog-field">
                          <span>工作流名称 *</span>
                          <input
                            value={copyName}
                            onChange={(event) => {
                              setCopyName(event.target.value)
                              setDialogError(null)
                            }}
                            autoFocus
                            spellCheck={false}
                            aria-invalid={Boolean(dialogError)}
                          />
                        </label>
                      </>
                    ) : (
                      <div className="definition-dialog-warning">
                        <WarningCircleIcon size={34} weight="fill" aria-hidden="true" />
                        <div>
                          <p id="definition-dialog-description">
                            确定删除工作流 <strong>{definitionDisplayName(dialog.definition)}</strong> 的版本 {dialog.definition.version ?? '-'} 吗？
                          </p>
                          <span>删除后无法恢复，请确认当前版本不再使用。</span>
                        </div>
                      </div>
                    )}
                    {dialogError ? <p className="definition-dialog-error" role="alert">{dialogError}</p> : null}
                  </div>

                  <footer className="definition-dialog-actions">
                    <button type="button" className="definition-dialog-cancel" onClick={closeDialog} disabled={dialogBusy} autoFocus={dialog.type === 'delete'}>
                      取消
                    </button>
                    <button
                      type="submit"
                      className={dialog.type === 'delete' ? 'definition-dialog-delete' : 'definition-dialog-primary'}
                      disabled={dialogBusy || (dialog.type === 'copy' && !copyName.trim())}
                    >
                      {dialogBusy ? '处理中...' : dialog.type === 'copy' ? '确认复制' : '确认删除'}
                    </button>
                  </footer>
                </form>
              </section>
            </div>,
            document.body,
          )
        : null}
    </>
  )
}
