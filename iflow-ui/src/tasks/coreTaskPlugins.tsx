import {
  BracketsCurlyIcon,
  DatabaseIcon,
  DiamondIcon,
  FileJsIcon,
  FunctionIcon,
  GitBranchIcon,
  GitForkIcon,
  GlobeIcon,
  LightningIcon,
  PlugsConnectedIcon,
  RepeatIcon,
} from '@phosphor-icons/react'
import type { IconProps } from '@phosphor-icons/react'
import { TaskType } from '@/flow/taskType'
import {
  DoWhileTaskForm,
  ForkJoinTaskForm,
  FunctionTaskForm,
  HTTPTaskForm,
  INLINETaskForm,
  JDBCTaskForm,
  JoinTaskForm,
  ParamMapTaskForm,
  SPITaskForm,
  SwitchTaskForm,
  WebServiceTaskForm,
} from '@/pages/definition/EditorPanel/TaskFormTab/forms'
import type { WorkflowTask } from '@/types/workflow'
import {
  HTTPTaskCard,
  FUNCTIONTaskCard,
  INLINETaskCard,
  JDBCTaskCard,
  MAPPINGTaskCard,
  SPITaskCard,
  WebServiceTaskCard,
} from './cards'
import { registerCoreTaskPlugins, type TaskCreationContext } from './taskPluginRegistry'
import { validateBuiltinTask } from './builtinTaskValidators'
import { spiJavaTemplate } from './templates'

const DEFAULT_NODE_WIDTH = 350
const SWITCH_SIZE_INCREMENTER = 60
const MIN_AMOUNT_OF_SWITCH_PORTS = 4

function ForkJoinIcon({ style, ...props }: IconProps) {
  return <GitForkIcon {...props} style={{ ...style, transform: 'rotate(180deg)' }} />
}

function additionalPortWidth(portCount: number) {
  return portCount > MIN_AMOUNT_OF_SWITCH_PORTS
    ? (portCount - MIN_AMOUNT_OF_SWITCH_PORTS) * SWITCH_SIZE_INCREMENTER
    : 0
}

function baseTask(context: TaskCreationContext, changes: Partial<WorkflowTask>): WorkflowTask {
  return {
    name: `${context.baseName}_${context.suffix}`,
    taskReferenceName: context.taskReferenceName,
    type: context.type,
    inputParameters: {},
    ...changes,
  }
}

function outputVariableOptions(task: WorkflowTask, fields: string[] = []) {
  const output = `\${${task.taskReferenceName}.output}`
  return [
    output,
    ...fields.map((field) => `${output.slice(0, -1)}.${field}}`),
  ]
}

function responseVariableOptions(task: WorkflowTask) {
  return outputVariableOptions(task, ['body', 'headers', 'statusCode', 'reasonPhrase'])
}

function jdbcVariableOptions(task: WorkflowTask) {
  return outputVariableOptions(task, ['rows', 'rowCount', 'updateCount'])
}

function createSwitchTask(context: TaskCreationContext) {
  return baseTask(context, {
    name: `分支路由_${context.suffix}`,
    type: TaskType.SWITCH,
    evaluatorType: 'value-param',
    expression: '${workflow.input.value}',
    decisionCases: { switch_case_1: [] },
    defaultCase: [],
  })
}

