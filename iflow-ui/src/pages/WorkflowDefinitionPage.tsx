import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { useSelector } from '@xstate/react'
import Editor, { DiffEditor, type BeforeMount, type MonacoDiffEditor, type OnMount } from '@monaco-editor/react'
import {
  ArrowsInSimple,
  ArrowsOutSimple,
  ArrowClockwise,
  BracketsCurly,
  CaretDown,
  CaretUp,
  CaretDoubleLeft,
  CaretDoubleRight,
  Copy,
  DownloadSimple,
  FloppyDisk,
  PlusCircle,
  RocketLaunch,
  Tag,
  Trash,
  WarningCircle,
  X,
  XCircle,
} from '@phosphor-icons/react'
import { buildVariableOptions, findTask, spiJavaTemplate } from '@/flow/workflowEditor'
import { TaskType, taskTypeLabel } from '@/flow/taskType'
import type { WorkflowDef, WorkflowTask } from '@/types/workflow'
import { ConductorFlowCanvas } from '@/components/flow/ConductorFlowCanvas'
import { formatInlineScript } from '@/components/flow/InlineScriptCode'
import { ParamMapTaskInspector } from '@/components/workflow/ParamMapTaskInspector'
import {
  hasWorkflowChanges,
  useWorkflowEditContext,
  type VersionChoice,
} from '@/pages/definition/state'

const SOURCE_EDITOR_THEME = 'iflow-source-light'
const MIN_CANVAS_WIDTH = 400
const MIN_INSPECTOR_WIDTH = 150
const VARIABLE_REGEX = /\$\{[^}]*$/

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function numberOrUndefined(value: string | null | undefined) {
  if (value == null || value === '') return undefined
  const number = Number(value)
  return Number.isFinite(number) ? number : undefined
}

function prettyJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2)
}

function valueText(value: unknown) {
  if (value == null) return ''
  return typeof value === 'string' ? value : JSON.stringify(value)
}

function uniqueValues(values: string[]) {
  return [...new Set(values.filter(Boolean))]
}

function filterVariableOptions(options: string[], value: string, forceAll: boolean) {
  if (forceAll || !value) return options
  const currentValue = value.toLowerCase()
  if (currentValue.endsWith('$')) return options.filter((option) => option.toLowerCase().startsWith('$'))
  const matchedValue = currentValue.match(VARIABLE_REGEX)?.[0]
  if (matchedValue) return options.filter((option) => option.toLowerCase().startsWith(matchedValue))
  return options.filter((option) => option.toLowerCase().startsWith(currentValue))
}

function replaceVariableExpression(value: string, option: string) {
  if (value.endsWith('$')) return `${value.slice(0, -1)}${option}`
  if (VARIABLE_REGEX.test(value)) return value.replace(VARIABLE_REGEX, option)
  return option
}

