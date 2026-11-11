import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createRequire, Module } from 'node:module'
import { fileURLToPath } from 'node:url'

// Keep alias and relative imports on the same task registry instance.
const root = fileURLToPath(new URL('..', import.meta.url)).replace(/\/$/, '')
const requireApp = createRequire(`${root}/package.json`)
const esbuild = createRequire(requireApp.resolve('vite'))('esbuild')
// Bundle the real actors/helpers without mounting the React task catalog.
const built = await esbuild.build({
  stdin: { contents: `
    export { interpret } from 'xstate';
    export { workflowDefinitionMachine } from './src/pages/definition/state/machine';
    export { getWorkflowToSave, hasWorkflowChanges } from './src/pages/definition/state/types';
    export * from './src/pages/definition/validation/validateWorkflow';
    export * from './src/tasks/taskPluginRegistry';
    export { validateBuiltinTask } from './src/tasks/builtinTaskValidators';
    export * from './src/tasks/doWhile';
    export * from './src/flow/workflowEditor';
    export { workflowToReaflow } from './src/flow/conductorMapper';
    export { applyAddTask } from './src/components/flow/ConductorFlowCanvas';
    export * from './src/flow/parameterValues';
    export * from './src/api/workflowDefinition';
    export { startWorkflow } from './src/api/workflowExecution';
    export { default as axios } from 'axios';
    export * from './src/utils/workflowDraft';
    export * from './src/utils/workflowTags';
    export { TaskInspector } from './src/pages/definition/EditorPanel/TaskInspector';
    export { WorkflowInspector } from './src/pages/definition/EditorPanel/WorkflowInspector';
    export { ValidationScope } from './src/pages/definition/validation/ValidationScope';
    export { QueryClient, QueryClientProvider } from '@tanstack/react-query';
    export { renderToStaticMarkup } from 'react-dom/server';
    export { createElement } from 'react';
    export * from './src/pages/definitions/workflowTemplates';
    export * from './src/pages/runWorkflow/gatewayInput';
  `, resolveDir: root, sourcefile: 'workflow-definition-test.ts' },
  bundle: true, platform: 'node', format: 'cjs', write: false, tsconfig: `${root}/tsconfig.json`, logLevel: 'silent',
  plugins: [{ name: 'headless-task-catalog', setup(build) {
    build.onResolve({ filter: /^@\/tasks$/ }, () => ({ path: `${root}/src/tasks/taskPluginRegistry.ts` }))
    build.onResolve({ filter: /^\.\/TaskFormTab\/forms$/ }, () => ({ path: 'task-form', namespace: 'headless-task-form' }))
    build.onLoad({ filter: /.*/, namespace: 'headless-task-form' }, () => ({ contents: 'export const UnknownTaskForm = () => null', loader: 'js' }))
  } }],
})
const compiled = new Module(`${root}/workflow-definition-test.cjs`)
compiled.filename = `${root}/workflow-definition-test.cjs`
compiled._compile(built.outputFiles[0].text, compiled.filename)
const lib = compiled.exports
lib.registerCoreTaskPlugins(['HTTP', 'INLINE', 'SWITCH', 'FORK_JOIN', 'JOIN', 'DO_WHILE', 'SPI', 'JDBC', 'WEBSERVICE', 'MAPPING'].map(type => ({
  type, label: type, validate: lib.validateBuiltinTask,
  ...(type === 'DO_WHILE' ? { variableOptions: lib.doWhileVariableOptions } : {}),
  flow: { kind: ({ SWITCH: 'switch', FORK_JOIN: 'fork', JOIN: 'join', DO_WHILE: 'do_while' })[type] ?? 'task' },
})))
const task = ref => ({ name: ref, taskReferenceName: ref, type: 'HTTP', inputParameters: { uri: 'https://example.test', method: 'GET' } })
const wf = (tasks = [task('a')]) => ({ route: 'review', version: 1, name: '测试', description: '测试定义', tasks })

function addAfterSwitch(workflow, reference, type = 'HTTP') {
  const node = lib.workflowToReaflow(workflow).nodes.find(node => node.id === `${reference}_switch_join`)
  assert.ok(node)
  const port = node.ports.find(port => port.side === 'SOUTH')
  assert.ok(port)
  return lib.applyAddTask(workflow, { kind: 'port', node, port: { id: port.id, properties: port } }, type)
}

test('SWITCH end add control inserts after the whole switch, including before an existing successor', () => {
  for (const remaining of [[], [task('after')]]) {
    const sw = { ...task('sw'), type: 'SWITCH', decisionCases: { yes: [task('caseTask')] }, defaultCase: [] }
    const workflow = wf([task('before'), sw, ...remaining])
    const original = structuredClone(workflow)
    const updated = addAfterSwitch(workflow, 'sw')
    assert.equal(updated.tasks.length, workflow.tasks.length + 1)
    assert.equal(updated.tasks[2].type, 'HTTP')
    assert.ok(updated.tasks[2].taskReferenceName)
    assert.deepEqual(updated.tasks.slice(0, 2), [task('before'), sw])
    assert.deepEqual(updated.tasks.slice(3), remaining)
    assert.deepEqual(workflow, original)
    const graph = lib.workflowToReaflow(updated)
    assert.ok(graph.edges.some(edge => edge.from === 'sw_switch_join' && edge.to === updated.tasks[2].taskReferenceName))
    assert.equal(updated.tasks.some(task => task.type === 'SWITCH_JOIN'), false)
  }
})

test('nested SWITCH end add control inserts a complete task sequence in the owning branch', () => {
  const unregister = lib.registerTaskPlugin({ type: 'FORK_JOIN', sequence: ['JOIN'] })
  try {
    for (const container of ['case', 'default', 'fork', 'loop']) {
      const sw = { ...task('nested_switch_join'), type: 'SWITCH', decisionCases: { yes: [] }, defaultCase: [task('fallback')] }
      const branch = [sw, task('branchAfter')]
      const parent = {
        case: { ...task('parent'), type: 'SWITCH', decisionCases: { yes: branch }, defaultCase: [] },
        default: { ...task('parent'), type: 'SWITCH', decisionCases: { yes: [task('otherCase')] }, defaultCase: branch },
        fork: { ...task('parent'), type: 'FORK_JOIN', forkTasks: [branch, [task('otherBranch')]] },
        loop: { ...task('parent'), type: 'DO_WHILE', loopOver: branch },
      }[container]
      const workflow = wf([parent, task('outerAfter')])
      const original = structuredClone(workflow)
      const updated = addAfterSwitch(workflow, sw.taskReferenceName, 'FORK_JOIN')
      const updatedParent = updated.tasks[0]
      const updatedBranch = ({ case: updatedParent.decisionCases?.yes, default: updatedParent.defaultCase,
        fork: updatedParent.forkTasks?.[0], loop: updatedParent.loopOver })[container]
      assert.deepEqual(updatedBranch.map(task => task.type), ['SWITCH', 'FORK_JOIN', 'JOIN', 'HTTP'])
      assert.deepEqual(updatedBranch[0], sw)
      assert.deepEqual(updatedBranch[3], task('branchAfter'))
      assert.deepEqual(updated.tasks[1], task('outerAfter'))
      assert.deepEqual(workflow, original)
    }
  } finally {
    unregister()
  }
})

test('startup expressions suggest only declared workflow inputs with valid field paths', () => {
  const workflow = { ...wf([task('later')]), inputParameters: ['data', 'patientInfo', 'data', '', 'a.b', '患者'] }
  assert.deepEqual(lib.buildWorkflowInputOptions(workflow), [
    '${workflow.input}', '${workflow.input.data}', '${workflow.input.patientInfo}',
    '${workflow.input["a.b"]}', '${workflow.input["患者"]}',
  ])
  assert.deepEqual(lib.buildWorkflowInputOptions({ ...workflow, inputParameters: null }), ['${workflow.input}'])
})

