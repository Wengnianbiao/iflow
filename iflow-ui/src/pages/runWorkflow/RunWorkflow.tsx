import { useMemo, useState } from 'react'
import { Helmet } from 'react-helmet'
import { useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Autocomplete, Box, Button, Grid, Paper, TextField, Typography } from '@mui/material'
import { ArrowCounterClockwiseIcon, PlayCircleIcon, XIcon } from '@phosphor-icons/react'
import { listWorkflowVersions } from '@/api/workflowDefinition'
import { getWorkflowExecution, startWorkflow } from '@/api/workflowExecution'
import { SectionContainer } from '@/components/layout/SectionContainer'
import { SectionHeader } from '@/components/layout/SectionHeader'
import { NavLink } from '@/components/ui/NavLink'
import type { WorkflowDef } from '@/types/workflow'
import type { WorkflowExecutionSummary } from '@/types/workflowExecution'
import { useWorkflowDefs } from '@/utils/query'
import { emptyRequestBody, requestBodyFromWorkflowInput, toHttpRequestBody } from './gatewayInput'
import { RunWorkflowHistoryTable } from './RunWorkflowHistoryTable'
import { RequestBodyEditor } from './RequestBodyEditor'

type WorkflowChoice = {
  route: string
  name: string
  versions: number[]
}

type RunWorkflowLocationState = {
  execution?: {
    workflowRoute: string
    workflowVersion: number
    input?: Record<string, unknown>
  }
}

function workflowChoices(definitions: WorkflowDef[]): WorkflowChoice[] {
  const byRoute = new Map<string, WorkflowDef[]>()
  for (const definition of definitions) {
    const versions = byRoute.get(definition.route) ?? []
    versions.push(definition)
    byRoute.set(definition.route, versions)
  }
  return [...byRoute.values()]
    .map((versions) => {
      const latest = [...versions].sort((left, right) => right.version - left.version)[0]
      return {
        route: latest.route,
        name: latest.name?.trim() || latest.route,
        versions: versions.map((definition) => definition.version).sort((left, right) => right - left),
      }
    })
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN') || left.route.localeCompare(right.route))
}

