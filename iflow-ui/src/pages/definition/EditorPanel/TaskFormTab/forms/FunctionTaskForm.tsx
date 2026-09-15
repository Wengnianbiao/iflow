import { useEffect, useRef, useState } from 'react'
import { CaretDownIcon, CheckIcon } from '@phosphor-icons/react'
import {
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  valueText,
} from '../../InspectorFields'
import { TaskPanelHeader } from './TaskPanelHeader'
import type { TaskFormProps } from './types'

const FUNCTION_OPTIONS = [
  { value: 'JSON_PARSE', label: 'JSON.parse' },
  { value: 'XML_PARSE', label: 'XML.parse' },
] as const

function FunctionSelect({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([])
  const selected = FUNCTION_OPTIONS.find((option) => option.value === value)

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
      className={`function-select ${open ? 'open' : ''}`}
      ref={rootRef}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setOpen(false)
      }}
    >
      <button
        type="button"
        className="function-select-trigger"
        ref={triggerRef}
        role="combobox"
        aria-label="转换函数"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls="function-select-options"
        onClick={() => setOpen((current) => !current)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown') {
            event.preventDefault()
            openAndFocus(0)
          } else if (event.key === 'ArrowUp') {
            event.preventDefault()
            openAndFocus(FUNCTION_OPTIONS.length - 1)
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
        <div className="function-select-options" id="function-select-options" role="listbox" aria-label="转换函数选项">
          {FUNCTION_OPTIONS.map((option, index) => {
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
                    optionRefs.current[(index + 1) % FUNCTION_OPTIONS.length]?.focus()
                  } else if (event.key === 'ArrowUp') {
                    event.preventDefault()
                    optionRefs.current[(index - 1 + FUNCTION_OPTIONS.length) % FUNCTION_OPTIONS.length]?.focus()
                  } else if (event.key === 'Home') {
                    event.preventDefault()
                    optionRefs.current[0]?.focus()
                  } else if (event.key === 'End') {
                    event.preventDefault()
                    optionRefs.current[FUNCTION_OPTIONS.length - 1]?.focus()
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

export function FunctionTaskForm({ task, variableOptions, onChange }: TaskFormProps) {
  const inputParameters = task.inputParameters ?? {}
  const functionName = String(inputParameters.function ?? 'JSON_PARSE')
  const value = valueText(inputParameters.value)

  const updateInputParameters = (changes: Record<string, unknown>) => {
    onChange({
      ...task,
      inputParameters: {
        ...inputParameters,
        ...changes,
      },
    })
  }

  return (
    <div className="workflow-form-panel function-task-inspector">
      <TaskPanelHeader task={task} label="函数转换" onChange={onChange} />

      <WorkflowCollapsibleSection title="转换配置">
        <div className="inspector-field full">
          <span>转换函数</span>
          <FunctionSelect
            value={functionName}
            onChange={(nextFunction) => updateInputParameters({ function: nextFunction })}
          />
        </div>
        <label className="inspector-field full">
          <span>解析表达式</span>
          <VariableExpressionInput
            value={value}
            options={variableOptions}
            onChange={(nextValue) => updateInputParameters({ value: nextValue })}
          />
        </label>
        <p className="inspector-empty">
          转换结果直接作为任务输出；JSON 支持对象、数组、字符串、数字、布尔值和 null。
        </p>
      </WorkflowCollapsibleSection>
    </div>
  )
}
