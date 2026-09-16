import { InlineHighlightedCode } from '@/components/flow/InlineScriptCode'
import type { TaskCardContentProps } from '../taskPluginRegistry'

export function JDBCTaskCard({ task }: TaskCardContentProps) {
  const inputParameters = task.inputParameters ?? {}
  const sqlTemplate = String(inputParameters.sqlTemplate ?? '').trim()

  return (
    <div className="iflow-card-content iflow-jdbc-content">
      <code className="iflow-jdbc-sql-preview language-sql">
        <InlineHighlightedCode code={sqlTemplate || '-'} format={false} language="sql" />
      </code>
    </div>
  )
}
