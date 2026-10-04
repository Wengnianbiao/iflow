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

export function normalizeJoinEdgeSections(
  sections: FlowEdgeSections | undefined,
): FlowEdgeSections | undefined {
  if (!sections?.length) return sections

  const [section, ...rest] = sections
  const { startPoint, endPoint, bendPoints } = section
  if (!startPoint || !endPoint || !bendPoints?.length || endPoint.y <= startPoint.y) return sections

  // Post-compaction can move a later bend above an earlier one. Pull the
  // detour up to that bend, keeping the horizontal route and port alignment.
  let nextY = endPoint.y
  let changed = false
  const compactBendPoints = bendPoints.map((point) => ({ ...point }))
  for (let index = compactBendPoints.length - 1; index >= 0; index -= 1) {
    const point = compactBendPoints[index]
    const y = Math.max(startPoint.y, Math.min(point.y, nextY))
    changed ||= y !== point.y
    point.y = y
    nextY = y
  }
  if (!changed) return sections

  const distinctBendPoints = compactBendPoints.filter((point, index) => {
    const previous = index === 0 ? startPoint : compactBendPoints[index - 1]
    return point.x !== previous.x || point.y !== previous.y
  })
  return [{ ...section, bendPoints: distinctBendPoints }, ...rest]
}
