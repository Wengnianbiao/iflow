import { useEffect, useRef, useState } from 'react'
import Editor, { type BeforeMount, type OnMount, type Monaco } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import { CopyIcon } from '@phosphor-icons/react'
import type { ProblemFocus, WorkflowProblem } from '../validation/types'
import { sourceRange } from '../validation/validateWorkflow'

const SOURCE_EDITOR_THEME = 'iflow-source-light'

export function CodeInspector({ source, problems, focus, readOnly, onChange }: { source: string; problems: WorkflowProblem[]; focus: ProblemFocus | null; readOnly: boolean; onChange: (source: string) => void }) {
  const [copied, setCopied] = useState(false)
  const copyTimerRef = useRef<number | undefined>(undefined)
  const editorRefreshFrameRef = useRef<number | undefined>(undefined)
  const editorRefreshTimerRef = useRef<number | undefined>(undefined)
  const editorDecorationCleanupRef = useRef<(() => void) | undefined>(undefined)
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<Monaco | null>(null)
  const diagnosticsRef = useRef<() => void>(() => {})
  const focusedSequenceRef = useRef<number | undefined>(undefined)
  diagnosticsRef.current = () => {
    const model = editorRef.current?.getModel()
    const monaco = monacoRef.current
    if (!model || !monaco) return
    if (!focus) focusedSequenceRef.current = undefined
    const rangeFor = (problem: WorkflowProblem) => {
      const offsets = problem.offset !== undefined ? { start: problem.offset, end: problem.offset + 1 } : sourceRange(source, problem.path)
      const start = model.getPositionAt(offsets.start); const end = model.getPositionAt(offsets.end)
      return { startLineNumber: start.lineNumber, startColumn: start.column, endLineNumber: end.lineNumber, endColumn: end.column }
    }
    monaco.editor.setModelMarkers(model, 'iflow-validation', problems.map((problem) => ({ ...rangeFor(problem), message: problem.message, severity: problem.severity === 'error' ? monaco.MarkerSeverity.Error : monaco.MarkerSeverity.Warning })))
    if (focus?.tab === 'code' && focus.sequence !== focusedSequenceRef.current) {
      focusedSequenceRef.current = focus.sequence
      const range = rangeFor(focus.problem)
      editorRef.current?.setSelection(range)
      editorRef.current?.revealRangeInCenter(range)
      editorRef.current?.focus()
    }
  }
  useEffect(() => { diagnosticsRef.current() }, [source, problems, focus])

  const handleSourceEditorBeforeMount: BeforeMount = (monaco) => {
    // @monaco-editor/react creates the editor before applying its theme prop.
    // Define and apply the source theme first so both the editor and its minimap
    // never flash the previous (possibly dark) Monaco theme while mounting.
    monaco.editor.defineTheme(SOURCE_EDITOR_THEME, {
      base: 'vs',
      inherit: true,
      rules: [],
      colors: {
        'editor.background': '#FFFFFF',
        'editorGutter.background': '#FFFFFF',
        'minimap.background': '#FFFFFF',
        'minimapSlider.background': '#CBD5E166',
        'minimapSlider.hoverBackground': '#94A3B880',
        'minimapSlider.activeBackground': '#64748B99',
      },
    })
    monaco.editor.setTheme(SOURCE_EDITOR_THEME)
  }

  const copySource = async () => {
    const legacyCopy = () => {
      const textarea = document.createElement('textarea')
      textarea.value = source
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
      await navigator.clipboard.writeText(source)
    } catch {
      legacyCopy()
    }
    setCopied(true)
    window.clearTimeout(copyTimerRef.current)
    copyTimerRef.current = window.setTimeout(() => setCopied(false), 2000)
  }

  const handleSourceEditorMount: OnMount = (editor, monaco) => {
    editorRef.current = editor
    monacoRef.current = monaco
    const model = editor.getModel()
    if (model) {
      monaco.editor.setModelLanguage(model, 'json')
    }

    const decorations = editor.createDecorationsCollection()
    const applyJsonDecorations = () => {
      const currentModel = editor.getModel()
      if (!currentModel || currentModel.isDisposed()) return
      const nextDecorations: Array<Parameters<typeof decorations.set>[0][number]> = []
      const tokenPattern = /"(?:\\.|[^"\\])*"\s*:|"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\b(?:true|false|null)\b/g

      for (let lineNumber = 1; lineNumber <= currentModel.getLineCount(); lineNumber += 1) {
        const line = currentModel.getLineContent(lineNumber)
        for (const match of line.matchAll(tokenPattern)) {
          const token = match[0]
          const trimmedToken = token.trimEnd()
          const className = trimmedToken.endsWith(':')
            ? 'workflow-json-key'
            : token.startsWith('"')
              ? 'workflow-json-string'
              : /^(?:true|false|null)$/.test(token)
                ? 'workflow-json-keyword'
                : 'workflow-json-number'
          nextDecorations.push({
            range: new monaco.Range(lineNumber, match.index + 1, lineNumber, match.index + token.length + 1),
            options: { inlineClassName: className },
          })
        }
      }
      decorations.set(nextDecorations)
    }

    applyJsonDecorations()
    const contentChangeDisposable = model?.onDidChangeContent(applyJsonDecorations)
    editorDecorationCleanupRef.current?.()
    editorDecorationCleanupRef.current = () => {
      contentChangeDisposable?.dispose()
      decorations.clear()
    }

    const refreshEditor = () => {
      const currentModel = editor.getModel()
      if (!currentModel || currentModel.isDisposed()) return
      editor.layout()
      editor.render(true)
    }

    editor.focus()
    refreshEditor()
    editorRefreshFrameRef.current = window.requestAnimationFrame(refreshEditor)
    editorRefreshTimerRef.current = window.setTimeout(refreshEditor, 120)
    diagnosticsRef.current()
  }

  useEffect(() => () => {
    window.clearTimeout(copyTimerRef.current)
    window.clearTimeout(editorRefreshTimerRef.current)
    if (editorRefreshFrameRef.current != null) window.cancelAnimationFrame(editorRefreshFrameRef.current)
    editorDecorationCleanupRef.current?.()
  }, [])

  return (
    <div className="workflow-source-editor">
      <div className="workflow-source-toolbar">
        <button type="button" title={copied ? '已复制' : '复制源码'} aria-label="复制源码" onClick={() => void copySource()}>
          <CopyIcon size={18} />
        </button>
      </div>
      <div className="workflow-source-editor-frame">
        <Editor
          height="100%"
          width="100%"
          theme={SOURCE_EDITOR_THEME}
          loading={<div className="workflow-source-editor-loading">正在加载源码…</div>}
          language="json"
          path="workflow-definition.json"
          value={source}
          beforeMount={handleSourceEditorBeforeMount}
          onChange={(nextSource) => onChange(nextSource ?? '')}
          onMount={handleSourceEditorMount}
          options={{
            readOnly,
            automaticLayout: true,
            bracketPairColorization: { enabled: true },
            folding: true,
            fontFamily: 'SFMono-Regular, Consolas, Liberation Mono, monospace',
            fontSize: 13,
            formatOnPaste: true,
            glyphMargin: false,
            lineHeight: 20,
            lineNumbers: 'on',
            lineNumbersMinChars: 3,
            minimap: { enabled: true, showSlider: 'always' },
            padding: { top: 4, bottom: 8 },
            renderLineHighlight: 'all',
            scrollBeyondLastLine: false,
            selectOnLineNumbers: true,
            tabSize: 2,
            wordWrap: 'off',
          }}
        />
      </div>
    </div>
  )
}
