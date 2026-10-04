import { useEffect, useId, useMemo, useRef, useState, type ReactNode } from 'react'
import { BracketsCurlyIcon, CaretDownIcon, CheckIcon, TrashIcon } from '@phosphor-icons/react'
import { useValidationFocus } from '../validation/ValidationScope'

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

export function filterVariableOptions(options: string[], value: string, forceAll: boolean) {
  if (forceAll || !value) return options
  const currentValue = value.toLowerCase()
  if (currentValue.endsWith('$')) return options.filter((option) => option.toLowerCase().startsWith('$'))
  const matchedValue = currentValue.match(VARIABLE_REGEX)?.[0]
  if (matchedValue) return options.filter((option) => option.toLowerCase().startsWith(matchedValue))
  return options.filter((option) => option.toLowerCase().startsWith(currentValue))
}

export function replaceVariableExpression(value: string, option: string) {
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
  const focus = useValidationFocus()
  useEffect(() => {
    if (focus && focus.tab !== 'code') setExpanded(true)
  }, [focus])

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

export function InspectorSelect({
  value,
  options,
  ariaLabel,
  onChange,
}: {
  value: string
  options: ReadonlyArray<{ value: string; label: string }>
  ariaLabel: string
  onChange: (value: string) => void
}) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([])
  const optionsId = useId()
  const selected = options.find((option) => option.value === value)

  useEffect(() => {
    if (!open) return
    const closeOnOutsidePointer = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', closeOnOutsidePointer)
    return () => document.removeEventListener('pointerdown', closeOnOutsidePointer)
  }, [open])

  const openAndFocus = (index: number) => {
    setOpen(true)
    requestAnimationFrame(() => optionRefs.current[index]?.focus())
  }

  const closeAndFocusTrigger = () => {
    setOpen(false)
    triggerRef.current?.focus()
  }

  return (
    <div
      className={`inspector-select ${open ? 'open' : ''}`}
      ref={rootRef}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setOpen(false)
      }}
    >
      <button
        type="button"
        className="inspector-select-trigger"
        ref={triggerRef}
        role="combobox"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={optionsId}
        onClick={() => setOpen((current) => !current)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown') {
            event.preventDefault()
            openAndFocus(0)
          } else if (event.key === 'ArrowUp') {
            event.preventDefault()
            openAndFocus(options.length - 1)
          } else if (event.key === 'Escape' && open) {
            event.preventDefault()
            setOpen(false)
          }
        }}
      >
        <span>{selected?.label ?? value}</span>
        <CaretDownIcon size={18} weight="bold" aria-hidden="true" />
      </button>
      {open ? (
        <div className="inspector-select-options" id={optionsId} role="listbox" aria-label={`${ariaLabel}选项`}>
          {options.map((option, index) => {
            const isSelected = option.value === value
            return (
              <button
                type="button"
                className={isSelected ? 'selected' : ''}
                ref={(element) => { optionRefs.current[index] = element }}
                role="option"
                aria-selected={isSelected}
                key={option.value}
                onClick={() => {
                  onChange(option.value)
                  setOpen(false)
                  triggerRef.current?.focus()
                }}
                onKeyDown={(event) => {
                  if (event.key === 'ArrowDown') {
                    event.preventDefault()
                    optionRefs.current[(index + 1) % options.length]?.focus()
                  } else if (event.key === 'ArrowUp') {
                    event.preventDefault()
                    optionRefs.current[(index - 1 + options.length) % options.length]?.focus()
                  } else if (event.key === 'Home') {
                    event.preventDefault()
                    optionRefs.current[0]?.focus()
                  } else if (event.key === 'End') {
                    event.preventDefault()
                    optionRefs.current[options.length - 1]?.focus()
                  } else if (event.key === 'Escape') {
                    event.preventDefault()
                    closeAndFocusTrigger()
                  }
                }}
              >
                <span>{option.label}</span>
                {isSelected ? <CheckIcon size={17} weight="bold" aria-hidden="true" /> : null}
              </button>
            )
          })}
        </div>
      ) : null}
    </div>
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