test('task expressions suggest preceding outputs and exclude current and future tasks', () => {
  const workflow = wf([task('first'), task('current'), task('last')])
  const original = structuredClone(workflow)
  assert.deepEqual(lib.buildVariableOptions(workflow, workflow.tasks[0]), ['${workflow.input}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, workflow.tasks[1]), ['${workflow.input}', '${first.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, workflow.tasks[2]), ['${workflow.input}', '${first.output}', '${current.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow), ['${workflow.input}', '${first.output}', '${current.output}', '${last.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, task('missing')), ['${workflow.input}'])
  assert.deepEqual(workflow, original)
})

test('SWITCH branches inherit upstream outputs but cannot reference sibling or later branches', () => {
  const a = [task('a1'), task('a2')]
  const b = [task('b1')]
  const fallback = [task('fallback')]
  const sw = { ...task('sw'), type: 'SWITCH', decisionCases: { a, b }, defaultCase: fallback }
  const after = task('after')
  const workflow = wf([task('before'), sw, after])
  assert.deepEqual(lib.buildVariableOptions(workflow, sw), ['${workflow.input}', '${before.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, a[1]), ['${workflow.input}', '${before.output}', '${sw.output}', '${a1.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, b[0]), ['${workflow.input}', '${before.output}', '${sw.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, fallback[0]), ['${workflow.input}', '${before.output}', '${sw.output}'])
  // After SWITCH, the selected branch can supply output; unselected outputs resolve to null.
  assert.deepEqual(lib.buildVariableOptions(workflow, after), [
    '${workflow.input}', '${before.output}', '${sw.output}', '${a1.output}', '${a2.output}', '${b1.output}', '${fallback.output}',
  ])
})

test('FORK branches stay isolated until JOIN and partial JOIN exposes only waited prefixes', () => {
  const a = [task('a1'), task('a2')]
  const b = [task('b1')]
  const fork = { ...task('fork'), type: 'FORK_JOIN', forkTasks: [a, b] }
  const between = task('between')
  const partialJoin = { ...task('partial'), type: 'JOIN', joinOn: ['a1'] }
  const afterPartial = task('afterPartial')
  const join = { ...task('join'), type: 'JOIN', joinOn: ['a2', 'b1'] }
  const after = task('after')
  const workflow = wf([task('before'), fork, between, partialJoin, afterPartial, join, after])
  assert.deepEqual(lib.buildVariableOptions(workflow, a[1]), ['${workflow.input}', '${before.output}', '${fork.output}', '${a1.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, b[0]), ['${workflow.input}', '${before.output}', '${fork.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, between), ['${workflow.input}', '${before.output}', '${fork.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, afterPartial), [
    '${workflow.input}', '${before.output}', '${fork.output}', '${between.output}', '${a1.output}', '${partial.output}',
  ])
  assert.deepEqual(lib.buildVariableOptions(workflow, after), [
    '${workflow.input}', '${before.output}', '${fork.output}', '${between.output}', '${a1.output}', '${partial.output}',
    '${afterPartial.output}', '${a2.output}', '${b1.output}', '${join.output}',
  ])
})

test('nested FORK and JOIN expose completed inner branches to their continuation', () => {
  const innerA = task('innerA')
  const innerB = task('innerB')
  const innerFork = { ...task('innerFork'), type: 'FORK_JOIN', forkTasks: [[innerA], [innerB]] }
  const innerJoin = { ...task('innerJoin'), type: 'JOIN', joinOn: ['innerA', 'innerB'] }
  const tail = task('tail')
  const outerOther = task('outerOther')
  const outerFork = { ...task('outerFork'), type: 'FORK_JOIN', forkTasks: [[innerFork, innerJoin, tail], [outerOther]] }
  const outerJoin = { ...task('outerJoin'), type: 'JOIN', joinOn: ['tail', 'outerOther'] }
  const after = task('after')
  const workflow = wf([outerFork, outerJoin, after])
  assert.deepEqual(lib.buildVariableOptions(workflow, innerA), ['${workflow.input}', '${outerFork.output}', '${innerFork.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, tail), [
    '${workflow.input}', '${outerFork.output}', '${innerFork.output}', '${innerA.output}', '${innerB.output}', '${innerJoin.output}',
  ])
  assert.deepEqual(lib.buildVariableOptions(workflow, after), [
    '${workflow.input}', '${outerFork.output}', '${innerFork.output}', '${innerA.output}', '${innerB.output}', '${innerJoin.output}',
    '${tail.output}', '${outerOther.output}', '${outerJoin.output}',
  ])
})

test('JOIN options contain only fork branch tails and do not change the saved selection', () => {
  const loop = { ...task('loop'), type: 'DO_WHILE', loopOver: [task('loopBody')] }
  const sw = { ...task('switch'), type: 'SWITCH', decisionCases: { yes: [task('caseTail')] }, defaultCase: [] }
  const fork = { ...task('fork'), type: 'FORK_JOIN', forkTasks: [[task('a1'), task('a2')], [loop], [sw], []] }
  const join = { ...task('join'), type: 'JOIN', joinOn: ['a2', 'loop', 'legacy_ref'] }
  const workflow = wf([task('before'), fork, join, task('after')])
  const original = structuredClone(workflow)
  assert.deepEqual(lib.buildJoinTaskOptions(workflow, join), ['a2', 'loop', 'switch'])
  assert.deepEqual(lib.buildJoinTaskOptions(workflow, fork), [])
  assert.deepEqual(workflow, original)
})

test('nested JOIN options stay within their FORK, SWITCH case, default case and loop body', () => {
  const forkJoin = prefix => [
    { ...task(`${prefix}Fork`), type: 'FORK_JOIN', forkTasks: [[task(`${prefix}A`)], [task(`${prefix}B`)]] },
    { ...task(`${prefix}Join`), type: 'JOIN', joinOn: [] },
  ]
  const inner = forkJoin('inner')
  const yes = forkJoin('yes')
  const fallback = forkJoin('fallback')
  const body = forkJoin('loop')
  const sw = { ...task('switch'), type: 'SWITCH', decisionCases: { yes }, defaultCase: fallback }
  const loop = { ...task('loop'), type: 'DO_WHILE', loopOver: body }
  const outerFork = { ...task('outerFork'), type: 'FORK_JOIN', forkTasks: [inner, [sw, loop]] }
  const outerJoin = { ...task('outerJoin'), type: 'JOIN', joinOn: [] }
  const workflow = wf([outerFork, outerJoin])
  for (const [prefix, branch] of [['inner', inner], ['yes', yes], ['fallback', fallback], ['loop', body]]) {
    assert.deepEqual(lib.buildJoinTaskOptions(workflow, branch[1]), [`${prefix}A`, `${prefix}B`])
  }
  assert.deepEqual(lib.buildJoinTaskOptions(workflow, outerJoin), ['innerJoin', 'loop'])
})

test('later JOIN options exclude branch tails already waited for by earlier JOINs', () => {
  const firstFork = { ...task('firstFork'), type: 'FORK_JOIN', forkTasks: [[task('a1'), task('a2')], [task('b')]] }
  const firstJoin = { ...task('firstJoin'), type: 'JOIN', joinOn: ['a2'] }
  const secondFork = { ...task('secondFork'), type: 'FORK_JOIN', forkTasks: [[task('c')], [task('d')]] }
  const secondJoin = { ...task('secondJoin'), type: 'JOIN', joinOn: ['b', 'c', 'd'] }
  const futureFork = { ...task('futureFork'), type: 'FORK_JOIN', forkTasks: [[task('future')]] }
  const workflow = wf([firstFork, firstJoin, task('between'), secondFork, secondJoin, futureFork])
  assert.deepEqual(lib.buildJoinTaskOptions(workflow, firstJoin), ['a2', 'b'])
  assert.deepEqual(lib.buildJoinTaskOptions(workflow, secondJoin), ['b', 'c', 'd'])
  assert.deepEqual(lib.buildJoinTaskOptions(workflow, { ...task('missing'), type: 'JOIN' }), [])
  const standaloneJoin = { ...task('standalone'), type: 'JOIN' }
  assert.deepEqual(lib.buildJoinTaskOptions(wf([standaloneJoin]), standaloneJoin), [])
})

test('JOIN options follow edited branch tails and exclude terminated or empty branches', () => {
  const unregister = lib.registerTaskPlugin({ type: 'TERMINATE', flow: { blocksContinuation: true } })
  try {
    const fork = { ...task('fork'), type: 'FORK_JOIN', forkTasks: [
      [task('a')], [], [{ ...task('stop'), type: 'TERMINATE' }],
      [{ ...task('stopBeforeTail'), type: 'TERMINATE' }, task('unreachable')],
    ] }
    const join = { ...task('join'), type: 'JOIN', joinOn: ['a'] }
    const workflow = wf([fork, join])
    assert.deepEqual(lib.buildJoinTaskOptions(workflow, join), ['a'])
    const appended = lib.appendToForkBranch(workflow, 'fork', 0, [task('newTail')])
    assert.deepEqual(lib.buildJoinTaskOptions(appended, appended.tasks[1]), ['newTail'])
    assert.deepEqual(appended.tasks[1].joinOn, ['newTail'])
    const removed = lib.removeForkBranch(appended, 'fork', 0)
    assert.deepEqual(lib.buildJoinTaskOptions(removed, removed.tasks[1]), [])
    assert.deepEqual(removed.tasks[1].joinOn, [])
  } finally {
    unregister()
  }
})

test('adding a fork branch then deleting its task removes its JOIN reference', () => {
  const original = wf([
    { ...task('fork'), type: 'FORK_JOIN', forkTasks: [[task('a')], [task('b')]] },
    { ...task('join'), type: 'JOIN', joinOn: ['a', 'b'] },
  ])
  const added = lib.appendToForkBranch(lib.addForkBranch(original, 'fork'), 'fork', 2, [task('newBranchTask')])
  assert.deepEqual(added.tasks[1].joinOn, ['a', 'b', 'newBranchTask'])
  const removed = lib.removeTask(added, 'newBranchTask')
  assert.deepEqual(removed.tasks[1].joinOn, ['a', 'b'])
  assert.deepEqual(removed.tasks[0].forkTasks[2], [])
  assert.deepEqual(lib.validateWorkflow(removed).problems, [])
  assert.deepEqual(added.tasks[1].joinOn, ['a', 'b', 'newBranchTask'])
  assert.deepEqual(original.tasks[1].joinOn, ['a', 'b'])
})

test('removing a branch cleans all deleted descendants from every JOIN', () => {
  const loop = { ...task('loop'), type: 'DO_WHILE', loopItems: '${workflow.input.items}', loopOver: [task('body')] }
  const fork = { ...task('fork'), type: 'FORK_JOIN', forkTasks: [[loop, task('tail')], [task('keep')]] }
  const partial = { ...task('partial'), type: 'JOIN', joinOn: ['body', 'loop'] }
  const join = { ...task('join'), type: 'JOIN', joinOn: ['tail', 'keep'] }
  const original = wf([fork, partial, join])
  const removed = lib.removeForkBranch(original, 'fork', 0)
  assert.deepEqual(removed.tasks[1].joinOn, [])
  assert.deepEqual(removed.tasks[2].joinOn, ['keep'])
  assert.deepEqual(lib.validateWorkflow(removed).problems, [])
  assert.deepEqual(original.tasks[1].joinOn, ['body', 'loop'])
})

test('deleting nested operators cleans descendant JOIN references and preserves unchecked branches', () => {
  const innerFork = { ...task('innerFork'), type: 'FORK_JOIN', forkTasks: [[task('innerA')], [task('innerB')]] }
  const innerJoin = { ...task('innerJoin'), type: 'JOIN', joinOn: ['innerA', 'innerB'] }
  const fork = { ...task('fork'), type: 'FORK_JOIN', forkTasks: [[innerFork, innerJoin], [task('unchecked')], [task('keep')]] }
  const join = { ...task('join'), type: 'JOIN', joinOn: ['innerA', 'innerJoin', 'keep'] }
  const original = wf([fork, join])
  const removed = lib.removeTask(original, 'innerFork')
  assert.deepEqual(removed.tasks[0].forkTasks[0], [])
  assert.deepEqual(removed.tasks[1].joinOn, ['keep'])
  assert.deepEqual(lib.validateWorkflow(removed).problems, [])
})

test('form branch removal synchronizes JOIN, source and validation before saving', async () => {
  const fork = { ...task('fork'), type: 'FORK_JOIN', forkTasks: [[task('keep')], [task('removed')]] }
  const join = { ...task('join'), type: 'JOIN', joinOn: ['keep', 'removed'] }
  const actor = await actorFor(wf([fork, join]))
  actor.send({ type: 'TASK_SELECTED', task: fork })
  actor.send({ type: 'TASK_UPDATED', task: { ...fork, forkTasks: [[task('keep')]] } })
  assert.deepEqual(actor.state.context.workflow.tasks[1].joinOn, ['keep'])
  assert.deepEqual(JSON.parse(actor.state.context.source).tasks[1].joinOn, ['keep'])
  assert.deepEqual(actor.state.context.problems, [])
  assert.deepEqual(lib.getWorkflowToSave(actor.state.context).tasks[1].joinOn, ['keep'])
  actor.stop()
})

test('explicit source edits keep missing JOIN references for validation', async () => {
  const fork = { ...task('fork'), type: 'FORK_JOIN', forkTasks: [[task('keep')], [task('removed')]] }
  const join = { ...task('join'), type: 'JOIN', joinOn: ['keep', 'removed'] }
  const actor = await actorFor(wf([fork, join]))
  const source = JSON.stringify(wf([{ ...fork, forkTasks: [[task('keep')]] }, join]))
  actor.send({ type: 'SOURCE_CHANGED', source })
  assert.equal(actor.state.context.source, source)
  assert.deepEqual(actor.state.context.workflow.tasks[1].joinOn, ['keep', 'removed'])
  assert.ok(actor.state.context.problems.some(problem => problem.code === 'MISSING_REF'))
  actor.stop()
})

test('DO_WHILE condition parameters can reference its body without exposing future outputs to body tasks', () => {
  const body = [task('body1'), task('body2')]
  const loop = { ...task('loop'), type: 'DO_WHILE', loopOver: body }
  const after = task('after')
  const workflow = wf([task('before'), loop, after])
  assert.deepEqual(lib.buildVariableOptions(workflow, body[0]), ['${workflow.input}', '${before.output}', '${loop.output}', '${loop.output.iteration}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, body[1]), ['${workflow.input}', '${before.output}', '${loop.output}', '${loop.output.iteration}', '${body1.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, loop), ['${workflow.input}', '${before.output}', '${body1.output}', '${body2.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, loop, false), ['${workflow.input}', '${before.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, after), ['${workflow.input}', '${before.output}', '${loop.output}', '${loop.output.iteration}', '${body1.output}', '${body2.output}'])
})

test('loopItems configuration exposes loopItem and loopIndex to body tasks', () => {
  const body = [task('body1'), task('body2')]
  const loop = { name: '遍历', taskReferenceName: 'loop', type: 'DO_WHILE', loopItems: '${before.output.entries}', loopOver: body }
  const workflow = wf([task('before'), loop, task('after')])
  assert.deepEqual(lib.buildVariableOptions(workflow, body[0]), [
    '${workflow.input}', '${before.output}', '${loop.output}', '${loop.output.iteration}', '${loop.output.loopItem}', '${loop.output.loopIndex}',
  ])
  assert.deepEqual(lib.buildVariableOptions(workflow, body[1]), [
    '${workflow.input}', '${before.output}', '${loop.output}', '${loop.output.iteration}', '${loop.output.loopItem}', '${loop.output.loopIndex}', '${body1.output}',
  ])
  const conditionWorkflow = wf([task('before'), { ...loop, loopItems: null, loopCondition: 'false' }, task('after')])
  assert.deepEqual(lib.buildVariableOptions(conditionWorkflow, body[0]), [
    '${workflow.input}', '${before.output}', '${loop.output}', '${loop.output.iteration}',
  ])
})

test('editor suggestions follow selection, topology edits and workflow output scope', async () => {
  const workflow = wf([task('a'), task('b'), task('c')])
  const actor = await actorFor(workflow)
  const options = () => actor.state.context.editorActor.getSnapshot().context.variableOptions
  actor.send({ type: 'TASK_SELECTED', task: workflow.tasks[0] })
  assert.deepEqual(options(), ['${workflow.input}'])
  actor.send({ type: 'TASK_SELECTED', task: workflow.tasks[2] })
  assert.deepEqual(options(), ['${workflow.input}', '${a.output}', '${b.output}'])
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: wf([workflow.tasks[2], workflow.tasks[0], workflow.tasks[1]]) })
  assert.deepEqual(options(), ['${workflow.input}'])
  actor.send({ type: 'CHANGE_TAB', tab: 'workflow' })
  assert.deepEqual(options(), ['${workflow.input}', '${c.output}', '${a.output}', '${b.output}'])
  actor.stop()
})
const backendTaskDefaults = {
  decisionCases: {}, defaultCase: null, forkTasks: [], joinOn: [], defaultExclusiveJoinTask: [],
  evaluatorType: null, expression: null, optional: false, permissive: false,
  loopOver: [], loopCondition: null, loopItems: null, retryCount: 0,
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
async function actorFor(workflow = wf(), services = {}, actions = {}) {
  const actor = lib.interpret(lib.workflowDefinitionMachine.withConfig({
    services: {
      loadWorkflowDefinition: async () => ({ workflow, versions: [1] }),
      loadVersionsForSave: async () => [workflow.version],
      ...services,
    },
    actions: { notifyWorkflowSaved: () => {}, notifyWorkflowReset: () => {}, navigateToSavedWorkflow: () => {}, ...actions },
  })).start()
  actor.send({ type: 'INITIALIZE', workflowRoute: workflow.route, workflowVersion: 1, isNewWorkflow: false })
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
    loopCondition: null, loopItems: null, joinOn: null, defaultExclusiveJoinTask: null, expression: null, evaluatorType: null,
  }
  const workflow = { ...wf([custom]), correlationExpression: null, timeoutPolicy: null, inputParameters: null, outputParameters: null }
  assert.deepEqual(lib.validateWorkflow(workflow).problems, [])
  assert.deepEqual(lib.updateTask(workflow, 'custom', t => ({ ...t, name: '改名称' })).tasks[0], { ...custom, name: '改名称' })
  for (const invalid of [
    { ...wf(), route: null }, wf([{ ...task('a'), type: null }]), wf([{ ...task('a'), taskReferenceName: null }]),
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
  ]) {
    const result = lib.validateWorkflow(wf([taskConfig]))
    assert.equal(result.diagramSafe, true)
    assert.deepEqual(result.problems.map(p => [p.code, p.path]), [['TASK_CONFIG', `/tasks/0${path}`]])
  }
})

const doWhile = (config = {}) => ({
  name: '循环', taskReferenceName: 'loop', type: 'DO_WHILE', inputParameters: {},
  loopOver: [task('body')], ...config,
})

test('DO_WHILE accepts exactly one array expression or JS condition without rewriting definitions', () => {
  for (const config of [
    { loopItems: '${workflow.input.entries}' },
    { loopItems: ' ${workflow.input.entries} ', loopCondition: null },
    { loopItems: null, loopCondition: 'false' },
    { loopCondition: '$.iteration < 3' },
  ]) {
    const loop = doWhile(config)
    const workflow = wf([loop])
    const original = structuredClone(workflow)
    assert.deepEqual(lib.validateWorkflow(workflow).problems, [])
    assert.equal(lib.doWhileStrategy(loop), config.loopItems != null ? 'array' : 'js')
    assert.deepEqual(workflow, original)
  }
})

test('DO_WHILE rejects conflicting, missing, literal, blank, reserved and non-JS configuration', () => {
  for (const [config, paths] of [
    [{}, ['/loopItems', '/loopCondition']],
    [{ loopItems: '${workflow.input.entries}', loopCondition: 'false' }, ['/loopItems', '/loopCondition']],
    [{ loopItems: '' }, ['/loopItems']],
    [{ loopItems: '[1,2]' }, ['/loopItems']],
    [{ loopItems: 'prefix-${workflow.input.entries}' }, ['/loopItems']],
    [{ loopItems: '${workflow.input.a}${workflow.input.b}' }, ['/loopItems']],
    [{ loopCondition: ' ' }, ['/loopCondition']],
    [{ loopCondition: 'false', evaluatorType: 'value-param' }, ['/evaluatorType']],
    [{ loopItems: '${workflow.input.entries}', inputParameters: { loopItems: [] } }, ['/inputParameters/loopItems']],
    [{ items: '${workflow.input.entries}' }, ['/loopItems', '/loopCondition']],
    [{ inputParameters: { _items: [1, 2] } }, ['/loopItems', '/loopCondition']],
  ]) {
    const loop = doWhile(config)
    const original = structuredClone(loop)
    const result = lib.validateWorkflow(wf([loop]))
    assert.equal(result.diagramSafe, true)
    assert.deepEqual(result.problems.map(problem => problem.path), paths.map(path => `/tasks/0${path}`))
    assert.deepEqual(loop, original)
  }
  const malformed = lib.validateWorkflow(wf([doWhile({ loopItems: [] })]))
  assert.equal(malformed.diagramSafe, false)
  assert.ok(malformed.problems.some(problem => problem.path === '/tasks/0/loopItems' && problem.code === 'STRING'))
})

test('loop strategy changes remove the other field and retain body and typed business parameters', () => {
  const js = doWhile({ loopCondition: 'false', evaluatorType: 'graaljs', inputParameters: { evaluatorType: 'graaljs', options: { retries: 2 }, keepLastN: 2 } })
  const original = structuredClone(js)
  const array = lib.changeDoWhileStrategy(js, 'array')
  assert.equal(array.loopItems, '')
  assert.equal(lib.doWhileStrategy(array), 'array')
  assert.equal(Object.hasOwn(array, 'loopCondition'), false)
  assert.equal(Object.hasOwn(array, 'evaluatorType'), false)
  assert.deepEqual(array.inputParameters, { options: { retries: 2 }, keepLastN: 2 })
  assert.deepEqual(array.loopOver, js.loopOver)
  assert.deepEqual(js, original)
  const configured = { ...array, loopItems: '${workflow.input.entries}' }
  const restoredJs = lib.changeDoWhileStrategy(configured, 'js')
  assert.equal(restoredJs.loopCondition, 'false')
  assert.equal(Object.hasOwn(restoredJs, 'loopItems'), false)
  assert.deepEqual(restoredJs.inputParameters, configured.inputParameters)
  assert.equal(Object.hasOwn(restoredJs, 'loopMode'), false)
})

test('array expression suggestions exclude the body while JS parameters can reference it', () => {
  const loop = doWhile({ loopItems: '${before.output.entries}' })
  const workflow = wf([task('before'), loop, task('after')])
  assert.deepEqual(lib.buildVariableOptions(workflow, loop, false), ['${workflow.input}', '${before.output}'])
  assert.deepEqual(lib.buildVariableOptions(workflow, loop), ['${workflow.input}', '${before.output}', '${body.output}'])
  assert.deepEqual(lib.doWhileVariableOptions(loop), ['${loop.output}', '${loop.output.iteration}', '${loop.output.loopItem}', '${loop.output.loopIndex}'])
  assert.deepEqual(lib.doWhileVariableOptions(doWhile({ loopCondition: 'false' })), ['${loop.output}', '${loop.output.iteration}'])
})

test('blank array selection survives task and source tab changes and blocks saving', async () => {
  const original = wf([doWhile({ loopCondition: 'false' }), task('after')])
  let saves = 0
  const actor = await actorFor(original, { saveWorkflowDefinition: async context => { saves++; return context.workflow } })
  actor.send({ type: 'TASK_SELECTED', task: original.tasks[0] })
  const arrayDraft = lib.changeDoWhileStrategy(original.tasks[0], 'array')
  actor.send({ type: 'TASK_UPDATED', task: arrayDraft })
  assert.equal(lib.doWhileStrategy(JSON.parse(actor.state.context.source).tasks[0]), 'array')
  actor.send({ type: 'CHANGE_TAB', tab: 'code' })
  actor.send({ type: 'CHANGE_TAB', tab: 'task' })
  actor.send({ type: 'TASK_SELECTED', task: actor.state.context.workflow.tasks[1] })
  actor.send({ type: 'TASK_SELECTED', task: actor.state.context.workflow.tasks[0] })
  assert.equal(lib.doWhileStrategy(actor.state.context.workflow.tasks[0]), 'array')
  actor.send({ type: 'REQUEST_SAVE' })
  assert.equal(saves, 0)
  assert.equal(actor.state.context.problemFocus.problem.path, '/tasks/0/loopItems')
  assert.ok(actor.state.matches('ready.lifecycle.editing'))
  actor.stop()
})

test('both DO_WHILE strategies require a body', () => {
  for (const config of [
    { loopCondition: 'true' },
    { loopItems: '${workflow.input.entries}' },
  ]) {
    for (const loopOver of [undefined, null, []]) {
      const result = lib.validateWorkflow(wf([doWhile({ ...config, loopOver })]))
      assert.equal(result.diagramSafe, true)
      assert.deepEqual(result.problems.map(problem => problem.path), ['/tasks/0/loopOver'])
    }
  }
})

test('nested DO_WHILE validation reports the body with escaped branch paths', () => {
  const loop = doWhile({ loopItems: '${workflow.input.entries}', loopOver: [] })
  const workflow = wf([{ ...task('switch'), type: 'SWITCH', expression: '${workflow.input.choice}', decisionCases: { 'a/b~c': [loop] } }])
  const result = lib.validateWorkflow(workflow)
  assert.equal(result.diagramSafe, true)
  assert.deepEqual(result.problems.map(problem => [problem.path, problem.taskPath, problem.taskReferenceName]), [
    ['/tasks/0/decisionCases/a~1b~0c/0/loopOver', '/tasks/0/decisionCases/a~1b~0c/0', 'loop'],
  ])
})

test('DO_WHILE array configuration roundtrip through source and the reviewed save snapshot', async () => {
  const original = wf([doWhile({ loopItems: null, loopCondition: 'false', inputParameters: { retry: 2 } })])
  let saved
  const actor = await actorFor(original, { saveWorkflowDefinition: async context => { saved = structuredClone(lib.getWorkflowToSave(context)); return saved } })
  actor.send({ type: 'TASK_SELECTED', task: original.tasks[0] })
  const collection = { ...lib.changeDoWhileStrategy(original.tasks[0], 'array'), loopItems: '${workflow.input.entries}' }
  actor.send({ type: 'TASK_UPDATED', task: collection })
  assert.deepEqual(actor.state.context.problems, [])
  assert.deepEqual(JSON.parse(actor.state.context.source).tasks[0], collection)
  actor.send({ type: 'CHANGE_TAB', tab: 'code' })
  actor.send({ type: 'SOURCE_CHANGED', source: actor.state.context.source })
  actor.send({ type: 'CHANGE_TAB', tab: 'workflow' })
  actor.send({ type: 'REQUEST_SAVE' })
  await tick()
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.deepEqual(saved, { ...original, version: 2, tasks: [collection] })
  assert.equal(Object.hasOwn(saved.tasks[0], 'loopCondition'), false)
  assert.equal(saved.tasks[0].loopItems, '${workflow.input.entries}')
  assert.equal(Object.hasOwn(saved.tasks[0], 'items'), false)
  assert.equal(lib.hasWorkflowChanges(actor.state.context), false)
  actor.stop()
})

test('DO_WHILE empty body cannot reach save', async () => {
  let saves = 0
  const actor = await actorFor(wf(), { saveWorkflowDefinition: async context => { saves++; return context.workflow } })
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(wf([doWhile({ loopItems: '${workflow.input.entries}', loopOver: [] })])) })
  actor.send({ type: 'REQUEST_SAVE' })
  actor.send({ type: 'CONFIRM_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.editing'))
  assert.equal(actor.state.context.problemFocus.problem.path, '/tasks/0/loopOver')
  assert.equal(saves, 0)
  actor.stop()
})

test('backend nullable definition loads, switches tabs, edits, and saves through the actor', async () => {
  const original = backendWorkflow()
  let saved
  const actor = await actorFor(original, { saveWorkflowDefinition: async ctx => { saved = structuredClone(lib.getWorkflowToSave(ctx)); return saved } })
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
  await tick()
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.deepEqual(saved, { ...edited, version: 2 })
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
  assert.equal(lib.validateWorkflow(wf([{ ...task('a'), inputParameters: { uri: '${workflow.input.uri}', method: '${workflow.input.method}' } }])).problems.length, 0)
  assert.ok(lib.validateWorkflow(wf([{ ...task('spi'), type: 'SPI', inputParameters: { extensionMode: 'ONLINE', spiInput: '${workflow.input}' } }])).problems.some(p => p.path.endsWith('/className')))
  assert.equal(lib.validateWorkflow(wf([{ ...task('spi'), type: 'SPI', inputParameters: { remoteExtension: 'project', extensionMode: 'PROJECT', spiInput: {} } }])).problems.length, 0)
  assert.ok(lib.validateWorkflow(wf([{ ...task('mapping'), type: 'MAPPING', inputParameters: { paramsConfig: {} } }])).problems.length)
  assert.ok(lib.validateWorkflow({ ...wf(), version: 0 }).problems.some(p => p.code === 'VERSION'))
})

test('HTTP validation rejects Conductor request wrappers before saving to the iFlow engine', () => {
  for (const request of [{ uri: 'https://example.test', method: 'POST' }, '${workflow.input.request}']) {
    const result = lib.validateWorkflow(wf([{ ...task('a'), inputParameters: { http_request: request } }]))
    assert.ok(result.problems.some(p => p.path === '/tasks/0/inputParameters/http_request'))
    assert.ok(result.problems.some(p => p.path === '/tasks/0/inputParameters/uri'))
    assert.ok(result.problems.some(p => p.path === '/tasks/0/inputParameters/method'))
  }
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
  await tick()
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
  await tick()
  actor.send({ type: 'CONFIRM_SAVE' })
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(wf([])) })
  rejectSave(new Error('服务端拒绝当前定义'))
  await tick()
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  assert.equal(actor.state.context.serverProblems[0].message, '服务端拒绝当前定义')
  assert.equal(actor.state.context.workflow.description, '待保存')
  actor.send({ type: 'CONFIRM_SAVE' })
  assert.equal(actor.state.context.serverProblems.length, 0)
  resolveSave({ ...edited, version: 2 })
  await tick()
  assert.equal(lib.hasWorkflowChanges(actor.state.context), false)
  actor.stop()
})

test('save before leaving freezes the draft and updates the baseline only after success', async () => {
  let resolveSave, saved, notifications = 0, editorNavigations = 0
  const edited = { ...wf(), description: '退出前保存' }
  const expected = { ...edited, version: 4 }
  const actor = await actorFor(wf(), {
    loadVersionsForSave: async () => [1, 3],
    saveWorkflowDefinition: context => {
      saved = structuredClone(lib.getWorkflowToSave(context))
      return new Promise(resolve => { resolveSave = resolve })
    },
  }, {
    notifyWorkflowSaved: () => { notifications++ },
    navigateToSavedWorkflow: () => { editorNavigations++ },
  })
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: edited })
  actor.send({ type: 'SAVE_BEFORE_LEAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.savingBeforeLeave'))
  assert.equal(saved, undefined)
  assert.ok(lib.hasWorkflowChanges(actor.state.context))
  assert.deepEqual(actor.state.context.baseline, wf())
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: wf([]) })
  actor.send({ type: 'SOURCE_CHANGED', source: '{unfinished' })
  actor.send({ type: 'SAVE_BEFORE_LEAVE' })
  assert.deepEqual(actor.state.context.workflow, edited)
  await tick()
  assert.deepEqual(saved, expected)
  resolveSave(expected)
  await tick()
  assert.ok(actor.state.matches('ready.lifecycle.editing'))
  assert.deepEqual(actor.state.context.baseline, expected)
  assert.equal(lib.hasWorkflowChanges(actor.state.context), false)
  assert.equal(notifications, 1)
  assert.equal(editorNavigations, 0)
  actor.stop()
})

