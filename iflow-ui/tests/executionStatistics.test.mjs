import test from 'node:test'
import assert from 'node:assert/strict'
import { presetRange, localDateTime, statisticsDuration, statisticsRate, statisticsSelection, statisticsDistribution } from '../src/pages/statistics/statisticsState.ts'

test('calendar presets include the whole local day and seven calendar days', () => {
  const now = new Date(2026, 9, 22, 15, 30)
  assert.deepEqual(presetRange('today', now), {
    startTime: new Date(2026, 9, 22).getTime(), endTime: new Date(2026, 9, 23).getTime() - 1,
  })
  assert.deepEqual(presetRange('yesterday', now), {
    startTime: new Date(2026, 9, 21).getTime(), endTime: new Date(2026, 9, 22).getTime() - 1,
  })
  assert.deepEqual(presetRange('last7Days', now), {
    startTime: new Date(2026, 9, 16).getTime(), endTime: new Date(2026, 9, 23).getTime() - 1,
  })
  assert.equal(localDateTime(now.getTime()), '2026-10-22T15:30')
})

test('zero milliseconds and zero success rate remain distinct from no samples', () => {
  assert.equal(statisticsDuration(0), '0 ms')
  assert.equal(statisticsDuration(null), '—')
  assert.equal(statisticsDuration(12.345), '12.35 ms')
  assert.equal(statisticsRate(0), '0.0%')
  assert.equal(statisticsRate(null), '—')
  assert.equal(statisticsRate(1 / 3), '33.3%')
})

test('dashboard selection defaults to today and ignores obsolete list controls', () => {
  const now = new Date(2026, 9, 30, 12)
  const selection = statisticsSelection(new URLSearchParams('view=task&page=5&sort=totalCount&asyncOnly=true'), now)
  assert.equal(selection.preset, 'today')
  assert.equal(selection.workflowRoute, undefined)
  assert.equal(selection.validRange, true)
  assert.deepEqual(selection.range, presetRange('today', now))
  const filtered = statisticsSelection(new URLSearchParams('range=last7Days&workflowRoute=%20demo%20'), now)
  assert.equal(filtered.workflowRoute, 'demo')
  assert.deepEqual(filtered.range, presetRange('last7Days', now))
})

test('custom ranges preserve endpoints and reject invalid or reversed dates', () => {
  const selected = statisticsSelection(new URLSearchParams('range=custom&startTime=100&endTime=200&workflowRoute=demo'))
  assert.deepEqual(selected.range, { startTime: 100, endTime: 200 })
  assert.equal(selected.validRange, true)
  for (const range of ['startTime=200&endTime=100', 'startTime=NaN&endTime=100', 'startTime=-1&endTime=100']) {
    assert.equal(statisticsSelection(new URLSearchParams(`range=custom&${range}`)).validRange, false)
  }
})

test('status distribution uses terminal outcomes and keeps unfinished instances separate', () => {
  const segments = statisticsDistribution({ totalCount: 10, successCount: 6, failureCount: 1, runningCount: 3 })
  assert.deepEqual(segments, [{ name: '成功', value: 6 }, { name: '失败', value: 1 }, { name: '未结束', value: 3 }])
  assert.equal(segments.reduce((sum, item) => sum + item.value, 0), 10)
  assert.equal(statisticsRate(segments[0].value / 10), '60.0%')
  assert.deepEqual(statisticsDistribution({ successCount: 0, failureCount: 0, runningCount: 0 }).map((item) => item.value), [0, 0, 0])
})
