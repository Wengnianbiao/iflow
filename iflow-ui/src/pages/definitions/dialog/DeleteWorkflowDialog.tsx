import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
} from '@mui/material'
import { deleteWorkflowDefinition } from '@/api/workflowDefinition'
import { useMessageContext } from '@/components/providers/messageContext'
import type { WorkflowDef } from '@/types/workflow'

type DeleteWorkflowDialogProps = {
  definition: WorkflowDef | null
  onClose: () => void
  onSuccess: () => void
}

export function DeleteWorkflowDialog({
  definition,
  onClose,
  onSuccess,
}: DeleteWorkflowDialogProps) {
  const { setMessage } = useMessageContext()
  const [submitting, setSubmitting] = useState(false)
  const [confirmName, setConfirmName] = useState('')

  useEffect(() => {
    if (definition) setConfirmName('')
  }, [definition])

  const nameMatched = Boolean(definition && confirmName === definition.name)

  const handleDelete = async () => {
    if (!definition || !nameMatched) return

    try {
      setSubmitting(true)
      await deleteWorkflowDefinition(definition.route, definition.version)
      onSuccess()
    } catch (reason) {
      setMessage({
        severity: 'error',
        text: reason instanceof Error ? reason.message : '删除工作流失败',
      })
    } finally {
      setSubmitting(false)
    }
  }

  const handleClose = () => {
    setConfirmName('')
    onClose()
  }

  return (
    <Dialog open={Boolean(definition)} fullWidth maxWidth="sm" onClose={handleClose}>
      <DialogTitle>删除确认</DialogTitle>
      <DialogContent>
        <Typography sx={{ mb: 2 }}>
          确定删除工作流{' '}
          <strong style={{ color: 'var(--iflow-color-danger)' }}>
            {definition?.name}
          </strong>{' '}
          （{definition?.route}）的版本 {definition?.version} 吗？此操作无法撤销。
        </Typography>
        <Typography sx={{ mb: 2 }}>
          请输入工作流名称 <strong>{definition?.name}</strong> 以确认。
        </Typography>
        <TextField
          autoFocus
          fullWidth
          label="工作流名称"
          slotProps={{ inputLabel: { shrink: true } }}
          spellCheck={false}
          value={confirmName}
          onChange={(event) => setConfirmName(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && nameMatched) void handleDelete()
          }}
        />
        <Alert severity="warning" sx={{ mt: 2 }}>
          删除后无法恢复，请确认当前版本不再使用。
        </Alert>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>取消</Button>
        <Button
          color="error"
          variant="contained"
          disabled={!nameMatched || submitting}
          startIcon={submitting ? <CircularProgress color="inherit" size={16} /> : undefined}
          onClick={() => void handleDelete()}
        >
          确认删除
        </Button>
      </DialogActions>
    </Dialog>
  )
}
