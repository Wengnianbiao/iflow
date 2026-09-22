import type { SxProps, Theme } from '@mui/material/styles'

export const dataTableStyles = {
  quickSearchRoot: {
    p: 3,
  },
  quickSearchRow: {
    display: 'grid',
    gridTemplateColumns: { xs: '1fr', md: 'minmax(280px, 1fr) auto' },
    alignItems: 'center',
    gap: 2,
    width: '100%',
  },
  quickSearchField: {
    width: '100%',
    maxWidth: { md: 520 },
  },
  quickSearchCreateButton: {
    display: 'flex',
    justifyContent: 'flex-end',
  },
  quickSearchDescription: {
    display: 'flex',
    alignItems: 'center',
    gridColumn: { xs: '1', md: '1 / -1' },
  },
  card: {
    overflow: 'hidden',
    bgcolor: 'background.paper',
  },
  toolbar: {
    display: 'flex',
    minHeight: 64,
    alignItems: 'center',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: 2,
    px: 2,
    py: 1,
    borderBottom: 1,
    borderColor: 'divider',
  },
  toolbarMain: {
    display: 'flex',
    minWidth: 0,
    flex: 1,
    alignItems: 'center',
    gap: 1.5,
  },
  toolbarActions: {
    display: 'flex',
    flexShrink: 0,
    alignItems: 'center',
    gap: 1,
    ml: 'auto',
  },
} satisfies Record<string, SxProps<Theme>>
