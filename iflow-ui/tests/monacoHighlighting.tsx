import '../src/monaco'
import { createRoot } from 'react-dom/client'
import { InlineScriptEditor } from '../src/pages/definition/EditorPanel/TaskFormTab/forms/InlineScriptEditor'
import { JsonViewer } from '../src/components/ui/JsonViewer'
import { ThemeProvider } from '../src/theme'
import '../src/styles.css'

const result = document.querySelector('#result')!
const editors = document.querySelector('#editors')!
const sql = `INSERT INTO items (id, name)
VALUES
  (1, 'first'),
  (2, 'second'),
  (3, 'third'),
  (4, 'example');`

// Keep Monaco's background tokenizer waiting for idle time during this check.
const requestIdleCallback = window.requestIdleCallback
window.requestIdleCallback = () => 0

createRoot(editors).render(
  <ThemeProvider>
    <div id="sql">
      <InlineScriptEditor language="sql" languageLabel="SQL" value={sql} onChange={() => {}} minHeight={100} />
    </div>
    <div id="json"><JsonViewer language="json" content={'{"enabled": true}'} ariaLabel="运行时 JSON" onMount={() => {}} /></div>
    <div id="xml"><JsonViewer language="xml" content={'<item enabled="true">example</item>'} ariaLabel="运行时 XML" onMount={() => {}} /></div>
  </ThemeProvider>,
)

const samples = [
  { id: 'sql', tokens: ['INSERT', "'example'"] },
  { id: 'json', tokens: ['"enabled"', 'true'] },
  { id: 'xml', tokens: ['item', 'enabled'] },
]

function hasHighlighting({ id, tokens }: typeof samples[number]) {
  const editor = document.querySelector(`#${id} .monaco-editor`)
  if (!editor || editor.contains(document.activeElement)) return false
  const spans = Array.from(editor.querySelectorAll('.view-line > span > span'))
  const colors = tokens.map(token => {
    const span = spans.find(span => span.textContent?.trim() === token)
    return span && getComputedStyle(span).color
  })
  return colors.every(Boolean) && new Set(colors).size === tokens.length
}

const deadline = Date.now() + 5000
function checkHighlighting() {
  const pending = samples.filter(sample => !hasHighlighting(sample))
  if (pending.length && Date.now() < deadline) {
    window.requestAnimationFrame(checkHighlighting)
    return
  }
  window.requestIdleCallback = requestIdleCallback
  result.textContent = pending.length
    ? `FAIL：未聚焦时未显示语法颜色 ${pending.map(sample => sample.id).join('、')}`
    : 'PASS：SQL 模板与只读 JSON、XML 在未聚焦时显示语法颜色。'
}

window.requestAnimationFrame(checkHighlighting)
