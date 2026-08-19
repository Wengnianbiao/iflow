export interface WorkflowTask {
  name?: string
  taskReferenceName: string
  description?: string
  type: string
  inputParameters?: Record<string, unknown>
  decisionCases?: Record<string, WorkflowTask[]>
  defaultCase?: WorkflowTask[]
  forkTasks?: WorkflowTask[][]
  loopOver?: WorkflowTask[]
  loopCondition?: string
  items?: string
  joinOn?: string[]
  defaultExclusiveJoinTask?: string[]
  expression?: string
  evaluatorType?: string
  optional?: boolean
}

export interface WorkflowDef {
  name: string
  version: number
  displayName?: string
  description?: string
  createTime?: number | string
  updateTime?: number | string
  correlationExpression?: string
  inputParameters?: string[]
  outputParameters?: Record<string, unknown>
  workflowStatusListenerEnabled?: boolean
  timeoutSeconds?: number
  timeoutPolicy?: string
  tasks: WorkflowTask[]
}
