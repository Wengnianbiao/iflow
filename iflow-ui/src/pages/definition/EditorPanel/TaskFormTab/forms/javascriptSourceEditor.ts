import type { Monaco } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import { javaScriptLanguage, javaScriptLanguageConfiguration } from './javascriptGrammar'
import styles from './javascriptSourceEditor.module.css'

export const JAVASCRIPT_SOURCE_LANGUAGE = 'iflow-inline-javascript'
export const JAVASCRIPT_SOURCE_CLASS = styles.editor

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
