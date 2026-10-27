export interface WorkflowTask {
  name?: string | null
  taskReferenceName: string
  description?: string | null
  type: string
  inputParameters?: Record<string, unknown> | null
  decisionCases?: Record<string, WorkflowTask[]> | null
  defaultCase?: WorkflowTask[] | null
  forkTasks?: WorkflowTask[][] | null
  loopOver?: WorkflowTask[] | null
  loopCondition?: string | null
  loopItems?: string | null
  joinOn?: string[] | null
  defaultExclusiveJoinTask?: string[] | null
  expression?: string | null
  evaluatorType?: string | null
  optional?: boolean
  retryCount?: number | null
  retryDelaySeconds?: number | null
  retryLogic?: 'FIXED' | 'LINEAR_BACKOFF' | 'EXPONENTIAL_BACKOFF' | null
  startDelay?: number | null
}

export interface WorkflowDef {
  route: string
  name: string
  version: number
  description?: string | null
  createTime?: number | string
  tags?: string[] | null
  updateTime?: number | string
  correlationExpression?: string | null
  inputParameters?: string[] | null
  outputParameters?: Record<string, unknown> | null
  workflowStatusListenerEnabled?: boolean
  timeoutSeconds?: number
  timeoutPolicy?: string | null
  tasks: WorkflowTask[]
}
