import type { WorkflowDef, WorkflowTask } from '@/types/workflow'
import { getTaskPlugin } from '@/tasks/taskPluginRegistry'
import type { WorkflowProblem } from './types'

export const pointerKey = (key: string) => key.replace(/~/g, '~0').replace(/\//g, '~1')
export function pointerValue(value: unknown, path: string): unknown {
  return path.split('/').slice(1).reduce<unknown>((current, key) => current !== null && typeof current === 'object'
    ? (current as Record<string, unknown>)[key.replace(/~1/g, '/').replace(/~0/g, '~')] : undefined, value)
}
export function findTaskPath(workflow: WorkflowDef, referenceName: string): string | undefined {
  const walk = (tasks: WorkflowTask[], base: string): string | undefined => {
    for (let index = 0; index < tasks.length; index++) {
      const task = tasks[index]; const path = `${base}/${index}`
      if (task.taskReferenceName === referenceName) return path
      const branches: Array<[WorkflowTask[], string]> = [
        ...Object.entries(task.decisionCases ?? {}).map(([key, branch]): [WorkflowTask[], string] => [branch, `${path}/decisionCases/${pointerKey(key)}`]),
        [task.defaultCase ?? [], `${path}/defaultCase`], [task.loopOver ?? [], `${path}/loopOver`],
        ...(task.forkTasks ?? []).map((branch, i): [WorkflowTask[], string] => [branch, `${path}/forkTasks/${i}`]),
      ]
      for (const [branch, branchPath] of branches) { const found = walk(branch, branchPath); if (found) return found }
    }
  }
  return walk(workflow.tasks, '/tasks')
}

export function replaceTaskAtPath(workflow: WorkflowDef, path: string, task: WorkflowTask): WorkflowDef {
  const keys = path.split('/').slice(1).map((key) => key.replace(/~1/g, '/').replace(/~0/g, '~'))
  const replace = (value: unknown, index: number): unknown => {
    if (index === keys.length) return task
    if (Array.isArray(value)) return value.map((item, i) => String(i) === keys[index] ? replace(item, index + 1) : item)
    if (value !== null && typeof value === 'object') {
      const object = value as Record<string, unknown>
      return { ...object, [keys[index]]: replace(object[keys[index]], index + 1) }
    }
    return value
  }
  return replace(workflow, 0) as WorkflowDef
}
const record = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value)

