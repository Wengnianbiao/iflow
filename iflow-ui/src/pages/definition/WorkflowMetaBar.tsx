import { useEffect, useRef, useState } from 'react'
import { useSelector } from '@xstate/react'
import { Link } from 'react-router-dom'
import { Menu, MenuItem } from '@mui/material'
import {
  ArrowClockwiseIcon,
  CaretDownIcon,
  CaretCircleDownIcon,
  DownloadSimpleIcon,
  FloppyDiskIcon,
  TagIcon,
  TrashIcon,
} from '@phosphor-icons/react'
import { downloadWorkflowDefinition } from '@/utils/workflowDraft'
import {
  hasWorkflowChanges,
  type VersionChoice,
  type WorkflowDefinitionActor,
} from './state'

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
    ...versions.map((version) => ({
      value: String(version),
      label: `版本 ${version}`,
      kind: 'version' as const,
      version,
    })),
    ...(showLatest
      ? [{
          value: 'latest',
          label: '最新版本',
          kind: 'latest' as const,
          version: undefined,
        }]
      : []),
  ]
  const selectedValue = latestSelected ? 'latest' : String(currentVersion ?? '')
  const displayValue = latestSelected
    ? '最新版本'
    : currentVersion != null
      ? `版本 ${currentVersion}`
      : '选择版本'
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
          <TagIcon size={16} weight="bold" />
        </span>
        <span>{displayValue}</span>
        {hasOptions ? (
          <CaretDownIcon className="version-headbar-chevron" size={18} weight="bold" />
        ) : null}
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

export function WorkflowMetaBar({ actor }: { actor: WorkflowDefinitionActor }) {
  const [saveMenuAnchor, setSaveMenuAnchor] = useState<HTMLElement | null>(null)
  const state = useSelector(actor, (snapshot) => snapshot)
  const {
    workflow,
    workflowRoute,
    workflowVersion,
    versions,
    isNewWorkflow,
    error,
  } = state.context
  const dirty = hasWorkflowChanges(state.context)
  const preparingNewVersion = state.matches('ready.lifecycle.preparingNewVersion')
  const saving = preparingNewVersion || state.matches('ready.lifecycle.saving') || state.matches('ready.lifecycle.savingBeforeLeave')
  const deleting = state.matches('ready.lifecycle.deleting')
  const saveDisabled = !workflow || !dirty || !state.matches('ready.lifecycle.editing')

  return (
    <header className="workflow-editor-header">
      <div className="workflow-title-block">
        <div className="workflow-breadcrumbs">
          <Link to="/workflowDef">工作流配置</Link>
          <span>/</span>
          <span>{workflow?.name || workflow?.route || workflowRoute}</span>
        </div>
        <div className="workflow-title-row">
          <h1>{workflow?.name || workflow?.route || workflowRoute}</h1>
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
          onChange={(version) => actor.send({ type: 'REQUEST_VERSION_CHANGE', version })}
        />
        <button
          type="button"
          className="danger-action"
          disabled={!workflow || isNewWorkflow || saving || deleting}
          onClick={() => actor.send({ type: 'REQUEST_DELETE' })}
        >
          <TrashIcon size={20} />
          删除
        </button>
        <button
          type="button"
          className="ghost-action"
          disabled={!dirty || saving}
          onClick={() => actor.send({ type: 'REQUEST_RESET' })}
        >
          <ArrowClockwiseIcon size={20} />
          重置
        </button>
        <button
          type="button"
          className="ghost-action"
          disabled={!workflow}
          onClick={() => workflow && downloadWorkflowDefinition(workflow)}
        >
          <DownloadSimpleIcon size={20} />
          下载
        </button>
        <div className={`workflow-save-button-group ${!isNewWorkflow ? 'has-save-menu' : ''}`}>
          <button
            type="button"
            className="save-action"
            disabled={saveDisabled}
            onClick={() => actor.send({ type: 'REQUEST_SAVE' })}
          >
            <FloppyDiskIcon size={20} />
            {preparingNewVersion ? '准备中...' : saving ? '保存中...' : '保存'}
          </button>
          {!isNewWorkflow ? <button
            type="button"
            className="save-action save-menu-toggle"
            disabled={saveDisabled}
            aria-label="更多保存选项"
            aria-haspopup="menu"
            aria-expanded={Boolean(saveMenuAnchor)}
            aria-controls={saveMenuAnchor ? 'workflow-save-menu' : undefined}
            onClick={(event) => setSaveMenuAnchor(event.currentTarget)}
          >
            <CaretCircleDownIcon size={18} />
          </button> : null}
        </div>
        <Menu
          id="workflow-save-menu"
          anchorEl={saveMenuAnchor}
          open={Boolean(saveMenuAnchor)}
          onClose={() => setSaveMenuAnchor(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          slotProps={{ paper: { sx: {
            minWidth: 156,
            border: '1px solid #1976d2',
            borderRadius: '6px',
            boxShadow: '4px 4px 10px rgba(89, 89, 89, 0.41)',
          } } }}
        >
          <MenuItem
            disabled={saveDisabled}
            sx={{ minHeight: 34, fontSize: 14, fontWeight: 500 }}
            onClick={() => {
              setSaveMenuAnchor(null)
              actor.send({ type: 'REQUEST_SAVE', saveCurrentVersion: true })
            }}
          >
            保存为当前版本
          </MenuItem>
        </Menu>
      </div>

      {error ? <div className="workflow-toast error">{error}</div> : null}
    </header>
  )
}
