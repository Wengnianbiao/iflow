import { PlugsConnectedIcon } from '@phosphor-icons/react'
import type { TaskCardContentProps } from '../taskPluginRegistry'

export function SPITaskCard({ task }: TaskCardContentProps) {
  const remoteExtension = String(task.inputParameters?.remoteExtension ?? '').trim()

  return (
    <div className="iflow-card-content iflow-http-content">
      <PlugsConnectedIcon className="iflow-http-link-icon" size={18} />
      <span className="iflow-method-badge">SPI</span>
      <span className="iflow-uri">{remoteExtension || 'default'}</span>
    </div>
  )
}