export function validateWorkflow(value: unknown): { workflow?: WorkflowDef; problems: WorkflowProblem[]; diagramSafe: boolean } {
  const problems: WorkflowProblem[] = []
  const tasks: Array<{ task: WorkflowTask; path: string }> = []
  const add = (path: string, code: string, message: string, structural = false, taskPath?: string) => {
    problems.push({ id: `${code}:${path}`, path, code, message, structural, taskPath, source: 'definition', severity: 'error' })
  }
  const string = (object: Record<string, unknown>, key: string, path: string, required = false, taskPath?: string) => {
    // Backend optional fields can be serialized as null. Treat them as unset,
    // while retaining the original definition and checking required task config.
    if (!required && object[key] == null) return
    if (object[key] != null && typeof object[key] !== 'string') add(`${path}/${key}`, 'STRING', `${key} 必须是字符串`, true, taskPath)
    else if (required && (typeof object[key] !== 'string' || !object[key].trim())) {
      const label = ({ route: '路由', name: taskPath ? '任务名称' : '工作流名称', description: '工作流描述', taskReferenceName: '任务引用名称', type: '任务类型' } as Record<string, string>)[key] ?? key
      add(`${path}/${key}`, 'REQUIRED', `请填写${label}`, object[key] == null && !['name', 'description'].includes(key), taskPath)
    }
  }
  const objectField = (object: Record<string, unknown>, key: string, path: string, taskPath?: string) => {
    if (object[key] != null && !record(object[key])) add(`${path}/${key}`, 'OBJECT', `${key} 必须是 JSON 对象`, true, taskPath)
  }
  const stringArray = (object: Record<string, unknown>, key: string, path: string, taskPath?: string) => {
    if (object[key] != null && (!Array.isArray(object[key]) || object[key].some((entry: unknown) => typeof entry !== 'string'))) {
      add(`${path}/${key}`, 'STRING_ARRAY', `${key} 必须是字符串数组`, true, taskPath)
    }
  }
  const walk = (list: unknown, path: string) => {
    if (!Array.isArray(list)) { add(path, 'TASK_ARRAY', '任务列表必须是数组', true); return }
    list.forEach((item: unknown, index: number) => {
      const taskPath = `${path}/${index}`
      if (!record(item)) { add(taskPath, 'TASK_OBJECT', '任务必须是 JSON 对象，不能是 null 或其他值', true); return }
      tasks.push({ task: item as unknown as WorkflowTask, path: taskPath })
      string(item, 'type', taskPath, true, taskPath)
      string(item, 'taskReferenceName', taskPath, true, taskPath)
      string(item, 'name', taskPath, false, taskPath)
      for (const key of ['description', 'expression', 'evaluatorType', 'loopCondition', 'items']) string(item, key, taskPath, false, taskPath)
      objectField(item, 'inputParameters', taskPath, taskPath)
      stringArray(item, 'joinOn', taskPath, taskPath)
      stringArray(item, 'defaultExclusiveJoinTask', taskPath, taskPath)
      if (item.optional !== undefined && typeof item.optional !== 'boolean') add(`${taskPath}/optional`, 'BOOLEAN', 'optional 必须是布尔值', true, taskPath)
      if (item.decisionCases != null) {
        if (!record(item.decisionCases)) add(`${taskPath}/decisionCases`, 'OBJECT', 'decisionCases 必须是分支对象', true, taskPath)
        else Object.entries(item.decisionCases).forEach(([key, branch]) => walk(branch, `${taskPath}/decisionCases/${pointerKey(key)}`))
      }
      for (const key of ['defaultCase', 'loopOver']) if (item[key] != null) walk(item[key], `${taskPath}/${key}`)
      if (item.forkTasks != null) {
        if (!Array.isArray(item.forkTasks)) add(`${taskPath}/forkTasks`, 'TASK_ARRAY', 'forkTasks 必须是分支数组', true, taskPath)
        else item.forkTasks.forEach((branch, i) => walk(branch, `${taskPath}/forkTasks/${i}`))
      }
    })
  }
  if (!record(value)) { add('', 'WORKFLOW_OBJECT', '工作流定义必须是 JSON 对象', true); return { problems, diagramSafe: false } }
  string(value, 'route', '', true)
  if (typeof value.route === 'string' && value.route.trim() && !/^[A-Za-z0-9][A-Za-z0-9._/-]{0,254}$/.test(value.route)) add('/route', 'ROUTE_FORMAT', '路由须以字母或数字开头，仅含 A-Za-z0-9._/-，最多 255 个字符')
  string(value, 'name', '', true)
  string(value, 'description', '', true)
  string(value, 'correlationExpression', '')
  string(value, 'timeoutPolicy', '')
  if (!Number.isInteger(value.version) || Number(value.version) < 1) add('/version', 'VERSION', 'version 必须是大于 0 的整数')
  stringArray(value, 'inputParameters', '')
  stringArray(value, 'tags', '')
  objectField(value, 'outputParameters', '')
  if (value.timeoutSeconds !== undefined && (typeof value.timeoutSeconds !== 'number' || value.timeoutSeconds < 0)) add('/timeoutSeconds', 'TIMEOUT', 'timeoutSeconds 必须是非负数字')
  walk(value.tasks, '/tasks')
  if (problems.some((problem) => problem.structural)) return { problems, diagramSafe: false }

  const workflow = value as unknown as WorkflowDef
  const references = new Map<string, string[]>()
  tasks.forEach(({ task, path }) => references.set(task.taskReferenceName, [...(references.get(task.taskReferenceName) ?? []), path]))
  const reservedReferences = new Set(['__start__', '__end__', ...tasks.filter(({ task }) => getTaskPlugin(task.type)?.flow?.kind === 'switch').map(({ task }) => `${task.taskReferenceName}_switch_join`)])
  tasks.forEach(({ task, path }) => {
    if ((references.get(task.taskReferenceName)?.length ?? 0) > 1) add(`${path}/taskReferenceName`, 'DUPLICATE_REF', `任务引用名称「${task.taskReferenceName}」重复，请使用唯一名称`, false, path)
    if (reservedReferences.has(task.taskReferenceName)) add(`${path}/taskReferenceName`, 'RESERVED_REF', '该引用名称与画布的起止或分支汇聚节点冲突，请使用其他名称', false, path)
    for (const key of ['joinOn', 'defaultExclusiveJoinTask'] as const) {
      task[key]?.forEach((ref, index) => {
        if (!references.has(ref) || ref === task.taskReferenceName) add(`${path}/${key}/${index}`, 'MISSING_REF', `${key} 引用「${ref}」不存在或指向任务自身`, false, path)
      })
    }
    // Validation belongs to the task plugin, so custom task types remain extensible.
    try {
      getTaskPlugin(task.type)?.validate?.(task, workflow).forEach((problem) => {
        const fullPath = `${path}${problem.path}`
        problems.push({ ...problem, id: `${problem.code}:${fullPath}`, path: fullPath, taskPath: path, taskReferenceName: task.taskReferenceName, source: 'definition', severity: problem.severity ?? 'error' })
      })
    } catch {
      add(path, 'VALIDATOR_FAILED', `无法校验 ${task.type} 任务，请检查该任务的校验插件`, false, path)
    }
  })
  problems.forEach((problem) => { if (problem.taskPath) problem.taskReferenceName = tasks.find(({ path }) => path === problem.taskPath)?.task.taskReferenceName })
  const diagramSafe = !problems.some((problem) => ['DUPLICATE_REF', 'RESERVED_REF'].includes(problem.code)) && tasks.every(({ task }) => task.taskReferenceName.trim() && task.type.trim())
  return { workflow, problems, diagramSafe }
}

