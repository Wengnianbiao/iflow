import { useState } from 'react'
import { Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
import { updateWorkflowTags } from '@/api/workflowDefinition'
import { WorkflowTagsInput } from '@/components/ui/WorkflowTagsInput'
import type { WorkflowDef } from '@/types/workflow'
import { normalizeWorkflowTags } from '@/utils/workflowTags'

export function EditWorkflowTagsDialog({
  definition,
  options,
  onClose,
  onSuccess,
}: {
  definition: WorkflowDef
  options: string[]
  onClose: () => void
  onSuccess: () => void
}) {
  const originalTags = normalizeWorkflowTags(definition.tags)
  const [tags, setTags] = useState(originalTags)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const unchanged = tags.length === originalTags.length && tags.every((tag) => originalTags.includes(tag))

  const save = async () => {
    setSubmitting(true)
    setError('')
    try {
      await updateWorkflowTags(definition.route, definition.version, tags)
      onSuccess()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '保存标签失败')
      setSubmitting(false)
    }
  }

  return (
    <Dialog open fullWidth maxWidth="sm" onClose={submitting ? undefined : onClose}>
      <DialogTitle>编辑标签</DialogTitle>
      <DialogContent>
        <Typography sx={{ mt: 1, mb: 3 }}>
          {definition.name} · 版本 {definition.version}
        </Typography>
        {error ? <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert> : null}
        <WorkflowTagsInput value={tags} options={options} onChange={setTags} disabled={submitting} />
      </DialogContent>
      <DialogActions>
        <Button disabled={submitting} onClick={onClose}>取消</Button>
        <Button variant="contained" disabled={unchanged || submitting} onClick={() => void save()}
          startIcon={submitting ? <CircularProgress color="inherit" size={16} /> : undefined}>
          保存
        </Button>
      </DialogActions>
    </Dialog>
  )
}
