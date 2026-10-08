import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { DEFAULT_PAGE_SIZE_OPTIONS } from '@/components/ui/DataTable'
import { useSearchWorkflowExecutions } from '@/utils/query'
import {
  buildSearchParams,
  emptySearchFilters,
  hasSearchFilters,
  normalizeSearchFilters,
  readSearchState,
  writeSearchState,
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
  const { workflowRoutes, keyword, status, startFrom, startTo } = applied.filters

  // Restore searches on back/forward navigation, without submitting drafts on pagination.
  useEffect(() => {
    setFilters({ workflowRoutes, keyword, status, startFrom, startTo })
  }, [workflowRoutes, keyword, status, startFrom, startTo])

  const params = useMemo(() => buildSearchParams(applied), [applied])
  const query = useSearchWorkflowExecutions(params)

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
    if (JSON.stringify(buildSearchParams(next)) === JSON.stringify(params)) {
      void query.refetch()
    }
  }

  const search = () => {
    const normalized = normalizeSearchFilters(filters)
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

  return {
    filters,
    setFilters,
    search,
    reset,
    changePage,
    refresh: () => { void query.refetch() },
    resultObj: query.data,
    error: query.error,
    busy: query.isFetching,
    page: applied.page,
    rowsPerPage: applied.rowsPerPage,
    filterOn: hasSearchFilters(applied.filters),
  }
}