function downloadDefinition(workflow: WorkflowDef) {
  const blob = new Blob([prettyJson(workflow)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${workflow.name}_v${workflow.version}.json`
  anchor.click()
  URL.revokeObjectURL(url)
}

function VersionHeadBarSelect({
  versions,
  currentVersion,
  latestSelected,
  disabled,
  showLatest,
  onChange,
}: {
  versions: number[]
  currentVersion?: number
  latestSelected: boolean
  disabled?: boolean
  showLatest: boolean
  onChange: (version: VersionChoice) => void
}) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const items = [
    ...versions.map((version) => ({ value: String(version), label: `版本 ${version}`, kind: 'version' as const, version })),
    ...(showLatest ? [{ value: 'latest', label: '最新版本', kind: 'latest' as const, version: undefined }] : []),
  ]
  const selectedValue = latestSelected ? 'latest' : String(currentVersion ?? '')
  const displayValue = latestSelected ? '最新版本' : currentVersion != null ? `版本 ${currentVersion}` : '选择版本'
  const hasOptions = items.length > 0

  useEffect(() => {
    if (!open) return
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  return (
    <div className="version-headbar-select" ref={rootRef}>
      <button
        type="button"
        className={`version-headbar-button ${open ? 'open' : ''}`}
        disabled={disabled || !hasOptions}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => hasOptions && setOpen((value) => !value)}
      >
        <span className="version-headbar-icon" aria-hidden="true">
          <Tag size={16} weight="bold" />
        </span>
        <span>{displayValue}</span>
        {hasOptions ? <CaretDown className="version-headbar-chevron" size={18} weight="bold" /> : null}
      </button>
      {open ? (
        <div className="version-headbar-menu" role="menu">
          {items.map((item) => {
            const selected = item.value === selectedValue
            return (
              <button
                type="button"
                className={`version-headbar-item ${selected ? 'selected' : ''}`}
                key={item.value}
                role="menuitemradio"
                aria-checked={selected}
                onClick={() => {
                  setOpen(false)
                  onChange(item.kind === 'latest' ? 'latest' : item.version)
                }}
              >
                {item.label}
              </button>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}

export function WorkflowDefinitionPage() {
  const actor = useWorkflowEditContext()
  const machineState = useSelector(actor, (state) => state)
  const {
    workflow,
    baseline,
    versions,
    selectedTaskRef,
    activeTab,
    inspectorWidth,
    workflowName,
    workflowVersion,
    isNewWorkflow,
    pendingVersion,
    deleteConfirmationValue,
    deleteError,
    error,
  } = machineState.context
  const loading = machineState.matches('loading')
  const saving = machineState.matches('ready.lifecycle.saving')
  const deleting = machineState.matches('ready.lifecycle.deleting')
  const saveConfirmationOpen = machineState.matches('ready.lifecycle.confirmSave') || saving
  const resetConfirmationOpen = machineState.matches('ready.lifecycle.confirmReset')
  const deleteConfirmationOpen = machineState.matches('ready.lifecycle.confirmDelete') || deleting
  const versionConfirmationOpen = machineState.matches('ready.lifecycle.confirmVersionChange')
  const inspectorOpen = !machineState.matches('ready.rightPanel.closed')
  const dirty = hasWorkflowChanges(machineState.context)
  const [saveDiffChangeCount, setSaveDiffChangeCount] = useState(0)
  const [resizingInspector, setResizingInspector] = useState(false)
  const inspectorResizeRef = useRef<{ containerLeft: number; containerWidth: number } | null>(null)
  const saveDiffEditorRef = useRef<MonacoDiffEditor | null>(null)
  const saveDiffUpdateRef = useRef<{ dispose: () => void } | null>(null)

  const selectedTask = useMemo(() => {
    if (!workflow || !selectedTaskRef) return undefined
    return findTask(workflow.tasks, selectedTaskRef)
  }, [workflow, selectedTaskRef])
  const variableOptions = useMemo(() => (workflow ? buildVariableOptions(workflow) : []), [workflow])

  const requestSave = () => {
    actor.send({ type: 'REQUEST_SAVE' })
  }

  const mountSaveDiffEditor = useCallback((editor: MonacoDiffEditor) => {
    const model = editor.getModel()
    if (model) {
      model.original.setValue(isNewWorkflow ? '{}' : baseline ? prettyJson(baseline) : '{}')
      model.modified.setValue(workflow ? prettyJson(workflow) : '{}')
    }
    saveDiffEditorRef.current = editor
    saveDiffUpdateRef.current?.dispose()
    const updateChangeCount = () => setSaveDiffChangeCount(editor.getLineChanges()?.length ?? 0)
    saveDiffUpdateRef.current = editor.onDidUpdateDiff(updateChangeCount)
    updateChangeCount()
  }, [baseline, isNewWorkflow, workflow])

  const navigateSaveDiff = (target: 'next' | 'previous') => {
    saveDiffEditorRef.current?.goToDiff(target)
  }

  useEffect(() => {
    if (!saveConfirmationOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !saving) actor.send({ type: 'CANCEL_DIALOG' })
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [actor, saveConfirmationOpen, saving])

  useEffect(() => {
    if (saveConfirmationOpen) return
    saveDiffUpdateRef.current?.dispose()
    saveDiffUpdateRef.current = null
    saveDiffEditorRef.current = null
    setSaveDiffChangeCount(0)
  }, [saveConfirmationOpen])

  const requestRemoveDefinition = () => {
    actor.send({ type: 'REQUEST_DELETE' })
  }

  const closeDeleteConfirmation = () => {
    if (deleting) return
    actor.send({ type: 'CANCEL_DIALOG' })
  }

  const removeDefinition = () => {
    actor.send({ type: 'CONFIRM_DELETE' })
  }

  useEffect(() => {
    if (!deleteConfirmationOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !deleting) closeDeleteConfirmation()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [actor, deleteConfirmationOpen, deleting])

  useEffect(() => {
    if (!resetConfirmationOpen && !versionConfirmationOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') actor.send({ type: 'CANCEL_DIALOG' })
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [actor, resetConfirmationOpen, versionConfirmationOpen])

  const changeVersion = (version: VersionChoice) => {
    actor.send({ type: 'REQUEST_VERSION_CHANGE', version })
  }

  const updateSelectedTask = (nextTask: WorkflowTask) => {
    actor.send({ type: 'TASK_UPDATED', task: nextTask })
  }

  const changeWorkflow = useCallback((nextWorkflow: WorkflowDef) => {
    actor.send({ type: 'WORKFLOW_CHANGED', workflow: nextWorkflow })
  }, [actor])

  const resizeInspectorFromClientX = useCallback((clientX: number) => {
    const resizeState = inspectorResizeRef.current
    if (!resizeState) return
    const leftWidth = clientX - resizeState.containerLeft
    const rightWidth = resizeState.containerWidth - leftWidth
    const minRightPercent = (MIN_INSPECTOR_WIDTH / resizeState.containerWidth) * 100
    const maxRightPercent = 100 - (MIN_CANVAS_WIDTH / resizeState.containerWidth) * 100
    actor.send({
      type: 'RESIZE_PANEL',
      width: clamp((rightWidth / resizeState.containerWidth) * 100, minRightPercent, maxRightPercent),
    })
  }, [actor])

  useEffect(() => {
    if (!resizingInspector) return undefined

    const handleMouseMove = (event: MouseEvent) => {
      event.preventDefault()
      resizeInspectorFromClientX(event.clientX)
    }

    const handleMouseUp = () => {
      inspectorResizeRef.current = null
      setResizingInspector(false)
    }

    document.addEventListener('mousemove', handleMouseMove, true)
    document.addEventListener('mouseup', handleMouseUp, true)
    return () => {
      document.removeEventListener('mousemove', handleMouseMove, true)
      document.removeEventListener('mouseup', handleMouseUp, true)
    }
  }, [resizeInspectorFromClientX, resizingInspector])

  const startInspectorResize = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (!inspectorOpen) return
    const container = event.currentTarget.parentElement
    if (!container) return
    event.preventDefault()
    const rect = container.getBoundingClientRect()
    inspectorResizeRef.current = {
      containerLeft: rect.left,
      containerWidth: rect.width,
    }
    setResizingInspector(true)
    resizeInspectorFromClientX(event.clientX)
  }

  const mainStyle = inspectorOpen
    ? ({ '--workflow-inspector-width': `${inspectorWidth}%` } as CSSProperties)
    : undefined

  return (
    <div className="workflow-page">
      <header className="workflow-editor-header">
        <div className="workflow-title-block">
          <div className="workflow-breadcrumbs">
            <Link to="/workflowDef">工作流配置</Link>
            <span>/</span>
            <span>{workflow?.displayName || workflow?.name || workflowName}</span>
          </div>
          <div className="workflow-title-row">
            <h1>{workflow?.displayName || workflow?.name || workflowName}</h1>
            {dirty ? <span className="unsaved-badge">未保存</span> : null}
          </div>
        </div>
        <div className="workflow-editor-actions">
          <VersionHeadBarSelect
            versions={versions.length ? versions : workflow ? [workflow.version] : []}
            currentVersion={workflow?.version}
            latestSelected={workflowVersion == null}
            disabled={!workflow}
            showLatest={!isNewWorkflow}
            onChange={changeVersion}
          />
          <button type="button" className="danger-action" disabled={!workflow || isNewWorkflow || saving || deleting} onClick={requestRemoveDefinition}>
            <Trash size={20} />
            删除
          </button>
          <button type="button" className="ghost-action" disabled={!dirty || saving} onClick={() => actor.send({ type: 'REQUEST_RESET' })}>
            <ArrowClockwise size={20} />
            重置
          </button>
          <button type="button" className="ghost-action" disabled={!workflow} onClick={() => workflow && downloadDefinition(workflow)}>
            <DownloadSimple size={20} />
            下载
          </button>
          <button type="button" className="save-action" onClick={requestSave} disabled={!workflow || !dirty || saving}>
            <FloppyDisk size={20} />
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
        {error ? <div className="workflow-toast error">{error}</div> : null}
      </header>

      <main className={`workflow-main ${inspectorOpen ? '' : 'inspector-collapsed'} ${resizingInspector ? 'resizing-panel' : ''}`} style={mainStyle}>
        <section className="workflow-canvas-shell">
          {!inspectorOpen ? (
            <button type="button" className="canvas-open-panel" title="展开属性面板" onClick={() => actor.send({ type: 'OPEN_PANEL' })}>
              <CaretDoubleLeft size={16} />
              打开面板
            </button>
          ) : null}
          {loading ? <div className="workflow-loading">正在加载工作流...</div> : null}
          {machineState.matches('loadFailed') ? (
            <div className="workflow-loading workflow-load-failed">
              <span>{error || '加载工作流失败'}</span>
              <button type="button" onClick={() => actor.send({ type: 'RETRY' })}>
                重新加载
              </button>
            </div>
          ) : null}
          {workflow ? (
            <ConductorFlowCanvas
              workflow={workflow}
              selectedTaskRef={selectedTaskRef}
              onWorkflowChange={changeWorkflow}
              onSelectTask={(task) => actor.send({ type: 'TASK_SELECTED', task })}
            />
          ) : null}
        </section>

        {inspectorOpen ? (
          <>
            <div
              id="editor-panel-resize-line"
              className={`workflow-panel-resizer ${resizingInspector ? 'active' : ''}`}
              onMouseDown={startInspectorResize}
            />
            <aside className="workflow-inspector">
              <div className="inspector-tabs">
                <button type="button" className="collapse-tab" title="收起属性面板" onClick={() => actor.send({ type: 'CLOSE_PANEL' })}>
                  <CaretDoubleRight size={18} />
                </button>
                <button type="button" className={activeTab === 'workflow' ? 'active' : ''} onClick={() => actor.send({ type: 'CHANGE_TAB', tab: 'workflow' })}>
                  工作流
                </button>
                <button type="button" className={activeTab === 'task' ? 'active' : ''} onClick={() => actor.send({ type: 'CHANGE_TAB', tab: 'task' })}>
                  任务
                </button>
                <button type="button" className={activeTab === 'code' ? 'active' : ''} onClick={() => actor.send({ type: 'CHANGE_TAB', tab: 'code' })}>
                  源码
                </button>
              </div>
              <div className="inspector-scroll">
                {activeTab === 'workflow' && workflow ? (
                  <WorkflowInspector workflow={workflow} variableOptions={variableOptions} onChange={changeWorkflow} />
                ) : null}
                {activeTab === 'task' ? (
                  selectedTask ? (
                    selectedTask.type === TaskType.INLINE
                      || selectedTask.type === TaskType.HTTP
                      || selectedTask.type === TaskType.PARAM_MAP
                      || selectedTask.type === TaskType.SPI ? (
                      <TaskInspector key={selectedTask.taskReferenceName} task={selectedTask} variableOptions={variableOptions} onChange={updateSelectedTask} />
                    ) : (
                      <div className="inspector-section task-panel-section">
                        <TaskInspector key={selectedTask.taskReferenceName} task={selectedTask} variableOptions={variableOptions} onChange={updateSelectedTask} />
                      </div>
                    )
                  ) : (
                    <div className="inspector-section task-panel-section">
                      <div className="inspector-empty-state">
                        <strong>尚未选择任务</strong>
                        <span>点击画布中的任务节点后，可在这里编辑任务属性。</span>
                      </div>
                    </div>
                  )
                ) : null}
                {activeTab === 'code' && workflow ? <CodeInspector workflow={workflow} onChange={changeWorkflow} /> : null}
              </div>
            </aside>
          </>
        ) : null}
      </main>

      {resetConfirmationOpen
        ? createPortal(
            <div className="workflow-delete-backdrop" role="presentation" onMouseDown={() => actor.send({ type: 'CANCEL_DIALOG' })}>
              <section
                className="workflow-delete-dialog workflow-confirm-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="workflow-reset-title"
                onMouseDown={(event) => event.stopPropagation()}
              >
                <header>
                  <span className="workflow-delete-dialog-icon workflow-confirm-dialog-icon" aria-hidden="true">
                    <ArrowClockwise size={23} weight="bold" />
                  </span>
                  <div>
                    <h2 id="workflow-reset-title">重置工作流修改</h2>
                    <p>恢复到上次保存的工作流定义。</p>
                  </div>
                  <button type="button" aria-label="关闭重置确认" title="关闭" onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}>
                    <X size={20} />
                  </button>
                </header>
                <div className="workflow-delete-dialog-content">
                  <p>当前所有未保存的画布、任务属性和工作流配置修改都将丢失。</p>
                </div>
                <footer>
                  <button type="button" className="delete-cancel-action" onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}>
                    取消
                  </button>
                  <button type="button" className="workflow-confirm-action" onClick={() => actor.send({ type: 'CONFIRM_RESET' })}>
                    <ArrowClockwise size={18} weight="bold" />
                    确认重置
                  </button>
                </footer>
              </section>
            </div>,
            document.body,
          )
        : null}

      {versionConfirmationOpen
        ? createPortal(
            <div className="workflow-delete-backdrop" role="presentation" onMouseDown={() => actor.send({ type: 'CANCEL_DIALOG' })}>
              <section
                className="workflow-delete-dialog workflow-confirm-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="workflow-version-change-title"
                onMouseDown={(event) => event.stopPropagation()}
              >
                <header>
                  <span className="workflow-delete-dialog-icon workflow-confirm-dialog-icon" aria-hidden="true">
                    <Tag size={23} weight="bold" />
                  </span>
                  <div>
                    <h2 id="workflow-version-change-title">切换工作流版本</h2>
                    <p>当前工作流存在未保存的修改。</p>
                  </div>
                  <button type="button" aria-label="关闭版本切换确认" title="关闭" onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}>
                    <X size={20} />
                  </button>
                </header>
                <div className="workflow-delete-dialog-content">
                  <p>
                    切换到{pendingVersion === 'latest' ? '最新版本' : `版本 ${pendingVersion ?? ''}`}
                    后，当前未保存的修改将丢失。
                  </p>
                </div>
                <footer>
                  <button type="button" className="delete-cancel-action" onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}>
                    取消
                  </button>
                  <button type="button" className="workflow-confirm-action" onClick={() => actor.send({ type: 'CONFIRM_VERSION_CHANGE' })}>
                    <Tag size={18} weight="bold" />
                    继续切换
                  </button>
                </footer>
              </section>
            </div>,
            document.body,
          )
        : null}

      {saveConfirmationOpen && workflow
        ? createPortal(
            <div className="workflow-save-diff-backdrop" role="presentation">
              <section className="workflow-save-diff-dialog" role="dialog" aria-modal="true" aria-labelledby="workflow-save-diff-title">
                <header>
                  <div>
                    <h2 id="workflow-save-diff-title">确认保存工作流</h2>
                    <p>请确认当前版本与待保存版本之间的差异。</p>
                  </div>
                  <button type="button" aria-label="关闭保存确认" title="关闭" disabled={saving} onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}>
                    <X size={20} />
                  </button>
                </header>
                <div className="workflow-save-diff-labels">
                  <span>当前已保存定义</span>
                  <div className="workflow-save-diff-modified-label">
                    <span>待保存定义</span>
                    <div className="workflow-save-diff-navigation">
                      <small>{saveDiffChangeCount > 0 ? `共 ${saveDiffChangeCount} 处变更` : '无变更'}</small>
                      <button
                        type="button"
                        disabled={saveDiffChangeCount === 0}
                        title="上一处变更"
                        aria-label="上一处变更"
                        onClick={() => navigateSaveDiff('previous')}
                      >
                        <CaretUp size={15} weight="bold" />
                        上一处
                      </button>
                      <button
                        type="button"
                        disabled={saveDiffChangeCount === 0}
                        title="下一处变更"
                        aria-label="下一处变更"
                        onClick={() => navigateSaveDiff('next')}
                      >
                        <CaretDown size={15} weight="bold" />
                        下一处
                      </button>
                    </div>
                  </div>
                </div>
                <div className="workflow-save-diff-editor">
                  <DiffEditor
                    height="100%"
                    language="json"
                    originalModelPath={`inmemory://workflow-save/${encodeURIComponent(workflow.name)}-${workflow.version}-original.json`}
                    modifiedModelPath={`inmemory://workflow-save/${encodeURIComponent(workflow.name)}-${workflow.version}-modified.json`}
                    keepCurrentOriginalModel
                    keepCurrentModifiedModel
                    onMount={mountSaveDiffEditor}
                    original={isNewWorkflow ? '{}' : baseline ? prettyJson(baseline) : '{}'}
                    modified={prettyJson(workflow)}
                    options={{
                      automaticLayout: true,
                      fontFamily: 'SFMono-Regular, Consolas, Liberation Mono, monospace',
                      fontSize: 12,
                      lineHeight: 19,
                      minimap: { enabled: false },
                      originalEditable: false,
                      readOnly: true,
                      renderSideBySide: true,
                      scrollBeyondLastLine: false,
                    }}
                  />
                </div>
                <footer>
                  <button type="button" className="diff-cancel-action" disabled={saving} onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}>
                    <X size={17} />
                    取消
                  </button>
                  <button type="button" className="diff-confirm-action" disabled={saving} onClick={() => actor.send({ type: 'CONFIRM_SAVE' })}>
                    <FloppyDisk size={17} />
                    {saving ? '保存中...' : '确认保存'}
                  </button>
                </footer>
              </section>
            </div>,
            document.body,
          )
        : null}

      {deleteConfirmationOpen && workflow
        ? createPortal(
            <div className="workflow-delete-backdrop" role="presentation" onMouseDown={closeDeleteConfirmation}>
              <section
                className="workflow-delete-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="workflow-delete-title"
                aria-describedby="workflow-delete-description"
                onMouseDown={(event) => event.stopPropagation()}
              >
                <header>
                  <span className="workflow-delete-dialog-icon" aria-hidden="true">
                    <Trash size={23} weight="bold" />
                  </span>
                  <div>
                    <h2 id="workflow-delete-title">删除工作流版本</h2>
                    <p>此操作不可撤销，请谨慎确认。</p>
                  </div>
                  <button type="button" aria-label="关闭删除确认" title="关闭" disabled={deleting} onClick={closeDeleteConfirmation}>
                    <X size={20} />
                  </button>
                </header>
                <div className="workflow-delete-dialog-content">
                  <p id="workflow-delete-description">
                    即将永久删除以下工作流定义，已经产生的调度记录不会受到影响。
                  </p>
                  <div className="workflow-delete-target">
                    <div>
                      <span>工作流名称</span>
                      <strong>{workflow.displayName || workflow.name}</strong>
                    </div>
                    <div>
                      <span>路由标识</span>
                      <code>{workflow.name}</code>
                    </div>
                    <div>
                      <span>版本</span>
                      <strong>版本 {workflow.version}</strong>
                    </div>
                  </div>
                  <label className="workflow-delete-confirm-field">
                    <span>请输入路由标识 <code>{workflow.name}</code> 以确认删除</span>
                    <input
                      autoFocus
                      value={deleteConfirmationValue}
                      disabled={deleting}
                      spellCheck={false}
                      autoComplete="off"
                      placeholder="输入完整路由标识"
                      onChange={(event) => {
                        actor.send({
                          type: 'UPDATE_DELETE_CONFIRMATION',
                          value: event.target.value,
                        })
                      }}
                    />
                  </label>
                  {deleteError ? <div className="workflow-delete-error"><WarningCircle size={17} />{deleteError}</div> : null}
                </div>
                <footer>
                  <button type="button" className="delete-cancel-action" disabled={deleting} onClick={closeDeleteConfirmation}>
                    取消
                  </button>
                  <button
                    type="button"
                    className="delete-confirm-action"
                    disabled={deleting || deleteConfirmationValue !== workflow.name}
                    onClick={removeDefinition}
                  >
                    <Trash size={18} weight="bold" />
                    {deleting ? '删除中...' : '确认删除'}
                  </button>
                </footer>
              </section>
            </div>,
            document.body,
          )
        : null}
    </div>
  )
}

