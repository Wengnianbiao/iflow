import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import Editor, { type BeforeMount, type OnMount } from '@monaco-editor/react'
import { ArrowsInSimpleIcon, ArrowsOutSimpleIcon, CopyIcon, MinusSquareIcon, PlusSquareIcon, XIcon } from '@phosphor-icons/react'
import { registerSoapXmlEditor, SOAP_XML_LANGUAGE, SOAP_XML_THEME, soapXmlHighlightTokens } from './soapXmlEditor'
import { useValidationFieldPath, useValidationFocus, useValidationReadOnly } from '../../../validation/ValidationScope'

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
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)
  const isXml = language === 'xml'
  const focus = useValidationFocus()
  const fieldPath = useValidationFieldPath()
  const readOnly = useValidationReadOnly()
  const focusEditor = () => {
    const editor = editorRef.current
    const model = editor?.getModel()
    if (!editor || !model || readOnly || !fieldPath || !focus || focus.tab === 'code') return
    if (focus.problem.path !== fieldPath && !focus.problem.path.startsWith(`${fieldPath}/`)) return
    const end = model.getPositionAt(model.getValueLength())
    editor.setPosition(end)
    editor.revealPositionInCenter(end)
    editor.focus()
  }
  useEffect(() => { focusEditor() }, [focus])

  const handleBeforeMount: BeforeMount = (monaco) => {
    if (!isXml) return
    registerSoapXmlEditor(monaco)
    monaco.editor.setTheme(SOAP_XML_THEME)
  }

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

  const handleMount: OnMount = (editor, monaco) => {
    editorRef.current = editor
    const model = editor.getModel()
    const decorations = isXml ? editor.createDecorationsCollection() : null
    const highlightXml = () => {
      if (!model || model.isDisposed() || !decorations) return
      decorations.set(soapXmlHighlightTokens(model.getValue()).map((token) => {
        const start = model.getPositionAt(token.start)
        const end = model.getPositionAt(token.end)
        return {
          range: new monaco.Range(start.lineNumber, start.column, end.lineNumber, end.column),
          options: { inlineClassName: `soap-xml-${token.kind}` },
        }
      }))
    }
    highlightXml()
    const highlightListener = isXml ? model?.onDidChangeContent(highlightXml) : undefined
    const updateHeight = () => {
      const minimumHeight = expanded ? 520 : minHeight
      setEditorHeight(autoSize || expanded
        ? Math.max(minimumHeight, Math.ceil(editor.getContentHeight()))
        : minimumHeight)
    }

    const heightListener = editor.onDidContentSizeChange(updateHeight)
    editor.onDidDispose(() => {
      heightListener.dispose()
      highlightListener?.dispose()
      decorations?.clear()
      if (editorRef.current === editor) editorRef.current = null
    })
    updateHeight()
    editor.layout()
    editor.render(true)
    focusEditor()
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
        language={isXml ? SOAP_XML_LANGUAGE : language}
        theme={isXml ? SOAP_XML_THEME : 'light'}
        value={value}
        onChange={(nextValue) => onChange(nextValue ?? '')}
        onMount={handleMount}
        beforeMount={handleBeforeMount}
        options={{
          readOnly,
          automaticLayout: true,
          contextmenu: true,
          folding: isXml,
          foldingStrategy: 'auto',
          showFoldingControls: 'always',
          fontFamily: 'SFMono-Regular, Consolas, Liberation Mono, monospace',
          fontSize: 13,
          glyphMargin: false,
          lineDecorationsWidth: isXml ? 20 : 8,
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
        {isXml ? <>
          <button type="button" title="折叠全部 XML" aria-label="折叠全部 XML" onClick={() => editorRef.current?.trigger('toolbar', 'editor.foldAll', undefined)}>
            <MinusSquareIcon size={20} />
          </button>
          <button type="button" title="展开全部 XML" aria-label="展开全部 XML" onClick={() => editorRef.current?.trigger('toolbar', 'editor.unfoldAll', undefined)}>
            <PlusSquareIcon size={20} />
          </button>
        </> : null}
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
