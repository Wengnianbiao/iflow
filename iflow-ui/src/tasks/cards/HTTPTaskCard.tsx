import { LinkIcon } from '@phosphor-icons/react'
import type { TaskCardContentProps } from '../taskPluginRegistry'

export function HTTPTaskCard({ task }: TaskCardContentProps) {
  const inputParameters = task.inputParameters ?? {}
  const request = (inputParameters.http_request as Record<string, unknown> | undefined) ?? inputParameters
  const method = String(request.method ?? 'GET')
  const uri = String(request.uri ?? '')
  const clickable = method.toUpperCase() === 'GET' && /^https?:\/\//i.test(uri)

  return (
    <div className="iflow-card-content iflow-http-content">
      <LinkIcon className="iflow-http-link-icon" size={18} />
      <span className={`iflow-method-badge method-${method.toLowerCase()}`}>{method}</span>
      {clickable ? (
        <a className="iflow-uri" href={uri} target="_blank" rel="noreferrer" onClick={(event) => event.stopPropagation()}>
          {uri}
        </a>
      ) : (
        <span className="iflow-uri">{uri || '-'}</span>
      )}
    </div>
  )
}