test('default save from an older version refreshes versions and creates latest plus one', async () => {
  const original = { ...wf(), tags: ['HIS'], outputParameters: { result: '${a.output}' } }
  const edited = { ...original, version: 10, description: '新版本内容' }
  const requests = []
  let created, navigated
  lib.axios.defaults.adapter = async config => {
    requests.push(config)
    if (config.url.endsWith('/versions')) return { data: { code: 200, data: [4, 1, 2] }, status: 200, statusText: 'OK', headers: {}, config }
    if (config.method === 'post') created = JSON.parse(config.data)
    return { data: { code: 200, data: created }, status: 200, statusText: 'OK', headers: {}, config }
  }
  const actor = await actorFor(original, {
    loadVersionsForSave: context => lib.listWorkflowVersions(context.workflow.route),
    saveWorkflowDefinition: context => lib.saveWorkflowDefinition(lib.getWorkflowToSave(context), context.baseline),
  }, { navigateToSavedWorkflow: context => { navigated = context.workflow.version } })
  actor.send({ type: 'REQUEST_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.editing'))
  assert.deepEqual(requests, [])
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: edited })
  actor.send({ type: 'REQUEST_SAVE' })
  await tick()
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  assert.equal(actor.state.context.saveVersion, 5)
  assert.deepEqual(lib.getWorkflowToSave(actor.state.context), { ...edited, version: 5 })
  assert.deepEqual(actor.state.context.workflow, edited)
  assert.deepEqual(actor.state.context.baseline, original)
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.deepEqual(created, { ...edited, version: 5 })
  assert.equal(requests.filter(request => request.method === 'post').length, 1)
  assert.equal(requests.some(request => request.method === 'put'), false)
  assert.equal(actor.state.context.workflowVersion, 5)
  assert.equal(actor.state.context.saveVersion, null)
  assert.deepEqual(actor.state.context.versions, [1, 2, 4, 5])
  assert.equal(lib.hasWorkflowChanges(actor.state.context), false)
  assert.equal(navigated, 5)
  actor.stop()
})