export function validateSource(source: string) {
  try { return validateWorkflow(JSON.parse(source)) }
  catch (error) {
    const detail = error instanceof Error ? error.message : 'JSON 格式不正确'
    const position = detail.match(/position (\d+)/)?.[1]
    const problem: WorkflowProblem = { id: 'JSON_SYNTAX', code: 'JSON_SYNTAX', path: '', message: `源码 JSON 格式不正确：${detail}`, severity: 'error', source: 'definition', structural: true, offset: position ? Number(position) : source.length }
    return { workflow: undefined, problems: [problem], diagramSafe: false }
  }
}

/** Locates by JSON pointer, including nested tasks and duplicate references. */
export function sourceRange(source: string, pointer: string): { start: number; end: number } {
  let cursor = 0
  const ranges = new Map<string, { start: number; end: number }>()
  const whitespace = () => { while (/\s/.test(source[cursor] ?? '') && cursor < source.length) cursor++ }
  const quoted = () => { const start = cursor++; while (cursor < source.length) { const c = source[cursor++]; if (c === '\\') cursor++; else if (c === '"') break } return JSON.parse(source.slice(start, cursor)) as string }
  const walk = (path: string) => {
    whitespace(); const start = cursor
    if (source[cursor] === '{') {
      cursor++; whitespace()
      while (cursor < source.length && source[cursor] !== '}') {
        const key = quoted(); whitespace(); cursor++; walk(`${path}/${pointerKey(key)}`); whitespace()
        if (source[cursor] !== ',') break
        cursor++; whitespace()
      }
      cursor++
    } else if (source[cursor] === '[') {
      cursor++; whitespace(); let index = 0
      while (cursor < source.length && source[cursor] !== ']') { walk(`${path}/${index++}`); whitespace(); if (source[cursor] !== ',') break; cursor++; whitespace() }
      cursor++
    } else if (source[cursor] === '"') quoted()
    else { while (cursor < source.length && !/[\s,}\]]/.test(source[cursor])) cursor++ }
    ranges.set(path, { start, end: Math.max(start + 1, cursor) })
  }
  try { walk('') } catch { /* Syntax errors use their own offset. */ }
  let path = pointer
  while (path && !ranges.has(path)) path = path.slice(0, path.lastIndexOf('/'))
  const range = ranges.get(path) ?? { start: 0, end: 1 }
  return path === pointer ? range : { start: range.start, end: range.start + 1 }
}
