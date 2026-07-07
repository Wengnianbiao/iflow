import Editor from '@monaco-editor/react'
import {
  ArrowRight,
  ArrowsLeftRight,
  CaretDown,
  CaretRight,
  Code,
  Copy,
  ListBullets,
  MagnifyingGlassMinus,
  MagnifyingGlassPlus,
  Minus,
  PencilSimple,
  Plus,
  PlusCircle,
  Trash,
  TreeStructure,
  X,
} from '@phosphor-icons/react'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import type { WorkflowTask } from '@/types/workflow'

const PARAM_TYPES = ['STRING', 'INTEGER', 'LONG', 'DOUBLE', 'BOOLEAN', 'OBJECT', 'ARRAY', 'PURE_ARRAY', 'NONE'] as const
const MAPPING_TYPES = ['NAME', 'CONSTANT', 'EXPRESSION', 'BEAN_EXPRESSION', 'SINGLE_MAP'] as const
const CONTAINER_TYPES = new Set<ParamType>(['OBJECT', 'ARRAY', 'PURE_ARRAY'])

type ParamType = (typeof PARAM_TYPES)[number]
type MappingType = (typeof MAPPING_TYPES)[number]
type ConfigPath = number[]
type EditorView = 'JSON' | 'PARAMETERS'

export interface ParamMappingConfig {
  sourceParamKey?: string
  sourceParamType: ParamType
  targetParamKey: string
  targetParamType: ParamType
  mappingType: MappingType
  mappingRule?: string
  paramDesc?: string
  childParams: ParamMappingConfig[]
  [key: string]: unknown
}

type ParamMapTaskInspectorProps = {
  task: WorkflowTask
  header: ReactNode
  renderPayloadInput: (value: string, onChange: (value: string) => void) => ReactNode
  onChange: (task: WorkflowTask) => void
}

