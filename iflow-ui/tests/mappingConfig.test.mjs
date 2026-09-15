import test from 'node:test'
import assert from 'node:assert/strict'
import {
  configAtPath, configChildren, configNodeLabel, countMappingTargetFields, flattenConfigs,
  insertAfterPath, normalizeForSave, objectTemplate, parseMappingConfigs, removeAtPath,
  scopeAliases, updateAtPath, validateNode, withConfigChildren,
} from '../src/tasks/mappingConfig.ts'

const leaf = (key, path = '$.id') => ({ targetParamKey: key, targetParamType: 'STRING', mappingType: 'NAME', sourceParamKey: path })
const raw = [{ targetParamKey: 'orders', targetParamType: 'ARRAY', mappingType: 'ARRAY_MAP', sourceParamKey: '$.orders', itemAlias: 'order',
  itemTemplate: { targetParamType: 'OBJECT', mappingType: 'OBJECT', childParams: [
    leaf('id', 'order.id'),
    { targetParamKey: 'lines', targetParamType: 'ARRAY', mappingType: 'ARRAY_MAP', sourceParamKey: 'order.lines', itemAlias: 'line',
      itemTemplate: { targetParamType: 'OBJECT', mappingType: 'OBJECT', childParams: [leaf('code', 'line.code'), leaf('orderId', 'order.id')] } },
    { targetParamKey: 'fixed', targetParamType: 'ARRAY', mappingType: 'ARRAY_FIXED', items: [
      { targetParamType: 'OBJECT', mappingType: 'OBJECT', childParams: [leaf('patientId')] },
      { targetParamType: 'BOOLEAN', mappingType: 'CONSTANT', mappingRule: false },
    ] },
  ] } }]

test('nested templates survive JSON, field normalization and reopening', () => {
  const configs = parseMappingConfigs(raw)
  const saved = configs.map(normalizeForSave)
  const reopened = parseMappingConfigs(JSON.parse(JSON.stringify(saved)))
  assert.deepEqual(reopened.map(normalizeForSave), saved)
  assert.equal(configAtPath(reopened, [0, 0, 2, 1]).mappingRule, false)
  assert.deepEqual(scopeAliases(reopened, [0, 0, 1, 0]), ['order', 'line'])
  assert.equal(countMappingTargetFields(reopened), 7)
  assert.equal(flattenConfigs(reopened).length, 11)
  assert.equal(configNodeLabel(reopened, [0, 0]), '元素模板')
  assert.equal(configNodeLabel(reopened, [0, 0, 2, 1]), '[1]')
})

test('editing, adding, copying and removing fixed elements and nested fields preserves siblings', () => {
  const configs = parseMappingConfigs(raw)
  let edited = updateAtPath(configs, [0, 0, 1, 0, 0], node => ({ ...node, targetParamKey: 'itemCode' }))
  assert.equal(configAtPath(configs, [0, 0, 1, 0, 0]).targetParamKey, 'code')
  assert.equal(configAtPath(edited, [0, 0, 1, 0, 0]).targetParamKey, 'itemCode')
  edited = insertAfterPath(edited, [0, 0, 2, 1], configAtPath(edited, [0, 0, 2, 1]))
  assert.equal(configChildren(configAtPath(edited, [0, 0, 2])).length, 3)
  edited = removeAtPath(edited, [0, 0, 2, 0])
  assert.deepEqual(configChildren(configAtPath(edited, [0, 0, 2])).map(node => node.mappingRule), [false, false])
  edited = updateAtPath(edited, [0, 0, 2], node => withConfigChildren(node, [...configChildren(node), objectTemplate()]))
  assert.equal(configAtPath(edited, [0, 0, 2, 2]).mappingType, 'OBJECT')
  assert.equal(configAtPath(edited, [0, 0, 0]).targetParamKey, 'id')
})

test('a loop keeps exactly one element template', () => {
  const configs = parseMappingConfigs(raw)
  assert.deepEqual(removeAtPath(configs, [0, 0]), configs)
  assert.deepEqual(insertAfterPath(configs, [0, 0], objectTemplate()), configs)
  const changed = updateAtPath(configs, [0, 0], () => parseMappingConfigs([leaf('temp')])[0])
  assert.equal(configChildren(changed[0]).length, 1)
})

test('constant JSON values and string whitespace are preserved', () => {
  for (const value of ['  literal  ', '', false, 0, null, { a: [1, true] }, [1, 2]]) {
    const config = parseMappingConfigs([{ targetParamKey: 'value', targetParamType: 'STRING', mappingType: 'CONSTANT', mappingRule: value }])[0]
    assert.deepEqual(normalizeForSave(config).mappingRule, value)
  }
})

test('changing node behavior removes inactive structural configuration', () => {
  const loop = parseMappingConfigs(raw)[0]
  const value = normalizeForSave({ ...loop, mappingType: 'EXPRESSION', mappingRule: '$.orders' })
  assert.equal(value.itemTemplate, undefined)
  assert.equal(value.itemAlias, undefined)
  assert.deepEqual(value.childParams, [])
  assert.deepEqual(value.items, [])
  assert.doesNotThrow(() => parseMappingConfigs([value]))
})

test('invalid JSON configurations do not silently normalize into different behavior', () => {
  assert.throws(() => parseMappingConfigs([{ ...leaf('id'), sourceParamType: 'STRING' }]), /已移除/)
  assert.throws(() => parseMappingConfigs([{ ...leaf('id'), mappingType: 'UNKNOWN' }]), /mappingType/)
  assert.throws(() => parseMappingConfigs([leaf('id'), leaf('id')]), /重复/)
  assert.throws(() => parseMappingConfigs([{ ...raw[0], itemTemplate: null }]), /模板/)
  assert.throws(() => parseMappingConfigs([{ ...raw[0], itemAlias: 'JSON' }]), /保留/)
  const config = parseMappingConfigs(raw)[0]
  assert.throws(() => validateNode({ ...config, itemAlias: 'order' }, ['order'], false), /重名/)
  assert.throws(() => parseMappingConfigs([{ ...leaf('id'), sourceParamKey: 'missing.id' }]), /循环变量/)
  assert.throws(() => parseMappingConfigs([{ ...leaf('id'), sourceParamKey: '$.items[' }]), /路径语法/)
  assert.throws(() => parseMappingConfigs([{ ...leaf('object'), targetParamType: 'OBJECT' }]), /仅支持标量/)
  assert.throws(() => parseMappingConfigs([{ ...leaf('array'), targetParamType: 'ARRAY' }]), /仅支持标量/)
  assert.throws(() => parseMappingConfigs([{ ...leaf('id'), childParams: 'invalid' }]), /必须是数组/)
  assert.throws(() => parseMappingConfigs([{ ...leaf('id'), unsupported: 'value' }]), /未知配置字段/)
  assert.doesNotThrow(() => parseMappingConfigs([{ ...leaf('id'), sourceParamKey: '$[0]["field.name"]' }]))
})
