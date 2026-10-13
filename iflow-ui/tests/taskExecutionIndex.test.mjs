import test from 'node:test'
import assert from 'node:assert/strict'
import { indexTaskExecutions } from '../src/pages/executions/taskExecutionIndex.ts'

const definition = (ref, type = 'INLINE', extra = {}) => ({ taskReferenceName: ref, type, ...extra })
const execution = (ref, extra = {}) => ({ taskId: ref, referenceTaskName: ref, status: 'COMPLETED', ...extra })
const loop = (tasks) => [definition('pacs_loop_ref', 'DO_WHILE', { loopOver: tasks })]

test('PACS loop executions resolve to the three definition nodes without iteration fields', () => {
  const refs = ['build_pacs_soap_ref', 'sync_pacs_ws_ref', 'parse_pacs_resp_ref']
  const children = refs.map((ref) => execution(`${ref}__1`))
  const parent = execution('pacs_loop_ref', { taskType: 'DO_WHILE', outputData: { iteration: 1 } })
  const index = indexTaskExecutions(loop(refs.map((ref) => definition(ref))), [parent, ...children])

  for (const [position, ref] of refs.entries()) {
    assert.equal(index.latestTaskByRef.get(ref), children[position])
    assert.equal(index.latestTaskByRef.get(ref).status, 'COMPLETED')
    assert.equal(index.definitionRefByTaskId.get(children[position].taskId), ref)
    assert.equal(index.iterationByTaskId.get(children[position].taskId), 1)
    assert.equal(index.taskDefinitionByRef.get(ref).taskReferenceName, ref)
  }
  assert.equal(index.latestTaskByRef.get('pacs_loop_ref'), parent)
  assert.equal(index.iterationByTaskId.has(parent.taskId), false)
})

test('the latest numeric iteration wins over retries and timestamps from an earlier iteration', () => {
  const oldRetry = execution('page_ref__2', { taskId: 'old-retry', retryCount: 3, seq: 50, updateTime: 999 })
  const lastIteration = execution('page_ref__10', { taskId: 'latest', retryCount: 0, seq: 10, updateTime: 100 })
  const failed = execution('page_ref__10', { taskId: 'failed', retryCount: 0, seq: 9, status: 'FAILED' })
  const retry = execution('page_ref__10', { taskId: 'retry', retryCount: 1, seq: 11, inputData: { page: 10 }, outputData: { ok: true } })
  const index = indexTaskExecutions(loop([definition('page_ref')]), [retry, oldRetry, lastIteration, failed])

  assert.equal(index.latestTaskByRef.get('page_ref'), retry)
  assert.deepEqual(index.taskAttemptsByRef.get('page_ref'), [retry, lastIteration, failed, oldRetry])
  assert.deepEqual(index.taskAttemptsByRef.get('page_ref').map((task) => task.status), ['COMPLETED', 'COMPLETED', 'FAILED', 'COMPLETED'])
  assert.deepEqual(retry.inputData, { page: 10 })
  assert.deepEqual(retry.outputData, { ok: true })
})

test('loop tasks in switch cases, default cases and fork branches resolve independently', () => {
  const tasks = loop([
    definition('switch_ref', 'SWITCH', {
      decisionCases: { yes: [definition('yes_ref')] }, defaultCase: [definition('default_ref')],
    }),
    definition('fork_ref', 'FORK_JOIN', { forkTasks: [[definition('left_ref')], [definition('right_ref')]] }),
  ])
  const refs = ['switch_ref', 'yes_ref', 'default_ref', 'fork_ref', 'left_ref', 'right_ref']
  const index = indexTaskExecutions(tasks, refs.map((ref) => execution(`${ref}__3`)))
  for (const ref of refs) assert.equal(index.latestTaskByRef.get(ref).referenceTaskName, `${ref}__3`)
})

test('loop definitions inside outer branches are indexed and separate loops stay separate', () => {
  const tasks = [definition('outer_fork', 'FORK_JOIN', { forkTasks: [
    loop([definition('pacs_ref')]),
    [definition('lis_loop_ref', 'DO_WHILE', { loopOver: [definition('lis_ref')] })],
  ] })]
  const index = indexTaskExecutions(tasks, [execution('pacs_ref__1'), execution('lis_ref__4')])
  assert.equal(index.taskAttemptsByRef.get('pacs_ref').length, 1)
  assert.equal(index.taskAttemptsByRef.get('lis_ref').length, 1)
  assert.equal(index.iterationByTaskId.get('pacs_ref__1'), 1)
  assert.equal(index.iterationByTaskId.get('lis_ref__4'), 4)
})

test('literal suffixes and unrelated runtime references are not mistaken for loop iterations', () => {
  const tasks = [definition('normal_ref'), definition('literal_ref__1'), ...loop([
    definition('literal_ref'), definition('has__delimiter'),
  ])]
  const rows = [execution('normal_ref__1'), execution('literal_ref__1'), execution('has__delimiter__2'), execution('unknown__3')]
  const index = indexTaskExecutions(tasks, rows)
  assert.equal(index.latestTaskByRef.has('normal_ref'), false)
  assert.equal(index.latestTaskByRef.has('literal_ref'), false)
  assert.equal(index.latestTaskByRef.get('literal_ref__1'), rows[1])
  assert.equal(index.iterationByTaskId.has(rows[1].taskId), false)
  assert.equal(index.latestTaskByRef.get('has__delimiter'), rows[2])
  assert.equal(index.iterationByTaskId.get(rows[2].taskId), 2)
  assert.equal(index.latestTaskByRef.get('unknown__3'), rows[3])
})

test('ordinary tasks retain retry selection and work without a definition snapshot', () => {
  const rows = [
    execution('http_ref', { taskId: 'first', retryCount: 0, status: 'FAILED' }),
    execution('http_ref', { taskId: 'retry-1', retryCount: 1, startTime: 100, endTime: 150, updateTime: 0 }),
    execution('http_ref', { taskId: 'retry-2', retryCount: 1, startTime: 200, endTime: 250, updateTime: 0 }),
  ]
  const index = indexTaskExecutions([], rows)
  assert.deepEqual(index.taskAttemptsByRef.get('http_ref'), [rows[2], rows[1], rows[0]])
  assert.equal(index.latestTaskByRef.get('http_ref'), rows[2])
  assert.equal(index.iterationByTaskId.size, 0)
})

test('empty and partially scheduled loops leave unexecuted child nodes unscheduled', () => {
  const definitions = loop([definition('first_ref'), definition('later_ref')])
  const empty = indexTaskExecutions(definitions, [execution('pacs_loop_ref', { outputData: { iteration: 0 } })])
  assert.equal(empty.latestTaskByRef.has('first_ref'), false)
  assert.equal(empty.latestTaskByRef.has('later_ref'), false)
  const running = indexTaskExecutions(definitions, [execution('first_ref__1', { status: 'IN_PROGRESS' })])
  assert.equal(running.latestTaskByRef.get('first_ref').status, 'IN_PROGRESS')
  assert.equal(running.latestTaskByRef.has('later_ref'), false)
})