registerCoreTaskPlugins([
  {
    type: TaskType.HTTP,
    validate: validateBuiltinTask,
    label: 'HTTP 请求',
    icon: GlobeIcon,
    menu: { label: TaskType.HTTP, description: '调用 API 或微服务。' },
    card: HTTPTaskCard,
    form: HTTPTaskForm,
    inspectorLayout: 'bare',
    flow: { nodeSize: { width: DEFAULT_NODE_WIDTH, height: 130 } },
    variableOptions: responseVariableOptions,
    create: (context) => baseTask(context, {
      name: `http_${context.suffix}`,
      inputParameters: {
        http_request: {
          method: 'GET',
          uri: 'http://localhost:8080',
          contentType: 'application/json',
          readTimeOut: 30000,
        },
      },
    }),
  },
  {
    type: TaskType.INLINE,
    validate: validateBuiltinTask,
    label: 'JS脚本',
    cardLabel: 'JS脚本',
    icon: FileJsIcon,
    menu: { label: 'JS脚本', description: '运行轻量 JavaScript 脚本。' },
    card: INLINETaskCard,
    form: INLINETaskForm,
    inspectorLayout: 'bare',
    flow: { nodeSize: { width: DEFAULT_NODE_WIDTH, height: 140 } },
    variableOptions: outputVariableOptions,
    create: (context) => baseTask(context, {
      inputParameters: {
        evaluatorType: 'graaljs',
        expression: '(function () { return {}; })()',
      },
    }),
  },
  {
    type: TaskType.FUNCTION,
    label: '函数转换',
    cardLabel: '函数转化',
    icon: FunctionIcon,
    menu: { label: TaskType.FUNCTION, description: '解析或序列化 JSON / XML。' },
    card: FUNCTIONTaskCard,
    form: FunctionTaskForm,
    inspectorLayout: 'bare',
    flow: { nodeSize: { width: DEFAULT_NODE_WIDTH, height: 120 } },
    variableOptions: outputVariableOptions,
    create: (context) => baseTask(context, {
      name: `function_${context.suffix}`,
      inputParameters: {
        function: 'JSON_PARSE',
        value: '',
      },
    }),
  },
  {
    type: TaskType.SWITCH,
    label: '分支路由',
    validate: validateBuiltinTask,
    icon: GitBranchIcon,
    menu: { label: '分支路由', description: '根据表达式选择后续分支。', icon: DiamondIcon },
    form: SwitchTaskForm,
    appearance: { shadow: false },
    flow: {
      kind: 'switch',
      nodeSize: (task) => ({
        width: DEFAULT_NODE_WIDTH + 100 + additionalPortWidth(Object.keys(task.decisionCases ?? {}).length + 1),
        height: 200,
      }),
    },
    create: createSwitchTask,
  },
  {
    type: TaskType.FORK_JOIN,
    label: '并行分支',
    cardLabel: 'FORK JOIN',
    icon: ForkJoinIcon,
    menu: { label: 'FORK JOIN', description: '并行执行多个任务分支并在 JOIN 汇聚。' },
    form: ForkJoinTaskForm,
    appearance: { cardVariant: 'operator' },
    flow: {
      kind: 'fork',
      nodeSize: (task) => ({
        width: DEFAULT_NODE_WIDTH + additionalPortWidth(task.forkTasks?.length ?? 0),
        height: 120,
      }),
    },
    sequence: [TaskType.JOIN],
    create: (context) => baseTask(context, {
      name: `fork_${context.suffix}`,
      forkTasks: [[]],
    }),
  },
  {
    type: TaskType.DO_WHILE,
    validate: validateBuiltinTask,
    label: '循环',
    icon: RepeatIcon,
    menu: { label: 'DO WHILE', description: '按条件重复执行循环体任务。' },
    form: DoWhileTaskForm,
    flow: { kind: 'do_while', nodeSize: { width: 570, height: 450 } },
    create: (context) => baseTask(context, {
      name: `do_while_${context.suffix}`,
      inputParameters: { evaluatorType: 'graaljs' },
      evaluatorType: 'graaljs',
      loopCondition: 'false',
      loopOver: [],
      optional: false,
    }),
  },
  {
    type: TaskType.MAPPING,
    validate: validateBuiltinTask,
    label: '参数映射',
    cardLabel: '参数映射',
    icon: LightningIcon,
    menu: { label: '参数映射', description: '映射和整理任务参数。', icon: LightningIcon },
    card: MAPPINGTaskCard,
    form: ParamMapTaskForm,
    inspectorLayout: 'bare',
    flow: { nodeSize: { width: DEFAULT_NODE_WIDTH, height: 140 } },
    create: (context) => baseTask(context, {
      name: `mapping_${context.suffix}`,
      inputParameters: { payload: '${workflow.input}', paramsConfig: [] },
    }),
  },
  {
    type: TaskType.SPI,
    validate: validateBuiltinTask,
    label: 'SPI 扩展',
    icon: PlugsConnectedIcon,
    menu: { label: TaskType.SPI, description: '通过扩展名路由 Java SPI 实现。', icon: BracketsCurlyIcon },
    card: SPITaskCard,
    form: SPITaskForm,
    inspectorLayout: 'bare',
    flow: { nodeSize: { width: DEFAULT_NODE_WIDTH, height: 130 } },
    create: (context) => {
      const className = `com.helianhealth.iflow.extension.SpiTaskExtension_${context.suffix}`
      return baseTask(context, {
        name: `spi_${context.suffix}`,
        inputParameters: {
          remoteExtension: `spi-${context.suffix}`,
          extensionMode: 'ONLINE',
          className,
          sourceCode: spiJavaTemplate(className),
          spiInput: {},
        },
      })
    },
  },
  {
    type: TaskType.JDBC,
    validate: validateBuiltinTask,
    label: '数据库',
    cardLabel: '数据库',
    icon: DatabaseIcon,
    menu: { label: '数据库', description: '执行数据库查询。' },
    card: JDBCTaskCard,
    form: JDBCTaskForm,
    flow: { nodeSize: { width: DEFAULT_NODE_WIDTH, height: 140 } },
    variableOptions: jdbcVariableOptions,
    create: (context) => baseTask(context, {
      name: `jdbc_${context.suffix}`,
      inputParameters: {
        url: 'jdbc:mysql://localhost:3306/demo',
        vendor: 'MYSQL',
        username: '',
        operation: 'QUERY',
        payload: { ids: '${workflow.input.data.ids}' },
        sqlTemplate: 'SELECT * FROM demo WHERE id IN $.ids',
        connectionTimeOut: 5000,
        queryTimeOut: 30000,
      },
    }),
  },
  {
    type: TaskType.WEBSERVICE,
    validate: validateBuiltinTask,
    label: 'Web 服务',
    icon: GlobeIcon,
    menu: { label: 'Web Service', description: '调用 Web Service 服务。', icon: GlobeIcon },
    card: WebServiceTaskCard,
    form: WebServiceTaskForm,
    flow: { nodeSize: { width: DEFAULT_NODE_WIDTH, height: 130 } },
    variableOptions: responseVariableOptions,
    create: (context) => baseTask(context, {
      name: `webservice_${context.suffix}`,
      inputParameters: {
        uri: 'http://localhost:8080/services/example',
        headers: { SOAPAction: '' },
        templateParameters: { payload: '${workflow.input}' },
        soapTemplate: '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">\n  <soap:Body>\n    <requestId>$.payload.requestId</requestId>\n  </soap:Body>\n</soap:Envelope>',
        contentType: 'text/xml;charset=UTF-8',
        connectionTimeOut: 5000,
        readTimeOut: 30000,
      },
    }),
  },
  {
    type: TaskType.DECISION,
    validate: validateBuiltinTask,
    label: '分支路由',
    icon: GitBranchIcon,
    form: SwitchTaskForm,
    appearance: { shadow: false },
    flow: {
      kind: 'switch',
      nodeSize: (task) => ({
        width: DEFAULT_NODE_WIDTH + 100 + additionalPortWidth(Object.keys(task.decisionCases ?? {}).length + 1),
        height: 200,
      }),
    },
    create: createSwitchTask,
  },
  {
    type: TaskType.JOIN,
    label: '汇聚',
    icon: GitForkIcon,
    form: JoinTaskForm,
    appearance: { cardVariant: 'operator' },
    flow: { kind: 'join', nodeSize: { width: DEFAULT_NODE_WIDTH, height: 80 } },
    create: (context) => baseTask(context, {
      name: `join_${context.suffix}`,
      joinOn: [],
      optional: false,
    }),
  },
  {
    type: TaskType.TERMINATE,
    label: '终止',
    icon: LightningIcon,
    appearance: { cardVariant: 'operator' },
    flow: { blocksContinuation: true },
  },
  {
    type: TaskType.TERMINAL,
    label: '起止节点',
    flow: { kind: 'terminal', nodeSize: { width: 80, height: 80 }, selectable: false },
  },
  {
    type: TaskType.SWITCH_JOIN,
    label: '分支汇聚',
    flow: { kind: 'switch_join', nodeSize: { width: 350, height: 55 }, selectable: false },
  },
])
