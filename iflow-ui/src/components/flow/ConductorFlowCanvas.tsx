import { useEffect, useMemo, useRef, useState, type MouseEvent, type PointerEvent, type ReactNode, type WheelEvent } from 'react'
import { Canvas, Edge, type EdgeData, type ElkRoot, type NodeData, type PortData } from 'reaflow'
import {
  workflowToReaflow,
  EDGE_SPACING,
  END_TASK_REF,
  START_TASK_REF,
  type NodeTaskData,
} from '@/flow/conductorMapper'
import { conductorTheme } from '@/flow/conductorTheme'
import { TaskType } from '@/flow/taskType'
import {
  addSwitchCase,
  addForkBranch,
  appendToDoWhile,
  appendToForkBranch,
  appendToSwitchBranch,
  createTaskSequence,
  findTask as findWorkflowTask,
  insertTasksAfterTask,
  insertTasksBeforeTask,
  parseSwitchBranch,
  removeSwitchCase,
  removeForkBranch,
  removeTask,
  type AddableTaskType,
} from '@/flow/workflowEditor'
import type { WorkflowDef, WorkflowTask } from '@/types/workflow'
import { CustomLabel } from './CustomLabel'
import { CustomNode } from './CustomNode'
import {
  ConductorDragNDropIcon,
  ConductorFitToFrameIcon,
  ConductorHomeIcon,
  ConductorMinusIcon,
  ConductorPlusIcon,
} from './ConductorZoomIcons'
import { QuickAddMenu } from './QuickAddMenu'

const INITIAL_ZOOM = 0.75
const PADDING_TOP = 65
const MIN_ZOOM = 0.1
const MAX_ZOOM = 2

type PortMenuTarget = {
  kind: 'port'
  node: NodeData<NodeTaskData>
  port: { id?: string; properties?: PortData }
}

type EdgeMenuTarget = {
  kind: 'edge'
  edge: EdgeData
}

type DoWhileBodyMenuTarget = {
  kind: 'doWhileBody'
  node: NodeData<NodeTaskData>
}

type MenuTarget = PortMenuTarget | EdgeMenuTarget | DoWhileBodyMenuTarget

type MenuState = {
  x: number
  y: number
  target: MenuTarget
} | null

type PendingDeleteTask = {
  name: string
  taskReferenceName: string
} | null

type PendingDeleteBranch = {
  operatorReferenceName: string
  operatorType: string
  branchName: string
  displayName: string
  taskCount: number
} | null

type LayoutSize = ElkRoot & {
  width: number
  height: number
}

