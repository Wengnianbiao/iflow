import { Chip } from '@mui/material'
import type { WorkflowExecutionStatus } from '@/types/workflowExecution'
import { WORKFLOW_STATUS_OPTIONS } from './searchState'

// Conductor StatusBadge / TagChip colors and typography.
const backgrounds: Record<WorkflowExecutionStatus, string> = {
  RUNNING: '#8DE0F9',
  COMPLETED: '#9FDCAA',
  FAILED: '#FBB4C6',
  TIMED_OUT: '#FBB4C6',
  TERMINATED: '#FBB4C6',
  INTERRUPTED: '#FBB4C6',
}

export default function WorkflowStatusBadge({ status }: { status: WorkflowExecutionStatus }) {
  return <Chip label={WORKFLOW_STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status}
    sx={{ backgroundColor: backgrounds[status], color: 'black', borderRadius: '100px', fontSize: 12, fontWeight: 400 }} />
}
