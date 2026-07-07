export const TaskType = {
  HTTP: 'HTTP',
  INLINE: 'INLINE',
  JSON_JQ_TRANSFORM: 'JSON_JQ_TRANSFORM',
  SWITCH: 'SWITCH',
  DECISION: 'DECISION',
  FORK_JOIN: 'FORK_JOIN',
  JOIN: 'JOIN',
  DO_WHILE: 'DO_WHILE',
  EXCLUSIVE_JOIN: 'EXCLUSIVE_JOIN',
  TERMINATE: 'TERMINATE',
  TERMINAL: 'TERMINAL',
  SWITCH_JOIN: 'SWITCH_JOIN',
  PARAM_MAP: 'PARAM_MAP',
  SPI: 'SPI',
  JDBC: 'JDBC',
  WEBSERVICE: 'WEBSERVICE',
  NOOP: 'NOOP',
} as const

export type TaskTypeValue = (typeof TaskType)[keyof typeof TaskType]

export function taskTypeLabel(type: string) {
  switch (type) {
    case TaskType.HTTP:
      return 'HTTP 请求'
    case TaskType.INLINE:
      return '内联脚本'
    case TaskType.JSON_JQ_TRANSFORM:
      return 'JSON 转换'
    case TaskType.SWITCH:
    case TaskType.DECISION:
      return '分支路由'
    case TaskType.JDBC:
      return '数据库'
    case TaskType.WEBSERVICE:
      return 'Web 服务'
    case TaskType.PARAM_MAP:
      return '参数映射'
    case TaskType.SPI:
      return 'SPI 扩展'
    case TaskType.FORK_JOIN:
      return '并行分支'
    case TaskType.JOIN:
      return '汇聚'
    case TaskType.DO_WHILE:
      return '循环'
    case TaskType.TERMINATE:
      return '终止'
    default:
      return type
  }
}
