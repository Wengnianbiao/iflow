import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { BracketsCurlyIcon, CaretDownIcon, TrashIcon } from '@phosphor-icons/react'

const VARIABLE_REGEX = /\$\{[^}]*$/

export function numberOrUndefined(value: string | null | undefined) {
  if (value == null || value === '') return undefined
  const number = Number(value)
  return Number.isFinite(number) ? number : undefined
}
export function prettyJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2)
}

export function valueText(value: unknown) {
  if (value == null) return ''
  return typeof value === 'string' ? value : JSON.stringify(value)
}

export function uniqueValues(values: string[]) {
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

export function WorkflowCollapsibleSection({
  title,
  children,
  defaultOpen = true,
  className = '',
}: {
  title: string
  children: ReactNode
  defaultOpen?: boolean
  className?: string
}) {
  const [expanded, setExpanded] = useState(defaultOpen)

  return (
    <section className={`inspector-section workflow-collapsible-section ${expanded ? 'expanded' : 'collapsed'} ${className}`.trim()}>
      <button
        type="button"
        className="workflow-section-toggle"
        aria-expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
      >
        <h2>{title}</h2>
        <CaretDownIcon className="workflow-section-toggle-icon" size={18} weight="bold" />
      </button>
      {expanded ? <div className="workflow-section-body">{children}</div> : null}
    </section>
  )
}

export function ParameterRows({
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
          <button type="button" title="删除参数" onClick={() => onDelete(index)}><TrashIcon size={17} /></button>
        </div>
      ))}
    </div>
  )
}

export function VariableExpressionInput({
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
        <BracketsCurlyIcon size={17} weight="bold" />
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
