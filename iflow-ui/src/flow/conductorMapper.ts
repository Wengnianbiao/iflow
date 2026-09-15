import type { EdgeData, NodeData, PortData, PortSide } from 'reaflow'
import {
  getTaskFlowKind,
  getTaskNodeSize,
  taskBlocksContinuation,
  type TaskFlowKind,
} from '@/tasks'
import type { WorkflowDef, WorkflowTask } from '@/types/workflow'
import { TaskType } from './taskType'

export interface Crumb {
  ref: string
  refIdx: number
  type: string
  parent?: string | null
  decisionBranch?: string
  forkIndex?: number
}

export interface NodeTaskData {
  task: WorkflowTask
  crumbs: Crumb[]
  nodeKind?: TaskFlowKind
  selected?: boolean
  status?: string
  attempts?: number
}

type WalkResult = {
  nodes: NodeData<NodeTaskData>[]
  edges: EdgeData[]
  previousTask?: WorkflowTask
  previousNode?: NodeData<NodeTaskData>
  previousAllowsConnection: boolean
  pendingFork?: {
    task: WorkflowTask
    branchTails: Array<NodeData<NodeTaskData> | undefined>
  }
}

export const START_TASK_REF = '__start__'
export const END_TASK_REF = '__end__'
export const EDGE_SPACING = 170
export const BOTTOM_PORT_MARGIN = 10

function refName(task: WorkflowTask): string {
  return task.taskReferenceName || task.name || 'unnamed'
}

function taskList(value: unknown): WorkflowTask[] {
  return Array.isArray(value) ? value : []
}

export function northPort(node: Pick<NodeData, 'id'>, index?: number, hidden = false): PortData & { index?: number } {
  return {
    id: `${node.id}-north-port`,
    width: 2,
    height: 2,
    side: 'NORTH' as PortSide,
    disabled: true,
    hidden,
    index,
  }
}

export function southPort(node: Pick<NodeData, 'id'>, index?: number): PortData & { index?: number } {
  return {
    id: `${node.id}-south-port`,
    width: 2,
    height: 2,
    side: 'SOUTH' as PortSide,
    disabled: true,
    index,
  }
}

function switchJoinId(switchRef: string) {
  return `${switchRef}_switch_join`
}

function switchJoinSouthPortId(fakeTaskId: string) {
  return `switch_fake_task_${fakeTaskId}-south-port`
}

export function nodeTargetPortId(nodeId: string) {
  return `${nodeId}-to`
}

export function switchBranchTargetPortId(switchId: string, decisionKey: string) {
  return `${switchId}_[key=${decisionKey}]-south-port-to`
}

export function switchBranchSouthPortId(switchId: string, decisionKey: string) {
  return `${switchId}_[key=${decisionKey}]-south-port`
}

export function switchJoinNorthPortId(fakeNodeId: string, decisionKey: string) {
  return `${fakeNodeId}-to-join-to=[key=${decisionKey}]-north-port`
}

function makeNode(task: WorkflowTask, crumbs: Crumb[], nodeKind: NodeTaskData['nodeKind'] = getTaskFlowKind(task.type), ports?: PortData[]): NodeData<NodeTaskData> {
  const id = refName(task)
  const size = getTaskNodeSize(task)
  return {
    id,
    text: task.name || id,
    ...size,
    ports: ports ?? [southPort({ id })],
    data: { task, crumbs, nodeKind },
  }
}

function withParent(node: NodeData<NodeTaskData>, parent?: string): NodeData<NodeTaskData> {
  return parent ? { ...node, parent } : node
}

function sourceSouthPort(node: NodeData<NodeTaskData>) {
  return node.data?.nodeKind === 'switch_join' ? switchJoinSouthPortId(node.id) : `${node.id}-south-port`
}

function addEdge(edges: EdgeData[], edge: EdgeData) {
  if (edges.some((item) => item.id === edge.id)) return
  edges.push(edge)
}

function edgeId(from: string, to: string, text?: string) {
  return `edge_${from}_${to}${text ? `_${text}` : ''}`
}

