import { Helmet } from 'react-helmet'
import { useSearchParams } from 'react-router-dom'
import { SectionContainer } from '@/components/layout/SectionContainer'
import { SectionHeader } from '@/components/layout/SectionHeader'
import { WorkflowListActions } from '@/pages/definitions/WorkflowListActions'
import BasicSearch from './workflowSearchComponents/BasicSearch'

/** Mirrors Conductor's WorkflowSearch entry: page chrome and search composition. */
export default function WorkflowSearch() {
  const [searchParams] = useSearchParams()
  const title = searchParams.get('view') === 'task' ? '工作流任务执行记录' : '工作流执行记录'
  return (
    <>
      <Helmet>
        <title>{title}</title>
      </Helmet>
      <SectionHeader
        title={title}
        actions={<WorkflowListActions />}
      />
      <SectionContainer>
        <BasicSearch />
      </SectionContainer>
    </>
  )
}
