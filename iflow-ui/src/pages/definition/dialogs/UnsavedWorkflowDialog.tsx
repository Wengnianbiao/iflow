import CloseIcon from '@mui/icons-material/Close'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Typography } from '@mui/material'

type UnsavedWorkflowDialogProps = {
  open: boolean
  saving: boolean
  hasErrors: boolean
  saveError?: string
  onCancel: () => void
  onDiscard: () => void
  onSave: () => void
}

export function UnsavedWorkflowDialog({ open, saving, hasErrors, saveError, onCancel, onDiscard, onSave }: UnsavedWorkflowDialogProps) {
  return (
    <Dialog
      open={open}
      onClose={saving ? undefined : onCancel}
      disableEscapeKeyDown={saving}
      aria-labelledby="unsaved-workflow-title"
      aria-describedby="unsaved-workflow-message"
      sx={{ '& .MuiDialog-paper': { width: 448, maxWidth: 'calc(100% - 64px)', borderRadius: 2 } }}
    >
      <DialogTitle id="unsaved-workflow-title" sx={{ pr: 6, fontSize: 16 }}>
        工作流未保存
        <IconButton aria-label="关闭未保存提醒" onClick={onCancel} disabled={saving} sx={{ position: 'absolute', right: 12, top: 12, color: 'text.secondary' }}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>
      <DialogContent sx={{ pb: 3 }}>
        <Typography id="unsaved-workflow-message" variant="body2" sx={{ mt: 1 }}>
          {hasErrors ? '当前修改尚未通过校验，请继续编辑并修复后再保存。' : '当前工作流有未保存的修改，你希望如何处理？'}
        </Typography>
        {saveError ? <Alert severity="error" sx={{ mt: 2 }}>保存失败，修改已保留：{saveError}</Alert> : null}
      </DialogContent>
      <DialogActions sx={{ p: '16px 24px', borderTop: 1, borderColor: 'divider' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%', gap: 2, flexWrap: 'wrap' }}>
          <Button variant="outlined" onClick={onCancel} disabled={saving}>取消</Button>
          <Box sx={{ display: 'flex', gap: 1.5 }}>
            <Button variant="contained" color="error" onClick={onDiscard} disabled={saving}>放弃修改</Button>
            <Button variant="contained" onClick={hasErrors ? onCancel : onSave} disabled={saving}>
              {saving ? '保存中...' : hasErrors ? '继续编辑' : '保存并继续'}
            </Button>
          </Box>
        </Box>
      </DialogActions>
    </Dialog>
  )
}
