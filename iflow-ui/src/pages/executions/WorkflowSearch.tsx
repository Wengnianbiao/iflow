import { Helmet } from 'react-helmet'
import { Button } from '@mui/material'
import { PlayCircleIcon } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
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
      <SectionHeader
        title="工作流执行记录"
        actions={(
          <Button component={Link} to="/runWorkflow" startIcon={<PlayCircleIcon size={18} />}>
            执行工作流
          </Button>
        )}
      />
      <SectionContainer>
        <BasicSearch />
      </SectionContainer>
    </>
  )
}
