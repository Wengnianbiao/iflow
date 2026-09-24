import { useMemo, useState } from 'react'
import { Helmet } from 'react-helmet'
import { Link as RouterLink } from 'react-router-dom'
import { Alert, Autocomplete, Box, Button, Paper, TextField, Typography } from '@mui/material'
import { PlayCircleIcon } from '@phosphor-icons/react'
import { startWorkflow } from '@/api/workflowExecution'
import { SectionContainer } from '@/components/layout/SectionContainer'
import { SectionHeader } from '@/components/layout/SectionHeader'
import type { WorkflowDef } from '@/types/workflow'
import { useWorkflowDefs } from '@/utils/query'

type WorkflowOption = {
  key: string
  name: string
  version: number
  displayName: string
  description?: string
  inputParameters: string[]
}

function toOption(definition: WorkflowDef): WorkflowOption {
  return {
    key: `${definition.name}@${definition.version}`,
    name: definition.name,
    version: definition.version,
    displayName: definition.displayName?.trim() || definition.name,
    description: definition.description?.trim() || undefined,
    inputParameters: definition.inputParameters ?? [],
  }
}

function inputTemplate(parameters: string[]) {
  if (!parameters.length) return '{\n}'
  return JSON.stringify(Object.fromEntries(parameters.map((name) => [name, ''])), null, 2)
}

function parseInput(text: string): Record<string, unknown> {
  const source = text.trim() || '{}'
  const value: unknown = JSON.parse(source)
  if (value == null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('输入必须是 JSON 对象')
  }
  return value as Record<string, unknown>
}

export function InvokeWorkflowPage() {
  const workflowDefs = useWorkflowDefs()
  const options = useMemo(
    () => (workflowDefs.data ?? [])
      .map(toOption)
      .sort((left, right) => left.displayName.localeCompare(right.displayName, 'zh-CN') || right.version - left.version),
    [workflowDefs.data],
  )
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [inputText, setInputText] = useState('{\n}')
  const [inputError, setInputError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [workflowId, setWorkflowId] = useState<string | null>(null)

  const selected = options.find((option) => option.key === selectedKey) ?? null

  const selectWorkflow = (option: WorkflowOption | null) => {
    setSelectedKey(option?.key ?? null)
    setInputText(inputTemplate(option?.inputParameters ?? []))
    setInputError(null)
    setError(null)
    setWorkflowId(null)
  }

  const submit = async () => {
    if (!selected) return
    let input: Record<string, unknown>
    try {
      input = parseInput(inputText)
      setInputError(null)
    } catch (cause) {
      setInputError(cause instanceof SyntaxError ? '输入不是合法 JSON' : cause instanceof Error ? cause.message : '输入不是合法 JSON')
      return
    }

    setBusy(true)
    setError(null)
    setWorkflowId(null)
    try {
      setWorkflowId(await startWorkflow(selected.name, selected.version, input))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '启动工作流失败')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Helmet>
        <title>调用工作流</title>
      </Helmet>
      <SectionHeader title="调用工作流" />
      <SectionContainer>
        <Paper variant="outlined" sx={{ maxWidth: 760 }}>
          <Box component="form" sx={{ p: '24px', display: 'grid', gap: '20px' }} onSubmit={(event) => {
            event.preventDefault()
            void submit()
          }}>
            <Autocomplete
              id="invoke-workflow"
              openOnFocus
              forcePopupIcon
              fullWidth
              size="small"
              options={options}
              value={selected}
              loading={workflowDefs.isFetching}
              loadingText="加载工作流中…"
              noOptionsText={workflowDefs.isError ? '工作流列表加载失败' : '没有可调用的工作流'}
              openText="展开工作流列表"
              closeText="收起工作流列表"
              clearText="清空"
              getOptionLabel={(option) => `${option.displayName}（v${option.version}）`}
              isOptionEqualToValue={(option, value) => option.key === value.key}
              onChange={(_, option) => selectWorkflow(option)}
              renderOption={(props, option) => {
                const { key, ...rest } = props
                return (
                  <li key={key} {...rest}>
                    <Box sx={{ py: 0.5 }}>
                      <Typography variant="body2">{option.displayName}（v{option.version}）</Typography>
                      <Typography variant="caption" color="text.secondary">{option.name}</Typography>
                    </Box>
                  </li>
                )
              }}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="工作流"
                  placeholder="选择要调用的工作流"
                  slotProps={{ inputLabel: { shrink: true } }}
                  error={workflowDefs.isError}
                  helperText={workflowDefs.isError ? '工作流列表加载失败，请刷新重试' : selected?.description}
                />
              )}
            />
            <TextField
              id="invoke-workflow-input"
              label="输入"
              fullWidth
              multiline
              minRows={10}
              value={inputText}
              spellCheck={false}
              error={Boolean(inputError)}
              helperText={inputError ?? (
                selected?.inputParameters.length
                  ? `已按声明的入参生成模板：${selected.inputParameters.join('、')}`
                  : '填写 JSON 对象。未声明入参时可以直接提交 {}'
              )}
              slotProps={{
                inputLabel: { shrink: true },
                htmlInput: {
                  style: { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace', fontSize: 13 },
                },
              }}
              onChange={(event) => {
                setInputText(event.target.value)
                setInputError(null)
              }}
            />
            {error ? <Alert severity="error">{error}</Alert> : null}
            {workflowId ? (
              <Alert
                severity="success"
                action={<Button color="inherit" component={RouterLink} to={`/executions/${encodeURIComponent(workflowId)}`}>查看执行</Button>}
              >
                已启动，实例 ID：{workflowId}
              </Alert>
            ) : null}
            <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button
                type="submit"
                variant="contained"
                disabled={!selected || busy}
                startIcon={<PlayCircleIcon size={18} />}
              >
                {busy ? '调用中…' : '调用'}
              </Button>
            </Box>
          </Box>
        </Paper>
      </SectionContainer>
    </>
  )
}
