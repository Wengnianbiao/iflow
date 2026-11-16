export const TaskType = {
  HTTP: 'HTTP',
  INLINE: 'INLINE',
  SWITCH: 'SWITCH',
  DECISION: 'DECISION',
  FORK_JOIN: 'FORK_JOIN',
  JOIN: 'JOIN',
  DO_WHILE: 'DO_WHILE',
  EXCLUSIVE_JOIN: 'EXCLUSIVE_JOIN',
  TERMINATE: 'TERMINATE',
  SET_KEYWORD: 'SET_KEYWORD',
  TERMINAL: 'TERMINAL',
  SWITCH_JOIN: 'SWITCH_JOIN',
  MAPPING: 'MAPPING',
  SPI: 'SPI',
  JDBC: 'JDBC',
  WEBSERVICE: 'WEBSERVICE',
  NOOP: 'NOOP',
} as const

export type TaskTypeValue = (typeof TaskType)[keyof typeof TaskType]

/** 与后端 TaskType.isBuiltIn 一致；FORK_JOIN 定义对应运行时 FORK。 */
export function isBuiltInTask(type: string) {
  return type === TaskType.DECISION
    || type === TaskType.SWITCH
    || type === 'FORK'
    || type === TaskType.FORK_JOIN
    || type === TaskType.JOIN
    || type === TaskType.EXCLUSIVE_JOIN
    || type === TaskType.DO_WHILE
}
