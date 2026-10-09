import {
  ArrowLeftIcon,
  ArrowsInSimpleIcon,
  ArrowsOutSimpleIcon,
  CaretDownIcon,
  CaretRightIcon,
  CheckIcon,
  CopyIcon,
  DotsThreeIcon,
  MinusIcon,
  PlusIcon,
  PlusCircleIcon,
  TrashIcon,
  XIcon,
} from '@phosphor-icons/react'
import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { WorkflowCollapsibleSection, ParameterRows, VariableExpressionInput, valueText } from '../../../InspectorFields'
import { InlineScriptEditor } from '../InlineScriptEditor'
import { InlineHighlightedCode } from '@/components/flow/InlineScriptCode'
import { MappingExpressionInput } from './MappingExpressionInput'
import { TaskPanelHeader } from '../TaskPanelHeader'
import type { TaskFormProps } from '../types'
import {
  PARAM_TYPES, configChildren, withConfigChildren, configAtPath,
  configNodeLabel, loopElementLabel, scopeAliases, normalizeConfig, normalizeForSave, emptyConfig, objectTemplate,
  updateAtPath, removeAtPath, insertAfterPath, flattenDisplayConfigs, countMappingTargetFields,
  parseMappingConfigs, validateNode, sourcePathAfterTargetRename, aliasPathCorrection, mappingTypesForTargetType,
  constantInputText, parseConstantInput,
  type ParamMappingConfig, type ParamType, type MappingType, type ConfigPath, type DisplayConfigRow,
} from '@/tasks/mappingConfig'
import type { WorkflowTask } from '@/types/workflow'
import { ValidationField, useTaskFieldDraft } from '../../../../validation/ValidationScope'
import { jsonErrorMessage } from '../../../../validation/jsonErrorMessage'

type EditorView = 'JSON' | 'PARAMETERS'
type PayloadMode = 'expression' | 'parameters'

const PAYLOAD_KEY = 'payload'
const DEFAULT_PAYLOAD_EXPRESSION = '${workflow.input}'

type EditSession = {
  kind: 'root' | 'child' | 'edit' | 'copy'
  path?: ConfigPath
  value: ParamMappingConfig
  initialValue: string
}

function createEditSession(kind: EditSession['kind'], value: ParamMappingConfig, path?: ConfigPath): EditSession {
  const normalized = normalizeConfig(value)
  return { kind, path, value: normalized, initialValue: JSON.stringify(normalized) }
}

