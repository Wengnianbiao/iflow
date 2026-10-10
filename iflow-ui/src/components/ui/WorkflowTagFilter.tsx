import { useId, useState } from 'react'
import {
  Box,
  Button,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
  InputAdornment,
  List,
  ListItem,
  Popover,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { MagnifyingGlassIcon, TagIcon } from '@phosphor-icons/react'
import { WorkflowTagChip } from './WorkflowTagsInput'

type WorkflowTagFilterProps = {
  options: string[]
  selectedTags: string[]
  onChange: (tags: string[]) => void
}

/** Conductor-style toolbar filter for iFlow's string tags. */
export function WorkflowTagFilter({
  options,
  selectedTags,
  onChange,
}: WorkflowTagFilterProps) {
  const dialogId = useId()
  const titleId = `${dialogId}-title`
  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null)
  const [searchTerm, setSearchTerm] = useState('')
  const keyword = searchTerm.trim().toLocaleLowerCase()
  const filteredTags = options.filter((tag) => tag.toLocaleLowerCase().includes(keyword))
  const open = Boolean(anchorEl)

  const toggleTag = (tag: string) => {
    onChange(selectedTags.includes(tag)
      ? selectedTags.filter((selectedTag) => selectedTag !== tag)
      : [...selectedTags, tag])
  }

  const handleClose = () => {
    setAnchorEl(null)
    setSearchTerm('')
  }

  return (
    <>
      <Tooltip title="按标签筛选">
        <Button
          size="small"
          variant="text"
          color={selectedTags.length ? 'primary' : 'inherit'}
          startIcon={<TagIcon size={17} />}
          aria-haspopup="dialog"
          aria-expanded={open}
          aria-controls={open ? dialogId : undefined}
          onClick={(event) => setAnchorEl(event.currentTarget)}
          sx={{
            width: 'fit-content',
            color: selectedTags.length ? 'primary.main' : 'text.secondary',
            fontWeight: 400,
          }}
        >
          标签过滤{selectedTags.length ? ` (${selectedTags.length})` : ''}
        </Button>
      </Tooltip>
      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{
          paper: {
            id: dialogId,
            role: 'dialog',
            'aria-modal': true,
            'aria-labelledby': titleId,
            sx: { p: 2, width: 400, maxWidth: 'calc(100vw - 32px)', maxHeight: 600, borderRadius: '6px' },
          },
        }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Typography id={titleId} component="h2" sx={{ fontSize: 16, fontWeight: 500 }}>
            按标签筛选
          </Typography>
          {selectedTags.length ? (
            <Button size="small" onClick={() => onChange([])}>清空全部</Button>
          ) : null}
        </Box>
        <TextField
          autoFocus
          fullWidth
          size="small"
          placeholder="搜索标签…"
          value={searchTerm}
          onChange={(event) => setSearchTerm(event.target.value)}
          slotProps={{
            htmlInput: { 'aria-label': '搜索标签' },
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <MagnifyingGlassIcon size={16} />
                </InputAdornment>
              ),
            },
          }}
          sx={{ mb: 1 }}
        />
        <Divider />
        {!options.length ? (
          <Typography color="text.secondary" sx={{ p: 2, fontSize: 13, fontStyle: 'italic' }}>
            暂无标签
          </Typography>
        ) : (
          <List dense sx={{ maxHeight: 300, overflow: 'auto' }}>
            {filteredTags.map((tag) => (
              <ListItem key={tag} sx={{ py: 0 }}>
                <FormControlLabel
                  control={(
                    <Checkbox
                      size="small"
                      checked={selectedTags.includes(tag)}
                      onChange={() => toggleTag(tag)}
                    />
                  )}
                  label={tag}
                  sx={{ width: '100%', '& .MuiFormControlLabel-label': { fontSize: 13, overflowWrap: 'anywhere' } }}
                />
              </ListItem>
            ))}
            {!filteredTags.length ? (
              <ListItem>
                <Typography color="text.secondary" sx={{ fontSize: 13 }}>
                  没有匹配“{searchTerm}”的标签
                </Typography>
              </ListItem>
            ) : null}
          </List>
        )}
        {selectedTags.length ? (
          <>
            <Divider sx={{ my: 2 }} />
            <Typography sx={{ fontSize: 13, fontWeight: 500, mb: 1 }}>
              已选标签 ({selectedTags.length})
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
              {selectedTags.slice(0, 5).map((tag) => (
                <WorkflowTagChip key={tag} label={tag} onDelete={() => toggleTag(tag)} />
              ))}
              {selectedTags.length > 5 ? (
                <Chip size="small" variant="outlined" label={`另 ${selectedTags.length - 5} 个`} />
              ) : null}
            </Box>
          </>
        ) : null}
      </Popover>
    </>
  )
}