test('saving as the current version after canceling a new version updates the loaded version', async () => {
  const requests = []
  let saved, lookups = 0
  lib.axios.defaults.adapter = async config => {
    requests.push(config)
    if (config.method === 'put') saved = JSON.parse(config.data)
    return { data: { code: 200, data: saved }, status: 200, statusText: 'OK', headers: {}, config }
  }
  const actor = await actorFor(wf(), {
    loadVersionsForSave: async () => { lookups++; return [1, 2] },
    saveWorkflowDefinition: context => lib.saveWorkflowDefinition(lib.getWorkflowToSave(context), context.baseline),
  })
  const edited = { ...wf(), version: 9, description: '未保存的修改' }
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(edited) })
  const source = actor.state.context.source
  actor.send({ type: 'REQUEST_SAVE' })
  await tick()
  assert.equal(actor.state.context.saveVersion, 3)
  actor.send({ type: 'CANCEL_DIALOG' })
  assert.equal(actor.state.context.saveVersion, null)
  assert.equal(actor.state.context.source, source)
  assert.deepEqual(actor.state.context.workflow, edited)
  assert.deepEqual(actor.state.context.baseline, wf())
  actor.send({ type: 'REQUEST_SAVE', saveCurrentVersion: true })
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.equal(lookups, 1)
  assert.deepEqual(requests.map(request => request.method), ['put', 'get'])
  assert.deepEqual(requests[0].params, { route: 'review', version: 1 })
  assert.deepEqual(saved, { ...edited, version: 1 })
  actor.stop()
})

