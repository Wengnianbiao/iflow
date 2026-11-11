import { useEffect, useState, type SyntheticEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Autocomplete, Box, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import cronstrue from 'cronstrue'
import 'cronstrue/locales/zh_CN'
import { previewWorkflowSchedule } from '@/api/workflowSchedule'
import { CRON_FIELD_COLORS, CronExpressionHelp } from './CronExpressionHelp'

const templates = [
  '* * * ? * *', '0 * * ? * *', '0 */2 * ? * *', '0 1/2 * ? * *',
  '0 */30 * ? * *', '0 15,30,45 * ? * *', '0 0 * ? * *', '0 0 */2 ? * *',
  '0 0 0/2 ? * *', '0 0 1/2 ? * *', '0 0 0 * * ?', '0 0 1 * * ?',
  '0 0 6 * * ?', '0 0 8 * * *', '0 0 12 ? * SUN', '0 0 12 ? * MON-FRI',
  '0 0 12 ? * SUN,SAT', '0 0 12 */7 * ?', '0 0 12 1 * ?', '0 0 12 15 * ?',
  '0 0 12 1/4 * ?', '0 0 12 L * ?', '0 0 12 L-2 * ?', '0 0 12 1W * ?',
  '0 0 12 15W * ?', '0 0 12 ? * 2#1', '0 0 12 ? * 6#2', '0 0 12 ? JAN *',
  '0 0 12 ? JAN,JUN *', '0 0 12 ? JAN,FEB,APR *', '0 0 12 ? 9-12 *',
]

export const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
const intl = Intl as typeof Intl & { supportedValuesOf?: (key: 'timeZone') => string[] }
const timeZones = [...new Set([
  'UTC', browserZone, 'Asia/Shanghai',
  ...(intl.supportedValuesOf?.('timeZone') ?? ['Asia/Tokyo', 'Europe/London', 'America/New_York']),
])]

function describeCron(expression: string) {
  try {
    const description = cronstrue.toString(expression, { locale: 'zh_CN', use24HourTimeFormat: true })
    const dayOffset = /^L-(\d+)$/.exec(expression.split(' ')[3] ?? '')
    return dayOffset
      ? description.replace(`限每月最后${dayOffset[1]}天`, `限每月倒数第 ${Number(dayOffset[1]) + 1} 天`)
      : description
  } catch {
    return expression
  }
}

function TemplateOption({ expression }: { expression: string }) {
  return (
    <Box sx={{ py: 0.25 }}>
      <Box component="code" sx={{ display: 'flex', gap: 1, fontSize: '1rem', fontWeight: 700 }}>
        {expression.split(' ').map((value, index) => (
          <Box component="span" key={index} sx={{ color: CRON_FIELD_COLORS[index] }}>{value}</Box>
        ))}
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'normal' }}>
        {describeCron(expression)}
      </Typography>
    </Box>
  )
}

function createTimeFormatter(timeZone: string) {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23', timeZoneName: 'short',
  })
}

function formatTime(formatter: Intl.DateTimeFormat, time: number) {
  const { year, month, day, hour, minute, second, timeZoneName } = Object.fromEntries(
    formatter.formatToParts(time).map(({ type, value }) => [type, value]),
  )
  return `${year}-${month}-${day} ${hour}:${minute}:${second} ${timeZoneName}`
}

const browserTimeFormatter = createTimeFormatter(browserZone)

function PreviewTimes({ times, zoneId }: { times: number[]; zoneId: string }) {
  let displayZone = zoneId
  let scheduleTimeFormatter: Intl.DateTimeFormat
  try {
    const intlZone = zoneId === 'Z' || zoneId === 'UT' ? 'UTC' : zoneId.replace(/^(?:UTC|GMT|UT)(?=[+-])/, '')
    scheduleTimeFormatter = createTimeFormatter(intlZone)
  } catch {
    displayZone = 'UTC'
    scheduleTimeFormatter = createTimeFormatter(displayZone)
  }
  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Box component="table" sx={{
        borderCollapse: 'collapse', fontSize: '0.875rem', lineHeight: 1.5,
        '& th, & td': { textAlign: 'left', pr: 3, whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' },
        '& th': { pb: 1, fontWeight: 600 },
      }}>
        <thead>
          <tr><th scope="col">{displayZone} 时间</th><th scope="col">浏览器本地时间</th></tr>
        </thead>
        <tbody>
          {times.map((time) => (
            <tr key={time}>
              <td>{formatTime(scheduleTimeFormatter, time)}</td>
              <td>{formatTime(browserTimeFormatter, time)}</td>
            </tr>
          ))}
        </tbody>
      </Box>
    </Box>
  )
}

