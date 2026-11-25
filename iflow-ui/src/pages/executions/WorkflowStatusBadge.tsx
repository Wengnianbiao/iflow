import { Chip } from '@mui/material'
import type { TaskExecutionStatus, WorkflowExecutionStatus } from '@/types/workflowExecution'
import { TASK_STATUS_OPTIONS, WORKFLOW_STATUS_OPTIONS } from './searchState'

const backgrounds: Record<WorkflowExecutionStatus | TaskExecutionStatus, string> = {
  RUNNING: '#8DE0F9',
  COMPLETED: '#9FDCAA',
  FAILED: '#FBB4C6',
  TIMED_OUT: '#FBB4C6',
  TERMINATED: '#FBB4C6',
  INTERRUPTED: '#FBB4C6',
  SCHEDULED: '#E0E0E0',
  IN_PROGRESS: '#8DE0F9',
  CANCELED: '#E0E0E0',
  FAILED_WITH_TERMINAL_ERROR: '#FBB4C6',
  COMPLETED_WITH_ERRORS: '#FFE3A3',
  SKIPPED: '#E0E0E0',
}

export default function WorkflowStatusBadge({ status }: { status: WorkflowExecutionStatus | TaskExecutionStatus }) {
  return <Chip label={[...WORKFLOW_STATUS_OPTIONS, ...TASK_STATUS_OPTIONS].find((option) => option.value === status)?.label ?? status}
    sx={{ backgroundColor: backgrounds[status], color: 'black', borderRadius: '100px', fontSize: 12, fontWeight: 400 }} />
}
