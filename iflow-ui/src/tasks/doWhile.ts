import type { WorkflowTask } from '@/types/workflow'

const nonBlank = (value: unknown): value is string => typeof value === 'string' && Boolean(value.trim())

export function hasDoWhileLoopItems(task: WorkflowTask): boolean {
  return nonBlank(task.loopItems)
}

export function isLoopItemsExpression(value: unknown): boolean {
  if (!nonBlank(value)) return false
  const expression = value.trim()
  return /^\$\{[^{}]+\}$/.test(expression) && Boolean(expression.slice(2, -1).trim())
}

export function doWhileStrategy(task: WorkflowTask): 'array' | 'js' {
  return task.loopItems != null ? 'array' : 'js'
}

export function changeDoWhileStrategy(task: WorkflowTask, strategy: 'array' | 'js'): WorkflowTask {
  const next = { ...task, inputParameters: { ...task.inputParameters } }
  delete next.evaluatorType
  delete next.inputParameters.evaluatorType
  if (strategy === 'array') {
    delete next.loopCondition
    next.loopItems = task.loopItems ?? ''
  } else {
    delete next.loopItems
    next.loopCondition = task.loopCondition ?? 'false'
  }
  return next
}

export function doWhileVariableOptions(task: WorkflowTask): string[] {
  const output = `\${${task.taskReferenceName}.output}`
  const options = [output, `\${${task.taskReferenceName}.output.iteration}`]
  if (hasDoWhileLoopItems(task)) {
    options.push(`\${${task.taskReferenceName}.output.loopItem}`, `\${${task.taskReferenceName}.output.loopIndex}`)
  }
  return options
}
