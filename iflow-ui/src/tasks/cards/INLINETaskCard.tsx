import { InlineHighlightedCode } from '@/components/flow/InlineScriptCode'
import type { TaskCardContentProps } from '../taskPluginRegistry'

export function INLINETaskCard({ task }: TaskCardContentProps) {
  const expression = String(task.inputParameters?.expression ?? '')

  return (
    <code className="iflow-card-content iflow-inline-content language-js">
      <InlineHighlightedCode code={expression} />
    </code>
  )
}
