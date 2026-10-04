import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createRequire, Module } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const requireApp = createRequire(`${root}/package.json`)
const esbuild = createRequire(requireApp.resolve('vite'))('esbuild')
// Bundle the real actors/helpers without mounting the React task catalog.
const built = await esbuild.build({
  stdin: { contents: `
    export { interpret } from 'xstate';
    export { workflowDefinitionMachine } from './src/pages/definition/state/machine';
    export { hasWorkflowChanges } from './src/pages/definition/state/types';
    export * from './src/pages/definition/validation/validateWorkflow';
    export * from './src/tasks/taskPluginRegistry';
    export { validateBuiltinTask } from './src/tasks/builtinTaskValidators';
    export * from './src/flow/workflowEditor';
    export * from './src/flow/parameterValues';
  `, resolveDir: root, sourcefile: 'workflow-definition-test.ts' },
  bundle: true, platform: 'node', format: 'cjs', write: false, tsconfig: `${root}/tsconfig.json`, logLevel: 'silent',
  plugins: [{ name: 'headless-task-catalog', setup(build) {
    build.onResolve({ filter: /^@\/tasks$/ }, () => ({ path: `${root}/src/tasks/taskPluginRegistry.ts` }))
  } }],
})
const compiled = new Module(`${root}/workflow-definition-test.cjs`)
compiled.filename = `${root}/workflow-definition-test.cjs`
compiled._compile(built.outputFiles[0].text, compiled.filename)
const lib = compiled.exports
lib.registerCoreTaskPlugins(['HTTP', 'INLINE', 'SWITCH', 'FORK_JOIN', 'JOIN', 'DO_WHILE', 'SPI', 'JDBC', 'WEBSERVICE', 'MAPPING'].map(type => ({
  type, label: type, validate: lib.validateBuiltinTask,
  flow: { kind: ({ SWITCH: 'switch', FORK_JOIN: 'fork', JOIN: 'join', DO_WHILE: 'do_while' })[type] ?? 'task' },
})))
const task = ref => ({ name: ref, taskReferenceName: ref, type: 'HTTP', inputParameters: { uri: 'https://example.test', method: 'GET' } })
const wf = (tasks = [task('a')]) => ({ name: 'review', version: 1, displayName: '测试', description: '测试定义', tasks })
const backendTaskDefaults = {
  decisionCases: {}, defaultCase: null, forkTasks: [], joinOn: [], defaultExclusiveJoinTask: [],
  evaluatorType: null, expression: null, optional: false, permissive: false,
  loopOver: [], loopCondition: null, items: null, retryCount: 0,
}
// Match the backend-serialized WEBSERVICE + INLINE shape, without real SOAP credentials.
const backendWorkflow = () => ({
  ...wf([
    { ...task('soap'), ...backendTaskDefaults, type: 'WEBSERVICE', inputParameters: { uri: 'https://example.test/soap', soapTemplate: '<Envelope />', templateParameters: { data: '${workflow.input.data}' } } },
    { ...task('parse'), ...backendTaskDefaults, type: 'INLINE', inputParameters: { evaluatorType: 'graaljs', response: '${soap.output}', expression: 'return $.response;' } },
  ]),
  createTime: 0, updateTime: 0, createdBy: null, updatedBy: null,
  correlationExpression: null, inputParameters: ['data'], outputParameters: {}, timeoutSeconds: 60, timeoutPolicy: 'TIME_OUT_WF',
})
const tick = () => new Promise(resolve => setImmediate(resolve))
async function actorFor(workflow = wf(), services = {}) {
  const actor = lib.interpret(lib.workflowDefinitionMachine.withConfig({
    services: { loadWorkflowDefinition: async () => ({ workflow, versions: [1] }), ...services },
    actions: { notifyWorkflowSaved: () => {}, notifyWorkflowReset: () => {}, navigateToSavedWorkflow: () => {} },
  })).start()
  actor.send({ type: 'INITIALIZE', workflowName: workflow.name, workflowVersion: 1, isNewWorkflow: false })
  await tick()
  assert.ok(actor.state.matches('ready'))
  return actor
}

