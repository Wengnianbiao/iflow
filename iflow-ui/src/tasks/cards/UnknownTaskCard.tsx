import type { TaskCardContentProps } from '../taskPluginRegistry'
import { cardText, truncateCardText } from './utils'

export function UnknownTaskCard({ task }: TaskCardContentProps) {
  const inputParameters = task.inputParameters ?? {}
  const candidates: Array<[string, unknown]> = [
    ['topic', inputParameters.topic],
    ['event', inputParameters.eventType ?? inputParameters.eventName],
    ['workflow', inputParameters.name],
    ['duration', inputParameters.duration],
  ]
  const values = candidates.filter(([, value]) => value != null && value !== '')

  if (!values.length) return null
  return (
    <div className="iflow-card-content iflow-generic-content">
      {values.slice(0, 2).map(([label, value]) => (
        <div key={label}>
          <span>{label}</span>
          <strong>{truncateCardText(cardText(value), 80)}</strong>
        </div>
      ))}
    </div>
  )
}
