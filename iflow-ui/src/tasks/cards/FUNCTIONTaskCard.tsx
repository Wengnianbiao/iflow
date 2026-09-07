import type { TaskCardContentProps } from '../taskPluginRegistry'
import { cardText, truncateCardText } from './utils'

const FUNCTION_LABELS: Record<string, string> = {
  JSON_PARSE: 'JSON.parse',
  XML_PARSE: 'XML.parse',
}

export function FUNCTIONTaskCard({ task }: TaskCardContentProps) {
  const inputParameters = task.inputParameters ?? {}
  const functionName = String(inputParameters.function ?? 'JSON_PARSE')
  const value = truncateCardText(cardText(inputParameters.value), 80)

  return (
    <div className="iflow-card-content iflow-generic-content">
      <div>
        <span>函数</span>
        <strong>{FUNCTION_LABELS[functionName] ?? functionName}</strong>
      </div>
      <div>
        <span>输入</span>
        <strong>{value || '-'}</strong>
      </div>
    </div>
  )
}
