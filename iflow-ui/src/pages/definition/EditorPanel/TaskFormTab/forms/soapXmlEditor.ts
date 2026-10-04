import type { Monaco } from '@monaco-editor/react'

export const SOAP_XML_LANGUAGE = 'iflow-soap-xml'
export const SOAP_XML_THEME = 'iflow-soap-light'
const XML_MARKUP = /<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?\]\]>|<\?[\s\S]*?\?>|<(?:"[^"]*"|'[^']*'|[^'">])*>/g

type XmlToken = { start: number; end: number; kind: 'tag' | 'attribute' | 'value' | 'comment' | 'delimiter' | 'variable' }

/** 返回不重叠的着色范围；注释和 CDATA 中的标签只是文本。 */
export function soapXmlHighlightTokens(xml: string): XmlToken[] {
  const tokens: XmlToken[] = []
  const add = (start: number, end: number, kind: XmlToken['kind']) => {
    if (end > start) tokens.push({ start, end, kind })
  }
  const text = (source: string, offset: number, base?: XmlToken['kind']) => {
    const paths = /\$\{[^}]*\}|\$\$?(?:\.[\w$\-\u00c0-\uffff]+|\[(?:\d+|'[^']*'|"[^"]*")\])+/g
    let cursor = 0
    for (const match of source.matchAll(paths)) {
      if (base) add(offset + cursor, offset + match.index, base)
      add(offset + match.index, offset + match.index + match[0].length, 'variable')
      cursor = match.index + match[0].length
    }
    if (base) add(offset + cursor, offset + source.length, base)
  }
  let cursor = 0
  for (const match of xml.matchAll(XML_MARKUP)) {
    text(xml.slice(cursor, match.index), cursor)
    const markup = match[0]
    const offset = match.index
    if (markup.startsWith('<!--')) {
      add(offset, offset + markup.length, 'comment')
    } else if (markup.startsWith('<![CDATA[')) {
      add(offset, offset + 9, 'delimiter')
      text(markup.slice(9, -3), offset + 9, 'value')
      add(offset + markup.length - 3, offset + markup.length, 'delimiter')
    } else {
      const name = /^<(?:\/|\?)?[^\s/>?]+/.exec(markup)
      if (name) add(offset, offset + name[0].length, 'tag')
      const attributes = /([^\s=<>/]+)(\s*=\s*)(["'])([\s\S]*?)\3/g
      for (const attribute of markup.matchAll(attributes)) {
        const start = offset + attribute.index
        add(start, start + attribute[1].length, 'attribute')
        const valueStart = start + attribute[1].length + attribute[2].length
        text(attribute[3] + attribute[4] + attribute[3], valueStart, 'value')
      }
      const closing = /(?:\/?|\?)>$/.exec(markup)
      if (closing) add(offset + closing.index, offset + markup.length, 'delimiter')
    }
    cursor = offset + markup.length
  }
  text(xml.slice(cursor), cursor)
  return tokens
}

/** 按 XML 标签生成折叠范围，避免依赖模板的缩进方式。 */
export function soapXmlFoldingRanges(xml: string): Array<{ start: number; end: number }> {
  const lineStarts = [0]
  for (let index = 0; index < xml.length; index += 1) {
    if (xml[index] === '\n') lineStarts.push(index + 1)
  }
  const lineAt = (offset: number) => {
    let low = 0
    let high = lineStarts.length
    while (low + 1 < high) {
      const middle = Math.floor((low + high) / 2)
      if (lineStarts[middle] <= offset) low = middle
      else high = middle
    }
    return low + 1
  }
  const ranges: Array<{ start: number; end: number }> = []
  const tags: Array<{ name: string; start: number }> = []
  for (const match of xml.matchAll(XML_MARKUP)) {
    const token = match[0]
    const start = lineAt(match.index)
    const end = lineAt(match.index + token.length - 1) - 1
    if (token.startsWith('<!--') || token.startsWith('<![CDATA[')) {
      if (end > start) ranges.push({ start, end })
      continue
    }
    if (token.startsWith('<?') || token.startsWith('<!')) continue
    const tag = /^<\s*(\/?)\s*([^\s/>]+)/.exec(token)
    if (!tag) continue
    if (tag[1]) {
      // 编辑中的不完整 XML 也可折叠已匹配的部分。
      const index = tags.map((item) => item.name).lastIndexOf(tag[2])
      if (index < 0) continue
      const opened = tags[index]
      tags.splice(index)
      if (start - 1 > opened.start) ranges.push({ start: opened.start, end: start - 1 })
    } else if (!/\/\s*>$/.test(token)) {
      tags.push({ name: tag[2], start })
    }
  }
  return ranges.sort((left, right) => left.start - right.start || right.end - left.end)
}

export function registerSoapXmlEditor(monaco: Monaco) {
  if (monaco.languages.getLanguages().some(({ id }) => id === SOAP_XML_LANGUAGE)) return
  // 使用独立语言，确保外部 Monaco loader 的 XML 扩展加载情况不影响 SOAP 编辑器。
  monaco.languages.register({ id: SOAP_XML_LANGUAGE, aliases: ['SOAP XML'] })
  monaco.languages.setLanguageConfiguration(SOAP_XML_LANGUAGE, {
    comments: { blockComment: ['<!--', '-->'] },
    brackets: [['<', '>']],
    autoClosingPairs: [{ open: '"', close: '"' }, { open: "'", close: "'" }],
    onEnterRules: [{
      beforeText: /<([\w.:-]+)(?:\s[^>]*)?>[^<]*$/,
      afterText: /^\s*<\/[\w.:-]+\s*>/,
      action: { indentAction: monaco.languages.IndentAction.IndentOutdent },
    }],
  })
  monaco.languages.registerFoldingRangeProvider(SOAP_XML_LANGUAGE, {
    provideFoldingRanges: (model) => soapXmlFoldingRanges(model.getValue()),
  })
  monaco.editor.defineTheme(SOAP_XML_THEME, {
    base: 'vs',
    inherit: true,
    rules: [],
    colors: { 'editor.background': '#FFFFFF', 'editorGutter.background': '#FFFFFF' },
  })
}
