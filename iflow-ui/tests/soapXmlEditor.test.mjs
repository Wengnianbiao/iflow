import test from 'node:test'
import assert from 'node:assert/strict'
import { soapXmlFoldingRanges, soapXmlHighlightTokens } from '../src/pages/definition/EditorPanel/TaskFormTab/forms/soapXmlEditor.ts'

const highlighted = (xml) => soapXmlHighlightTokens(xml).map(({ start, end, kind }) => ({ text: xml.slice(start, end), kind }))

test('XML highlighting distinguishes tags, attributes, quoted values and parameter paths', () => {
  assert.deepEqual(highlighted('<r id="$.id">$.patient.name</r>'), [
    { text: '<r', kind: 'tag' }, { text: 'id', kind: 'attribute' },
    { text: '"', kind: 'value' }, { text: '$.id', kind: 'variable' }, { text: '"', kind: 'value' },
    { text: '>', kind: 'delimiter' }, { text: '$.patient.name', kind: 'variable' },
    { text: '</r', kind: 'tag' }, { text: '>', kind: 'delimiter' },
  ])
})

test('comments and CDATA are not highlighted as XML elements', () => {
  const tokens = highlighted('<!-- <r>$.ignored</r> --><![CDATA[<r>$.value</r>]]>')
  assert.deepEqual(tokens, [
    { text: '<!-- <r>$.ignored</r> -->', kind: 'comment' },
    { text: '<![CDATA[', kind: 'delimiter' }, { text: '<r>', kind: 'value' },
    { text: '$.value', kind: 'variable' }, { text: '</r>', kind: 'value' },
    { text: ']]>', kind: 'delimiter' },
  ])
})

test('multiline attributes, quoted property paths and framework expressions retain exact ranges', () => {
  const xml = '<?xml version="1.0"?>\n<r\n title="a > b"\n name="$.patient[\'display name\']"/>${prepare.output.xml}'
  const tokens = highlighted(xml)
  assert.ok(tokens.some(({ text, kind }) => text === '"a > b"' && kind === 'value'))
  assert.ok(tokens.some(({ text, kind }) => text === "$.patient['display name']" && kind === 'variable'))
  assert.ok(tokens.some(({ text, kind }) => text === '${prepare.output.xml}' && kind === 'variable'))
  const ranges = soapXmlHighlightTokens(xml).sort((a, b) => a.start - b.start)
  for (let i = 1; i < ranges.length; i++) assert.ok(ranges[i].start >= ranges[i - 1].end)
})

test('nested SOAP tags fold independently without relying on indentation', () => {
  assert.deepEqual(soapXmlFoldingRanges([
    '<soap:Envelope xmlns:soap="urn:soap">',
    '<soap:Body>',
    '<request>',
    '<name>$.patient.name</name>',
    '</request>',
    '</soap:Body>',
    '</soap:Envelope>',
  ].join('\n')), [{ start: 1, end: 6 }, { start: 2, end: 5 }, { start: 3, end: 4 }])
})

test('quoted greater-than signs, self closing tags and multiline attributes do not confuse nesting', () => {
  assert.deepEqual(soapXmlFoldingRanges([
    '<r label=">">', '<empty/>', '<patient', ' id="42">', '<name>A</name>', '</patient>', '</r>',
  ].join('\n')), [{ start: 1, end: 6 }, { start: 3, end: 5 }])
})

test('comments and CDATA fold as text blocks and do not contribute fake tags', () => {
  assert.deepEqual(soapXmlFoldingRanges([
    '<r>', '<!--', '<fake>', '-->', '<![CDATA[', '<another>', ']]>', '<value>A</value>', '</r>',
  ].join('\n')), [{ start: 1, end: 8 }, { start: 2, end: 3 }, { start: 5, end: 6 }])
})

test('XML declarations and draft unmatched tags do not break matched ranges', () => {
  assert.deepEqual(soapXmlFoldingRanges('<?xml version="1.0"?>\n<r>\n<value>A</value>\n<unfinished>\n</r>'),
    [{ start: 2, end: 4 }])
  assert.deepEqual(soapXmlFoldingRanges('<r/>\n<open>\n</unmatched>'), [])
})
