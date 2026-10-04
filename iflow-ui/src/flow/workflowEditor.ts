import {
  createRegisteredTask,
  createRegisteredTaskSequence,
  getTaskFlowKind,
  getTaskVariableOptions,
  type AddableTaskType,
} from '@/tasks'
import type { WorkflowDef, WorkflowTask } from '@/types/workflow'

function collectWorkflowTasks(tasks: WorkflowTask[] = []): WorkflowTask[] {
  return tasks.flatMap((task) => [
    task,
    ...collectWorkflowTasks(Object.values(task.decisionCases ?? {}).flat()),
    ...collectWorkflowTasks(task.defaultCase ?? []),
    ...collectWorkflowTasks((task.forkTasks ?? []).flat()),
    ...collectWorkflowTasks(task.loopOver ?? []),
  ])
}

export function buildVariableOptions(workflow: WorkflowDef) {
  const taskOutputs = collectWorkflowTasks(workflow.tasks)
    .filter((task) => Boolean(task.taskReferenceName))
    .flatMap(getTaskVariableOptions)

  return [...new Set([
    '${workflow.input}',
    ...taskOutputs,
  ].filter(Boolean))]
}

export type { AddableTaskType } from '@/tasks'
export { spiJavaTemplate } from '@/tasks/templates'

export function createTask(type: string) {
  return createRegisteredTask(type)
}

export function createTaskSequence(type: AddableTaskType) {
  return createRegisteredTaskSequence(type)
}

function cloneTask(task: WorkflowTask): WorkflowTask {
  return {
    ...task,
    inputParameters: task.inputParameters ? { ...task.inputParameters } : task.inputParameters,
    decisionCases: task.decisionCases
      ? Object.fromEntries(Object.entries(task.decisionCases).map(([key, tasks]) => [key, tasks.map(cloneTask)]))
      : task.decisionCases,
    defaultCase: task.defaultCase ? task.defaultCase.map(cloneTask) : task.defaultCase,
    forkTasks: task.forkTasks ? task.forkTasks.map((branch) => branch.map(cloneTask)) : task.forkTasks,
    loopOver: task.loopOver ? task.loopOver.map(cloneTask) : task.loopOver,
    joinOn: task.joinOn ? [...task.joinOn] : task.joinOn,
  }
}

function updateTaskList(
  tasks: WorkflowTask[],
  updater: (task: WorkflowTask, index: number, list: WorkflowTask[]) => WorkflowTask[] | undefined,
): WorkflowTask[] {
  for (let index = 0; index < tasks.length; index += 1) {
    const task = tasks[index]
    const result = updater(task, index, tasks)
    if (result) return result

    if (task.decisionCases || task.defaultCase) {
      const nextTask = cloneTask(task)
      let changed = false
      if (task.decisionCases) {
        const nextCases: Record<string, WorkflowTask[]> = {}
        for (const [key, branch] of Object.entries(task.decisionCases)) {
          const nextBranch = updateTaskList(branch, updater)
          nextCases[key] = nextBranch
          if (nextBranch !== branch) changed = true
        }
        nextTask.decisionCases = nextCases
      }
      if (task.defaultCase) {
        const nextDefault = updateTaskList(task.defaultCase, updater)
        nextTask.defaultCase = nextDefault
        if (nextDefault !== task.defaultCase) changed = true
      }
      if (changed) return tasks.map((item, taskIndex) => (taskIndex === index ? nextTask : item))
    }

    if (task.forkTasks) {
      const nextTask = cloneTask(task)
      let changed = false
      nextTask.forkTasks = task.forkTasks.map((branch) => {
        const nextBranch = updateTaskList(branch, updater)
        if (nextBranch !== branch) changed = true
        return nextBranch
      })
      if (changed) return tasks.map((item, taskIndex) => (taskIndex === index ? nextTask : item))
    }

    if (task.loopOver) {
      const nextLoopOver = updateTaskList(task.loopOver, updater)
      if (nextLoopOver !== task.loopOver) {
        return tasks.map((item, taskIndex) => (
          taskIndex === index ? { ...cloneTask(task), loopOver: nextLoopOver } : item
        ))
      }
    }
  }
  return tasks
}

function insertTaskSequence(
  def: WorkflowDef,
  taskReferenceName: string,
  tasksToInsert: WorkflowTask[],
  position: 'before' | 'after',
): WorkflowDef {
  return {
    ...def,
    tasks: updateTaskList(def.tasks, (task, index, list) => {
      if (task.taskReferenceName !== taskReferenceName) return undefined
      const insertionIndex = position === 'before' ? index : index + 1
      return [...list.slice(0, insertionIndex), ...tasksToInsert, ...list.slice(insertionIndex)]
    }),
  }
}

