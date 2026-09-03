import { useMemo, useRef, useState, type ComponentType } from 'react'
import {
  DotsThreeIcon,
  MagnifyingGlassIcon,
  XIcon,
  type IconProps,
} from '@phosphor-icons/react'
import { getAddableTaskPlugins, type AddableTaskType } from '@/tasks'

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
  taskLabel: string
  description: string
  icon: ComponentType<IconProps>
}

const MENU_WIDTH = 360
const MENU_MAX_HEIGHT = 380
const MENU_OFFSET = 12
const VIEWPORT_PADDING = 16

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
      getAddableTaskPlugins().map((plugin) => ({
        type: plugin.type,
        label: plugin.menu!.label,
        taskLabel: plugin.label,
        description: plugin.menu!.description,
        icon: plugin.menu!.icon ?? plugin.icon ?? DotsThreeIcon,
      })),
    [open],
  )

  const filteredOptions = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return taskOptions
    return taskOptions.filter((item) =>
      [item.label, item.type, item.taskLabel, item.description]
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
            <MagnifyingGlassIcon size={20} weight="bold" />
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
                <XIcon size={16} weight="bold" />
              </button>
            ) : null}
          </label>
        </div>

        {!query.trim() ? (
          <div className="quick-add-section">
            <div className="quick-add-section-heading">
              <span>快速添加</span>
              <button type="button" onClick={() => searchRef.current?.focus()}>
                <DotsThreeIcon size={18} weight="bold" />
                更多任务
              </button>
            </div>
            <div className="quick-add-grid">
              {taskOptions.map((item) => {
                const Icon = item.icon
                return (
                  <button type="button" className="quick-add-grid-item" key={item.type} title={`${item.label} · ${item.taskLabel}`} onClick={() => selectTask(item.type)}>
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
                  <MagnifyingGlassIcon size={24} />
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