function connectSequential(result: WalkResult, target: NodeData<NodeTaskData>) {
  if (!result.previousNode || !result.previousAllowsConnection) return
  addEdge(result.edges, {
    id: edgeId(result.previousNode.id, target.id),
    from: result.previousNode.id,
    fromPort: sourceSouthPort(result.previousNode),
    to: target.id,
    toPort: nodeTargetPortId(target.id),
  })
}

function decisionBranches(task: WorkflowTask): Record<string, WorkflowTask[]> {
  return {
    ...(task.decisionCases ?? {}),
    defaultCase: taskList(task.defaultCase),
  }
}

export function forkBranchSouthPortId(forkId: string, branchIndex: number) {
  return `${forkId}_[key=${branchIndex}]-south-port`
}

function joinNorthPortId(joinId: string, branchIndex: number) {
  return `${joinId}-joinOnTask-${branchIndex}-north-port`
}

function makeSwitchNode(task: WorkflowTask, crumbs: Crumb[], decisionKeys: string[]) {
  const id = refName(task)
  const ports = decisionKeys.map((key, index) => southPort({ id: `${id}_[key=${key}]` }, index))
  return makeNode(task, crumbs, 'switch', ports)
}

function makeSwitchJoinNode(task: WorkflowTask, crumbs: Crumb[], decisionKeys: string[]) {
  const id = switchJoinId(refName(task))
  const ports: PortData[] = [
    {
      id: switchJoinSouthPortId(id),
      side: 'SOUTH' as PortSide,
      disabled: true,
      width: 2,
      height: 2,
    },
    ...decisionKeys.map((key, index) =>
      northPort({ id: `${id}-to-join-to=[key=${key}]` }, (decisionKeys.length || 1) - 1 - index, true),
    ),
  ]
  return makeNode({ taskReferenceName: id, name: `${refName(task)}_switch_join`, type: TaskType.SWITCH_JOIN }, crumbs, 'switch_join', ports)
}

function processSwitch(task: WorkflowTask, crumbs: Crumb[]): WalkResult {
  const branches = decisionBranches(task)
  const decisionKeys = Object.keys(branches)
  const switchNode = makeSwitchNode(task, crumbs, decisionKeys)
  const fakeNode = makeSwitchJoinNode(task, crumbs, decisionKeys)
  const result: WalkResult = {
    nodes: [switchNode],
    edges: [],
    previousTask: fakeNode.data!.task,
    previousNode: fakeNode,
    previousAllowsConnection: true,
  }

  decisionKeys.forEach((key) => {
    const branchTasks = taskList(branches[key])
    if (!branchTasks.length) {
      addEdge(result.edges, {
        id: `edge_dp__fake_${switchNode.id}_${key}-direct`,
        from: switchNode.id,
        fromPort: switchBranchSouthPortId(switchNode.id, key),
        to: fakeNode.id,
        toPort: switchJoinNorthPortId(fakeNode.id, key),
        text: key,
        data: { isDefault: key === 'defaultCase' },
      })
      return
    }

    const branchResult = walkTasks(branchTasks, crumbs.concat({ ref: switchNode.id, refIdx: -1, type: task.type, parent: switchNode.id, decisionBranch: key }))
    result.nodes.push(...branchResult.nodes)
    result.edges.push(...branchResult.edges)

    const firstNode = branchResult.nodes[0]
    addEdge(result.edges, {
      id: edgeId(switchNode.id, firstNode.id, key),
      from: switchNode.id,
      fromPort: switchBranchSouthPortId(switchNode.id, key),
      to: firstNode.id,
      toPort: switchBranchTargetPortId(switchNode.id, key),
      text: key,
      data: { action: 'ADD_TASK_ABOVE', isDefault: key === 'defaultCase' },
    })

    const tailNode = branchResult.previousNode ?? firstNode
    if (branchResult.previousAllowsConnection) {
      addEdge(result.edges, {
        id: `edge_dp__fake_${switchNode.id}_${tailNode.id}`,
        from: tailNode.id,
        fromPort: sourceSouthPort(tailNode),
        to: fakeNode.id,
        toPort: switchJoinNorthPortId(fakeNode.id, key),
      })
    }
  })

  result.nodes.push(fakeNode)
  return result
}

