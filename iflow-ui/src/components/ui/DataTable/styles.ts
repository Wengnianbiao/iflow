import type { CSSProperties } from 'react'
import type { ThemeConfig } from 'antd'

export const DATA_TABLE_SCOPE = 'iflow-data-table'

/** Shared inline styles for list-page DataTable consumers. */
export const dataTableStyles = {
  quickSearchRoot: {
    padding: 24,
  } satisfies CSSProperties,

  quickSearchRow: {
    width: '100%',
  } satisfies CSSProperties,

  quickSearchField: {
    position: 'relative',
    width: '100%',
    paddingTop: 8,
  } satisfies CSSProperties,

  quickSearchLabel: {
    position: 'absolute',
    top: 0,
    left: 12,
    zIndex: 2,
    padding: '0 4px',
    color: 'var(--iflow-color-text-muted)',
    background: 'var(--iflow-color-surface)',
    fontSize: 12,
    fontWeight: 600,
    lineHeight: 1,
    cursor: 'text',
  } satisfies CSSProperties,

  quickSearchCreateButton: {
    display: 'flex',
    justifyContent: 'flex-end',
  } satisfies CSSProperties,

  quickSearchDescription: {
    display: 'flex',
    alignItems: 'center',
  } satisfies CSSProperties,

  card: {
    overflow: 'hidden',
  } satisfies CSSProperties,

  cardBody: {
    padding: 0,
  } satisfies CSSProperties,

  toolbar: {
    display: 'flex',
    minHeight: 64,
    alignItems: 'center',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: 16,
    padding: '8px 16px',
    borderBottom: '1px solid var(--iflow-color-border)',
  } satisfies CSSProperties,

  toolbarMain: {
    display: 'flex',
    minWidth: 0,
    flex: 1,
    alignItems: 'center',
    gap: 12,
  } satisfies CSSProperties,

  toolbarActions: {
    display: 'flex',
    flexShrink: 0,
    alignItems: 'center',
    gap: 8,
    marginLeft: 'auto',
  } satisfies CSSProperties,

  tableShell: {
    width: '100%',
  } satisfies CSSProperties,
}

export const dataTableTheme: ThemeConfig = {
  components: {
    Table: {
      headerBg: 'var(--iflow-color-surface-muted)',
      headerColor: 'var(--iflow-color-text-muted)',
      colorText: 'var(--iflow-color-text-secondary)',
      cellFontSize: 13,
      cellPaddingBlock: 17,
      headerBorderRadius: 0,
    },
  },
}

/**
 * Deep Ant Design Table DOM bits that tokens / inline style cannot reach.
 * Scoped by DATA_TABLE_SCOPE on the Card root.
 */
export const dataTableScopedCss = `
.${DATA_TABLE_SCOPE} .iflow-quick-search-field:focus-within .iflow-quick-search-label {
  color: var(--iflow-color-primary) !important;
}
.${DATA_TABLE_SCOPE} .ant-table-thead > tr > th {
  height: 48px;
  font-size: 12px;
  font-weight: 700;
}
.${DATA_TABLE_SCOPE} .ant-table-tbody > tr > td {
  height: 62px;
}
.${DATA_TABLE_SCOPE} .ant-pagination {
  padding: 0 16px;
}
`