type ConductorFlowCanvasProps = {
  workflow: WorkflowDef
  graph?: { nodes: NodeData<NodeTaskData>[]; edges: EdgeData[] }
  selectedTaskRef?: string
  onWorkflowChange?: (workflow: WorkflowDef) => void
  onSelectTask: (task?: WorkflowTask) => void
  readOnly?: boolean
  taskStatusByRef?: Record<string, string | undefined>
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

function findNodeTask(node?: NodeData<NodeTaskData>) {
  const task = node?.data?.task
  if (!task || task.type === TaskType.TERMINAL || task.type === TaskType.SWITCH_JOIN) return undefined
  return task
}

function selectedNodes(
  nodes: NodeData<NodeTaskData>[],
  selectedTaskRef?: string,
  taskStatusByRef: Record<string, string | undefined> = {},
): NodeData<NodeTaskData>[] {
  return nodes.map((node) => ({
    ...node,
    data: node.data
      ? { ...node.data, selected: node.id === selectedTaskRef, status: taskStatusByRef[node.id] }
      : node.data,
  }))
}

function parseNodeBranchTarget(port?: { id?: string; properties?: PortData }) {
  return parseSwitchBranch(port?.properties?.id ?? port?.id)
}

function initialZoomCenter(layout: LayoutSize, viewportWidth: number, zoomValue: number) {
  const startNode = layout.children?.[0] as
    | { x?: number; y?: number; width?: number; height?: number }
    | undefined

  if (!startNode) {
    return {
      x: Math.max(40, (viewportWidth - layout.width * zoomValue) / 2),
      y: PADDING_TOP,
    }
  }

  const realXPosition = (startNode.width ?? 0) / 2 + (startNode.x ?? 0)

  return {
    x: viewportWidth / 2 - realXPosition * zoomValue,
    y: (startNode.y ?? 0) + PADDING_TOP,
  }
}

function applyAddTask(workflow: WorkflowDef, target: MenuTarget, type: AddableTaskType) {
  const tasks = createTaskSequence(type)

  if (target.kind === 'doWhileBody') {
    return appendToDoWhile(workflow, target.node.id, tasks)
  }

  if (target.kind === 'port') {
    const node = target.node
    if (node.id === START_TASK_REF) {
      return { ...workflow, tasks: [...tasks, ...workflow.tasks] }
    }
    const branchName = parseNodeBranchTarget(target.port)
    if (branchName && [TaskType.SWITCH, TaskType.DECISION].includes(node.data?.task.type as never)) {
      return appendToSwitchBranch(workflow, node.id, branchName, tasks, 'end')
    }
    if (branchName && node.data?.task.type === TaskType.FORK_JOIN) {
      return appendToForkBranch(workflow, node.id, Number(branchName), tasks, 'end')
    }
    return insertTasksAfterTask(workflow, node.id, tasks)
  }

  const edge = target.edge
  const forkReferenceName = String(edge.data?.forkReferenceName ?? '')
  const branchIndex = Number(edge.data?.branchIndex)
  if (forkReferenceName && Number.isInteger(branchIndex)) {
    return appendToForkBranch(workflow, forkReferenceName, branchIndex, tasks, 'end')
  }
  const branchName = typeof edge.text === 'string' ? edge.text : parseSwitchBranch(edge.fromPort)
  if (branchName && edge.from) {
    return appendToSwitchBranch(workflow, edge.from, branchName, tasks, 'start')
  }

  if (edge.to && edge.to !== END_TASK_REF) return insertTasksBeforeTask(workflow, edge.to, tasks)
  if (edge.from === START_TASK_REF) return { ...workflow, tasks: [...tasks, ...workflow.tasks] }
  if (edge.from) return insertTasksAfterTask(workflow, edge.from, tasks)
  return workflow
}

export function ConductorFlowCanvas({
  workflow,
  graph,
  selectedTaskRef,
  onWorkflowChange = () => undefined,
  onSelectTask,
  readOnly = false,
  taskStatusByRef = {},
}: ConductorFlowCanvasProps) {
  const viewportRef = useRef<HTMLDivElement | null>(null)
  const hasCenteredRef = useRef(false)
  const draggingRef = useRef<{ x: number; y: number; originX: number; originY: number } | null>(null)
  const [zoom, setZoom] = useState(INITIAL_ZOOM)
  const [position, setPosition] = useState({ x: 80, y: PADDING_TOP })
  const [layout, setLayout] = useState<LayoutSize>({ width: 1600, height: 1000 })
  const [menu, setMenu] = useState<MenuState>(null)
  const [panEnabled, setPanEnabled] = useState(true)
  const [pendingDeleteTask, setPendingDeleteTask] = useState<PendingDeleteTask>(null)
  const [pendingDeleteBranch, setPendingDeleteBranch] = useState<PendingDeleteBranch>(null)

  const mappedGraph = useMemo(() => workflowToReaflow(workflow), [workflow])
  const { nodes: baseNodes, edges: baseEdges } = graph ?? mappedGraph
  const nodes = useMemo(
    () => selectedNodes(baseNodes, selectedTaskRef, taskStatusByRef),
    [baseNodes, selectedTaskRef, taskStatusByRef],
  )
  const edges = useMemo(
    () => baseEdges.map((edge) => ({
      ...edge,
      data: { ...edge.data, runtimeStatus: taskStatusByRef[edge.to ?? ''] },
    })),
    [baseEdges, taskStatusByRef],
  )

  useEffect(() => {
    hasCenteredRef.current = false
  }, [workflow.name, workflow.version])

  useEffect(() => {
    if (hasCenteredRef.current || !viewportRef.current || layout.width <= 0 || !layout.children?.length) return
    const rect = viewportRef.current.getBoundingClientRect()
    setZoom(INITIAL_ZOOM)
    setPosition(initialZoomCenter(layout, rect.width, INITIAL_ZOOM))
    hasCenteredRef.current = true
  }, [layout])

  const closeMenu = () => setMenu(null)

  const cancelDeleteTask = () => setPendingDeleteTask(null)

  const confirmDeleteTask = () => {
    if (!pendingDeleteTask) return
    onWorkflowChange(removeTask(workflow, pendingDeleteTask.taskReferenceName))
    if (selectedTaskRef === pendingDeleteTask.taskReferenceName) onSelectTask(undefined)
    setPendingDeleteTask(null)
  }

  const deleteOperatorBranch = (operatorReferenceName: string, operatorType: string, branchName: string) => {
    const nextWorkflow = operatorType === TaskType.FORK_JOIN
      ? removeForkBranch(workflow, operatorReferenceName, Number(branchName))
      : removeSwitchCase(workflow, operatorReferenceName, branchName)
    onWorkflowChange(nextWorkflow)
    if (selectedTaskRef && !findWorkflowTask(nextWorkflow.tasks, selectedTaskRef)) onSelectTask(undefined)
    closeMenu()
  }

  const cancelDeleteBranch = () => setPendingDeleteBranch(null)

  const confirmDeleteBranch = () => {
    if (!pendingDeleteBranch) return
    deleteOperatorBranch(
      pendingDeleteBranch.operatorReferenceName,
      pendingDeleteBranch.operatorType,
      pendingDeleteBranch.branchName,
    )
    setPendingDeleteBranch(null)
  }

  const handleWheel = (event: WheelEvent<HTMLDivElement>) => {
    event.preventDefault()
    if (event.ctrlKey || event.metaKey) {
      const nextZoom = clamp(zoom * (1 - event.deltaY * 0.001), MIN_ZOOM, MAX_ZOOM)
      const rect = viewportRef.current?.getBoundingClientRect()
      const cursorX = event.clientX - (rect?.left ?? 0)
      const cursorY = event.clientY - (rect?.top ?? 0)
      const scale = nextZoom / zoom
      setPosition((current) => ({
        x: cursorX - (cursorX - current.x) * scale,
        y: cursorY - (cursorY - current.y) * scale,
      }))
      setZoom(nextZoom)
      return
    }

    if (panEnabled) setPosition((current) => ({ x: current.x - event.deltaX, y: current.y - event.deltaY }))
  }

  const handlePointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (!panEnabled) return
    if (event.button !== 0) return
    const target = event.target as HTMLElement
    if (
      target.closest('.iflow-node-html') ||
      target.closest('.iflow-quick-add-menu') ||
      target.closest('.conductor-edge-button') ||
      target.closest('.conductor-edge-label') ||
      target.closest('button')
    ) return
    draggingRef.current = {
      x: event.clientX,
      y: event.clientY,
      originX: position.x,
      originY: position.y,
    }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!draggingRef.current) return
    const drag = draggingRef.current
    setPosition({
      x: drag.originX + event.clientX - drag.x,
      y: drag.originY + event.clientY - drag.y,
    })
  }

  const handlePointerUp = (event: PointerEvent<HTMLDivElement>) => {
    draggingRef.current = null
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
  }

  const resetPosition = () => {
    if (!viewportRef.current) return
    const rect = viewportRef.current.getBoundingClientRect()
    setPosition(initialZoomCenter(layout, rect.width, zoom))
  }

  const fitToScreen = () => {
    if (!viewportRef.current || layout.width <= 0 || layout.height <= 0) return
    const rect = viewportRef.current.getBoundingClientRect()
    const nextZoom = clamp(Math.min((rect.width - 80) / layout.width, (rect.height - 80) / layout.height), MIN_ZOOM, MAX_ZOOM)
    setZoom(nextZoom)
    setPosition({
      x: Math.max(40, (rect.width - layout.width * nextZoom) / 2),
      y: Math.max(40, (rect.height - layout.height * nextZoom) / 2),
    })
  }

  const activeEdgeId =
    menu?.target.kind === 'port'
      ? menu.target.port.properties?.id ?? menu.target.port.id
      : menu?.target.kind === 'edge'
        ? menu.target.edge.id
        : undefined

  return (
    <div
      ref={viewportRef}
      id="viewport-container"
      className="iflow-flow-viewport"
      onWheel={handleWheel}
      onPointerDownCapture={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
    >
      <div className="iflow-zoom-controls">
        <button type="button" className="zoom-control-button reset-position-button" aria-label="重置位置" data-tooltip="重置位置" onClick={resetPosition}>
          <ConductorHomeIcon />
        </button>
        <button type="button" className="zoom-control-button zoom-percent-button" aria-label="当前缩放比例" data-tooltip="当前缩放比例">
          {Math.round(zoom * 100)}%
        </button>
        <button type="button" className="zoom-control-button zoom-out-button" aria-label="缩小" data-tooltip="缩小" onClick={() => setZoom((value) => clamp(value - 0.1, MIN_ZOOM, MAX_ZOOM))}>
          <ConductorMinusIcon />
        </button>
        <button type="button" className="zoom-control-button zoom-in-button with-left-border" aria-label="放大" data-tooltip="放大" onClick={() => setZoom((value) => clamp(value + 0.1, MIN_ZOOM, MAX_ZOOM))}>
          <ConductorPlusIcon />
        </button>
        <button type="button" className="zoom-control-button fit-screen-button with-left-border" aria-label="适应屏幕" data-tooltip="适应屏幕" onClick={fitToScreen}>
          <ConductorFitToFrameIcon />
        </button>
        {!readOnly ? <button
          type="button"
          className={`zoom-control-button toggle-pan-button with-left-border ${panEnabled ? '' : 'active'}`}
          aria-label={panEnabled ? '禁用画布拖动' : '启用画布拖动'}
          data-tooltip={panEnabled ? '禁用画布拖动' : '启用画布拖动'}
          onClick={() => setPanEnabled((value) => !value)}
        >
          <ConductorDragNDropIcon />
        </button> : null}
      </div>

      {!readOnly ? <QuickAddMenu
        open={Boolean(menu)}
        x={menu?.x ?? 0}
        y={menu?.y ?? 0}
        onClose={closeMenu}
        onSelect={(type) => {
          if (!menu) return
          onWorkflowChange(applyAddTask(workflow, menu.target, type))
          closeMenu()
        }}
      /> : null}

      {!readOnly && pendingDeleteTask ? (
        <ConfirmChoiceDialog
          title="确认操作"
          message={<>确定删除任务 <strong>{pendingDeleteTask.name}</strong> 吗？</>}
          cancelLabel="取消"
          confirmLabel="确认"
          onCancel={cancelDeleteTask}
          onConfirm={confirmDeleteTask}
        />
      ) : null}

      {!readOnly && pendingDeleteBranch ? (
        <ConfirmChoiceDialog
          title="确认删除分支"
          message={
            <>
              分支 <strong>{pendingDeleteBranch.displayName}</strong> 中包含 {pendingDeleteBranch.taskCount} 个任务，
              删除分支会同时删除其中的任务，确定继续吗？
            </>
          }
          cancelLabel="取消"
          confirmLabel="确认删除"
          onCancel={cancelDeleteBranch}
          onConfirm={confirmDeleteBranch}
        />
      ) : null}

      <div id="workflow-diagram-outer">
        <div
          id="pan-and-zoom-wrapper-diagram-container"
          style={{
            transform: `translateX(${position.x}px) translateY(${position.y}px) scale(${zoom})`,
            width: layout.width,
            height: layout.height,
          }}
        >
          <div id="diagram-canvas-container" style={{ width: layout.width, height: layout.height }}>
            <Canvas
              nodes={nodes}
              edges={edges}
              animated
              fit={false}
              zoomable={false}
              pannable={false}
              layoutOptions={{
                'org.eclipse.elk.spacing.edgeEdge': EDGE_SPACING.toString(),
                'org.eclipse.elk.padding': '[top=10,left=100,bottom=10,right=100]',
                'org.eclipse.elk.layered.edgeLabels.centerLabelPlacementStrategy': 'SPACE_EFFICIENT_LAYER',
                'org.eclipse.elk.nodeLabels.placement': 'V_CENTER',
              }}
              edge={(edge: EdgeData) => {
                const dashed = edge?.data?.unreachableEdge === true || edge?.data?.delayedEdge === true
                const runtimeStatus = String(edge?.data?.runtimeStatus ?? '')
                const failed = ['FAILED', 'FAILED_WITH_TERMINAL_ERROR', 'TIMED_OUT'].includes(runtimeStatus)
                const completed = ['COMPLETED', 'COMPLETED_WITH_ERRORS'].includes(runtimeStatus)
                const unreachable = ['CANCELED', 'SKIPPED'].includes(runtimeStatus)
                return (
                  <Edge
                    selectable={false}
                    label={<CustomLabel />}
                    style={
                      failed
                        ? { stroke: '#dd2222', strokeWidth: 2 }
                        : completed
                          ? conductorTheme.edges.completed
                          : dashed || unreachable
                        ? {
                            stroke: '#b1b1b7',
                            strokeDasharray: '5',
                            strokeDashoffset: 10,
                            strokeWidth: 2,
                            markerEnd: 'none',
                          }
                        : conductorTheme.edges.default
                    }
                  />
                )
              }}
              node={
                <CustomNode
                  activeEdgeId={activeEdgeId}
                  readOnly={readOnly}
                  displayDescription={false}
                  onClick={(event: MouseEvent<Element>, node: NodeData<NodeTaskData>) => {
                    const target = event.target as HTMLElement
                    if (target.className.toString().includes('DeleteButton')) {
                      event.preventDefault()
                      event.stopPropagation()
                      return
                    }
                    event.stopPropagation()
                    onSelectTask(findNodeTask(node))
                  }}
                  onToggleTaskMenu={(event, payload) => {
                    if (readOnly) return
                    setMenu({ x: event.clientX, y: event.clientY, target: { kind: 'port', node: payload.node, port: payload.port } })
                  }}
                  onDeleteBranch={(event, payload) => {
                    if (readOnly) return
                    const branchName = parseNodeBranchTarget(payload.port)
                    if (!branchName) return
                    event.preventDefault()
                    event.stopPropagation()
                    const operatorTask = payload.node.data?.task
                    const branchTasks =
                      operatorTask?.type === TaskType.FORK_JOIN
                        ? operatorTask.forkTasks?.[Number(branchName)] ?? []
                        : branchName === 'defaultCase'
                          ? operatorTask?.defaultCase ?? []
                          : operatorTask?.decisionCases?.[branchName] ?? []
                    if (branchTasks.length > 0) {
                      closeMenu()
                      setPendingDeleteBranch({
                        operatorReferenceName: payload.node.id,
                        operatorType: operatorTask?.type ?? TaskType.SWITCH,
                        branchName,
                        displayName: operatorTask?.type === TaskType.FORK_JOIN
                          ? `并行分支 ${Number(branchName) + 1}`
                          : branchName === 'defaultCase' ? '默认分支' : branchName,
                        taskCount: branchTasks.length,
                      })
                      return
                    }
                    deleteOperatorBranch(payload.node.id, operatorTask?.type ?? TaskType.SWITCH, branchName)
                  }}
                  onAddSwitchCase={(event, node) => {
                    if (readOnly) return
                    event.preventDefault()
                    event.stopPropagation()
                    onWorkflowChange(
                      node.data?.task.type === TaskType.FORK_JOIN
                        ? addForkBranch(workflow, node.id)
                        : addSwitchCase(workflow, node.id),
                    )
                  }}
                  onAddDoWhileTask={(event, node) => {
                    if (readOnly) return
                    event.preventDefault()
                    event.stopPropagation()
                    setMenu({ x: event.clientX, y: event.clientY, target: { kind: 'doWhileBody', node } })
                  }}
                  onDeleteTask={(event, node) => {
                    if (readOnly) return
                    event.preventDefault()
                    event.stopPropagation()
                    const task = findNodeTask(node)
                    if (!task) return
                    closeMenu()
                    setPendingDeleteTask({
                      name: task.name || task.taskReferenceName,
                      taskReferenceName: task.taskReferenceName,
                    })
                  }}
                />
              }
              onLayoutChange={(nextLayout) => {
                if (nextLayout?.width != null && nextLayout?.height != null) {
                  setLayout({ ...nextLayout, width: nextLayout.width, height: nextLayout.height })
                }
              }}
            />
          </div>
        </div>
      </div>
    </div>
  )
}

function ConfirmChoiceDialog({
  title,
  message,
  cancelLabel,
  confirmLabel,
  onCancel,
  onConfirm,
}: {
  title: string
  message: ReactNode
  cancelLabel: string
  confirmLabel: string
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <div className="iflow-confirm-backdrop" role="presentation" onClick={onCancel}>
      <div
        className="iflow-confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="iflow-confirm-dialog-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="iflow-confirm-dialog-title">{title}</h2>
        <div className="iflow-confirm-content">
          <p>{message}</p>
        </div>
        <div className="iflow-confirm-actions">
          <button type="button" className="iflow-confirm-cancel" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button type="button" className="iflow-confirm-primary" onClick={onConfirm} autoFocus>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
