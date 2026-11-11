import { Fragment } from 'react'
import { Box } from '@mui/material'

export const CRON_FIELD_COLORS = [
  '#4FAAD1', '#6569AC', '#45AC59', '#C99E00', '#EE6B31', '#CE2836',
] as const

const labels = [
  '秒（0–59）',
  '分（0–59）',
  '时（0–23）',
  '日（1–31）',
  '月（1–12 或 JAN–DEC）',
  '星期（0–7 或 MON–SUN，0 和 7 均为周日）',
]

const labelHeight = 20
const guideHeight = labels.length * labelHeight

export function CronExpressionHelp({ highlightedPart }: { highlightedPart: number | null }) {
  return (
    <Box sx={{ display: 'flex', columnGap: 0.75, pt: 1.5, pb: 2, pl: 1.5 }}>
      <Box
        aria-hidden="true"
        sx={{ position: 'relative', flex: '0 0 clamp(68px, 35%, 180px)', height: guideHeight + 18 }}
      >
        {labels.map((label, index) => {
          const left = `${index * 100 / labels.length}%`
          const top = index * labelHeight
          const color = CRON_FIELD_COLORS[index]
          const opacity = highlightedPart === null || highlightedPart === index ? 1 : 0.3

          return (
            <Fragment key={label}>
              <Box
                sx={{
                  position: 'absolute', left, top, right: 0, height: guideHeight - top,
                  borderTop: `2px solid ${color}`, borderLeft: `2px solid ${color}`,
                  borderRadius: '14px 0 0 0', opacity,
                }}
              />
              <Box
                component="span"
                sx={{
                  position: 'absolute', left, top: guideHeight + 2, transform: 'translateX(-40%)',
                  color, opacity, fontSize: 18, lineHeight: 1, fontWeight: 700,
                }}
              >
                *
              </Box>
            </Fragment>
          )
        })}
      </Box>
      <Box sx={{ minWidth: 0, mt: '-5px' }}>
        {labels.map((label, index) => (
          <Box
            key={label}
            sx={{
              minHeight: labelHeight, color: CRON_FIELD_COLORS[index], fontSize: 11, lineHeight: '14px',
              opacity: highlightedPart === null || highlightedPart === index ? 1 : 0.3,
            }}
          >
            {label}
          </Box>
        ))}
      </Box>
    </Box>
  )
}
