import { TaskType } from './taskType'
import type { WorkflowDef, WorkflowTask } from '@/types/workflow'

const OUR_TASK_TYPES = [
  TaskType.HTTP,
  TaskType.INLINE,
  TaskType.JSON_JQ_TRANSFORM,
  TaskType.SWITCH,
  TaskType.FORK_JOIN,
  TaskType.DO_WHILE,
  TaskType.PARAM_MAP,
  TaskType.SPI,
  TaskType.JDBC,
  TaskType.WEBSERVICE,
] as const

export type AddableTaskType = (typeof OUR_TASK_TYPES)[number]

export const addableTaskTypes: AddableTaskType[] = [...OUR_TASK_TYPES]

const DEFAULT_WORKFLOW_ATTRIBUTES = [
  'workflow.workflowId',
  'workflow.output',
  'workflow.status',
  'workflow.parentWorkflowId',
  'workflow.parentWorkflowTaskId',
  'workflow.workflowType',
  'workflow.version',
  'workflow.correlationId',
  'workflow.variables',
  'workflow.createTime',
  'workflow.taskToDomain',
]

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
  const workflowInputs = (workflow.inputParameters ?? []).map((name) => `\${workflow.input.${name}}`)
  const workflowAttributes = DEFAULT_WORKFLOW_ATTRIBUTES.map((name) => `\${${name}}`)
  const taskOutputs = collectWorkflowTasks(workflow.tasks)
    .filter((task) => Boolean(task.taskReferenceName))
    .flatMap((task) => {
      const output = `\${${task.taskReferenceName}.output}`
      return task.type === TaskType.PARAM_MAP ? [output, `\${${task.taskReferenceName}.output.output}`] : [output]
    })

  return [...new Set([
    '${workflow.input}',
    '${workflow.secrets}',
    '${workflow.env}',
    ...workflowInputs,
    ...workflowAttributes,
    ...taskOutputs,
  ].filter(Boolean))]
}

export function spiJavaTemplate(className: string) {
  const separator = className.lastIndexOf('.')
  const packageName = separator > 0 ? className.slice(0, separator) : ''
  const simpleName = separator > 0 ? className.slice(separator + 1) : className
  return `${packageName ? `package ${packageName};\n\n` : ''}import com.helianhealth.agent.tasks.spi.InterfaceRemoteInvoker;

import java.util.LinkedHashMap;
import java.util.Map;

public class ${simpleName} implements InterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(Map<String, Object> spiInput) {
        Map<String, Object> result = new LinkedHashMap<>(spiInput);
        // TODO 在这里实现当前 SPI 节点的接口业务逻辑
        return result;
    }
}
`
}

export function createTask(type: string): WorkflowTask {
  const suffix = Math.random().toString(36).slice(2, 7)
  const base = type.toLowerCase()
  const taskReferenceName = `${base}_${suffix}_ref`

  if (type === TaskType.SWITCH || type === TaskType.DECISION) {
    return {
      name: `分支路由_${suffix}`,
      taskReferenceName,
      type: TaskType.SWITCH,
      evaluatorType: 'value-param',
      expression: '${workflow.input.value}',
      decisionCases: {
        switch_case_1: [],
      },
      defaultCase: [],
      inputParameters: {},
    }
  }

  if (type === TaskType.FORK_JOIN) {
    return {
      name: `fork_${suffix}`,
      taskReferenceName,
      type,
      inputParameters: {},
      forkTasks: [[]],
    }
  }

  if (type === TaskType.JOIN) {
    return {
      name: `join_${suffix}`,
      taskReferenceName,
      type,
      inputParameters: {},
      joinOn: [],
      optional: false,
    }
  }

  if (type === TaskType.DO_WHILE) {
    return {
      name: `do_while_${suffix}`,
      taskReferenceName,
      type,
      inputParameters: { evaluatorType: 'graaljs' },
      evaluatorType: 'graaljs',
      loopCondition: 'false',
      loopOver: [],
      optional: false,
    }
  }

  if (type === TaskType.HTTP) {
    return {
      name: `http_${suffix}`,
      taskReferenceName,
      type,
      inputParameters: {
        http_request: {
          method: 'GET',
          uri: 'http://localhost:8080',
          contentType: 'application/json',
          readTimeOut: 30000,
        },
      },
    }
  }

  if (type === TaskType.JDBC) {
    return {
      name: `jdbc_${suffix}`,
      taskReferenceName,
      type,
      inputParameters: {
        jdbc_request: {
          url: 'jdbc:mysql://localhost:3306/demo',
          vendor: 'MYSQL',
          username: '',
          operation: 'QUERY',
          sql: 'SELECT 1',
          parameters: {},
          connectionTimeOut: 5000,
          queryTimeOut: 30000,
        },
      },
    }
  }

  if (type === TaskType.WEBSERVICE) {
    return {
      name: `webservice_${suffix}`,
      taskReferenceName,
      type,
      inputParameters: {
        uri: 'http://localhost:8080/services/example',
        headers: {
          SOAPAction: '',
        },
        templateParameters: {
          payload: '${workflow.input}',
        },
        soapTemplate: '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">\n  <soap:Body>\n    <requestId>${$.payload.requestId}</requestId>\n  </soap:Body>\n</soap:Envelope>',
        contentType: 'text/xml;charset=UTF-8',
        connectionTimeOut: 5000,
        readTimeOut: 30000,
      },
    }
  }

  if (type === TaskType.INLINE) {
    return {
      name: `${base}_${suffix}`,
      taskReferenceName,
      type,
      inputParameters: {
        evaluatorType: 'graaljs',
        expression: '(function () { return {}; })()',
      },
    }
  }

  if (type === TaskType.JSON_JQ_TRANSFORM) {
    return {
      name: `${base}_${suffix}`,
      taskReferenceName,
      type,
      evaluatorType: 'javascript',
      expression: '(function () { return {}; })()',
      inputParameters: {},
    }
  }

  if (type === TaskType.PARAM_MAP) {
    return {
      name: `param_map_${suffix}`,
      taskReferenceName,
      type,
      inputParameters: {
        payload: '${workflow.input}',
        paramsConfig: [],
      },
    }
  }

  if (type === TaskType.SPI) {
    const className = `com.helianhealth.agent.extension.SpiTaskExtension_${suffix}`
    return {
      name: `spi_${suffix}`,
      taskReferenceName,
      type,
      inputParameters: {
        remoteExtension: `spi-${suffix}`,
        extensionMode: 'ONLINE',
        className,
        sourceCode: spiJavaTemplate(className),
        spiInput: {},
      },
    }
  }

  return {
    name: `${base}_${suffix}`,
    taskReferenceName,
    type,
    inputParameters: {},
  }
}