export function WorkflowInspector({
  workflow,
  variableOptions,
  onChange,
}: {
  workflow: WorkflowDef
  variableOptions: string[]
  onChange: (workflow: WorkflowDef) => void
}) {
  const inputParameters = workflow.inputParameters ?? []
  const outputParameters = Object.entries(workflow.outputParameters ?? {})

  return (
    <div className="workflow-form-panel">
      <WorkflowCollapsibleSection title="工作流信息">
        <div className="inspector-form-grid">
          <label className="inspector-field">
            <span>工作流名称 <strong>*</strong></span>
            <input required value={workflow.displayName ?? ''} onChange={(event) => onChange({ ...workflow, displayName: event.target.value })} placeholder="用于界面展示，例如：鑫达HIS建档" />
          </label>
          <label className="inspector-field">
            <span>路由标识 <strong>*</strong></span>
            <input required value={workflow.name} onChange={(event) => onChange({ ...workflow, name: event.target.value })} placeholder="全局唯一，例如：CreateArchives" />
          </label>
          <label className="inspector-field full">
            <span>关键字表达式</span>
            <VariableExpressionInput
              value={workflow.correlationExpression ?? ''}
              options={variableOptions}
              onChange={(value) => onChange({ ...workflow, correlationExpression: value })}
            />
          </label>
          <label className="inspector-field full">
            <span>描述 <strong>*</strong></span>
            <textarea required value={workflow.description ?? ''} onChange={(event) => onChange({ ...workflow, description: event.target.value })} placeholder="说明该工作流的用途" />
          </label>
        </div>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="输入参数">
        <ParameterRows
          items={inputParameters.map((key) => ({ key, value: '' }))}
          keyLabel="参数名"
          onChange={(index, next) => {
            const values = [...inputParameters]
            values[index] = next.key
            onChange({ ...workflow, inputParameters: values })
          }}
          onDelete={(index) => onChange({ ...workflow, inputParameters: inputParameters.filter((_, itemIndex) => itemIndex !== index) })}
        />
        <button type="button" className="add-parameter-button" onClick={() => onChange({ ...workflow, inputParameters: [...inputParameters, `input_${inputParameters.length + 1}`] })}>
          + 添加输入参数
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="输出参数">
        <ParameterRows
          items={outputParameters.map(([key, value]) => ({ key, value: valueText(value) }))}
          keyLabel="参数名"
          valueLabel="映射值"
          onChange={(index, next) => {
            const entries = [...outputParameters]
            entries[index] = [next.key, next.value]
            onChange({ ...workflow, outputParameters: Object.fromEntries(entries) })
          }}
          onDelete={(index) => onChange({ ...workflow, outputParameters: Object.fromEntries(outputParameters.filter((_, itemIndex) => itemIndex !== index)) })}
          variableOptions={variableOptions}
        />
        <button type="button" className="add-parameter-button" onClick={() => onChange({ ...workflow, outputParameters: { ...(workflow.outputParameters ?? {}), [`output_${outputParameters.length + 1}`]: '' } })}>
          + 添加输出参数
        </button>
      </WorkflowCollapsibleSection>
    </div>
  )
}

function WorkflowCollapsibleSection({ title, children, defaultOpen = true }: { title: string; children: ReactNode; defaultOpen?: boolean }) {
  const [expanded, setExpanded] = useState(defaultOpen)

  return (
    <section className={`inspector-section workflow-collapsible-section ${expanded ? 'expanded' : 'collapsed'}`}>
      <button
        type="button"
        className="workflow-section-toggle"
        aria-expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
      >
        <h2>{title}</h2>
        <CaretDown className="workflow-section-toggle-icon" size={18} weight="bold" />
      </button>
      {expanded ? <div className="workflow-section-body">{children}</div> : null}
    </section>
  )
}

function ParameterRows({
  items,
  keyLabel,
  valueLabel,
  variableOptions,
  onChange,
  onDelete,
}: {
  items: Array<{ key: string; value: string }>
  keyLabel: string
  valueLabel?: string
  variableOptions?: string[]
  onChange: (index: number, item: { key: string; value: string }) => void
  onDelete: (index: number) => void
}) {
  if (!items.length) return <p className="inspector-empty">暂未配置参数</p>
  return (
    <div className="parameter-list">
      {items.map((item, index) => (
        <div className={`parameter-row ${valueLabel ? 'two-column' : ''}`} key={index}>
          <label>
            <span>{keyLabel}</span>
            <input value={item.key} onChange={(event) => onChange(index, { ...item, key: event.target.value })} />
          </label>
          {valueLabel ? (
            <label>
              <span>{valueLabel}</span>
              <VariableExpressionInput
                value={item.value}
                options={variableOptions ?? []}
                onChange={(value) => onChange(index, { ...item, value })}
              />
            </label>
          ) : null}
          <button type="button" title="删除参数" onClick={() => onDelete(index)}><Trash size={17} /></button>
        </div>
      ))}
    </div>
  )
}

