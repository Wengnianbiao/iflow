import type { SxProps, Theme } from '@mui/material/styles'

/** Conductor's outlined search/result panels, adapted to iFlow's supported filters. */
const executionsStyles = {
  searchPaper: { mb: '24px' },
  searchForm: { p: '24px' },
  fields: {
    display: 'grid',
    gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' },
    gap: '24px',
  },
  searchFooter: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2, mt: '24px' },
  actions: {
    display: 'flex',
    justifyContent: 'flex-end',
    flexWrap: 'wrap',
    gap: 1,
    ml: 'auto',
  },
  resultCount: { display: 'flex', alignItems: 'center', gap: 2 },
  empty: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 },
  link: { fontWeight: 600 },
  monospace: {
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    fontSize: 12,
  },
} satisfies Record<string, SxProps<Theme>>

export default executionsStyles
