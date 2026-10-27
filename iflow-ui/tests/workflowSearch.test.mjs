import test from 'node:test'
import assert from 'node:assert/strict'
import { buildSearchParams, emptySearchFilters, readSearchState, writeSearchState } from '../src/pages/executions/searchState.ts'
import { formatExecutionDuration, formatExecutionTime } from '../src/pages/executions/formatters.ts'
import { presetTimeRange, relativeTimeRange } from '../src/pages/executions/timeRange.ts'

const PAGE_SIZES = [15, 25, 50]

test('workflowRoute links map to the iFlow search API', () => {
  const state = readSearchState(new URLSearchParams('workflowRoute=hl-open-api%2Flis-status&correlationId=order-1&status=FAILED'), PAGE_SIZES)
  assert.deepEqual(buildSearchParams(state), {
    workflowRoutes: ['hl-open-api/lis-status'], correlationId: 'order-1', status: ['FAILED'], pageNum: 1, pageSize: 15,
  })
})

test('a shared URL restores submitted filters, page and page size', () => {
  const expected = {
    filters: { ...emptySearchFilters(), workflowRoutes: ['hl-open-api/申请单', 'GetItemResult'], keyword: 'A&B=1', status: 'COMPLETED' },
    page: 3, rowsPerPage: 25,
  }
  const url = writeSearchState(new URLSearchParams(), expected)
  assert.deepEqual(readSearchState(new URLSearchParams(url.toString()), PAGE_SIZES), expected)
})

test('malformed URL pagination and unknown status cannot reach the API', () => {
  for (const page of ['0', '-2', '1.5', 'NaN', 'Infinity', '9007199254740992']) {
    const state = readSearchState(new URLSearchParams({ page, rowsPerPage: '9999', status: 'UNKNOWN' }), PAGE_SIZES)
    assert.equal(state.page, 1)
    assert.equal(state.rowsPerPage, 15)
    assert.equal(state.filters.status, '')
  }
})

test('reset clears workflow routes and preserves page size and unrelated URL state', () => {
  const url = writeSearchState(new URLSearchParams('workflowRoute=first&workflowRoute=old&correlationId=x&status=FAILED&startFrom=1000&startTo=2000&page=3&rowsPerPage=25&view=compact'), {
    filters: emptySearchFilters(), page: 1, rowsPerPage: 25,
  })
  assert.equal(url.get('view'), 'compact')
  assert.deepEqual(readSearchState(url, PAGE_SIZES), {
    filters: emptySearchFilters(), page: 1, rowsPerPage: 25,
  })
  for (const key of ['workflowRoute', 'correlationId', 'status', 'startFrom', 'startTo']) assert.equal(url.has(key), false)
})

test('whitespace-only filters are not sent to the API', () => {
  const state = readSearchState(new URLSearchParams('workflowRoute=%20%20&correlationId=%20'), PAGE_SIZES)
  const params = buildSearchParams(state)
  assert.equal(params.workflowRoutes, undefined)
  assert.equal(params.correlationId, undefined)
  assert.equal(params.status, undefined)
})

test('running executions with a zero end time use the current time for duration', () => {
  assert.equal(formatExecutionDuration({ startTime: 1000, endTime: 0, durationMs: null }, 61000), '1 分 0 秒')
  assert.equal(formatExecutionDuration({ startTime: 1000, endTime: 0, durationMs: 125 }, 61000), '125 毫秒')
  assert.equal(formatExecutionDuration({ startTime: 0, endTime: null }, 61000), '-')
  assert.equal(formatExecutionDuration({ startTime: 2000, endTime: 1000 }, 61000), '-')
})

test('invalid execution timestamps display a fallback instead of throwing', () => {
  for (const time of [undefined, null, 0, NaN, Infinity, 1e20]) assert.equal(formatExecutionTime(time), '-')
})


test('multiple workflow selections are deduplicated and kept separate in URL and API', () => {
  const state = readSearchState(new URLSearchParams('workflowRoute=first&workflowRoute=second&workflowRoute=first&workflowRoute=%20'), PAGE_SIZES)
  assert.deepEqual(state.filters.workflowRoutes, ['first', 'second'])
  assert.deepEqual(buildSearchParams(state).workflowRoutes, ['first', 'second'])
  assert.deepEqual(writeSearchState(new URLSearchParams(), state).getAll('workflowRoute'), ['first', 'second'])
})

test('time bounds survive sharing and pagination and reach the start-time API', () => {
  const state = readSearchState(new URLSearchParams('startFrom=1700000000000&startTo=1700100000000'), PAGE_SIZES)
  const next = { ...state, page: 2 }
  assert.deepEqual(readSearchState(writeSearchState(new URLSearchParams(), next), PAGE_SIZES), next)
  assert.equal(buildSearchParams(next).startTime, 1700000000000)
  assert.equal(buildSearchParams(next).endTime, 1700100000000)
})

test('invalid dates are omitted and open-ended ranges keep their one boundary', () => {
  for (const value of ['NaN', 'Infinity', '-1', '1.5', '8640000000000001', '']) {
    const state = readSearchState(new URLSearchParams({ startFrom: value, startTo: value }), PAGE_SIZES)
    assert.equal(buildSearchParams(state).startTime, undefined)
    assert.equal(buildSearchParams(state).endTime, undefined)
  }
  const upperOnly = readSearchState(new URLSearchParams('startTo=1700000000000'), PAGE_SIZES)
  assert.equal(buildSearchParams(upperOnly).startTime, 0)
  const state = readSearchState(new URLSearchParams('startFrom=0&startTo=1700000000000'), PAGE_SIZES)
  assert.equal(buildSearchParams(state).startTime, 0)
  assert.equal(buildSearchParams(state).endTime, 1700000000000)
})

test('presets use local calendar boundaries and relative ranges preserve or round seconds', () => {
  const now = new Date(2026, 8, 22, 12, 34, 56, 789).getTime()
  assert.deepEqual(presetTimeRange('today', now), {
    from: String(new Date(2026, 8, 22).getTime()), to: String(new Date(2026, 8, 22, 23, 59, 59, 999).getTime()),
  })
  assert.deepEqual(presetTimeRange('yesterday', now), {
    from: String(new Date(2026, 8, 21).getTime()), to: String(new Date(2026, 8, 21, 23, 59, 59, 999).getTime()),
  })
  assert.deepEqual(presetTimeRange('72h', now), { from: String(now - 72 * 3600000), to: '' })
  assert.deepEqual(relativeTimeRange(15, 'minutes', true, now), { from: String(new Date(2026, 8, 22, 12, 19).getTime()), to: '' })
  for (const value of [0, -1, NaN, Infinity, 1e20]) assert.equal(relativeTimeRange(value, 'hours', false, now), null)
})

test('manual and restart termination filters survive shared URLs and reach the API', () => {
  for (const status of ['TERMINATED', 'INTERRUPTED']) {
    const state = readSearchState(new URLSearchParams({ status }), PAGE_SIZES)
    assert.equal(state.filters.status, status)
    assert.deepEqual(buildSearchParams(state).status, [status])
    assert.equal(writeSearchState(new URLSearchParams(), state).get('status'), status)
  }
})


test('unsupported pause status cannot reach the workflow search API', () => {
  const state = readSearchState(new URLSearchParams({ status: 'PAUSED' }), PAGE_SIZES)
  assert.equal(state.filters.status, '')
  assert.equal(buildSearchParams(state).status, undefined)
})
