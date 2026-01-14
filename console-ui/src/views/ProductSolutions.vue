<template>
  <div class="product-solutions">
    <div class="intro-section">
      <p class="intro-text">
        Interface-Agent 是一个<strong>轻量级接口集成框架</strong>，通过配置化替代硬编码，让接口对接从"反复造轮子"转变为"填表配置"。
        <br/>
        <span class="intro-highlight">核心理念：框架内建集成必要能力，使用方只需关注配置，零编码完成复杂接口对接。</span>
      </p>
    </div>

    <el-tabs v-model="activeTab" type="card" class="solution-tabs">
      <!-- 核心问题概览 -->
      <el-tab-pane label="核心问题概览" name="overview">
        <div class="overview-grid">
          <el-card 
            v-for="(problem, index) in problems" 
            :key="index" 
            shadow="hover" 
            class="problem-card"
            :class="'card-' + (index % 4)"
          >
            <div class="card-header">
              <i :class="problem.icon" class="card-icon"></i>
              <h3>{{ problem.title }}</h3>
            </div>
            <div class="card-content">
              <p class="problem-desc">{{ problem.description }}</p>
              <div class="solution-preview">
                <el-tag type="success" size="small">✓ 已解决</el-tag>
                <span class="solution-text">{{ problem.solutionPreview }}</span>
              </div>
            </div>
            <el-button 
              type="text" 
              class="detail-btn"
              @click="showDetail(problem)"
            >
              查看详情 <i class="el-icon-arrow-right"></i>
            </el-button>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- 详细解决方案 -->
      <el-tab-pane 
        v-for="(problem, index) in problems" 
        :key="'detail-' + index"
        :label="problem.title" 
        :name="'detail-' + index"
      >
        <div class="solution-detail">
          <div class="detail-header">
            <i :class="problem.icon" class="detail-icon"></i>
            <h2>{{ problem.title }}</h2>
          </div>

          <el-card shadow="never" class="problem-section">
            <div slot="header" class="section-title">
              <i class="el-icon-warning"></i>
              <span>问题描述</span>
            </div>
            <div class="problem-content">
              <p>{{ problem.problemDetail }}</p>
              <ul class="pain-points">
                <li v-for="(pain, idx) in problem.painPoints" :key="idx">
                  <i class="el-icon-close"></i>
                  {{ pain }}
                </li>
              </ul>
            </div>
          </el-card>

          <el-card shadow="never" class="solution-section">
            <div slot="header" class="section-title">
              <i class="el-icon-success"></i>
              <span>解决方案</span>
            </div>
            <div class="solution-content">
              <p class="solution-main">{{ problem.solutionDetail }}</p>
              <div class="features">
                <div 
                  v-for="(feature, idx) in problem.features" 
                  :key="idx" 
                  class="feature-item"
                >
                  <div class="feature-header">
                    <i class="el-icon-check"></i>
                    <strong>{{ feature.name }}</strong>
                  </div>
                  <p class="feature-desc">{{ feature.description }}</p>
                  <div v-if="feature.example" class="feature-example">
                    <span class="example-label">示例：</span>
                    <code>{{ feature.example }}</code>
                  </div>
                </div>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" class="benefit-section">
            <div slot="header" class="section-title">
              <i class="el-icon-trophy"></i>
              <span>实际收益</span>
            </div>
            <div class="benefits">
              <el-tag 
                v-for="(benefit, idx) in problem.benefits" 
                :key="idx"
                type="success"
                effect="plain"
                class="benefit-tag"
              >
                {{ benefit }}
              </el-tag>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- AI赋能潜力 -->
      <el-tab-pane label="AI赋能潜力" name="ai">
        <div class="ai-section">
          <el-alert
            title="未来展望：AI驱动的智能化对接"
            type="info"
            description="Interface-Agent的架构设计天然支持AI能力集成，通过AI可以进一步降低接入门槛，提升配置效率。"
            :closable="false"
            show-icon
          />

          <div class="ai-capabilities">
            <el-card 
              v-for="(capability, index) in aiCapabilities" 
              :key="index"
              shadow="hover"
              class="ai-card"
            >
              <div class="ai-header">
                <i :class="capability.icon"></i>
                <h3>{{ capability.title }}</h3>
              </div>
              <p class="ai-desc">{{ capability.description }}</p>
              <div class="ai-scenario">
                <div class="scenario-title">应用场景</div>
                <p>{{ capability.scenario }}</p>
              </div>
              <div class="ai-example">
                <div class="example-title">示例</div>
                <div class="example-content">
                  <div class="example-input">
                    <span class="example-label">输入：</span>
                    <span>{{ capability.example.input }}</span>
                  </div>
                  <i class="el-icon-right"></i>
                  <div class="example-output">
                    <span class="example-label">输出：</span>
                    <span>{{ capability.example.output }}</span>
                  </div>
                </div>
              </div>
            </el-card>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
