import type { MouseEvent as ReactMouseEvent } from 'react'
import { Node, type NodeData, type NodeProps, type PortData } from 'reaflow'
import type { NodeTaskData } from '@/flow/conductorMapper'
import CustomPort from './CustomPort'
import { TaskShape } from './TaskShape'

type ToggleTaskMenuPayload = {
  port: { properties?: PortData; id?: string }
  node: NodeData<NodeTaskData>
}

interface CustomNodeProps extends Omit<Partial<NodeProps<NodeTaskData>>, 'onClick'> {
  activeEdgeId?: string
  onClick?: (event: ReactMouseEvent<Element>, node: NodeData<NodeTaskData>) => void
  onToggleTaskMenu?: (event: ReactMouseEvent<HTMLDivElement>, payload: ToggleTaskMenuPayload) => void
  onDeleteBranch?: (event: ReactMouseEvent<HTMLDivElement>, payload: ToggleTaskMenuPayload) => void
  onAddSwitchCase?: (event: ReactMouseEvent<HTMLButtonElement>, node: NodeData<NodeTaskData>) => void
  onAddDoWhileTask?: (event: ReactMouseEvent<HTMLButtonElement>, node: NodeData<NodeTaskData>) => void
  onDeleteTask?: (event: ReactMouseEvent<HTMLButtonElement>, node: NodeData<NodeTaskData>) => void
  readOnly?: boolean
  displayDescription?: boolean
}

export const CustomNode = (nodeProps: CustomNodeProps) => {
  const {
    onClick = () => undefined,
    onToggleTaskMenu = () => undefined,
    onDeleteBranch = () => undefined,
    onAddSwitchCase = () => undefined,
    onAddDoWhileTask = () => undefined,
    onDeleteTask = () => undefined,
    readOnly = false,
    displayDescription = false,
    properties: nodeProperties,
    activeEdgeId,
  } = nodeProps
  const portsHidden = nodeProperties?.ports?.[0]?.hidden === true

  return (
    <Node
      {...nodeProps}
      onClick={() => null}
      label={<></>}
      style={{ stroke: 'none', fill: 'none' }}
      port={
        <CustomPort
          nodeProperties={nodeProperties as NodeData<NodeTaskData>}
          readOnly={readOnly}
          activeEdgeId={activeEdgeId}
          onClick={(event, { port, node }) => {
            onToggleTaskMenu(event, { port: { id: port.id, properties: port.properties as PortData }, node })
          }}
          onDeleteClick={(event, { port, node }) => {
            onDeleteBranch(event, { port: { id: port.id, properties: port.properties as PortData }, node })
          }}
        />
      }
    >
      {(event) => (
        <g>
          <foreignObject
            onClick={(clickEvent) => onClick(clickEvent, nodeProperties as NodeData<NodeTaskData>)}
            style={{ overflow: 'visible' }}
            height={event.height}
            width={event.width}
          >
            <div className="iflow-node-html" style={{ width: event.width, height: event.height, borderRadius: '10px' }}>
              <TaskShape
                portsVisible={!portsHidden}
                nodeData={event.node.data as NodeTaskData}
                width={event.width}
                height={event.height}
                nodeId={event.node.id}
                onAddSwitchCase={readOnly ? undefined : (clickEvent) => onAddSwitchCase(clickEvent, event.node as NodeData<NodeTaskData>)}
                onAddDoWhileTask={readOnly ? undefined : (clickEvent) => onAddDoWhileTask(clickEvent, event.node as NodeData<NodeTaskData>)}
                onDelete={readOnly ? undefined : (clickEvent) => onDeleteTask(clickEvent, event.node as NodeData<NodeTaskData>)}
                displayDescription={displayDescription}
              />
            </div>
          </foreignObject>
        </g>
      )}
    </Node>
  )
}
