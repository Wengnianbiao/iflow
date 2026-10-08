import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire, Module } from 'node:module'
import { fileURLToPath } from 'node:url'
import { elkLayout } from 'reaflow'
import { compactFlowLayoutOptions, normalizeJoinEdgeSections } from '../src/flow/layoutOptions.ts'

// Use the real mapper and task sizes, replacing only the unmounted UI components.
const root = fileURLToPath(new URL('..', import.meta.url))
const requireApp = createRequire(`${root}/package.json`)
const esbuild = createRequire(requireApp.resolve('vite'))('esbuild')
const uiExports = {
  forms: ['DoWhileTaskForm', 'ForkJoinTaskForm', 'FunctionTaskForm', 'HTTPTaskForm', 'INLINETaskForm', 'JDBCTaskForm', 'JoinTaskForm', 'ParamMapTaskForm', 'SPITaskForm', 'SwitchTaskForm', 'WebServiceTaskForm'],
  cards: ['HTTPTaskCard', 'FUNCTIONTaskCard', 'INLINETaskCard', 'JDBCTaskCard', 'MAPPINGTaskCard', 'SPITaskCard', 'WebServiceTaskCard'],
}
const built = await esbuild.build({
  entryPoints: [`${root}/src/flow/conductorMapper.ts`],
  bundle: true, platform: 'node', format: 'cjs', write: false, tsconfig: `${root}/tsconfig.json`, logLevel: 'silent',
  plugins: [{ name: 'headless-flow-catalog', setup(build) {
    build.onResolve({ filter: /^@\/pages\/definition\/EditorPanel\/TaskFormTab\/forms$/ }, () => ({ path: 'forms', namespace: 'flow-ui' }))
    build.onResolve({ filter: /^\.\/cards$/ }, args => args.importer.endsWith('/coreTaskPlugins.tsx') ? { path: 'cards', namespace: 'flow-ui' } : undefined)
    build.onLoad({ filter: /.*/, namespace: 'flow-ui' }, args => ({
      contents: uiExports[args.path].map(name => `export const ${name} = () => null;`).join('\n'),
    }))
  } }],
})
const compiled = new Module(`${root}/flow-layout-test.cjs`)
compiled.filename = `${root}/flow-layout-test.cjs`
compiled._compile(built.outputFiles[0].text, compiled.filename)
const { workflowToReaflow } = compiled.exports

const canvasLayoutOptions = {
  ...compactFlowLayoutOptions,
  'org.eclipse.elk.spacing.edgeEdge': '170',
  'org.eclipse.elk.padding': '[top=10,left=100,bottom=10,right=100]',
  'org.eclipse.elk.layered.edgeLabels.centerLabelPlacementStrategy': 'SPACE_EFFICIENT_LAYER',
  'org.eclipse.elk.nodeLabels.placement': 'V_CENTER',
}

function assertDownwardRoute(sections, edgeId) {
  for (const section of sections) {
    const points = [section.startPoint, ...(section.bendPoints ?? []), section.endPoint]
    for (let index = 1; index < points.length; index += 1) {
      const previous = points[index - 1]
      const point = points[index]
      assert.ok(point.y >= previous.y, `${edgeId} doubles back before its merge node`)
      assert.ok(point.x === previous.x || point.y === previous.y, `${edgeId} loses its orthogonal route`)
    }
    assert.equal(points[1].x, section.startPoint.x, `${edgeId} leaves its source port sideways`)
    assert.equal(points.at(-2).x, section.endPoint.x, `${edgeId} enters its merge port sideways`)
  }
}

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
  const layout = await elkLayout(nodes, edges, canvasLayoutOptions)
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
    assertDownwardRoute(normalizeJoinEdgeSections(edge.sections), edge.id)
  }
})

test('SWITCH empty cases and shorter branches merge without turning back after compaction', async () => {
  // GetItemResult topology, with no workflow inputs or HTTP configuration.
  const task = (ref, type = 'HTTP') => ({ name: ref, taskReferenceName: ref, type })
  const workflow = {
    route: 'switch-layout', name: '条件布局测试', version: 1,
    tasks: [{
      ...task('switch_report_ref', 'SWITCH'),
      decisionCases: {
        LIS: [task('lis_report_ref'), task('mapping_ctstk_ref', 'MAPPING')],
        ECG: [task('ecg_report_ref')],
        PACS: [],
      },
      defaultCase: [],
    }],
  }
  const original = structuredClone(workflow)
  const { nodes, edges } = workflowToReaflow(workflow)
  const mergeEdges = edges.filter(edge => edge.data?.switchJoinMerge)
  assert.equal(mergeEdges.length, 4)
  assert.deepEqual(mergeEdges.map(edge => edge.from), ['mapping_ctstk_ref', 'ecg_report_ref', 'switch_report_ref', 'switch_report_ref'])
  assert.deepEqual(mergeEdges.filter(edge => edge.text).map(edge => edge.text), ['PACS', 'defaultCase'])
  assert.equal(mergeEdges.find(edge => edge.text === 'defaultCase').data.isDefault, true)
  assert.ok(edges.filter(edge => edge.to !== 'switch_report_ref_switch_join').every(edge => !edge.data?.switchJoinMerge))

  // Text measurement needs a browser; node sizes and ports determine routing here.
  const layout = await elkLayout(
    nodes.map(({ text, ...node }) => node),
    edges.map(({ text, ...edge }) => edge),
    canvasLayoutOptions,
  )
  const byId = Object.fromEntries(layout.children.map(node => [node.id, node]))
  const join = byId.switch_report_ref_switch_join
  assert.ok(join.y > byId.mapping_ctstk_ref.y + byId.mapping_ctstk_ref.height)

  const routedMerges = layout.edges.filter(edge => edge.data?.switchJoinMerge)
  assert.ok(routedMerges.some(edge => {
    const section = edge.sections[0]
    const points = [section.startPoint, ...(section.bendPoints ?? []), section.endPoint]
    return points.some((point, index) => index > 0 && point.y < points[index - 1].y)
  }), 'The regression topology must reproduce an ELK compaction backtrack')
  for (const edge of routedMerges) {
    const originalSections = structuredClone(edge.sections)
    const sections = normalizeJoinEdgeSections(edge.sections)
    assertDownwardRoute(sections, edge.id)
    assert.deepEqual(edge.sections, originalSections)
    assert.deepEqual(sections[0].startPoint, edge.sections[0].startPoint)
    assert.deepEqual(sections[0].endPoint, edge.sections[0].endPoint)
  }
  assert.deepEqual(workflow, original)
})

test('merge normalization preserves valid routes and upward loop edges', () => {
  const valid = [{ startPoint: { x: 10, y: 10 }, bendPoints: [{ x: 10, y: 50 }, { x: 70, y: 50 }], endPoint: { x: 70, y: 100 } }]
  const upward = [{ startPoint: { x: 10, y: 100 }, bendPoints: [{ x: 10, y: 120 }, { x: 70, y: 120 }], endPoint: { x: 70, y: 10 } }]
  assert.equal(normalizeJoinEdgeSections(valid), valid)
  assert.equal(normalizeJoinEdgeSections(upward), upward)
  assert.equal(normalizeJoinEdgeSections(undefined), undefined)
})