type EditSession = {
  kind: 'root' | 'child' | 'edit' | 'copy'
  path?: ConfigPath
  value: ParamMappingConfig
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

function includesValue<T extends readonly string[]>(values: T, value: unknown): value is T[number] {
  return typeof value === 'string' && values.includes(value as T[number])
}

function normalizeConfig(value: unknown): ParamMappingConfig {
  const raw = isRecord(value) ? value : {}
  return {
    ...raw,
    sourceParamKey: typeof raw.sourceParamKey === 'string' ? raw.sourceParamKey : '',
    sourceParamType: includesValue(PARAM_TYPES, raw.sourceParamType) ? raw.sourceParamType : 'NONE',
    targetParamKey: typeof raw.targetParamKey === 'string' ? raw.targetParamKey : '',
    targetParamType: includesValue(PARAM_TYPES, raw.targetParamType) ? raw.targetParamType : 'STRING',
    mappingType: includesValue(MAPPING_TYPES, raw.mappingType) ? raw.mappingType : 'NAME',
    mappingRule: typeof raw.mappingRule === 'string' ? raw.mappingRule : '',
    paramDesc: typeof raw.paramDesc === 'string' ? raw.paramDesc : '',
    childParams: Array.isArray(raw.childParams) ? raw.childParams.map(normalizeConfig) : [],
  }
}

export function readParamMappingConfigs(task: WorkflowTask): ParamMappingConfig[] {
  const raw = task.inputParameters?.paramsConfig
  return Array.isArray(raw) ? raw.map(normalizeConfig) : []
}

export function countParamMappingConfigs(configs: ParamMappingConfig[]): number {
  return configs.reduce((count, config) => count + 1 + countParamMappingConfigs(config.childParams), 0)
}

function emptyConfig(): ParamMappingConfig {
  return {
    sourceParamKey: '',
    sourceParamType: 'STRING',
    targetParamKey: '',
    targetParamType: 'STRING',
    mappingType: 'NAME',
    mappingRule: '',
    paramDesc: '',
    childParams: [],
  }
}

function pathKey(path: ConfigPath) {
  return path.join('.')
}

function updateAtPath(
  configs: ParamMappingConfig[],
  path: ConfigPath,
  update: (config: ParamMappingConfig) => ParamMappingConfig,
): ParamMappingConfig[] {
  const [index, ...rest] = path
  return configs.map((config, configIndex) => {
    if (configIndex !== index) return config
    if (!rest.length) return update(config)
    return { ...config, childParams: updateAtPath(config.childParams, rest, update) }
  })
}

function removeAtPath(configs: ParamMappingConfig[], path: ConfigPath): ParamMappingConfig[] {
  const [index, ...rest] = path
  if (!rest.length) return configs.filter((_, configIndex) => configIndex !== index)
  return configs.map((config, configIndex) => (
    configIndex === index
      ? { ...config, childParams: removeAtPath(config.childParams, rest) }
      : config
  ))
}

function insertAfterPath(configs: ParamMappingConfig[], path: ConfigPath, value: ParamMappingConfig): ParamMappingConfig[] {
  const [index, ...rest] = path
  if (!rest.length) return [...configs.slice(0, index + 1), value, ...configs.slice(index + 1)]
  return configs.map((config, configIndex) => (
    configIndex === index
      ? { ...config, childParams: insertAfterPath(config.childParams, rest, value) }
      : config
  ))
}

function flattenConfigs(
  configs: ParamMappingConfig[],
  parent: ConfigPath = [],
): Array<{ config: ParamMappingConfig; path: ConfigPath; depth: number }> {
  return configs.flatMap((config, index) => {
    const path = [...parent, index]
    return [
      { config, path, depth: path.length - 1 },
      ...flattenConfigs(config.childParams, path),
    ]
  })
}

function mappingLabel(type: MappingType) {
  switch (type) {
    case 'NAME': return '值映射'
    case 'CONSTANT': return '固定值'
    case 'EXPRESSION': return '表达式'
    case 'BEAN_EXPRESSION': return 'Bean 表达式'
    case 'SINGLE_MAP': return '单值映射'
  }
}

function normalizeForSave(value: ParamMappingConfig): ParamMappingConfig {
  return {
    ...value,
    sourceParamKey: value.sourceParamType === 'NONE' ? '' : value.sourceParamKey?.trim(),
    targetParamKey: value.targetParamKey.trim(),
    mappingRule: value.mappingRule?.trim(),
    paramDesc: value.paramDesc?.trim(),
    childParams: CONTAINER_TYPES.has(value.targetParamType) ? value.childParams : [],
  }
}

export function ParamMapTaskInspector({ task, header, renderPayloadInput, onChange }: ParamMapTaskInspectorProps) {
  const inputParameters = task.inputParameters ?? {}
  const payload = typeof inputParameters.payload === 'string' ? inputParameters.payload : ''
  const configs = readParamMappingConfigs(task)
  const [view, setView] = useState<EditorView>('JSON')
  const [jsonDraft, setJsonDraft] = useState(() => JSON.stringify(configs, null, 2))
  const [jsonError, setJsonError] = useState('')
  const [visualOpen, setVisualOpen] = useState(false)
  const [editSession, setEditSession] = useState<EditSession | null>(null)

  useEffect(() => {
    const nextConfigs = readParamMappingConfigs(task)
    setJsonDraft(JSON.stringify(nextConfigs, null, 2))
    setJsonError('')
    setView('JSON')
    setVisualOpen(false)
    setEditSession(null)
  }, [task.taskReferenceName])

  const commit = (nextPayload: string, nextConfigs: ParamMappingConfig[]) => {
    onChange({
      ...task,
      inputParameters: {
        payload: nextPayload,
        paramsConfig: nextConfigs,
      },
    })
  }

  const commitConfigs = (nextConfigs: ParamMappingConfig[]) => {
    setJsonDraft(JSON.stringify(nextConfigs, null, 2))
    setJsonError('')
    commit(payload, nextConfigs)
  }

  const updateJson = (nextValue: string) => {
    setJsonDraft(nextValue)
    try {
      const parsed = JSON.parse(nextValue)
      if (!Array.isArray(parsed) || parsed.some((item) => !isRecord(item))) {
        throw new Error('paramsConfig 必须是 JSON 对象数组')
      }
      setJsonError('')
      commit(payload, parsed.map(normalizeConfig))
    } catch (error) {
      setJsonError(error instanceof Error ? error.message : 'paramsConfig JSON 无效')
    }
  }

  const openRootEditor = () => setEditSession({ kind: 'root', value: emptyConfig() })
  const openChildEditor = (path: ConfigPath) => setEditSession({ kind: 'child', path, value: emptyConfig() })
  const openEditEditor = (path: ConfigPath, value: ParamMappingConfig) => setEditSession({ kind: 'edit', path, value: normalizeConfig(value) })
  const openCopyEditor = (path: ConfigPath, value: ParamMappingConfig) => setEditSession({ kind: 'copy', path, value: normalizeConfig(value) })

  const saveEditSession = (value: ParamMappingConfig) => {
    if (!editSession) return
    const nextValue = normalizeForSave(value)
    if (editSession.kind === 'root') {
      commitConfigs([...configs, nextValue])
    } else if (editSession.kind === 'child' && editSession.path) {
      commitConfigs(updateAtPath(configs, editSession.path, (config) => ({
        ...config,
        childParams: [...config.childParams, nextValue],
      })))
    } else if (editSession.kind === 'edit' && editSession.path) {
      commitConfigs(updateAtPath(configs, editSession.path, () => nextValue))
    } else if (editSession.kind === 'copy' && editSession.path) {
      commitConfigs(insertAfterPath(configs, editSession.path, nextValue))
    }
    setEditSession(null)
  }

  const actions = {
    addChild: openChildEditor,
    edit: openEditEditor,
    copy: openCopyEditor,
    remove: (path: ConfigPath) => commitConfigs(removeAtPath(configs, path)),
  }

  return (
    <div className="workflow-form-panel inline-task-inspector param-map-task-inspector">
      {header}

      <section className="inspector-section workflow-collapsible-section expanded">
        <div className="workflow-section-toggle param-map-section-heading">
          <h2>映射输入</h2>
        </div>
        <div className="workflow-section-body">
          <div className="param-map-payload-row">
            <label className="inspector-field">
              <span>参数名</span>
              <input value="payload" disabled />
            </label>
            <label className="inspector-field param-map-payload-value">
              <span>映射值 <strong>*</strong></span>
              {renderPayloadInput(payload, (value) => commit(value, configs))}
            </label>
            <span className="param-map-object-badge">JSON Object</span>
          </div>
          <p className="param-map-helper">payload 必须引用工作流输入或上游任务输出，并在运行时解析为 JSON Object。</p>
        </div>
      </section>

      <section className="inspector-section workflow-collapsible-section expanded param-map-config-section">
        <div className="param-map-config-header">
          <div className="param-map-config-title">
            <span className="param-map-config-title-icon"><ArrowsLeftRight size={18} weight="bold" /></span>
            <div>
              <div className="param-map-config-heading">
                <h2>映射配置</h2>
                <span>{countParamMappingConfigs(configs)} 个字段</span>
              </div>
              <p>定义 payload 到 result 的字段转换规则</p>
            </div>
          </div>
          <div className="param-map-config-actions">
            <button type="button" className="param-map-visual-button" disabled={Boolean(jsonError)} onClick={() => setVisualOpen(true)}>
              <TreeStructure size={15} weight="bold" />
              可视化映射
            </button>
            {view === 'PARAMETERS' ? (
              <button type="button" className="param-map-add-button" onClick={openRootEditor}>
                <Plus size={15} weight="bold" />
                新增字段
              </button>
            ) : null}
          </div>
        </div>

        <div className="param-map-command-bar">
          <div className="param-map-view-switch" role="radiogroup" aria-label="映射配置编辑方式">
            <button type="button" role="radio" aria-checked={view === 'JSON'} className={view === 'JSON' ? 'active' : ''} onClick={() => setView('JSON')}>
              <Code size={15} />
              JSON
            </button>
            <button type="button" role="radio" aria-checked={view === 'PARAMETERS'} className={view === 'PARAMETERS' ? 'active' : ''} disabled={Boolean(jsonError)} onClick={() => setView('PARAMETERS')}>
              <ListBullets size={15} />
              Parameters
            </button>
          </div>
          <div className="param-map-flow-summary" aria-label="映射数据流">
            <span><b>INPUT</b>payload</span>
            <ArrowRight size={14} weight="bold" />
            <span><b>OUTPUT</b>output</span>
          </div>
        </div>

        <div className="param-map-config-content">
          {view === 'JSON' ? (
            <div className={`param-map-json-editor ${jsonError ? 'has-error' : ''}`}>
              <div className="param-map-code-label"><span>Code</span><strong>JSON</strong></div>
              <Editor
                height="420px"
                language="json"
                value={jsonDraft}
                onChange={(value) => updateJson(value ?? '')}
                options={{
                  automaticLayout: true,
                  folding: true,
                  fontFamily: 'SFMono-Regular, Consolas, Liberation Mono, monospace',
                  fontSize: 13,
                  glyphMargin: false,
                  lineHeight: 21,
                  lineNumbers: 'on',
                  lineNumbersMinChars: 3,
                  minimap: { enabled: false },
                  padding: { top: 16, bottom: 16 },
                  scrollBeyondLastLine: false,
                  tabSize: 2,
                  wordWrap: 'on',
                }}
              />
              {jsonError ? <p className="inline-error">{jsonError}</p> : null}
            </div>
          ) : (
            <ParameterConfigTable configs={configs} onAddRoot={openRootEditor} {...actions} />
          )}
        </div>
      </section>

      {visualOpen ? createPortal(
        <ParamMapVisualization
          configs={configs}
          onClose={() => setVisualOpen(false)}
          onAddRoot={openRootEditor}
          {...actions}
        />,
        document.body,
      ) : null}

      {editSession ? createPortal(
        <ParamConfigDialog
          session={editSession}
          onCancel={() => setEditSession(null)}
          onSave={saveEditSession}
        />,
        document.body,
      ) : null}
    </div>
  )
}

type ConfigActions = {
  addChild: (path: ConfigPath) => void
  edit: (path: ConfigPath, config: ParamMappingConfig) => void
  copy: (path: ConfigPath, config: ParamMappingConfig) => void
  remove: (path: ConfigPath) => void
}

function ParameterConfigTable({
  configs,
  onAddRoot,
  addChild,
  edit,
  copy,
  remove,
}: { configs: ParamMappingConfig[]; onAddRoot: () => void } & ConfigActions) {
  const rows = useMemo(() => flattenConfigs(configs), [configs])
  return (
    <div className="param-map-parameters">
      <div className="param-map-table-scroll">
        <table className="param-map-table">
          <thead>
            <tr>
              <th>源参数名</th>
              <th>源类型</th>
              <th>目标参数名</th>
              <th>目标类型</th>
              <th>映射类型</th>
              <th>映射规则</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(({ config, path, depth }) => (
              <tr key={pathKey(path)}>
                <td title={config.sourceParamKey || '(无源参数)'}>
                  <span className="param-map-tree-name" style={{ paddingLeft: depth * 18 }}>
                    {depth ? <span className="param-map-tree-elbow">└</span> : null}
                    {config.sourceParamKey || '(无源参数)'}
                  </span>
                </td>
                <td><TypeBadge value={config.sourceParamType} side="source" /></td>
                <td title={config.targetParamKey}>{config.targetParamKey || '-'}</td>
                <td><TypeBadge value={config.targetParamType} side="target" /></td>
                <td><span className="param-map-mapping-badge">{mappingLabel(config.mappingType)}</span></td>
                <td className="param-map-rule-cell" title={config.mappingRule}>{config.mappingRule || '-'}</td>
                <td><ConfigActionButtons config={config} path={path} addChild={addChild} edit={edit} copy={copy} remove={remove} /></td>
              </tr>
            ))}
          </tbody>
        </table>
        {!rows.length ? (
          <div className="param-map-empty">
            <div className="param-map-empty-diagram" aria-hidden="true">
              <span>payload</span>
              <i><ArrowRight size={16} weight="bold" /></i>
              <span>result</span>
            </div>
            <strong>还没有字段映射</strong>
            <p>添加目标字段，建立源数据到输出结果的转换关系。</p>
            <button type="button" onClick={onAddRoot}><Plus size={15} weight="bold" />新增第一个字段</button>
          </div>
        ) : null}
      </div>
      {rows.length ? (
        <button type="button" className="param-map-table-add" onClick={onAddRoot}><PlusCircle size={16} />新增根字段</button>
      ) : null}
    </div>
  )
}