function processFork(task: WorkflowTask, crumbs: Crumb[]): WalkResult {
  const forkTasks = task.forkTasks ?? []
  const id = refName(task)
  const forkNode = makeNode(
    task,
    crumbs,
    'fork',
    forkTasks.map((_, branchIndex) => southPort({ id: `${id}_[key=${branchIndex}]` }, branchIndex)),
  )
  const result: WalkResult = {
    nodes: [forkNode],
    edges: [],
    previousTask: task,
    previousNode: forkNode,
    previousAllowsConnection: true,
    pendingFork: { task, branchTails: [] },
  }

  forkTasks.forEach((branch, branchIndex) => {
    const branchCrumbs = crumbs.concat({
      ref: id,
      refIdx: -1,
      type: task.type,
      parent: id,
      forkIndex: branchIndex,
    })
    const branchResult = walkTasks(taskList(branch), branchCrumbs)
    result.nodes.push(...branchResult.nodes)
    result.edges.push(...branchResult.edges)
    const firstNode = branchResult.nodes[0]
    if (firstNode) {
      addEdge(result.edges, {
        id: edgeId(id, firstNode.id, String(branchIndex)),
        from: id,
        fromPort: forkBranchSouthPortId(id, branchIndex),
        to: firstNode.id,
        toPort: nodeTargetPortId(firstNode.id),
        data: { forkReferenceName: id, branchIndex },
      })
    }
    result.pendingFork!.branchTails[branchIndex] = branchResult.previousAllowsConnection
      ? branchResult.previousNode
      : undefined
  })

  return result
}

function connectForkToJoin(result: WalkResult, joinTask: WorkflowTask, crumbs: Crumb[]) {
  const pendingFork = result.pendingFork!
  const forkTask = pendingFork.task
  const forkId = refName(forkTask)
  const joinId = refName(joinTask)
  const branchCount = Math.max(forkTask.forkTasks?.length ?? 0, 1)
  const ports: PortData[] = [
    southPort({ id: joinId }),
    ...Array.from({ length: branchCount }, (_, branchIndex) => (
      northPort({ id: `${joinId}-joinOnTask-${branchIndex}` }, branchCount - 1 - branchIndex, true)
    )),
  ]
  const joinNode = makeNode(joinTask, crumbs, 'task', ports)
  result.nodes.push(joinNode)

  Array.from({ length: branchCount }, (_, branchIndex) => {
    const tail = pendingFork.branchTails[branchIndex]
    const from = tail?.id ?? forkId
    addEdge(result.edges, {
      id: `edge_fork_join_${forkId}_${joinId}_${branchIndex}`,
      from,
      fromPort: tail ? sourceSouthPort(tail) : forkBranchSouthPortId(forkId, branchIndex),
      to: joinId,
      toPort: joinNorthPortId(joinId, branchIndex),
      data: {
        forkReferenceName: forkId,
        branchIndex,
        delayedEdge: Boolean(tail && !(joinTask.joinOn ?? []).includes(tail.id)),
      },
    })
  })

  result.previousTask = joinTask
  result.previousNode = joinNode
  result.previousAllowsConnection = true
  result.pendingFork = undefined
}

function processDoWhile(task: WorkflowTask, crumbs: Crumb[]): WalkResult {
  const id = refName(task)
  const doWhileNode = makeNode(
    task,
    crumbs,
    'do_while',
    [southPort({ id })],
  )
  doWhileNode.nodePadding = [50, 110, 50, 110]
  const bodyCrumbs = crumbs.concat({ ref: id, refIdx: -1, type: task.type, parent: id })
  const bodyResult = walkTasks(taskList(task.loopOver), bodyCrumbs)
  const childNodes = bodyResult.nodes.map((node, index) => {
    const childNode = index === 0
      ? { ...node, ports: [...(node.ports ?? []), northPort(node)] }
      : node
    return withParent(childNode, childNode.parent ?? id)
  })
  const childEdges = bodyResult.edges.map((edge) => edge.parent ? edge : { ...edge, parent: id })

  return {
    nodes: [doWhileNode, ...childNodes],
    edges: childEdges,
    previousTask: task,
    previousNode: doWhileNode,
    previousAllowsConnection: true,
  }
}

