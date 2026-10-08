import { Component, useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useSelector } from '@xstate/react'
import { DiffEditor, type MonacoDiffEditor } from '@monaco-editor/react'
import {
  ArrowClockwiseIcon,
  CaretDownIcon,
  CaretUpIcon,
  FloppyDiskIcon,
  TagIcon,
  TrashIcon,
  WarningCircleIcon,
  XIcon,
} from '@phosphor-icons/react'
import type { WorkflowDefinitionActor } from '../state'

function prettyJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2)
}

function isMonacoDisposedError(error: unknown) {
  const message = error instanceof Error ? error.message : String(error)
  return message.includes('InstantiationService has been disposed')
}

class MonacoDisposeBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null }

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  render() {
    if (!this.state.error) return this.props.children
    if (isMonacoDisposedError(this.state.error)) return null
    throw this.state.error
  }
}

function SaveWorkflowDialog({
  actor,
  open,
  saving,
}: {
  actor: WorkflowDefinitionActor
  open: boolean
  saving: boolean
}) {
  const { workflow, baseline, isNewWorkflow, serverProblems } = useSelector(
    actor,
    (state) => state.context,
  )
  const [changeCount, setChangeCount] = useState(0)
  const editorRef = useRef<MonacoDiffEditor | null>(null)
  const updateRef = useRef<{ dispose: () => void } | null>(null)

  const mountEditor = useCallback((editor: MonacoDiffEditor) => {
    try {
      const model = editor.getModel()
      if (model) {
        model.original.setValue(isNewWorkflow ? '{}' : baseline ? prettyJson(baseline) : '{}')
        model.modified.setValue(workflow ? prettyJson(workflow) : '{}')
      }
      editorRef.current = editor
      updateRef.current?.dispose()
      const updateChangeCount = () => {
        try {
          setChangeCount(editor.getLineChanges()?.length ?? 0)
        } catch (error) {
          if (!isMonacoDisposedError(error)) throw error
        }
      }
      updateRef.current = editor.onDidUpdateDiff(updateChangeCount)
      updateChangeCount()
    } catch (error) {
      if (!isMonacoDisposedError(error)) throw error
    }
  }, [baseline, isNewWorkflow, workflow])

  useEffect(() => () => {
    updateRef.current?.dispose()
    updateRef.current = null
    editorRef.current = null
  }, [])

  useEffect(() => {
    if (open) return
    updateRef.current?.dispose()
    updateRef.current = null
    editorRef.current = null
    setChangeCount(0)
  }, [open])

  if (!open || !workflow) return null

  return createPortal(
    <div className="workflow-save-diff-backdrop" role="presentation">
      <section
        className="workflow-save-diff-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="workflow-save-diff-title"
      >
        <header>
          <div>
            <h2 id="workflow-save-diff-title">确认保存工作流</h2>
            <p>{!isNewWorkflow && baseline && workflow.route !== baseline.route
              ? '路由已修改，确认后将创建新工作流，原工作流保留。'
              : '请确认当前版本与待保存版本之间的差异。'}</p>
          </div>
          <button
            type="button"
            aria-label="关闭保存确认"
            title="关闭"
            disabled={saving}
            onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}
          >
            <XIcon size={20} />
          </button>
        </header>

        <div className="workflow-save-diff-labels">
          <span>当前已保存定义</span>
          <div className="workflow-save-diff-modified-label">
            <span>待保存定义</span>
            <div className="workflow-save-diff-navigation">
              <small>{changeCount > 0 ? `共 ${changeCount} 处变更` : '无变更'}</small>
              <button
                type="button"
                disabled={changeCount === 0}
                title="上一处变更"
                aria-label="上一处变更"
                onClick={() => editorRef.current?.goToDiff('previous')}
              >
                <CaretUpIcon size={15} weight="bold" />
                上一处
              </button>
              <button
                type="button"
                disabled={changeCount === 0}
                title="下一处变更"
                aria-label="下一处变更"
                onClick={() => editorRef.current?.goToDiff('next')}
              >
                <CaretDownIcon size={15} weight="bold" />
                下一处
              </button>
            </div>
          </div>
        </div>

        <div className="workflow-save-diff-editor">
          <MonacoDisposeBoundary>
            <DiffEditor
              height="100%"
              language="json"
              originalModelPath={`inmemory://workflow-save/${encodeURIComponent(workflow.route)}-${workflow.version}-original.json`}
              modifiedModelPath={`inmemory://workflow-save/${encodeURIComponent(workflow.route)}-${workflow.version}-modified.json`}
              onMount={mountEditor}
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
          </MonacoDisposeBoundary>
        </div>

        {serverProblems.length ? <div className="workflow-save-error" role="alert"><WarningCircleIcon size={18} /><div><strong>保存失败，草稿已保留</strong>{serverProblems.map((problem) => <p key={problem.id}>{problem.message}</p>)}</div></div> : null}
        <footer>
          <button
            type="button"
            className="diff-cancel-action"
            disabled={saving}
            onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}
          >
            <XIcon size={17} />
            取消
          </button>
          <button
            type="button"
            className="diff-confirm-action"
            disabled={saving}
            onClick={() => actor.send({ type: 'CONFIRM_SAVE' })}
          >
            <FloppyDiskIcon size={17} />
            {saving ? '保存中...' : '确认保存'}
          </button>
        </footer>
      </section>
    </div>,
    document.body,
  )
}