test('new-version failures keep the reviewed version for retry and preserve the original baseline', async () => {
  let attempts = 0
  const targets = []
  const actor = await actorFor(wf(), {
    loadVersionsForSave: async () => [1, 2],
    saveWorkflowDefinition: async context => {
      const target = structuredClone(lib.getWorkflowToSave(context))
      targets.push(target)
      if (++attempts === 1) throw new Error('创建新版本失败')
      return target
    },
  })
  const edited = { ...wf(), description: '新版本草稿' }
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: edited })
  actor.send({ type: 'REQUEST_SAVE' })
  await tick()
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  assert.equal(actor.state.context.saveVersion, 3)
  assert.deepEqual(actor.state.context.baseline, wf())
  assert.deepEqual(actor.state.context.workflow, edited)
  assert.match(actor.state.context.serverProblems[0].message, /创建新版本失败/)
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.deepEqual(targets, [{ ...edited, version: 3 }, { ...edited, version: 3 }])
  assert.equal(actor.state.context.baseline.version, 3)
  actor.stop()
})

test('failed version lookup cannot fall back to overwriting the original version', async () => {
  let saves = 0
  const actor = await actorFor(wf(), {
    loadVersionsForSave: async () => { throw new Error('加载工作流版本失败') },
    saveWorkflowDefinition: async context => { saves++; return lib.getWorkflowToSave(context) },
  })
  const edited = { ...wf(), description: '保留草稿' }
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: edited })
  actor.send({ type: 'REQUEST_SAVE' })
  await tick()
  actor.send({ type: 'CONFIRM_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.editing'))
  assert.equal(actor.state.context.saveVersion, null)
  assert.deepEqual(actor.state.context.baseline, wf())
  assert.deepEqual(actor.state.context.workflow, edited)
  assert.equal(saves, 0)
  assert.match(actor.state.context.error, /加载工作流版本失败/)
  actor.stop()
})

test('default save respects validation and uses the initial version for unsaved workflows', async () => {
  let lookups = 0
  const actor = await actorFor(wf(), { loadVersionsForSave: async () => { lookups++; return [1] } })
  actor.send({ type: 'SOURCE_CHANGED', source: '{invalid' })
  actor.send({ type: 'REQUEST_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.editing'))
  assert.equal(actor.state.context.activeTab, 'code')
  actor.send({ type: 'INITIALIZE', workflowRoute: 'review', workflowVersion: 1, isNewWorkflow: true })
  await tick()
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: { ...wf(), description: '新工作流' } })
  actor.send({ type: 'REQUEST_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  assert.equal(lib.getWorkflowToSave(actor.state.context).version, 1)
  assert.equal(lookups, 0)
  actor.stop()
})

test('failed save before leaving preserves the draft and can retry without navigating', async () => {
  let attempts = 0
  const edited = { ...wf(), description: '保留修改' }
  const actor = await actorFor(wf(), {
    saveWorkflowDefinition: async context => {
      if (++attempts === 1) throw new Error('连接失败')
      return structuredClone(lib.getWorkflowToSave(context))
    },
  })
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: edited })
  actor.send({ type: 'SAVE_BEFORE_LEAVE' })
  await tick()
  assert.ok(actor.state.matches('ready.lifecycle.editing'))
  assert.deepEqual(actor.state.context.workflow, edited)
  assert.deepEqual(actor.state.context.baseline, wf())
  assert.ok(lib.hasWorkflowChanges(actor.state.context))
  assert.equal(actor.state.context.serverProblems[0].message, '连接失败')
  actor.send({ type: 'SAVE_BEFORE_LEAVE' })
  assert.equal(actor.state.context.serverProblems.length, 0)
  await tick()
  assert.equal(attempts, 2)
  assert.equal(lib.hasWorkflowChanges(actor.state.context), false)
  actor.stop()
})

test('invalid workflow source and task drafts cannot be saved before leaving', async () => {
  for (const draftKind of ['source', 'task']) {
    let saves = 0
    const actor = await actorFor(wf(), { saveWorkflowDefinition: async context => { saves++; return context.workflow } })
    if (draftKind === 'source') actor.send({ type: 'SOURCE_CHANGED', source: '{unfinished' })
    else {
      const path = '/tasks/0/inputParameters/body'
      actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: { path, taskPath: '/tasks/0', taskReferenceName: 'a', value: '{unfinished', message: 'JSON 尚未完成' } })
    }
    actor.send({ type: 'SAVE_BEFORE_LEAVE' })
    assert.ok(actor.state.matches('ready.lifecycle.editing'))
    assert.ok(lib.hasWorkflowChanges(actor.state.context))
    assert.equal(saves, 0)
    assert.ok(actor.state.context.validationActor.getSnapshot().matches('expanded'))
    actor.stop()
  }
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
  actor.send({ type: 'INITIALIZE', workflowRoute: 'another', isNewWorkflow: true })
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
  await tick()
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
  assert.equal(target.route, 'review')
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

test('saved route edits through forms and JSON create a new workflow and replace the saved baseline', async () => {
  for (const type of ['WORKFLOW_CHANGED', 'SOURCE_CHANGED']) {
    const original = { ...wf(), version: 3 }
    const changed = { ...original, route: 'another-route' }
    let saved
    const actor = await actorFor(original, {
      loadWorkflowDefinition: async () => ({ workflow: original, versions: [1, 2, 3] }),
      saveWorkflowDefinition: async ctx => { saved = structuredClone(ctx.workflow); return saved },
    })
    actor.send(type === 'SOURCE_CHANGED' ? { type, source: JSON.stringify(changed) } : { type, workflow: changed })
    assert.deepEqual(actor.state.context.problems, [])
    assert.equal(actor.state.context.baseline.route, 'review')
    actor.send({ type: 'REQUEST_SAVE' })
    assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
    actor.send({ type: 'CONFIRM_SAVE' })
    await tick()
    assert.deepEqual(saved, changed)
    assert.equal(actor.state.context.baseline.route, 'another-route')
    assert.equal(actor.state.context.baseline.name, original.name)
    assert.equal(actor.state.context.workflowRoute, 'another-route')
    assert.equal(actor.state.context.workflowVersion, 3)
    assert.deepEqual(actor.state.context.versions, [3])
    assert.equal(lib.hasWorkflowChanges(actor.state.context), false)
    assert.equal(original.route, 'review')
    actor.stop()
  }
})

test('new drafts and saved definitions can keep changing routes and create versions', async () => {
  const actor = lib.interpret(lib.workflowDefinitionMachine.withConfig({
    services: {
      loadWorkflowDefinition: async () => ({ workflow: wf(), versions: [1] }),
      loadVersionsForSave: async () => [1],
      saveWorkflowDefinition: async ctx => lib.getWorkflowToSave(ctx),
    },
    actions: { notifyWorkflowSaved: () => {}, navigateToSavedWorkflow: () => {} },
  })).start()
  actor.send({ type: 'INITIALIZE', workflowRoute: 'review', workflowVersion: 1, isNewWorkflow: true })
  await tick()
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: { ...wf(), route: 'chosen-route' } })
  actor.send({ type: 'REQUEST_SAVE' })
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.equal(actor.state.context.isNewWorkflow, false)
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: { ...actor.state.context.workflow, description: '第二个版本' } })
  actor.send({ type: 'REQUEST_SAVE' })
  await tick()
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.deepEqual(actor.state.context.versions, [1, 2])
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: { ...actor.state.context.workflow, route: 'another-route' } })
  assert.deepEqual(actor.state.context.problems, [])
  actor.stop()
})

