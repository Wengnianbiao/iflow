import {
  Box,
  LinearProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
} from '@mui/material'
import {
  useEffect,
  useMemo,
  useState,
  type Key,
  type ReactNode,
} from 'react'
import { QuickSearch, type QuickSearchProps } from './QuickSearch'
import { dataTableStyles } from './styles'

export { dataTableStyles } from './styles'
export { QuickSearch, type QuickSearchProps } from './QuickSearch'

export const DEFAULT_PAGE_SIZE_OPTIONS = [15, 25, 50]

export type DataTableColumn<RecordType extends object> = {
  title: ReactNode
  dataIndex?: keyof RecordType | string
  key: string
  width?: number | string
  align?: 'left' | 'center' | 'right'
  ellipsis?: boolean
  fixed?: 'left' | 'right'
  render?: (value: unknown, record: RecordType, index: number) => ReactNode
}

export type DataTablePagination = false | {
  current?: number
  defaultPageSize?: number
  pageSize?: number
  total?: number
  pageSizeOptions?: readonly number[]
  showSizeChanger?: boolean
  showTotal?: (total: number, range: [number, number]) => ReactNode
  onChange?: (page: number, pageSize: number) => void
}

export type DataTableProps<RecordType extends object> = {
  id?: string
  toolbar?: ReactNode
  customActions?: ReactNode
  header?: ReactNode
  quickSearchEnabled?: boolean
  quickSearchPlaceholder?: string
  quickSearchLabel?: ReactNode
  searchTerm?: string
  onSearchTermChange?: (value: string) => void
  autoFocus?: boolean
  createButton?: ReactNode
  description?: ReactNode
  searchModalContainerProps?: QuickSearchProps['searchModalContainerProps']
  className?: string
  columns: DataTableColumn<RecordType>[]
  dataSource?: RecordType[]
  loading?: boolean
  rowKey: keyof RecordType | string | ((record: RecordType) => Key)
  scroll?: { x?: number | string; y?: number | string }
  locale?: { emptyText?: ReactNode }
  pagination?: DataTablePagination
}

function getRecordValue<RecordType extends object>(
  record: RecordType,
  dataIndex?: keyof RecordType | string,
) {
  if (dataIndex == null) return undefined
  return record[dataIndex as keyof RecordType]
}

function getRecordKey<RecordType extends object>(
  record: RecordType,
  rowKey: DataTableProps<RecordType>['rowKey'],
) {
  if (typeof rowKey === 'function') return rowKey(record)
  return String(record[rowKey as keyof RecordType])
}