function TypeBadge({ value, side }: { value: ParamType; side: 'source' | 'target' }) {
  return <span className={`param-map-type-badge ${side}`}>{value}</span>
}

function ConfigActionButtons({ config, path, addChild, edit, copy, remove }: { config: ParamMappingConfig; path: ConfigPath } & ConfigActions) {
  return (
    <div className="param-map-actions">
      <button type="button" title="编辑" onClick={() => edit(path, config)}><PencilSimple size={15} /></button>
      <button type="button" title="复制" onClick={() => copy(path, config)}><Copy size={15} /></button>
      {CONTAINER_TYPES.has(config.targetParamType) ? (
        <button type="button" className="param-map-child-action" title="在当前节点下添加子节点" onClick={() => addChild(path)}>
          <Plus size={14} weight="bold" />
          <span>子节点</span>
        </button>
      ) : null}
      <button type="button" className="danger" title="删除" onClick={() => remove(path)}><Trash size={15} /></button>
    </div>
  )
}

function ParamConfigDialog({ session, onCancel, onSave }: { session: EditSession; onCancel: () => void; onSave: (value: ParamMappingConfig) => void }) {
  const [value, setValue] = useState(() => normalizeConfig(session.value))
  const [error, setError] = useState('')
  const title = session.kind === 'edit' ? '编辑映射配置'
    : session.kind === 'copy' ? '复制映射配置'
      : session.kind === 'child' ? '新增子节点'
        : '新增映射配置'

  const submit = () => {
    if (!value.targetParamKey.trim()) {
      setError('目标参数名不能为空')
      return
    }
    onSave(value)
  }

  return (
    <div className="param-map-dialog-backdrop" onMouseDown={onCancel}>
      <section className="param-map-dialog" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(event) => event.stopPropagation()}>
        <header><h2>{title}</h2><button type="button" title="关闭" onClick={onCancel}><X size={20} /></button></header>
        <div className="param-map-dialog-body">
          <div className="param-map-dialog-grid">
            <label><span>源参数名</span><input disabled={value.sourceParamType === 'NONE'} value={value.sourceParamKey ?? ''} onChange={(event) => setValue({ ...value, sourceParamKey: event.target.value })} /></label>
            <label><span>源参数类型</span><select value={value.sourceParamType} onChange={(event) => setValue({ ...value, sourceParamType: event.target.value as ParamType })}>{PARAM_TYPES.map((type) => <option key={type}>{type}</option>)}</select></label>
            <label><span>目标参数名 <strong>*</strong></span><input value={value.targetParamKey} onChange={(event) => setValue({ ...value, targetParamKey: event.target.value })} /></label>
            <label><span>目标参数类型</span><select value={value.targetParamType} onChange={(event) => setValue({ ...value, targetParamType: event.target.value as ParamType })}>{PARAM_TYPES.map((type) => <option key={type}>{type}</option>)}</select></label>
            <label><span>映射类型</span><select value={value.mappingType} onChange={(event) => setValue({ ...value, mappingType: event.target.value as MappingType })}>{MAPPING_TYPES.map((type) => <option key={type} value={type}>{mappingLabel(type)}</option>)}</select></label>
            <label className="full"><span>映射规则</span><textarea rows={4} value={value.mappingRule ?? ''} onChange={(event) => setValue({ ...value, mappingRule: event.target.value })} placeholder="固定值或表达式；值映射可留空" /></label>
            <label className="full"><span>参数描述</span><textarea rows={2} value={value.paramDesc ?? ''} onChange={(event) => setValue({ ...value, paramDesc: event.target.value })} /></label>
          </div>
          {error ? <p className="inline-error">{error}</p> : null}
        </div>
        <footer><button type="button" onClick={onCancel}>取消</button><button type="button" className="primary" onClick={submit}>确定</button></footer>
      </section>
    </div>
  )
}

