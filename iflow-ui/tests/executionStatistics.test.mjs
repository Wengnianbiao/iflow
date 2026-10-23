import test from 'node:test'
import assert from 'node:assert/strict'
import { presetRange, localDateTime, statisticsDuration, statisticsRate } from '../src/pages/statistics/statisticsState.ts'

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
