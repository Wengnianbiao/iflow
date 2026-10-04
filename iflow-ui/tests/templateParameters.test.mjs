import test from 'node:test'
import assert from 'node:assert/strict'
import { templateParameterText, updateTemplateParameter } from '../src/pages/definition/EditorPanel/TaskFormTab/forms/templateParameters.ts'

test('renaming preserves objects, arrays, numbers, booleans, null and string values', () => {
  for (const value of [{ nested: [1, false, null] }, [1, 'a'], 42, true, null, '123', '${workflow.input.patient}']) {
    const untouched = { value: false }
    const result = updateTemplateParameter({ patient: value, other: untouched }, 0,
      { key: 'workflow', value: templateParameterText(value) })
    assert.strictEqual(result.workflow, value)
    assert.strictEqual(result.other, untouched)
    assert.ok(!Object.hasOwn(result, 'patient'))
    assert.deepEqual(JSON.parse(JSON.stringify(result)).workflow, value)
  }
})

test('edited values parse valid JSON and preserve other text as strings', () => {
  for (const [text, expected] of [
    ['{"name":"张三","items":[true,null]}', { name: '张三', items: [true, null] }],
    ['[1,2]', [1, 2]], ['42', 42], ['false', false], ['null', null],
    ['"123"', '123'], ['${prepare.output.patient}', '${prepare.output.patient}'],
    ['not JSON', 'not JSON'], ['', ''],
  ]) {
    assert.deepEqual(updateTemplateParameter({ patient: 'old', other: [1] }, 0,
      { key: 'patient', value: text }), { patient: expected, other: [1] })
  }
})

test('null remains visibly distinguishable from empty text', () => {
  assert.equal(templateParameterText(null), 'null')
  assert.equal(templateParameterText(''), '')
})