function ParamMapVisualization({ configs, onClose, onAddRoot, addChild, edit, copy, remove }: { configs: ParamMappingConfig[]; onClose: () => void; onAddRoot: () => void } & ConfigActions) {
  const allRows = useMemo(() => flattenConfigs(configs), [configs])
  const containerKeys = useMemo(() => allRows.filter(({ config }) => config.childParams.length).map(({ path }) => pathKey(path)), [allRows])
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set(containerKeys))
  const [zoom, setZoom] = useState(1)

  const toggle = (path: ConfigPath) => {
    const key = pathKey(path)
    setExpanded((current) => {
      const next = new Set(current)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  const visibleRows = allRows.filter(({ path }) => {
    for (let length = 1; length < path.length; length += 1) {
      if (!expanded.has(pathKey(path.slice(0, length)))) return false
    }
    return true
  })

  return (
    <div className="param-map-visual-backdrop">
      <section className="param-map-visual-workspace" role="dialog" aria-modal="true" aria-label="参数映射可视化">
        <header className="param-map-visual-header">
          <div><strong>参数映射可视化</strong><span>payload → output</span></div>
          <button type="button" title="关闭" onClick={onClose}><X size={21} /></button>
        </header>
        <div className="param-map-visual-toolbar">
          <div className="param-map-visual-legend"><span className="source">源参数</span><span>→</span><span className="target">目标参数</span></div>
          <div className="param-map-visual-tools">
            <button type="button" onClick={onAddRoot}><Plus size={15} />新增配置</button>
            <button type="button" onClick={() => setExpanded(new Set(containerKeys))}>展开全部</button>
            <button type="button" onClick={() => setExpanded(new Set())}>折叠全部</button>
            <button type="button" title="缩小" onClick={() => setZoom((value) => Math.max(0.7, value - 0.1))}><MagnifyingGlassMinus size={16} /></button>
            <button type="button" title="放大" onClick={() => setZoom((value) => Math.min(1.3, value + 0.1))}><MagnifyingGlassPlus size={16} /></button>
            <span>{Math.round(zoom * 100)}%</span>
          </div>
        </div>
        <div className="param-map-visual-canvas">
          <div className="param-map-visual-content" style={{ transform: `scale(${zoom})` }}>
            {visibleRows.map(({ config, path, depth }) => {
              const hasChildren = config.childParams.length > 0
              const isExpanded = expanded.has(pathKey(path))
              return (
                <div className="param-map-visual-row" key={pathKey(path)} style={{ marginLeft: depth * 48 }}>
                  {depth ? <span className="param-map-visual-connector" /> : null}
                  <button type="button" className={`param-map-expand ${hasChildren ? '' : 'empty'}`} disabled={!hasChildren} onClick={() => toggle(path)}>
                    {hasChildren ? (isExpanded ? <CaretDown size={15} /> : <CaretRight size={15} />) : <Minus size={12} />}
                  </button>
                  <div className="param-map-visual-source"><strong>{config.sourceParamKey || '(无源参数)'}</strong><TypeBadge value={config.sourceParamType} side="source" /></div>
                  <div className="param-map-visual-arrow"><span>{mappingLabel(config.mappingType)}</span><i /></div>
                  <div className="param-map-visual-target"><strong>{config.targetParamKey || '-'}</strong><TypeBadge value={config.targetParamType} side="target" /></div>
                  <ConfigActionButtons config={config} path={path} addChild={addChild} edit={edit} copy={copy} remove={remove} />
                </div>
              )
            })}
            {!visibleRows.length ? <div className="param-map-visual-empty">暂无映射配置</div> : null}
          </div>
        </div>
      </section>
    </div>
  )
}
