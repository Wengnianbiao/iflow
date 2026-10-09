import type { Monaco } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import { javaScriptLanguage, javaScriptLanguageConfiguration } from './javascriptGrammar'
import { javaScriptSyntaxProblems } from './javascriptDiagnostics'
import styles from './javascriptSourceEditor.module.css'

export const JAVASCRIPT_SOURCE_LANGUAGE = 'iflow-inline-javascript'
export const JAVASCRIPT_SOURCE_CLASS = styles.editor
const JAVASCRIPT_SYNTAX_MARKER_OWNER = 'iflow-javascript-syntax'

/** Bundle the JavaScript grammar so INLINE highlighting does not depend on CDN language extensions. */
export function registerJavaScriptSourceEditor(monaco: Monaco) {
  if (monaco.languages.getLanguages().some(({ id }) => id === JAVASCRIPT_SOURCE_LANGUAGE)) return

  monaco.languages.register({ id: JAVASCRIPT_SOURCE_LANGUAGE, aliases: ['JavaScript', 'ECMASCRIPT'] })
  monaco.languages.setLanguageConfiguration(JAVASCRIPT_SOURCE_LANGUAGE, {
    ...javaScriptLanguageConfiguration(monaco),
    indentationRules: {
      increaseIndentPattern: /^((?!\/\/).)*(\{|\[|\()[^)}\]'"`]*$/,
      decreaseIndentPattern: /^\s*[}\])]/,
    },
  })
  monaco.languages.setMonarchTokensProvider(JAVASCRIPT_SOURCE_LANGUAGE, javaScriptLanguage)
}

/** Refresh syntax markers while typing, and remove only this editor's diagnostics on disposal. */
export function attachJavaScriptSyntaxDiagnostics(monaco: Monaco, model: editor.ITextModel) {
  let timer: number | undefined
  const validate = () => {
    if (model.isDisposed()) return
    monaco.editor.setModelMarkers(model, JAVASCRIPT_SYNTAX_MARKER_OWNER, javaScriptSyntaxProblems(model.getValue()).map((problem) => {
      const start = model.getPositionAt(problem.start)
      const end = model.getPositionAt(problem.end)
      return {
        message: problem.message,
        severity: monaco.MarkerSeverity.Error,
        source: 'JavaScript',
        startLineNumber: start.lineNumber,
        startColumn: start.column,
        endLineNumber: end.lineNumber,
        endColumn: end.column,
      }
    }))
  }
  const listener = model.onDidChangeContent(() => {
    window.clearTimeout(timer)
    timer = window.setTimeout(validate, 150)
  })
  validate()
  return {
    dispose() {
      window.clearTimeout(timer)
      listener.dispose()
      if (!model.isDisposed()) monaco.editor.setModelMarkers(model, JAVASCRIPT_SYNTAX_MARKER_OWNER, [])
    },
  }
}

/** Keep script colors stable when another Monaco editor changes the shared theme. */
export function javaScriptSourceDecorations(monaco: Monaco, source: string): editor.IModelDeltaDecoration[] {
  const lines = source.split(/\r?\n/)
  return monaco.editor.tokenize(source, JAVASCRIPT_SOURCE_LANGUAGE).flatMap((tokens, lineIndex) => tokens.flatMap((token, tokenIndex) => {
    const className = token.type.startsWith('keyword') ? styles.keyword
      : token.type.startsWith('type.identifier') ? styles.type
        : token.type.startsWith('comment') ? styles.comment
          : token.type.startsWith('string') ? styles.string
            : token.type.startsWith('regexp') ? styles.regexp
              : token.type.startsWith('number') ? styles.number
                : undefined
    if (!className) return []
    const endColumn = tokens[tokenIndex + 1]?.offset !== undefined ? tokens[tokenIndex + 1].offset + 1 : lines[lineIndex].length + 1
    return [{
      range: new monaco.Range(lineIndex + 1, token.offset + 1, lineIndex + 1, endColumn),
      options: { inlineClassName: className },
    }]
  }))
}
