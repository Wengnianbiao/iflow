import { Helmet } from 'react-helmet'
import { SectionContainer } from '@/components/layout/SectionContainer'
import { SectionHeader } from '@/components/layout/SectionHeader'
import BasicSearch from './workflowSearchComponents/BasicSearch'

/** Mirrors Conductor's WorkflowSearch entry: page chrome and search composition. */
export default function WorkflowSearch() {
  return (
    <>
      <Helmet>
        <title>工作流执行记录</title>
      </Helmet>
      <SectionHeader title="工作流执行记录" />
      <SectionContainer>
        <BasicSearch />
      </SectionContainer>
    </>
  )
}
