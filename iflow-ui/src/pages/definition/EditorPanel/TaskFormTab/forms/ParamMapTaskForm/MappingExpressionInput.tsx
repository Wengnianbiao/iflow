import { CheckIcon, CopyIcon } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { InlineHighlightedCode } from '@/components/flow/InlineScriptCode'
import { formatJavaScriptSource } from '../javascriptFormatting'

export function MappingExpressionInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const [code, setCode] = useState(value)
  const [copied, setCopied] = useState(false)
  const versionRef = useRef(0)
  const localValueRef = useRef<string>()
  const copyTimerRef = useRef<number>()

  useEffect(() => {
    if (value === localValueRef.current) return
    setCode(value)
    const version = ++versionRef.current
    void formatJavaScriptSource(value).then((formatted) => {
      if (versionRef.current === version) setCode(formatted)
    }).catch(() => { /* Keep incomplete expressions editable. */ })
    return () => { versionRef.current += 1 }
  }, [value])

  useEffect(() => () => {
    versionRef.current += 1
    window.clearTimeout(copyTimerRef.current)
  }, [])

  const formatDisplay = () => {
    const version = ++versionRef.current
    void formatJavaScriptSource(code).then((formatted) => {
      if (versionRef.current !== version) return
      setCode(formatted)
    }).catch(() => { /* Keep the original draft when formatting cannot parse it. */ })
  }

  const copyCode = async () => {
    const text = await formatJavaScriptSource(code).catch(() => code)
    try {
      await navigator.clipboard.writeText(text)
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      const succeeded = document.execCommand('copy')
      textarea.remove()
      if (!succeeded) return
    }
    setCopied(true)
    window.clearTimeout(copyTimerRef.current)
    copyTimerRef.current = window.setTimeout(() => setCopied(false), 2200)
  }

  return (
    <div className="param-map-expression-input">
      <div className="param-map-expression-heading">
        <span>JS 表达式</span>
        <button type="button" title={copied ? '已复制' : '复制 JS 表达式'} aria-label="复制 JS 表达式" onClick={() => void copyCode()}>
          {copied ? <CheckIcon size={15} /> : <CopyIcon size={15} />}
        </button>
      </div>
      <div className="param-map-expression-scroll">
        <div className="param-map-expression-code">
          <pre aria-hidden="true"><InlineHighlightedCode code={code + '\n'} format={false} /></pre>
          <textarea aria-label="JS 表达式" value={code} spellCheck={false}
            onFocus={() => { versionRef.current += 1 }}
            onBlur={formatDisplay}
            onChange={(event) => {
              const next = event.target.value
              versionRef.current += 1
              localValueRef.current = next
              setCode(next)
              setCopied(false)
              onChange(next)
            }} />
        </div>
      </div>
    </div>
  )
}
