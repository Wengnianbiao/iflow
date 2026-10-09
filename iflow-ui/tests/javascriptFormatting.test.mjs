import test from 'node:test'
import assert from 'node:assert/strict'
import { runInNewContext } from 'node:vm'
import { formatJavaScriptSource } from '../src/pages/definition/EditorPanel/TaskFormTab/forms/javascriptFormatting.ts'

const evaluate = (source) => JSON.stringify(runInNewContext(source, {
  $: { provider: 'LIS', name: '患者', data: [{ code: 'A' }, { code: 'B' }] },
  item: { id: '00123' },
}))

test('compact Mapping functions expand into indented code with the same result', async () => {
  const source = '(function(){var c=$.data.length;if(c===0)return "empty";return item.id+":"+c;})()'
  const formatted = await formatJavaScriptSource(source)
  assert.match(formatted, /\n  var c = /)
  assert.match(formatted, /\n  return /)
  assert.equal(evaluate(formatted), evaluate(source))
  assert.equal(await formatJavaScriptSource(formatted), formatted)
})

test('formatting preserves literal whitespace, templates, regular expressions and object expressions', async () => {
  const sources = [
    '(function(){var msg="a  b: {x}; // text";var regex=/a  b/;return {msg:msg,matches:regex.test(msg),template:` ${$.name}  ${item.id} `};})()',
    '({id:item.id,source:$.provider,values:$.data.map(x=>x.code)})',
    '$.provider + "  / { } ; +  " + item.id',
    '(() => { // keep this comment\n return $.name; })()',
    '(() => {return\n {value: 1};})()',
  ]
  for (const source of sources) assert.equal(evaluate(await formatJavaScriptSource(source)), evaluate(source))
})

test('empty or invalid drafts are never replaced with a fallback script and source is not executed', async () => {
  assert.equal(await formatJavaScriptSource(''), '')
  assert.equal(await formatJavaScriptSource('  '), '  ')
  await assert.rejects(formatJavaScriptSource('$.data + )'))
  assert.match(await formatJavaScriptSource('throw new Error("must not execute");'), /must not execute/)
})

test('the formatter respects the editor indentation settings', async () => {
  const source = '(function(){return $.provider;})()'
  assert.match(await formatJavaScriptSource(source, 4), /\n {4}return /)
  assert.match(await formatJavaScriptSource(source, 2, false), /\n\treturn /)
})
