import { useMemo, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Alert, Autocomplete, Button, FormControlLabel, Grid, MenuItem, Paper,
  Stack, Switch, TextField,
} from '@mui/material'
import { listWorkflowVersions } from '@/api/workflowDefinition'
import { isValidScheduleName, type ScheduleConfiguration, type WorkflowSchedule } from '@/api/workflowSchedule'
import { useWorkflowDefs } from '@/utils/query'
import { browserZone, CronExpressionSection } from './CronExpressionSection'

type ScheduleFormProps = {
  schedule: WorkflowSchedule | null
  onClose: () => void
  onSave: (configuration: ScheduleConfiguration) => void
  saving: boolean
  error: string | null
}

export function ScheduleForm({ schedule, onClose, onSave, saving, error }: ScheduleFormProps) {
  const [name, setName] = useState(schedule?.name ?? '')
  const [displayName, setDisplayName] = useState(schedule?.displayName ?? '')
  const [cronExpression, setCronExpression] = useState(schedule?.cronExpression ?? '')
  const [zoneId, setZoneId] = useState(schedule?.zoneId ?? browserZone)
  const [enabled, setEnabled] = useState(schedule?.enabled ?? true)
  const [route, setRoute] = useState(schedule?.startWorkflowRequest.route ?? '')
  const [version, setVersion] = useState<number | null>(schedule?.startWorkflowRequest.version ?? null)
  const [inputText, setInputText] = useState(() => JSON.stringify(schedule?.startWorkflowRequest.input ?? {}, null, 2))
  const [inputError, setInputError] = useState('')

  const definitions = useWorkflowDefs({ refetchOnMount: 'always' })
  const versions = useQuery({
    queryKey: ['workflowVersions', route],
    queryFn: () => listWorkflowVersions(route),
    enabled: Boolean(route),
  })
  const workflows = useMemo(
    () => (definitions.data ?? []).map(({ route, name }) => ({ route, name })),
    [definitions.data],
  )
  const matchingWorkflow = workflows.find((workflow) => workflow.route === route)
  const missingWorkflow = Boolean(route && definitions.isSuccess && !matchingWorkflow)
  const selected = useMemo(
    () => matchingWorkflow ?? (route ? { route, name: route } : null),
    [matchingWorkflow, route],
  )
  const workflowOptions = selected && !matchingWorkflow ? [selected, ...workflows] : workflows
  const missingVersion = version != null && versions.isSuccess && !versions.data.includes(version)
  const versionOptions = [...new Set([
    ...(versions.data ?? []),
    ...(version == null ? [] : [version]),
  ])].sort((left, right) => right - left)
  const trimmedName = name.trim()
  const reservedName = trimmedName === 'preview' || trimmedName === 'new'
  const nameInvalid = trimmedName !== '' && !isValidScheduleName(trimmedName)
  const canSave = !saving && isValidScheduleName(trimmedName) && Boolean(displayName.trim())
    && Boolean(cronExpression.trim() && zoneId.trim() && route)
    && !missingWorkflow && !missingVersion

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!canSave) return

    let input: unknown
    try {
      input = JSON.parse(inputText.trim() || '{}')
    } catch {
      setInputError('请输入有效的 JSON')
      return
    }
    if (input === null || typeof input !== 'object' || Array.isArray(input)) {
      setInputError('工作流入参必须是 JSON 对象')
      return
    }

    setInputError('')
    onSave({
      name: trimmedName,
      displayName: displayName.trim(),
      cronExpression: cronExpression.trim(),
      zoneId: zoneId.trim(),
      enabled,
      startWorkflowRequest: { route, version, input: input as Record<string, unknown> },
    })
  }

  return (
    <Paper component="form" variant="outlined" onSubmit={submit} sx={{ maxWidth: 920, p: { xs: 2, sm: 3 } }}>
      <Stack spacing={3}>
        {error ? <Alert severity="error">{error}</Alert> : null}
        {definitions.isError ? (
          <Alert
            severity="error"
            action={<Button color="inherit" size="small" onClick={() => void definitions.refetch()}>重试</Button>}
          >
            {definitions.error.message}
          </Alert>
        ) : null}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 5 }}>
            <TextField
              autoFocus
              required
              fullWidth
              size="small"
              label="计划名称"
              value={name}
              disabled={saving}
              error={nameInvalid}
              helperText={reservedName ? '不能使用 preview 或 new' : '只能包含英文字母、数字和下划线'}
              slotProps={{ htmlInput: { maxLength: 255 } }}
              onChange={(event) => setName(event.target.value)}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 7 }}>
            <TextField
              required
              fullWidth
              size="small"
              label="展示名称"
              value={displayName}
              disabled={saving}
              slotProps={{ htmlInput: { maxLength: 255 } }}
              onChange={(event) => setDisplayName(event.target.value)}
            />
          </Grid>
        </Grid>
        <CronExpressionSection
          cronExpression={cronExpression} zoneId={zoneId} disabled={saving}
          onCronChange={setCronExpression} onZoneChange={setZoneId}
        />
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 9 }}>
            <Autocomplete
              openOnFocus
              size="small"
              options={workflowOptions}
              value={selected}
              disabled={saving}
              loading={definitions.isFetching}
              loadingText="加载工作流中…"
              noOptionsText="暂无可选工作流"
              openText="展开工作流列表"
              closeText="收起工作流列表"
              clearText="清空工作流"
              getOptionKey={(workflow) => workflow.route}
              getOptionLabel={(workflow) => workflow.name === workflow.route
                ? workflow.route
                : `${workflow.name}（${workflow.route}）`}
              isOptionEqualToValue={(option, value) => option.route === value.route}
              onChange={(_, workflow) => {
                setRoute(workflow?.route ?? '')
                setVersion(null)
              }}
              renderInput={(params) => (
                <TextField
                  {...params}
                  required
                  label="工作流"
                  error={missingWorkflow}
                  helperText={missingWorkflow ? '该工作流已不存在，请重新选择' : undefined}
                />
              )}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 3 }}>
            <TextField
              select
              fullWidth
              size="small"
              label="版本"
              value={version ?? ''}
              disabled={!route || saving}
              error={missingVersion || versions.isError}
              helperText={missingVersion
                ? '该版本已不存在，请重新选择'
                : versions.isError ? '工作流版本加载失败' : versions.isFetching ? '加载版本中…' : undefined}
              slotProps={{ inputLabel: { shrink: true }, select: { displayEmpty: true } }}
              onChange={(event) => setVersion(event.target.value === '' ? null : Number(event.target.value))}
            >
              <MenuItem value="">最新版本</MenuItem>
              {versionOptions.map((item) => <MenuItem key={item} value={item}>{item}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid size={12}>
            <TextField
              fullWidth
              multiline
              minRows={6}
              size="small"
              label="工作流入参"
              value={inputText}
              disabled={saving}
              error={Boolean(inputError)}
              helperText={inputError || '填写 JSON 对象，留空时使用 {}'}
              slotProps={{ input: { sx: { fontFamily: 'monospace' } }, htmlInput: { spellCheck: false } }}
              onChange={(event) => {
                setInputText(event.target.value)
                setInputError('')
              }}
            />
          </Grid>
        </Grid>
        <FormControlLabel
          label="启用计划"
          sx={{ alignSelf: 'flex-start' }}
          control={<Switch checked={enabled} disabled={saving} onChange={(_, checked) => setEnabled(checked)} />}
        />
        <Stack direction="row" spacing={1} justifyContent="flex-end">
          <Button disabled={saving} onClick={onClose}>取消</Button>
          <Button
            variant="contained"
            type="submit"
            disabled={!canSave}
          >
            {saving ? '保存中…' : '保存计划'}
          </Button>
        </Stack>
      </Stack>
    </Paper>
  )
}
