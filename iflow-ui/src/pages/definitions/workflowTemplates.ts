import type { Icon } from '@phosphor-icons/react'
import {
  ArrowsSplitIcon,
  GitForkIcon,
  GlobeHemisphereWestIcon,
} from '@phosphor-icons/react'
import type { WorkflowDef } from '@/types/workflow'

export type WorkflowTemplate = {
  id: string
  name: string
  title: string
  description: string
  taskTypes: string[]
  icon: Icon
  create: (workflowName: string) => WorkflowDef
}

export const workflowTemplates: WorkflowTemplate[] = [
  {
    id: 'api-orchestration',
    name: 'api_orchestration',
    title: 'API 数据编排',
    description: '请求外部 API，并通过内联脚本整理响应数据。',
    taskTypes: ['HTTP', 'INLINE'],
    icon: GlobeHemisphereWestIcon,
    create: (workflowName) => ({
      name: workflowName,
      version: 1,
      displayName: 'API 数据编排',
      description: '请求外部 API，并整理响应数据。',
      inputParameters: ['apiBaseUrl', 'resourceId'],
      outputParameters: {
        result: '${format_response_ref.output.result}',
      },
      workflowStatusListenerEnabled: false,
      timeoutSeconds: 0,
      timeoutPolicy: 'ALERT_ONLY',
      tasks: [
        {
          name: '请求 API',
          taskReferenceName: 'request_api_ref',
          type: 'HTTP',
          inputParameters: {
            http_request: {
              method: 'GET',
              uri: '${workflow.input.apiBaseUrl}/${workflow.input.resourceId}',
              contentType: 'application/json',
              readTimeOut: 30000,
            },
          },
        },
        {
          name: '整理响应',
          taskReferenceName: 'format_response_ref',
          type: 'INLINE',
          inputParameters: {
            evaluatorType: 'graaljs',
            expression: '(function () { return { result: $.response }; })()',
            response: '${request_api_ref.output.body}',
          },
        },
      ],
    }),
  },
  {
    id: 'conditional-routing',
    name: 'conditional_routing',
    title: '条件分支处理',
    description: '根据工作流输入值进入不同处理分支，并保留默认路径。',
    taskTypes: ['SWITCH', 'INLINE'],
    icon: ArrowsSplitIcon,
    create: (workflowName) => ({
      name: workflowName,
      version: 1,
      displayName: '条件分支处理',
      description: '根据输入状态执行不同的业务处理分支。',
      inputParameters: ['status'],
      outputParameters: {},
      workflowStatusListenerEnabled: false,
      timeoutSeconds: 0,
      timeoutPolicy: 'ALERT_ONLY',
      tasks: [
        {
          name: '状态路由',
          taskReferenceName: 'status_switch_ref',
          type: 'SWITCH',
          evaluatorType: 'value-param',
          expression: '${workflow.input.status}',
          inputParameters: {},
          decisionCases: {
            approved: [
              {
                name: '处理已通过状态',
                taskReferenceName: 'approved_handler_ref',
                type: 'INLINE',
                inputParameters: {
                  evaluatorType: 'graaljs',
                  expression: '(function () { return { status: "approved" }; })()',
                },
              },
            ],
            rejected: [
              {
                name: '处理已拒绝状态',
                taskReferenceName: 'rejected_handler_ref',
                type: 'INLINE',
                inputParameters: {
                  evaluatorType: 'graaljs',
                  expression: '(function () { return { status: "rejected" }; })()',
                },
              },
            ],
          },
          defaultCase: [
            {
              name: '处理未知状态',
              taskReferenceName: 'default_handler_ref',
              type: 'INLINE',
              inputParameters: {
                evaluatorType: 'graaljs',
                expression: '(function () { return { status: "unknown" }; })()',
              },
            },
          ],
        },
      ],
    }),
  },
  {
    id: 'parallel-processing',
    name: 'parallel_processing',
    title: '并行任务处理',
    description: '同时执行两条独立分支，并在完成后汇聚结果。',
    taskTypes: ['FORK_JOIN', 'HTTP', 'JOIN'],
    icon: GitForkIcon,
    create: (workflowName) => ({
      name: workflowName,
      version: 1,
      displayName: '并行任务处理',
      description: '并行调用多个服务，并在全部完成后继续处理。',
      inputParameters: ['serviceAUrl', 'serviceBUrl'],
      outputParameters: {},
      workflowStatusListenerEnabled: false,
      timeoutSeconds: 0,
      timeoutPolicy: 'ALERT_ONLY',
      tasks: [
        {
          name: '并行调用服务',
          taskReferenceName: 'parallel_services_ref',
          type: 'FORK_JOIN',
          inputParameters: {},
          forkTasks: [
            [
              {
                name: '调用服务 A',
                taskReferenceName: 'service_a_ref',
                type: 'HTTP',
                inputParameters: {
                  http_request: {
                    method: 'GET',
                    uri: '${workflow.input.serviceAUrl}',
                    contentType: 'application/json',
                    readTimeOut: 30000,
                  },
                },
              },
            ],
            [
              {
                name: '调用服务 B',
                taskReferenceName: 'service_b_ref',
                type: 'HTTP',
                inputParameters: {
                  http_request: {
                    method: 'GET',
                    uri: '${workflow.input.serviceBUrl}',
                    contentType: 'application/json',
                    readTimeOut: 30000,
                  },
                },
              },
            ],
          ],
        },
        {
          name: '汇聚服务结果',
          taskReferenceName: 'join_services_ref',
          type: 'JOIN',
          inputParameters: {},
          joinOn: ['service_a_ref', 'service_b_ref'],
          optional: false,
        },
      ],
    }),
  },
]