export default {
  name: 'ProductSolutions',
  data() {
    return {
      activeTab: 'overview',
      problems: [
        {
          title: '多协议适配重复开发',
          icon: 'el-icon-connection',
          description: '每个项目都要写HTTP调用、WebService解析、数据库连接代码，反复造轮子。',
          solutionPreview: '框架内建多协议支持，通过配置选择协议类型',
          problemDetail: '对接三方系统时，每次都要写HTTP请求代码、WebService SOAP解析、JDBC数据库访问。即使逻辑基本一样，也要每个项目重复写一遍，严重浪费开发时间。',
          painPoints: [
            '每个项目都要写协议适配代码，重复劳动',
            '不同协议的请求构造、响应解析逻辑分散',
            '错误处理、超时控制每次都要重新实现',
            '新协议接入需要复制粘贴代码，容易出错'
          ],
          solutionDetail: '框架内建 HTTP、WebService、JDBC 等协议调用器，通过配置 remote_extension 字段选择协议类型，框架自动处理请求构造和响应解析。',
          features: [
            {
              name: '内建协议支持',
              description: 'HTTP、WebService、JDBC等常用协议开箱即用',
              example: 'remote_extension: "http"'
            },
            {
              name: '配置选择协议',
              description: '通过配置字段指定协议类型，框架自动路由',
              example: 'remote_extension: "webservice"'
            },
            {
              name: '自动格式转换',
              description: 'JSON/XML自动转换，无需手动处理',
              example: 'adaptRequest(workflow, requestBody)'
            },
            {
              name: 'SPI扩展机制',
              description: '需要特殊协议时，通过SPI扩展即可',
              example: '@SPI("custom-protocol")'
            }
          ],
          benefits: [
            '一次开发，处处可用',
            '新项目零编码接入',
            '协议切换只需改配置',
            '统一的错误处理和超时控制'
          ]
        },
        {
          title: '参数映射硬编码维护',
          icon: 'el-icon-share',
          description: '字段名不匹配、数据结构不一致，每次都要写一堆 if-else 和 set方法。',
          solutionPreview: '树形配置+SpEL表达式，参数映射配置化',
          problemDetail: '上游系统传patientId，下游要patient_code；上游是扁平结构，下游要嵌套对象；上游传单个，下游要数组。每次对接都要写一堆字段转换代码，修改需求时要翻代码，容易改出 bug。',
          painPoints: [
            '参数映射逻辑硬编码在适配类中',
            '字段名、类型、结构转换混在一起，代码难懂',
            '修改映射规则需要改代码、重新发布',
            '复杂嵌套结构转换代码又臭又长'
          ],
          solutionDetail: '通过数据库存储的树形配置，支持前置（请求）和后置（响应）双向映射。配合 5 种映射类型和 SpEL 表达式，可处理任意复杂的参数转换。',
          features: [
            {
              name: '树形结构映射',
              description: '支持object、array嵌套，通过parent_id构建参数树',
              example: 'patientInfo.name → patient.patientName'
            },
            {
              name: '五种映射类型',
              description: 'CONSTANT(固定值)、NAME(名称映射)、EXPRESSION(SpEL表达式)、BEAN_EXPRESSION(Bean调用)、SINGLE_MAP(单值映射)',
              example: 'EXPRESSION: #data["idCard"] ?: #data["phone"]'
            },
            {
              name: '双向映射',
              description: 'PRE_PROCESS处理请求参数，POST_PROCESS处理响应参数',
              example: 'process_type: PRE_PROCESS / POST_PROCESS'
            },
            {
              name: '数据来源选择',
              description: 'POST_PROCESS支持从INPUT(原始入参)或RESPONSE(上游响应)取值',
              example: 'mapping_source: INPUT / RESPONSE'
            }
          ],
          benefits: [
            '参数映射配置化，无需编码',
            '可视化配置界面，一目了然',
            'SpEL表达式支持复杂逻辑',
            '配置可导入导出复用'
          ]
        },
        {
          title: '工作流编排与调度',
          icon: 'el-icon-s-operation',
          description: '多步骤业务流程编排复杂，串行/并行混合调度难实现，条件流转逻辑分散。',
          solutionPreview: 'DAG工作流引擎，支持条件流转和并行调度',
          problemDetail: '复杂业务场景往往需要多步骤串联：查询患者信息 → 调用体检接口 → 写入报告数据。还可能涉及并行调用：同时调用多个三方接口后合并结果。传统代码实现这些逻辑需要大量if-else和回调嵌套，维护成本高。',
          painPoints: [
            '多步骤流程硬编码在业务代码中',
            '流程变更需要修改代码重新发布',
            '并行调用需手动管理线程池和结果合并',
            '条件流转逻辑与业务代码混杂'
          ],
          solutionDetail: '基于SpEL表达式的DAG工作流引擎，支持单节点、并行节点、顺序流转，自动结果合并，调度参数来源可选。',
          features: [
            {
              name: 'DAG工作流',
              description: '有向无环图模型，通过schedule_expr配置下一跳节点',
              example: 'schedule_expr: #data["code"]=="200" ? [2,3] : [4]'
            },
            {
              name: '并行节点调度',
              description: '多个并行节点使用线程池异步执行，CompletableFuture自动等待合并',
              example: 'processParallelNode(nodes, businessData)'
            },
            {
              name: '自动结果合并',
              description: '数组类型自动聚合，对象类型递归合并，保留所有节点返回值',
              example: 'mergeResults(nodeResultList)'
            },
            {
              name: '参数来源选择',
              description: 'schedule_param_source_type控制下游节点入参来源',
              example: 'ORIGINAL(原始入参) / PREVIOUS(上游响应)'
            }
          ],
          benefits: [
            '流程可视化配置，无需编码',
            '流程变更无需重启服务',
            '并行调度自动化',
            '条件流转配置化'
          ]
        },
        {
          title: '业务数据并行处理',
          icon: 'el-icon-s-unfold',
          description: '批量数据需要串行处理效率低，手动实现并行拆分逻辑复杂。',
          solutionPreview: '字段拆分并行处理，自动线程池调度',
          problemDetail: '当一次请求中包含批量数据(如多个患者的体检申请)时，如果逐个串行调用下游接口，性能会很差。手动实现并行拆分需要管理线程池、Future等，代码复杂且容易出错。',
          painPoints: [
            '批量数据串行处理性能瓶颈',
            '手动拆分并行逻辑代码量大',
            '线程池管理、异常处理复杂',
            '结果合并逻辑容易出错'
          ],
          solutionDetail: '通过split_field配置指定拆分字段，框架自动将数组拆分为多个独立任务，使用WorkFlowNodeExecutor线程池并发执行，CompletableFuture异步编排自动等待合并。',
          features: [
            {
              name: '字段拆分配置',
              description: '配置split_field指定需要拆分的数组字段',
              example: 'split_field: "patients"'
            },
            {
              name: '自动并行调度',
              description: 'CompletableFuture.supplyAsync并发执行，无需手动管理线程',
              example: 'parallelSplitNodeProcess(flowNode, businessData)'
            },
            {
              name: '线程池隔离',
              description: 'WorkFlowNodeExecutor专用线程池，资源隔离',
              example: '@Qualifier("WorkFlowNodeExecutor")'
            },
            {
              name: '结果自动合并',
              description: 'allOf等待所有任务完成，mergeResults聚合结果',
              example: 'CompletableFuture.allOf(futures).join()'
            }
          ],
          benefits: [
            '批量处理性能大幅提升',
            '配置化拆分，无需编码',
            '自动线程池管理',
            '结果合并自动化'
          ]
        },
        {
          title: '动态扩展能力不足',
          icon: 'el-icon-magic-stick',
          description: '新协议接入需要重启服务，无法热更新，扩展点编译部署周期长。',
          solutionPreview: '动态SPI管理器，运行时编译加载，热更新无需重启',
          problemDetail: '传统的SPI扩展需要编译、打包、部署、重启服务。对于紧急需求或实验性扩展，这个周期太长。而且每次新增扩展都要修改代码库，管理混乱。',
          painPoints: [
            '新扩展需要重启服务，影响在线业务',
            '扩展点代码与框架代码混杂',
            '实验性扩展污染代码库',
            '扩展版本管理困难'
          ],
          solutionDetail: '动态SPI管理器支持运行时编译加载Java源码，每个动态类使用独立ClassLoader隔离，支持完整类卸载和热更新，双模式加载(源码编译/classpath加载)。',
          features: [
            {
              name: '运行时编译',
              description: 'JavaCompiler动态编译源码，内存加载class',
              example: 'compileAndLoad(sourceCode)'
            },
            {
              name: '独立ClassLoader',
              description: '每个扩展独立ClassLoader，实现类级隔离和卸载',
              example: 'DynamicClassLoader(parent, className)'
            },
            {
              name: '热更新支持',
              description: '卸载旧ClassLoader，重新加载新版本，无需重启',
              example: 'refresh(dynamicSpi)'
            },
            {
              name: '双模式加载',
              description: '支持源码编译模式和项目classpath模式',
              example: 'sourceCode为空时从classpath加载'
            }
          ],
          benefits: [
            '零停机新增扩展',
            '热更新即时生效',
            '扩展代码独立管理',
            '实验性扩展快速验证'
          ]
        },
        {
          title: '调试依赖三方环境',
          icon: 'el-icon-view',
          description: '对接调试时依赖三方环境，环境不稳定影响开发进度。',
          solutionPreview: '节点级 Mock 配置，多场景切换，一键启用/禁用',
          problemDetail: '接口对接开发过程中，常遇三方环境不稳定、测试数据难准备、接口限流等问题。传统做法是在代码中硬编码 Mock 数据或搭建 Mock 服务器，维护成本高。',
          painPoints: [
            '调试时依赖三方环境，开发效率低',
            '三方环境不稳定或限流，影响开发进度',
            'Mock 数据硬编码在代码中，修改需要重启',
            '难以模拟成功/失败/异常等多种场景'
          ],
          solutionDetail: '每个节点都可配置 Mock 响应数据，支持多场景（case_name）切换。提供独立 Mock 调用接口，可单独测试节点。启用/禁用开关灵活切换真实/Mock 模式。',
          features: [
            {
              name: '节点级 Mock',
              description: '为每个工作流节点配置 Mock 响应数据，精确控制',
              example: 'node_id: 1, mock_response: {...}'
            },
            {
              name: '多场景 Mock',
              description: '同一节点支持多个 Mock 场景，模拟成功/失败/异常',
              example: 'case_name: "成功场景" / "失败场景"'
            },
            {
              name: '独立测试接口',
              description: '可单独调用 Mock 节点，无需走完整工作流',
              example: 'POST /v1/console/mock/invoke'
            },
            {
              name: '一键切换模式',
              description: '通过 enabled 开关控制，灵活切换真实/Mock 模式',
              example: 'enabled: 1 (启用) / 0 (禁用)'
            }
          ],
          benefits: [
            '脱离三方环境开发，提升效率',
            '快速验证参数映射逻辑',
            '多场景 Mock 覆盖各种情况',
            '一键切换真实/Mock 模式'
          ]
        },
        {
          title: '参数过滤与条件执行',
          icon: 'el-icon-s-tools',
          description: '不是所有数据都需要调用下游，缺少前置过滤机制，造成无效调用。',
          solutionPreview: 'SpEL表达式过滤，前置条件判断，自动跳过',
          problemDetail: '业务场景中经常遇到：只有特定条件的数据才需要调用某个接口。比如只有VIP患者才调用高端体检接口。如果没有前置过滤，所有数据都会走一遍接口调用，造成资源浪费。',
          painPoints: [
            '无效数据也会触发下游接口调用',
            '过滤逻辑散落在各处代码中',
            '难以统一管理过滤规则',
            '浪费接口调用配额和性能'
          ],
          solutionDetail: '通过param_filter_expr配置SpEL表达式过滤业务数据，过滤后数据为空直接跳过节点执行，keyword_field配置支持数据路由。',
          features: [
            {
              name: 'SpEL过滤表达式',
              description: '使用param_filter_expr配置过滤条件',
              example: 'param_filter_expr: #data["vipLevel"] > 3'
            },
            {
              name: '自动跳过执行',
              description: '过滤后数据为空时，直接返回空Map，不调用下游',
              example: 'businessDataAfterPost == null || isEmpty()'
            },
            {
              name: '关键字路由',
              description: 'keyword_field配置数据路由关键字',
              example: 'keyword_field: "patientCode"'
            },
            {
              name: '前置处理',
              description: '在节点调用前执行过滤，避免无效调用',
              example: 'paramFilterPostProcess(expr, data)'
            }
          ],
          benefits: [
            '减少无效接口调用',
            '过滤规则配置化',
            '节省接口配额',
            '提升系统性能'
          ]
        },
        {
          title: '每次对接都要写代码',
          icon: 'el-icon-s-marketing',
          description: '一个接口对接需要写适配类、映射逻辑、配置文件，反复劳动。',
          solutionPreview: '可视化配置界面，所有配置数据库存储，零编码对接',
          problemDetail: '传统接口对接需要开发人员编写适配类、参数转换逻辑、流程编排代码。每次对接都是重复劳动，非技术人员完全无法参与。历史对接逻辑难以复用，每次都要从头开始。',
          painPoints: [
            '每次对接都要写代码，开发成本高',
            '配置分散在代码、注解、yml文件中',
            '历史对接逻辑难以复用，每次从头写',
            '非技术人员无法参与配置'
          ],
          solutionDetail: '提供完整的可视化配置界面，工作流、节点、参数映射全部数据库存储。配合配置映射演示、导入导出、调用日志等功能，实现零编码对接。',
          features: [
            {
              name: '可视化配置界面',
              description: 'Vue.js 前端，工作流/节点/参数映射全部可视化配置',
              example: '工作流管理 / 节点配置 / 参数映射'
            },
            {
              name: '配置数据库存储',
              description: '所有配置持久化到数据库，支持 MySQL/SQL Server/OpenGauss',
              example: 'interface_workflow / interface_flow_node / node_param_config'
            },
            {
              name: '配置映射演示',
              description: '动画演示参数映射配置过程，可视化学习',
              example: '档案创建 / 申请单创建 / 账单创建'
            },
            {
              name: '导入导出配置',
              description: 'JSON 格式导入导出，配置快速复用，减少重复工作',
              example: 'exportNodeConfig() / importNodeConfig()'
            },
            {
              name: '调用日志追溯',
              description: '记录业务数据、请求参数、远程响应、响应参数，全链路可追溯',
              example: 'interface_invoke_log 表'
            }
          ],
          benefits: [
            '零编码完成接口对接',
            '配置统一管理，一目了然',
            '历史配置可复用，提升效率',
            '降低技术门槛，业务人员也能配'
          ]
        }
      ],
      aiCapabilities: [
        {
          icon: 'el-icon-cpu',
          title: '智能表达式生成',
          description: 'AI根据业务需求描述，自动生成SpEL映射表达式，无需手动编写复杂逻辑。',
          scenario: '当用户描述"如果身份证为空则使用手机号加后缀hl"，AI自动生成对应的SpEL表达式。',
          example: {
            input: '如果身份证为空则用手机号加hl后缀',
            output: '#data["idCard"] ?: #data["phone"] + "hl"'
          }
        },
        {
          icon: 'el-icon-document',
          title: '接口文档分析',
          description: 'AI分析上下游接口文档(Swagger/OpenAPI/Word)，自动生成参数映射配置，极大降低配置工作量。',
          scenario: '上传上下游接口文档，AI自动识别字段对应关系，生成完整的参数映射树。',
          example: {
            input: '上游patientInfo.name → 下游patient.patientName',
            output: 'JSON配置：{sourceParamKey: "patientInfo.name", targetParamKey: "patientName", ...}'
          }
        },
        {
          icon: 'el-icon-magic-stick',
          title: '动态代码生成',
          description: 'AI根据特殊转换需求生成Java代码，通过动态SPI加载，无需手动编写扩展类。',
          scenario: '当标准映射无法满足时，AI生成自定义转换逻辑代码，动态加载生效。',
          example: {
            input: '需要将日期格式从yyyy-MM-dd转为yyyyMMdd',
            output: 'Java代码：DateFormatter.format(date, "yyyyMMdd")'
          }
        },
        {
          icon: 'el-icon-s-operation',
          title: '工作流智能编排',
          description: 'AI根据业务流程描述，自动生成工作流节点和流转规则，快速搭建复杂流程。',
          scenario: '描述"先查询患者信息，如果是VIP则调用高端体检接口，否则调用普通接口"，AI生成完整工作流。',
          example: {
            input: '查询患者→判断VIP→分别调用不同接口',
            output: '节点1(查询) → 条件表达式 → 节点2(VIP接口) / 节点3(普通接口)'
          }
        },
        {
          icon: 'el-icon-data-analysis',
          title: '日志智能分析',
          description: 'AI分析调用日志，识别异常模式，提供优化建议和故障诊断。',
          scenario: '当某个节点频繁失败时，AI分析日志数据，给出可能的原因和修复建议。',
          example: {
            input: '节点A调用失败率80%',
            output: '检测到参数映射错误，建议检查字段：patient.idCard'
          }
        }
      ]
    };
  },
  methods: {
    showDetail(problem) {
      const index = this.problems.indexOf(problem);
      this.activeTab = 'detail-' + index;
    }
  }
};
</script>

