import { useLayoutEffect, useRef, useState } from 'react'
import { Box, Button, IconButton, InputAdornment, MenuItem, Popover, Switch, Tab, Tabs, TextField, Typography } from '@mui/material'
import { CalendarBlankIcon, CaretDownIcon, CaretLeftIcon, CaretRightIcon, CheckIcon, ClockIcon, XIcon } from '@phosphor-icons/react'
import { formatExecutionTime } from '../formatters'
import { presetTimeRange, relativeTimeRange, TIME_PRESETS, TIME_UNITS, type TimeUnit } from '../timeRange'
import type { PopoverActions } from '@mui/material/Popover'
import styles from './DateControl.module.css'

type Props = { from: string; to: string; onChange: (from: string, to: string) => void }
type DateParts = { date: string; time: string }
const dayKey = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const dateParts = (value: string, fallbackTime: string): DateParts => {
  if (!value) return { date: '', time: fallbackTime }
  const date = new Date(Number(value))
  return { date: dayKey(date), time: [date.getHours(), date.getMinutes(), date.getSeconds()].map((part) => String(part).padStart(2, '0')).join(':') }
}
const rangeLabel = (from: string, to: string) => from || to
  ? `${from ? formatExecutionTime(Number(from)) : '不限'} 至 ${to ? formatExecutionTime(Number(to)) : '现在'}`
  : '选择时间范围'

/** Conductor's three time modes, calendar range selection and second-level time inputs. */
export default function DateControl({ from, to, onChange }: Props) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const popoverActions = useRef<PopoverActions>(null)
  return <div className={styles.control}>
    <span className={styles.controlLabel}>执行开始时间</span>
    <Button id="date-picker-start-time" aria-haspopup="dialog" aria-expanded={Boolean(anchor)}
      onClick={(event) => setAnchor(event.currentTarget)}
      startIcon={<CalendarBlankIcon size={16} />} endIcon={<CaretDownIcon size={12} />}
      sx={{ px: 1.25, py: 0.75, minHeight: 34, fontSize: 12, fontWeight: 500, textAlign: 'left', color: from || to || anchor ? 'primary.main' : 'text.secondary', border: '1px solid', borderColor: anchor ? 'primary.main' : 'divider', bgcolor: 'background.paper', '&:hover': { borderColor: 'primary.main', bgcolor: 'action.hover' }, '& .MuiButton-startIcon': { mr: 0.75 }, '& .MuiButton-endIcon': { ml: 1 } }}>
      {rangeLabel(from, to)}
    </Button>
    {from || to ? <IconButton size="small" aria-label="清除时间筛选" onClick={() => onChange('', '')}><XIcon size={14} /></IconButton> : null}
    <Popover action={popoverActions} open={Boolean(anchor)} anchorEl={anchor} onClose={() => setAnchor(null)}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }} transformOrigin={{ vertical: 'top', horizontal: 'left' }}
      slotProps={{ paper: { sx: { mt: 1, width: 584, maxWidth: 'calc(100vw - 32px)', border: '1px solid', borderColor: 'divider', borderRadius: '8px', boxShadow: '0 12px 40px rgba(20,35,55,.14), 0 2px 8px rgba(20,35,55,.06)' } } }}>
      {anchor ? <TimeRangePicker onLayout={() => popoverActions.current?.updatePosition()} from={from} to={to} onClose={() => setAnchor(null)} onChange={(start, end) => { onChange(start, end); setAnchor(null) }} /> : null}
    </Popover>
  </div>
}

