import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type MouseEvent as ReactMouseEvent,
} from 'react'
import { useSelector } from '@xstate/react'
import { EditorPanel } from './EditorPanel/EditorPanel'
import { GraphPanel } from './GraphPanel'
import type { WorkflowDefinitionActor } from './state'

const MIN_CANVAS_WIDTH = 400
const MIN_INSPECTOR_WIDTH = 150

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

export function WorkflowWorkspace({ actor }: { actor: WorkflowDefinitionActor }) {
  const state = useSelector(actor, (snapshot) => snapshot)
  const inspectorOpen = !state.matches('ready.rightPanel.closed')
  const inspectorWidth = state.context.inspectorWidth
  const [resizingInspector, setResizingInspector] = useState(false)
  const resizeRef = useRef<{ containerLeft: number; containerWidth: number } | null>(null)

  const resizeInspectorFromClientX = useCallback((clientX: number) => {
    const resizeState = resizeRef.current
    if (!resizeState) return
    const leftWidth = clientX - resizeState.containerLeft
    const rightWidth = resizeState.containerWidth - leftWidth
    const minRightPercent = (MIN_INSPECTOR_WIDTH / resizeState.containerWidth) * 100
    const maxRightPercent = 100 - (MIN_CANVAS_WIDTH / resizeState.containerWidth) * 100
    actor.send({
      type: 'RESIZE_PANEL',
      width: clamp(
        (rightWidth / resizeState.containerWidth) * 100,
        minRightPercent,
        maxRightPercent,
      ),
    })
  }, [actor])

  useEffect(() => {
    if (!resizingInspector) return

    const handleMouseMove = (event: MouseEvent) => {
      event.preventDefault()
      resizeInspectorFromClientX(event.clientX)
    }
    const handleMouseUp = () => {
      resizeRef.current = null
      setResizingInspector(false)
    }

    document.addEventListener('mousemove', handleMouseMove, true)
    document.addEventListener('mouseup', handleMouseUp, true)
    return () => {
      document.removeEventListener('mousemove', handleMouseMove, true)
      document.removeEventListener('mouseup', handleMouseUp, true)
    }
  }, [resizeInspectorFromClientX, resizingInspector])

  const startInspectorResize = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (!inspectorOpen) return
    const container = event.currentTarget.parentElement
    if (!container) return
    event.preventDefault()
    const rect = container.getBoundingClientRect()
    resizeRef.current = {
      containerLeft: rect.left,
      containerWidth: rect.width,
    }
    setResizingInspector(true)
    resizeInspectorFromClientX(event.clientX)
  }

  const mainStyle = inspectorOpen
    ? ({ '--workflow-inspector-width': `${inspectorWidth}%` } as CSSProperties)
    : undefined

  return (
    <main
      className={`workflow-main ${inspectorOpen ? '' : 'inspector-collapsed'} ${resizingInspector ? 'resizing-panel' : ''}`}
      style={mainStyle}
      data-testid="workflow-definition-container"
    >
      <GraphPanel actor={actor} />
      {inspectorOpen ? (
        <>
          <div
            id="editor-panel-resize-line"
            className={`workflow-panel-resizer ${resizingInspector ? 'active' : ''}`}
            onMouseDown={startInspectorResize}
          />
          <EditorPanel actor={actor} />
        </>
      ) : null}
    </main>
  )
}