test('route creation failures preserve the original baseline, versions and edited source', async () => {
  const actor = await actorFor(wf(), { saveWorkflowDefinition: async () => { throw new Error('工作流定义已存在') } })
  const changed = { ...wf(), route: 'existing-route' }
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(changed) })
  actor.send({ type: 'REQUEST_SAVE' })
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  assert.deepEqual(actor.state.context.baseline, wf())
  assert.deepEqual(JSON.parse(actor.state.context.source), changed)
  assert.equal(actor.state.context.workflowRoute, 'review')
  assert.deepEqual(actor.state.context.versions, [1])
  assert.match(actor.state.context.serverProblems[0].message, /已存在/)
  actor.stop()
})

test('deleting an edited route still confirms the saved route', async () => {
  const actor = await actorFor()
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: { ...wf(), route: 'edited-route', version: 9 } })
  actor.send({ type: 'REQUEST_DELETE' })
  assert.ok(actor.state.matches('ready.lifecycle.confirmDelete'))
  assert.equal(actor.state.context.baseline.route, 'review')
  assert.equal(actor.state.context.baseline.version, 1)
  actor.send({ type: 'UPDATE_DELETE_CONFIRMATION', value: 'edited-route' })
  actor.send({ type: 'CONFIRM_DELETE' })
  assert.ok(actor.state.matches('ready.lifecycle.confirmDelete'))
  actor.stop()
})

test('route syntax is validated separately from unrestricted business and task names', () => {
  assert.deepEqual(lib.validateWorkflow({ ...wf(), name: '检验结果 / 同名工作流', tasks: [{ ...task('a'), name: '中文任务名称' }] }).problems, [])
  for (const route of ['中文路由', '/leading-slash', 'route with spaces', 'a'.repeat(256)]) {
    assert.ok(lib.validateWorkflow({ ...wf(), route }).problems.some(p => p.path === '/route'))
  }
  assert.ok(lib.validateWorkflow({ ...wf(), name: '' }).problems.some(p => p.path === '/name'))
})

test('definition and start APIs send route identities; updates use the saved target and never fall back to create', async () => {
  const requests = []
  let failUpdate = false
  lib.axios.defaults.adapter = async config => {
    requests.push(config)
    if (failUpdate && config.method === 'put') throw new Error('保存失败')
    return { data: { code: 200, data: config.url.endsWith('/start-http') ? 'instance-1' : wf() }, status: 200, statusText: 'OK', headers: {}, config }
  }
  await lib.getWorkflowDefinition('review', 1)
  assert.deepEqual(requests.at(-1).params, { route: 'review', version: 1 })
  const renamed = { ...wf(), name: '改名后的工作流' }
  await lib.saveWorkflowDefinition(renamed, wf())
  const update = requests.find(r => r.method === 'put')
  assert.deepEqual(update.params, { route: 'review', version: 1 })
  assert.equal(JSON.parse(update.data).name, renamed.name)
  failUpdate = true
  await assert.rejects(lib.saveWorkflowDefinition(renamed, wf()), /保存失败/)
  assert.equal(requests.filter(r => r.method === 'post').length, 0)
  failUpdate = false
  await lib.saveWorkflowDefinition({ ...wf(), version: 2 }, wf())
  assert.equal(JSON.parse(requests.find(r => r.method === 'post').data).version, 2)
  const original = wf()
  await lib.saveWorkflowDefinition({ ...original, route: 'another' }, original)
  const creation = requests.filter(r => r.method === 'post').at(-1)
  assert.equal(creation.url, '/api/workflow/metadata/create')
  assert.deepEqual(JSON.parse(creation.data), { ...original, route: 'another' })
  assert.equal(requests.filter(r => r.method === 'put').length, 2)
  assert.equal(original.route, 'review')
  for (const [contentType, body] of [
    ['application/xml', '<root><patientCode id="56">患者编号</patientCode></root>\n'],
    ['application/json', '  {"id":1234567890123456789}\n'],
    ['text/plain', '  原始文本\n'],
  ]) {
    assert.equal(await lib.startWorkflow('review', 1, body, contentType), 'instance-1')
    assert.equal(requests.at(-1).url, '/api/workflow/execution/start-http')
    assert.deepEqual(requests.at(-1).params, { route: 'review', version: 1 })
    assert.equal(requests.at(-1).headers.get('Content-Type'), `${contentType};charset=UTF-8`)
    assert.equal(requests.at(-1).data, body)
  }
  await lib.deleteWorkflowDefinition('review', 1)
  assert.deepEqual(requests.at(-1).params, { route: 'review', version: 1 })
})

test('imports and templates preserve route, business names and task names independently', () => {
  assert.deepEqual(lib.normalizeWorkflowDraft(wf()).tasks, wf().tasks)
  assert.equal(lib.normalizeWorkflowDraft({ ...wf(), route: 'review-copy', name: '同名工作流' }).route, 'review-copy')
  assert.throws(() => lib.normalizeWorkflowDraft({ name: 'legacy-route', displayName: '旧名称', version: 1, tasks: [] }), /route/)
  for (const template of lib.workflowTemplates) {
    const definition = template.create('chosen-route')
    assert.equal(definition.route, 'chosen-route')
    assert.ok(definition.name)
    assert.equal(Object.hasOwn(definition, 'displayName'), false)
    assert.ok(definition.tasks.some(task => task.name))
  }
})

test('execution body editor restores raw XML, JSON and text with their formats', () => {
  const rawXml = '<root>\n  <patientCode id="56">患者编号</patientCode>\n</root>\n'
  for (const [contentType, format, rawBody] of [
    ['application/soap+xml;charset=UTF-8', 'xml', rawXml],
    ['text/xml', 'xml', rawXml],
    ['application/json', 'json', '  {"id":1234567890123456789}\n'],
    ['text/plain', 'text', '  原始文本\n'],
    ['application/json', 'json', 'null'],
    ['application/json', 'json', ''],
  ]) {
    const restored = lib.requestBodyFromWorkflowInput({
      body: { parsed: true }, rawBody, query: {}, headers: { 'content-type': contentType }, method: 'POST', path: '/test',
    })
    assert.equal(restored.mode, 'raw')
    assert.equal(restored.text, rawBody)
    assert.equal(restored.format, format)
  }
  for (const [input, text, format] of [
    [{ body: { BusinessMethod: 'apply' }, rawBody: '{"BusinessMethod":"apply"}' }, '{"BusinessMethod":"apply"}', 'json'],
    [{ patientCode: '001' }, '{\n  "patientCode": "001"\n}', 'json'],
    [{ body: [1, 2], query: {}, headers: {}, method: 'POST', path: '/test' }, '[\n  1,\n  2\n]', 'json'],
    ['<root><patientCode>001</patientCode></root>', '<root><patientCode>001</patientCode></root>', 'xml'],
    ['{"patientCode":"001"}', '{"patientCode":"001"}', 'json'],
    ['原始文本', '原始文本', 'text'],
  ]) {
    const restored = lib.requestBodyFromWorkflowInput(input)
    assert.equal(restored.text, text)
    assert.equal(restored.format, format)
  }
  const oldXml = lib.requestBodyFromWorkflowInput({
    body: { root: { patientCode: { '@id': '56', '#text': '患者 & 编号' }, item: ['a', 'b'] } },
    query: {}, headers: { 'content-type': 'application/xml' }, method: 'POST', path: '/test',
  })
  assert.equal(oldXml.format, 'xml')
  assert.equal(oldXml.text, '<root><patientCode id="56">患者 &amp; 编号</patientCode><item>a</item><item>b</item></root>')
})

test('body editor restores form fields, empty requests and saved file bytes for resubmission', async () => {
  const gatewayInput = (body, rawBody, contentType, files = {}) => ({ body, rawBody, files, query: {}, headers: { 'content-type': contentType }, method: 'POST', path: '/test' })
  const form = lib.requestBodyFromWorkflowInput(gatewayInput({ tag: ['a', 'b'], name: '张三' }, 'tag=a&tag=b&name=%E5%BC%A0%E4%B8%89', 'application/x-www-form-urlencoded'))
  assert.equal(form.mode, 'urlencoded')
  assert.deepEqual(form.fields.filter(field => field.name).map(field => [field.name, field.value]), [['tag', 'a'], ['tag', 'b'], ['name', '张三']])
  assert.deepEqual(lib.toHttpRequestBody(form), { body: 'tag=a&tag=b&name=%E5%BC%A0%E4%B8%89', contentType: 'application/x-www-form-urlencoded' })
  const multipart = lib.requestBodyFromWorkflowInput(gatewayInput({ name: '张三' }, null, 'multipart/form-data;boundary=test', {
    file: [{ fileName: 'raw.bin', contentType: 'application/octet-stream', contentBase64: 'AAH/', size: 3 }],
  }))
  assert.equal(multipart.mode, 'form-data')
  const payload = lib.toHttpRequestBody(multipart)
  assert.equal(payload.contentType, undefined)
  assert.equal(payload.body.get('name'), '张三')
  assert.equal(payload.body.get('file').name, 'raw.bin')
  assert.deepEqual([...new Uint8Array(await payload.body.get('file').arrayBuffer())], [0, 1, 255])
  const empty = lib.requestBodyFromWorkflowInput(gatewayInput(null, '', ''))
  assert.equal(empty.mode, 'none')
  assert.deepEqual(lib.toHttpRequestBody(empty), { body: '' })
  form.fields.push({ ...lib.emptyBodyField(), enabled: false, name: 'ignored', value: 'x' })
  assert.equal(new URLSearchParams(lib.toHttpRequestBody(form).body).has('ignored'), false)
})


