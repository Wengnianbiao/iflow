import { useMemo } from 'react'
import Editor, { type OnMount } from '@monaco-editor/react'
import { useTheme } from '@/theme/ThemeProvider'
import { formatXml } from './jsonViewerContent'
import styles from './JsonViewer.module.css'

export function serializeJson(value: unknown) {
  try {
    return JSON.stringify(value === undefined ? {} : value, null, 2) ?? '{}'
  } catch {
    return JSON.stringify({ error: '无法序列化此 JSON 数据' }, null, 2)
  }
}

export function JsonViewer({ content, language, ariaLabel, onMount }: {
  content: string
  language: 'json' | 'xml' | 'sql' | 'plaintext'
  ariaLabel: string
  onMount: OnMount
}) {
  const { mode } = useTheme()
  const source = useMemo(() => language === 'xml' ? formatXml(content) : content, [content, language])

  return (
    <div className={styles.viewer}>
      <Editor
        value={source}
        language={language}
        theme={mode === 'dark' ? 'vs-dark' : 'light'}
        loading="正在加载数据…"
        onMount={onMount}
        options={{
          ariaLabel,
          readOnly: true,
          domReadOnly: true,
          automaticLayout: true,
          tabSize: 2,
          fontSize: 12,
          lineHeight: 20,
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
          minimap: { enabled: false },
          lineNumbers: 'off',
          folding: true,
          showFoldingControls: 'always',
          glyphMargin: false,
          lineDecorationsWidth: 18,
          scrollBeyondLastLine: false,
          wordWrap: 'on',
          renderLineHighlight: 'none',
          renderValidationDecorations: 'off',
          overviewRulerLanes: 0,
          hideCursorInOverviewRuler: true,
          stickyScroll: { enabled: false },
          padding: { top: 12, bottom: 12 },
          scrollbar: { alwaysConsumeMouseWheel: false, verticalSliderSize: 9, horizontalSliderSize: 9 },
        }}
      />
    </div>
  )
}