function VariableExpressionInput({
  value,
  options,
  onChange,
}: {
  value: string
  options: string[]
  onChange: (value: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [forceAll, setForceAll] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const filteredOptions = useMemo(() => filterVariableOptions(options, value, forceAll).slice(0, 24), [forceAll, options, value])

  useEffect(() => {
    if (!open) return
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [open])

  const selectOption = (option: string) => {
    onChange(replaceVariableExpression(value, option))
    setOpen(false)
    setForceAll(false)
  }

  return (
    <div className="variable-expression-input" ref={rootRef}>
      <input
        value={value}
        onFocus={() => {
          setForceAll(false)
          setOpen(true)
        }}
        onChange={(event) => {
          setForceAll(false)
          onChange(event.target.value)
          setOpen(true)
        }}
        onKeyDown={(event) => {
          if (event.key === 'Escape') setOpen(false)
        }}
      />
      <button
        type="button"
        className="inline-expression-chip variable-expression-trigger"
        title="选择表达式变量"
        aria-label="选择表达式变量"
        aria-expanded={open}
        onClick={() => {
          setForceAll(true)
          setOpen((expanded) => !expanded)
        }}
      >
        <BracketsCurly size={17} weight="bold" />
      </button>
      {open ? (
        <div className="variable-expression-menu" role="listbox">
          {filteredOptions.length ? (
            filteredOptions.map((option) => (
              <button type="button" role="option" key={option} onClick={() => selectOption(option)}>
                {option}
              </button>
            ))
          ) : (
            <span className="variable-expression-empty">暂无匹配变量</span>
          )}
        </div>
      ) : null}
    </div>
  )
}

export function TaskInspector({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  if (task.type === TaskType.INLINE) {
    return <InlineTaskInspector task={task} variableOptions={variableOptions} onChange={onChange} />
  }
  if (task.type === TaskType.HTTP) {
    return <HttpTaskInspector task={task} variableOptions={variableOptions} onChange={onChange} />
  }
  if (task.type === TaskType.JDBC) {
    return <JdbcTaskInspector task={task} variableOptions={variableOptions} onChange={onChange} />
  }
  if (task.type === TaskType.WEBSERVICE) {
    return <WebServiceTaskInspector task={task} variableOptions={variableOptions} onChange={onChange} />
  }
  if (task.type === TaskType.PARAM_MAP) {
    return (
      <ParamMapTaskInspector
        task={task}
        header={<TaskPanelHeader task={task} label="参数映射" onChange={onChange} />}
        renderPayloadInput={(value, updateValue) => (
          <VariableExpressionInput value={value} options={variableOptions} onChange={updateValue} />
        )}
        onChange={onChange}
      />
    )
  }
  if (task.type === TaskType.SPI) {
    return <SpiTaskInspector task={task} variableOptions={variableOptions} onChange={onChange} />
  }

  if (task.type === TaskType.SWITCH || task.type === TaskType.DECISION) {
    return <SwitchTaskInspector task={task} variableOptions={variableOptions} onChange={onChange} />
  }

  if (task.type === TaskType.FORK_JOIN) {
    return <ForkJoinTaskInspector task={task} onChange={onChange} />
  }

  if (task.type === TaskType.JOIN) {
    return <JoinTaskInspector task={task} onChange={onChange} />
  }

  if (task.type === TaskType.DO_WHILE) {
    return <DoWhileTaskInspector task={task} variableOptions={variableOptions} onChange={onChange} />
  }

  return <GenericTaskInspector task={task} onChange={onChange} />
}

function ForkJoinTaskInspector({ task, onChange }: { task: WorkflowTask; onChange: (task: WorkflowTask) => void }) {
  const forkTasks = task.forkTasks ?? []
  return (
    <div className="workflow-form-panel fork-join-task-inspector">
      <TaskPanelHeader task={task} label="并行分支" onChange={onChange} />
      <WorkflowCollapsibleSection title="并行路径">
        <div className="operator-branch-list">
          {forkTasks.map((branch, index) => (
            <div className="operator-branch-row" key={index}>
              <span>分支 {index + 1}</span>
              <strong>{branch.length} 个任务</strong>
              <small>{branch.map((item) => item.taskReferenceName).join(' → ') || '点击画布分支端口添加任务'}</small>
            </div>
          ))}
          {!forkTasks.length ? <p className="inspector-empty">尚未创建并行分支</p> : null}
        </div>
        <button
          type="button"
          className="add-parameter-button"
          onClick={() => onChange({ ...task, forkTasks: [...forkTasks, []] })}
        >
          <PlusCircle size={16} />
          添加并行分支
        </button>
        <p className="field-help">FORK 与紧随其后的 JOIN 作为一个结构整体保存；在画布分支端口的 + 中添加分支任务。</p>
      </WorkflowCollapsibleSection>
    </div>
  )
}

function JoinTaskInspector({ task, onChange }: { task: WorkflowTask; onChange: (task: WorkflowTask) => void }) {
  const joinOn = task.joinOn ?? []
  return (
    <div className="workflow-form-panel join-task-inspector">
      <TaskPanelHeader task={task} label="汇聚" onChange={onChange} />
      <WorkflowCollapsibleSection title="等待任务">
        <label className="inspector-field full">
          <span>Join On</span>
          <textarea
            rows={5}
            value={joinOn.join('\n')}
            placeholder="每行填写一个分支末端任务引用名；留空表示直接汇聚"
            onChange={(event) => onChange({
              ...task,
              joinOn: uniqueValues(event.target.value.split(/[\n,]/).map((item) => item.trim())),
            })}
          />
          <small>列出的任务全部进入终态后，JOIN 才会继续后续流程。</small>
        </label>
      </WorkflowCollapsibleSection>
    </div>
  )
}

function DoWhileTaskInspector({
  task,
  variableOptions,
  onChange,
}: {
  task: WorkflowTask
  variableOptions: string[]
  onChange: (task: WorkflowTask) => void
}) {
  const inputParameters = task.inputParameters ?? {}
  const evaluatorType = String(inputParameters.evaluatorType ?? task.evaluatorType ?? 'graaljs')
  const parameters = Object.entries(inputParameters)
    .filter(([key]) => key !== 'evaluatorType')
    .map(([key, value]) => ({ key, value: valueText(value) }))

  const updateParameters = (items: Array<{ key: string; value: string }>) => {
    onChange({
      ...task,
      evaluatorType,
      inputParameters: { evaluatorType, ...Object.fromEntries(items.map((item) => [item.key, item.value])) },
    })
  }

  return (
    <div className="workflow-form-panel do-while-task-inspector">
      <TaskPanelHeader task={task} label="循环" onChange={onChange} />
      <WorkflowCollapsibleSection title="循环条件">
        <label className="inspector-field full">
          <span>求值器</span>
          <select
            value={evaluatorType}
            onChange={(event) => {
              const nextEvaluator = event.target.value
              onChange({
                ...task,
                evaluatorType: nextEvaluator,
                inputParameters: { ...inputParameters, evaluatorType: nextEvaluator },
              })
            }}
          >
            <option value="graaljs">GraalJS</option>
            <option value="javascript">JavaScript</option>
            <option value="value-param">参数值</option>
          </select>
        </label>
        {evaluatorType === 'value-param' ? (
          <label className="inspector-field full">
            <span>Loop Condition</span>
            <VariableExpressionInput
              value={task.loopCondition ?? ''}
              options={variableOptions}
              onChange={(loopCondition) => onChange({ ...task, loopCondition })}
            />
          </label>
        ) : (
          <InlineScriptEditor
            value={task.loopCondition ?? ''}
            onChange={(loopCondition) => onChange({ ...task, loopCondition })}
            minHeight={190}
            codeLabel="Loop Condition"
            modalTitle="DO WHILE 循环条件"
          />
        )}
        <p className="field-help">循环体每次完成后求值；返回 true 继续下一轮，返回 false 结束循环。上下文包含 <code>iteration</code> 与 <code>workflow.input</code>。</p>
      </WorkflowCollapsibleSection>
      <WorkflowCollapsibleSection title="条件参数">
        <ParameterRows
          items={parameters}
          keyLabel="参数名"
          valueLabel="映射值"
          variableOptions={variableOptions}
          onChange={(index, item) => updateParameters(parameters.map((current, currentIndex) => currentIndex === index ? item : current))}
          onDelete={(index) => updateParameters(parameters.filter((_, currentIndex) => currentIndex !== index))}
        />
        <button
          type="button"
          className="add-parameter-button"
          onClick={() => updateParameters([...parameters, { key: `parameter_${parameters.length + 1}`, value: '' }])}
        >
          <PlusCircle size={16} />
          添加条件参数
        </button>
        <p className="field-help">循环体当前包含 {task.loopOver?.length ?? 0} 个任务；从画布循环节点下方的 + 添加。</p>
      </WorkflowCollapsibleSection>
    </div>
  )
}

function TaskPanelHeader({
  task,
  label,
  onChange,
}: {
  task: WorkflowTask
  label?: string
  onChange: (task: WorkflowTask) => void
}) {
  const [draftName, setDraftName] = useState(task.name ?? '')
  const [draftReferenceName, setDraftReferenceName] = useState(task.taskReferenceName)
  const editingFieldRef = useRef<'name' | 'reference' | null>(null)

  useEffect(() => {
    if (editingFieldRef.current !== 'name') setDraftName(task.name ?? '')
    if (editingFieldRef.current !== 'reference') setDraftReferenceName(task.taskReferenceName)
  }, [task.name, task.taskReferenceName])

  const commitReferenceName = () => {
    editingFieldRef.current = null
    const taskReferenceName = draftReferenceName.trim()
    if (!taskReferenceName) {
      setDraftReferenceName(task.taskReferenceName)
      return
    }
    if (taskReferenceName !== task.taskReferenceName) {
      onChange({ ...task, taskReferenceName })
    }
  }

  return (
    <section className="inspector-section task-summary-section">
      <div className="inline-task-tabs">
        <div className="inline-task-tab-group">
          <button type="button" className="active">{label ?? task.type}</button>
        </div>
        <button type="button" className="inline-test-task">
          <RocketLaunch size={16} weight="fill" />
          MOCK
        </button>
      </div>

      <div className="inspector-form-grid inline-task-definition-card">
        <label className="inspector-field">
          <span>任务名称 <strong>*</strong></span>
          <input
            required
            value={draftName}
            onFocus={() => { editingFieldRef.current = 'name' }}
            onBlur={() => {
              editingFieldRef.current = null
              setDraftName(task.name ?? '')
            }}
            onChange={(event) => {
              const name = event.target.value
              setDraftName(name)
              onChange({ ...task, name })
            }}
          />
        </label>
        <label className="inspector-field">
          <span>引用名称 <strong>*</strong></span>
          <input
            required
            value={draftReferenceName}
            onFocus={() => { editingFieldRef.current = 'reference' }}
            onBlur={commitReferenceName}
            onChange={(event) => setDraftReferenceName(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') event.currentTarget.blur()
            }}
          />
        </label>
      </div>
    </section>
  )
}

function GenericTaskInspector({ task, onChange }: { task: WorkflowTask; onChange: (task: WorkflowTask) => void }) {
  const [inputJson, setInputJson] = useState(prettyJson(task.inputParameters))
  const [jsonError, setJsonError] = useState('')

  const applyInputJson = () => {
    try {
      const parsed = JSON.parse(inputJson) as Record<string, unknown>
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('请输入 JSON 对象')
      onChange({ ...task, inputParameters: parsed })
      setJsonError('')
    } catch (error) {
      setJsonError(error instanceof Error ? error.message : 'JSON 格式不正确')
    }
  }

  return (
    <div className="workflow-form-panel generic-task-inspector">
      <TaskPanelHeader task={task} onChange={onChange} />
      <WorkflowCollapsibleSection title="输入参数">
        <div className="json-editor-block generic-json-editor">
          <div className="json-editor-heading">
            <div><span className="section-kicker">JSON</span><h3>{taskTypeLabel(task.type)}参数</h3></div>
            <button type="button" onClick={applyInputJson}>应用参数</button>
          </div>
          <textarea value={inputJson} onChange={(event) => setInputJson(event.target.value)} spellCheck={false} />
          {jsonError ? <p className="inline-error">{jsonError}</p> : <p className="field-help">支持 Conductor 表达式，例如 <code>${'{workflow.input.id}'}</code></p>}
        </div>
      </WorkflowCollapsibleSection>
    </div>
  )
}

function SwitchCaseRow({
  name,
  taskCount,
  caseNames,
  onRename,
  onDelete,
}: {
  name: string
  taskCount: number
  caseNames: string[]
  onRename: (nextName: string) => void
  onDelete: () => void
}) {
  const [draft, setDraft] = useState(name)
  const [error, setError] = useState('')

  const commit = () => {
    const nextName = draft.trim()
    if (!nextName) {
      setError('分支名称不能为空')
      return
    }
    if (nextName !== name && caseNames.includes(nextName)) {
      setError('分支名称不能重复')
      return
    }
    setError('')
    if (nextName !== name) onRename(nextName)
  }

  return (
    <div className={`switch-case-row ${error ? 'has-error' : ''}`}>
      <label>
        <span>匹配值</span>
        <input
          value={draft}
          aria-invalid={Boolean(error)}
          onChange={(event) => {
            setDraft(event.target.value)
            setError('')
          }}
          onBlur={commit}
          onKeyDown={(event) => {
            if (event.key === 'Enter') event.currentTarget.blur()
            if (event.key === 'Escape') {
              event.preventDefault()
              setDraft(name)
              setError('')
            }
          }}
        />
        {error ? <small>{error}</small> : null}
      </label>
      <span className="switch-case-count">{taskCount} 个任务</span>
      <button type="button" title="删除分支" aria-label={`删除分支 ${name}`} onClick={onDelete}>
        <Trash size={18} />
      </button>
    </div>
  )
}

function SwitchTaskInspector({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const parameterRows = Object.entries(inputParameters).map(([key, value]) => ({ key, value: valueText(value) }))
  const evaluatorType = task.evaluatorType ?? 'value-param'
  const expression = task.expression ?? ''
  const decisionCases = task.decisionCases ?? {}
  const isScriptEvaluator = evaluatorType === 'javascript' || evaluatorType === 'graaljs'

  const updateParameter = (index: number, next: { key: string; value: string }) => {
    const rows = [...parameterRows]
    const previous = rows[index]
    rows[index] = next
    onChange({
      ...task,
      expression: isScriptEvaluator && expression === `$.${previous.key}` ? `$.${next.key}` : expression,
      inputParameters: Object.fromEntries(rows.map((item) => [item.key, item.value])),
    })
  }

  const addParameter = () => {
    let index = parameterRows.length + 1
    let key = index === 1 ? 'switchCaseValue' : `parameter_${index}`
    while (key in inputParameters) {
      index += 1
      key = `parameter_${index}`
    }
    onChange({
      ...task,
      expression: isScriptEvaluator && !expression ? `$.${key}` : expression,
      inputParameters: { ...inputParameters, [key]: '' },
    })
  }

  const deleteParameter = (index: number) => {
    const deletedKey = parameterRows[index]?.key
    const nextRows = parameterRows.filter((_, itemIndex) => itemIndex !== index)
    onChange({
      ...task,
      expression: isScriptEvaluator && expression === `$.${deletedKey}` ? (nextRows[0]?.key ? `$.${nextRows[0].key}` : '') : expression,
      inputParameters: Object.fromEntries(nextRows.map((item) => [item.key, item.value])),
    })
  }

  const changeEvaluatorType = (nextType: 'value-param' | 'graaljs') => {
    if (nextType === 'value-param') {
      const referencedKey = /^\$\.([A-Za-z_$][\w$]*)$/.exec(expression.trim())?.[1]
      const mappedValue = referencedKey && Object.prototype.hasOwnProperty.call(inputParameters, referencedKey)
        ? inputParameters[referencedKey]
        : parameterRows[0]?.value
      onChange({ ...task, evaluatorType: nextType, expression: mappedValue == null ? '' : valueText(mappedValue) })
      return
    }

    const existingKey = parameterRows.find((item) => item.key)?.key
    const parameterKey = existingKey ?? 'switchCaseValue'
    onChange({
      ...task,
      evaluatorType: nextType,
      expression: isScriptEvaluator ? expression : `$.${parameterKey}`,
      inputParameters: existingKey ? inputParameters : { ...inputParameters, [parameterKey]: expression },
    })
  }

  const renameCase = (previousName: string, nextName: string) => {
    const entries = Object.entries(decisionCases).map(([name, tasks]) => [name === previousName ? nextName : name, tasks] as const)
    onChange({ ...task, decisionCases: Object.fromEntries(entries) })
  }

  const addCase = () => {
    let index = Object.keys(decisionCases).length + 1
    let name = `switch_case_${index}`
    while (name in decisionCases) {
      index += 1
      name = `switch_case_${index}`
    }
    onChange({ ...task, decisionCases: { ...decisionCases, [name]: [] } })
  }

  const deleteCase = (name: string) => {
    const { [name]: _removed, ...nextCases } = decisionCases
    onChange({ ...task, decisionCases: nextCases })
  }

  return (
    <div className="workflow-form-panel switch-task-inspector">
      <TaskPanelHeader task={task} label="分支路由" onChange={onChange} />

      <WorkflowCollapsibleSection title="求值规则">
        <div className="switch-evaluator-heading">
          <span>求值方式</span>
          <div className="switch-evaluator-options" role="radiogroup" aria-label="求值方式">
            <label>
              <input
                type="radio"
                name="switch-evaluator-type"
                value="value-param"
                checked={evaluatorType === 'value-param'}
                onChange={() => changeEvaluatorType('value-param')}
              />
              <span>参数值</span>
            </label>
            <label>
              <input
                type="radio"
                name="switch-evaluator-type"
                value="graaljs"
                checked={isScriptEvaluator}
                onChange={() => changeEvaluatorType('graaljs')}
              />
              <span>脚本</span>
            </label>
          </div>
        </div>
        {isScriptEvaluator ? (
          <div className="switch-script-evaluator">
            <div className="switch-script-parameters">
              <h3>脚本入参</h3>
              <ParameterRows
                items={parameterRows}
                keyLabel="参数名"
                valueLabel="映射值"
                variableOptions={variableOptions}
                onChange={updateParameter}
                onDelete={deleteParameter}
              />
              <button type="button" className="add-parameter-button" onClick={addParameter}>
                <PlusCircle size={16} />
                添加脚本入参
              </button>
            </div>
            <InlineScriptEditor value={expression} onChange={(value) => onChange({ ...task, expression: value })} minHeight={190} />
            <p className="field-help">通过 <code>$.参数名</code> 读取上方脚本入参，脚本返回值用于匹配分支条件。</p>
          </div>
        ) : (
          <label className="inspector-field switch-expression-field">
            <span>路由取值</span>
            <VariableExpressionInput
              value={expression}
              options={variableOptions}
              onChange={(value) => onChange({ ...task, expression: value })}
            />
            <small>直接填写 <code>${'{...}'}</code> 表达式，表达式结果用于匹配下方分支条件。</small>
          </label>
        )}
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="分支条件">
        <div className="switch-case-list">
          {Object.entries(decisionCases).map(([name, tasks]) => (
            <SwitchCaseRow
              key={name}
              name={name}
              taskCount={tasks.length}
              caseNames={Object.keys(decisionCases)}
              onRename={(nextName) => renameCase(name, nextName)}
              onDelete={() => deleteCase(name)}
            />
          ))}
          <div className="switch-default-case">
            <span>默认分支</span>
            <strong>{task.defaultCase?.length ?? 0} 个任务</strong>
          </div>
          {!Object.keys(decisionCases).length ? <p className="inspector-empty">暂未配置分支条件</p> : null}
        </div>
        <button type="button" className="add-parameter-button switch-add-case" onClick={addCase}>
          <PlusCircle size={16} />
          添加分支条件
        </button>
      </WorkflowCollapsibleSection>
    </div>
  )
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function HttpTaskInspector({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const hasNestedRequest = isRecord(inputParameters.http_request)
  const request = hasNestedRequest ? inputParameters.http_request as Record<string, unknown> : inputParameters
  const headers = isRecord(request.headers) ? request.headers : {}
  const headerRows = Object.entries(headers).map(([key, value]) => ({ key, value: valueText(value) }))
  const bodyParameters = isRecord(request.body) ? request.body : {}
  const bodyParameterRows = Object.entries(bodyParameters).map(([key, value]) => ({ key, value: valueText(value) }))
  const [bodyJson, setBodyJson] = useState(prettyJson(request.body ?? {}))
  const [bodyError, setBodyError] = useState('')
  const [bodyViewType, setBodyViewType] = useState<'JSON' | 'PARAMETERS'>('JSON')

  const updateHttpRequest = (next: Record<string, unknown>) => {
    const nextRequest = { ...request, ...next }
    onChange({
      ...task,
      inputParameters: hasNestedRequest ? { ...inputParameters, http_request: nextRequest } : nextRequest,
    })
  }

  const updateHeader = (index: number, next: { key: string; value: string }) => {
    const rows = [...headerRows]
    rows[index] = next
    updateHttpRequest({ headers: Object.fromEntries(rows.map((item) => [item.key, item.value])) })
  }

  const addHeader = () => {
    updateHttpRequest({
      headers: {
        ...headers,
        [`header_${headerRows.length + 1}`]: '',
      },
    })
  }

  const deleteHeader = (index: number) => {
    updateHttpRequest({ headers: Object.fromEntries(headerRows.filter((_, itemIndex) => itemIndex !== index).map((item) => [item.key, item.value])) })
  }

  const updateBodyJson = (nextValue: string) => {
    setBodyJson(nextValue)
    try {
      const parsed = nextValue.trim() ? JSON.parse(nextValue) : {}
      updateHttpRequest({ body: parsed })
      setBodyError('')
    } catch (error) {
      setBodyError(error instanceof Error ? error.message : 'JSON 格式不正确')
    }
  }

  const updateBodyParameters = (rows: Array<{ key: string; value: string }>) => {
    const nextBody = Object.fromEntries(rows.map((item) => [item.key, item.value]))
    setBodyJson(prettyJson(nextBody))
    setBodyError('')
    updateHttpRequest({ body: nextBody })
  }

  const updateBodyParameter = (index: number, next: { key: string; value: string }) => {
    const rows = [...bodyParameterRows]
    rows[index] = next
    updateBodyParameters(rows)
  }

  const addBodyParameter = () => {
    let index = bodyParameterRows.length + 1
    let key = `parameter_${index}`
    while (Object.prototype.hasOwnProperty.call(bodyParameters, key)) {
      index += 1
      key = `parameter_${index}`
    }
    updateBodyParameters([...bodyParameterRows, { key, value: '' }])
  }

  const deleteBodyParameter = (index: number) => {
    updateBodyParameters(bodyParameterRows.filter((_, itemIndex) => itemIndex !== index))
  }

  return (
    <div className="workflow-form-panel http-task-inspector">
      <TaskPanelHeader task={task} label="HTTP" onChange={onChange} />

      <WorkflowCollapsibleSection title="请求详情">
        <div className="inspector-form-grid http-request-grid">
          <label className="inspector-field">
            <span>Method</span>
            <select value={String(request.method ?? 'GET')} onChange={(event) => updateHttpRequest({ method: event.target.value })}>
              {['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map((method) => (
                <option value={method} key={method}>{method}</option>
              ))}
            </select>
          </label>
          <label className="inspector-field">
            <span>URL</span>
            <VariableExpressionInput value={String(request.uri ?? '')} options={variableOptions} onChange={(value) => updateHttpRequest({ uri: value })} />
          </label>
          <label className="inspector-field">
            <span>Content-Type</span>
            <input value={String(request.contentType ?? '')} onChange={(event) => updateHttpRequest({ contentType: event.target.value })} placeholder="application/json" />
          </label>
          <label className="inspector-field">
            <span>Accept</span>
            <input value={String(request.accept ?? '')} onChange={(event) => updateHttpRequest({ accept: event.target.value })} placeholder="application/json" />
          </label>
          <label className="inspector-field">
            <span>connectionTimeout</span>
            <input value={String(request.connectionTimeOut ?? '')} onChange={(event) => updateHttpRequest({ connectionTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
          <label className="inspector-field">
            <span>readTimeout</span>
            <input value={String(request.readTimeOut ?? '')} onChange={(event) => updateHttpRequest({ readTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
        </div>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="请求头">
        <ParameterRows
          items={headerRows}
          keyLabel="参数名"
          valueLabel="映射值"
          variableOptions={variableOptions}
          onChange={updateHeader}
          onDelete={deleteHeader}
        />
        <button type="button" className="add-parameter-button" onClick={addHeader}>
          <PlusCircle size={15} />
          添加请求头
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="请求体">
        <div className="http-body-editor">
          <div className="http-body-view-options" role="radiogroup" aria-label="请求体编辑方式">
            <label>
              <input
                type="radio"
                name="http-body-view-type"
                value="JSON"
                checked={bodyViewType === 'JSON'}
                onChange={() => setBodyViewType('JSON')}
              />
              <span>JSON</span>
            </label>
            <label>
              <input
                type="radio"
                name="http-body-view-type"
                value="PARAMETERS"
                checked={bodyViewType === 'PARAMETERS'}
                onChange={() => setBodyViewType('PARAMETERS')}
              />
              <span>参数</span>
            </label>
          </div>
          {bodyViewType === 'JSON' ? (
            <>
              <InlineScriptEditor
                value={bodyJson}
                onChange={updateBodyJson}
                minHeight={300}
                language="json"
                languageLabel="JSON"
                codeLabel="Code"
                modalTitle="请求体 JSON 编辑器"
              />
              {bodyError ? <p className="inline-error">{bodyError}</p> : null}
            </>
          ) : (
            <div className="http-body-parameters">
              <ParameterRows
                items={bodyParameterRows}
                keyLabel="参数名"
                valueLabel="参数值"
                variableOptions={variableOptions}
                onChange={updateBodyParameter}
                onDelete={deleteBodyParameter}
              />
              <button type="button" className="add-parameter-button" onClick={addBodyParameter}>
                <PlusCircle size={15} />
                添加参数
              </button>
            </div>
          )}
        </div>
      </WorkflowCollapsibleSection>
    </div>
  )
}

function JdbcTaskInspector({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const hasNestedRequest = isRecord(inputParameters.jdbc_request)
  const request = hasNestedRequest ? inputParameters.jdbc_request as Record<string, unknown> : inputParameters
  const parameters = isRecord(request.parameters) ? request.parameters : {}
  const parameterRows = Object.entries(parameters).map(([key, value]) => ({ key, value: valueText(value) }))

  const updateJdbcRequest = (next: Record<string, unknown>) => {
    const nextRequest = { ...request, ...next }
    onChange({
      ...task,
      inputParameters: hasNestedRequest ? { ...inputParameters, jdbc_request: nextRequest } : nextRequest,
    })
  }

  const updateParameters = (rows: Array<{ key: string; value: string }>) => {
    updateJdbcRequest({ parameters: Object.fromEntries(rows.map((item) => [item.key, item.value])) })
  }

  const updateParameter = (index: number, next: { key: string; value: string }) => {
    const rows = [...parameterRows]
    rows[index] = next
    updateParameters(rows)
  }

  const addParameter = () => {
    let index = parameterRows.length + 1
    let key = `parameter_${index}`
    while (Object.prototype.hasOwnProperty.call(parameters, key)) {
      index += 1
      key = `parameter_${index}`
    }
    updateParameters([...parameterRows, { key, value: '' }])
  }

  return (
    <div className="workflow-form-panel jdbc-task-inspector">
      <TaskPanelHeader task={task} label="JDBC" onChange={onChange} />

      <WorkflowCollapsibleSection title="连接信息">
        <div className="inspector-form-grid jdbc-connection-grid">
          <label className="inspector-field jdbc-url-field">
            <span>JDBC URL <strong>*</strong></span>
            <VariableExpressionInput value={String(request.url ?? '')} options={variableOptions} onChange={(value) => updateJdbcRequest({ url: value })} />
          </label>
          <label className="inspector-field">
            <span>数据库厂商</span>
            <select value={String(request.vendor ?? '')} onChange={(event) => updateJdbcRequest({ vendor: event.target.value || undefined })}>
              <option value="">自定义驱动</option>
              {['MYSQL', 'POSTGRES', 'SQLSERVER', 'ORACLE', 'OPENGAUSS', 'KINGBASE'].map((vendor) => (
                <option value={vendor} key={vendor}>{vendor}</option>
              ))}
            </select>
          </label>
          <label className="inspector-field">
            <span>用户名</span>
            <VariableExpressionInput value={String(request.username ?? '')} options={variableOptions} onChange={(value) => updateJdbcRequest({ username: value })} />
          </label>
          <label className="inspector-field">
            <span>密码</span>
            <VariableExpressionInput value={String(request.password ?? '')} options={variableOptions} onChange={(value) => updateJdbcRequest({ password: value })} />
          </label>
          <label className="inspector-field jdbc-driver-field">
            <span>driverClassName</span>
            <input value={String(request.driverClassName ?? '')} onChange={(event) => updateJdbcRequest({ driverClassName: event.target.value })} placeholder="选择厂商后可留空" />
          </label>
        </div>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="SQL 语句">
        <div className="jdbc-operation-options" role="radiogroup" aria-label="JDBC 操作类型">
          {['QUERY', 'UPDATE'].map((operation) => (
            <label key={operation}>
              <input
                type="radio"
                name={`jdbc-operation-${task.taskReferenceName}`}
                value={operation}
                checked={String(request.operation ?? 'QUERY').toUpperCase() === operation}
                onChange={() => updateJdbcRequest({ operation })}
              />
              <span>{operation}</span>
            </label>
          ))}
        </div>
        <InlineScriptEditor
          value={String(request.sql ?? '')}
          onChange={(sql) => updateJdbcRequest({ sql })}
          minHeight={280}
          language="sql"
          languageLabel="SQL"
          codeLabel="Statement"
          modalTitle="JDBC SQL 编辑器"
        />
        <p className="field-help">使用 <code>:参数名</code> 绑定下方命名参数；UPDATE 同时适用于 INSERT、UPDATE 和 DELETE。</p>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="命名参数">
        <ParameterRows
          items={parameterRows}
          keyLabel="参数名"
          valueLabel="映射值"
          variableOptions={variableOptions}
          onChange={updateParameter}
          onDelete={(index) => updateParameters(parameterRows.filter((_, itemIndex) => itemIndex !== index))}
        />
        <button type="button" className="add-parameter-button" onClick={addParameter}>
          <PlusCircle size={15} />
          添加命名参数
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="高级设置" defaultOpen={false}>
        <div className="inspector-form-grid">
          <label className="inspector-field">
            <span>connectionTimeOut (ms)</span>
            <input value={String(request.connectionTimeOut ?? '')} onChange={(event) => updateJdbcRequest({ connectionTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
          <label className="inspector-field">
            <span>queryTimeOut (ms)</span>
            <input value={String(request.queryTimeOut ?? '')} onChange={(event) => updateJdbcRequest({ queryTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
        </div>
      </WorkflowCollapsibleSection>
    </div>
  )
}

function WebServiceTaskInspector({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const headers = isRecord(inputParameters.headers) ? inputParameters.headers : {}
  const templateParameters = isRecord(inputParameters.templateParameters) ? inputParameters.templateParameters : {}
  const headerRows = Object.entries(headers).map(([key, value]) => ({ key, value: valueText(value) }))
  const templateParameterRows = Object.entries(templateParameters).map(([key, value]) => ({ key, value: valueText(value) }))

  const updateInputParameters = (next: Record<string, unknown>) => {
    onChange({ ...task, inputParameters: { ...inputParameters, ...next } })
  }

  const updateRows = (
    field: 'headers' | 'templateParameters',
    rows: Array<{ key: string; value: string }>,
  ) => {
    updateInputParameters({ [field]: Object.fromEntries(rows.map((item) => [item.key, item.value])) })
  }

  const addRow = (field: 'headers' | 'templateParameters') => {
    const rows = field === 'headers' ? headerRows : templateParameterRows
    const values = field === 'headers' ? headers : templateParameters
    let index = rows.length + 1
    let key = field === 'headers' ? `header_${index}` : `parameter_${index}`
    if (field === 'templateParameters' && rows.length === 0) key = 'payload'
    while (Object.prototype.hasOwnProperty.call(values, key)) {
      index += 1
      key = field === 'headers' ? `header_${index}` : `parameter_${index}`
    }
    updateRows(field, [...rows, { key, value: '' }])
  }

  return (
    <div className="workflow-form-panel webservice-task-inspector">
      <TaskPanelHeader task={task} label="WEBSERVICE" onChange={onChange} />

      <WorkflowCollapsibleSection title="请求详情">
        <div className="inspector-form-grid webservice-request-grid">
          <label className="inspector-field webservice-uri-field">
            <span>服务地址 <strong>*</strong></span>
            <VariableExpressionInput value={String(inputParameters.uri ?? '')} options={variableOptions} onChange={(uri) => updateInputParameters({ uri })} />
          </label>
          <label className="inspector-field">
            <span>Content-Type</span>
            <input value={String(inputParameters.contentType ?? '')} onChange={(event) => updateInputParameters({ contentType: event.target.value })} placeholder="text/xml;charset=UTF-8" />
          </label>
          <label className="inspector-field">
            <span>connectionTimeOut (ms)</span>
            <input value={String(inputParameters.connectionTimeOut ?? '')} onChange={(event) => updateInputParameters({ connectionTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
          <label className="inspector-field">
            <span>readTimeOut (ms)</span>
            <input value={String(inputParameters.readTimeOut ?? '')} onChange={(event) => updateInputParameters({ readTimeOut: numberOrUndefined(event.target.value) ?? event.target.value })} />
          </label>
        </div>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="请求头">
        <ParameterRows
          items={headerRows}
          keyLabel="Header"
          valueLabel="映射值"
          variableOptions={variableOptions}
          onChange={(index, next) => {
            const rows = [...headerRows]
            rows[index] = next
            updateRows('headers', rows)
          }}
          onDelete={(index) => updateRows('headers', headerRows.filter((_, itemIndex) => itemIndex !== index))}
        />
        <button type="button" className="add-parameter-button" onClick={() => addRow('headers')}>
          <PlusCircle size={15} />
          添加请求头
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="模板参数">
        <ParameterRows
          items={templateParameterRows}
          keyLabel="参数名"
          valueLabel="映射值"
          variableOptions={variableOptions}
          onChange={(index, next) => {
            const rows = [...templateParameterRows]
            rows[index] = next
            updateRows('templateParameters', rows)
          }}
          onDelete={(index) => updateRows('templateParameters', templateParameterRows.filter((_, itemIndex) => itemIndex !== index))}
        />
        <button type="button" className="add-parameter-button" onClick={() => addRow('templateParameters')}>
          <PlusCircle size={15} />
          添加模板参数
        </button>
        <p className="field-help">映射值先解析工作流表达式；SOAP 模板通过 <code>{'${$.payload.xxx}'}</code> 读取。参数名不能使用 <code>workflow</code> 或任务引用名。</p>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="SOAP 模板">
        <InlineScriptEditor
          value={String(inputParameters.soapTemplate ?? '')}
          onChange={(soapTemplate) => updateInputParameters({ soapTemplate })}
          minHeight={360}
          language="xml"
          languageLabel="XML / SOAP"
          codeLabel="soapTemplate"
          modalTitle="SOAP XML 模板编辑器"
        />
        <p className="field-help">保留兼容语法 <code>{'${workflow.input.xxx}'}</code>；数组、重复标签或需 XML 转义的数据建议由前置 INLINE 生成完整 XML。</p>
      </WorkflowCollapsibleSection>
    </div>
  )
}

function InlineScriptEditor({
  value,
  onChange,
  minHeight = 360,
  language = 'javascript',
  languageLabel = 'ECMASCRIPT',
  codeLabel = 'Code',
  modalTitle = 'Code Editor',
}: {
  value: string
  onChange: (value: string) => void
  minHeight?: number
  language?: string
  languageLabel?: string
  codeLabel?: string
  modalTitle?: string
}) {
  const [expanded, setExpanded] = useState(false)
  const [copied, setCopied] = useState(false)
  const [editorHeight, setEditorHeight] = useState(minHeight)
  const copyTimerRef = useRef<number | undefined>(undefined)

  const copyCode = async () => {
    const legacyCopy = () => {
      const textarea = document.createElement('textarea')
      textarea.value = value
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      const succeeded = document.execCommand('copy')
      textarea.remove()
      return succeeded
    }

    try {
      if (!navigator.clipboard) throw new Error('Clipboard API unavailable')
      await navigator.clipboard.writeText(value)
    } catch {
      legacyCopy()
    }
    setCopied(true)
    window.clearTimeout(copyTimerRef.current)
    copyTimerRef.current = window.setTimeout(() => setCopied(false), 2200)
  }

  const handleMount: OnMount = (editor) => {
    const updateHeight = () => {
      const minimumHeight = expanded ? 520 : minHeight
      setEditorHeight(Math.max(minimumHeight, Math.ceil(editor.getContentHeight())))
    }

    editor.onDidContentSizeChange(updateHeight)
    updateHeight()
  }

  useEffect(() => {
    return () => window.clearTimeout(copyTimerRef.current)
  }, [])

  useEffect(() => {
    if (!expanded) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setExpanded(false)
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [expanded])

  const editor = (
    <div className={`conductor-code-input ${expanded ? 'expanded' : ''}`}>
      <div className="conductor-code-label" aria-hidden="true">
        <span>{codeLabel}</span>
        <strong>{languageLabel}</strong>
      </div>
      <Editor
        height={editorHeight}
        language={language}
        value={value}
        onChange={(nextValue) => onChange(nextValue ?? '')}
        onMount={handleMount}
        options={{
          automaticLayout: true,
          contextmenu: true,
          folding: false,
          fontFamily: 'SFMono-Regular, Consolas, Liberation Mono, monospace',
          fontSize: 13,
          glyphMargin: false,
          lineDecorationsWidth: 8,
          lineHeight: 22,
          lineNumbers: 'on',
          lineNumbersMinChars: 3,
          minimap: { enabled: false },
          overviewRulerBorder: false,
          overviewRulerLanes: 0,
          padding: { top: 16, bottom: 16 },
          renderLineHighlight: 'none',
          scrollBeyondLastLine: false,
          scrollbar: {
            alwaysConsumeMouseWheel: false,
            horizontal: 'hidden',
            vertical: expanded ? 'auto' : 'hidden',
          },
          tabSize: 2,
          wordWrap: 'on',
          wrappingIndent: 'same',
          wrappingStrategy: 'advanced',
        }}
      />
      <div className="conductor-code-actions">
        <button
          type="button"
          title={expanded ? '收起代码编辑器' : '展开代码编辑器'}
          aria-label={expanded ? '收起代码编辑器' : '展开代码编辑器'}
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? <ArrowsInSimple size={20} /> : <ArrowsOutSimple size={20} />}
        </button>
        <button type="button" title="复制代码" aria-label="复制代码" onClick={() => void copyCode()}>
          <Copy size={20} />
        </button>
      </div>
    </div>
  )

  return (
    <>
      {expanded
        ? createPortal(
            <div className="code-editor-modal-backdrop" onMouseDown={() => setExpanded(false)}>
              <section className="code-editor-modal" role="dialog" aria-modal="true" aria-labelledby="code-editor-modal-title" onMouseDown={(event) => event.stopPropagation()}>
                <header>
                  <h2 id="code-editor-modal-title">{modalTitle}</h2>
                  <button type="button" title="关闭" aria-label="关闭代码编辑器" onClick={() => setExpanded(false)}>
                    <X size={22} />
                  </button>
                </header>
                <div className="code-editor-modal-content">{editor}</div>
              </section>
            </div>,
            document.body,
          )
        : editor}
      {copied ? <div className="code-copy-toast" role="status">已复制到剪贴板</div> : null}
    </>
  )
}

function spiInputSource(value: unknown) {
  return typeof value === 'string' ? value : prettyJson(isRecord(value) ? value : {})
}

function SpiTaskInspector({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const remoteExtension = typeof inputParameters.remoteExtension === 'string'
    ? inputParameters.remoteExtension
    : valueText(inputParameters.remoteExtension)
  const extensionMode = inputParameters.extensionMode === 'PROJECT' ? 'PROJECT' : 'ONLINE'
  const className = typeof inputParameters.className === 'string' ? inputParameters.className : ''
  const [spiInputText, setSpiInputText] = useState(() => spiInputSource(inputParameters.spiInput))
  const [javaSource, setJavaSource] = useState(() => (
    typeof inputParameters.sourceCode === 'string'
      ? inputParameters.sourceCode
      : spiJavaTemplate(className || 'com.helianhealth.agent.extension.CustomSpiTaskExtension')
  ))
  const [inputError, setInputError] = useState('')

  const updateParameters = (changes: Record<string, unknown>) => {
    onChange({
      ...task,
      inputParameters: {
        ...inputParameters,
        ...changes,
      },
    })
  }

  const updateSpiInput = (nextSource: string) => {
    setSpiInputText(nextSource)
    const expression = nextSource.trim()
    if (expression.startsWith('${') && expression.endsWith('}')) {
      setInputError('')
      updateParameters({ spiInput: expression })
      return
    }

    try {
      const parsed = JSON.parse(nextSource) as unknown
      if (!isRecord(parsed)) throw new Error('spiInput 必须是 JSON Map 或返回 Map 的 ${...} 表达式')
      setInputError('')
      updateParameters({ spiInput: parsed })
    } catch (error) {
      setInputError(error instanceof Error ? error.message : 'spiInput 格式不正确')
    }
  }

  const updateJavaSource = (value: string) => {
    setJavaSource(value)
    updateParameters({ sourceCode: value })
  }

  return (
    <div className="workflow-form-panel inline-task-inspector spi-task-inspector">
      <TaskPanelHeader task={task} label="SPI 扩展" onChange={onChange} />

      <WorkflowCollapsibleSection title="SPI 实现">
        <label className="inspector-field">
          <span>扩展名称（remoteExtension） <strong>*</strong></span>
          <VariableExpressionInput
            value={remoteExtension}
            options={variableOptions}
            onChange={(value) => updateParameters({ remoteExtension: value })}
          />
          <small>用于注入和路由 ExtensionLoader；也支持返回扩展名的 ${'{...}'} 表达式。</small>
        </label>

        <div className="switch-evaluator-heading spi-extension-mode-heading">
          <span>扩展方式</span>
          <div className="switch-evaluator-options" role="group" aria-label="SPI 扩展方式">
            <button
              type="button"
              className={extensionMode === 'ONLINE' ? 'active' : ''}
              onClick={() => updateParameters({ extensionMode: 'ONLINE', sourceCode: javaSource })}
            >
              在线代码
            </button>
            <button
              type="button"
              className={extensionMode === 'PROJECT' ? 'active' : ''}
              onClick={() => updateParameters({ extensionMode: 'PROJECT', sourceCode: undefined })}
            >
              项目实现
            </button>
          </div>
        </div>

        <label className="inspector-field">
          <span>实现类全名 {extensionMode === 'ONLINE' ? <strong>*</strong> : null}</span>
          <input
            value={className}
            spellCheck={false}
            placeholder="com.example.CustomRemoteInvoker"
            onChange={(event) => updateParameters({ className: event.target.value })}
          />
          <small>
            {extensionMode === 'ONLINE'
              ? '必须与下面 Java 源码中的 package 和 public 类名一致。'
              : '可留空以直接调用项目内已注册的同名扩展；填写后会从 ClassPath 注入。'}
          </small>
        </label>

        {extensionMode === 'ONLINE' ? (
          <InlineScriptEditor
            value={javaSource}
            onChange={updateJavaSource}
            minHeight={440}
            language="java"
            languageLabel="JAVA 17"
            codeLabel="Java Source"
            modalTitle="SPI Java Source Editor"
          />
        ) : null}
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="SPI 入参">
        <InlineScriptEditor
          value={spiInputText}
          onChange={updateSpiInput}
          minHeight={220}
          language="json"
          languageLabel="JSON / EXPRESSION"
          codeLabel="spiInput"
          modalTitle="SPI Input Editor"
        />
        {inputError
          ? <p className="inline-error">{inputError}</p>
          : <p className="field-help">输入 JSON Map，或直接使用返回 Map 的表达式，例如 <code>${'{workflow.input}'}</code>。</p>}
      </WorkflowCollapsibleSection>
    </div>
  )
}

function InlineTaskInspector({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const rawExpression = String(inputParameters.expression ?? '')
  const expression = formatInlineScript(rawExpression)
  const evaluatorType = 'graaljs'
  const scriptParameters = Object.entries(inputParameters)
    .filter(([key]) => key !== 'expression' && key !== 'evaluatorType')
    .map(([key, value]) => ({ key, value: valueText(value) }))

  const updateInputParameters = (next: Record<string, unknown>) => {
    onChange({
      ...task,
      inputParameters: {
        evaluatorType,
        ...next,
        expression,
      },
    })
  }

  const updateExpression = (nextExpression: string) => {
    onChange({
      ...task,
      inputParameters: {
        evaluatorType,
        ...Object.fromEntries(scriptParameters.map((item) => [item.key, item.value])),
        expression: nextExpression,
      },
    })
  }

  const updateScriptParameter = (index: number, next: { key: string; value: string }) => {
    const items = [...scriptParameters]
    items[index] = next
    updateInputParameters(Object.fromEntries(items.map((item) => [item.key, item.value])))
  }

  const addScriptParameter = () => {
    const key = scriptParameters.length ? `parameter_${scriptParameters.length + 1}` : 'payload'
    updateInputParameters({
      ...Object.fromEntries(scriptParameters.map((item) => [item.key, item.value])),
      [key]: '',
    })
  }

  const deleteScriptParameter = (index: number) => {
    updateInputParameters(Object.fromEntries(scriptParameters.filter((_, itemIndex) => itemIndex !== index).map((item) => [item.key, item.value])))
  }

  return (
    <div className="workflow-form-panel inline-task-inspector">
      <TaskPanelHeader task={task} label="内联脚本" onChange={onChange} />

      <WorkflowCollapsibleSection title="脚本入参">
        <div className="inline-script-parameters">
          {scriptParameters.map((item, index) => (
            <div className="parameter-row two-column inline-script-parameter-row" key={index}>
              <label>
                <span>参数名</span>
                <div className="inline-key-input">
                  <input value={item.key} onChange={(event) => updateScriptParameter(index, { ...item, key: event.target.value })} />
                  <button type="button" className="inline-clear-key" title="清空参数名" onClick={() => updateScriptParameter(index, { ...item, key: '' })}>
                    <XCircle size={16} />
                  </button>
                </div>
              </label>
              <label>
                <span>映射值</span>
                <VariableExpressionInput
                  value={item.value}
                  options={variableOptions}
                  onChange={(value) => updateScriptParameter(index, { ...item, value })}
                />
              </label>
              <button type="button" className="inline-delete-parameter" title="删除参数" onClick={() => deleteScriptParameter(index)}>
                <Trash size={19} />
              </button>
            </div>
          ))}
          {!scriptParameters.length ? <p className="inspector-empty">暂未配置脚本参数</p> : null}
        </div>
        <button type="button" className="inline-add-parameter" onClick={addScriptParameter}>
          <PlusCircle size={16} />
          添加脚本入参
        </button>
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="脚本代码">
        <InlineScriptEditor value={expression} onChange={updateExpression} />
      </WorkflowCollapsibleSection>

    </div>
  )
}

export function CodeInspector({ workflow, onChange }: { workflow: WorkflowDef; onChange: (workflow: WorkflowDef) => void }) {
  const [source, setSource] = useState(prettyJson(workflow))
  const [copied, setCopied] = useState(false)
  const copyTimerRef = useRef<number | undefined>(undefined)
  const editorRefreshFrameRef = useRef<number | undefined>(undefined)
  const editorRefreshTimerRef = useRef<number | undefined>(undefined)
  const editorDecorationCleanupRef = useRef<(() => void) | undefined>(undefined)

  const handleSourceEditorBeforeMount: BeforeMount = (monaco) => {
    // @monaco-editor/react creates the editor before applying its theme prop.
    // Define and apply the source theme first so both the editor and its minimap
    // never flash the previous (possibly dark) Monaco theme while mounting.
    monaco.editor.defineTheme(SOURCE_EDITOR_THEME, {
      base: 'vs',
      inherit: true,
      rules: [],
      colors: {
        'editor.background': '#FFFFFF',
        'editorGutter.background': '#FFFFFF',
        'minimap.background': '#FFFFFF',
        'minimapSlider.background': '#CBD5E166',
        'minimapSlider.hoverBackground': '#94A3B880',
        'minimapSlider.activeBackground': '#64748B99',
      },
    })
    monaco.editor.setTheme(SOURCE_EDITOR_THEME)
  }

  const updateSource = (nextSource: string) => {
    setSource(nextSource)
    try {
      const parsed = JSON.parse(nextSource) as WorkflowDef
      if (!parsed.name || !Array.isArray(parsed.tasks)) throw new Error('工作流必须包含 name 和 tasks 数组')
      onChange(parsed)
    } catch {
      // Monaco keeps the invalid draft visible until it becomes valid JSON again.
    }
  }

  const copySource = async () => {
    const legacyCopy = () => {
      const textarea = document.createElement('textarea')
      textarea.value = source
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      textarea.remove()
    }

    try {
      if (!navigator.clipboard) throw new Error('Clipboard API unavailable')
      await navigator.clipboard.writeText(source)
    } catch {
      legacyCopy()
    }
    setCopied(true)
    window.clearTimeout(copyTimerRef.current)
    copyTimerRef.current = window.setTimeout(() => setCopied(false), 2000)
  }

  const handleSourceEditorMount: OnMount = (editor, monaco) => {
    const model = editor.getModel()
    if (model) {
      monaco.editor.setModelLanguage(model, 'json')
    }

    const decorations = editor.createDecorationsCollection()
    const applyJsonDecorations = () => {
      const currentModel = editor.getModel()
      if (!currentModel || currentModel.isDisposed()) return
      const nextDecorations: Array<Parameters<typeof decorations.set>[0][number]> = []
      const tokenPattern = /"(?:\\.|[^"\\])*"\s*:|"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\b(?:true|false|null)\b/g

      for (let lineNumber = 1; lineNumber <= currentModel.getLineCount(); lineNumber += 1) {
        const line = currentModel.getLineContent(lineNumber)
        for (const match of line.matchAll(tokenPattern)) {
          const token = match[0]
          const trimmedToken = token.trimEnd()
          const className = trimmedToken.endsWith(':')
            ? 'workflow-json-key'
            : token.startsWith('"')
              ? 'workflow-json-string'
              : /^(?:true|false|null)$/.test(token)
                ? 'workflow-json-keyword'
                : 'workflow-json-number'
          nextDecorations.push({
            range: new monaco.Range(lineNumber, match.index + 1, lineNumber, match.index + token.length + 1),
            options: { inlineClassName: className },
          })
        }
      }
      decorations.set(nextDecorations)
    }

    applyJsonDecorations()
    const contentChangeDisposable = model?.onDidChangeContent(applyJsonDecorations)
    editorDecorationCleanupRef.current?.()
    editorDecorationCleanupRef.current = () => {
      contentChangeDisposable?.dispose()
      decorations.clear()
    }

    const refreshEditor = () => {
      const currentModel = editor.getModel()
      if (!currentModel || currentModel.isDisposed()) return
      editor.layout()
      editor.render(true)
    }

    editor.focus()
    refreshEditor()
    editorRefreshFrameRef.current = window.requestAnimationFrame(refreshEditor)
    editorRefreshTimerRef.current = window.setTimeout(refreshEditor, 120)
  }

  useEffect(() => () => {
    window.clearTimeout(copyTimerRef.current)
    window.clearTimeout(editorRefreshTimerRef.current)
    if (editorRefreshFrameRef.current != null) window.cancelAnimationFrame(editorRefreshFrameRef.current)
    editorDecorationCleanupRef.current?.()
  }, [])

  return (
    <div className="workflow-source-editor">
      <div className="workflow-source-toolbar">
        <button type="button" title={copied ? '已复制' : '复制源码'} aria-label="复制源码" onClick={() => void copySource()}>
          <Copy size={18} />
        </button>
      </div>
      <div className="workflow-source-editor-frame">
        <Editor
          height="100%"
          width="100%"
          theme={SOURCE_EDITOR_THEME}
          loading={<div className="workflow-source-editor-loading">正在加载源码…</div>}
          language="json"
          path="workflow-definition.json"
          value={source}
          beforeMount={handleSourceEditorBeforeMount}
          onChange={(nextSource) => updateSource(nextSource ?? '')}
          onMount={handleSourceEditorMount}
          options={{
            automaticLayout: true,
            bracketPairColorization: { enabled: true },
            folding: true,
            fontFamily: 'SFMono-Regular, Consolas, Liberation Mono, monospace',
            fontSize: 13,
            formatOnPaste: true,
            glyphMargin: false,
            lineHeight: 20,
            lineNumbers: 'on',
            lineNumbersMinChars: 3,
            minimap: { enabled: true, showSlider: 'always' },
            padding: { top: 4, bottom: 8 },
            renderLineHighlight: 'all',
            scrollBeyondLastLine: false,
            selectOnLineNumbers: true,
            tabSize: 2,
            wordWrap: 'off',
          }}
        />
      </div>
    </div>
  )
}