function ResetWorkflowDialog({
  actor,
  open,
}: {
  actor: WorkflowDefinitionActor
  open: boolean
}) {
  if (!open) return null

  return createPortal(
    <div
      className="workflow-delete-backdrop"
      role="presentation"
      onMouseDown={() => actor.send({ type: 'CANCEL_DIALOG' })}
    >
      <section
        className="workflow-delete-dialog workflow-confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="workflow-reset-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header>
          <span
            className="workflow-delete-dialog-icon workflow-confirm-dialog-icon"
            aria-hidden="true"
          >
            <ArrowClockwiseIcon size={23} weight="bold" />
          </span>
          <div>
            <h2 id="workflow-reset-title">重置工作流修改</h2>
            <p>恢复到上次保存的工作流定义。</p>
          </div>
          <button
            type="button"
            aria-label="关闭重置确认"
            title="关闭"
            onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}
          >
            <XIcon size={20} />
          </button>
        </header>
        <div className="workflow-delete-dialog-content">
          <p>当前所有未保存的画布、任务属性和工作流配置修改都将丢失。</p>
        </div>
        <footer>
          <button
            type="button"
            className="delete-cancel-action"
            onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}
          >
            取消
          </button>
          <button
            type="button"
            className="workflow-confirm-action"
            onClick={() => actor.send({ type: 'CONFIRM_RESET' })}
          >
            <ArrowClockwiseIcon size={18} weight="bold" />
            确认重置
          </button>
        </footer>
      </section>
    </div>,
    document.body,
  )
}

function VersionChangeDialog({
  actor,
  open,
}: {
  actor: WorkflowDefinitionActor
  open: boolean
}) {
  const pendingVersion = useSelector(actor, (state) => state.context.pendingVersion)
  if (!open) return null

  return createPortal(
    <div
      className="workflow-delete-backdrop"
      role="presentation"
      onMouseDown={() => actor.send({ type: 'CANCEL_DIALOG' })}
    >
      <section
        className="workflow-delete-dialog workflow-confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="workflow-version-change-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header>
          <span
            className="workflow-delete-dialog-icon workflow-confirm-dialog-icon"
            aria-hidden="true"
          >
            <TagIcon size={23} weight="bold" />
          </span>
          <div>
            <h2 id="workflow-version-change-title">切换工作流版本</h2>
            <p>当前工作流存在未保存的修改。</p>
          </div>
          <button
            type="button"
            aria-label="关闭版本切换确认"
            title="关闭"
            onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}
          >
            <XIcon size={20} />
          </button>
        </header>
        <div className="workflow-delete-dialog-content">
          <p>
            切换到{pendingVersion === 'latest' ? '最新版本' : `版本 ${pendingVersion ?? ''}`}
            后，当前未保存的修改将丢失。
          </p>
        </div>
        <footer>
          <button
            type="button"
            className="delete-cancel-action"
            onClick={() => actor.send({ type: 'CANCEL_DIALOG' })}
          >
            取消
          </button>
          <button
            type="button"
            className="workflow-confirm-action"
            onClick={() => actor.send({ type: 'CONFIRM_VERSION_CHANGE' })}
          >
            <TagIcon size={18} weight="bold" />
            继续切换
          </button>
        </footer>
      </section>
    </div>,
    document.body,
  )
}

