import { Helmet } from 'react-helmet'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, LinearProgress } from '@mui/material'
import { ArrowLeftIcon } from '@phosphor-icons/react'
import {
  getWorkflowSchedule, isValidScheduleName, saveWorkflowSchedule, type ScheduleConfiguration,
} from '@/api/workflowSchedule'
import { SectionContainer } from '@/components/layout/SectionContainer'
import { SectionHeader } from '@/components/layout/SectionHeader'
import { useMessageContext } from '@/components/providers/messageContext'
import { ScheduleForm } from './ScheduleForm'

export function ScheduleEditorPage() {
  const navigate = useNavigate()
  const { name: nameParam } = useParams()
  const validName = nameParam != null && isValidScheduleName(nameParam)
  const queryClient = useQueryClient()
  const { setMessage } = useMessageContext()
  const schedule = useQuery({
    queryKey: ['workflowSchedules', nameParam],
    queryFn: ({ signal }) => getWorkflowSchedule(nameParam!, signal),
    enabled: validName,
    retry: false,
  })
  const save = useMutation({
    mutationFn: (configuration: ScheduleConfiguration) => saveWorkflowSchedule(configuration, nameParam),
    onSuccess: (saved) => {
      queryClient.setQueryData(['workflowSchedules', saved.name], saved)
      void queryClient.invalidateQueries({ queryKey: ['workflowSchedules'], exact: true })
      setMessage({ severity: 'success', text: '定时计划已保存' })
      navigate('/scheduleDef')
    },
  })

  const title = nameParam == null ? '新建定时计划' : '编辑定时计划'
  const loadError = nameParam != null && !validName ? '计划名称无效' : schedule.error?.message

  return (
    <>
      <Helmet><title>{title}</title></Helmet>
      <SectionHeader title={title} actions={
        <Button component={RouterLink} to="/scheduleDef" disabled={save.isPending}
          startIcon={<ArrowLeftIcon size={18} />}>返回定时计划</Button>
      } />
      <SectionContainer>
        {loadError ? <Alert severity="error">{loadError}</Alert> : (
          nameParam == null || schedule.data ? (
            <ScheduleForm
              key={nameParam ?? 'new'}
              schedule={schedule.data ?? null}
              onSave={(configuration) => save.mutate(configuration)}
              onClose={() => navigate('/scheduleDef')}
              saving={save.isPending}
              error={save.error?.message ?? null}
            />
          ) : <LinearProgress aria-label="正在加载定时计划" />
        )}
      </SectionContainer>
    </>
  )
}
