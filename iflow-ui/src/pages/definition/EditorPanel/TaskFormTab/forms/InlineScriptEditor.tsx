import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import Editor, { type OnMount } from '@monaco-editor/react'
import { ArrowsInSimpleIcon, ArrowsOutSimpleIcon, CopyIcon, XIcon } from '@phosphor-icons/react'

export function InlineScriptEditor({
  value,
  onChange,
  minHeight = 360,
  language = 'javascript',
  languageLabel = 'ECMASCRIPT',
  codeLabel = 'Code',
  modalTitle = 'Code Editor',
  autoSize = true,
}: {
  value: string
  onChange: (value: string) => void
  minHeight?: number
  language?: string
  languageLabel?: string
  codeLabel?: string
  modalTitle?: string
  autoSize?: boolean
}) {
  const [expanded, setExpanded] = useState(false)
  const [copied, setCopied] = useState(false)
  const [editorHeight, setEditorHeight] = useState(minHeight)
  const copyTimerRef = useRef<number | undefined>(undefined)

  const copyCode = async () => {
    const legacyCopy = () => {
      const textarea = document.createElement('textarea')
      textarea.value = value
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      const succeeded = document.execCommand('copy')
      textarea.remove()
      return succeeded
    }

    try {
      if (!navigator.clipboard) throw new Error('Clipboard API unavailable')
      await navigator.clipboard.writeText(value)
    } catch {
      legacyCopy()
    }
    setCopied(true)
    window.clearTimeout(copyTimerRef.current)
    copyTimerRef.current = window.setTimeout(() => setCopied(false), 2200)
  }

  const handleMount: OnMount = (editor) => {
    const updateHeight = () => {
      const minimumHeight = expanded ? 520 : minHeight
      setEditorHeight(autoSize || expanded
        ? Math.max(minimumHeight, Math.ceil(editor.getContentHeight()))
        : minimumHeight)
    }

    editor.onDidContentSizeChange(updateHeight)
    updateHeight()
  }

  useEffect(() => {
    return () => window.clearTimeout(copyTimerRef.current)
  }, [])

  useEffect(() => {
    if (!expanded) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setExpanded(false)
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [expanded])

  const editor = (
    <div className={`conductor-code-input ${expanded ? 'expanded' : ''}`}>
      <div className="conductor-code-label" aria-hidden="true">
        <span>{codeLabel}</span>
        <strong>{languageLabel}</strong>
      </div>
      <Editor
        height={editorHeight}
        language={language}
        value={value}
        onChange={(nextValue) => onChange(nextValue ?? '')}
        onMount={handleMount}
        options={{
          automaticLayout: true,
          contextmenu: true,
          folding: false,
          fontFamily: 'SFMono-Regular, Consolas, Liberation Mono, monospace',
          fontSize: 13,
          glyphMargin: false,
          lineDecorationsWidth: 8,
          lineHeight: 22,
          lineNumbers: 'on',
          lineNumbersMinChars: 3,
          minimap: { enabled: false },
          overviewRulerBorder: false,
          overviewRulerLanes: 0,
          padding: { top: 16, bottom: 16 },
          renderLineHighlight: 'none',
          scrollBeyondLastLine: false,
          scrollbar: {
            alwaysConsumeMouseWheel: false,
            horizontal: 'hidden',
            vertical: expanded || !autoSize ? 'auto' : 'hidden',
          },
          tabSize: 2,
          wordWrap: 'on',
          wrappingIndent: 'same',
          wrappingStrategy: 'advanced',
        }}
      />
      <div className="conductor-code-actions">
        <button
          type="button"
          title={expanded ? '收起代码编辑器' : '展开代码编辑器'}
          aria-label={expanded ? '收起代码编辑器' : '展开代码编辑器'}
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? <ArrowsInSimpleIcon size={20} /> : <ArrowsOutSimpleIcon size={20} />}
        </button>
        <button type="button" title="复制代码" aria-label="复制代码" onClick={() => void copyCode()}>
          <CopyIcon size={20} />
        </button>
      </div>
    </div>
  )

  return (
    <>
      {expanded
        ? createPortal(
            <div className="code-editor-modal-backdrop" onMouseDown={() => setExpanded(false)}>
              <section className="code-editor-modal" role="dialog" aria-modal="true" aria-labelledby="code-editor-modal-title" onMouseDown={(event) => event.stopPropagation()}>
                <header>
                  <h2 id="code-editor-modal-title">{modalTitle}</h2>
                  <button type="button" title="关闭" aria-label="关闭代码编辑器" onClick={() => setExpanded(false)}>
                    <XIcon size={22} />
                  </button>
                </header>
                <div className="code-editor-modal-content">{editor}</div>
              </section>
            </div>,
            document.body,
          )
        : editor}
      {copied ? <div className="code-copy-toast" role="status">已复制到剪贴板</div> : null}
    </>
  )
}
