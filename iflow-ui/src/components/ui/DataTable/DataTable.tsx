import type { ReactNode } from 'react'
import { Card, ConfigProvider, Table, type TableProps } from 'antd'
import { QuickSearch, type QuickSearchProps } from './QuickSearch'
import {
  DATA_TABLE_SCOPE,
  dataTableScopedCss,
  dataTableStyles,
  dataTableTheme,
} from './styles'

export { dataTableStyles } from './styles'
export { QuickSearch, type QuickSearchProps } from './QuickSearch'

export const DEFAULT_PAGE_SIZE_OPTIONS = [15, 25, 50]

export type DataTableProps<RecordType extends object> = {
  id?: string
  /** Left-side toolbar content (filters, result count, etc.). */
  toolbar?: ReactNode
  /** Right-side toolbar actions (Refresh, column picker, etc.). */
  customActions?: ReactNode
  /**
   * Custom block above the table (e.g. multi-field search panel).
   * Rendered above quick search when both are present.
   */
  header?: ReactNode
  /** Enable Conductor-style quick search panel. */
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
} & Omit<TableProps<RecordType>, 'className' | 'title'>

/**
 * Shared list-page table shell (Conductor DataTable role, Ant Design Table).
 * Owns card chrome + toolbar + table chrome so list pages stay visually consistent.
 */
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
  ...tableProps
}: DataTableProps<RecordType>) {
  const hasToolbarRow = toolbar != null || customActions != null
  const rootClassName = [DATA_TABLE_SCOPE, className].filter(Boolean).join(' ')

  return (
    <ConfigProvider theme={dataTableTheme}>
      <style>{dataTableScopedCss}</style>
      <Card
        id={id}
        variant="outlined"
        className={rootClassName}
        style={dataTableStyles.card}
        styles={{ body: dataTableStyles.cardBody }}
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
          <div style={dataTableStyles.toolbar}>
            <div style={dataTableStyles.toolbarMain}>{toolbar}</div>
            {customActions != null ? (
              <div style={dataTableStyles.toolbarActions}>{customActions}</div>
            ) : null}
          </div>
        ) : null}
        <Table<RecordType> style={dataTableStyles.tableShell} {...tableProps} />
      </Card>
    </ConfigProvider>
  )
}
