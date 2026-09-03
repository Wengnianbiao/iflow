import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type MouseEvent as ReactMouseEvent,
  type ReactNode,
} from 'react'
import { CaretDoubleLeftIcon } from '@phosphor-icons/react'
import styles from './TwoPanesDivider.module.css'

const DEFAULT_MIN_LEFT_WIDTH = 400
const DEFAULT_MIN_RIGHT_WIDTH = 150

type DividerStyle = CSSProperties & {
  '--two-panes-right-width'?: string
  '--two-panes-left-min-width'?: string
  '--two-panes-right-min-width'?: string
}

export type TwoPanesDividerProps = {
  leftPanelContent: ReactNode
  rightPanelContent: ReactNode
  leftPanelExpanded?: boolean
  setLeftPanelExpanded: (expanded: boolean) => void
  rightPanelWidth?: number
  setRightPanelWidth?: (width: number) => void
  minLeftWidth?: number
  minRightWidth?: number
  openPanelLabel?: string
  testId?: string
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

export default function TwoPanesDivider({
  leftPanelContent,
  rightPanelContent,
  leftPanelExpanded = false,
  setLeftPanelExpanded,
  rightPanelWidth = 50,
  setRightPanelWidth,
  minLeftWidth = DEFAULT_MIN_LEFT_WIDTH,
  minRightWidth = DEFAULT_MIN_RIGHT_WIDTH,
  openPanelLabel = '打开面板',
  testId,
}: TwoPanesDividerProps) {
  const [resizing, setResizing] = useState(false)
  const containerRef = useRef<HTMLDivElement | null>(null)

  const widthBounds = useCallback(() => {
    const containerWidth = containerRef.current?.getBoundingClientRect().width ?? 0
    if (containerWidth <= 0) return { min: 0, max: 100 }
    return {
      min: (minRightWidth / containerWidth) * 100,
      max: 100 - (minLeftWidth / containerWidth) * 100,
    }
  }, [minLeftWidth, minRightWidth])

  const resizeFromClientX = useCallback((clientX: number) => {
    const rect = containerRef.current?.getBoundingClientRect()
    if (!rect?.width || !setRightPanelWidth) return
    const { min, max } = widthBounds()
    const rightWidth = ((rect.right - clientX) / rect.width) * 100
    setRightPanelWidth(clamp(rightWidth, min, max))
  }, [setRightPanelWidth, widthBounds])

  useEffect(() => {
    if (!resizing) return

    const handleMouseMove = (event: MouseEvent) => {
      event.preventDefault()
      resizeFromClientX(event.clientX)
    }
    const handleMouseUp = () => setResizing(false)

    document.addEventListener('mousemove', handleMouseMove, true)
    document.addEventListener('mouseup', handleMouseUp, true)
    return () => {
      document.removeEventListener('mousemove', handleMouseMove, true)
      document.removeEventListener('mouseup', handleMouseUp, true)
    }
  }, [resizeFromClientX, resizing])

  const startResize = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (leftPanelExpanded || !setRightPanelWidth) return
    event.preventDefault()
    setResizing(true)
    resizeFromClientX(event.clientX)
  }

  const resizeWithKeyboard = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!setRightPanelWidth || !['ArrowLeft', 'ArrowRight'].includes(event.key)) return
    event.preventDefault()
    const { min, max } = widthBounds()
    const delta = event.key === 'ArrowLeft' ? 2 : -2
    setRightPanelWidth(clamp(rightPanelWidth + delta, min, max))
  }

  const rootStyle = {
    '--two-panes-right-width': `${rightPanelWidth}%`,
    '--two-panes-left-min-width': `${minLeftWidth}px`,
    '--two-panes-right-min-width': `${minRightWidth}px`,
  } as DividerStyle

  return (
    <div
      ref={containerRef}
      className={`${styles.root} ${leftPanelExpanded ? styles.leftPanelExpanded : ''} ${resizing ? styles.resizing : ''}`}
      style={rootStyle}
      data-testid={testId}
    >
      <div className={styles.leftPanel}>
        {leftPanelContent}
        {leftPanelExpanded ? (
          <button
            type="button"
            className={styles.openPanelButton}
            title="展开属性面板"
            onClick={() => setLeftPanelExpanded(false)}
          >
            <CaretDoubleLeftIcon size={16} />
            {openPanelLabel}
          </button>
        ) : null}
      </div>

      {!leftPanelExpanded ? (
        <>
          <div
            id="editor-panel-resize-line"
            className={`${styles.resizer} ${resizing ? styles.active : ''}`}
            role="separator"
            aria-label="调整属性面板宽度"
            aria-orientation="vertical"
            aria-valuemin={Math.round(widthBounds().min)}
            aria-valuemax={Math.round(widthBounds().max)}
            aria-valuenow={Math.round(rightPanelWidth)}
            tabIndex={setRightPanelWidth ? 0 : -1}
            onMouseDown={startResize}
            onKeyDown={resizeWithKeyboard}
          />
          <div className={styles.rightPanel}>{rightPanelContent}</div>
        </>
      ) : null}
    </div>
  )
}
