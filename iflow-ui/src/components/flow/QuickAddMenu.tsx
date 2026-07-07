import { useMemo, useRef, useState, type ComponentType } from 'react'
import {
  ArrowsLeftRight,
  BracketsCurly,
  Database,
  Diamond,
  DotsThree,
  FileJs,
  GitBranch,
  Globe,
  MagnifyingGlass,
  Repeat,
  X,
  type IconProps,
} from '@phosphor-icons/react'
import type { AddableTaskType } from '@/flow/workflowEditor'
import { addableTaskTypes } from '@/flow/workflowEditor'
import { TaskType, taskTypeLabel } from '@/flow/taskType'

type QuickAddMenuProps = {
  open: boolean
  x: number
  y: number
  onSelect: (type: AddableTaskType) => void
  onClose: () => void
}

type TaskOption = {
  type: AddableTaskType
  label: string
  description: string
  icon: ComponentType<IconProps>
}

const MENU_WIDTH = 360
const MENU_MAX_HEIGHT = 380
const MENU_OFFSET = 12
const VIEWPORT_PADDING = 16

const taskDescriptions: Record<AddableTaskType, string> = {
  [TaskType.HTTP]: '调用 API 或微服务。',
  [TaskType.INLINE]: '运行轻量 JavaScript 脚本。',
  [TaskType.JSON_JQ_TRANSFORM]: '使用 JQ 转换 JSON 数据。',
  [TaskType.SWITCH]: '根据表达式选择后续分支。',
  [TaskType.FORK_JOIN]: '并行执行多个任务分支并在 JOIN 汇聚。',
  [TaskType.DO_WHILE]: '按条件重复执行循环体任务。',
  [TaskType.PARAM_MAP]: '映射和整理任务参数。',
  [TaskType.SPI]: '通过扩展名路由 Java SPI 实现。',
  [TaskType.JDBC]: '执行数据库查询。',
  [TaskType.WEBSERVICE]: '调用 Web Service 服务。',
}

const taskIcons: Record<AddableTaskType, ComponentType<IconProps>> = {
  [TaskType.HTTP]: Globe,
  [TaskType.INLINE]: FileJs,
  [TaskType.JSON_JQ_TRANSFORM]: BracketsCurly,
  [TaskType.SWITCH]: Diamond,
  [TaskType.FORK_JOIN]: GitBranch,
  [TaskType.DO_WHILE]: Repeat,
  [TaskType.PARAM_MAP]: ArrowsLeftRight,
  [TaskType.SPI]: BracketsCurly,
  [TaskType.JDBC]: Database,
  [TaskType.WEBSERVICE]: Globe,
}

function taskDisplayLabel(type: AddableTaskType) {
  switch (type) {
    case TaskType.SWITCH:
      return '分支路由'
    case TaskType.JSON_JQ_TRANSFORM:
      return 'JSON JQ'
    case TaskType.PARAM_MAP:
      return 'PARAM MAP'
    case TaskType.FORK_JOIN:
      return 'FORK JOIN'
    case TaskType.DO_WHILE:
      return 'DO WHILE'
    case TaskType.WEBSERVICE:
      return 'Web Service'
    default:
      return type
  }
}

function menuPosition(x: number, y: number) {
  if (typeof window === 'undefined') return { left: x, top: y + MENU_OFFSET }
  return {
    left: Math.max(VIEWPORT_PADDING, Math.min(x, window.innerWidth - MENU_WIDTH - VIEWPORT_PADDING)),
    top: Math.max(VIEWPORT_PADDING, Math.min(y + MENU_OFFSET, window.innerHeight - MENU_MAX_HEIGHT - VIEWPORT_PADDING)),
  }
}

export function QuickAddMenu({ open, x, y, onSelect, onClose }: QuickAddMenuProps) {
  const [query, setQuery] = useState('')
  const searchRef = useRef<HTMLInputElement | null>(null)

  const taskOptions = useMemo<TaskOption[]>(
    () =>
      addableTaskTypes.map((type) => ({
        type,
        label: taskDisplayLabel(type),
        description: taskDescriptions[type] ?? taskTypeLabel(type),
        icon: taskIcons[type] ?? DotsThree,
      })),
    [],
  )

  const filteredOptions = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return taskOptions
    return taskOptions.filter((item) =>
      [item.label, item.type, taskTypeLabel(item.type), item.description]
        .some((value) => value.toLowerCase().includes(keyword)),
    )
  }, [query, taskOptions])

  if (!open) return null
  const position = menuPosition(x, y)

  const selectTask = (type: AddableTaskType) => {
    setQuery('')
    onSelect(type)
  }

  return (
    <>
      <button type="button" className="iflow-menu-backdrop" aria-label="关闭任务菜单" onClick={onClose} />
      <div className="iflow-quick-add-menu" style={position} role="dialog" aria-label="添加任务">
        <div className="quick-add-search-header">
          <label className="quick-add-search-box">
            <MagnifyingGlass size={20} weight="bold" />
            <input
              ref={searchRef}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索任务..."
              autoFocus
            />
            {query ? (
              <button type="button" aria-label="清空搜索" onClick={() => {
                setQuery('')
                searchRef.current?.focus()
              }}>
                <X size={16} weight="bold" />
              </button>
            ) : null}
          </label>
        </div>

        {!query.trim() ? (
          <div className="quick-add-section">
            <div className="quick-add-section-heading">
              <span>快速添加</span>
              <button type="button" onClick={() => searchRef.current?.focus()}>
                <DotsThree size={18} weight="bold" />
                更多任务
              </button>
            </div>
            <div className="quick-add-grid">
              {taskOptions.map((item) => {
                const Icon = item.icon
                return (
                  <button type="button" className="quick-add-grid-item" key={item.type} title={`${item.label} · ${taskTypeLabel(item.type)}`} onClick={() => selectTask(item.type)}>
                    <Icon size={24} />
                    <span>{item.label}</span>
                  </button>
                )
              })}
            </div>
          </div>
        ) : (
          <div className="quick-add-results-section">
            <div className="quick-add-results-heading">
              <span>搜索结果</span>
              {filteredOptions.length > 3 ? <em>+{filteredOptions.length - 3} 更多</em> : null}
            </div>
            <div className="quick-add-results-list">
              {filteredOptions.length ? filteredOptions.slice(0, 3).map((item) => {
                const Icon = item.icon
                return (
                  <button type="button" className="quick-add-result-item" key={item.type} onClick={() => selectTask(item.type)}>
                    <span className="quick-add-result-icon"><Icon size={24} /></span>
                    <span className="quick-add-result-copy">
                      <strong>{item.label}</strong>
                      <small>{item.description}</small>
                    </span>
                  </button>
                )
              }) : (
                <div className="quick-add-empty">
                  <MagnifyingGlass size={24} />
                  <span>未找到任务</span>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </>
  )
}
