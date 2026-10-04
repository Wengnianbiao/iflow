import type { ComponentType } from 'react'
import type { IconProps } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'
import type { TaskFormProps } from '@/pages/definition/EditorPanel/TaskFormTab/forms/types'
import type { TaskValidator } from '@/pages/definition/validation/types'
export type { TaskValidator, TaskValidationProblem } from '@/pages/definition/validation/types'

export type AddableTaskType = string

export type TaskFlowKind =
  | 'task'
  | 'switch'
  | 'switch_join'
  | 'fork'
  | 'join'
  | 'do_while'
  | 'terminal'

export type TaskNodeSize = { width: number; height: number }
export type TaskIcon = ComponentType<IconProps>

export type TaskCreationContext = {
  type: string
  suffix: string
  baseName: string
  taskReferenceName: string
}

export type TaskCardContentProps = {
  task: WorkflowTask
}

export type TaskPlugin = {
  type: string
  label: string
  cardLabel?: string
  icon?: TaskIcon
  menu?: {
    label: string
    description: string
    icon?: TaskIcon
  }
  create?: (context: TaskCreationContext) => WorkflowTask
  sequence?: string[]
  variableOptions?: (task: WorkflowTask) => string[]
  form?: ComponentType<TaskFormProps>
  validate?: TaskValidator
  card?: ComponentType<TaskCardContentProps> | null
  appearance?: {
    cardVariant?: 'system' | 'operator'
    shadow?: boolean
  }
  inspectorLayout?: 'bare' | 'section'
  flow?: {
    kind?: TaskFlowKind
    nodeSize?: TaskNodeSize | ((task: WorkflowTask) => TaskNodeSize)
    selectable?: boolean
    blocksContinuation?: boolean
  }
}

export type TaskPluginRegistration = Omit<Partial<TaskPlugin>, 'type'> & { type: string }

const DEFAULT_NODE_SIZE: TaskNodeSize = { width: 350, height: 100 }
const coreTaskPlugins = new Map<string, TaskPlugin>()
const extensionTaskPlugins = new Map<string, TaskPluginRegistration>()

export function registerCoreTaskPlugins(plugins: TaskPlugin[]) {
  plugins.forEach((plugin) => {
    coreTaskPlugins.set(plugin.type, plugin)
  })
}

/**
 * Registers or overrides a task plugin. Extension registrations always win
 * over the built-in catalog and the returned disposer restores the previous
 * resolution.
 */
export function registerTaskPlugin(plugin: TaskPluginRegistration) {
  const previous = extensionTaskPlugins.get(plugin.type)
  extensionTaskPlugins.set(plugin.type, plugin)
  return () => {
    if (extensionTaskPlugins.get(plugin.type) !== plugin) return
    if (previous) extensionTaskPlugins.set(plugin.type, previous)
    else extensionTaskPlugins.delete(plugin.type)
  }
}

export function getTaskPlugin(type: string) {
  const corePlugin = coreTaskPlugins.get(type)
  const extensionPlugin = extensionTaskPlugins.get(type)
  if (!extensionPlugin) return corePlugin
  return {
    ...corePlugin,
    ...extensionPlugin,
    type,
    label: extensionPlugin.label ?? corePlugin?.label ?? type,
    menu: extensionPlugin.menu ?? corePlugin?.menu,
    appearance: corePlugin?.appearance || extensionPlugin.appearance
      ? { ...corePlugin?.appearance, ...extensionPlugin.appearance }
      : undefined,
    flow: corePlugin?.flow || extensionPlugin.flow
      ? { ...corePlugin?.flow, ...extensionPlugin.flow }
      : undefined,
  } satisfies TaskPlugin
}

export function getTaskPlugins() {
  const types = new Set([...coreTaskPlugins.keys(), ...extensionTaskPlugins.keys()])
  return [...types].map((type) => getTaskPlugin(type)!).filter(Boolean)
}

export function getAddableTaskPlugins() {
  return getTaskPlugins().filter((plugin) => plugin.menu != null)
}

export function getTaskPluginLabel(type: string) {
  return getTaskPlugin(type)?.label ?? type
}

export function getTaskVariableOptions(task: WorkflowTask) {
  if (!task.taskReferenceName) return []
  return getTaskPlugin(task.type)?.variableOptions?.(task)
    ?? [`\${${task.taskReferenceName}.output}`]
}

export function getTaskAppearance(type: string) {
  return {
    cardVariant: 'system' as const,
    shadow: true,
    ...getTaskPlugin(type)?.appearance,
  }
}

export function getTaskFlowKind(type: string): TaskFlowKind {
  return getTaskPlugin(type)?.flow?.kind ?? 'task'
}

export function getTaskNodeSize(task: WorkflowTask): TaskNodeSize {
  const nodeSize = getTaskPlugin(task.type)?.flow?.nodeSize
  if (typeof nodeSize === 'function') return nodeSize(task)
  return nodeSize ?? DEFAULT_NODE_SIZE
}

export function isTaskSelectable(type: string) {
  return getTaskPlugin(type)?.flow?.selectable !== false
}

export function taskBlocksContinuation(type: string) {
  return getTaskPlugin(type)?.flow?.blocksContinuation === true
}

function createTaskContext(type: string): TaskCreationContext {
  const suffix = Math.random().toString(36).slice(2, 7)
  const baseName = type.toLowerCase()
  return {
    type,
    suffix,
    baseName,
    taskReferenceName: `${baseName}_${suffix}_ref`,
  }
}

export function createRegisteredTask(type: string): WorkflowTask {
  const context = createTaskContext(type)
  return getTaskPlugin(type)?.create?.(context) ?? {
    name: `${context.baseName}_${context.suffix}`,
    taskReferenceName: context.taskReferenceName,
    type,
    inputParameters: {},
  }
}

export function createRegisteredTaskSequence(type: AddableTaskType): WorkflowTask[] {
  const plugin = getTaskPlugin(type)
  return [type, ...(plugin?.sequence ?? [])].map(createRegisteredTask)
}
