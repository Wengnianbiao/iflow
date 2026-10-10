import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

const SEARCH_QUERY_PARAM = 'search'
const PAGE_QUERY_PARAM = 'page'
const TAG_QUERY_PARAM = 'tag'

/**
 * Sync table search, tags and page to the URL so refresh keeps them.
 */
export function useCustomPagination() {
  const [searchParams, setSearchParams] = useSearchParams()

  const searchParam = searchParams.get(SEARCH_QUERY_PARAM) ?? ''
  const pageParam = searchParams.get(PAGE_QUERY_PARAM) ?? ''
  const tagsParam = searchParams.getAll(TAG_QUERY_PARAM)

  const setTagsParam = useCallback(
    (tags: string[]) => {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev)
        next.delete(TAG_QUERY_PARAM)
        for (const tag of tags) next.append(TAG_QUERY_PARAM, tag)
        next.delete(PAGE_QUERY_PARAM)
        return next
      }, { replace: true })
    },
    [setSearchParams],
  )

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
      tagsParam,
    },
    {
      handlePageChange,
      setSearchParam,
      setTagsParam,
    },
  ] as const
}
