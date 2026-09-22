import { useEffect, useState, type FormEvent } from 'react'
import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
} from '@mui/material'
import { registerWorkflowDefinition } from '@/api/workflowDefinition'
import { useMessageContext } from '@/components/providers/messageContext'
import type { WorkflowDef } from '@/types/workflow'

type CloneWorkflowDialogProps = {
  selectedWorkflow: WorkflowDef | null
  workflowList: WorkflowDef[]
  onClose: () => void
  onSuccess: () => void
}

function nextCopyName(name: string, definitions: WorkflowDef[]) {
  const existingNames = new Set(definitions.map((definition) => definition.name))
  const baseName = `${name}_copy`
  if (!existingNames.has(baseName)) return baseName

  let index = 2
  while (existingNames.has(`${baseName}_${index}`)) index += 1
  return `${baseName}_${index}`
}

export function CloneWorkflowDialog({
  selectedWorkflow: definition,
  workflowList: definitions,
  onClose,
  onSuccess,
}: CloneWorkflowDialogProps) {
  const { setMessage } = useMessageContext()
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!definition) return
    setName(nextCopyName(definition.name, definitions))
    setNameError('')
  }, [definition, definitions])

  const handleSubmit = async (event?: FormEvent) => {
    event?.preventDefault()
    if (!definition) return

    const normalizedName = name.trim()
    if (!normalizedName) {
      setNameError('请输入工作流名称')
      return
    }
    if (definitions.some((item) => item.name === normalizedName)) {
      setNameError(`工作流名称已存在：${normalizedName}`)
      return
    }

    try {
      setSubmitting(true)
      await registerWorkflowDefinition({
        ...definition,
        name: normalizedName,
        version: 1,
        displayName: definition.displayName
          ? `${definition.displayName} 副本`
          : normalizedName,
      })
      onSuccess()
    } catch (reason) {
      setMessage({
        severity: 'error',
        text: reason instanceof Error ? reason.message : '复制工作流失败',
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={Boolean(definition)} fullWidth maxWidth="sm" onClose={onClose}>
      <DialogTitle>复制工作流</DialogTitle>
      <DialogContent>
        <Typography sx={{ mb: 2 }}>
          复制 <strong>{definition?.displayName?.trim() || definition?.name}</strong>{' '}
          的最新定义，副本版本将从 1 开始。
        </Typography>
        <form id="clone-workflow-form" onSubmit={(event) => void handleSubmit(event)}>
          <TextField
            autoFocus
            fullWidth
            label="工作流名称"
            error={Boolean(nameError)}
            helperText={nameError}
            spellCheck={false}
            value={name}
            onChange={(event) => {
              setName(event.target.value)
              if (nameError) setNameError('')
            }}
          />
        </form>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>取消</Button>
        <Button
          variant="contained"
          type="submit"
          form="clone-workflow-form"
          disabled={submitting}
          startIcon={submitting ? <CircularProgress color="inherit" size={16} /> : undefined}
        >
          确认复制
        </Button>
      </DialogActions>
    </Dialog>
  )
}