function TimeRangePicker({ from, to, onChange, onClose, onLayout }: Props & { onClose: () => void; onLayout: () => void }) {
  const [tab, setTab] = useState(0)
  const [start, setStart] = useState(() => dateParts(from, '00:00:00'))
  const [end, setEnd] = useState(() => dateParts(to, '23:59:59'))
  const [month, setMonth] = useState(() => {
    const date = from ? new Date(Number(from)) : new Date()
    return new Date(date.getFullYear(), date.getMonth(), 1)
  })
  const [selectEnd, setSelectEnd] = useState(false)
  const [hoverDay, setHoverDay] = useState('')
  const [count, setCount] = useState('72')
  const [unit, setUnit] = useState<TimeUnit>('hours')
  const [round, setRound] = useState(true)
  const [error, setError] = useState('')
  useLayoutEffect(onLayout, [tab, error, month, onLayout])
  const relativeRange = relativeTimeRange(Number(count), unit, round)
  const applyAbsolute = () => {
    const parse = (parts: DateParts, original: string) => {
      if (!parts.date) return undefined
      if (!/^\d{4}-\d{2}-\d{2}$/.test(parts.date) || !/^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$/.test(parts.time)) return NaN
      const date = new Date(`${parts.date}T${parts.time}`)
      if (dayKey(date) !== parts.date) return NaN
      // Keep existing millisecond boundaries when reopening and applying a range.
      if (original && date.getTime() === Math.floor(Number(original) / 1000) * 1000) return Number(original)
      return date.getTime()
    }
    const first = parse(start, from)
    const last = parse(end, to)
    if (first === undefined && last === undefined) { setError('请选择时间范围'); return }
    if ((first !== undefined && (!Number.isFinite(first) || first < 0)) || (last !== undefined && (!Number.isFinite(last) || last < 0))) { setError('请输入有效的日期和时间'); return }
    if (first !== undefined && last !== undefined && first > last) { setError('起始时间不能晚于截止时间'); return }
    const inclusiveEnd = last !== undefined && end.time === '23:59:59' ? Math.floor(last / 1000) * 1000 + 999 : last
    onChange(first === undefined ? '' : String(first), inclusiveEnd === undefined ? '' : String(inclusiveEnd))
  }
  const chooseDay = (date: Date) => {
    const key = dayKey(date)
    setError('')
    if (!selectEnd || !start.date || key < start.date) {
      setStart({ date: key, time: '00:00:00' })
      setEnd({ date: '', time: '23:59:59' })
      setSelectEnd(true)
    } else {
      setEnd({ date: key, time: '23:59:59' })
      setSelectEnd(false)
    }
  }
  const firstDay = new Date(month.getFullYear(), month.getMonth(), 1).getDay()
  const weekCount = Math.ceil((firstDay + new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()) / 7)
  const days = Array.from({ length: weekCount * 7 }, (_, index) => new Date(month.getFullYear(), month.getMonth(), index - firstDay + 1))
  const today = dayKey(new Date())
  const previewEnd = selectEnd && hoverDay >= start.date ? hoverDay : end.date

  const timeField = (label: string, parts: DateParts, setParts: (parts: DateParts) => void) => <div className={styles.dateField}>
    <span className={styles.fieldLabel}>{label}</span>
    <div className={styles.dateInputGroup}>
      <label className={styles.inputRow}>
        <CalendarBlankIcon size={15} />
        <input aria-label={`${label}日期`} value={parts.date} maxLength={10} onChange={(event) => { setParts({ ...parts, date: event.target.value }); setError('') }} />
      </label>
      <label className={styles.inputRow}>
        <ClockIcon size={15} />
        <input aria-label={`${label}时分秒`} value={parts.time} maxLength={8} onChange={(event) => { setParts({ ...parts, time: event.target.value }); setError('') }} />
      </label>
    </div>
  </div>

  return <div role="dialog" aria-label="执行开始时间筛选" className={styles.picker}>
    <div className={styles.header}>
      <div>
        <div className={styles.title}><CalendarBlankIcon size={18} />执行开始时间</div>
        <p className={styles.subtitle}>按工作流的开始时间筛选执行记录</p>
      </div>
      <IconButton aria-label="关闭时间筛选" onClick={onClose} size="small"><XIcon size={18} /></IconButton>
    </div>
    <Tabs value={tab} onChange={(_, value) => { setTab(value); setError('') }} aria-label="时间范围类型"
      sx={{ px: 2.5, minHeight: 42, borderBottom: '1px solid', borderColor: 'divider', '& .MuiTab-root': { minHeight: 42, minWidth: 0, px: 2, mr: 1.5, fontSize: 13, fontWeight: 500 }, '& .MuiTabs-indicator': { height: 2 } }}>
      <Tab label="常用时间" /><Tab label="绝对时间" /><Tab label="相对时间" />
    </Tabs>
    {tab === 0 ? <div className={styles.presets}>
      <div className={styles.sectionLabel}>快捷选择</div>
      <div className={styles.presetGrid}>
        {TIME_PRESETS.map((preset) => <button type="button" key={preset.key} className={styles.preset} onClick={() => {
          const range = presetTimeRange(preset.key)
          onChange(range.from, range.to)
        }}><span>{preset.label}</span><CaretRightIcon size={14} /></button>)}
      </div>
      <div className={styles.hint}><ClockIcon size={14} />选择后可点击「搜索」查看对应记录</div>
    </div> : null}
    {tab === 1 ? <div className={styles.absolute}>
      <div className={styles.calendar}>
        <div className={styles.monthHeader}>
          <span>{month.getFullYear()} 年 {month.getMonth() + 1} 月</span>
          <div>
            <IconButton size="small" aria-label="上个月" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}><CaretLeftIcon size={15} /></IconButton>
            <IconButton size="small" aria-label="下个月" disabled={dayKey(month).slice(0, 7) >= today.slice(0, 7)} onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}><CaretRightIcon size={15} /></IconButton>
          </div>
        </div>
        <div className={styles.days} onMouseLeave={() => setHoverDay('')}>
          {['日', '一', '二', '三', '四', '五', '六'].map((day) => <span className={styles.weekday} key={day}>{day}</span>)}
          {days.map((date) => {
            const key = dayKey(date)
            if (date.getMonth() !== month.getMonth()) return <span key={key} />
            const isStart = key === start.date
            const isEnd = key === end.date
            const inRange = start.date && previewEnd && key >= start.date && key <= previewEnd
            return <div key={key} className={[styles.dayCell, inRange ? styles.inRange : '', isStart ? styles.rangeStart : '', key === previewEnd ? styles.rangeEnd : ''].join(' ')}>
              <button type="button" aria-label={key} aria-pressed={isStart || isEnd} aria-current={key === today ? 'date' : undefined}
                disabled={key > today} onClick={() => chooseDay(date)} onMouseEnter={() => setHoverDay(key)}
                className={[styles.day, isStart || isEnd ? styles.selectedDay : '', key === today ? styles.today : ''].join(' ')}>{date.getDate()}</button>
            </div>
          })}
        </div>
        <div className={styles.calendarHint}>{selectEnd ? '请选择截止日期' : '点击日期选择时间范围'}</div>
      </div>
      <div className={styles.timeFields}>
        {timeField('起始时间', start, setStart)}
        {timeField('截止时间', end, setEnd)}
        <span className={styles.timeHint}>使用本地时区 · 精确到秒</span>
      </div>
    </div> : null}
    {tab === 2 ? <div className={styles.relative}>
      <div className={styles.sectionLabel}>向前查询一段时间</div>
      <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, mt: 2 }}>
        <TextField label="最近" type="number" value={count} size="small" error={!relativeRange} slotProps={{ input: { startAdornment: <InputAdornment position="start"><ClockIcon size={16} /></InputAdornment> }, htmlInput: { min: 1 } }} onChange={(event) => setCount(event.target.value)} />
        <TextField select label="时间单位" value={unit} size="small" onChange={(event) => setUnit(event.target.value as TimeUnit)}>
          {Object.entries(TIME_UNITS).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
        </TextField>
      </Box>
      <label className={styles.roundControl}><span>按分钟取整</span><Switch size="small" checked={round} onChange={(_, checked) => setRound(checked)} slotProps={{ input: { 'aria-label': '按分钟取整' } }} /></label>
      <div className={styles.preview}><span className={styles.sectionLabel}>时间范围预览</span>
        {relativeRange ? <p>{rangeLabel(relativeRange.from, relativeRange.to)}</p> : <p role="alert" className={styles.error}>请输入有效的正整数</p>}
      </div>
    </div> : null}
    {tab !== 0 ? <div className={styles.footer}>
      <Typography role={error ? 'alert' : undefined} sx={{ fontSize: 12, color: error ? 'error.main' : 'text.secondary', mr: 'auto' }}>{error || (tab === 1 ? (start.date && end.date ? `${start.date} → ${end.date}` : '可仅设置起始或截止时间') : '截止时间为现在')}</Typography>
      <Button size="small" color="inherit" onClick={onClose}>取消</Button>
      <Button size="small" variant="contained" disabled={tab === 2 && !relativeRange} startIcon={<CheckIcon size={14} />} onClick={() => {
        if (tab === 1) applyAbsolute()
        else {
          const range = relativeTimeRange(Number(count), unit, round)
          if (range) onChange(range.from, range.to)
        }
      }}>应用</Button>
    </div> : null}
  </div>
}
