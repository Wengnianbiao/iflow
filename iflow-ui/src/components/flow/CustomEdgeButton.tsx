import type { FunctionComponent, MouseEvent } from 'react'
import { MinusIcon, PlusIcon } from '@phosphor-icons/react'
import type { PortChildProps } from 'reaflow'
import { BOTTOM_PORT_MARGIN } from '@/flow/conductorMapper'

type CustomEdgeButtonProps = PortChildProps & {
  size?: number
  hidden?: boolean
  variant?: 'ADD' | 'DELETE' | 'ADD_DELETE'
  onDeleteClick?: (event: MouseEvent<HTMLDivElement>) => void
  onClick?: (event: MouseEvent<HTMLDivElement>) => void
  nodeId: string
  activeEdgeId?: string
  actionTitle?: string
}

export const CustomEdgeButton: FunctionComponent<CustomEdgeButtonProps> = ({
  activeEdgeId,
  x,
  y,
  size = 20,
  hidden = true,
  variant = 'ADD',
  onClick = () => undefined,
  onDeleteClick = () => undefined,
  nodeId,
  port,
  actionTitle,
}) => {
  if (hidden) return null

  const half = size / 2
  const translateX = x - half
  const translateY = y - (half + (port.side === 'SOUTH' ? BOTTOM_PORT_MARGIN : 0))
  const active = activeEdgeId === port.id

  return (
    <g transform={`translate(${translateX}, ${translateY})`}>
      <foreignObject
        style={{ overflow: 'visible', cursor: 'pointer', pointerEvents: 'all' }}
        width={size + 22}
        height={size + 22}
        onClick={(event) => {
          event.preventDefault()
          event.stopPropagation()
          onClick(event as unknown as MouseEvent<HTMLDivElement>)
        }}
      >
        {variant === 'ADD_DELETE' ? (
          <div
            className={`conductor-edge-button split ${active ? 'active' : ''}`}
            style={{
              width: `${size * 2 + 10}px`,
              height: `${size}px`,
              marginLeft: `-${size / 2 + 5}px`,
              borderRadius: `${size}px`,
            }}
            data-node-id={nodeId}
          >
            <div
              className="conductor-edge-button-action"
              id={`ADD-${port.id}`}
              onClick={(event) => {
                event.preventDefault()
                event.stopPropagation()
                onClick(event)
              }}
            >
              <PlusIcon size={14} weight="bold" />
            </div>
            <div
              className="conductor-edge-button-action delete"
              id={`DELETE-${port.id}`}
              onClick={(event) => {
                event.preventDefault()
                event.stopPropagation()
                onDeleteClick(event)
              }}
            >
              <MinusIcon size={14} weight="bold" />
            </div>
          </div>
        ) : (
          <div
            className={`conductor-edge-button ${active ? 'active' : ''}`}
            style={{
              width: `${size}px`,
              height: `${size}px`,
              borderRadius: `${size}px`,
            }}
            id={`${variant}-${port.id}`}
            data-node-id={nodeId}
            title={actionTitle}
            aria-label={actionTitle}
            onClick={(event) => {
              event.preventDefault()
              event.stopPropagation()
              onClick(event)
            }}
          >
            {variant === 'ADD' ? <PlusIcon size={14} weight="bold" /> : <MinusIcon size={14} weight="bold" />}
          </div>
        )}
      </foreignObject>
    </g>
  )
}

export default CustomEdgeButton
