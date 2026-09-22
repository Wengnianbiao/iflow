import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { elkLayout } from 'reaflow'
import { compactFlowLayoutOptions, normalizeForkJoinEdgeSections } from '../src/flow/layoutOptions.ts'

test('fork branches stay compact beside a tall loop and join below the longest branch', async () => {
  const nodes = [
    { id: 'fork', width: 350, height: 120 },
    { id: 'ecg-map', width: 350, height: 130 },
    { id: 'ecg-http', width: 350, height: 120 },
    { id: 'pacs-loop', width: 570, height: 610 },
    { id: 'join', width: 350, height: 120 },
  ].map(node => ({
    ...node,
    layoutOptions: { 'org.eclipse.elk.alignment': 'TOP' },
  }))
  const edges = [
    ['fork', 'ecg-map'], ['ecg-map', 'ecg-http'],
    ['fork', 'pacs-loop'], ['ecg-http', 'join'], ['pacs-loop', 'join'],
  ].map(([from, to]) => ({ id: `${from}-${to}`, from, to }))
  const layout = await elkLayout(nodes, edges, compactFlowLayoutOptions)
  const byId = Object.fromEntries(layout.children.map(node => [node.id, node]))
  const bottom = node => node.y + node.height

  assert.equal(byId['ecg-map'].y, byId['pacs-loop'].y)
  const ecgGap = byId['ecg-http'].y - bottom(byId['ecg-map'])
  assert.ok(ecgGap >= 70 && ecgGap <= 110, `Unexpected ECG gap: ${ecgGap}`)
  assert.ok(bottom(byId['ecg-http']) < bottom(byId['pacs-loop']))
  const joinGap = byId.join.y - bottom(byId['pacs-loop'])
  assert.ok(joinGap >= 70 && joinGap <= 110, `Unexpected join gap: ${joinGap}`)
})

test('PACS loop with nested tasks and fork ports does not stretch the ECG branch', async () => {
  // Layout-only snapshot of SyncPacsLisNewApply: no workflow inputs or task configuration.
  const { nodes, edges } = JSON.parse(readFileSync(new URL('./fixtures/fork-loop-layout.json', import.meta.url)))
  const layout = await elkLayout(nodes, edges, {
    ...compactFlowLayoutOptions,
    'org.eclipse.elk.spacing.edgeEdge': '170',
    'org.eclipse.elk.padding': '[top=10,left=100,bottom=10,right=100]',
    'org.eclipse.elk.layered.edgeLabels.centerLabelPlacementStrategy': 'SPACE_EFFICIENT_LAYER',
    'org.eclipse.elk.nodeLabels.placement': 'V_CENTER',
  })
  const byId = Object.fromEntries(layout.children.map(node => [node.id, node]))
  const loop = byId.pacs_loop_ref
  const mapping = byId.mapping_ecg_req_ref
  const http = byId.sync_ecg_ref
  const join = byId.apply_join_ref
  assert.equal(mapping.y, loop.y)
  assert.equal(http.y - mapping.y - mapping.height, 70)
  assert.ok(http.y + http.height < loop.y + loop.height)
  assert.equal(join.y - loop.y - loop.height, 100)
  assert.equal(loop.children.length, 3)
  for (const edge of layout.edges) assert.ok(edge.sections?.length, `Missing route: ${edge.id}`)

  const mergeEdges = layout.edges.filter(edge => edge.id.startsWith('edge_fork_join_'))
  for (const edge of mergeEdges) {
    const sections = normalizeForkJoinEdgeSections(edge.sections)
    for (const section of sections) {
      const points = [section.startPoint, ...(section.bendPoints ?? []), section.endPoint]
      points.reduce((previousY, point) => {
        assert.ok(point.y >= previousY, `${edge.id} doubles back below the Join node`)
        return point.y
      }, points[0].y)
    }
  }
})