type CronExpressionSectionProps = {
  cronExpression: string
  zoneId: string
  disabled: boolean
  onCronChange: (expression: string) => void
  onZoneChange: (zoneId: string) => void
}

export function CronExpressionSection({
  cronExpression, zoneId, disabled, onCronChange, onZoneChange,
}: CronExpressionSectionProps) {
  const expression = cronExpression.trim().replace(/\s+/g, ' ')
  const zone = zoneId.trim()
  const [highlightedPart, setHighlightedPart] = useState<number | null>(null)
  const [previewInput, setPreviewInput] = useState({ expression, zone })

  useEffect(() => {
    const timeout = window.setTimeout(() => setPreviewInput({ expression, zone }), 250)
    return () => window.clearTimeout(timeout)
  }, [expression, zone])

  const hasInput = Boolean(expression && zone)
  const previewReady = hasInput && previewInput.expression === expression && previewInput.zone === zone
  const preview = useQuery({
    queryKey: ['workflowSchedulePreview', previewInput.expression, previewInput.zone],
    queryFn: ({ signal }) => previewWorkflowSchedule(previewInput.expression, previewInput.zone, signal),
    enabled: previewReady,
    staleTime: 0,
    retry: false,
  })

  const updateHighlight = (input: HTMLInputElement | HTMLTextAreaElement) => {
    const beforeCursor = input.value.slice(0, input.selectionStart ?? 0)
    const fields = beforeCursor.match(/\S+/g)?.length ?? 0
    const index = fields - (/\S$/.test(beforeCursor) ? 1 : 0)
    setHighlightedPart(Math.min(index, CRON_FIELD_COLORS.length - 1))
  }

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}>
      <Stack spacing={2}>
        <Box>
          <Typography variant="body2" color="text.secondary">Cron 表达式说明</Typography>
          <CronExpressionHelp highlightedPart={highlightedPart} />
        </Box>
        <TextField
          select fullWidth size="small" label="选择模板开始" disabled={disabled}
          value={templates.includes(expression) ? expression : ''}
          slotProps={{
            inputLabel: { shrink: true },
            select: {
              displayEmpty: true,
              renderValue: (value) => typeof value === 'string' && value
                ? <TemplateOption expression={value} />
                : <Typography color="text.secondary" variant="body2" sx={{ py: 1 }}>选择 Cron 模板</Typography>,
            },
          }}
          onChange={(event) => onCronChange(event.target.value)}
        >
          <MenuItem value="" disabled>选择 Cron 模板</MenuItem>
          {templates.map((template) => (
            <MenuItem key={template} value={template} divider>
              <TemplateOption expression={template} />
            </MenuItem>
          ))}
        </TextField>
        <TextField
          fullWidth required size="small" label="Cron 表达式" placeholder="0 0 8 * * *"
          value={cronExpression} disabled={disabled}
          slotProps={{
            inputLabel: { shrink: true },
            input: { sx: { fontSize: '1.3rem' } },
            htmlInput: {
              maxLength: 255, spellCheck: false,
              onSelect: (event: SyntheticEvent<HTMLInputElement>) => updateHighlight(event.currentTarget),
              onFocus: (event: SyntheticEvent<HTMLInputElement>) => updateHighlight(event.currentTarget),
              onBlur: () => setHighlightedPart(null),
            },
          }}
          onChange={(event) => {
            onCronChange(event.target.value)
            updateHighlight(event.target)
          }}
        />
        <Autocomplete
          freeSolo openOnFocus forcePopupIcon size="small" disabled={disabled}
          options={timeZones} value={zoneId || null} inputValue={zoneId}
          openText="展开时区列表" closeText="收起时区列表" clearText="清空时区"
          onInputChange={(_, value, reason) => { if (reason === 'input') onZoneChange(value) }}
          onChange={(_, value) => onZoneChange(value ?? '')}
          renderInput={(params) => <TextField {...params} required label="选择时区" placeholder="Asia/Shanghai" />}
        />
        <Paper variant="outlined" aria-live="polite" sx={{ p: 1.5 }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            未来五次触发预览
          </Typography>
          {!hasInput ? (
            <Typography variant="body2" color="text.secondary">填写 Cron 表达式和时区后显示预览</Typography>
          ) : !previewReady || preview.isFetching ? (
            <Typography variant="body2" color="text.secondary">正在计算触发时间…</Typography>
          ) : preview.isError ? (
            <Typography variant="body2" color="error">{preview.error.message}</Typography>
          ) : preview.data?.length ? (
            <>
              <Typography variant="body2" fontWeight={600} sx={{ mb: 0.75 }}>
                {describeCron(expression)}（{zone}）
              </Typography>
              <PreviewTimes times={preview.data} zoneId={zone} />
            </>
          ) : null}
        </Paper>
      </Stack>
    </Paper>
  )
}
