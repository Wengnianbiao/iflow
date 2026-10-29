import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { searchTaskExecutions } from '@/api/workflowExecution'
import { DEFAULT_PAGE_SIZE_OPTIONS } from '@/components/ui/DataTable'
import { DEFAULT_STALE_TIME, useSearchWorkflowExecutions } from '@/utils/query'
import {
  buildSearchParams,
  buildTaskSearchParams,
  emptySearchFilters,
  hasSearchFilters,
  normalizeSearchFilters,
  readSearchState,
  writeSearchState,
  type ExecutionSearchMode,
  type WorkflowSearchState,
} from '../searchState'

/** URL = applied search; local state = editable draft; React Query = server state. */
export function useWorkflowSearch() {
  const [searchParams, setSearchParams] = useSearchParams()
  const searchKey = searchParams.toString()
  const applied = useMemo(
    () => readSearchState(new URLSearchParams(searchKey), DEFAULT_PAGE_SIZE_OPTIONS),
    [searchKey],
  )
  const [filters, setFilters] = useState(applied.filters)

  // Restore searches on back/forward navigation, without submitting drafts on pagination.
  useEffect(() => {
    setFilters(applied.filters)
  }, [applied.filters])

  const params = useMemo(() => buildSearchParams(applied), [applied])
  const workflowQuery = useSearchWorkflowExecutions(params, { enabled: applied.mode === 'workflow' })
  const taskParams = useMemo(() => buildTaskSearchParams(applied), [applied])
  const taskQuery = useQuery({
    queryKey: ['taskExecutions', taskParams],
    queryFn: () => searchTaskExecutions(taskParams),
    staleTime: DEFAULT_STALE_TIME,
    enabled: applied.mode === 'task',
  })
  const query = applied.mode === 'task' ? taskQuery : workflowQuery

  // A refreshed result set may have fewer pages than the URL previously requested.
  useEffect(() => {
    if (!query.data || query.isFetching || query.error) return
    const lastPage = Math.max(1, Math.ceil(query.data.total / applied.rowsPerPage))
    if (applied.page > lastPage) {
      setSearchParams(writeSearchState(new URLSearchParams(searchKey), {
        ...applied,
        page: lastPage,
      }), { replace: true })
    }
  }, [applied, query.data, query.error, query.isFetching, searchKey, setSearchParams])

  const commitSearch = (next: WorkflowSearchState) => {
    setSearchParams(writeSearchState(searchParams, next))
    // Conductor also refreshes when Search is clicked with the same query.
    const nextParams = next.mode === 'task' ? buildTaskSearchParams(next) : buildSearchParams(next)
    const currentParams = applied.mode === 'task' ? taskParams : params
    if (next.mode === applied.mode && JSON.stringify(nextParams) === JSON.stringify(currentParams)) {
      void query.refetch()
    }
  }

  const search = () => {
    const normalized = normalizeSearchFilters(filters, applied.mode)
    setFilters(normalized)
    commitSearch({ ...applied, filters: normalized, page: 1 })
  }

  const reset = () => {
    const empty = emptySearchFilters()
    setFilters(empty)
    commitSearch({ ...applied, filters: empty, page: 1 })
  }

  const changePage = (page: number, rowsPerPage: number) => {
    setSearchParams(writeSearchState(searchParams, {
      ...applied,
      page: rowsPerPage === applied.rowsPerPage ? page : 1,
      rowsPerPage,
    }))
  }

  const changeMode = (mode: ExecutionSearchMode) => {
    commitSearch({ ...applied, mode, filters: normalizeSearchFilters(applied.filters, mode), page: 1 })
  }

  const changeTaskScope = (asyncOnly: boolean) => {
    commitSearch({ ...applied, asyncOnly, page: 1 })
  }

  return {
    mode: applied.mode,
    asyncOnly: applied.asyncOnly,
    changeMode,
    changeTaskScope,
    filters,
    setFilters,
    search,
    reset,
    changePage,
    refresh: () => { void query.refetch() },
    resultObj: workflowQuery.data,
    taskResultObj: taskQuery.data,
    error: query.error,
    busy: query.isFetching,
    page: applied.page,
    rowsPerPage: applied.rowsPerPage,
    filterOn: hasSearchFilters(applied.filters),
  }
}
