import test from 'node:test'
import assert from 'node:assert/strict'
import { focusedTaskData } from '../src/pages/executions/taskInputOutput.ts'

test('HTTP and WebService focus on the actual body, preserving empty and scalar results', () => {
  for (const type of ['HTTP', 'WEBSERVICE']) {
    for (const direction of ['input', 'output']) {
      for (const body of [{ id: 1 }, ['a'], '<request>实际值</request>', '', 0, false, null]) {
        const value = { body, soapTemplate: '<request>$.id</request>', headers: { token: 'value' }, statusCode: 200 }
        assert.equal(focusedTaskData(type, direction, value), body)
        assert.equal(value.soapTemplate, '<request>$.id</request>')
      }
      assert.equal(focusedTaskData(type, direction, { headers: {} }), undefined)
    }
  }
})

test('JDBC input shows recorded SQL rather than guessing from a historical template', () => {
  const value = { sql: "SELECT * FROM t WHERE id='A001'", sqlTemplate: 'SELECT * FROM t WHERE id=$.id', payload: { id: 'A001' } }
  assert.equal(focusedTaskData('JDBC', 'input', value), value.sql)
  assert.equal(focusedTaskData('JDBC', 'input', { sqlTemplate: value.sqlTemplate, payload: value.payload }), undefined)
})

test('JDBC output shows query rows, including an empty result, or an update count of zero', () => {
  for (const rows of [[], [{ id: 1 }]]) {
    assert.equal(focusedTaskData('JDBC', 'output', { rows, rowCount: rows.length, updateCount: 0 }), rows)
  }
  assert.deepEqual(focusedTaskData('JDBC', 'output', { rows: null, rowCount: 0, updateCount: 0 }), { updateCount: 0 })
})

test('INLINE input hides only script configuration and keeps all resolved business parameters', () => {
  const value = { expression: 'return items', evaluatorType: 'graaljs', items: null, count: 0, enabled: false }
  assert.deepEqual(focusedTaskData('INLINE', 'input', value), { items: null, count: 0, enabled: false })
  assert.equal(value.expression, 'return items')
})

test('MAPPING and SPI focus on their business payloads and retain their complete outputs', () => {
  const payload = { id: 1 }
  assert.equal(focusedTaskData('MAPPING', 'input', { payload, paramsConfig: [] }), payload)
  assert.equal(focusedTaskData('SPI', 'input', { spiInput: payload, remoteExtension: 'test', sourceCode: 'code' }), payload)
  for (const type of ['MAPPING', 'SPI', 'INLINE']) {
    for (const result of [payload, [1, 2], false, 0, null, '成功']) {
      assert.equal(focusedTaskData(type, 'output', result), result)
    }
  }
})

test('control tasks retain business state and custom task data remains intact', () => {
  for (const type of ['SWITCH', 'DECISION']) {
    assert.deepEqual(focusedTaskData(type, 'input', { case: 'yes', hasChildren: 'true', enabled: false }), { case: 'yes', enabled: false })
  }
  const value = { iteration: 1, items: [] }
  for (const type of ['DO_WHILE', 'FORK', 'FORK_JOIN', 'FORK_JOIN_DYNAMIC', 'JOIN', 'EXCLUSIVE_JOIN', 'TERMINATE', 'NOOP', 'CUSTOM']) {
    for (const direction of ['input', 'output']) assert.equal(focusedTaskData(type, direction, value), value)
  }
  assert.equal(focusedTaskData('CUSTOM', 'input', undefined), undefined)
})