export function insertTasksAfterTask(def: WorkflowDef, taskReferenceName: string, tasksToInsert: WorkflowTask[]): WorkflowDef {
  return insertTaskSequence(def, taskReferenceName, tasksToInsert, 'after')
}

export function insertTasksBeforeTask(def: WorkflowDef, taskReferenceName: string, tasksToInsert: WorkflowTask[]): WorkflowDef {
  return insertTaskSequence(def, taskReferenceName, tasksToInsert, 'before')
}

export function insertAfterTask(def: WorkflowDef, taskReferenceName: string, taskToInsert: WorkflowTask): WorkflowDef {
  return insertTasksAfterTask(def, taskReferenceName, [taskToInsert])
}

export function insertBeforeTask(def: WorkflowDef, taskReferenceName: string, taskToInsert: WorkflowTask): WorkflowDef {
  return insertTasksBeforeTask(def, taskReferenceName, [taskToInsert])
}

export function appendToForkBranch(
  def: WorkflowDef,
  forkReferenceName: string,
  branchIndex: number,
  tasksToInsert: WorkflowTask[],
  position: 'start' | 'end' = 'end',
): WorkflowDef {
  return {
    ...def,
    tasks: updateTaskList(def.tasks, (task, index, list) => {
      if (task.taskReferenceName !== forkReferenceName || getTaskFlowKind(task.type) !== 'fork') return undefined
      const nextTask = cloneTask(task)
      const forkTasks = nextTask.forkTasks ?? []
      const branch = forkTasks[branchIndex] ?? []
      const previousTailRef = branch.at(-1)?.taskReferenceName
      forkTasks[branchIndex] = position === 'start'
        ? [...tasksToInsert, ...branch]
        : [...branch, ...tasksToInsert]
      nextTask.forkTasks = forkTasks
      const nextList = list.map((item, taskIndex) => (taskIndex === index ? nextTask : item))
      const join = nextList[index + 1]
      const nextTailRef = forkTasks[branchIndex]?.at(-1)?.taskReferenceName
      if (join && getTaskFlowKind(join.type) === 'join' && nextTailRef) {
        const currentJoinOn = join.joinOn ?? []
        const shouldTrackNewTail = !previousTailRef || currentJoinOn.includes(previousTailRef)
        nextList[index + 1] = {
          ...cloneTask(join),
          joinOn: shouldTrackNewTail
            ? [...new Set([...currentJoinOn.filter((reference) => reference !== previousTailRef), nextTailRef])]
            : currentJoinOn,
        }
      }
      return nextList
    }),
  }
}

export function appendToDoWhile(
  def: WorkflowDef,
  doWhileReferenceName: string,
  tasksToInsert: WorkflowTask[],
): WorkflowDef {
  return {
    ...def,
    tasks: updateTaskList(def.tasks, (task, index, list) => {
      if (task.taskReferenceName !== doWhileReferenceName || getTaskFlowKind(task.type) !== 'do_while') return undefined
      const nextTask = cloneTask(task)
      nextTask.loopOver = [...(nextTask.loopOver ?? []), ...tasksToInsert]
      return list.map((item, taskIndex) => (taskIndex === index ? nextTask : item))
    }),
  }
}

export function addForkBranch(def: WorkflowDef, forkReferenceName: string): WorkflowDef {
  return {
    ...def,
    tasks: updateTaskList(def.tasks, (task, index, list) => {
      if (task.taskReferenceName !== forkReferenceName || getTaskFlowKind(task.type) !== 'fork') return undefined
      const nextTask = cloneTask(task)
      nextTask.forkTasks = [...(nextTask.forkTasks ?? []), []]
      return list.map((item, taskIndex) => (taskIndex === index ? nextTask : item))
    }),
  }
}

export function removeForkBranch(def: WorkflowDef, forkReferenceName: string, branchIndex: number): WorkflowDef {
  return {
    ...def,
    tasks: updateTaskList(def.tasks, (task, index, list) => {
      if (task.taskReferenceName !== forkReferenceName || getTaskFlowKind(task.type) !== 'fork') return undefined
      const removedBranch = task.forkTasks?.[branchIndex] ?? []
      const removedTailRef = removedBranch.at(-1)?.taskReferenceName
      const nextTask = cloneTask(task)
      nextTask.forkTasks = (nextTask.forkTasks ?? []).filter((_, itemIndex) => itemIndex !== branchIndex)
      const nextList = list.map((item, taskIndex) => (taskIndex === index ? nextTask : item))
      const join = nextList[index + 1]
      if (join && getTaskFlowKind(join.type) === 'join' && removedTailRef) {
        nextList[index + 1] = {
          ...cloneTask(join),
          joinOn: (join.joinOn ?? []).filter((reference) => reference !== removedTailRef),
        }
      }
      return nextList
    }),
  }
}

