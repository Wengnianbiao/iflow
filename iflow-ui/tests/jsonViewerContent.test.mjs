import test from 'node:test'
import assert from 'node:assert/strict'
import { formatXml, isXml } from '../src/components/ui/jsonViewerContent.ts'

test('recognises XML bodies without treating full runtime objects or templates as the body', () => {
  const body = '  <soap:Envelope><soap:Body><result>成功</result></soap:Body></soap:Envelope>\n'
  assert.equal(isXml(body), true)
  assert.equal(isXml('<request/>'), true)
  for (const value of [null, { soapTemplate: '<request/>', body }, '普通文本', 'a < b > c', '<request', '{"ok":true}']) {
    assert.equal(isXml(value), false)
  }
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
