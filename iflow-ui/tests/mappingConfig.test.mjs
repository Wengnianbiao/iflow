import test from 'node:test'
import assert from 'node:assert/strict'
import {
  aliasPathCorrection, applyMappingConfigEdit, configAtPath, configChildren, configNodeLabel, countMappingTargetFields, constantInputText, parseConstantInput,
  flattenConfigs, flattenDisplayConfigs,
  insertAfterPath, loopElementLabel, mappingTypesForTargetType, normalizeConfig, normalizeForSave, objectTemplate, parseMappingConfigs, removeAtPath,
  scopeAliases, sourcePathAfterTargetRename, sourcePathForKey, updateAtPath, validateNode, withConfigChildren,
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
  assert.equal(configNodeLabel(reopened, [0, 0]), '对象元素')
  assert.equal(configNodeLabel(reopened, [0, 0, 2, 1]), '[1]')
})

test('loop element labels follow the element target type', () => {
  const scalarLoop = parseMappingConfigs([{
    targetParamKey: 'values', targetParamType: 'ARRAY', mappingType: 'ARRAY_MAP', sourceParamKey: '$.values', itemAlias: 'value',
    itemTemplate: { targetParamType: 'STRING', mappingType: 'NAME', sourceParamKey: 'value' },
  }])
  const arrayLoop = parseMappingConfigs([{
    targetParamKey: 'groups', targetParamType: 'ARRAY', mappingType: 'ARRAY_MAP', sourceParamKey: '$.groups', itemAlias: 'group',
    itemTemplate: { targetParamType: 'ARRAY', mappingType: 'ARRAY_FIXED', items: [] },
  }])

  assert.equal(configNodeLabel(scalarLoop, [0, 0]), '标量元素')
  assert.equal(configNodeLabel(arrayLoop, [0, 0]), '数组元素')
  assert.equal(loopElementLabel(normalizeConfig({ targetParamType: 'INTEGER' })), '标量元素')
  assert.equal(loopElementLabel(objectTemplate()), '对象元素')
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

test('editing a nested mapping changes only the edited property in saved JSON', () => {
  const original = [...raw, { ...leaf('status'), paramDesc: null, childParams: null }]
  const configs = parseMappingConfigs(original)
  const edited = updateAtPath(configs, [0, 0, 1, 0, 0], node =>
    applyMappingConfigEdit(node, { ...normalizeConfig(node), sourceParamKey: 'line.newCode' }))

  const expected = structuredClone(original)
  expected[0].itemTemplate.childParams[1].itemTemplate.childParams[0].sourceParamKey = 'line.newCode'
  assert.equal(JSON.stringify(edited), JSON.stringify(expected))
  assert.equal(original[0].itemTemplate.childParams[1].itemTemplate.childParams[0].sourceParamKey, 'line.code')

  const renamed = updateAtPath(configs, [0], node =>
    applyMappingConfigEdit(node, { ...normalizeConfig(node), targetParamKey: 'results' }))
  assert.equal(JSON.stringify(renamed), JSON.stringify([{ ...original[0], targetParamKey: 'results' }, original[1]]))
})

test('a loop keeps exactly one element template', () => {
  const configs = parseMappingConfigs(raw)
  assert.deepEqual(removeAtPath(configs, [0, 0]), configs)
  assert.deepEqual(insertAfterPath(configs, [0, 0], objectTemplate()), configs)
  const changed = updateAtPath(configs, [0, 0], () => parseMappingConfigs([leaf('temp')])[0])
  assert.equal(configChildren(changed[0]).length, 1)
})

test('loop element templates are explicit tree rows without changing persisted paths', () => {
  const configs = parseMappingConfigs(raw)
  const rows = flattenDisplayConfigs(configs)
  assert.deepEqual(rows.map(({ path, depth }) => ({ path, depth })), [
    { path: [0], depth: 0 },
    { path: [0, 0], depth: 1 },
    { path: [0, 0, 0], depth: 2 },
    { path: [0, 0, 1], depth: 2 },
    { path: [0, 0, 1, 0], depth: 3 },
    { path: [0, 0, 1, 0, 0], depth: 4 },
    { path: [0, 0, 1, 0, 1], depth: 4 },
    { path: [0, 0, 2], depth: 2 },
    { path: [0, 0, 2, 0], depth: 3 },
    { path: [0, 0, 2, 0, 0], depth: 4 },
    { path: [0, 0, 2, 1], depth: 3 },
  ])
  assert.deepEqual(rows.find(({ path }) => path.join('.') === '0.0.0').ancestors, [[0], [0, 0]])
  assert.equal(flattenConfigs(configs).length, 11)
})

test('source path suggestions follow scope and quote non-identifier fields', () => {
  assert.equal(sourcePathForKey('$', 'patientCode'), '$.patientCode')
  assert.equal(sourcePathForKey('item', 'patientCode'), 'item.patientCode')
  assert.equal(sourcePathForKey('item', 'patient.code'), "item['patient.code']")
  assert.equal(sourcePathForKey('item', "doctor's-code"), "item['doctor\\'s-code']")
  assert.equal(sourcePathAfterTargetRename('item', 'item', '', 'patientCode'), 'item.patientCode')
  assert.equal(sourcePathAfterTargetRename('item.patientCode', 'item', 'patientCode', 'code'), 'item.code')
  assert.equal(sourcePathAfterTargetRename('$.manual', 'item', 'patientCode', 'code'), '$.manual')
  assert.equal(aliasPathCorrection('$.item.patientCode', ['item']), 'item.patientCode')
  assert.equal(aliasPathCorrection("$['line'][0].code", ['item', 'line']), 'line[0].code')
  assert.equal(aliasPathCorrection('$.patient.item', ['item']), undefined)
  const aliases = scopeAliases(parseMappingConfigs(raw), [0, 0, 1, 0])
  assert.equal(sourcePathForKey(aliases.at(-1), 'code'), 'line.code')
})

test('mapping modes follow target types and object construction survives save and reopen', () => {
  assert.deepEqual(mappingTypesForTargetType('ARRAY'), ['ARRAY_MAP', 'ARRAY_FIXED', 'EXPRESSION'])
  assert.deepEqual(mappingTypesForTargetType('OBJECT'), ['OBJECT', 'EXPRESSION'])
  assert.deepEqual(mappingTypesForTargetType('STRING'), ['NAME', 'CONSTANT', 'EXPRESSION'])
  const constructed = [{ targetParamKey: 'patient', targetParamType: 'OBJECT', mappingType: 'OBJECT', childParams: [leaf('id', '$.patient.id')] }]
  const saved = parseMappingConfigs(constructed).map(normalizeForSave)
  assert.deepEqual(parseMappingConfigs(JSON.parse(JSON.stringify(saved))).map(normalizeForSave), saved)
})

test('constant JSON values and string whitespace are preserved', () => {
  for (const value of ['  literal  ', '', false, 0, null, { a: [1, true] }, [1, 2]]) {
    const config = parseMappingConfigs([{ targetParamKey: 'value', targetParamType: 'STRING', mappingType: 'CONSTANT', mappingRule: value }])[0]
    assert.deepEqual(normalizeForSave(config).mappingRule, value)
  }
})

test('plain text constants preserve quotes, whitespace, empty strings and JSON-looking text through save and reopen', () => {
  for (const text of ['LIS', '"LIS"', '  literal  ', '', 'null', 'true', '123', '{"id":1}', '第一行\n第二行', 'C:\\files\\report']) {
    const value = parseConstantInput(text, 'STRING')
    const saved = normalizeForSave(normalizeConfig({
      targetParamKey: 'value', targetParamType: 'STRING', mappingType: 'CONSTANT', mappingRule: value,
    }))
    const reopened = parseMappingConfigs(JSON.parse(JSON.stringify([saved])))[0]
    assert.equal(reopened.mappingRule, text)
    assert.equal(constantInputText(reopened.mappingRule, 'STRING'), text)
  }
  assert.equal(parseConstantInput('false', 'BOOLEAN'), false)
  assert.equal(parseConstantInput('true', 'BOOLEAN'), true)
  assert.throws(() => parseConstantInput('1', 'BOOLEAN'), /请选择/)
})

test('numeric constants validate Java ranges and preserve full Long precision in serialized configs', () => {
  assert.equal(parseConstantInput('0', 'INTEGER'), 0)
  assert.equal(parseConstantInput(' -2147483648 ', 'INTEGER'), -2147483648)
  assert.equal(parseConstantInput('+2147483647', 'INTEGER'), 2147483647)
  assert.equal(parseConstantInput('9007199254740991', 'LONG'), 9007199254740991)
  for (const text of ['9007199254740993', '9223372036854775807', '-9223372036854775808']) {
    const rule = parseConstantInput(text, 'LONG')
    const reopened = JSON.parse(JSON.stringify({ mappingRule: rule }))
    assert.equal(BigInt(reopened.mappingRule), BigInt(text))
    assert.equal(constantInputText(reopened.mappingRule, 'LONG'), text)
  }
  assert.equal(parseConstantInput('123.45', 'DOUBLE'), 123.45)
  assert.equal(parseConstantInput('-1.25e3', 'DOUBLE'), -1250)
  for (const text of ['', ' ', '1.5', '1e3', 'NaN', '0xff', 'abc']) {
    assert.throws(() => parseConstantInput(text, 'INTEGER'), /整数/)
    assert.throws(() => parseConstantInput(text, 'LONG'), /整数/)
  }
  assert.throws(() => parseConstantInput('2147483648', 'INTEGER'), /范围/)
  assert.throws(() => parseConstantInput('-2147483649', 'INTEGER'), /范围/)
  assert.throws(() => parseConstantInput('9223372036854775808', 'LONG'), /范围/)
  assert.throws(() => parseConstantInput('-9223372036854775809', 'LONG'), /范围/)
  for (const text of ['', ' ', 'Infinity', 'NaN', '1e309', '0xff', '123abc']) {
    assert.throws(() => parseConstantInput(text, 'DOUBLE'), /有效数字/)
  }
})

test('legacy constants display without JSON quoting and null stays distinct from empty or literal null strings', () => {
  assert.equal(constantInputText('LIS', 'STRING'), 'LIS')
  assert.equal(constantInputText('00123', 'STRING'), '00123')
  assert.equal(constantInputText(false, 'BOOLEAN'), 'false')
  assert.equal(constantInputText('TRUE', 'BOOLEAN'), 'true')
  assert.equal(constantInputText('1', 'BOOLEAN'), 'true')
  assert.equal(constantInputText(0, 'INTEGER'), '0')
  for (const value of [null, '', 'null']) {
    const saved = normalizeForSave(normalizeConfig({
      targetParamKey: 'value', targetParamType: 'STRING', mappingType: 'CONSTANT', mappingRule: value,
    }))
    assert.equal(JSON.parse(JSON.stringify(saved)).mappingRule, value)
  }
})

test('changing node behavior removes inactive structural configuration', () => {
  const loop = parseMappingConfigs(raw)[0]
  const value = applyMappingConfigEdit(loop, { ...normalizeConfig(loop), mappingType: 'EXPRESSION', mappingRule: '$.orders' })
  assert.equal(value.itemTemplate, undefined)
  assert.equal(value.itemAlias, undefined)
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
  assert.throws(() => parseMappingConfigs([{ ...leaf('object'), targetParamType: 'OBJECT' }]), /对象仅支持对象构建或 JS 表达式/)
  assert.throws(() => parseMappingConfigs([{
    targetParamKey: 'object', targetParamType: 'OBJECT', mappingType: 'CONSTANT', mappingRule: {},
  }]), /对象仅支持对象构建或 JS 表达式/)
  assert.throws(() => parseMappingConfigs([{
    targetParamKey: 'outer', targetParamType: 'ARRAY', mappingType: 'ARRAY_FIXED', items: [
      { targetParamType: 'OBJECT', mappingType: 'NAME', sourceParamKey: '$.patient' },
    ],
  }]), /paramsConfig\[0\]\.items\[0\].*对象仅支持对象构建或 JS 表达式/)
  assert.doesNotThrow(() => parseMappingConfigs([{
    targetParamKey: 'object', targetParamType: 'OBJECT', mappingType: 'EXPRESSION', mappingRule: '$.patient',
  }]))
  assert.throws(() => parseMappingConfigs([{ ...leaf('array'), targetParamType: 'ARRAY' }]), /数组仅支持循环映射、固定长度或 JS 表达式/)
  assert.throws(() => parseMappingConfigs([{
    targetParamKey: 'array', targetParamType: 'ARRAY', mappingType: 'CONSTANT', mappingRule: [],
  }]), /数组仅支持循环映射、固定长度或 JS 表达式/)
  assert.throws(() => parseMappingConfigs([{
    targetParamKey: 'outer', targetParamType: 'ARRAY', mappingType: 'ARRAY_FIXED', items: [
      { targetParamType: 'ARRAY', mappingType: 'NAME', sourceParamKey: '$.rows' },
    ],
  }]), /paramsConfig\[0\]\.items\[0\].*数组仅支持循环映射、固定长度或 JS 表达式/)
  assert.doesNotThrow(() => parseMappingConfigs([{
    targetParamKey: 'array', targetParamType: 'ARRAY', mappingType: 'EXPRESSION', mappingRule: '$.rows',
  }]))
  assert.throws(() => parseMappingConfigs([{ ...leaf('id'), childParams: 'invalid' }]), /必须是数组/)
  assert.throws(() => parseMappingConfigs([{ ...leaf('id'), unsupported: 'value' }]), /未知配置字段/)
  assert.doesNotThrow(() => parseMappingConfigs([{ ...leaf('id'), sourceParamKey: '$[0]["field.name"]' }]))
})
