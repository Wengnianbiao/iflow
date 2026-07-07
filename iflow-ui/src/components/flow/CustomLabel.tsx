import type { FunctionComponent } from 'react'
import type { EdgeChildProps, LabelProps } from 'reaflow'
import { EDGE_SPACING } from '@/flow/conductorMapper'
import { conductorTheme } from '@/flow/conductorTheme'

const HORIZONTAL_PADDING = 10

interface CustomLabelProps extends LabelProps {
  edgeChildProps: EdgeChildProps
}

export const CustomLabel: FunctionComponent<Partial<CustomLabelProps>> = ({
  text = '',
  x = 0,
  y = 0,
  originalText = '',
  edgeChildProps,
  ...labelProps
}) => {
  const labelPropsWidth = labelProps?.width || 0
  const centeredX = x + labelPropsWidth
  const isDefault = Boolean(edgeChildProps?.edge.data?.isDefault)

  const labelSize = {
    width: labelPropsWidth * 2 + HORIZONTAL_PADDING * 2,
    height: 30,
    x: centeredX,
    y,
  }

  return (
    <g transform={`translate(${labelSize.x - EDGE_SPACING / 2}, ${labelSize.y})`}>
      <foreignObject style={{ overflow: 'visible' }} width={EDGE_SPACING} height={labelSize.height}>
        <div
          title={originalText}
          className="conductor-edge-label"
          style={{
            minHeight: labelSize.height,
            lineHeight: `${labelSize.height}px`,
            backgroundColor: isDefault
              ? conductorTheme.decisionOperator.caseLabel.defaultCaseBackground
              : conductorTheme.decisionOperator.caseLabel.background,
            width: EDGE_SPACING,
          }}
          onClick={(event) => {
            event.preventDefault()
            event.stopPropagation()
          }}
        >
          {text}
        </div>
      </foreignObject>
    </g>
  )
}