/** Shared MUI list-page table shell, modelled after Conductor's DataTable role. */
export function DataTable<RecordType extends object>({
  id,
  toolbar,
  customActions,
  header,
  quickSearchEnabled = false,
  quickSearchPlaceholder = '搜索',
  quickSearchLabel = '快速搜索',
  searchTerm = '',
  onSearchTermChange,
  autoFocus = true,
  createButton,
  description,
  searchModalContainerProps,
  className,
  columns,
  dataSource = [],
  loading = false,
  rowKey,
  scroll,
  locale,
  pagination = false,
}: DataTableProps<RecordType>) {
  const hasToolbarRow = toolbar != null || customActions != null
  const configuredPageSize = pagination === false
    ? DEFAULT_PAGE_SIZE_OPTIONS[0]
    : (pagination.pageSize ?? pagination.defaultPageSize ?? DEFAULT_PAGE_SIZE_OPTIONS[0])
  const [pageSize, setPageSize] = useState(configuredPageSize)

  useEffect(() => setPageSize(configuredPageSize), [configuredPageSize])

  const currentPage = pagination === false ? 1 : (pagination.current ?? 1)
  const serverPaginated = pagination !== false && pagination.total != null
  const visibleRows = useMemo(() => {
    if (pagination === false || serverPaginated) return dataSource
    const start = (currentPage - 1) * pageSize
    return dataSource.slice(start, start + pageSize)
  }, [currentPage, dataSource, pageSize, pagination, serverPaginated])
  const total = pagination === false ? dataSource.length : (pagination.total ?? dataSource.length)

  return (
    <Paper
      id={id}
      variant="outlined"
      className={className}
      sx={dataTableStyles.card}
    >
      {header}
      {quickSearchEnabled ? (
        <QuickSearch
          autoFocusValue={autoFocus}
          createButton={createButton}
          description={description}
          quickSearchLabel={quickSearchLabel}
          quickSearchPlaceholder={quickSearchPlaceholder}
          searchTerm={searchTerm}
          onChange={(value) => onSearchTermChange?.(value)}
          searchModalContainerProps={searchModalContainerProps}
        />
      ) : null}
      {hasToolbarRow ? (
        <Box sx={dataTableStyles.toolbar}>
          <Box sx={dataTableStyles.toolbarMain}>{toolbar}</Box>
          {customActions != null ? (
            <Box sx={dataTableStyles.toolbarActions}>{customActions}</Box>
          ) : null}
        </Box>
      ) : null}
      {loading ? <LinearProgress aria-label="正在加载" /> : null}
      <TableContainer
        sx={{
          maxHeight: scroll?.y,
          overflowX: scroll?.x ? 'auto' : undefined,
        }}
      >
        <Table
          stickyHeader={Boolean(scroll?.y)}
          size="small"
          sx={{ minWidth: scroll?.x }}
        >
          <TableHead>
            <TableRow>
              {columns.map((column) => (
                <TableCell
                  key={column.key}
                  align={column.align}
                  sx={{
                    width: column.width,
                    minWidth: column.width,
                    ...(column.fixed === 'right' ? {
                      position: 'sticky',
                      right: 0,
                      zIndex: 3,
                    } : null),
                  }}
                >
                  {column.title}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {visibleRows.map((record, rowIndex) => (
              <TableRow hover key={getRecordKey(record, rowKey)}>
                {columns.map((column) => {
                  const value = getRecordValue(record, column.dataIndex)
                  const content = column.render
                    ? column.render(value, record, rowIndex)
                    : value == null ? '-' : String(value)
                  return (
                    <TableCell
                      key={column.key}
                      align={column.align}
                      sx={{
                        height: 62,
                        width: column.width,
                        minWidth: column.width,
                        ...(column.fixed === 'right' ? {
                          position: 'sticky',
                          right: 0,
                          zIndex: 1,
                          bgcolor: 'background.paper',
                        } : null),
                      }}
                    >
                      {column.ellipsis ? (
                        <Box
                          component="span"
                          sx={{
                            display: 'block',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {content}
                        </Box>
                      ) : content}
                    </TableCell>
                  )
                })}
              </TableRow>
            ))}
            {!loading && visibleRows.length === 0 ? (
              <TableRow>
                <TableCell align="center" colSpan={Math.max(columns.length, 1)} sx={{ py: 8 }}>
                  {locale?.emptyText ?? '暂无数据'}
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </TableContainer>
      {pagination !== false ? (
        <TablePagination
          component="div"
          count={total}
          page={Math.max(0, currentPage - 1)}
          rowsPerPage={pageSize}
          rowsPerPageOptions={pagination.showSizeChanger === false
            ? []
            : [...(pagination.pageSizeOptions ?? DEFAULT_PAGE_SIZE_OPTIONS)]}
          labelRowsPerPage="每页行数"
          labelDisplayedRows={({ from, to, count }) => (
            pagination.showTotal?.(count, [from, to]) ?? `${from}-${to} / ${count}`
          )}
          onPageChange={(_event, nextPage) => {
            pagination.onChange?.(nextPage + 1, pageSize)
          }}
          onRowsPerPageChange={(event) => {
            const nextPageSize = Number(event.target.value)
            setPageSize(nextPageSize)
            pagination.onChange?.(1, nextPageSize)
          }}
        />
      ) : null}
    </Paper>
  )
}
