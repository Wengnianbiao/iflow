import type { WorkflowDef, WorkflowTask } from '@/types/workflow'

const WORKFLOW_DRAFT_PREFIX = 'iflow:workflow-draft:'

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function unwrapDefinition(value: unknown): unknown {
  if (Array.isArray(value)) {
    if (value.length !== 1) {
      throw new Error('一次只能导入一个工作流定义')
    }
    return value[0]
  }

  if (isRecord(value) && 'data' in value) return unwrapDefinition(value.data)
  return value
}

export function normalizeWorkflowDraft(value: unknown): WorkflowDef {
  const raw = unwrapDefinition(value)
  if (!isRecord(raw)) throw new Error('文件内容不是有效的工作流 JSON 对象')

  const name = typeof raw.name === 'string'
    ? raw.name.trim()
    : typeof raw.uniqueName === 'string'
      ? raw.uniqueName.trim()
      : ''
  if (!name) throw new Error('工作流定义缺少 name 字段')

  const rawTasks = Array.isArray(raw.tasks)
    ? raw.tasks
    : Array.isArray(raw.nodes)
      ? raw.nodes
      : null
  if (!rawTasks) throw new Error('工作流定义缺少 tasks 数组')

  const invalidTask = rawTasks.find((task) => (
    !isRecord(task)
      || typeof task.type !== 'string'
      || typeof task.taskReferenceName !== 'string'
  ))
  if (invalidTask) throw new Error('tasks 中存在缺少 type 或 taskReferenceName 的任务')

  const parsedVersion = Number(raw.version)
  const version = Number.isInteger(parsedVersion) && parsedVersion > 0 ? parsedVersion : 1
  const { createTime: _createTime, updateTime: _updateTime, nodes: _nodes, ...definition } = raw

  return {
    ...definition,
    name,
    version,
    tasks: rawTasks as WorkflowTask[],
    inputParameters: Array.isArray(raw.inputParameters)
      ? raw.inputParameters.filter((item): item is string => typeof item === 'string')
      : [],
    outputParameters: isRecord(raw.outputParameters) ? raw.outputParameters : {},
    workflowStatusListenerEnabled: raw.workflowStatusListenerEnabled === true,
    timeoutSeconds: typeof raw.timeoutSeconds === 'number' ? raw.timeoutSeconds : 0,
    timeoutPolicy: typeof raw.timeoutPolicy === 'string' ? raw.timeoutPolicy : 'ALERT_ONLY',
  }
}

function createDraftId() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function stageWorkflowDraft(workflow: WorkflowDef) {
  const draftId = createDraftId()
  window.sessionStorage.setItem(
    `${WORKFLOW_DRAFT_PREFIX}${draftId}`,
    JSON.stringify(workflow),
  )
  return draftId
}

export function readWorkflowDraft(draftId?: string): WorkflowDef | null {
  if (!draftId || typeof window === 'undefined') return null

  try {
    const stored = window.sessionStorage.getItem(`${WORKFLOW_DRAFT_PREFIX}${draftId}`)
    return stored ? normalizeWorkflowDraft(JSON.parse(stored)) : null
  } catch {
    return null
  }
}
