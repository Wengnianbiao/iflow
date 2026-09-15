import type { TaskCardContentProps } from '../taskPluginRegistry'

export function JDBCTaskCard({ task }: TaskCardContentProps) {
  const inputParameters = task.inputParameters ?? {}
  const vendor = String(inputParameters.vendor ?? '').toUpperCase().trim()

  return (
    <div className="iflow-card-content iflow-jdbc-content">
      <span className="iflow-method-badge">{vendor || '-'}</span>
    </div>
  )
}