test('recursive structural validation quarantines malformed task trees', () => {
  for (const tasks of [[null], [{ ...task('a'), forkTasks: [[null]] }], [{ ...task('a'), decisionCases: { x: {} } }], [{ ...task('a'), inputParameters: [] }], [{ type: 'HTTP' }]]) {
    const result = lib.validateWorkflow(wf(tasks))
    assert.equal(result.workflow, undefined)
    assert.equal(result.diagramSafe, false)
    assert.ok(result.problems.some(problem => problem.structural))
  }
})

test('backend default null fields on WEBSERVICE and INLINE are valid and preserved', () => {
  const workflow = backendWorkflow()
  const original = structuredClone(workflow)
  const result = lib.validateWorkflow(workflow)
  assert.deepEqual(result.problems, [])
  assert.equal(result.diagramSafe, true)
  assert.equal(result.workflow, workflow)
  assert.deepEqual(workflow, original)
  assert.equal(lib.findTaskPath(workflow, 'parse'), '/tasks/1')
  const edited = lib.updateTask(workflow, 'soap', t => ({ ...t, description: '只改描述' }))
  assert.deepEqual(edited.tasks[0], { ...original.tasks[0], description: '只改描述' })
  assert.deepEqual(edited.tasks[1], original.tasks[1])
  assert.deepEqual(JSON.parse(JSON.stringify(edited)).tasks[0].defaultCase, null)
})

test('optional null properties are unset; null task entries and required values remain invalid', () => {
  const custom = {
    ...task('custom'), type: 'CUSTOM_HL7', name: null, description: null, inputParameters: null,
    decisionCases: null, defaultCase: null, forkTasks: null, loopOver: null,
    loopCondition: null, items: null, joinOn: null, defaultExclusiveJoinTask: null, expression: null, evaluatorType: null,
  }
  const workflow = { ...wf([custom]), correlationExpression: null, timeoutPolicy: null, inputParameters: null, outputParameters: null }
  assert.deepEqual(lib.validateWorkflow(workflow).problems, [])
  assert.deepEqual(lib.updateTask(workflow, 'custom', t => ({ ...t, name: '改名称' })).tasks[0], { ...custom, name: '改名称' })
  for (const invalid of [
    { ...wf(), name: null }, wf([{ ...task('a'), type: null }]), wf([{ ...task('a'), taskReferenceName: null }]),
    { ...wf(), tasks: null }, wf([null]), wf([{ ...task('a'), defaultCase: [null] }]),
    wf([{ ...task('a'), forkTasks: [null] }]), wf([{ ...task('a'), decisionCases: { x: null } }]),
    wf([{ ...task('a'), optional: null }]), wf([{ ...task('a'), expression: 42 }]),
  ]) {
    const result = lib.validateWorkflow(invalid)
    assert.equal(result.workflow, undefined)
    assert.equal(result.diagramSafe, false)
    assert.ok(result.problems.some(p => p.structural))
  }
})

test('required task-specific config still reports missing values rather than rejecting optional nulls', () => {
  for (const [taskConfig, path] of [
    [{ ...backendWorkflow().tasks[1], inputParameters: { expression: null } }, '/inputParameters/expression'],
    [{ ...task('s'), ...backendTaskDefaults, type: 'SWITCH' }, '/expression'],
    [{ ...task('loop'), ...backendTaskDefaults, type: 'DO_WHILE' }, '/loopCondition'],
  ]) {
    const result = lib.validateWorkflow(wf([taskConfig]))
    assert.equal(result.diagramSafe, true)
    assert.deepEqual(result.problems.map(p => [p.code, p.path]), [['TASK_CONFIG', `/tasks/0${path}`]])
  }
})

