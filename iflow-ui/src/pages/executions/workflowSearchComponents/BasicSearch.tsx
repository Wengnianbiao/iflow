import { useEffect, useMemo, useState } from 'react'
import { Autocomplete, Box, Button, MenuItem, Paper, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { ArrowCounterClockwiseIcon, MagnifyingGlassIcon } from '@phosphor-icons/react'
import { useWorkflowDefs } from '@/utils/query'
import DateControl from './DateControl'
import WorkflowStatusBadge from '../WorkflowStatusBadge'
import ResultsTable from '../ResultsTable'
import executionsStyles from '../executionsStyles'
import { TASK_STATUS_OPTIONS, WORKFLOW_STATUS_OPTIONS, type ExecutionSearchMode } from '../searchState'
import { useWorkflowSearch } from './useWorkflowSearch'

/** Like Conductor BasicSearch, composes the search form and the results table. */
export default function BasicSearch() {
  const search = useWorkflowSearch()
  const { filters, setFilters } = search
  const statusOptions = search.mode === 'task' ? TASK_STATUS_OPTIONS : WORKFLOW_STATUS_OPTIONS
  const workflowDefs = useWorkflowDefs()
  const [nameInput, setNameInput] = useState('')
  const workflowLabels = useMemo(() => {
    const definitions = new Map<string, { version: number; label: string }>()
    for (const definition of workflowDefs.data ?? []) {
      const previous = definitions.get(definition.route)
      if (!previous || definition.version > previous.version) {
        definitions.set(definition.route, {
          version: definition.version,
          label: definition.name?.trim() || '未命名工作流',
        })
      }
    }
    return new Map([...definitions].map(([name, definition]) => [name, definition.label]))
  }, [workflowDefs.data])
  const workflowRoutes = useMemo(
    () => [...workflowLabels.keys()].sort((a, b) => workflowLabels.get(a)!.localeCompare(workflowLabels.get(b)!, 'zh-CN')),
    [workflowLabels],
  )

  useEffect(() => { setNameInput('') }, [filters.workflowRoutes])

  return (
    <>
      <Paper variant="outlined" sx={executionsStyles.searchPaper}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, px: '24px', pt: '24px' }}>
          <ToggleButtonGroup exclusive size="small" value={search.mode} aria-label="查询对象"
            onChange={(_event, value: ExecutionSearchMode | null) => value && search.changeMode(value)}>
            <ToggleButton value="workflow">工作流</ToggleButton>
            <ToggleButton value="task">工作流任务</ToggleButton>
          </ToggleButtonGroup>
          {search.mode === 'task' && (
            <ToggleButtonGroup exclusive size="small" value={search.asyncOnly ? 'async' : 'all'} aria-label="任务范围"
              onChange={(_event, value: string | null) => value && search.changeTaskScope(value === 'async')}>
              <ToggleButton value="all">全部任务</ToggleButton>
              <ToggleButton value="async">外部调用</ToggleButton>
            </ToggleButtonGroup>
          )}
        </Box>
        <Box
          component="form"
          aria-label={search.mode === 'task' ? '搜索工作流任务执行记录' : '搜索工作流执行记录'}
          sx={executionsStyles.searchForm}
          onSubmit={(event) => {
            event.preventDefault()
            search.search()
          }}
        >
          <Box sx={executionsStyles.fields}>
            <Autocomplete
              id="workflow-search-name"
              multiple
              filterSelectedOptions
              openOnFocus
              forcePopupIcon
              fullWidth
              size="small"
              options={workflowRoutes}
              getOptionLabel={(name) => workflowLabels.get(name) ?? '未命名工作流'}
              getOptionKey={(name) => name}
              renderOption={(props, route) => {
                const { key, ...rest } = props
                return (
                  <li key={key} {...rest}>
                    <Box sx={{ py: 0.5, minWidth: 0 }}>
                      <Typography variant="body2">{workflowLabels.get(route) ?? '未命名工作流'}</Typography>
                      <Typography variant="caption" color="text.secondary">{route}</Typography>
                    </Box>
                  </li>
                )
              }}
              noOptionsText="没有匹配的工作流"
              loading={workflowDefs.isFetching}
              loadingText="加载工作流名称中…"
              openText="展开工作流列表"
              closeText="收起工作流列表"
              clearText="清空工作流名称"
              value={filters.workflowRoutes}
              inputValue={nameInput}
              onInputChange={(_, value) => setNameInput(value)}
              onChange={(_, value) => setFilters((current) => ({ ...current, workflowRoutes: value }))}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="工作流名称"
                  slotProps={{ inputLabel: { shrink: true } }}
                  error={workflowDefs.isError}
                  helperText={workflowDefs.isError ? '工作流名称加载失败，请刷新重试' : undefined}
                />
              )}
            />
            <TextField
              id="workflow-search-correlation-id"
              label="关键字"
              fullWidth
              size="small"
              slotProps={{ inputLabel: { shrink: true } }}
              value={filters.keyword}
              helperText={search.mode === 'task' ? '沿用所属工作流的关键字' : undefined}
              onChange={(event) => setFilters({ ...filters, keyword: event.target.value })}
            />
            <TextField
              id="workflow-search-status"
              select
              fullWidth
              label="状态"
              size="small"
              slotProps={{
                inputLabel: { shrink: true },
                select: {
                  displayEmpty: true,
                  renderValue: () => filters.status ? <WorkflowStatusBadge status={filters.status} /> : null,
                },
              }}
              value={filters.status}
              onChange={(event) => setFilters({
                ...filters,
                status: statusOptions.find((option) => option.value === event.target.value)?.value ?? '',
              })}
            >
              <MenuItem value="">全部状态</MenuItem>
              {statusOptions.map((option) => (
                <MenuItem key={option.value} value={option.value} sx={{ py: 1 }}><WorkflowStatusBadge status={option.value} /></MenuItem>
              ))}
            </TextField>
          </Box>
          <Box sx={executionsStyles.searchFooter}>
            <DateControl from={filters.startFrom} to={filters.startTo}
              onChange={(startFrom, startTo) => setFilters((current) => ({ ...current, startFrom, startTo }))} />
            <Box sx={executionsStyles.actions}>
              <Button
                id="reset-workflow-btn"
                startIcon={<ArrowCounterClockwiseIcon size={17} />}
                onClick={search.reset}
              >
                重置
              </Button>
              <Button
                id="search-workflow-btn"
                variant="contained"
                type="submit"
                startIcon={<MagnifyingGlassIcon size={17} />}
                disabled={search.busy}
              >
                搜索
              </Button>
            </Box>
          </Box>
        </Box>
      </Paper>
      <ResultsTable
        mode={search.mode}
        resultObj={search.resultObj}
        taskResultObj={search.taskResultObj}
        error={search.error}
        busy={search.busy}
        page={search.page}
        rowsPerPage={search.rowsPerPage}
        onPageChange={search.changePage}
        refetchExecution={search.refresh}
        filterOn={search.filterOn}
        handleReset={search.reset}
      />
    </>
  )
}