function DeleteWorkflowDialog({
  actor,
  open,
  deleting,
}: {
  actor: WorkflowDefinitionActor
  open: boolean
  deleting: boolean
}) {
  const {
    baseline: workflow,
    deleteConfirmationValue,
    deleteError,
  } = useSelector(actor, (state) => state.context)

  if (!open || !workflow) return null

  const close = () => {
    if (!deleting) actor.send({ type: 'CANCEL_DIALOG' })
  }

  return createPortal(
    <div className="workflow-delete-backdrop" role="presentation" onMouseDown={close}>
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
            <TrashIcon size={23} weight="bold" />
          </span>
          <div>
            <h2 id="workflow-delete-title">删除工作流版本</h2>
            <p>此操作不可撤销，请谨慎确认。</p>
          </div>
          <button
            type="button"
            aria-label="关闭删除确认"
            title="关闭"
            disabled={deleting}
            onClick={close}
          >
            <XIcon size={20} />
          </button>
        </header>

        <div className="workflow-delete-dialog-content">
          <p id="workflow-delete-description">
            即将永久删除以下工作流定义，已经产生的调度记录不会受到影响。
          </p>
          <div className="workflow-delete-target">
            <div>
              <span>工作流名称</span>
              <strong>{workflow.name || workflow.route}</strong>
            </div>
            <div>
              <span>路由</span>
              <code>{workflow.route}</code>
            </div>
            <div>
              <span>版本</span>
              <strong>版本 {workflow.version}</strong>
            </div>
          </div>
          <label className="workflow-delete-confirm-field">
            <span>
              请输入路由 <code>{workflow.route}</code> 以确认删除
            </span>
            <input
              autoFocus
              value={deleteConfirmationValue}
              disabled={deleting}
              spellCheck={false}
              autoComplete="off"
              placeholder="输入完整路由"
              onChange={(event) => {
                actor.send({
                  type: 'UPDATE_DELETE_CONFIRMATION',
                  value: event.target.value,
                })
              }}
            />
          </label>
          {deleteError ? (
            <div className="workflow-delete-error">
              <WarningCircleIcon size={17} />
              {deleteError}
            </div>
          ) : null}
        </div>

        <footer>
          <button
            type="button"
            className="delete-cancel-action"
            disabled={deleting}
            onClick={close}
          >
            取消
          </button>
          <button
            type="button"
            className="delete-confirm-action"
            disabled={deleting || deleteConfirmationValue !== workflow.route}
            onClick={() => actor.send({ type: 'CONFIRM_DELETE' })}
          >
            <TrashIcon size={18} weight="bold" />
            {deleting ? '删除中...' : '确认删除'}
          </button>
        </footer>
      </section>
    </div>,
    document.body,
  )
}

export function WorkflowDialogs({ actor }: { actor: WorkflowDefinitionActor }) {
  const state = useSelector(actor, (snapshot) => snapshot)
  const saving = state.matches('ready.lifecycle.saving')
  const deleting = state.matches('ready.lifecycle.deleting')
  const saveOpen = state.matches('ready.lifecycle.confirmSave') || saving
  const resetOpen = state.matches('ready.lifecycle.confirmReset')
  const deleteOpen = state.matches('ready.lifecycle.confirmDelete') || deleting
  const versionOpen = state.matches('ready.lifecycle.confirmVersionChange')
  const anyDialogOpen = saveOpen || resetOpen || deleteOpen || versionOpen

  useEffect(() => {
    if (!anyDialogOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || saving || deleting) return
      actor.send({ type: 'CANCEL_DIALOG' })
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [actor, anyDialogOpen, deleting, saving])

  return (
    <>
      <ResetWorkflowDialog actor={actor} open={resetOpen} />
      <VersionChangeDialog actor={actor} open={versionOpen} />
      <SaveWorkflowDialog actor={actor} open={saveOpen} saving={saving} />
      <DeleteWorkflowDialog actor={actor} open={deleteOpen} deleting={deleting} />
    </>
  )
}