test('backend nullable definition loads, switches tabs, edits, and saves through the actor', async () => {
  const original = backendWorkflow()
  let saved
  const actor = await actorFor(original, { saveWorkflowDefinition: async ctx => { saved = structuredClone(ctx.workflow); return saved } })
  assert.equal(actor.state.context.sourceBlocked, false)
  assert.deepEqual(actor.state.context.problems, [])
  actor.send({ type: 'CHANGE_TAB', tab: 'code' })
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(original, null, 2) })
  actor.send({ type: 'CHANGE_TAB', tab: 'workflow' })
  actor.send({ type: 'TASK_SELECTED', task: original.tasks[1] })
  assert.equal(lib.hasWorkflowChanges(actor.state.context), false)
  assert.equal(actor.state.context.flowActor.getSnapshot().context.nodes.some(n => n.id === 'parse'), true)
  const edited = lib.updateTask(original, 'parse', t => ({ ...t, description: '编辑脚本任务描述' }))
  actor.send({ type: 'TASK_UPDATED', task: edited.tasks[1] })
  assert.deepEqual(JSON.parse(actor.state.context.source), edited)
  actor.send({ type: 'REQUEST_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.deepEqual(saved, edited)
  assert.equal(lib.hasWorkflowChanges(actor.state.context), false)
  actor.stop()
})

test('duplicate refs include both exact paths; unresolved JOIN refs are reported', () => {
  const result = lib.validateWorkflow(wf([task('a'), { ...task('switch'), type: 'SWITCH', expression: '${workflow.input}', decisionCases: { 'a/b~c': [task('a')] }, defaultCase: [] }, { ...task('join'), type: 'JOIN', joinOn: ['missing', 'join'] }]))
  assert.equal(result.diagramSafe, false)
  assert.deepEqual(result.problems.filter(p => p.code === 'DUPLICATE_REF').map(p => p.path), ['/tasks/0/taskReferenceName', '/tasks/1/decisionCases/a~1b~0c/0/taskReferenceName'])
  assert.equal(result.problems.filter(p => p.code === 'MISSING_REF').length, 2)
})

test('iFlow task validators preserve expressions and custom task extension rules', () => {
  const custom = { ...task('custom'), type: 'CUSTOM_HL7', inputParameters: { payload: { count: 2, enabled: true, missing: null } } }
  assert.equal(lib.validateWorkflow(wf([custom])).problems.length, 0)
  const dispose = lib.registerTaskPlugin({ type: 'CUSTOM_HL7', validate: () => [{ code: 'CUSTOM_RULE', path: '/inputParameters/channel', message: '请选择通道' }] })
  assert.equal(lib.validateWorkflow(wf([custom])).problems[0].path, '/tasks/0/inputParameters/channel')
  dispose()
  assert.equal(lib.validateWorkflow(wf([custom])).problems.length, 0)
  assert.equal(lib.validateWorkflow(wf([{ ...task('a'), inputParameters: { http_request: '${workflow.input.request}' } }])).problems.length, 0)
  assert.ok(lib.validateWorkflow(wf([{ ...task('spi'), type: 'SPI', inputParameters: { extensionMode: 'ONLINE', spiInput: '${workflow.input}' } }])).problems.some(p => p.path.endsWith('/className')))
  assert.equal(lib.validateWorkflow(wf([{ ...task('spi'), type: 'SPI', inputParameters: { remoteExtension: 'project', extensionMode: 'PROJECT', spiInput: {} } }])).problems.length, 0)
  assert.ok(lib.validateWorkflow(wf([{ ...task('mapping'), type: 'MAPPING', inputParameters: { paramsConfig: {} } }])).problems.length)
  assert.ok(lib.validateWorkflow({ ...wf(), version: 0 }).problems.some(p => p.code === 'VERSION'))
})

