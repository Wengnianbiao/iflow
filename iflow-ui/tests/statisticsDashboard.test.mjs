import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'

let server
let ExecutionStatisticsPage
let statisticsSelection

before(async () => {
  const root = fileURLToPath(new URL('..', import.meta.url))
  server = await createServer({ root, server: { middlewareMode: true, ws: false, hmr: false } })
  ;({ ExecutionStatisticsPage } = await server.ssrLoadModule('/src/pages/statistics/ExecutionStatisticsPage.tsx'))
  ;({ statisticsSelection } = await server.ssrLoadModule('/src/pages/statistics/statisticsState.ts'))
})

after(async () => { await server?.close() })

const emptyMetrics = {
  totalCount: 0, runningCount: 0, successCount: 0, failureCount: 0, durationCount: 0,
  avgDurationMs: null, minDurationMs: null, maxDurationMs: null, successRate: null,
}

async function renderDashboard({ search = '', workflows = emptyMetrics, tasks = emptyMetrics, failed = false } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const selection = statisticsSelection(new URLSearchParams(search))
  const params = { ...selection.range, workflowRoute: selection.workflowRoute, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone }
  const key = ['statistics-overview', params]
  client.setQueryData(['statistics-workflow-options'], [])
  client.setQueryData(key, {
    workflowCount: 2, taskCount: 7,
    runtime: { workflows, tasks, granularity: 'hour', trend: [{ startTime: params.startTime, workflowCount: workflows.totalCount, taskCount: tasks.totalCount }] },
  })
  if (failed) {
    await assert.rejects(client.fetchQuery({ queryKey: key, queryFn: () => { throw new Error('统计服务不可用') } }))
  }
  try {
    return renderToStaticMarkup(React.createElement(QueryClientProvider, { client },
      React.createElement(MemoryRouter, { initialEntries: [`/dashboard?${search}`] }, React.createElement(ExecutionStatisticsPage))))
  } finally { client.clear() }
}

test('dashboard renders global configuration and explicit empty states without execution lists', async () => {
  const html = await renderDashboard()
  assert.match(html, /工作流总数<\/div><div[^>]*>2<\/div>/)
  assert.match(html, /任务节点总数<\/div><div[^>]*>7<\/div>/)
  assert.equal((html.match(/暂无执行数据/g) ?? []).length, 2)
  assert.match(html, /平均耗时<\/div><div[^>]*>—<\/div>/)
  assert.match(html, /执行次数<\/div><div[^>]*>0<\/div>/)
  assert.doesNotMatch(html, /<table|执行记录|每页|查看执行/)
})

test('workflow selection renders its runtime metrics while keeping global configuration totals', async () => {
  const metrics = { ...emptyMetrics, totalCount: 10, runningCount: 3, successCount: 6, failureCount: 1, durationCount: 7, avgDurationMs: 25, successRate: 6 / 7 }
  const html = await renderDashboard({ search: 'workflowRoute=demo&range=last7Days', workflows: metrics })
  assert.match(html, /value="demo"/)
  assert.match(html, /工作流总数<\/div><div[^>]*>2<\/div>/)
  assert.match(html, /任务节点总数<\/div><div[^>]*>7<\/div>/)
  assert.match(html, /85\.7%/)
  assert.match(html, /25 ms/)
  assert.match(html, /工作流状态分布：成功 6，失败 1，未结束 3/)
  assert.match(html, /60\.0%/)
})

test('failed refresh hides cached metrics and reports unavailable rather than zero data', async () => {
  const html = await renderDashboard({ failed: true })
  assert.match(html, /统计服务不可用/)
  assert.match(html, /统计暂不可用/)
  assert.match(html, /工作流总数<\/div><div[^>]*>—<\/div>/)
  assert.doesNotMatch(html, /暂无执行数据/)
  assert.doesNotMatch(html, /执行次数<\/div><div[^>]*>0<\/div>/)
})

test('invalid ranges suppress cached statistics and show the range validation error', async () => {
  const html = await renderDashboard({ search: 'range=custom&startTime=200&endTime=100' })
  assert.match(html, /结束时间不能早于开始时间/)
  assert.doesNotMatch(html, /暂无执行数据/)
  assert.match(html, /工作流总数<\/div><div[^>]*>—<\/div>/)
})