export function appendToSwitchBranch(
  def: WorkflowDef,
  switchReferenceName: string,
  branchName: string,
  taskToInsert: WorkflowTask | WorkflowTask[],
  position: 'start' | 'end' = 'end',
): WorkflowDef {
  return {
    ...def,
    tasks: updateTaskList(def.tasks, (task, index, list) => {
      if (task.taskReferenceName !== switchReferenceName) return undefined
      const nextTask = cloneTask(task)
      const tasksToInsert = Array.isArray(taskToInsert) ? taskToInsert : [taskToInsert]
      if (branchName === 'defaultCase') {
        const branch = nextTask.defaultCase ?? []
        nextTask.defaultCase = position === 'start' ? [...tasksToInsert, ...branch] : [...branch, ...tasksToInsert]
      } else {
        const cases = nextTask.decisionCases ?? {}
        const branch = cases[branchName] ?? []
        nextTask.decisionCases = {
          ...cases,
          [branchName]: position === 'start' ? [...tasksToInsert, ...branch] : [...branch, ...tasksToInsert],
        }
      }
      return list.map((item, taskIndex) => (taskIndex === index ? nextTask : item))
    }),
  }
}

export function addSwitchCase(def: WorkflowDef, switchReferenceName: string): WorkflowDef {
  return {
    ...def,
    tasks: updateTaskList(def.tasks, (task, index, list) => {
      if (task.taskReferenceName !== switchReferenceName) return undefined
      const nextTask = cloneTask(task)
      const cases = nextTask.decisionCases ?? {}
      let nextIndex = Object.keys(cases).length + 1
      let key = `switch_case_${nextIndex}`
      while (cases[key]) {
        nextIndex += 1
        key = `switch_case_${nextIndex}`
      }
      nextTask.decisionCases = { ...cases, [key]: [] }
      return list.map((item, taskIndex) => (taskIndex === index ? nextTask : item))
    }),
  }
}

export function removeSwitchCase(def: WorkflowDef, switchReferenceName: string, branchName: string): WorkflowDef {
  return {
    ...def,
    tasks: updateTaskList(def.tasks, (task, index, list) => {
      if (task.taskReferenceName !== switchReferenceName) return undefined
      const nextTask = cloneTask(task)
      if (branchName === 'defaultCase') {
        nextTask.defaultCase = []
      } else if (nextTask.decisionCases) {
        const { [branchName]: _removed, ...rest } = nextTask.decisionCases
        nextTask.decisionCases = rest
      }
      return list.map((item, taskIndex) => (taskIndex === index ? nextTask : item))
    }),
  }
}

export function parseSwitchBranch(value?: string) {
  if (!value) return null
  const match = value.match(/\[key=([^\]]+)\]/)
  return match?.[1] ?? null
}

export function findTask(tasks: WorkflowTask[], taskReferenceName: string): WorkflowTask | undefined {
  for (const task of tasks) {
    if (task.taskReferenceName === taskReferenceName) return task
    if (task.decisionCases) {
      for (const branch of Object.values(task.decisionCases)) {
        const found = findTask(branch, taskReferenceName)
        if (found) return found
      }
    }
    if (task.defaultCase) {
      const found = findTask(task.defaultCase, taskReferenceName)
      if (found) return found
    }
    for (const branch of task.forkTasks ?? []) {
      const found = findTask(branch, taskReferenceName)
      if (found) return found
    }
    if (task.loopOver) {
      const found = findTask(task.loopOver, taskReferenceName)
      if (found) return found
    }
  }
  return undefined
}

export function updateTask(
  def: WorkflowDef,
  taskReferenceName: string,
  updater: (task: WorkflowTask) => WorkflowTask,
): WorkflowDef {
  return {
    ...def,
    tasks: updateTaskList(def.tasks, (task, index, list) => {
      if (task.taskReferenceName !== taskReferenceName) return undefined
      return list.map((item, taskIndex) => (taskIndex === index ? updater(cloneTask(item)) : item))
    }),
  }
}

export function removeTask(def: WorkflowDef, taskReferenceName: string): WorkflowDef {
  return {
    ...def,
    tasks: updateTaskList(def.tasks, (task, index, list) => {
      if (task.taskReferenceName !== taskReferenceName) return undefined
      const flowKind = getTaskFlowKind(task.type)
      const pairedTask = flowKind === 'fork' && getTaskFlowKind(list[index + 1]?.type ?? '') === 'join'
        ? 2
        : flowKind === 'join' && getTaskFlowKind(list[index - 1]?.type ?? '') === 'fork'
          ? 2
          : 1
      const removalIndex = pairedTask === 2 && flowKind === 'join' ? index - 1 : index
      return [...list.slice(0, removalIndex), ...list.slice(removalIndex + pairedTask)]
    }),
  }
}
