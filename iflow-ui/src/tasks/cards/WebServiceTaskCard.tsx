import { LinkIcon } from '@phosphor-icons/react'
import type { TaskCardContentProps } from '../taskPluginRegistry'

export function WebServiceTaskCard({ task }: TaskCardContentProps) {
  const uri = String(task.inputParameters?.uri ?? '')

  return (
    <div className="iflow-card-content iflow-http-content">
      <LinkIcon className="iflow-http-link-icon" size={18} />
      <span className="iflow-method-badge">SOAP</span>
      <span className="iflow-uri">{uri || '-'}</span>
    </div>
  )
}