test('export uses route/version for the filename and preserves business and task names', async () => {
  const originalDocument = globalThis.document
  const createObjectURL = URL.createObjectURL
  const revokeObjectURL = URL.revokeObjectURL
  const workflow = { ...wf([{ ...task('task_ref'), name: '任务名称不改动' }]), tags: ['HIS', '门诊'] }
  let exportedBlob
  let clicked = false
  let revoked
  const anchor = { click: () => { clicked = true } }
  try {
    globalThis.document = { createElement: tag => { assert.equal(tag, 'a'); return anchor } }
    URL.createObjectURL = blob => { exportedBlob = blob; return 'blob:export-test' }
    URL.revokeObjectURL = url => { revoked = url }
    lib.downloadWorkflowDefinition(workflow)
    assert.equal(clicked, true)
    assert.equal(anchor.download, 'review_v1.json')
    assert.equal(anchor.href, revoked)
    assert.equal(exportedBlob.type, 'application/json;charset=utf-8')
    const exported = JSON.parse(await exportedBlob.text())
    assert.deepEqual(exported, workflow)
    assert.equal(exported.displayName, undefined)
    assert.equal(lib.normalizeWorkflowDraft(exported).route, 'review')
    assert.deepEqual(lib.normalizeWorkflowDraft(exported).tags, ['HIS', '门诊'])
  } finally {
    globalThis.document = originalDocument
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = revokeObjectURL
  }
})

test('plain tags survive imports and normalize empty names and duplicates', () => {
  const imported = lib.normalizeWorkflowDraft({ ...wf(), tags: [' HIS ', '', 'HIS', '门诊'] })
  assert.deepEqual(imported.tags, ['HIS', '门诊'])
  assert.deepEqual(lib.normalizeWorkflowDraft(wf()).tags, [])
  assert.deepEqual(lib.normalizeWorkflowDraft({ ...wf(), tags: null }).tags, [])
  assert.throws(() => lib.normalizeWorkflowDraft({ ...wf(), tags: ['HIS', 1] }), /字符串数组/)
  assert.ok(lib.validateWorkflow({ ...wf(), tags: 'HIS' }).problems.some(problem => problem.path === '/tags'))
  assert.ok(lib.validateWorkflow(imported).workflow)
})

test('tag options cover all definitions and keyword plus tag filtering compose', () => {
  const definitions = [
    { ...wf(), route: 'a', name: '报告查询', tags: ['HIS', '门诊'] },
    { ...wf(), route: 'b', name: '检验查询', tags: ['LIS', '门诊', ' LIS '] },
    { ...wf(), route: 'c', name: '无标签查询' },
  ]
  assert.deepEqual(new Set(lib.collectWorkflowTags(definitions)), new Set(['HIS', 'LIS', '门诊']))
  assert.equal(lib.filterWorkflowDefinitions(definitions, '', []).length, 3)
  assert.deepEqual(lib.filterWorkflowDefinitions(definitions, '', ['HIS']).map(def => def.route), ['a'])
  assert.deepEqual(lib.filterWorkflowDefinitions(definitions, '', ['HIS', 'LIS']).map(def => def.route), ['a', 'b'])
  assert.deepEqual(lib.filterWorkflowDefinitions(definitions, '检验', ['门诊']).map(def => def.route), ['b'])
  assert.equal(lib.filterWorkflowDefinitions(definitions, '报告', ['LIS']).length, 0)
  assert.equal(lib.filterWorkflowDefinitions(definitions, '', ['missing']).length, 0)
})

test('tags follow code edits, reset, and saves to another version', async () => {
  const definition = { ...wf(), tags: ['HIS'] }
  const actor = await actorFor(definition, {
    saveWorkflowDefinition: async context => structuredClone(lib.getWorkflowToSave(context)),
  })
  const updated = { ...definition, tags: ['LIS', '门诊'], version: 2 }
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(updated, null, 2) })
  assert.deepEqual(actor.state.context.workflow.tags, ['LIS', '门诊'])
  actor.send({ type: 'REQUEST_RESET' })
  actor.send({ type: 'CONFIRM_RESET' })
  assert.deepEqual(actor.state.context.workflow.tags, ['HIS'])
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: updated })
  actor.send({ type: 'REQUEST_SAVE' })
  await tick()
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.deepEqual(actor.state.context.baseline.tags, ['LIS', '门诊'])
  actor.stop()
})

test('tag API sends just tags to the selected route and version', async () => {
  let request
  lib.axios.defaults.adapter = async config => {
    request = config
    return { data: { code: 200, data: { ...wf(), tags: ['HIS'] } }, status: 200, statusText: 'OK', headers: {}, config }
  }
  assert.deepEqual((await lib.updateWorkflowTags('HIS/query', 2, [' HIS ', 'HIS'])).tags, ['HIS'])
  assert.equal(request.url, '/api/workflow/metadata/tags')
  assert.equal(request.method, 'put')
  assert.deepEqual(request.params, { route: 'HIS/query', version: 2 })
  assert.deepEqual(JSON.parse(request.data), ['HIS'])
})


test('creating an edited route reports duplicate keys without updating the destination', async () => {
  const requests = []
  lib.axios.defaults.adapter = async config => {
    requests.push(config)
    return { data: { code: 409, message: '工作流定义已存在' }, status: 200, statusText: 'OK', headers: {}, config }
  }
  await assert.rejects(lib.saveWorkflowDefinition({ ...wf(), route: 'existing-route' }, wf()), /已存在/)
  assert.equal(requests.length, 1)
  assert.equal(requests[0].method, 'post')
  assert.equal(requests[0].url, '/api/workflow/metadata/create')
})

test('workflow timeouts default to termination and preserve explicit alert-only imports', () => {
  assert.equal(lib.normalizeWorkflowDraft(wf()).timeoutPolicy, 'TIME_OUT_WF')
  assert.equal(lib.normalizeWorkflowDraft({ ...wf(), timeoutPolicy: null }).timeoutPolicy, 'TIME_OUT_WF')
  assert.equal(lib.normalizeWorkflowDraft({ ...wf(), timeoutPolicy: 'ALERT_ONLY', timeoutSeconds: 180 }).timeoutPolicy, 'ALERT_ONLY')
  for (const template of lib.workflowTemplates) {
    const definition = template.create('timeout-test')
    assert.equal(definition.timeoutSeconds, 0)
    assert.equal(definition.timeoutPolicy, 'TIME_OUT_WF')
  }
})

test('task policies accept defaults and explicit values, and validate nested fields', () => {
  for (const field of ['retryCount', 'retryDelaySeconds', 'startDelay']) {
    for (const value of [undefined, null, 0, 1, 30]) {
      assert.equal(lib.validateWorkflow(wf([{ ...task('a'), [field]: value }])).problems.length, 0)
    }
    for (const value of [-1, 0.5, Infinity, NaN, '1', true, []]) {
      const definition = wf([doWhile({ loopCondition: 'false', loopOver: [{ ...task('a'), [field]: value }] })])
      const problem = lib.validateWorkflow(definition).problems.find(problem => problem.path === `/tasks/0/loopOver/0/${field}`)
      assert.ok(problem, `${field} should reject ${JSON.stringify(value)}`)
      assert.equal(problem.taskPath, '/tasks/0/loopOver/0')
      assert.equal(problem.taskReferenceName, 'a')
    }
  }
  for (const field of ['retryCount', 'retryDelaySeconds', 'startDelay']) {
    assert.ok(lib.validateWorkflow(wf([{ ...task('a'), [field]: 2147483648 }])).problems.some(problem => problem.path === `/tasks/0/${field}`))
  }
  for (const [field, values] of [
    ['retryLogic', ['FIXED', 'LINEAR_BACKOFF', 'EXPONENTIAL_BACKOFF']],
  ]) {
    for (const value of [undefined, null, ...values]) {
      assert.equal(lib.validateWorkflow(wf([{ ...task('a'), [field]: value }])).problems.length, 0)
    }
    for (const value of ['', 'UNKNOWN', 0, true, []]) {
      assert.ok(lib.validateWorkflow(wf([{ ...task('a'), [field]: value }])).problems.some(problem => problem.path === `/tasks/0/${field}`))
    }
  }
})

test('task policy forms exclude built-in control flow from failure retries', async () => {
  const actor = await actorFor()
  const render = type => lib.renderToStaticMarkup(lib.createElement(lib.ValidationScope, {
    value: { actor, basePath: '/tasks/0', taskReferenceName: 'a' },
  }, lib.createElement(lib.TaskInspector, {
    task: { ...task('a'), type, startDelay: 1, retryCount: 1 }, variableOptions: [], onChange: () => {},
  })))
  for (const type of ['HTTP', 'JDBC', 'WEBSERVICE', 'SPI', 'INLINE', 'MAPPING', 'CUSTOM',
    'DECISION', 'SWITCH', 'FORK', 'FORK_JOIN', 'JOIN', 'EXCLUSIVE_JOIN', 'DO_WHILE']) {
    const html = render(type)
    const canRetry = ['HTTP', 'JDBC', 'WEBSERVICE', 'SPI', 'INLINE', 'MAPPING', 'CUSTOM'].includes(type)
    for (const label of ['失败重试', '重试次数', '重试间隔（秒）', '重试策略']) {
      assert.equal(html.includes(`aria-label="${label}"`), canRetry, `${type}: ${label}`)
    }
    assert.ok(html.includes('aria-label="启动延迟"'), type)
    assert.ok(html.includes('aria-label="启动延迟（秒）"'), type)
    assert.ok(html.includes('aria-label="可选任务"'), type)
    assert.equal(html.includes('任务超时'), false, type)
    assert.equal(html.includes('暂未生效'), false, type)
    assert.equal(html.includes('继承框架配置'), false, type)
  }
  const unregister = lib.registerTaskPlugin({ type: 'SWITCH', label: '自定义分支表单' })
  assert.equal(render('SWITCH').includes('aria-label="失败重试"'), false)
  unregister()
  assert.ok(render('CUSTOM').includes('aria-label="失败重试"'))
  actor.stop()
})

