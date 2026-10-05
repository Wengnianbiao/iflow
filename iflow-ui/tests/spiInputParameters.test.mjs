import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire, Module } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const requireApp = createRequire(`${root}/package.json`)
const esbuild = createRequire(requireApp.resolve('vite'))('esbuild')
const built = await esbuild.build({
  entryPoints: [`${root}/src/pages/definition/EditorPanel/TaskFormTab/forms/spiInputParameters.ts`],
  bundle: true, platform: 'node', format: 'cjs', write: false, logLevel: 'silent',
})
const compiled = new Module(`${root}/spi-input-parameters-test.cjs`)
compiled.filename = `${root}/spi-input-parameters-test.cjs`
compiled._compile(built.outputFiles[0].text, compiled.filename)
const { readSpiParameterDrafts, applySpiParameterDrafts } = compiled.exports
const text = value => typeof value === 'string' ? value : JSON.stringify(value)
const draft = (originalKey, key, value) => ({ originalKey, key, value: text(value) })

test('renaming SPI parameters retains typed values and leaves siblings untouched', () => {
  for (const value of [{ nested: [1, false, null] }, [1, 'a'], 42, true, null, '123', '${workflow.input.patient}']) {
    const sibling = { visits: [1, 2] }
    const original = { patient: value, sibling }
    const result = applySpiParameterDrafts(original, [draft('patient', 'renamed', value)])
    assert.strictEqual(result.parameters.renamed, value)
    assert.strictEqual(result.parameters.sibling, sibling)
    assert.deepEqual(original, { patient: value, sibling })
    assert.deepEqual(result.drafts, [])
    assert.equal(result.message, '')
  }
})

test('two simultaneous duplicate names stay pending without losing either row', () => {
  const original = { patient: { id: 1 }, visits: [1, 2], taken: true }
  const pending = [draft('patient', 'taken', original.patient), draft('visits', 'taken', original.visits)]
  const result = applySpiParameterDrafts(original, pending)
  assert.strictEqual(result.parameters, original)
  assert.deepEqual(result.drafts, pending)
  assert.equal(result.message, '参数名 taken 已存在，请使用不同的名称')

  const afterDelete = applySpiParameterDrafts({ patient: original.patient, visits: original.visits }, pending)
  assert.deepEqual(afterDelete.parameters, { taken: original.patient, visits: original.visits })
  assert.deepEqual(afterDelete.drafts, [pending[1]])
  assert.equal(Object.keys(afterDelete.parameters).length, 2)
})

test('integer key reorder does not redirect a pending rename to another value', () => {
  const original = { patient: { id: 'P001' }, sibling: 7, taken: false }
  const pending = [draft('patient', 'taken', original.patient), draft('sibling', '1', 7)]
  const first = applySpiParameterDrafts(original, pending)
  assert.deepEqual(Object.keys(first.parameters), ['1', 'patient', 'taken'])
  assert.deepEqual(first.drafts, [pending[0]])
  const { taken: _deleted, ...withoutConflict } = first.parameters
  const second = applySpiParameterDrafts(withoutConflict, first.drafts)
  assert.deepEqual(second.parameters, { 1: 7, taken: { id: 'P001' } })
  assert.deepEqual(second.drafts, [])
})

test('deleting a conflicting sibling applies the waiting row and clears its error', () => {
  const value = { history: [false, null] }
  const pending = [draft('patient', 'reserved', value)]
  const before = applySpiParameterDrafts({ patient: value, reserved: 2 }, pending)
  assert.equal(before.drafts.length, 1)
  const after = applySpiParameterDrafts({ patient: value }, before.drafts)
  assert.deepEqual(after.parameters, { reserved: value })
  assert.strictEqual(after.parameters.reserved, value)
  assert.equal(after.message, '')
})

test('rename dependencies apply after a later sibling frees the conflicting name', () => {
  const result = applySpiParameterDrafts({ a: 1, b: 2, c: 3 }, [
    draft('a', 'b', 1), draft('b', 'c', 2), draft('c', 'd', 3),
  ])
  assert.deepEqual(result.parameters, { b: 1, c: 2, d: 3 })
  assert.deepEqual(result.drafts, [])
  assert.equal(result.message, '')
})

test('deleting the source row discards its draft without adding a phantom parameter', () => {
  const remaining = { sibling: [1, 2] }
  const result = applySpiParameterDrafts(remaining, [draft('deleted', 'new', false)])
  assert.strictEqual(result.parameters, remaining)
  assert.deepEqual(result.drafts, [])
  assert.equal(result.message, '')
})

test('blank names preserve pending value edits until the name is repaired', () => {
  const original = { patient: 'old', sibling: null }
  const invalid = { originalKey: 'patient', key: '   ', value: '{"id":1}' }
  const first = applySpiParameterDrafts(original, [invalid])
  assert.strictEqual(first.parameters, original)
  assert.deepEqual(first.drafts, [invalid])
  assert.equal(first.message, '参数名不能为空')
  const repaired = applySpiParameterDrafts(first.parameters, [{ ...invalid, key: 'payload' }])
  assert.deepEqual(repaired.parameters, { payload: { id: 1 }, sibling: null })
  assert.deepEqual(repaired.drafts, [])
  assert.equal(repaired.message, '')
})

test('only valid SPI parameter draft envelopes decode, keeping multiple rows', () => {
  const rows = [draft('a', 'taken', null), draft('b', '', [1])]
  assert.deepEqual(readSpiParameterDrafts(JSON.stringify({ kind: 'spi-parameters', rows }), '错误'), rows)
  assert.deepEqual(readSpiParameterDrafts(JSON.stringify({ kind: 'spi-parameters', rows }), ''), [])
  for (const value of ['{broken', '${workflow.input}', 'null', '[]',
    JSON.stringify({ kind: 'spi-parameter', rows }),
    JSON.stringify({ kind: 'spi-parameters', rows: [{ originalKey: 'a', key: 'b', value: 1 }] }),
    JSON.stringify({ kind: 'spi-parameters', rows: [rows[0], rows[0]] }),
  ]) assert.deepEqual(readSpiParameterDrafts(value, '错误'), [])
})
