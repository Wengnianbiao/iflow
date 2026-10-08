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

function nextCopyRoute(route: string, definitions: WorkflowDef[]) {
  const existingRoutes = new Set(definitions.map((definition) => definition.route))
  const baseRoute = `${route}_copy`
  if (!existingRoutes.has(baseRoute)) return baseRoute

  let index = 2
  while (existingRoutes.has(`${baseRoute}_${index}`)) index += 1
  return `${baseRoute}_${index}`
}

export function CloneWorkflowDialog({
  selectedWorkflow: definition,
  workflowList: definitions,
  onClose,
  onSuccess,
}: CloneWorkflowDialogProps) {
  const { setMessage } = useMessageContext()
  const [route, setRoute] = useState('')
  const [routeError, setRouteError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!definition) return
    setRoute(nextCopyRoute(definition.route, definitions))
    setRouteError('')
  }, [definition, definitions])

  const handleSubmit = async (event?: FormEvent) => {
    event?.preventDefault()
    if (!definition) return

    const normalizedRoute = route.trim()
    if (!normalizedRoute) {
      setRouteError('请输入路由')
      return
    }
    if (definitions.some((item) => item.route === normalizedRoute)) {
      setRouteError(`路由已存在：${normalizedRoute}`)
      return
    }

    try {
      setSubmitting(true)
      await registerWorkflowDefinition({
        ...definition,
        route: normalizedRoute,
        version: 1,
        name: definition.name
          ? `${definition.name} 副本`
          : normalizedRoute,
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
          复制 <strong>{definition?.name?.trim() || definition?.route}</strong>{' '}
          的最新定义，副本版本将从 1 开始。
        </Typography>
        <form id="clone-workflow-form" onSubmit={(event) => void handleSubmit(event)}>
          <TextField
            autoFocus
            fullWidth
            label="路由"
            error={Boolean(routeError)}
            helperText={routeError}
            spellCheck={false}
            value={route}
            onChange={(event) => {
              setRoute(event.target.value)
              if (routeError) setRouteError('')
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