test('task policy switches hide disabled parameters and retain unfinished required drafts', async () => {
  const actor = await actorFor()
  const render = (policies, readOnly = false) => lib.renderToStaticMarkup(lib.createElement(lib.ValidationScope, {
    value: { actor, basePath: '/tasks/0', taskReferenceName: 'a', readOnly },
  }, lib.createElement(lib.TaskInspector, {
    task: { ...task('a'), ...policies }, variableOptions: [], onChange: () => {},
  })))
  for (const policies of [{}, { startDelay: null, retryCount: null, retryDelaySeconds: null, retryLogic: null },
    { startDelay: 0, retryCount: 0, retryDelaySeconds: 3, retryLogic: 'EXPONENTIAL_BACKOFF' }]) {
    const html = render(policies)
    assert.equal((html.match(/role="switch"/g) ?? []).length, 3)
    assert.equal(html.includes('inputMode="numeric"'), false)
    assert.equal(html.includes('role="combobox"'), false)
    assert.equal(html.includes('checked=""'), false)
    assert.equal(html.includes('<small>'), false)
  }
  const policies = { startDelay: 5, retryCount: 2, retryDelaySeconds: 0, retryLogic: 'FIXED' }
  const html = render(policies)
  assert.equal((html.match(/checked=""/g) ?? []).length, 2)
  assert.equal((html.match(/required=""|aria-required="true"/g) ?? []).length, 4)
  assert.ok(html.includes('role="combobox"'))
  assert.ok(html.includes('固定间隔'))
  assert.equal(html.includes('type="number"'), false)
  assert.equal((html.match(/inputMode="numeric"/g) ?? []).length, 3)
  assert.equal((html.match(/role="spinbutton"/g) ?? []).length, 3)
  for (const label of ['启动延迟（秒）', '重试次数', '重试间隔（秒）']) {
    assert.ok(html.includes(`aria-label="增加${label}"`))
    assert.ok(html.includes(`aria-label="减少${label}"`))
  }
  assert.ok(html.indexOf('aria-label="可选任务"') < html.indexOf('aria-label="启动延迟"'))
  assert.ok(html.indexOf('aria-label="启动延迟"') < html.indexOf('aria-label="失败重试"'))
  assert.equal((render({}, true).match(/disabled=""/g) ?? []).length, 3)
  assert.equal((render(policies, true).match(/disabled=""/g) ?? []).length, 13)
  for (const field of ['startDelay', 'retryCount', 'retryDelaySeconds', 'retryLogic']) {
    const path = `/tasks/0/${field}`
    actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: {
      path, taskPath: '/tasks/0', taskReferenceName: 'a', value: '', message: '请完成策略配置',
    } })
    actor.send({ type: 'TASK_SELECTED', task: actor.state.context.workflow.tasks[0] })
    const pending = render({})
    assert.equal((pending.match(/checked=""/g) ?? []).length, 1, field)
    assert.equal((pending.match(/required=""|aria-required="true"/g) ?? []).length, field === 'startDelay' ? 1 : 3, field)
    assert.ok(pending.includes('请完成策略配置'), field)
    actor.send({ type: 'REQUEST_SAVE' })
    assert.ok(actor.state.matches('ready.lifecycle.editing'), field)
    actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: null })
    assert.equal(render({}).includes('required=""'), false, field)
  }
  actor.stop()
})

test('task policies survive imports, source edits, unrelated form edits and save reloads', async () => {
  const policies = { retryCount: 0, retryDelaySeconds: 0, retryLogic: 'EXPONENTIAL_BACKOFF', startDelay: 5 }
  const definition = wf([doWhile({ loopCondition: 'false', loopOver: [{ ...task('a'), ...policies }] })])
  const imported = lib.normalizeWorkflowDraft(JSON.parse(JSON.stringify(definition)))
  assert.deepEqual(imported.tasks, definition.tasks)
  let saved
  const actor = await actorFor(imported, { saveWorkflowDefinition: async context => {
    saved = structuredClone(context.workflow)
    return saved
  } })
  const edited = lib.updateTask(imported, 'a', current => ({ ...current, description: '仅修改描述' }))
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(edited, null, 2) })
  actor.send({ type: 'TASK_SELECTED', task: actor.state.context.workflow.tasks[0].loopOver[0] })
  actor.send({ type: 'TASK_UPDATED', task: { ...actor.state.context.workflow.tasks[0].loopOver[0], optional: true } })
  actor.send({ type: 'REQUEST_SAVE' })
  await tick()
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.ok(saved)
  for (const [key, value] of Object.entries(policies)) assert.equal(saved.tasks[0].loopOver[0][key], value)
  assert.equal(saved.tasks[0].loopOver[0].optional, true)
  assert.equal(saved.tasks[0].loopOver[0].description, '仅修改描述')
  assert.equal(saved.tasks[0].retryCount, undefined)
  assert.equal(saved.tasks[0].startDelay, undefined)
  assert.deepEqual(lib.normalizeWorkflowDraft(JSON.parse(JSON.stringify(saved))).tasks, saved.tasks)
  assert.deepEqual(actor.state.context.baseline.tasks, saved.tasks)
  actor.stop()
})

test('workflow execution policies hide inherited timeouts and retain existing overrides', async () => {
  const actor = await actorFor()
  const queryClient = new lib.QueryClient()
  const render = (timeout, readOnly = false) => lib.renderToStaticMarkup(lib.createElement(lib.QueryClientProvider, {
    client: queryClient,
  }, lib.createElement(lib.ValidationScope, {
    value: { actor, basePath: '', readOnly },
  }, lib.createElement(lib.WorkflowInspector, {
    workflow: { ...wf(), ...timeout }, variableOptions: [], onChange: () => {},
  }))))
  for (const timeout of [{}, { timeoutSeconds: 0, timeoutPolicy: 'TIME_OUT_WF' }]) {
    const html = render(timeout)
    assert.ok(html.includes('执行策略'))
    assert.ok(html.includes('aria-label="工作流超时"'))
    assert.equal(html.includes('超时设置'), false)
    assert.equal(html.includes('checked=""'), false)
    assert.equal(html.includes('aria-label="超时时间（秒）"'), false)
    assert.equal(html.includes('aria-label="超时策略"'), false)
    assert.equal(html.includes('继承系统超时时间，超时后终止工作流'), false)
  }
  for (const timeout of [
    { timeoutSeconds: 60, timeoutPolicy: 'TIME_OUT_WF' },
    { timeoutSeconds: 120, timeoutPolicy: 'ALERT_ONLY' },
    { timeoutSeconds: 0, timeoutPolicy: 'ALERT_ONLY' },
  ]) {
    const html = render(timeout)
    assert.ok(html.includes('checked=""'))
    assert.ok(html.includes('aria-label="超时时间（秒）"'))
    assert.ok(html.includes('aria-label="超时策略"'))
    assert.ok(html.includes(`value="${timeout.timeoutSeconds}"`))
    assert.ok(html.includes('role="combobox"'))
    assert.ok(html.includes(timeout.timeoutPolicy === 'TIME_OUT_WF' ? '超时终止' : '仅告警'))
    assert.ok(html.includes('inputMode="numeric"'))
    assert.equal(html.includes('配置整个工作流的超时时间及处理策略'), false)
    assert.equal(html.includes('0 表示继承系统配置'), false)
    assert.equal(html.includes('同步调用继续等待'), false)
    assert.equal((html.match(/required=""|aria-required="true"/g) ?? []).length, 5) // name, route, description, time, policy
    assert.ok(html.includes('aria-label="增加超时时间（秒）"'))
    assert.ok(html.includes('aria-label="减少超时时间（秒）"'))
    assert.equal((render(timeout, true).match(/disabled=""/g) ?? []).length, 5) // switch, time, two step buttons, policy
  }
  for (const path of ['/timeoutSeconds', '/timeoutPolicy']) {
    actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: { path, value: '', taskReferenceName: '', message: '请完成超时配置' } })
  }
  const pending = render({ timeoutSeconds: 0, timeoutPolicy: 'TIME_OUT_WF' })
  assert.ok(pending.includes('checked=""'))
  assert.ok(pending.includes('请完成超时配置'))
  assert.ok(pending.includes('class="placeholder">请选择超时策略'))
  actor.stop()
  queryClient.clear()
})

test('unfinished workflow timeouts survive tab changes and focus workflow fields before save', async () => {
  let saved
  const actor = await actorFor(wf(), { saveWorkflowDefinition: async context => {
    saved = structuredClone(context.workflow)
    return saved
  } })
  const path = '/timeoutSeconds'
  const draft = { path, value: '', taskReferenceName: '', message: '请填写超时时间（秒）' }
  actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft })
  actor.send({ type: 'TASK_SELECTED', task: actor.state.context.workflow.tasks[0] })
  actor.send({ type: 'TASK_UPDATED', task: { ...task('a'), optional: true } })
  assert.equal(actor.state.context.workflow.tasks[0].optional, undefined)
  actor.send({ type: 'CHANGE_TAB', tab: 'code' })
  actor.send({ type: 'SOURCE_CHANGED', source: JSON.stringify(wf([])) })
  assert.equal(actor.state.context.workflow.tasks.length, 1)
  assert.deepEqual(actor.state.context.fieldDrafts[path], draft)
  actor.send({ type: 'REQUEST_SAVE' })
  assert.ok(actor.state.matches('ready.lifecycle.editing'))
  assert.equal(actor.state.context.activeTab, 'workflow')
  assert.equal(actor.state.context.problemFocus.problem.path, path)
  assert.equal(actor.state.context.problemFocus.problem.taskPath, undefined)
  assert.equal(actor.state.context.problemFocus.problem.taskReferenceName, undefined)
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: { ...actor.state.context.workflow, timeoutSeconds: 90, timeoutPolicy: 'ALERT_ONLY' } })
  actor.send({ type: 'FIELD_DRAFT_CHANGED', path, draft: null })
  actor.send({ type: 'REQUEST_SAVE' })
  await tick()
  assert.ok(actor.state.matches('ready.lifecycle.confirmSave'))
  actor.send({ type: 'CONFIRM_SAVE' })
  await tick()
  assert.equal(saved.timeoutSeconds, 90)
  assert.equal(saved.timeoutPolicy, 'ALERT_ONLY')
  assert.deepEqual(saved.tasks, wf().tasks)
  actor.stop()
})

test('workflow timeout validation rejects fractions, negatives and unsupported policies', () => {
  for (const timeoutSeconds of [-1, 1.5, Infinity, NaN, '60']) {
    const result = lib.validateWorkflow({ ...wf(), timeoutSeconds })
    assert.ok(result.problems.some(problem => problem.path === '/timeoutSeconds'))
  }
  assert.ok(lib.validateWorkflow({ ...wf(), timeoutPolicy: 'RETRY' }).problems.some(problem => problem.path === '/timeoutPolicy'))
  for (const timeoutSeconds of [0, 60, 120]) {
    for (const timeoutPolicy of [undefined, null, 'TIME_OUT_WF', 'ALERT_ONLY']) {
      assert.equal(lib.validateWorkflow({ ...wf(), timeoutSeconds, timeoutPolicy }).problems.length, 0)
    }
  }
})
