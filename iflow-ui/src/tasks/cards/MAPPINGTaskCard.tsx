import { InlineHighlightedCode } from '@/components/flow/InlineScriptCode'
import type { TaskCardContentProps } from '../taskPluginRegistry'

export function MAPPINGTaskCard({ task }: TaskCardContentProps) {
  const paramsConfig = Array.isArray(task.inputParameters?.paramsConfig)
    ? task.inputParameters.paramsConfig
    : []
  const mappingJson = JSON.stringify(paramsConfig, null, 2)
    .split('\n')
    .map((line) => line.trim())
    .join(' ')

  return (
    <code className="iflow-card-content iflow-inline-content language-json">
      <InlineHighlightedCode code={mappingJson} format={false} />
    </code>
  )
}
