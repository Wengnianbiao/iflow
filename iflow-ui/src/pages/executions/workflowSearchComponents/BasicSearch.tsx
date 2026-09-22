import { useEffect, useMemo, useState } from 'react'
import { Autocomplete, Box, Button, MenuItem, Paper, TextField } from '@mui/material'
import { ArrowCounterClockwiseIcon, MagnifyingGlassIcon } from '@phosphor-icons/react'
import { useWorkflowDefs } from '@/utils/query'
import DateControl from './DateControl'
import WorkflowStatusBadge from '../WorkflowStatusBadge'
import ResultsTable from '../ResultsTable'
import executionsStyles from '../executionsStyles'
import { WORKFLOW_STATUS_OPTIONS } from '../searchState'
import { useWorkflowSearch } from './useWorkflowSearch'

/** Like Conductor BasicSearch, composes the search form and the results table. */
export default function BasicSearch() {
  const search = useWorkflowSearch()
  const { filters, setFilters } = search
  const workflowDefs = useWorkflowDefs()
  const [nameInput, setNameInput] = useState('')
  const workflowLabels = useMemo(() => {
    const definitions = new Map<string, { version: number; label: string }>()
    for (const definition of workflowDefs.data ?? []) {
      const previous = definitions.get(definition.name)
      if (!previous || definition.version > previous.version) {
        definitions.set(definition.name, {
          version: definition.version,
          label: definition.displayName?.trim() || '未命名工作流',
        })
      }
    }
    return new Map([...definitions].map(([name, definition]) => [name, definition.label]))
  }, [workflowDefs.data])
  const workflowNames = useMemo(
    () => [...workflowLabels.keys()].sort((a, b) => workflowLabels.get(a)!.localeCompare(workflowLabels.get(b)!, 'zh-CN')),
    [workflowLabels],
  )

  useEffect(() => { setNameInput('') }, [filters.workflowNames])

  return (
    <>
      <Paper variant="outlined" sx={executionsStyles.searchPaper}>
        <Box
          component="form"
          aria-label="搜索工作流执行记录"
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
              options={workflowNames}
              getOptionLabel={(name) => workflowLabels.get(name) ?? '未命名工作流'}
              getOptionKey={(name) => name}
              noOptionsText="没有匹配的工作流"
              loading={workflowDefs.isFetching}
              loadingText="加载工作流名称中…"
              openText="展开工作流列表"
              closeText="收起工作流列表"
              clearText="清空工作流名称"
              value={filters.workflowNames}
              inputValue={nameInput}
              onInputChange={(_, value) => setNameInput(value)}
              onChange={(_, value) => setFilters((current) => ({ ...current, workflowNames: value }))}
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
              onChange={(event) => setFilters({ ...filters, keyword: event.target.value })}
            />
            <TextField
              id="workflow-search-status"
              select
              fullWidth
              label="状态"
              size="small"
              sx={{ '& .MuiSelect-select': { py: '3px', minHeight: '32px', display: 'flex', alignItems: 'center' } }}
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
                status: WORKFLOW_STATUS_OPTIONS.find((option) => option.value === event.target.value)?.value ?? '',
              })}
            >
              <MenuItem value="">全部状态</MenuItem>
              {WORKFLOW_STATUS_OPTIONS.map((option) => (
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
        resultObj={search.resultObj}
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
