import type { WorkflowTask } from '../../types/workflow'
import type { TaskExecution } from '../../types/workflowExecution'

export function indexTaskExecutions(definitionTasks: WorkflowTask[], tasks: TaskExecution[]) {
  const taskDefinitionByRef = new Map<string, WorkflowTask>()
  const loopTaskRefs = new Set<string>()

  const visit = (list: WorkflowTask[], withinLoop = false) => {
    for (const task of list) {
      taskDefinitionByRef.set(task.taskReferenceName, task)
      if (withinLoop) loopTaskRefs.add(task.taskReferenceName)
      for (const branch of Object.values(task.decisionCases ?? {})) visit(branch, withinLoop)
      visit(task.defaultCase ?? [], withinLoop)
      for (const branch of task.forkTasks ?? []) visit(branch, withinLoop)
      visit(task.loopOver ?? [], true)
    }
  }
  visit(definitionTasks)

  const definitionRefByTaskId = new Map<string, string>()
  const iterationByTaskId = new Map<string, number>()
  const taskAttemptsByRef = new Map<string, TaskExecution[]>()
  const latestTaskByRef = new Map<string, TaskExecution>()

  for (const task of tasks) {
    const runtimeRef = task.referenceTaskName
    if (!runtimeRef) continue
    let definitionRef = runtimeRef
    // iFlow's execution DTO omits workflowTask/iteration. Recover the definition
    // reference from the engine's __N suffix, only for tasks inside a loop.
    if (!taskDefinitionByRef.has(runtimeRef)) {
      const match = /^(.*)__([1-9]\d*)$/.exec(runtimeRef)
      if (match && loopTaskRefs.has(match[1]) && Number.isSafeInteger(Number(match[2]))) {
        definitionRef = match[1]
        iterationByTaskId.set(task.taskId, Number(match[2]))
      }
    }
    definitionRefByTaskId.set(task.taskId, definitionRef)
    const attempts = taskAttemptsByRef.get(definitionRef) ?? []
    attempts.push(task)
    taskAttemptsByRef.set(definitionRef, attempts)
  }

  for (const [ref, attempts] of taskAttemptsByRef) {
    attempts.sort((left, right) =>
      (iterationByTaskId.get(right.taskId) ?? 0) - (iterationByTaskId.get(left.taskId) ?? 0)
      || (right.retryCount ?? 0) - (left.retryCount ?? 0)
      || (right.seq ?? 0) - (left.seq ?? 0)
      || taskTime(right) - taskTime(left),
    )
    latestTaskByRef.set(ref, attempts[0])
  }

  return { taskDefinitionByRef, definitionRefByTaskId, iterationByTaskId, taskAttemptsByRef, latestTaskByRef }
}

function taskTime(task: TaskExecution) {
  return Math.max(task.updateTime ?? 0, task.endTime ?? 0, task.startTime ?? 0, task.scheduledTime ?? 0)
}
