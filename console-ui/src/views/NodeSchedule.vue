<template>
  <div class="node-schedule">
    <!-- 顶部操作栏 -->
    <div style="margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center;">
      <div>
        <!-- 视图切换 -->
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button label="flow">
            <i class="el-icon-share"></i> 流程视图
          </el-radio-button>
          <el-radio-button label="list">
            <i class="el-icon-s-grid"></i> 列表视图
          </el-radio-button>
        </el-radio-group>
      </div>
      <div>
        <el-button icon="el-icon-question" @click="showSpelHelp">SPEL调度表达式帮助</el-button>
        <el-button type="primary" icon="el-icon-plus" @click="handleAdd">新增调度</el-button>
      </div>
    </div>

    <!-- 列表视图 -->
    <el-table :data="scheduleList" v-loading="loading" style="width: 100%" v-show="viewMode === 'list'">
      <el-table-column label="源节点" width="200">
        <template #default="scope">
          <div class="node-info">
            <div class="node-icon-small" :class="getNodeIconClass(scope.row.nodeType)">
              <i :class="getNodeIcon(scope.row.nodeType)"></i>
            </div>
            <span>{{ scope.row.nodeName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="调度表达式" show-overflow-tooltip>
        <template #default="scope">
          <code class="expr-code">{{ scope.row.scheduleExpr || '未设置' }}</code>
        </template>
      </el-table-column>
      <el-table-column label="调度参数来源" width="150">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.scheduleParamSourceType === 'ORIGINAL' ? 'primary' : 'success'">
            {{ getParamSourceLabel(scope.row.scheduleParamSourceType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标节点" width="250">
        <template #default="scope">
          <el-tag
            v-for="nodeId in scope.row.nextNodeIds"
            :key="nodeId"
            size="small"
            style="margin-right: 5px;"
          >
            {{ getNodeName(nodeId) }}
          </el-tag>
          <span v-if="!scope.row.nextNodeIds || scope.row.nextNodeIds.length === 0" style="color: #999;">未设置</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 流程视图 -->
    <div v-show="viewMode === 'flow'" class="flow-container" v-loading="loading">
      <div class="flow-canvas">
        <!-- 只显示配置了调度的节点链路 -->
        <div
          v-for="(chain, chainIndex) in scheduleChains"
          :key="'chain-' + chainIndex"
          class="schedule-chain-wrapper"
          @click="handleEdit(chain.sourceNode)"
        >
          <div class="chain-row">
            <!-- 源节点 -->
            <div class="chain-source">
              <div class="flow-node source-node">
                <div class="node-icon">
                  <i :class="getNodeIcon(chain.sourceNode.nodeType)"></i>
                </div>
                <div class="node-name">{{ chain.sourceNode.nodeName }}</div>
              </div>
            </div>

            <!-- 连接线和条件 -->
            <div class="chain-connector">
              <div class="connector-line"></div>
              <div class="condition-wrapper">
                <div class="condition-text">{{ chain.condition }}</div>
                <div class="condition-badge">条件</div>
              </div>
              <div class="connector-line"></div>
            </div>

            <!-- 目标节点列表 -->
            <div class="chain-targets">
              <div
                v-for="targetNode in chain.targetNodes"
                :key="targetNode.nodeId"
                class="target-item"
              >
                <div class="flow-node target-node">
                  <div class="node-icon">
                    <i :class="getNodeIcon(targetNode.nodeType)"></i>
                  </div>
                  <div class="node-name">{{ targetNode.nodeName }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态提示 -->
        <div v-if="scheduleChains.length === 0" class="empty-state">
          <i class="el-icon-folder-opened"></i>
          <p>暂无调度配置</p>
          <el-button type="primary" size="small" @click="handleAdd">新增调度</el-button>
        </div>
      </div>
    </div>

    <!-- 编辑/新增对话框 -->
    <el-dialog
      :title="dialogTitle"
      :visible.sync="dialogVisible"
      width="700px"
      @close="resetForm"
    >
      <el-form
        :model="scheduleForm"
        :rules="scheduleRules"
        ref="scheduleFormRef"
        label-width="140px"
      >
        <el-form-item label="选择节点" prop="nodeId">
          <el-select
            v-model="scheduleForm.nodeId"
            placeholder="请选择节点"
            filterable
            style="width: 100%;"
            @change="handleNodeChange"
          >
            <el-option
              v-for="node in allNodes"
              :key="node.nodeId"
              :label="node.nodeName"
              :value="node.nodeId"
            >
              <span style="float: left;">
                <i :class="getNodeIcon(node.nodeType)" style="margin-right: 5px;"></i>
                {{ node.nodeName }}
              </span>
              <span style="float: right; color: #8492a6; font-size: 13px;">{{ node.nodeType }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <!-- 可视化配置模式切换 -->
        <el-form-item label="配置模式">
          <el-radio-group v-model="configMode">
            <el-radio label="visual">可视化配置</el-radio>
            <el-radio label="manual">手动编写</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 可视化配置区域 -->
        <div v-if="configMode === 'visual'">
          <el-form-item label="条件配置">
            <div class="condition-builder">
              <el-row :gutter="10">
                <el-col :span="8">
                  <el-select v-model="conditionConfig.paramName" placeholder="选择参数" filterable>
                    <el-option
                      v-for="param in postProcessParams"
                      :key="param.key"
                      :label="param.label"
                      :value="param.key"
                    >
                      <span>{{ param.label }}</span>
                      <span style="float: right; color: #8492a6; font-size: 12px;">{{ param.type }}</span>
                    </el-option>
                  </el-select>
                </el-col>
                <el-col :span="4">
                  <el-select v-model="conditionConfig.operator" placeholder="操作符">
                    <el-option label="等于 ==" value="=="></el-option>
                    <el-option label="不等于 !=" value="!="></el-option>
                    <el-option label="大于 >" value=">"></el-option>
                    <el-option label="小于 <" value="<"></el-option>
                    <el-option label="大于等于 >=" value=">="></el-option>
                    <el-option label="小于等于 <=" value="<="></el-option>
                  </el-select>
                </el-col>
                <el-col :span="8">
                  <el-input v-model="conditionConfig.value" placeholder="比较值"></el-input>
                </el-col>
                <el-col :span="4">
                  <el-button @click="loadPostProcessParams" icon="el-icon-refresh" circle title="刷新参数列表"></el-button>
                </el-col>
              </el-row>
            </div>
            <div style="font-size: 12px; color: #999; margin-top: 5px;">
              从节点的后置处理参数中选择条件
            </div>
          </el-form-item>

          <el-form-item label="调度目标节点" prop="targetNodeIds">
            <el-select
              v-model="conditionConfig.targetNodeIds"
              multiple
              placeholder="选择要调度的节点"
              style="width: 100%;"
            >
              <el-option
                v-for="node in allNodes.filter(n => n.nodeId !== scheduleForm.nodeId)"
                :key="node.nodeId"
                :label="node.nodeName"
                :value="node.nodeId"
              >
                <span style="float: left;">
                  <i :class="getNodeIcon(node.nodeType)" style="margin-right: 5px;"></i>
                  {{ node.nodeName }}
                </span>
                <span style="float: right; color: #8492a6; font-size: 13px;">{{ node.nodeType }}</span>
              </el-option>
            </el-select>
          </el-form-item>

          <!-- 生成的表达式预览 -->
          <el-form-item label="生成的表达式">
            <div class="expr-preview">
              <code>{{ generatedExpression }}</code>
            </div>
          </el-form-item>
        </div>

        <!-- 手动编写表达式 -->
        <el-form-item label="调度表达式" prop="scheduleExpr" v-if="configMode === 'manual'">
          <el-input
            v-model="scheduleForm.scheduleExpr"
            type="textarea"
            :rows="3"
            placeholder="请输入SPEL表达式，例如：#data[code] == '200' ? T(java.util.Arrays).asList(2) : null"
          >
            <template slot="append">
              <el-button @click="showSpelHelp">帮助</el-button>
            </template>
          </el-input>
          <div style="font-size: 12px; color: #999; margin-top: 5px;">
            表达式根据当前节点响应数据判断，返回值为要调度的节点ID列表（List&lt;Integer&gt;）
          </div>
        </el-form-item>

        <el-form-item label="调度参数来源" prop="scheduleParamSourceType">
          <el-select v-model="scheduleForm.scheduleParamSourceType" placeholder="请选择参数来源" style="width: 100%;">
            <el-option label="原始入参" value="ORIGINAL">
              <span>原始入参</span>
              <span style="float: right; color: #8492a6; font-size: 13px;">使用工作流初始输入</span>
            </el-option>
            <el-option label="当前节点响应" value="PRE_RESPONSE">
              <span>当前节点响应</span>
              <span style="float: right; color: #8492a6; font-size: 13px;">使用节点执行结果</span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>

      <span slot="footer" class="dialog-footer">
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitSchedule">确 定</el-button>
      </span>
    </el-dialog>

    <!-- SPEL表达式帮助对话框 -->
    <el-dialog
      :visible.sync="spelHelpVisible"
      width="800px"
    >
      <div class="spel-help">
        <h4>节点调度表达式说明</h4>
        <p>节点调度表达式用于根据当前节点的响应数据，<strong>决定接下来要调度哪些节点</strong>。</p>

        <h4>表达式格式</h4>
        <div class="example-block">
          <code>#data[字段名] 条件判断 ? T(java.util.Arrays).asList(节点ID列表) : null</code>
          <p class="explain">条件满足时调度指定节点，不满足则不调度</p>
        </div>

        <h4>核心要点</h4>
        <ul>
          <li><code>#data</code> - 当前节点的响应数据</li>
          <li><code>#data[字段名]</code> - 获取响应中的某个字段（字段名不带引号）</li>
          <li>条件判断 - 支持 <code>==</code> <code>!=</code> <code>&gt;</code> <code>&lt;</code> <code>and</code> <code>or</code></li>
          <li>字符串值要加引号，如 <code>'200'</code> <code>'success'</code></li>
          <li>返回节点ID列表：<code>T(java.util.Arrays).asList(2, 3)</code> 表示调度节点2和3</li>
          <li>返回 <code>null</code> 表示不触发任何调度</li>
        </ul>

        <h4>典型示例</h4>
        <div class="example-block">
          <p><strong>示例1：</strong>响应码判断</p>
          <code>#data[code] == '200' ? T(java.util.Arrays).asList(2, 3) : null</code>
          <p class="explain">当响应的code等于'200'时，调度节点2和3，否则不调度</p>
        </div>

        <div class="example-block">
          <p><strong>示例2：</strong>状态分支调度</p>
          <code>#data[status] == 'success' ? T(java.util.Arrays).asList(5) : T(java.util.Arrays).asList(6)</code>
          <p class="explain">成功时调度节点5，失败时调度节点6</p>
        </div>

        <div class="example-block">
          <p><strong>示例3：</strong>多条件判断</p>
          <code>#data[code] == '200' and #data[count] &gt; 0 ? T(java.util.Arrays).asList(2, 3, 4) : null</code>
          <p class="explain">code为'200'且count大于0时，同时调度节点2、3、4</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getNodeList, updateNodeSchedule } from '@/api/workflownode';
import { getNodeParams } from '@/api/nodeParam';

export default {
  name: 'NodeSchedule',
  data() {
    return {
      viewMode: 'flow', // 视图模式：list 或 flow，默认流程视图
      canvasWidth: 1200,
      canvasHeight: 800,
      allNodes: [],
      scheduleList: [],
      loading: false,
      dialogVisible: false,
      spelHelpVisible: false,
      dialogTitle: '新增调度',
      isEdit: false,
      configMode: 'visual', // 配置模式：visual 或 manual
      postProcessParams: [], // 后置处理参数列表
      conditionConfig: {
        paramName: '',
        operator: '==',
        value: '',
        targetNodeIds: []
      },
      scheduleForm: {
        nodeId: null,
        nodeName: '',
        nodeType: '',
        scheduleExpr: '',
        scheduleParamSourceType: 'ORIGINAL'
      },
      scheduleRules: {
        nodeId: [
          { required: true, message: '请选择节点', trigger: 'change' }
        ],
        scheduleExpr: [
          { required: true, message: '请输入调度表达式', trigger: 'blur' }
        ],
        scheduleParamSourceType: [
          { required: true, message: '请选择参数来源', trigger: 'change' }
        ]
      }
    };
  },
  mounted() {
    this.loadNodes();
  },
  computed: {
    // 生成的SPEL表达式
    generatedExpression() {
      const { paramName, operator, value, targetNodeIds } = this.conditionConfig;

      if (!paramName || !operator || !value || !targetNodeIds || targetNodeIds.length === 0) {
        return '请完成所有配置项';
      }

      // 根据参数类型决定是否需要引号
      const param = this.postProcessParams.find(p => p.key === paramName);
      const isStringType = param && (param.type === 'STRING' || param.type === 'OBJECT' || param.type === 'ARRAY');

      // 如果是字符串类型，给值加双引号
      const formattedValue = isStringType ? `"${value}"` : value;

      // 生成节点ID列表
      const nodeIdsStr = targetNodeIds.join(', ');

      // 生成三元表达式 - 使用 T(java.util.Arrays).asList() 而不是 Arrays.asList()
      return `#data[${paramName}] ${operator} ${formattedValue} ? T(java.util.Arrays).asList(${nodeIdsStr}) : null`;
    },

    // 调度链路数据（每条链路包含：源节点 + 条件 + 目标节点列表）
    scheduleChains() {
      return this.scheduleList.map(node => {
        // 获取目标节点详细信息
        const targetNodes = (node.nextNodeIds || []).map(nodeId => {
          return this.allNodes.find(n => n.nodeId === nodeId);
        }).filter(n => n); // 过滤掉不存在的节点

        return {
          sourceNode: node,
          condition: this.extractCondition(node.scheduleExpr),
          targetNodes: targetNodes
        };
      });
    }
  },
  methods: {
    // 加载所有节点
    async loadNodes() {
      this.loading = true;
      try {
        const res = await getNodeList({
          pageNum: 1,
          pageSize: 1000
        });

        if (res.code === '200') {
          this.allNodes = res.rsp.rows || [];

          // 筛选出有调度配置的节点
          this.scheduleList = this.allNodes.filter(node =>
            node.scheduleExpr && node.scheduleExpr.trim() !== ''
          ).map(node => ({
            ...node,
            nextNodeIds: this.parseNextNodeIds(node.scheduleExpr)
          }));
        } else {
          this.$message.error('加载节点列表失败：' + (res.message || '未知错误'));
        }
      } catch (error) {
        console.error('加载节点列表失败:', error);
        this.$message.error('加载节点列表失败');
      } finally {
        this.loading = false;
      }
    },

    // 解析表达式中的节点ID
    parseNextNodeIds(expr) {
      if (!expr) return [];
      // 支持两种格式：Arrays.asList() 和 T(java.util.Arrays).asList()
      const match = expr.match(/(?:T\(java\.util\.Arrays\)|Arrays)\.asList\((\d+(?:,\s*\d+)*)\)/);
      if (match && match[1]) {
        return match[1].split(',').map(id => parseInt(id.trim()));
      }
      return [];
    },

    // 获取节点名称
    getNodeName(nodeId) {
      const node = this.allNodes.find(n => n.nodeId === nodeId);
      return node ? node.nodeName : `节点${nodeId}`;
    },

    // 获取节点图标
    getNodeIcon(nodeType) {
      const iconMap = {
        'HTTP': 'el-icon-link',
        'WEBSERVICE': 'el-icon-coordinate',
        'DATABASE': 'el-icon-coin'
      };
      return iconMap[nodeType] || 'el-icon-document';
    },

    // 获取节点图标样式类
    getNodeIconClass(nodeType) {
      const classMap = {
        'HTTP': 'icon-http',
        'WEBSERVICE': 'icon-webservice',
        'DATABASE': 'icon-database'
      };
      return classMap[nodeType] || 'icon-default';
    },

    // 获取参数来源标签
    getParamSourceLabel(type) {
      const labelMap = {
        'ORIGINAL': '原始入参',
        'PRE_RESPONSE': '节点响应'
      };
      return labelMap[type] || '未设置';
    },

    // 新增
    handleAdd() {
      this.dialogTitle = '新增调度';
      this.isEdit = false;
      this.configMode = 'visual'; // 默认使用可视化模式
      this.dialogVisible = true;
    },

    // 编辑
    handleEdit(row) {
      this.dialogTitle = '编辑调度';
      this.isEdit = true;
      this.scheduleForm.nodeId = row.nodeId;
      this.scheduleForm.nodeName = row.nodeName;
      this.scheduleForm.nodeType = row.nodeType;
      this.scheduleForm.scheduleExpr = row.scheduleExpr || '';
      this.scheduleForm.scheduleParamSourceType = row.scheduleParamSourceType || 'ORIGINAL';

      // 尝试解析表达式，如果可以解析则使用可视化模式
      if (this.parseExpressionToVisual(row.scheduleExpr)) {
        this.configMode = 'visual';
      } else {
        this.configMode = 'manual';
      }

      this.dialogVisible = true;
      // 加载后置参数
      this.$nextTick(() => {
        this.loadPostProcessParams();
      });
    },

    // 尝试将表达式解析为可视化配置
    parseExpressionToVisual(expr) {
      if (!expr) return false;

      // 解析三元表达式：#data[xxx] == "yyy" ? T(java.util.Arrays).asList(1, 2) : null
      // 支持两种格式：带引号的 #data['xxx'] 和不带引号的 #data[xxx]
      // 同时支持 Arrays.asList() 和 T(java.util.Arrays).asList()
      const regex = /#data\[(?:'([^']+)'|([^\]]+))\]\s*([=!<>]+)\s*(["']?)([^"'\s?]+)\4\s*\?\s*(?:T\(java\.util\.Arrays\)|Arrays)\.asList\(([\d,\s]+)\)/;
      const match = expr.match(regex);

      if (match) {
        // match[1] 是带引号的字段名，match[2] 是不带引号的字段名
        this.conditionConfig.paramName = match[1] || match[2];
        this.conditionConfig.operator = match[3];
        this.conditionConfig.value = match[5];
        this.conditionConfig.targetNodeIds = match[6].split(',').map(id => parseInt(id.trim()));
        return true;
      }

      return false;
    },

    // 删除
    async handleDelete(row) {
      try {
        await this.$confirm('确定要删除该调度配置吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        });

        await updateNodeSchedule({
          nodeId: row.nodeId,
          scheduleExpr: '',
          scheduleParamSourceType: null
        });

        this.$message.success('删除成功');
        this.loadNodes();
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('删除失败');
        }
      }
    },

    // 节点选择变化
    handleNodeChange(nodeId) {
      const node = this.allNodes.find(n => n.nodeId === nodeId);
      if (node) {
        this.scheduleForm.nodeName = node.nodeName;
        this.scheduleForm.nodeType = node.nodeType;
        // 自动加载该节点的后置处理参数
        this.loadPostProcessParams();
      }
    },

    // 加载后置处理参数
    async loadPostProcessParams() {
      if (!this.scheduleForm.nodeId) {
        this.$message.warning('请先选择节点');
        return;
      }

      try {
        const res = await getNodeParams({
          nodeId: this.scheduleForm.nodeId,
          processType: 'POST_PROCESS',
          pageNum: 1,
          pageSize: 1000
        });

        if (res.status === 200 && res.data.code === '200') {
          const params = res.data.rsp.rows || [];
          // 转换为下拉选项格式
          this.postProcessParams = params.map(p => ({
            key: p.targetParamKey,
            label: p.paramDesc ? `${p.targetParamKey} (${p.paramDesc})` : p.targetParamKey,
            type: p.targetParamType
          }));

          if (this.postProcessParams.length === 0) {
            this.$message.info('该节点没有配置后置处理参数');
          }
        }
      } catch (error) {
        console.error('加载后置参数失败:', error);
      }
    },

    // 显示SPEL帮助
    showSpelHelp() {
      this.spelHelpVisible = true;
    },

    // 提交调度配置
    submitSchedule() {
      this.$refs.scheduleFormRef.validate(async (valid) => {
        if (valid) {
          try {
            const node = this.allNodes.find(n => n.nodeId === this.scheduleForm.nodeId);
            if (!node) {
              this.$message.error('节点不存在');
              return;
            }

            // 根据配置模式决定表达式来源
            let scheduleExpr;
            if (this.configMode === 'visual') {
              // 可视化模式：使用生成的表达式
              if (this.generatedExpression === '请完成所有配置项') {
                this.$message.error('请完成所有配置项');
                return;
              }
              scheduleExpr = this.generatedExpression;
            } else {
              // 手动模式：使用输入的表达式
              scheduleExpr = this.scheduleForm.scheduleExpr;
            }

            await updateNodeSchedule({
              nodeId: node.nodeId,
              scheduleExpr: scheduleExpr,
              scheduleParamSourceType: this.scheduleForm.scheduleParamSourceType
            });

            this.$message.success('保存成功');
            this.dialogVisible = false;
            this.loadNodes();
          } catch (error) {
            this.$message.error('保存失败');
          }
        }
      });
    },

    // 重置表单
    resetForm() {
      if (this.$refs.scheduleFormRef) {
        this.$refs.scheduleFormRef.resetFields();
      }
      this.scheduleForm = {
        nodeId: null,
        nodeName: '',
        nodeType: '',
        scheduleExpr: '',
        scheduleParamSourceType: 'PRE_RESPONSE'
      };
      this.conditionConfig = {
        paramName: '',
        operator: '==',
        value: '',
        targetNodeIds: []
      };
      this.postProcessParams = [];
      this.configMode = 'visual';
    },

    // ========== 流程视图相关方法 ==========

    // 获取调度链路位置
    getChainPosition(chainIndex) {
      return {
        top: `${50 + chainIndex * 200}px`,
        left: '50px'
      };
    },

    // 提取条件简述
    extractCondition(expr) {
      if (!expr) return '条件';

      // 提取三元表达式中的条件部分：#data[xxx] == "yyy"
      // SPEL语法：字段名不带引号，只有字符串值才带引号
      const match = expr.match(/#data\[([^\]]+)\]\s*([=!<>]+)\s*([^?]+)/);
      if (match) {
        const paramName = match[1];
        const operator = match[2];
        let value = match[3].trim();
        // 去掉值的引号（如果有）
        value = value.replace(/^['"]|['"]$/g, '');
        return `${paramName} ${operator} ${value}`;
      }
      return '条件';
    },

    // 节点点击事件
    handleNodeClick(node) {
      if (node.scheduleExpr && node.scheduleExpr.trim() !== '') {
        this.handleEdit(node);
      } else {
        // 新增调度
        this.handleAdd();
        this.$nextTick(() => {
          this.scheduleForm.nodeId = node.nodeId;
          this.handleNodeChange(node.nodeId);
        });
      }
    }
  }
};
</script>

<style scoped>
.node-schedule {
  padding: 20px;
}

.node-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-icon-small {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: white;
  flex-shrink: 0;
}

.icon-http {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.icon-webservice {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.icon-database {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.icon-default {
  background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);
}

.expr-code {
  background-color: #f5f7fa;
  padding: 4px 8px;
  border-radius: 4px;
  color: #e6a23c;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.spel-help h4 {
  color: #303133;
  margin: 20px 0 10px;
  font-size: 16px;
  border-bottom: 2px solid #409eff;
  padding-bottom: 8px;
}

.spel-help p, .spel-help li {
  color: #606266;
  font-size: 14px;
  line-height: 1.8;
}

.spel-help ul {
  padding-left: 20px;
  margin: 10px 0;
}

.spel-help code {
  background-color: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  color: #e6a23c;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.example-block {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  border-left: 4px solid #409eff;
  margin: 15px 0;
}

.example-block p {
  margin: 8px 0;
}

.example-block code {
  display: block;
  background-color: #fff;
  padding: 10px;
  border-radius: 4px;
  margin: 10px 0;
  border: 1px solid #dcdfe6;
  word-break: break-all;
  white-space: pre-wrap;
}

.example-block .explain {
  color: #909399;
  font-size: 13px;
  font-style: italic;
}

.condition-builder {
  background-color: #f9fafc;
  padding: 15px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.expr-preview {
  background-color: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  min-height: 40px;
}

.expr-preview code {
  color: #e6a23c;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  word-break: break-all;
  white-space: pre-wrap;
}

/* 流程视图样式 */
.flow-container {
  background-color: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
  min-height: 600px;
  position: relative;
}

.flow-canvas {
  width: 100%;
  min-height: 600px;
}

/* 调度链路容器 */
.schedule-chain-wrapper {
  margin-bottom: 30px;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  transition: all 0.3s;
}

.schedule-chain-wrapper:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
}

.chain-row {
  display: flex;
  align-items: center;
  gap: 30px;
}

/* 源节点区域 */
.chain-source {
  flex-shrink: 0;
}

/* 连接线区域 */
.chain-connector {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.connector-line {
  width: 40px;
  height: 2px;
  background: linear-gradient(to right, #409eff, #67c23a);
  position: relative;
}

.connector-line:last-child::after {
  content: '';
  position: absolute;
  right: -6px;
  top: 50%;
  transform: translateY(-50%);
  width: 0;
  height: 0;
  border-left: 6px solid #67c23a;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}

/* 条件容器 */
.condition-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.condition-text {
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
  background: #f5f7fa;
  padding: 4px 12px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
}

.condition-badge {
  padding: 6px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

/* 目标节点区域 */
.chain-targets {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  flex: 1;
}

.target-item {
  flex-shrink: 0;
}



/* 节点样式 */
.flow-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 160px;
  padding: 20px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s;
  border: 2px solid #e4e7ed;
  flex-shrink: 0;
  pointer-events: none; /* 禁用节点自身的点击事件 */
}

.source-node {
  border-color: #409eff;
  border-width: 2px;
}

.target-node {
  border-color: #67c23a;
}

.node-icon {
  width: 50px;
  height: 50px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
  margin-bottom: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.node-name {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  text-align: center;
  word-break: break-all;
  line-height: 1.4;
}



/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: #909399;
}

.empty-state i {
  font-size: 64px;
  margin-bottom: 16px;
  color: #dcdfe6;
}

.empty-state p {
  font-size: 14px;
  margin-bottom: 16px;
}
</style>
