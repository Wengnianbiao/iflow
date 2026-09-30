import { useMemo, useState } from 'react'
import { Autocomplete, TextField } from '@mui/material'
import { XIcon } from '@phosphor-icons/react'
import { filterVariableOptions, replaceVariableExpression, uniqueValues } from '../../InspectorFields'

export function HttpRequestAutocomplete({
  label,
  value,
  options = [],
  variableOptions,
  placeholder,
  onChange,
}: {
  label: string
  value: string
  options?: string[]
  variableOptions: string[]
  placeholder?: string
  onChange: (value: string) => void
}) {
  const [showAllOptions, setShowAllOptions] = useState(true)
  const suggestions = useMemo(() => uniqueValues([...options, ...variableOptions]), [options, variableOptions])

  return (
    <Autocomplete
      className="http-request-autocomplete"
      freeSolo
      fullWidth
      openOnFocus
      forcePopupIcon={false}
      value={value || null}
      inputValue={value}
      options={suggestions}
      filterOptions={(items, { inputValue }) => filterVariableOptions(items, inputValue, showAllOptions)}
      onOpen={(event) => setShowAllOptions(event.type !== 'change')}
      onInputChange={(_event, nextValue, reason) => {
        if (reason === 'input') {
          setShowAllOptions(false)
          onChange(nextValue)
        }
      }}
      onChange={(_event, nextValue) => onChange(nextValue === null ? '' : replaceVariableExpression(value, nextValue))}
      clearIcon={<XIcon size={15} />}
      clearText={`清空 ${label}`}
      noOptionsText="暂无匹配选项"
      slotProps={{
        popper: { className: 'http-request-autocomplete-popper' },
      }}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder={placeholder}
          InputLabelProps={{ shrink: true }}
        />
      )}
    />
  )
}
