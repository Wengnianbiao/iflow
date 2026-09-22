import { Box, TextField, type BoxProps } from '@mui/material'
import type { ReactNode } from 'react'
import { dataTableStyles } from './styles'

export type QuickSearchProps = {
  autoFocusValue?: boolean
  createButton?: ReactNode
  description?: ReactNode
  onChange: (value: string) => void
  quickSearchLabel?: ReactNode
  quickSearchPlaceholder: string
  searchTerm: string
  searchModalContainerProps?: BoxProps
}

/** Conductor-style responsive quick-search panel backed by MUI. */
export function QuickSearch({
  autoFocusValue = true,
  createButton,
  description,
  quickSearchLabel = '快速搜索',
  onChange,
  quickSearchPlaceholder,
  searchTerm,
  searchModalContainerProps,
}: QuickSearchProps) {
  return (
    <Box sx={dataTableStyles.quickSearchRoot}>
      <Box sx={dataTableStyles.quickSearchRow}>
        <Box sx={dataTableStyles.quickSearchField} {...searchModalContainerProps}>
          <TextField
            id="quick-search-field"
            autoFocus={autoFocusValue}
            fullWidth
            label={quickSearchLabel}
            placeholder={quickSearchPlaceholder}
            size="small"
            value={searchTerm}
            onChange={(event) => onChange(event.target.value)}
          />
        </Box>

        {createButton ? (
          <Box sx={dataTableStyles.quickSearchCreateButton}>{createButton}</Box>
        ) : null}

        {description ? (
          <Box sx={dataTableStyles.quickSearchDescription}>{description}</Box>
        ) : null}
      </Box>
    </Box>
  )
}
