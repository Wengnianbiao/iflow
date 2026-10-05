import type { Monaco } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import { conf, language } from 'monaco-editor/esm/vs/basic-languages/java/java.js'
import styles from './javaSourceEditor.module.css'

export const JAVA_SOURCE_LANGUAGE = 'iflow-spi-java'
export const JAVA_SOURCE_CLASS = styles.editor

/** Bundle Monaco's Java grammar so highlighting never waits for a CDN language extension. */
export function registerJavaSourceEditor(monaco: Monaco) {
  if (monaco.languages.getLanguages().some(({ id }) => id === JAVA_SOURCE_LANGUAGE)) return

  monaco.languages.register({ id: JAVA_SOURCE_LANGUAGE, aliases: ['Java'] })
  monaco.languages.setLanguageConfiguration(JAVA_SOURCE_LANGUAGE, {
    ...conf,
    indentationRules: {
      increaseIndentPattern: /^.*\{[^}"']*$/,
      decreaseIndentPattern: /^\s*\}/,
    },
  })
  monaco.languages.setMonarchTokensProvider(JAVA_SOURCE_LANGUAGE, {
    ...language,
    keywords: [...language.keywords, 'null', 'var'],
    tokenizer: {
      ...language.tokenizer,
      root: [
        [/[a-zA-Z_$][\w$]*(?=\s*\()/, { cases: { '@keywords': 'keyword.$0', '@default': 'identifier.method' } }],
        [/[A-Z][\w$]*/, 'type.identifier'],
        ...language.tokenizer.root,
      ],
    },
  })
}

/** Decorations keep Java colors stable when another Monaco editor changes the shared theme. */
export function javaSourceDecorations(monaco: Monaco, source: string): editor.IModelDeltaDecoration[] {
  const lines = source.split(/\r?\n/)
  return monaco.editor.tokenize(source, JAVA_SOURCE_LANGUAGE).flatMap((tokens, lineIndex) => tokens.flatMap((token, tokenIndex) => {
    const className = token.type.startsWith('keyword') ? styles.keyword
      : token.type.startsWith('type.identifier') ? styles.type
        : token.type.startsWith('identifier.method') ? styles.method
          : token.type.startsWith('annotation') ? styles.annotation
            : token.type.startsWith('comment') ? styles.comment
              : token.type.startsWith('string') ? styles.string
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
