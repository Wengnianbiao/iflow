import {
  QueryClient,
  useQuery,
  useQueryClient,
  type UseQueryOptions,
  type UseQueryResult,
} from '@tanstack/react-query'
import { listWorkflowDefinitions } from '@/api/workflowDefinition'
import { searchWorkflowExecutions } from '@/api/workflowExecution'
import type { WorkflowDef } from '@/types/workflow'
import type {
  PageResult,
  WorkflowExecutionSearchParams,
  WorkflowExecutionSummary,
} from '@/types/workflowExecution'

export const DEFAULT_STALE_TIME = 5_000
export const STALE_TIME_WORKFLOW_DEFS = 600_000

export const queryKeys = {
  workflowDefs: ['workflowDefs'] as const,
  workflowExecutions: (params: WorkflowExecutionSearchParams) =>
    ['workflowExecutions', params] as const,
}

export function createAppQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: DEFAULT_STALE_TIME,
        refetchOnWindowFocus: false,
        retry: 1,
      },
    },
  })
}

export function useWorkflowDefs(
  optionsOverride: Partial<UseQueryOptions<WorkflowDef[], Error>> = {},
): UseQueryResult<WorkflowDef[], Error> {
  return useQuery({
    queryKey: queryKeys.workflowDefs,
    queryFn: listWorkflowDefinitions,
    staleTime: STALE_TIME_WORKFLOW_DEFS,
    ...optionsOverride,
  })
}

export function useInvalidateWorkflowDefs() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.workflowDefs })
}

export function useSearchWorkflowExecutions(
  params: WorkflowExecutionSearchParams,
  optionsOverride: Partial<
    UseQueryOptions<PageResult<WorkflowExecutionSummary>, Error>
  > = {},
): UseQueryResult<PageResult<WorkflowExecutionSummary>, Error> {
  return useQuery({
    queryKey: queryKeys.workflowExecutions(params),
    queryFn: () => searchWorkflowExecutions(params),
    staleTime: DEFAULT_STALE_TIME,
    ...optionsOverride,
  })
}