function configPathLabel(configs: ParamMappingConfig[], path: ConfigPath): string {
  return path.flatMap((_, index) => {
    const currentPath = path.slice(0, index + 1)
    const config = configAtPath(configs, path.slice(0, index + 1))
    return [`${configNodeLabel(configs, currentPath)}${config?.targetParamType === 'ARRAY' ? '[]' : ''}`]
  }).join('.')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

function paramTypeLabel(type: ParamType) {
  return type.charAt(0) + type.slice(1).toLowerCase()
}

function readParamMappingConfigs(task: WorkflowTask): ParamMappingConfig[] {
  const raw = task.inputParameters?.paramsConfig
  try { return parseMappingConfigs(raw ?? []) } catch { return [] }
}

function countParamMappingConfigs(configs: ParamMappingConfig[]): number {
  return countMappingTargetFields(configs)
}

function pathKey(path: ConfigPath) { return path.join('.') }

function mappingLabel(type: MappingType) {
  switch (type) {
    case 'NAME': return '字段映射'
    case 'CONSTANT': return '固定值'
    case 'EXPRESSION': return 'JS 表达式'
    case 'OBJECT': return '对象构建'
    case 'ARRAY_MAP': return '循环映射'
    case 'ARRAY_FIXED': return '固定长度'
  }
}

export function ParamMapTaskForm({ task, variableOptions, onChange }: TaskFormProps) {
  const inputParameters = task.inputParameters ?? {}
  const payload = inputParameters[PAYLOAD_KEY]
  const payloadMode: PayloadMode = isRecord(payload) ? 'parameters' : 'expression'
  const payloadParams = isRecord(payload)
    ? Object.entries(payload).map(([key, value]) => ({ key, value: valueText(value) }))
    : []
  const configs = useMemo(() => readParamMappingConfigs(task), [task.inputParameters?.paramsConfig])
  const storedConfigError = useMemo(() => {
    try { parseMappingConfigs(inputParameters.paramsConfig ?? []); return '' }
    catch (error) { return (error as Error).message }
  }, [inputParameters.paramsConfig])
  const [view, setView] = useState<EditorView>('PARAMETERS')
  const jsonDraft = useTaskFieldDraft('/inputParameters/paramsConfig', JSON.stringify(inputParameters.paramsConfig ?? [], null, 2))
  const visibleJsonError = jsonDraft.error || storedConfigError
  const [editSession, setEditSession] = useState<EditSession | null>(null)

  useEffect(() => {
    setView('PARAMETERS')
    setEditSession(null)
  }, [task.taskReferenceName])

  const commit = (nextPayload: unknown, nextConfigs: ParamMappingConfig[]) => {
    onChange({
      ...task,
      inputParameters: {
        [PAYLOAD_KEY]: nextPayload,
        paramsConfig: nextConfigs,
      },
    })
  }

  const commitConfigs = (nextConfigs: ParamMappingConfig[]) => {
    jsonDraft.clear()
    commit(Object.prototype.hasOwnProperty.call(inputParameters, PAYLOAD_KEY) ? payload : DEFAULT_PAYLOAD_EXPRESSION, nextConfigs)
  }

  const updateJson = (nextValue: string) => {
    try {
      const parsed = JSON.parse(nextValue)
      const nextConfigs = parseMappingConfigs(parsed)
      commit(Object.prototype.hasOwnProperty.call(inputParameters, PAYLOAD_KEY) ? payload : DEFAULT_PAYLOAD_EXPRESSION, nextConfigs)
      jsonDraft.applied(nextValue)
    } catch (error) {
      jsonDraft.invalid(nextValue, jsonErrorMessage(error, 'paramsConfig'))
    }
  }

  const canDiscardDraft = () => !editSession
    || JSON.stringify(editSession.value) === editSession.initialValue
    || window.confirm('当前字段有未应用的修改，是否放弃这些修改？')

  const closeFieldEditor = () => {
    if (!canDiscardDraft()) return false
    setEditSession(null)
    return true
  }

  const openSession = (session: EditSession) => {
    if (canDiscardDraft()) setEditSession(session)
  }
  const openRootEditor = () => openSession(createEditSession('root', emptyConfig()))
  const openChildEditor = (path: ConfigPath) => {
    const parent = configAtPath(configs, path)
    const aliases = scopeAliases(configs, path)
    const sourceRoot = aliases[aliases.length - 1] ?? '$'
    const isArrayElement = parent?.mappingType === 'ARRAY_MAP' || parent?.mappingType === 'ARRAY_FIXED'
    openSession(createEditSession('child', isArrayElement ? objectTemplate() : emptyConfig(sourceRoot), path))
  }
  const openEditEditor = (path: ConfigPath, value: ParamMappingConfig) => {
    if (editSession?.kind === 'edit' && editSession.path && pathKey(editSession.path) === pathKey(path)) return
    openSession(createEditSession('edit', value, path))
  }
  const openCopyEditor = (path: ConfigPath, value: ParamMappingConfig) => openSession(createEditSession('copy', value, path))

  const setPayloadMode = (nextMode: PayloadMode) => {
    if (nextMode === payloadMode) return
    if (nextMode === 'parameters') {
      commit(isRecord(payload) ? payload : {}, configs)
      return
    }
    commit(typeof payload === 'string' ? payload : DEFAULT_PAYLOAD_EXPRESSION, configs)
  }

  const updatePayloadParam = (index: number, next: { key: string; value: string }) => {
    const rows = [...payloadParams]
    rows[index] = next
    commit(Object.fromEntries(rows.map((item) => [item.key, item.value])), configs)
  }

  const addPayloadParam = () => {
    const names = new Set(payloadParams.map((item) => item.key))
    let index = payloadParams.length + 1
    let key = `parameter_${index}`
    while (names.has(key)) {
      index += 1
      key = `parameter_${index}`
    }
    commit({ ...(isRecord(payload) ? payload : {}), [key]: '' }, configs)
  }

  const deletePayloadParam = (index: number) => {
    commit(
      Object.fromEntries(payloadParams.filter((_, itemIndex) => itemIndex !== index).map((item) => [item.key, item.value])),
      configs,
    )
  }

  const saveEditSession = (value: ParamMappingConfig) => {
    if (!editSession) return
    const nextValue = normalizeForSave(value)
    let nextPath = editSession.path ?? [configs.length]
    if (editSession.kind === 'root') {
      commitConfigs([...configs, nextValue])
    } else if (editSession.kind === 'child' && editSession.path) {
      const parent = configAtPath(configs, editSession.path)
      if (!parent) return
      nextPath = [...editSession.path, configChildren(parent).length]
      commitConfigs(updateAtPath(configs, editSession.path, (config) => withConfigChildren(config, [...configChildren(config), nextValue])))
    } else if (editSession.kind === 'edit' && editSession.path) {
      commitConfigs(updateAtPath(configs, editSession.path, () => nextValue))
    } else if (editSession.kind === 'copy' && editSession.path) {
      nextPath = [...editSession.path.slice(0, -1), editSession.path[editSession.path.length - 1] + 1]
      commitConfigs(insertAfterPath(configs, editSession.path, nextValue))
    }
    setEditSession(createEditSession('edit', nextValue, nextPath))
  }

  const actions = {
    addChild: openChildEditor,
    edit: openEditEditor,
    copy: openCopyEditor,
    remove: (path: ConfigPath) => {
      if (!canDiscardDraft()) return
      // Paths use sibling indexes; clearing selection prevents editing a shifted sibling.
      setEditSession(null)
      commitConfigs(removeAtPath(configs, path))
    },
  }

  return (
    <div className="workflow-form-panel inline-task-inspector param-map-task-inspector">
      <TaskPanelHeader task={task} label="参数映射" onChange={onChange} />

      <WorkflowCollapsibleSection title="映射输入">
        <div className="switch-evaluator-heading">
          <div className="switch-evaluator-options" role="radiogroup" aria-label="映射输入方式">
            <label>
              <input
                type="radio"
                name={`param-map-payload-mode-${task.taskReferenceName}`}
                value="expression"
                checked={payloadMode === 'expression'}
                onChange={() => setPayloadMode('expression')}
              />
              <span>表达式</span>
            </label>
            <label>
              <input
                type="radio"
                name={`param-map-payload-mode-${task.taskReferenceName}`}
                value="parameters"
                checked={payloadMode === 'parameters'}
                onChange={() => setPayloadMode('parameters')}
              />
              <span>参数</span>
            </label>
          </div>
        </div>
        {payloadMode === 'expression' ? (
          <label className="inspector-field">
            <span>payload</span>
            <VariableExpressionInput
              value={typeof payload === 'string' ? payload : ''}
              options={variableOptions}
              onChange={(value) => commit(value, configs)}
            />
          </label>
        ) : (
          <>
            <ParameterRows
              items={payloadParams}
              keyLabel="参数名"
              valueLabel="映射值"
              variableOptions={variableOptions}
              onChange={updatePayloadParam}
              onDelete={deletePayloadParam}
            />
            <button type="button" className="param-map-add-input" onClick={addPayloadParam}>
              <PlusCircleIcon size={16} />
              添加参数
            </button>
          </>
        )}
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="映射配置" className="param-map-config-section">
        <div className="param-map-config-toolbar">
          <div className="param-map-view-options" role="radiogroup" aria-label="映射配置编辑方式">
            <label className={visibleJsonError ? 'disabled' : ''}>
              <input
                type="radio"
                name={`param-map-view-${task.taskReferenceName}`}
                value="PARAMETERS"
                checked={view === 'PARAMETERS' && !storedConfigError}
                disabled={Boolean(visibleJsonError)}
                onChange={() => setView('PARAMETERS')}
              />
              <span>参数</span>
            </label>
            <label>
              <input
                type="radio"
                name={`param-map-view-${task.taskReferenceName}`}
                value="JSON"
                checked={view === 'JSON' || Boolean(visibleJsonError)}
                onChange={() => { if (closeFieldEditor()) setView('JSON') }}
              />
              <span>JSON</span>
            </label>
          </div>
          <div className="param-map-config-meta">
            <span className="param-map-field-count">{countParamMappingConfigs(configs)} 个字段</span>
          </div>
        </div>

        {view === 'JSON' || visibleJsonError ? (
          <ValidationField path="/inputParameters/paramsConfig"><div className={`param-map-json-view ${visibleJsonError ? 'has-error' : ''}`}>
            <InlineScriptEditor
              value={jsonDraft.value}
              onChange={updateJson}
              minHeight={360}
              language="json"
              languageLabel="JSON"
              codeLabel="Code"
              modalTitle="映射配置 JSON 编辑器"
              autoSize={false}
            />
          </div></ValidationField>
        ) : (
          <ParamMappingTreeEditor
            key={task.taskReferenceName}
            configs={configs}
            onAddRoot={openRootEditor}
            selectedPath={editSession?.path}
            onCloseFieldEditor={closeFieldEditor}
            fieldEditor={editSession ? (
              <ParamConfigPanel
                key={`${editSession.kind}:${editSession.path ? pathKey(editSession.path) : 'root'}`}
                session={editSession}
                parentPath={configPathLabel(configs, editSession.kind === 'child' ? editSession.path ?? [] : editSession.path?.slice(0, -1) ?? [])}
                parentConfig={configAtPath(configs, editSession.kind === 'child' ? editSession.path ?? [] : editSession.path?.slice(0, -1) ?? [])}
                aliases={scopeAliases(configs, editSession.kind === 'child' ? editSession.path ?? [] : editSession.path?.slice(0, -1) ?? [])}
                onChange={(value) => setEditSession({ ...editSession, value })}
                onCancel={closeFieldEditor}
                onSave={saveEditSession}
              />
            ) : null}
            {...actions}
          />
        )}
      </WorkflowCollapsibleSection>

    </div>
  )
}

type ConfigActions = {
  addChild: (path: ConfigPath) => void
  edit: (path: ConfigPath, config: ParamMappingConfig) => void
  copy: (path: ConfigPath, config: ParamMappingConfig) => void
  remove: (path: ConfigPath) => void
}

function ParamMappingTreeEditor({
  configs,
  onAddRoot,
  selectedPath,
  fieldEditor,
  onCloseFieldEditor,
  addChild,
  edit,
  copy,
  remove,
}: {
  configs: ParamMappingConfig[]
  onAddRoot: () => void
  selectedPath?: ConfigPath
  fieldEditor: ReactNode
  onCloseFieldEditor: () => boolean
} & ConfigActions) {
  const allRows = useMemo(() => flattenDisplayConfigs(configs), [configs])
  const containerPaths = useMemo(
    () => allRows.filter((row, index) => allRows[index + 1]?.depth > row.depth).map(({ path }) => pathKey(path)),
    [allRows],
  )
  const [expandedPaths, setExpandedPaths] = useState<Set<string>>(() => new Set(containerPaths))
  const [editorExpanded, setEditorExpanded] = useState(false)
  const [copied, setCopied] = useState(false)
  const copyTimerRef = useRef<number | undefined>(undefined)
  const knownContainerPathsRef = useRef(new Set(containerPaths))
  const hostRef = useRef<HTMLDivElement>(null)
  const modalRef = useRef<HTMLElement>(null)
  const wasExpandedRef = useRef(false)
  const hasFieldEditor = Boolean(fieldEditor)
  const selectedKey = selectedPath ? pathKey(selectedPath) : ''

  useEffect(() => {
    if (!selectedKey) return
    const selectedRow = allRows.find(({ path }) => pathKey(path) === selectedKey)
    const ancestors = selectedRow?.ancestors
      ?? allRows.filter(({ path }) => selectedPath && path.length < selectedPath.length
        && path.every((part, index) => selectedPath[index] === part)).map(({ path }) => path)
    setExpandedPaths((current) => {
      const next = new Set(current)
      ancestors.forEach((path) => next.add(pathKey(path)))
      return next
    })
  }, [selectedKey])

  const visibleRows = allRows.filter(({ ancestors }) => ancestors.every((path) => expandedPaths.has(pathKey(path))))

  const toggle = (path: ConfigPath) => {
    const key = pathKey(path)
    setExpandedPaths((current) => {
      const next = new Set(current)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  const copyConfig = async () => {
    const text = JSON.stringify(configs, null, 2)
    const legacyCopy = () => {
      const textarea = document.createElement('textarea')
      textarea.value = text
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
      await navigator.clipboard.writeText(text)
    } catch {
      legacyCopy()
    }

    setCopied(true)
    window.clearTimeout(copyTimerRef.current)
    copyTimerRef.current = window.setTimeout(() => setCopied(false), 2200)
  }

  useEffect(() => () => window.clearTimeout(copyTimerRef.current), [])

  useEffect(() => {
    const nextContainerPaths = new Set(containerPaths)
    setExpandedPaths((current) => {
      const next = new Set([...current].filter((key) => nextContainerPaths.has(key)))
      containerPaths.forEach((key) => {
        if (!knownContainerPathsRef.current.has(key)) next.add(key)
      })
      return next
    })
    knownContainerPathsRef.current = nextContainerPaths
  }, [containerPaths])

  useEffect(() => {
    if (editorExpanded) {
      if (!modalRef.current?.contains(document.activeElement)) {
        modalRef.current?.querySelector<HTMLButtonElement>('header button')?.focus({ preventScroll: true })
      }
    } else if (wasExpandedRef.current) {
      hostRef.current?.querySelector<HTMLButtonElement>('.param-map-tree-editor-actions button')?.focus({ preventScroll: true })
    }
    wasExpandedRef.current = editorExpanded
  }, [editorExpanded, hasFieldEditor])

  useEffect(() => {
    if (!editorExpanded) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previousOverflow
    }
  }, [editorExpanded])

  const closeEditor = () => {
    if (onCloseFieldEditor()) setEditorExpanded(false)
  }

  const openFieldEditor = (action: () => void) => {
    setEditorExpanded(true)
    action()
  }

  const surface = (
    <ParamMappingTreeSurface
      configs={configs}
      rows={visibleRows}
      allRows={allRows}
      expandedPaths={expandedPaths}
      expanded={editorExpanded}
      onToggle={toggle}
      onExpandAll={() => setExpandedPaths(new Set(containerPaths))}
      onCollapseAll={() => setExpandedPaths(new Set())}
      onAddRoot={() => openFieldEditor(onAddRoot)}
      onToggleEditor={() => editorExpanded ? closeEditor() : setEditorExpanded(true)}
      onCopy={() => void copyConfig()}
      selectedPath={selectedPath}
      fieldEditor={fieldEditor}
      addChild={(path) => openFieldEditor(() => addChild(path))}
      edit={(path, config) => openFieldEditor(() => edit(path, config))}
      copy={(path, config) => openFieldEditor(() => copy(path, config))}
      remove={remove}
    />
  )

  return (
    <div ref={hostRef} className="param-map-tree-host">
      {editorExpanded ? createPortal(
        <div className="param-map-tree-modal-backdrop" onMouseDown={closeEditor}>
          <section
            ref={modalRef}
            className="param-map-tree-modal"
            role="dialog"
            aria-modal="true"
            aria-label="参数映射编辑器"
            onMouseDown={(event) => event.stopPropagation()}
            onKeyDown={(event) => {
              if (event.key === 'Tab') {
                const controls = [...event.currentTarget.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), summary')]
                  .filter((control) => control.getClientRects().length > 0)
                const first = controls[0]
                const last = controls[controls.length - 1]
                if (event.shiftKey && document.activeElement === first) {
                  event.preventDefault()
                  last?.focus()
                } else if (!event.shiftKey && document.activeElement === last) {
                  event.preventDefault()
                  first?.focus()
                }
                return
              }
              if (event.key !== 'Escape') return
              event.stopPropagation()
              if (fieldEditor) onCloseFieldEditor()
              else closeEditor()
            }}
          >
            <header>
              <div><h2>参数映射编辑器</h2></div>
              <button type="button" title="关闭" aria-label="关闭参数映射编辑器" onClick={closeEditor}><XIcon size={22} /></button>
            </header>
            <div className="param-map-tree-modal-content">{surface}</div>
          </section>
        </div>,
        document.body,
      ) : surface}
      {copied ? <div className="code-copy-toast" role="status">映射配置已复制到剪贴板</div> : null}
    </div>
  )
}

function ParamMappingTreeSurface({
  configs,
  rows,
  allRows,
  expandedPaths,
  expanded,
  onToggle,
  onExpandAll,
  onCollapseAll,
  onAddRoot,
  onToggleEditor,
  onCopy,
  selectedPath,
  fieldEditor,
  addChild,
  edit,
  copy,
  remove,
}: {
  configs: ParamMappingConfig[]
  rows: DisplayConfigRow[]
  allRows: DisplayConfigRow[]
  expandedPaths: Set<string>
  expanded: boolean
  onToggle: (path: ConfigPath) => void
  onExpandAll: () => void
  onCollapseAll: () => void
  onAddRoot: () => void
  onToggleEditor: () => void
  onCopy: () => void
  selectedPath?: ConfigPath
  fieldEditor: ReactNode
} & ConfigActions) {
  const [detailWidth, setDetailWidth] = useState(340)
  const [workspaceWidth, setWorkspaceWidth] = useState(0)
  const [resizing, setResizing] = useState(false)
  const workspaceRef = useRef<HTMLDivElement>(null)
  const dragRef = useRef<{ x: number; width: number } | null>(null)
  const hasFieldEditor = Boolean(fieldEditor)

  useEffect(() => {
    const workspace = workspaceRef.current
    if (!workspace || !expanded || !hasFieldEditor) return
    const observer = new ResizeObserver(() => {
      const width = workspace.getBoundingClientRect().width
      setWorkspaceWidth(width)
      if (width > 1000) setDetailWidth((current) => Math.min(Math.max(280, width - 360), current))
    })
    observer.observe(workspace)
    return () => observer.disconnect()
  }, [expanded, hasFieldEditor])

  const resizeDetail = (width: number) => {
    const total = workspaceRef.current?.getBoundingClientRect().width ?? workspaceWidth
    setDetailWidth(Math.min(Math.max(280, total - 360), Math.max(280, width)))
  }

  const containerPathKeys = new Set(allRows
    .filter((row, index) => allRows[index + 1]?.depth > row.depth)
    .map(({ path }) => pathKey(path)))
  return (
    <div className={`param-map-tree-editor ${expanded ? 'expanded' : 'embedded'}`}>
      <div className="param-map-tree-toolbar">
        <span>参数树 · {countParamMappingConfigs(configs)} 个字段</span>
        <div>
          {rows.length ? (
            <>
              <button type="button" onClick={onExpandAll}>展开全部</button>
              <button type="button" onClick={onCollapseAll}>折叠全部</button>
            </>
          ) : null}
          <button type="button" className="primary" onClick={onAddRoot}><PlusIcon size={14} weight="bold" />新增根字段</button>
        </div>
      </div>
      <div ref={workspaceRef}
        className={`param-map-tree-workspace ${fieldEditor ? 'with-detail' : ''} ${resizing ? 'resizing' : ''}`}
        style={{ '--param-map-detail-width': `${detailWidth}px` } as CSSProperties}>
      <div className="param-map-tree-list">
        <div className="param-map-tree-columns" aria-hidden="true">
          <span>字段名称</span><span>字段类型</span><span>取值方式</span><span>操作</span>
        </div>
      <div className="param-map-tree-scroll" role="group" aria-label="目标参数树">
        {rows.map(({ config, path, depth }) => {
          const hasChildren = containerPathKeys.has(pathKey(path))
          return (
            <div
              className={`param-map-tree-row ${selectedPath && pathKey(selectedPath) === pathKey(path) ? 'selected' : ''}`}
              key={pathKey(path)}
              onClick={(event) => {
                if (!(event.target as HTMLElement).closest('button, details')) edit(path, config)
              }}
            >
              <div className="param-map-tree-field" style={{ '--param-map-depth': depth } as CSSProperties}>
              <button
                type="button"
                className={`param-map-tree-toggle ${hasChildren ? '' : 'empty'}`}
                disabled={!hasChildren}
                aria-label={hasChildren ? `${expandedPaths.has(pathKey(path)) ? '折叠' : '展开'} ${configNodeLabel(configs, path)}` : undefined}
                aria-expanded={hasChildren ? expandedPaths.has(pathKey(path)) : undefined}
                onClick={() => onToggle(path)}
              >
                {hasChildren
                  ? (expandedPaths.has(pathKey(path)) ? <CaretDownIcon size={15} /> : <CaretRightIcon size={15} />)
                  : <MinusIcon size={11} />}
              </button>
              <button
                type="button"
                className="param-map-tree-target"
                title={`${configPathLabel(configs, path)}${config.paramDesc ? ` · ${config.paramDesc}` : ''}`}
                aria-label={`编辑字段 ${configPathLabel(configs, path)}`}
                aria-pressed={Boolean(selectedPath && pathKey(selectedPath) === pathKey(path))}
                onClick={() => edit(path, config)}
              >
                <strong>{configNodeLabel(configs, path)}</strong>
              </button>
              </div>
              <button
                type="button"
                className="param-map-tree-type"
                title={`编辑 ${configNodeLabel(configs, path)} 的目标类型`}
                aria-label={`编辑 ${configNodeLabel(configs, path)} 的目标类型`}
                onClick={() => edit(path, config)}
              >
                <TypeBadge value={config.targetParamType} side="target" />
              </button>
              <MappingSummary config={config} onEdit={() => edit(path, config)} />
              <ConfigActionButtons config={config} path={path} isLoopTemplate={configAtPath(configs, path.slice(0, -1))?.mappingType === 'ARRAY_MAP'} label={configNodeLabel(configs, path)} addChild={addChild} edit={edit} copy={copy} remove={remove} />
            </div>
          )
        })}
        {!rows.length ? (
          <div className="param-map-tree-empty">
            <strong>还没有目标字段</strong>
            <p>新增字段，按目标结构配置取字段、固定值或 JS表达式。</p>
            <button type="button" onClick={onAddRoot}><PlusIcon size={15} weight="bold" />新增第一个字段</button>
          </div>
        ) : null}
      </div>
      </div>
      {fieldEditor}
      {expanded && fieldEditor ? <div className="param-map-detail-resizer" role="separator"
        aria-label="调整单字段编辑区宽度" aria-orientation="vertical"
        aria-valuemin={280} aria-valuemax={Math.max(detailWidth, workspaceWidth - 360)} aria-valuenow={Math.round(detailWidth)}
        aria-valuetext={`${Math.round(detailWidth)} 像素`} tabIndex={0}
        onPointerDown={(event) => {
          if (event.button !== 0) return
          event.preventDefault()
          dragRef.current = { x: event.clientX, width: detailWidth }
          setResizing(true)
          event.currentTarget.setPointerCapture(event.pointerId)
        }}
        onPointerMove={(event) => {
          if (dragRef.current) resizeDetail(dragRef.current.width + dragRef.current.x - event.clientX)
        }}
        onPointerUp={(event) => {
          dragRef.current = null
          setResizing(false)
          if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
        }}
        onLostPointerCapture={() => { dragRef.current = null; setResizing(false) }}
        onKeyDown={(event) => {
          if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
          event.preventDefault()
          resizeDetail(detailWidth + (event.key === 'ArrowLeft' ? 20 : -20))
        }} /> : null}
      </div>
      <div className="param-map-tree-editor-actions">
        <span>{selectedPath ? configPathLabel(configs, selectedPath) : '选择字段查看配置'}</span>
        <div>
        <button
          type="button"
          title={expanded ? '收起参数映射编辑器' : '放大参数映射编辑器'}
          aria-label={expanded ? '收起参数映射编辑器' : '放大参数映射编辑器'}
          onClick={onToggleEditor}
        >
          {expanded ? <ArrowsInSimpleIcon size={20} /> : <ArrowsOutSimpleIcon size={20} />}
        </button>
        <button type="button" title="复制映射配置" aria-label="复制映射配置" onClick={onCopy}><CopyIcon size={20} /></button>
        </div>
      </div>
    </div>
  )
}

function MappingSummary({ config, onEdit }: { config: ParamMappingConfig; onEdit: () => void }) {
  const mode = config.mappingType
  const detail = mode === 'CONSTANT' ? typeof config.mappingRule === 'string' && config.mappingRule !== ''
    ? config.mappingRule : JSON.stringify(config.mappingRule ?? null)
    : mode === 'EXPRESSION' ? String(config.mappingRule ?? '')
      : mode === 'ARRAY_MAP' ? `${config.sourceParamKey || '未配置循环源'} → ${config.itemAlias || '未命名变量'}`
        : mode === 'ARRAY_FIXED' ? `${config.items.length} 个元素`
          : mode === 'OBJECT' ? `${config.childParams.length} 个子字段` : config.sourceParamKey || '未配置路径'
  const style = mode === 'CONSTANT' ? 'constant' : mode === 'EXPRESSION' ? 'expression'
    : mode === 'NAME' || mode === 'ARRAY_MAP' ? 'source' : 'structure'
  return (
    <button type="button" className={`param-map-tree-binding ${style}`} title={detail} onClick={onEdit}>
      <span className="param-map-tree-binding-label">{mode === 'NAME' ? '字段映射' : mappingLabel(mode)}</span>
      {mode === 'NAME' ? <ArrowLeftIcon size={14} /> : null}
      <code>{mode === 'EXPRESSION' ? <InlineHighlightedCode code={detail} format={false} /> : detail}</code>
    </button>
  )
}

function TypeBadge({ value, side }: { value: ParamType; side: 'source' | 'target' }) {
  return <span className={`param-map-type-badge ${side}`}>{paramTypeLabel(value)}</span>
}

function ConfigActionButtons({ config, path, isLoopTemplate, label, addChild, copy, remove }: { config: ParamMappingConfig; path: ConfigPath; isLoopTemplate: boolean; label: string } & ConfigActions) {
  const [openMenu, setOpenMenu] = useState<'more' | null>(null)
  const actionsRef = useRef<HTMLDivElement>(null)
  const moreMenuRef = useRef<HTMLDetailsElement>(null)
  const needsLoopTemplate = config.mappingType === 'ARRAY_MAP' && !config.itemTemplate

  useEffect(() => {
    if (!openMenu) return
    const close = (event: MouseEvent) => {
      if (!actionsRef.current?.contains(event.target as Node)) setOpenMenu(null)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [openMenu])

  return (
    <div className="param-map-actions" ref={actionsRef}>
      {needsLoopTemplate || config.mappingType === 'OBJECT' || config.mappingType === 'ARRAY_FIXED' ? (
        <button type="button" className="param-map-child-action"
          title={needsLoopTemplate ? '配置元素' : config.mappingType === 'ARRAY_FIXED' ? '添加元素' : '添加子字段'}
          aria-label={needsLoopTemplate ? `配置 ${label} 的元素` : `在 ${label} 下${config.mappingType === 'ARRAY_FIXED' ? '添加元素' : '添加子字段'}`}
          onClick={() => addChild(path)}>
          <PlusIcon size={14} weight="bold" />
        </button>
      ) : null}
      {!isLoopTemplate ? <details
        className="param-map-row-more"
        ref={moreMenuRef}
        open={openMenu === 'more'}
        onToggle={(event) => setOpenMenu((current) => event.currentTarget.open ? 'more' : current === 'more' ? null : current)}
        onKeyDown={(event) => {
          if (event.key === 'Escape' && openMenu === 'more') {
            event.stopPropagation()
            setOpenMenu(null)
            moreMenuRef.current?.querySelector('summary')?.focus()
          }
        }}
      >
        <summary title="更多操作" aria-label={`${label} 的更多操作`}><DotsThreeIcon size={18} weight="bold" /></summary>
        <div className="param-map-row-menu">
          <button type="button" onClick={() => { setOpenMenu(null); copy(path, config) }}><CopyIcon size={15} />复制字段</button>
          <button type="button" className="danger" onClick={() => { setOpenMenu(null); remove(path) }}><TrashIcon size={15} />删除字段</button>
        </div>
      </details> : null}
    </div>
  )
}

function MappingSelect<T extends string>({
  value,
  options,
  ariaLabel,
  onChange,
}: {
  value: T
  options: Array<{ value: T; label: string }>
  ariaLabel: string
  onChange: (value: T) => void
}) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const selected = options.find((option) => option.value === value) ?? options[0]

  useEffect(() => {
    if (!open) return
    const closeOnOutsidePointer = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', closeOnOutsidePointer)
    return () => document.removeEventListener('pointerdown', closeOnOutsidePointer)
  }, [open])

  return (
    <div className={`param-map-select ${open ? 'open' : ''}`} ref={rootRef} onKeyDown={(event) => {
      if (event.key === 'Escape' && open) {
        event.stopPropagation()
        setOpen(false)
        rootRef.current?.querySelector('button')?.focus()
      }
    }}>
      <button
        type="button"
        className="param-map-select-trigger"
        role="combobox"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        onKeyDown={(event) => {
          if (event.key === 'Escape') setOpen(false)
          if (event.key === 'ArrowDown') {
            event.preventDefault()
            setOpen(true)
          }
        }}
      >
        <span>{selected?.label ?? value}</span>
        <CaretDownIcon size={16} weight="bold" />
      </button>
      {open ? (
        <div className="param-map-select-options" role="listbox" aria-label={`${ariaLabel}选项`}>
          {options.map((option) => (
            <button
              type="button"
              className={option.value === value ? 'selected' : ''}
              role="option"
              aria-selected={option.value === value}
              key={option.value}
              onClick={() => {
                onChange(option.value)
                setOpen(false)
              }}
            >
              <span>{option.label}</span>
              {option.value === value ? <CheckIcon size={15} weight="bold" /> : null}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}

function ParamConfigPanel({ session, parentPath, parentConfig, aliases, onChange, onCancel, onSave }: {
  session: EditSession
  parentPath: string
  parentConfig?: ParamMappingConfig
  aliases: string[]
  onChange: (value: ParamMappingConfig) => void
  onCancel: () => void
  onSave: (value: ParamMappingConfig) => void
}) {
  const value = session.value
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const [constantDraft, setConstantDraft] = useState(() => constantInputText(value.mappingRule, value.targetParamType))
  const [constantNull, setConstantNull] = useState(value.mappingRule == null)
  const [constantEdited, setConstantEdited] = useState(false)
  const panelRef = useRef<HTMLElement>(null)
  const dirty = JSON.stringify(value) !== session.initialValue
  const loopElement = parentConfig?.mappingType === 'ARRAY_MAP'
  const element = loopElement || parentConfig?.mappingType === 'ARRAY_FIXED'
  const sourceRoot = aliases[aliases.length - 1] ?? '$'
  const pathCorrection = value.mappingType === 'NAME' || value.mappingType === 'ARRAY_MAP'
    ? aliasPathCorrection(value.sourceParamKey ?? '', aliases) : undefined
  const elementLabel = loopElement ? loopElementLabel(value) : '元素配置'
  const title = element ? elementLabel : session.kind === 'edit' ? '字段配置'
    : session.kind === 'copy' ? '复制字段' : session.kind === 'child' ? '新增子字段' : '新增根字段'
  const pathLabel = [parentPath, element ? elementLabel : value.targetParamKey || '新字段'].filter(Boolean).join('.')
  const modes = mappingTypesForTargetType(value.targetParamType)
  const modeFieldLabel = value.targetParamType === 'ARRAY' ? '数组生成方式'
    : value.targetParamType === 'OBJECT' ? '对象生成方式' : '取值方式'
  const initialNode = normalizeConfig(JSON.parse(session.initialValue))
  const clearsChildren = configChildren(initialNode).length > 0 && initialNode.mappingType !== value.mappingType

  useEffect(() => {
    panelRef.current?.scrollIntoView({ block: 'nearest' })
    panelRef.current?.querySelector<HTMLInputElement>('input')?.focus({ preventScroll: true })
  }, [])

  const setValue = (next: ParamMappingConfig) => {
    setError('')
    setSaved(false)
    onChange(next)
  }

  const updateConstant = (text: string, isNull = constantNull) => {
    setConstantDraft(text)
    setConstantNull(isNull)
    setConstantEdited(true)
    try {
      setValue({ ...value, mappingRule: isNull ? null : parseConstantInput(text, value.targetParamType) })
    } catch (error) {
      // Keep invalid numeric drafts in the edit session so closing it still warns about unsaved changes.
      setValue({ ...value, mappingRule: text })
      setError((error as Error).message)
    }
  }

  const changeMode = (mode: MappingType, type = value.targetParamType) => {
    let aliasIndex = 1
    let alias = 'item'
    while (aliases.includes(alias)) alias = `item${++aliasIndex}`
    const mappingRule = mode === 'CONSTANT' ? (value.mappingType === 'CONSTANT' ? value.mappingRule
      : type === 'STRING' ? '' : type === 'BOOLEAN' ? false : 0)
      : mode === 'EXPRESSION' ? (value.mappingType === 'EXPRESSION' ? value.mappingRule : sourceRoot) : undefined
    const nextDraft = constantInputText(mappingRule, type)
    setConstantDraft(nextDraft)
    setConstantNull(mappingRule == null)
    setConstantEdited(mode === 'CONSTANT' && (constantEdited || value.mappingType !== 'CONSTANT' || type !== value.targetParamType))
    setValue({ ...value, mappingType: mode, targetParamType: type, mappingRule,
      sourceParamKey: (mode === 'NAME' || mode === 'ARRAY_MAP') && !value.sourceParamKey ? sourceRoot : value.sourceParamKey,
      itemAlias: mode === 'ARRAY_MAP' ? value.itemAlias || alias : value.itemAlias,
      itemTemplate: mode === 'ARRAY_MAP' ? value.itemTemplate ?? objectTemplate() : value.itemTemplate,
    })
    if (mode === 'CONSTANT' && mappingRule != null) {
      try { parseConstantInput(nextDraft, type) }
      catch (error) { setError((error as Error).message) }
    }
  }

  const changeTargetParamKey = (targetParamKey: string) => {
    setValue({
      ...value,
      targetParamKey,
      sourceParamKey: value.mappingType === 'NAME' || value.mappingType === 'ARRAY_MAP'
        ? sourcePathAfterTargetRename(value.sourceParamKey, sourceRoot, value.targetParamKey, targetParamKey)
        : value.sourceParamKey,
    })
  }

  const submit = () => {
    try {
      const constantValue = value.mappingType === 'CONSTANT' && !constantNull
        ? parseConstantInput(constantDraft, value.targetParamType) : null
      const next = normalizeForSave({ ...value, targetParamKey: element ? '' : value.targetParamKey,
        mappingRule: value.mappingType === 'CONSTANT'
          ? constantNull ? null : constantEdited ? constantValue : value.mappingRule : value.mappingRule })
      validateNode(next, aliases, element)
      setError('')
      onSave(next)
      setSaved(true)
      setConstantEdited(false)
      if (next.mappingType === 'CONSTANT') setConstantDraft(constantInputText(next.mappingRule, next.targetParamType))
    } catch (error) {
      setError((error as Error).message)
    }
  }

  return (
    <aside className="param-map-field-panel" aria-label={title} ref={panelRef}>
      <header>
        <div><small className="param-map-field-path">{pathLabel}</small><h3>{title}</h3></div>
        <button type="button" title="关闭字段配置" aria-label="关闭字段配置" onClick={onCancel}><XIcon size={18} /></button>
      </header>
      <div className="param-map-dialog-body">
        <div className="param-map-dialog-grid">
          {!element ? <label><span>参数名称 <strong>*</strong></span><input aria-required="true" value={value.targetParamKey} onChange={(event) => changeTargetParamKey(event.target.value)} /></label> : null}
          <div className="param-map-dialog-field">
            <span>{element ? '元素类型' : '参数类型'}</span>
            <MappingSelect value={value.targetParamType}
              options={PARAM_TYPES.map((type) => ({ value: type, label: paramTypeLabel(type) }))}
              ariaLabel={element ? '元素类型' : '参数类型'}
              onChange={(type) => changeMode(type === 'OBJECT' ? 'OBJECT' : type === 'ARRAY' ? 'ARRAY_MAP'
                : ['OBJECT', 'ARRAY_MAP', 'ARRAY_FIXED'].includes(value.mappingType) ? 'NAME' : value.mappingType, type)} />
          </div>
          <div className="param-map-dialog-field">
            <span>{modeFieldLabel}</span>
            <MappingSelect value={value.mappingType}
              options={modes.map((mode) => ({ value: mode, label: mappingLabel(mode) }))}
              ariaLabel={modeFieldLabel} onChange={(mode) => changeMode(mode)} />
          </div>
          <div className="param-map-field-context">
            <strong>可用上下文</strong>
            <span className="param-map-context-roots"><span><small>根</small><code>$</code></span>
              {aliases.map((alias, index) => <span key={alias}><small>{index === aliases.length - 1 ? '当前元素' : '外层元素'}</small><code>{alias}</code></span>)}
            </span>
          </div>
          {value.mappingType === 'NAME' || value.mappingType === 'ARRAY_MAP' ? (
            <label className="full"><span>{value.mappingType === 'ARRAY_MAP' ? '循环源路径' : '取值路径'}</span>
              <input value={value.sourceParamKey ?? ''} placeholder={value.mappingType === 'ARRAY_MAP' ? '$.items' : '$.patientId / item.code'} onChange={(event) => setValue({ ...value, sourceParamKey: event.target.value })} />
              {pathCorrection ? <span className="param-map-path-warning" role="status">
                <span><code>$</code> 表示根输入；如果要读取当前循环元素，请使用 <code>{pathCorrection}</code>。</span>
                <button type="button" onClick={() => setValue({ ...value, sourceParamKey: pathCorrection })}>改为 <code>{pathCorrection}</code></button>
              </span> : null}
            </label>
          ) : null}
          {value.mappingType === 'ARRAY_MAP' ? (
            <label className="full"><span>循环变量名</span><input value={value.itemAlias ?? ''} placeholder="item" onChange={(event) => setValue({ ...value, itemAlias: event.target.value })} />
            </label>
          ) : null}
          {value.mappingType === 'CONSTANT' ? (
            <div className="param-map-dialog-field full">
              <div className="param-map-constant-heading">
                <span>固定值</span>
                <label className="param-map-null-toggle">
                  <input type="checkbox" checked={constantNull}
                    onChange={(event) => updateConstant(constantDraft, event.target.checked)} />
                  <span>设为 null</span>
                </label>
              </div>
              {constantNull ? <small className="param-map-rule-help">此字段输出 null。</small>
                : value.targetParamType === 'BOOLEAN' ? (
                  <MappingSelect value={constantDraft} ariaLabel="固定值"
                    options={[{ value: 'true', label: 'true' }, { value: 'false', label: 'false' }]}
                    onChange={(text) => updateConstant(text)} />
                ) : value.targetParamType === 'STRING' ? (
                  <>
                    <textarea rows={4} aria-label="固定值" value={constantDraft} placeholder="例如：LIS"
                      onChange={(event) => updateConstant(event.target.value)} />
                  </>
                ) : (
                  <input aria-label="固定值" value={constantDraft}
                    inputMode={value.targetParamType === 'DOUBLE' ? 'decimal' : 'numeric'}
                    placeholder={value.targetParamType === 'DOUBLE' ? '例如：123.45' : '例如：123'}
                    onChange={(event) => updateConstant(event.target.value)} />
                )}
            </div>
          ) : null}
          {value.mappingType === 'EXPRESSION' ? (
            <div className="param-map-dialog-field full">
              <MappingExpressionInput
                value={typeof value.mappingRule === 'string' ? value.mappingRule : ''}
                onChange={(mappingRule) => setValue({ ...value, mappingRule })}
              />
            </div>
          ) : null}
          <label className="full"><span>参数描述</span><textarea rows={2} value={value.paramDesc ?? ''} onChange={(event) => setValue({ ...value, paramDesc: event.target.value })} /></label>
        </div>
        {clearsChildren ? <p className="param-map-field-notice" role="status">应用后将移除原来的子结构配置。</p> : null}
        {error ? <p className="inline-error" role="alert">{error}</p> : null}
      </div>
      <footer>
        <small role="status">{dirty ? '未应用的修改' : saved ? '已应用到工作流草稿' : ''}</small>
        <button type="button" onClick={onCancel}>取消</button><button type="button" className="primary" onClick={submit}>应用到字段</button>
      </footer>
    </aside>
  )
}
