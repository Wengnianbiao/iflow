/** Compact along the flow direction after layering, including beside tall loop containers. */
export const compactFlowLayoutOptions = {
  'org.eclipse.elk.layered.compaction.postCompaction.strategy': 'LEFT',
  // Scanline constraints can stack adjacent branches with ports and large edge spacing.
  'org.eclipse.elk.layered.compaction.postCompaction.constraints': 'QUADRATIC',
}

type EdgePoint = { x: number; y: number }

export type FlowEdgeSections = Array<{
  id?: string
  startPoint?: EdgePoint
  endPoint?: EdgePoint
  bendPoints?: EdgePoint[]
}>

export function normalizeForkJoinEdgeSections(
  sections: FlowEdgeSections | undefined,
): FlowEdgeSections | undefined {
  if (!sections?.length) return sections

  const [section, ...rest] = sections
  const { startPoint, endPoint, bendPoints } = section
  if (!startPoint || !endPoint || !bendPoints?.length || endPoint.y <= startPoint.y) return sections

  let previousY = startPoint.y
  const validBendPoints = bendPoints.filter((point) => {
    const valid = point.y >= previousY && point.y <= endPoint.y
    if (valid) previousY = point.y
    return valid
  })
  if (validBendPoints.length === bendPoints.length) return sections

  const compactBendPoints = validBendPoints.length
    ? validBendPoints
    : [
        { x: startPoint.x, y: (startPoint.y + endPoint.y) / 2 },
        { x: endPoint.x, y: (startPoint.y + endPoint.y) / 2 },
      ]

  return [{ ...section, bendPoints: compactBendPoints }, ...rest]
}
