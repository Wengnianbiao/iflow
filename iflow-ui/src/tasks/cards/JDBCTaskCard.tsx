import { DatabaseIcon } from '@phosphor-icons/react'
import type { TaskCardContentProps } from '../taskPluginRegistry'
import { truncateCardText } from './utils'

export function JDBCTaskCard({ task }: TaskCardContentProps) {
  const inputParameters = task.inputParameters ?? {}
  const nestedRequest = inputParameters.jdbc_request
  const request = nestedRequest && typeof nestedRequest === 'object' && !Array.isArray(nestedRequest)
    ? nestedRequest as Record<string, unknown>
    : inputParameters
  const operation = String(request.operation ?? 'QUERY').toUpperCase()
  const sql = String(request.sql ?? '').replace(/\s+/g, ' ').trim()
  const vendor = String(request.vendor ?? '').toUpperCase().trim()
  const url = String(request.url ?? '').trim()
  const summary = [vendor, sql || url].filter(Boolean).join(' · ')

  return (
    <div className="iflow-card-content iflow-http-content" title={[url, sql].filter(Boolean).join('\n')}>
      <DatabaseIcon className="iflow-http-link-icon" size={18} />
      <span className={`iflow-method-badge jdbc-operation-${operation.toLowerCase()}`}>{operation}</span>
      <span className="iflow-uri">{summary ? truncateCardText(summary) : '-'}</span>
    </div>
  )
}