test('source ranges locate nested duplicate refs and escaped JSON pointer keys', () => {
  const source = JSON.stringify(wf([{ ...task('s'), decisionCases: { 'a/b~c': [task('a'), task('a')] } }]), null, 2)
  const range = lib.sourceRange(source, '/tasks/0/decisionCases/a~1b~0c/1/taskReferenceName')
  assert.equal(source.slice(range.start, range.end), '"a"')
  assert.ok(range.start > source.indexOf('"a"'))
  const parentRange = lib.sourceRange(source, '/tasks/0/inputParameters')
  assert.deepEqual(lib.sourceRange(source, '/tasks/0/inputParameters/missing'), { start: parentRange.start, end: parentRange.start + 1 })
})

test('invalid source survives tabs, is dirty, freezes the canvas, and blocks save', async () => {
  let saves = 0
  const actor = await actorFor(wf(), { saveWorkflowDefinition: async ctx => { saves++; return ctx.workflow } })
  const graph = actor.state.context.diagramWorkflow
  const source = '{"name":"draft","tasks":['
  actor.send({ type: 'SOURCE_CHANGED', source })
  actor.send({ type: 'CHANGE_TAB', tab: 'workflow' })
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: { ...wf(), description: 'stale form' } })
  assert.equal(actor.state.context.source, source)
  assert.equal(actor.state.context.diagramWorkflow, graph)
  assert.ok(lib.hasWorkflowChanges(actor.state.context))
  actor.send({ type: 'REQUEST_SAVE' })
  actor.send({ type: 'CONFIRM_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.editing'))
  assert.equal(actor.state.context.activeTab, 'code')
  assert.ok(actor.state.context.validationActor.getSnapshot().matches('expanded'))
  assert.equal(saves, 0)
  actor.stop()
})

test('source syncs with canvas edits; duplicate refs never reach graph or save', async () => {
  const actor = await actorFor()
  actor.send({ type: 'CHANGE_TAB', tab: 'code' })
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: wf([task('a'), task('b')]) })
  assert.equal(JSON.parse(actor.state.context.source).tasks.length, 2)
  const graph = actor.state.context.diagramWorkflow
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(wf([task('a'), task('a')])) })
  assert.equal(actor.state.context.diagramWorkflow, graph)
  actor.send({ type: 'REQUEST_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.editing'))
  assert.equal(actor.state.context.problemFocus.problem.path, '/tasks/0/taskReferenceName')
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(wf([task('a'), task('b')])) })
  actor.send({ type: 'REQUEST_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  actor.stop()
})

test('task field drafts persist across selection; source edits and save cannot discard them', async () => {
  const actor = await actorFor(wf([task('a'), task('b')]))
  const path = '/tasks/0/inputParameters/body'
  const draft = { path, taskPath: '/tasks/0', taskReferenceName: 'a', value: '{oops', message: 'JSON 格式不正确' }
  actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft })
  actor.send({ type: 'TASK_SELECTED', task: task('b') })
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(wf([])) })
  assert.equal(actor.state.context.workflow.tasks.length, 2)
  assert.deepEqual(actor.state.context.fieldDrafts[path], draft)
  actor.send({ type: 'REQUEST_SAVE' })
  assert.equal(actor.state.context.selectedTaskRef, 'a')
  assert.equal(actor.state.context.activeTab, 'task')
  actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: null })
  assert.equal(Object.keys(actor.state.context.fieldDrafts).length, 0)
  actor.stop()
})

test('save uses the reviewed snapshot, reports server failure in the persistent inspector, and retries', async () => {
  let rejectSave, resolveSave
  const actor = await actorFor(wf(), { saveWorkflowDefinition: () => new Promise((resolve, reject) => { resolveSave = resolve; rejectSave = reject }) })
  const edited = { ...wf(), description: '待保存' }
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: edited })
  actor.send({ type: 'REQUEST_SAVE' })
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: { ...edited, description: '覆盖 diff' } })
  assert.equal(actor.state.context.workflow.description, '待保存')
  actor.send({ type: 'CONFIRM_SAVE' })
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(wf([])) })
  rejectSave(new Error('服务端拒绝当前定义'))
  await tick()
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  assert.equal(actor.state.context.serverProblems[0].message, '服务端拒绝当前定义')
  assert.equal(actor.state.context.workflow.description, '待保存')
  actor.send({ type: 'CONFIRM_SAVE' })
  assert.equal(actor.state.context.serverProblems.length, 0)
  resolveSave(edited)
  await tick()
  assert.equal(lib.hasWorkflowChanges(actor.state.context), false)
  actor.stop()
})