<style scoped>
.product-solutions {
  padding: 20px;
}

.intro-section {
  margin-bottom: 20px;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  color: white;
}

.intro-text {
  font-size: 16px;
  line-height: 1.8;
  margin: 0;
  text-align: center;
}

.intro-highlight {
  display: block;
  margin-top: 10px;
  font-size: 14px;
  font-style: italic;
  opacity: 0.95;
}

.solution-tabs {
  margin-top: 20px;
}

/* 概览网格 */
.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
  padding: 20px 0;
}

.problem-card {
  transition: transform 0.3s, box-shadow 0.3s;
  border-radius: 8px;
}

.problem-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.card-0 { border-left: 4px solid #409eff; }
.card-1 { border-left: 4px solid #67c23a; }
.card-2 { border-left: 4px solid #e6a23c; }
.card-3 { border-left: 4px solid #f56c6c; }

.card-header {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.card-icon {
  font-size: 28px;
  margin-right: 12px;
  color: #409eff;
}

.card-header h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.card-content {
  margin-bottom: 15px;
}

.problem-desc {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 15px;
}

.solution-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background-color: #f0f9ff;
  border-radius: 4px;
}

.solution-text {
  font-size: 13px;
  color: #409eff;
  flex: 1;
}

.detail-btn {
  float: right;
  padding: 0;
}

/* 详细解决方案 */
.solution-detail {
  padding: 20px 0;
}

.detail-header {
  display: flex;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 15px;
  border-bottom: 2px solid #e4e7ed;
}

.detail-icon {
  font-size: 36px;
  margin-right: 15px;
  color: #409eff;
}

.detail-header h2 {
  margin: 0;
  color: #303133;
}

.problem-section,
.solution-section,
.benefit-section {
  margin-bottom: 20px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: bold;
  font-size: 16px;
}

.section-title i {
  font-size: 18px;
}

.problem-content,
.solution-content {
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
}

.pain-points {
  list-style: none;
  padding: 0;
  margin: 15px 0 0 0;
}

.pain-points li {
  padding: 8px 0;
  display: flex;
  align-items: center;
  color: #f56c6c;
}

.pain-points li i {
  margin-right: 8px;
  font-size: 16px;
}

.solution-main {
  margin-bottom: 20px;
}

.features {
  display: grid;
  gap: 15px;
}

.feature-item {
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 6px;
  border-left: 3px solid #67c23a;
}

.feature-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #67c23a;
}

.feature-desc {
  margin: 8px 0;
  color: #606266;
}

.feature-example {
  margin-top: 10px;
  padding: 10px;
  background-color: #fff;
  border-radius: 4px;
  font-size: 13px;
}

.example-label {
  color: #909399;
  margin-right: 5px;
}

.feature-example code {
  color: #e6a23c;
  font-family: 'Courier New', monospace;
}

.benefits {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.benefit-tag {
  padding: 8px 16px;
  font-size: 14px;
}

/* AI能力部分 */
.ai-section {
  padding: 20px 0;
}

.ai-capabilities {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(450px, 1fr));
  gap: 20px;
  margin-top: 30px;
}

.ai-card {
  border-radius: 8px;
  transition: transform 0.3s;
}

.ai-card:hover {
  transform: translateY(-3px);
}

.ai-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 15px;
}

.ai-header i {
  font-size: 32px;
  color: #409eff;
}

.ai-header h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.ai-desc {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 15px;
}

.ai-scenario,
.ai-example {
  margin-top: 15px;
}

.scenario-title,
.example-title {
  font-size: 13px;
  font-weight: bold;
  color: #909399;
  margin-bottom: 8px;
}

.example-content {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
}

.example-input,
.example-output {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.example-label {
  font-weight: bold;
  color: #909399;
}

.example-content i {
  color: #409eff;
  font-size: 16px;
}
</style>