function walkTasks(tasks: WorkflowTask[], crumbs: Crumb[] = []): WalkResult {
  const result: WalkResult = { nodes: [], edges: [], previousAllowsConnection: false }

  tasks.forEach((task, index) => {
    const taskCrumbs = crumbs.concat({ ref: refName(task), refIdx: index, type: task.type, parent: crumbs.at(-1)?.ref ?? null })
    const flowKind = getTaskFlowKind(task.type)
    if (flowKind === 'switch') {
      const switchResult = processSwitch(task, taskCrumbs)
      connectSequential(result, switchResult.nodes[0])
      result.nodes.push(...switchResult.nodes)
      result.edges.push(...switchResult.edges)
      result.previousTask = switchResult.previousTask
      result.previousNode = switchResult.previousNode
      result.previousAllowsConnection = switchResult.previousAllowsConnection
      return
    }

    if (flowKind === 'fork') {
      const forkResult = processFork(task, taskCrumbs)
      connectSequential(result, forkResult.nodes[0])
      result.nodes.push(...forkResult.nodes)
      result.edges.push(...forkResult.edges)
      result.previousTask = forkResult.previousTask
      result.previousNode = forkResult.previousNode
      result.previousAllowsConnection = forkResult.previousAllowsConnection
      result.pendingFork = forkResult.pendingFork
      return
    }

    if (flowKind === 'join' && result.pendingFork) {
      connectForkToJoin(result, task, taskCrumbs)
      return
    }

    if (flowKind === 'do_while') {
      const doWhileResult = processDoWhile(task, taskCrumbs)
      connectSequential(result, doWhileResult.nodes[0])
      result.nodes.push(...doWhileResult.nodes)
      result.edges.push(...doWhileResult.edges)
      result.previousTask = doWhileResult.previousTask
      result.previousNode = doWhileResult.previousNode
      result.previousAllowsConnection = doWhileResult.previousAllowsConnection
      result.pendingFork = undefined
      return
    }

    const node = makeNode(task, taskCrumbs)
    connectSequential(result, node)
    result.nodes.push(node)
    result.previousTask = task
    result.previousNode = node
    result.previousAllowsConnection = !taskBlocksContinuation(task.type)
    result.pendingFork = undefined
  })

  return result
}

export function workflowToReaflow(def: WorkflowDef): { nodes: NodeData<NodeTaskData>[]; edges: EdgeData[] } {
  const start = makeNode({ taskReferenceName: START_TASK_REF, name: 'Start', type: TaskType.TERMINAL }, [], 'terminal')
  const end = makeNode({ taskReferenceName: END_TASK_REF, name: 'End', type: TaskType.TERMINAL }, [], 'terminal', undefined)
  end.ports = undefined
  const result = walkTasks(taskList(def.tasks))
  const nodes = [start, ...result.nodes]
  const edges = [...result.edges]

  if (result.nodes.length) {
    addEdge(edges, {
      id: edgeId(start.id, result.nodes[0].id),
      from: start.id,
      fromPort: `${start.id}-south-port`,
      to: result.nodes[0].id,
      toPort: nodeTargetPortId(result.nodes[0].id),
    })
  } else {
    nodes.push(end)
    addEdge(edges, {
      id: edgeId(start.id, end.id),
      from: start.id,
      fromPort: `${start.id}-south-port`,
      to: end.id,
      toPort: nodeTargetPortId(end.id),
    })
  }

  if (result.previousNode && result.previousAllowsConnection) {
    nodes.push(end)
    addEdge(edges, {
      id: edgeId(result.previousNode.id, end.id),
      from: result.previousNode.id,
      fromPort: sourceSouthPort(result.previousNode),
      to: end.id,
      toPort: nodeTargetPortId(end.id),
    })
  }

  return { nodes, edges }
}