test('reset and new route clear all raw drafts and validation state', async () => {
  const actor = await actorFor()
  const validation = actor.state.context.validationActor
  actor.send({ type: 'SOURCE_CHANGED', source: '{broken' })
  actor.send({ type: 'REQUEST_RESET' })
  actor.send({ type: 'CONFIRM_RESET' })
  assert.equal(actor.state.context.sourceBlocked, false)
  assert.deepEqual(JSON.parse(actor.state.context.source), wf())
  assert.ok(validation.getSnapshot().matches('collapsed'))
  actor.send({ type: 'SOURCE_CHANGED', source: '{broken' })
  actor.send({ type: 'INITIALIZE', workflowName: 'another', isNewWorkflow: true })
  assert.equal(actor.state.context.source, '')
  assert.equal(validation.getSnapshot().context.problems.length, 0)
  await tick()
  assert.equal(actor.state.context.validationActor, validation)
  actor.stop()
})

test('selection and tab changes reuse the mapped diagram; tasks after SWITCH remain editable', async () => {
  const workflow = wf([{ ...task('s'), type: 'SWITCH', expression: '${workflow.input}', decisionCases: { x: [task('branch')] }, defaultCase: [] }, task('after')])
  assert.equal(lib.findTask(lib.updateTask(workflow, 'after', t => ({ ...t, name: 'changed' })).tasks, 'after').name, 'changed')
  assert.equal(lib.findTask(lib.removeTask(workflow, 'after').tasks, 'after'), undefined)
  assert.ok(lib.findTask(lib.insertAfterTask(workflow, 'after', task('new')).tasks, 'new'))
  const actor = await actorFor(workflow)
  const flow = actor.state.context.flowActor
  const revision = flow.getSnapshot().context.renderRevision
  actor.send({ type: 'CHANGE_TAB', tab: 'code' })
  actor.send({ type: 'TASK_SELECTED', task: workflow.tasks[1] })
  assert.equal(flow.getSnapshot().context.renderRevision, revision)
  actor.stop()
})

test('editing a duplicate or empty task ref keeps the selected task identity', async () => {
  const actor = await actorFor(wf([task('a'), task('b')]))
  actor.send({ type: 'TASK_SELECTED', task: task('b') })
  actor.send({ type: 'TASK_UPDATED', task: { ...task('b'), taskReferenceName: 'a' } })
  assert.equal(actor.state.context.editorActor.getSnapshot().context.selectedTask.name, 'b')
  actor.send({ type: 'TASK_UPDATED', task: { ...task('b'), taskReferenceName: '' } })
  assert.equal(actor.state.context.editorActor.getSnapshot().context.selectedTask.name, 'b')
  actor.send({ type: 'TASK_UPDATED', task: { ...task('b'), taskReferenceName: 'corrected' } })
  assert.deepEqual(actor.state.context.workflow.tasks.map(t => t.taskReferenceName), ['a', 'corrected'])
  assert.equal(actor.state.context.problems.length, 0)
  actor.stop()
})

test('task drafts follow a renamed task; sibling topology cannot orphan the draft', async () => {
  const actor = await actorFor(wf([task('a'), task('b')]))
  const path = '/tasks/1/inputParameters/body'
  actor.send({ type: 'TASK_SELECTED', task: task('b') })
  actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: { path, taskPath: '/tasks/1', taskReferenceName: 'b', value: '{invalid', message: 'JSON 错误' } })
  actor.send({ type: 'TASK_UPDATED', task: { ...task('b'), taskReferenceName: 'renamed' } })
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: wf([]) })
  assert.equal(actor.state.context.workflow.tasks.length, 2)
  actor.send({ type: 'REQUEST_SAVE' })
  assert.equal(actor.state.context.selectedTaskRef, 'renamed')
  assert.equal(actor.state.context.selectedTaskPath, '/tasks/1')
  actor.stop()
})

