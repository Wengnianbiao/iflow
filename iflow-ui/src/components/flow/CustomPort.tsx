import type { MouseEvent } from 'react'
import { Port, type NodeData, type PortData, type PortProps } from 'reaflow'
import type { NodeTaskData } from '@/flow/conductorMapper'
import { getTaskFlowKind } from '@/tasks'
import CustomEdgeButton from './CustomEdgeButton'

type PortEventPayload = {
  port: { id?: string; properties?: PortData }
  node: NodeData<NodeTaskData>
}

interface CustomPortProps extends Omit<Partial<PortProps>, 'onClick'> {
  nodeProperties?: NodeData<NodeTaskData>
  activeEdgeId?: string
  onClick?: (event: MouseEvent<HTMLDivElement>, payload: PortEventPayload) => void
  onDeleteClick?: (event: MouseEvent<HTMLDivElement>, payload: PortEventPayload) => void
  readOnly?: boolean
}

const CustomPort = ({
  onClick = () => undefined,
  onDeleteClick = () => undefined,
  nodeProperties,
  activeEdgeId,
  readOnly = false,
  ...restProps
}: CustomPortProps) => {
  const taskType = nodeProperties?.data?.task.type
  const flowKind = getTaskFlowKind(taskType ?? '')
  const portSide = restProps.properties?.side
  const portVariant =
    (flowKind === 'switch' || flowKind === 'fork') && portSide === 'SOUTH'
      ? 'ADD_DELETE'
      : 'ADD'
  const isEndTerminal = nodeProperties?.data?.task.name === 'End' || nodeProperties?.id === '__end__'
  const actionTitle = portSide === 'NORTH'
    ? '在当前任务前添加任务'
    : flowKind === 'do_while'
      ? '在循环后添加任务'
      : undefined

  return (
    <Port {...restProps} style={{ display: 'none' }}>
      {(event) => {
        const currentPort = {
          id: event.port?.id ?? restProps.id,
          properties: event.port ?? (restProps.properties as PortData | undefined),
        }
        return !readOnly && !isEndTerminal && nodeProperties ? (
          <CustomEdgeButton
            {...event}
            variant={portVariant}
            hidden={false}
            onClick={(clkEvent) => onClick(clkEvent, { port: currentPort, node: nodeProperties })}
            onDeleteClick={(clkEvent) => onDeleteClick(clkEvent, { port: currentPort, node: nodeProperties })}
            nodeId={nodeProperties.id}
            activeEdgeId={activeEdgeId}
            actionTitle={actionTitle}
          />
        ) : null
      }}
    </Port>
  )
}

export default CustomPort
