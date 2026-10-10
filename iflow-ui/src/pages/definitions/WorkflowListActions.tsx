import { useRef, useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, ButtonGroup, Menu, MenuItem } from '@mui/material'
import {
  CaretCircleDownIcon,
  FileArrowUpIcon,
  PlayCircleIcon,
  PlusCircleIcon,
  SquaresFourIcon,
} from '@phosphor-icons/react'
import { SectionHeaderActions } from '@/components/layout/SectionHeaderActions'
import { useMessageContext } from '@/components/providers/messageContext'
import type { WorkflowDef } from '@/types/workflow'
import { useWorkflowDefs } from '@/utils/query'
import { normalizeWorkflowDraft, stageWorkflowDraft } from '@/utils/workflowDraft'
import { WorkflowTemplateDialog } from './dialog/WorkflowTemplateDialog'
import type { WorkflowTemplate } from './workflowTemplates'
import styles from './WorkflowListActions.module.css'

function nextAvailableWorkflowRoute(route: string, definitions: WorkflowDef[]) {
  const existingRoutes = new Set(definitions.map((definition) => definition.route))
  if (!existingRoutes.has(route)) return route

  let index = 2
  while (existingRoutes.has(`${route}_${index}`)) index += 1
  return `${route}_${index}`
}

function newWorkflowUrl(route: string, draftId?: string) {
  const search = new URLSearchParams({ route, version: '1', new: 'true' })
  if (draftId) search.set('draft', draftId)
  return `/workflowDef/edit?${search.toString()}`
}

/** Shared run/create actions for the definition and execution lists. */
export function WorkflowListActions() {
  const navigate = useNavigate()
  const { setMessage } = useMessageContext()
  const { data } = useWorkflowDefs()
  const importInputRef = useRef<HTMLInputElement>(null)
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false)
  const [createMenuAnchor, setCreateMenuAnchor] = useState<HTMLElement | null>(null)

  const openWorkflowDraft = (workflow: WorkflowDef) => {
    try {
      const draftId = stageWorkflowDraft(workflow)
      navigate(newWorkflowUrl(workflow.route, draftId))
    } catch {
      setMessage({
        severity: 'error',
        text: '暂存工作流草稿失败，请检查浏览器存储权限后重试',
      })
    }
  }

  const handleImport = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    if (file.size > 5 * 1024 * 1024) {
      setMessage({ severity: 'error', text: '工作流文件不能超过 5 MB' })
      return
    }

    try {
      const imported = normalizeWorkflowDraft(JSON.parse(await file.text()))
      const route = nextAvailableWorkflowRoute(imported.route, data ?? [])
      openWorkflowDraft({
        ...imported,
        route,
        version: 1,
        name: route === imported.route
          ? imported.name
          : `${imported.name?.trim() || imported.route}（导入）`,
      })
    } catch (reason) {
      setMessage({
        severity: 'error',
        text: reason instanceof Error ? reason.message : '解析工作流文件失败',
      })
    }
  }

  const handleTemplateSelect = (template: WorkflowTemplate) => {
    const route = nextAvailableWorkflowRoute(template.route, data ?? [])
    setTemplateDialogOpen(false)
    openWorkflowDraft(template.create(route))
  }

  return (
    <>
      <SectionHeaderActions
        buttons={[
          {
            key: 'run-workflow',
            label: '执行工作流',
            variant: 'outlined',
            className: styles.actionButton,
            startIcon: <PlayCircleIcon size={20} />,
            onClick: () => navigate('/runWorkflow'),
          },
          {
            key: 'create-workflow',
            customButtonElement: (
              <ButtonGroup
                variant="contained"
                disableElevation
                aria-label="新建工作流操作"
                className={styles.createWorkflowGroup}
              >
                <Button
                  startIcon={<PlusCircleIcon size={20} />}
                  className={styles.actionButton}
                  onClick={() => navigate(newWorkflowUrl(
                    nextAvailableWorkflowRoute('new_workflow', data ?? []),
                  ))}
                >
                  新建工作流
                </Button>
                <Button
                  className={styles.createWorkflowMenuButton}
                  aria-label="更多新建工作流方式"
                  aria-controls={createMenuAnchor ? 'create-workflow-menu' : undefined}
                  aria-haspopup="menu"
                  aria-expanded={createMenuAnchor ? 'true' : undefined}
                  onClick={(event) => setCreateMenuAnchor(event.currentTarget)}
                >
                  <CaretCircleDownIcon size={20} />
                </Button>
              </ButtonGroup>
            ),
          },
        ]}
      />
      <Menu
        id="create-workflow-menu"
        anchorEl={createMenuAnchor}
        open={Boolean(createMenuAnchor)}
        onClose={() => setCreateMenuAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ paper: { className: styles.createWorkflowMenu } }}
      >
        <MenuItem
          onClick={() => {
            setCreateMenuAnchor(null)
            importInputRef.current?.click()
          }}
        >
          <FileArrowUpIcon size={18} />
          导入工作流
        </MenuItem>
        <MenuItem
          onClick={() => {
            setCreateMenuAnchor(null)
            setTemplateDialogOpen(true)
          }}
        >
          <SquaresFourIcon size={18} />
          选择工作流模板
        </MenuItem>
      </Menu>
      <WorkflowTemplateDialog
        open={templateDialogOpen}
        onClose={() => setTemplateDialogOpen(false)}
        onSelect={handleTemplateSelect}
      />
      <input
        ref={importInputRef}
        className={styles.hiddenFileInput}
        type="file"
        accept=".json,application/json"
        aria-label="导入工作流 JSON 文件"
        onChange={(event) => void handleImport(event)}
      />
    </>
  )
}