export function RunWorkflow() {
  const navigate = useNavigate()
  const location = useLocation()
  const latestExecution = (location.state as RunWorkflowLocationState | null)?.execution
  const workflowDefs = useWorkflowDefs()
  const choices = useMemo(() => workflowChoices(workflowDefs.data ?? []), [workflowDefs.data])
  const [selectedRoute, setSelectedRoute] = useState<string | null>(latestExecution?.workflowRoute ?? null)
  const [version, setVersion] = useState<number | null>(latestExecution?.workflowVersion ?? null)
  const [requestBody, setRequestBody] = useState(() => latestExecution
    ? requestBodyFromWorkflowInput(latestExecution.input)
    : emptyRequestBody())
  const [busy, setBusy] = useState(false)
  const [restoringId, setRestoringId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [workflowId, setWorkflowId] = useState<string | null>(null)
  const [historyReload, setHistoryReload] = useState(0)

  const workflowVersions = useQuery({
    queryKey: ['workflowVersions', selectedRoute],
    queryFn: () => listWorkflowVersions(selectedRoute!),
    enabled: Boolean(selectedRoute),
  })

  const selected = choices.find((choice) => choice.route === selectedRoute) ?? null
  const availableVersions = useMemo(
    () => [...(workflowVersions.data ?? selected?.versions ?? [])].sort((left, right) => right - left),
    [selected, workflowVersions.data],
  )
  const validVersion = version != null && availableVersions.includes(version)
  const selectedDefinition = (workflowDefs.data ?? []).find((definition) =>
    definition.route === selectedRoute && definition.version === version)
  const description = selectedDefinition?.description?.trim() || undefined

  const selectWorkflow = (choice: WorkflowChoice | null) => {
    setSelectedRoute(choice?.route ?? null)
    setVersion(choice?.versions[0] ?? null)
    setRequestBody(emptyRequestBody())
    setError(null)
    setWorkflowId(null)
  }

  const reset = () => {
    selectWorkflow(null)
  }

  const submit = async () => {
    if (!selected || version == null || !validVersion || workflowVersions.isError) return
    setBusy(true)
    setError(null)
    setWorkflowId(null)
    try {
      const payload = toHttpRequestBody(requestBody)
      const startedId = await startWorkflow(selected.route, version, payload.body, payload.contentType)
      setWorkflowId(startedId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '启动工作流失败')
    } finally {
      setHistoryReload((current) => current + 1)
      setBusy(false)
    }
  }

  const restore = async (execution: WorkflowExecutionSummary) => {
    setRestoringId(execution.workflowId)
    setError(null)
    try {
      const detail = await getWorkflowExecution(execution.workflowId, false)
      setRequestBody(requestBodyFromWorkflowInput(detail.input))
      setWorkflowId(null)
      if (availableVersions.includes(execution.workflowVersion)) {
        setVersion(execution.workflowVersion)
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '读取执行入参失败')
    } finally {
      setRestoringId(null)
    }
  }

  return (
    <>
      <Helmet>
        <title>执行工作流</title>
      </Helmet>
      <SectionHeader
        title="执行工作流"
        actions={(
          <>
            <Button variant="text" startIcon={<XIcon size={18} />} onClick={() => navigate(-1)}>
              关闭
            </Button>
            <Button variant="text" startIcon={<ArrowCounterClockwiseIcon size={18} />} onClick={reset}>
              重置
            </Button>
            <Button
              id="run-workflow-btn"
              variant="contained"
              disabled={!selected || !validVersion || workflowVersions.isError || busy}
              startIcon={<PlayCircleIcon size={18} />}
              onClick={() => void submit()}
            >
              {busy ? '执行中…' : '执行工作流'}
            </Button>
          </>
        )}
      />
      <SectionContainer>
        {error ? <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>{error}</Alert> : null}
        {workflowId ? (
          <Alert severity="success" sx={{ mb: 3 }} onClose={() => setWorkflowId(null)}>
            已启动，实例 ID：
            <NavLink path={`/executions/${encodeURIComponent(workflowId)}`} style={{ marginLeft: 4 }}>
              {workflowId}
            </NavLink>
          </Alert>
        ) : null}
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '24px' }}>
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'minmax(0, 1fr) 120px' }, gap: '24px', alignItems: 'start' }}>
                <Autocomplete
                  id="workflow-route-dropdown"
                  openOnFocus
                  forcePopupIcon
                  fullWidth
                  size="small"
                  options={choices}
                  value={selected}
                  loading={workflowDefs.isFetching}
                  loadingText="加载工作流中…"
                  noOptionsText={workflowDefs.isError ? '工作流列表加载失败' : '没有可执行的工作流'}
                  openText="展开工作流列表"
                  closeText="收起工作流列表"
                  clearText="清空"
                  getOptionLabel={(option) => option.name}
                  getOptionKey={(option) => option.route}
                  isOptionEqualToValue={(option, value) => option.route === value.route}
                  onChange={(_, option) => selectWorkflow(option)}
                  renderOption={(props, option) => {
                    const { key, ...rest } = props
                    return (
                      <li key={key} {...rest}>
                        <Box sx={{ py: 0.5, minWidth: 0 }}>
                          <Typography variant="body2">{option.name}</Typography>
                          <Typography variant="caption" color="text.secondary">{option.route}</Typography>
                        </Box>
                      </li>
                    )
                  }}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="工作流名称"
                      placeholder="选择要执行的工作流"
                      required
                      slotProps={{ inputLabel: { shrink: true } }}
                      error={workflowDefs.isError}
                      helperText={workflowDefs.isError ? '工作流列表加载失败，请刷新重试' : undefined}
                    />
                  )}
                />
                <Autocomplete
                  id="workflow-version-dropdown"
                  openOnFocus
                  forcePopupIcon
                  fullWidth
                  size="small"
                  disabled={!selected}
                  options={availableVersions}
                  value={version}
                  loading={workflowVersions.isFetching}
                  loadingText="加载版本中…"
                  noOptionsText={workflowVersions.isError ? '工作流版本加载失败' : '没有版本'}
                  openText="展开版本列表"
                  closeText="收起版本列表"
                  clearText="清空"
                  getOptionLabel={(option) => String(option)}
                  onChange={(_, nextVersion) => setVersion(nextVersion)}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="版本"
                      placeholder="版本"
                      error={workflowVersions.isError || (version != null && !validVersion && !workflowVersions.isFetching)}
                      helperText={workflowVersions.isError
                        ? '工作流版本加载失败，请刷新重试'
                        : version != null && !validVersion && !workflowVersions.isFetching
                          ? '该版本已不存在，请选择其他版本'
                          : undefined}
                      slotProps={{ inputLabel: { shrink: true } }}
                    />
                  )}
                />
              </Box>
              {description ? (
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                  {description}
                </Typography>
              ) : null}
              <RequestBodyEditor value={requestBody} path={selectedRoute ?? undefined} onChange={(value) => {
                setRequestBody(value)
                setError(null)
              }} />
            </Paper>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <RunWorkflowHistoryTable
              workflowRoute={selectedRoute}
              reloadKey={historyReload}
              restoringId={restoringId}
              onRestore={(execution) => void restore(execution)}
            />
          </Grid>
        </Grid>
      </SectionContainer>
    </>
  )
}
