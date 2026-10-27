import {
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  IconButton,
  MenuItem,
  Radio,
  RadioGroup,
  TextField,
  Typography,
} from '@mui/material'
import { PlusIcon, XIcon } from '@phosphor-icons/react'
import { InlineScriptEditor } from '@/pages/definition/EditorPanel/TaskFormTab/forms/InlineScriptEditor'
import {
  BODY_FORMATS,
  emptyBodyField,
  type RequestBody,
  type RequestBodyField,
  type RequestBodyFormat,
  type RequestBodyMode,
} from './gatewayInput'
import styles from './RequestBodyEditor.module.css'

export function RequestBodyEditor({
  value,
  path,
  onChange,
}: {
  value: RequestBody
  path?: string
  onChange: (value: RequestBody) => void
}) {
  const format = BODY_FORMATS.find((format) => format.value === value.format)!
  const updateField = (index: number, patch: Partial<RequestBodyField>) => {
    onChange({
      ...value,
      fields: value.fields.map((field, current) => (current === index ? { ...field, ...patch } : field)),
    })
  }

  return (
    <Box sx={{ mt: 3 }}>
      <div className={styles.requestLine}>
        <strong>POST</strong>
        <code>{path ? `/${path}` : '选择工作流后显示请求路径'}</code>
      </div>
      <div className={styles.bodyHeading}>
        <span>Body</span>
      </div>
      <div className={styles.toolbar}>
        <RadioGroup
          row
          aria-label="请求体类型"
          value={value.mode}
          onChange={(event) => onChange({ ...value, mode: event.target.value as RequestBodyMode })}
        >
          <FormControlLabel value="none" control={<Radio size="small" />} label="无请求体" />
          <FormControlLabel value="form-data" control={<Radio size="small" />} label="form-data" />
          <FormControlLabel value="urlencoded" control={<Radio size="small" />} label="x-www-form-urlencoded" />
          <span className={styles.rawControls}>
            <FormControlLabel value="raw" control={<Radio size="small" />} label="raw" />
            {value.mode === 'raw' ? (
              <TextField
                select
                size="small"
                variant="standard"
                value={value.format}
                slotProps={{ select: { SelectDisplayProps: { 'aria-label': '原始报文格式' } } }}
                onChange={(event) => onChange({ ...value, format: event.target.value as RequestBodyFormat })}
              >
                {BODY_FORMATS.map((format) => (
                  <MenuItem key={format.value} value={format.value}>
                    {format.label}
                  </MenuItem>
                ))}
              </TextField>
            ) : null}
          </span>
        </RadioGroup>
      </div>
      {value.mode === 'raw' ? (
        <InlineScriptEditor
          key={value.format}
          value={value.text}
          language={format.language}
          languageLabel={format.label}
          codeLabel="请求体"
          modalTitle="请求体 body"
          minHeight={400}
          autoSize={false}
          onChange={(text) => onChange({ ...value, text })}
        />
      ) : value.mode === 'none' ? (
        <div className={styles.empty}>此请求不包含请求体。</div>
      ) : (
        <div className={styles.fields}>
          <table aria-label="请求体表单字段">
            <thead>
              <tr>
                <th aria-label="启用" />
                <th>键（Key）</th>
                {value.mode === 'form-data' ? <th>类型</th> : null}
                <th>值（Value）</th>
                <th aria-label="操作" />
              </tr>
            </thead>
            <tbody>
              {value.fields.map((field, index) => (
                <tr key={index}>
                  <td>
                    <Checkbox
                      size="small"
                      checked={field.enabled}
                      slotProps={{ input: { 'aria-label': `启用字段 ${index + 1}` } }}
                      onChange={(_, enabled) => updateField(index, { enabled })}
                    />
                  </td>
                  <td>
                    <TextField
                      size="small"
                      variant="standard"
                      placeholder="Key"
                      fullWidth
                      value={field.name}
                      slotProps={{ htmlInput: { 'aria-label': `字段 ${index + 1} 名称` } }}
                      onChange={(event) => updateField(index, { name: event.target.value })}
                    />
                  </td>
                  {value.mode === 'form-data' ? (
                    <td>
                      <TextField
                        select
                        size="small"
                        variant="standard"
                        value={field.kind}
                        slotProps={{ select: { SelectDisplayProps: { 'aria-label': `字段 ${index + 1} 类型` } } }}
                        onChange={(event) =>
                          updateField(index, { kind: event.target.value as 'text' | 'file', file: undefined })
                        }
                      >
                        <MenuItem value="text">文本</MenuItem>
                        <MenuItem value="file">文件</MenuItem>
                      </TextField>
                    </td>
                  ) : null}
                  <td>
                    {value.mode === 'form-data' && field.kind === 'file' ? (
                      <Button component="label" size="small" className={styles.fileButton}>
                        {field.file?.name ?? '选择文件'}
                        <input
                          type="file"
                          hidden
                          aria-label={`字段 ${index + 1} 文件`}
                          onChange={(event) => updateField(index, { file: event.target.files?.[0] })}
                        />
                      </Button>
                    ) : (
                      <TextField
                        size="small"
                        variant="standard"
                        placeholder="Value"
                        fullWidth
                        value={field.value}
                        slotProps={{ htmlInput: { 'aria-label': `字段 ${index + 1} 值` } }}
                        onChange={(event) => updateField(index, { value: event.target.value })}
                      />
                    )}
                  </td>
                  <td>
                    <IconButton
                      size="small"
                      aria-label={`删除字段 ${index + 1}`}
                      onClick={() =>
                        onChange({
                          ...value,
                          fields:
                            value.fields.length === 1
                              ? [emptyBodyField()]
                              : value.fields.filter((_, current) => current !== index),
                        })
                      }
                    >
                      <XIcon size={14} />
                    </IconButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Button
            size="small"
            startIcon={<PlusIcon size={14} />}
            onClick={() => onChange({ ...value, fields: [...value.fields, emptyBodyField()] })}
          >
            添加字段
          </Button>
        </div>
      )}
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        直接填写接口请求体。恢复入参时自动选择报文类型和编辑格式。
      </Typography>
    </Box>
  )
}