test('parameter key/add/remove edits preserve structured sibling values', () => {
  const original = { payload: { a: 1 }, list: [1, 2], count: 2, flag: true, nullable: null }
  const rows = lib.editableParameterRows(original)
  assert.deepEqual(lib.parameterValues([...rows, { key: 'new', value: '' }]), { ...original, new: '' })
  assert.deepEqual(lib.parameterValues(rows.map(row => row.key === 'payload' ? { ...row, key: 'renamed' } : row)).renamed, { a: 1 })
  assert.equal(lib.parameterValues(rows.filter(row => row.key !== 'payload')).count, 2)
  assert.equal(lib.parameterValues(rows.map(row => row.key === 'count' ? { ...row, value: 'changed' } : row)).count, 'changed')
})

test('applied raw parameter formatting survives remounts without blocking save; Code supersedes it', async () => {
  const actor = await actorFor()
  const path = '/tasks/0/inputParameters/body'
  actor.send({ type: 'TASK_SELECTED', task: task('a') })
  actor.send({ type: 'TASK_UPDATED', task: { ...task('a'), inputParameters: { ...task('a').inputParameters, body: { count: 2 } } } })
  actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: { path, taskPath: '/tasks/0', taskReferenceName: 'a', value: '{ "count" : 2 }', message: '' } })
  actor.send({ type: 'CHANGE_TAB', tab: 'workflow' })
  assert.equal(actor.state.context.fieldDrafts[path].value, '{ "count" : 2 }')
  actor.send({ type: 'REQUEST_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  actor.send({ type: 'CANCEL_DIALOG' })
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(wf([])) })
  assert.equal(Object.keys(actor.state.context.fieldDrafts).length, 0)
  actor.stop()
})

test('delete confirmation remains bound to the loaded identity after editing name and version', async () => {
  let target
  const actor = await actorFor(wf(), { deleteWorkflowDefinition: ctx => { target = ctx.baseline; return new Promise(() => {}) } })
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: { ...wf(), name: 'other', version: 99 } })
  actor.send({ type: 'REQUEST_DELETE' })
  actor.send({ type: 'UPDATE_DELETE_CONFIRMATION', value: 'other' })
  actor.send({ type: 'CONFIRM_DELETE' })
  assert.ok(actor.state.matches('ready.lifecycle.confirmDelete'))
  assert.equal(target, undefined)
  actor.send({ type: 'UPDATE_DELETE_CONFIRMATION', value: 'review' })
  actor.send({ type: 'CONFIRM_DELETE' })
  assert.equal(target.name, 'review')
  assert.equal(target.version, 1)
  actor.stop()
})

test('task refs cannot collide with a generated SWITCH merge node', () => {
  const result = lib.validateWorkflow(wf([{ ...task('sw'), type: 'SWITCH', expression: '${workflow.input}', decisionCases: {}, defaultCase: [] }, task('sw_switch_join')]))
  assert.equal(result.diagramSafe, false)
  assert.ok(result.problems.some(problem => problem.code === 'RESERVED_REF'))
})

test('latest raw field errors replace stale definition errors for the same field', async () => {
  const actor = await actorFor(wf([{ ...task('m'), type: 'MAPPING', inputParameters: { paramsConfig: {} } }]))
  const path = '/tasks/0/inputParameters/paramsConfig'
  actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: { path, taskPath: '/tasks/0', taskReferenceName: 'm', value: '[unfinished', message: '最新 JSON 草稿未完成' } })
  const problems = actor.state.context.validationActor.getSnapshot().context.problems
  assert.equal(problems.length, 1)
  assert.equal(problems[0].message, '最新 JSON 草稿未完成')
  actor.stop()
})
