import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

const SEARCH_QUERY_PARAM = 'search'
const PAGE_QUERY_PARAM = 'page'

/**
 * Sync table search + page to the URL so refresh keeps them.
 * No filter/tags — iFlow does not use those.
 */
export function useCustomPagination() {
  const [searchParams, setSearchParams] = useSearchParams()

  const searchParam = searchParams.get(SEARCH_QUERY_PARAM) ?? ''
  const pageParam = searchParams.get(PAGE_QUERY_PARAM) ?? ''

  const setSearchParam = useCallback(
    (searchTerm: string) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          if (searchTerm) next.set(SEARCH_QUERY_PARAM, searchTerm)
          else next.delete(SEARCH_QUERY_PARAM)
          // Searching again should start from page 1.
          next.delete(PAGE_QUERY_PARAM)
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  const handlePageChange = useCallback(
    (currentTablePage: number) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          if (currentTablePage > 1) next.set(PAGE_QUERY_PARAM, String(currentTablePage))
          else next.delete(PAGE_QUERY_PARAM)
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  return [
    {
      pageParam,
      searchParam,
    },
    {
      handlePageChange,
      setSearchParam,
    },
  ] as const
}