/**
 * Some Conductor operators are structural task sequences. A FORK_JOIN is only
 * executable when its matching JOIN immediately follows it, so expose one
 * operation payload instead of letting callers create an invalid half-pair.
 */
export function createTaskSequence(type: AddableTaskType): WorkflowTask[] {
  if (type !== TaskType.FORK_JOIN) return [createTask(type)]
  return [createTask(TaskType.FORK_JOIN), createTask(TaskType.JOIN)]
}

function cloneTask(task: WorkflowTask): WorkflowTask {
  return {
    ...task,
    inputParameters: task.inputParameters ? { ...task.inputParameters } : task.inputParameters,
    decisionCases: task.decisionCases
      ? Object.fromEntries(Object.entries(task.decisionCases).map(([key, tasks]) => [key, tasks.map(cloneTask)]))
      : task.decisionCases,
    defaultCase: task.defaultCase?.map(cloneTask),
    forkTasks: task.forkTasks?.map((branch) => branch.map(cloneTask)),
    loopOver: task.loopOver?.map(cloneTask),
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
      if (nextTask.decisionCases) {
        const nextCases: Record<string, WorkflowTask[]> = {}
        for (const [key, branch] of Object.entries(nextTask.decisionCases)) {
          const nextBranch = updateTaskList(branch, updater)
          nextCases[key] = nextBranch
          if (nextBranch !== branch) changed = true
        }
        nextTask.decisionCases = nextCases
      }
      if (nextTask.defaultCase) {
        const nextDefault = updateTaskList(nextTask.defaultCase, updater)
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
      if (task.taskReferenceName !== forkReferenceName || task.type !== TaskType.FORK_JOIN) return undefined
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
      if (join?.type === TaskType.JOIN && nextTailRef) {
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
      if (task.taskReferenceName !== doWhileReferenceName || task.type !== TaskType.DO_WHILE) return undefined
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
      if (task.taskReferenceName !== forkReferenceName || task.type !== TaskType.FORK_JOIN) return undefined
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
      if (task.taskReferenceName !== forkReferenceName || task.type !== TaskType.FORK_JOIN) return undefined
      const removedBranch = task.forkTasks?.[branchIndex] ?? []
      const removedTailRef = removedBranch.at(-1)?.taskReferenceName
      const nextTask = cloneTask(task)
      nextTask.forkTasks = (nextTask.forkTasks ?? []).filter((_, itemIndex) => itemIndex !== branchIndex)
      const nextList = list.map((item, taskIndex) => (taskIndex === index ? nextTask : item))
      const join = nextList[index + 1]
      if (join?.type === TaskType.JOIN && removedTailRef) {
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
      const pairedTask = task.type === TaskType.FORK_JOIN && list[index + 1]?.type === TaskType.JOIN
        ? 2
        : task.type === TaskType.JOIN && list[index - 1]?.type === TaskType.FORK_JOIN
          ? 2
          : 1
      const removalIndex = pairedTask === 2 && task.type === TaskType.JOIN ? index - 1 : index
      return [...list.slice(0, removalIndex), ...list.slice(removalIndex + pairedTask)]
    }),
  }
}
