import { Autocomplete, Chip, TextField, createFilterOptions, type ChipProps } from '@mui/material'
import { normalizeWorkflowTags } from '@/utils/workflowTags'

const filterTags = createFilterOptions<string>()

const tagColors = ['#DBEAFE', '#DCFCE7', '#FEF3C7', '#FFEDD5', '#CCFBF1', '#FCE7F3', '#ECFCCB', '#CFFAFE', '#FFE4E6', '#FEE2E2']

// Assign varied colors by name so a tag keeps its color across renders and views.
function getTagColor(tag: string) {
  let hash = 0
  for (const character of tag.trim()) {
    hash = (Math.imul(hash, 31) + character.codePointAt(0)!) >>> 0
  }
  return tagColors[hash % tagColors.length]
}

export function WorkflowTagChip(props: ChipProps & { label: string }) {
  return <Chip size="small" {...props} sx={{
    borderRadius: '100px',
    bgcolor: getTagColor(props.label),
    color: 'text.primary',
    fontSize: 12,
    fontWeight: 400,
    maxWidth: '100%',
    ...props.sx,
  }} />
}

export function WorkflowTagsInput({
  value,
  options,
  onChange,
  disabled = false,
}: {
  value: string[]
  options: string[]
  onChange: (tags: string[]) => void
  disabled?: boolean
}) {
  const selectedTags = normalizeWorkflowTags(value)
  const availableTags = normalizeWorkflowTags(options)

  return (
    <Autocomplete
      multiple
      freeSolo
      openOnFocus
      filterSelectedOptions
      disabled={disabled}
      options={availableTags}
      value={selectedTags}
      filterOptions={(tags, params) => {
        const inputValue = params.inputValue.trim()
        const filtered = filterTags(tags, { ...params, inputValue })
        if (inputValue && !availableTags.includes(inputValue) && !selectedTags.includes(inputValue)) {
          filtered.push(inputValue)
        }
        return filtered
      }}
      onChange={(_event, tags) => onChange(normalizeWorkflowTags(tags))}
      noOptionsText="暂无可选标签"
      renderOption={(props, tag) => {
        const { key, ...rest } = props
        return <li key={key} {...rest}>{availableTags.includes(tag) ? tag : `添加 “${tag}”`}</li>
      }}
      renderTags={(tags, getTagProps) => tags.map((tag, index) => {
        const { key, ...props } = getTagProps({ index })
        return <WorkflowTagChip key={key} label={tag} {...props} />
      })}
      renderInput={(params) => (
        <TextField
          {...params}
          size="small"
          label="标签"
          placeholder="例如：HIS、门诊"
          helperText="输入标签名称后，点击下拉选项添加"
        />
      )}
    />
  )
}
