import test from 'node:test'
import assert from 'node:assert/strict'
import { formatXml, xmlDocuments } from '../src/components/ui/jsonViewerContent.ts'

test('finds XML in workflow and task payloads without changing the original strings', () => {
  const body = '  <soap:Envelope><soap:Body><result>成功</result></soap:Body></soap:Envelope>\n'
  const value = { tasks: [{ inputData: { soapTemplate: '<request/>', body }, outputData: { body } }], count: 2, empty: null }
  assert.deepEqual(xmlDocuments(value), [
    { path: '$.tasks[0].inputData.soapTemplate', content: '<request/>' },
    { path: '$.tasks[0].inputData.body', content: body },
    { path: '$.tasks[0].outputData.body', content: body },
  ])
  assert.deepEqual(xmlDocuments(body), [{ path: '$', content: body }])
  assert.deepEqual(xmlDocuments({ text: '普通文本', expression: 'a < b > c', incomplete: '<request', json: '{"ok":true}' }), [])
})

test('XML field paths distinguish dots, brackets and array indexes in keys', () => {
  assert.deepEqual(xmlDocuments({ 'a.b': ['<result/>'], a: { b: '<request/>' } }).map(({ path }) => path), [
    '$["a.b"][0]', '$.a.b',
  ])
})

test('formats compact SOAP for folding while preserving namespaces, attributes and entities', () => {
  const xml = '<?xml version="1.0"?><soap:Envelope xmlns:soap="urn:soap"><soap:Body><result status="a > b">成功 &amp; ok</result><empty/></soap:Body></soap:Envelope>'
  assert.equal(formatXml(xml), [
    '<?xml version="1.0"?>',
    '<soap:Envelope xmlns:soap="urn:soap">',
    '  <soap:Body>',
    '    <result status="a > b">成功 &amp; ok</result>',
    '    <empty/>',
    '  </soap:Body>',
    '</soap:Envelope>',
  ].join('\n'))
})

test('preserves mixed text, CDATA, significant whitespace and xml:space subtrees', () => {
  for (const xml of [
    '<p> before <b>重点</b> after </p>',
    '<result><![CDATA[<r>raw & text</r>]]></result>',
    '<result>   </result>',
    '<request xml:space="preserve">  <a><b/></a>  </request>',
  ]) assert.equal(formatXml(xml), xml)
  assert.equal(formatXml('<r><!-- <fake/> --><value>ok</value></r>'), '<r>\n  <!-- <fake/> -->\n  <value>ok</value>\n</r>')
})

test('keeps malformed XML readable without guessing missing tags', () => {
  for (const xml of ['<r><a></r>', '<r><a>', '<r>before < after</r>', '</r>', '<r></other>']) {
    assert.equal(formatXml(xml), xml)
  }
})
