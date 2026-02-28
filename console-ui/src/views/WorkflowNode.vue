<template>
  <div class="workflow-node-list">
    <!-- 筛选区域 -->
    <div style="margin-bottom: 10px; background-color: #f5f7fa; padding: 20px; border-radius: 4px;">
      <div style="display: flex; justify-content: center; align-items: center; gap: 20px;">
        <!-- 归属工作流选择框 -->
        <div style="display: flex; align-items: center; gap: 8px;">
          <span style="font-size: 14px; color: #666;">归属工作流</span>
          <el-select
            v-model="filterFlowId"
            placeholder="全部"
            clearable
            @change="handleFilterChange"
            style="width: 180px;"
          >
            <el-option
              v-for="flow in workflowOptions"
              :key="flow.flowId"
              :label="flow.flowName"
              :value="flow.flowId"
            ></el-option>
          </el-select>
        </div>

        <div style="display: flex; gap: 10px;">
          <el-button @click="resetFilters">重置</el-button>
          <el-dropdown trigger="click" @command="handleImportCommand">
            <el-button type="primary">
              导入节点 <i class="el-icon-arrow-down el-icon--right"></i>
            </el-button>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="file">
                <i class="el-icon-upload"></i> 从文件导入
              </el-dropdown-item>
              <el-dropdown-item command="json">
                <i class="el-icon-document"></i> 从json文本导入
              </el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
          <el-button type="success" @click="handleAdd">新增节点</el-button>
        </div>
      </div>
    </div>

    <!-- 节点表格 -->
    <el-table :data="nodes" style="width: 100%" v-loading="loading">
      <el-table-column prop="nodeName" label="节点名称" width="150"></el-table-column>
      <el-table-column label="所属工作流" width="120">
        <template #default="scope">
          {{ getFlowName(scope.row.flowId) }}
        </template>
      </el-table-column>
      <el-table-column label="节点类型" width="100">
        <template #default="scope">
          <el-tag size="small">{{ scope.row.nodeType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="keywordField" label="关键字字段" width="120"></el-table-column>
      <el-table-column label="节点元数据信息" show-overflow-tooltip header-align="center">
        <template #default="scope">
          <json-preview-cell
            :content="scope.row.metaInfo"
            @click="jsonViewer.open($event, '节点元数据信息')"
          />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="500" fixed="right" header-align="center">
        <template #default="scope">
          <!-- 第一行：基础操作按钮 - 全局导出移至编辑左侧 -->
          <div style="display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 8px;">
            <el-button size="small" @click="nodeImportExport.exportNodeConfig(scope.row)">导出节点</el-button>
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" @click="handleCopy(scope.row)">复制</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </div>

          <div style="display: flex; justify-content: flex-end; gap: 8px;">
            <el-button size="small" type="warning" @click="nodeImportExport.exportNodeParams(scope.row)">导出参数配置</el-button>
            <el-button size="small" type="primary" @click="nodeImportExport.importNodeParams(scope.row)">导入参数配置</el-button>
            <el-button size="small" type="danger" @click="nodeImportExport.deleteAllConfig(scope.row)">全量删除配置</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <el-pagination
      @size-change="pagination.handleSizeChange"
      @current-change="pagination.handleCurrentChange"
      :current-page="pagination.pageNum.value"
      :page-sizes="[10, 20, 50, 100]"
      :page-size="pagination.pageSize.value"
      layout="total, sizes, prev, pager, next, jumper"
      :total="pagination.total.value"
      style="margin-top: 20px; text-align: right;"
    >
    </el-pagination>

    <!-- 编辑/新增/复制对话框 -->
    <el-dialog
      :title="dialog.title.value"
      :visible.sync="dialog.visible.value"
      width="1000px"
      @close="resetForm"
    >
      <div class="node-config-container">
        <!-- 左侧：基础信息表单 -->
        <div class="form-section">
          <div class="section-title">基础信息</div>
          <el-form
            :model="form"
            :rules="rules"
            ref="formRef"
            label-width="120px"
          >
            <el-form-item label="节点名称" prop="nodeName">
              <el-input v-model="form.nodeName" placeholder="请输入节点名称"></el-input>
            </el-form-item>

            <el-form-item label="所属工作流" prop="flowId">
              <el-select v-model.number="form.flowId" placeholder="请选择所属工作流" clearable style="width: 100%">
                <el-option
                  v-for="flow in workflowOptions"
                  :key="flow.flowId"
                  :label="flow.flowName"
                  :value="flow.flowId"
                ></el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="节点类型" prop="nodeType">
              <el-select v-model="form.nodeType" placeholder="请选择节点类型" @change="handleNodeTypeChange">
                <el-option label="HTTP(S)" value="HTTP"></el-option>
                <el-option label="WEBSERVICE" value="WEBSERVICE"></el-option>
                <el-option label="数据库" value="DATABASE"></el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="关键字字段" prop="keywordField">
              <el-input v-model="form.keywordField" placeholder="请输入关键字字段"></el-input>
            </el-form-item>

            <el-form-item label="拆分字段" prop="splitField">
              <el-input v-model="form.splitField" placeholder="请输入拆分字段"></el-input>
            </el-form-item>

            <el-form-item label="参数过滤表达式" prop="paramFilterExpr">
              <el-input v-model="form.paramFilterExpr" placeholder="请输入参数过滤表达式"></el-input>
            </el-form-item>
          </el-form>
        </div>

        <!-- 右侧：元信息配置区域 -->
        <div class="meta-config-section">
          <div class="section-title">元信息配置</div>
          <el-form
            :model="metaFields"
            :rules="metaRules"
            ref="metaFormRef"
            label-width="120px"
          >
            <!-- HTTP节点配置 -->
            <template v-if="form.nodeType === 'HTTP'">
              <el-form-item label="请求地址" prop="url" required>
                <el-input v-model="metaFields.url" placeholder="例如: http://127.0.0.1:8807/api/create" style="width: 70%;"></el-input>
              </el-form-item>
              <el-form-item label="请求方法" prop="method">
                <el-select v-model="metaFields.method" placeholder="请选择请求方法" style="width: 30%;">
                  <el-option label="POST" value="POST"></el-option>
                  <el-option label="GET" value="GET"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item label="请求头">
                <div class="headers-editor">
                  <div v-for="(header, index) in metaFields.headers" :key="index" class="header-row">
                    <el-select v-model="header.key" placeholder="选择或输入Header名称" filterable allow-create style="flex: 0.8; min-width: 150px;">
                      <el-option label="Content-Type" value="Content-Type"></el-option>
                      <el-option label="Authorization" value="Authorization"></el-option>
                    </el-select>
                    <el-select
                      v-if="header.key === 'Content-Type'"
                      v-model="header.value"
                      placeholder="选择Content-Type"
                      style="flex: 2.2; min-width: 200px;"
                    >
                      <el-option label="application/json" value="application/json"></el-option>
                      <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded"></el-option>
                    </el-select>
                    <el-input
                      v-else
                      v-model="header.value"
                      placeholder="Header值"
                      style="flex: 2.2; min-width: 180px;"
                    ></el-input>
                    <el-button @click="removeHeader(index)" icon="el-icon-delete" circle size="small"></el-button>
                  </div>
                  <el-button @click="addHeader" icon="el-icon-plus" size="small" style="margin-top: 10px;">添加Header</el-button>
                </div>
              </el-form-item>
              <el-form-item label="响应格式">
                <el-select v-model="metaFields.responseType" placeholder="请选择响应格式" clearable style="width: 30%;">
                  <el-option label="JSON" value="json"></el-option>
                  <el-option label="XML" value="xml"></el-option>
                </el-select>
              </el-form-item>
            </template>


            <!-- WEBSERVICE节点配置 -->
            <template v-if="form.nodeType === 'WEBSERVICE'">
              <el-form-item label="请求地址" prop="url" required>
                <el-input v-model="metaFields.url" placeholder="例如: http://127.0.0.1:8080/services/HelloService"></el-input>
              </el-form-item>
              <el-form-item label="请求头">
                <div class="headers-editor">
                  <div v-for="(header, index) in metaFields.headers" :key="index" class="header-row">
                    <el-select v-model="header.key" placeholder="选择或输入Header名称" filterable allow-create style="flex: 0.8; min-width: 150px;">
                      <el-option label="Content-Type" value="Content-Type"></el-option>
                      <el-option label="Authorization" value="Authorization"></el-option>
                      <el-option label="SOAPAction" value="SOAPAction"></el-option>
                    </el-select>
                    <el-select
                      v-if="header.key === 'Content-Type'"
                      v-model="header.value"
                      placeholder="选择Content-Type"
                      style="flex: 2.2; min-width: 200px;"
                    >
                      <el-option label="text/xml;charset=UTF-8" value="text/xml;charset=UTF-8"></el-option>
                      <el-option label="application/json" value="application/json"></el-option>
                    </el-select>
                    <el-input
                      v-else
                      v-model="header.value"
                      placeholder="Header值"
                      style="flex: 2.2; min-width: 200px;"
                    ></el-input>
                    <el-button @click="removeHeader(index)" icon="el-icon-delete" circle size="small"></el-button>
                  </div>
                  <el-button @click="addHeader" icon="el-icon-plus" size="small" style="margin-top: 10px;">添加Header</el-button>
                </div>
              </el-form-item>
              <el-form-item label="SOAP请求模板" prop="soupTemplate">
                <el-input
                  v-model="metaFields.soupTemplate"
                  type="textarea"
                  :rows="4"
                  placeholder="例如: <soapenv:Envelope>...${inputParam}...</soapenv:Envelope>"
                ></el-input>
              </el-form-item>
              <el-form-item label="业务数据类型">
                <el-select v-model="metaFields.dataType" placeholder="请选择业务数据类型(即inputParam中的数据类型)" style="width: 80%;" clearable>
                  <el-option label="JSON" value="json"></el-option>
                  <el-option label="XML" value="xml"></el-option>
                </el-select>
              </el-form-item>
              
              <!-- 响应数据解析分割线 -->
              <el-divider content-position="left">响应数据解析</el-divider>
              
              <el-form-item label="业务数据标签名">
                <el-input v-model="metaFields.elementName" placeholder="根据标签名定位业务数据" style="width: 40%;"></el-input>
              </el-form-item>
              <el-form-item label="响应格式">
                <el-select v-model="metaFields.responseType" placeholder="请选择响应格式" clearable style="width: 30%;">
                  <el-option label="JSON" value="json"></el-option>
                  <el-option label="XML" value="xml"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item label="使用CDATA">
                <el-switch v-model="metaFields.isCDATA"></el-switch>
                <span style="font-size: 12px; color: #999; margin-left: 10px;"></span>
              </el-form-item>
              <el-form-item label="是否转义">
                <el-switch v-model="metaFields.escape"></el-switch>
              </el-form-item>
              <el-form-item label="对象标签">
                <el-input v-model="metaFields.objectTagStr" placeholder="多个标签用逗号分隔，例如: PatientInfo,OrderInfo"></el-input>
              </el-form-item>

            </template>

            <!-- DATABASE节点配置 -->
            <template v-if="form.nodeType === 'DATABASE'">
              <el-form-item label="厂商类型" prop="driverClassName" required>
                <el-select v-model="metaFields.driverClassName" placeholder="请选择数据库厂商类型" clearable style="width: 40%;">
                  <el-option label="MySQL" value="com.mysql.cj.jdbc.Driver"></el-option>
                  <el-option label="SQL Server" value="com.microsoft.sqlserver.jdbc.SQLServerDriver"></el-option>
                  <el-option label="Oracle" value="oracle.jdbc.driver.OracleDriver"></el-option>
                  <el-option label="OpenGauss" value="org.opengauss.Driver"></el-option>
                  <el-option label="KingBase" value="com.kingbase8.Driver"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item label="操作类型" prop="operation" required>
                <el-select v-model="metaFields.operation" placeholder="请选择数据库操作类型" style="width: 30%;">
                  <el-option label="SELECT" value="SELECT"></el-option>
                  <el-option label="INSERT" value="INSERT"></el-option>
                  <el-option label="UPDATE" value="UPDATE"></el-option>
                  <el-option label="DELETE" value="DELETE"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item label="JDBC连接串" prop="dbUrl" required>
                <el-input v-model="metaFields.dbUrl" placeholder="例如: jdbc:sqlserver://10.20.4.245:1433;DatabaseName=HEMS_V5"></el-input>
              </el-form-item>
              <el-form-item label="用户名" prop="username" required>
                <el-input v-model="metaFields.username" placeholder="数据库用户名"></el-input>
              </el-form-item>
              <el-form-item label="密码" prop="password" required>
                <el-input v-model="metaFields.password" type="password" placeholder="数据库密码" show-password></el-input>
              </el-form-item>
              <el-form-item label="SQL模板" prop="sqlTemplate" required>
                <el-input
                  v-model="metaFields.sqlTemplate"
                  type="textarea"
                  :rows="4"
                  placeholder="例如: SELECT * FROM patient WHERE patient_code = '{patientCode}'"
                ></el-input>
              </el-form-item>
              <el-form-item label="响应格式">
                <el-select v-model="metaFields.responseType" placeholder="请选择响应格式" clearable style="width: 30%;">
                  <el-option label="JSON" value="json"></el-option>
                  <el-option label="XML" value="xml"></el-option>
                </el-select>
              </el-form-item>
            </template>

            <!-- 空状态提示 -->
            <div v-if="!form.nodeType" class="empty-meta-tip">
              <i class="el-icon-info"></i>
              <span>请先选择节点类型</span>
            </div>
          </el-form>
        </div>
      </div>

      <span slot="footer" class="dialog-footer">
        <el-button @click="dialog.visible.value = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </span>
    </el-dialog>

    <!-- JSON查看器对话框 -->
    <json-viewer-dialog
      :visible="jsonViewer.visible.value"
      :title="jsonViewer.title.value"
      :raw-content="jsonViewer.rawContent.value"
      :parsed-json="jsonViewer.parsedJson.value"
      :is-xml="jsonViewer.isXml.value"
      :is-plain-text="jsonViewer.isPlainText.value"
      @close="jsonViewer.close"
      @copy="jsonViewer.copy"
    />

    <!-- JSON文本导入对话框 -->
    <el-dialog
      title="从json文本导入节点"
      :visible.sync="jsonImportDialog.visible"
      width="700px"
    >
      <el-input
        v-model="jsonImportDialog.content"
        type="textarea"
        :rows="15"
        placeholder="请粘贴JSON文本..."
      >
      </el-input>
      <span slot="footer" class="dialog-footer">
        <el-button @click="jsonImportDialog.visible = false">取 消</el-button>
        <el-button type="primary" @click="handleJsonImport">导 入</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue';
import { Message, MessageBox } from 'element-ui';
import request from '@/utils/request';
import { getNodeList, createNode, updateNode, deleteNode } from '@/api/workflownode';
import { getAllWorkflowsUnpaged } from '@/api/workflow';
import { usePagination } from '@/composables/usePagination';
import { useTableLoading } from '@/composables/useTableLoading';
import { useDialog } from '@/composables/useDialog';
import { useJsonViewer } from '@/composables/useJsonViewer';
import { useNodeImportExport } from '@/composables/useNodeImportExport';
import JsonViewerDialog from '@/components/common/JsonViewerDialog.vue';
import JsonPreviewCell from '@/components/common/JsonPreviewCell.vue';

export default {
  name: 'WorkflowNode',
  components: {
    JsonViewerDialog,
    JsonPreviewCell
  },
  setup() {
    const nodes = ref([]);
    const workflowOptions = ref([]);
    const flowIdToNameMap = ref(new Map());
    const filterFlowId = ref(null);
    const formRef = ref(null);
    const metaFormRef = ref(null);

    const { loading, withLoading } = useTableLoading();
    const dialog = useDialog();
    const jsonViewer = useJsonViewer();

    const jsonImportDialog = reactive({
      visible: false,
      content: ''
    });

    const form = reactive({
      nodeId: '',
      nodeName: '',
      flowId: null,
      nodeType: 'HTTP',
      metaInfo: '{}',
      scheduleParamSourceType: '',
      scheduleExpr: '',
      paramFilterExpr: '',
      keywordField: '',
      splitField: '',
      remoteExtension: ''
    });

    // 元数据结构化字段
    const metaFields = reactive({
      // HTTP/WEBSERVICE 共用字段
      url: '',
      method: 'POST',
      headers: [],
      responseType: '',
      // WEBSERVICE 专用字段
      soupTemplate: '',
      dataType: '',
      escape: false,
      elementName: '',
      isCDATA: true,
      objectTagStr: '', // 前端显示为字符串，提交时转数组
      // DATABASE 专用字段
      driverClassName: '',
      operation: 'SELECT',
      dbUrl: '', // 前端使用dbUrl，后端存为url
      username: '',
      password: '',
      sqlTemplate: ''
    });

    const rules = {
      nodeName: [
        { required: true, message: '请输入节点名称', trigger: 'blur' }
      ],
      nodeType: [
        { required: true, message: '请选择节点类型', trigger: 'change' }
      ],
      scheduleExpr: [
        { validator: (rule, value, callback) => callback(), trigger: 'blur' }
      ]
    };

    const metaRules = reactive({
      url: [
        {
          required: false, // 默认非必填，动态根据节点类型判断
          validator: (rule, value, callback) => {
            if ((form.nodeType === 'HTTP' || form.nodeType === 'WEBSERVICE') && (!value || value.trim() === '')) {
              callback(new Error('请输入URL地址'));
            } else {
              callback();
            }
          },
          trigger: 'blur'
        }
      ],
      driverClassName: [
        {
          required: false,
          validator: (rule, value, callback) => {
            if (form.nodeType === 'DATABASE' && !value) {
              callback(new Error('请选择数据库厂商类型'));
            } else {
              callback();
            }
          },
          trigger: 'change'
        }
      ],
      operation: [
        {
          required: false,
          validator: (rule, value, callback) => {
            if (form.nodeType === 'DATABASE' && !value) {
              callback(new Error('请选择操作类型'));
            } else {
              callback();
            }
          },
          trigger: 'change'
        }
      ],
      dbUrl: [
        {
          required: false,
          validator: (rule, value, callback) => {
            if (form.nodeType === 'DATABASE' && (!value || value.trim() === '')) {
              callback(new Error('请输入JDBC连接串'));
            } else {
              callback();
            }
          },
          trigger: 'blur'
        }
      ],
      username: [
        {
          required: false,
          validator: (rule, value, callback) => {
            if (form.nodeType === 'DATABASE' && (!value || value.trim() === '')) {
              callback(new Error('请输入数据库用户名'));
            } else {
              callback();
            }
          },
          trigger: 'blur'
        }
      ],
      password: [
        {
          required: false,
          validator: (rule, value, callback) => {
            if (form.nodeType === 'DATABASE' && (!value || value.trim() === '')) {
              callback(new Error('请输入数据库密码'));
            } else {
              callback();
            }
          },
          trigger: 'blur'
        }
      ],
      sqlTemplate: [
        {
          required: false,
          validator: (rule, value, callback) => {
            if (form.nodeType === 'DATABASE' && (!value || value.trim() === '')) {
              callback(new Error('请输入SQL模板'));
            } else {
              callback();
            }
          },
          trigger: 'blur'
        }
      ]
    });

    const fetchData = async () => {
      await withLoading(async () => {
        const params = {
          pageNum: pagination.pageNum.value,
          pageSize: pagination.pageSize.value
        };
        if (filterFlowId.value) {
          params.flowId = filterFlowId.value;
        }

        const response = await getNodeList(params);
        if (response.status === 200 && response.data.code === '200') {
          const pageList = response.data.rsp;
          nodes.value = pageList.rows || [];
          pagination.total.value = pageList.total || 0;
        }
      });
    };

    const pagination = usePagination({ fetchData });
    const nodeImportExport = useNodeImportExport(fetchData);

    const handleImportCommand = (command) => {
      if (command === 'file') {
        // 调用原有的文件导入功能
        nodeImportExport.importGlobalNodeConfig();
      } else if (command === 'json') {
        // 打开JSON文本对话框
        jsonImportDialog.visible = true;
        jsonImportDialog.content = '';
      }
    };

    const handleJsonImport = async () => {
      try {
        if (!jsonImportDialog.content.trim()) {
          Message.warning('请输入JSON文本');
          return;
        }

        // 验证JSON格式
        let jsonData;
        try {
          jsonData = JSON.parse(jsonImportDialog.content);
        } catch (e) {
          Message.error('JSON格式错误：' + e.message);
          return;
        }

        // 将JSON数据转换为Blob，然后转换为File对象
        const blob = new Blob([JSON.stringify(jsonData)], { type: 'application/json' });
        const file = new File([blob], 'import.json', { type: 'application/json' });

        const formData = new FormData();
        formData.append('file', file);

        const response = await request.post('/v1/console/node/import-node', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });

        if (response.status === 200 && response.data.code === '200') {
          Message.success('节点导入成功');
          jsonImportDialog.visible = false;
          jsonImportDialog.content = '';
          await fetchData();
        } else {
          Message.error('节点导入失败: ' + (response.data.message || '未知错误'));
        }
      } catch (error) {
        console.error('导入失败:', error);
        Message.error('节点导入失败: ' + (error.message || '未知错误'));
      }
    };

    const fetchWorkflows = async () => {
      try {
        const response = await getAllWorkflowsUnpaged();
        if (response.status === 200 && response.data.code === '200') {
          const workflows = Array.isArray(response.data.rsp) ? response.data.rsp : [];
          workflowOptions.value = workflows;

          const map = new Map();
          workflows.forEach(flow => {
            map.set(flow.flowId, flow.flowName);
          });
          flowIdToNameMap.value = map;
        }
      } catch (error) {
        console.error('获取工作流列表失败:', error);
      }
    };

    const getFlowName = (flowId) => {
      return flowIdToNameMap.value.get(flowId) || '未分配';
    };

    const handleFilterChange = () => {
      pagination.pageNum.value = 1;
      fetchData();
    };

    const resetFilters = () => {
      filterFlowId.value = null;
      pagination.pageNum.value = 1;
      fetchData();
    };

    const handleAdd = () => {
      resetForm();
      dialog.open('新增节点');
    };

    const handleEdit = (row) => {
      Object.assign(form, JSON.parse(JSON.stringify(row)));
      // 解析metaInfo到结构化字段
      parseMetaInfoToFields(row.metaInfo);
      dialog.open('编辑节点');
    };

    const handleCopy = (row) => {
      const copied = JSON.parse(JSON.stringify(row));
      copied.nodeId = '';
      copied.nodeName = `${copied.nodeName}（副本）`;
      copied.flowId = null;
      copied.scheduleExpr = '';
      copied.paramFilterExpr = '';
      copied.keywordField = '';
      copied.splitField = '';
      copied.remoteExtension = '';

      Object.assign(form, copied);
      // 解析metaInfo到结构化字段
      parseMetaInfoToFields(copied.metaInfo);
      dialog.open('复制节点');
    };

    const handleDelete = async (row) => {
      try {
        await MessageBox.confirm(`确定要删除节点 "${row.nodeName}" 吗?`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        });

        await deleteNode(row.nodeId);
        await fetchData();
      } catch (error) {
        if (error !== 'cancel') {
          console.error('删除失败:', error);
        }
      }
    };

    const resetForm = () => {
      formRef.value?.resetFields();
      Object.assign(form, {
        nodeId: '',
        nodeName: '',
        flowId: null,
        nodeType: 'HTTP',
        metaInfo: '{}',
        scheduleParamSourceType: '',
        scheduleExpr: '',
        paramFilterExpr: '',
        keywordField: '',
        splitField: '',
        remoteExtension: ''
      });
      // 重置结构化字段
      resetMetaFields();
    };

    const submitForm = async () => {
      // 校验基础信息表单
      const valid = await formRef.value.validate().catch(() => false);
      if (!valid) return;

      // 校验元信息表单（如果存在）
      if (metaFormRef.value) {
        const metaValid = await metaFormRef.value.validate().catch(() => false);
        if (!metaValid) return;
      }

      // 将结构化字段转换为metaInfo JSON
      form.metaInfo = buildMetaInfoFromFields();

      const submitData = { ...form };
      Object.keys(submitData).forEach(key => {
        if (submitData[key] === '') {
          submitData[key] = null;
        }
      });

      try {
        let response;
        if (form.nodeId) {
          response = await updateNode(submitData);
        } else {
          response = await createNode(submitData);
        }

        if (response.status === 200 && response.data.code === '200') {
          dialog.close();
          await fetchData();
        }
      } catch (error) {
        console.error('保存失败:', error);
      }
    };

    // ========== 元数据结构化字段管理 ==========

    // 节点类型变化时重置元数据字段
    const handleNodeTypeChange = () => {
      resetMetaFields();
      // 清除元信息表单的校验状态
      if (metaFormRef.value) {
        metaFormRef.value.clearValidate();
      }
    };

    // 重置元数据字段
    const resetMetaFields = () => {
      Object.assign(metaFields, {
        url: '',
        method: 'POST',
        headers: [],
        responseType: '',
        // WEBSERVICE 专用
        soupTemplate: '',
        dataType: '',
        escape: false,
        elementName: '',
        isCDATA: true,
        objectTagStr: '',
        // DATABASE 专用
        driverClassName: '',
        operation: 'SELECT',
        dbUrl: '',
        username: '',
        password: '',
        sqlTemplate: ''
      });
    };

    // 解析metaInfo字符串到结构化字段
    const parseMetaInfoToFields = (metaInfoStr) => {
      try {
        if (!metaInfoStr || metaInfoStr === '{}') {
          resetMetaFields();
          return;
        }

        const metaInfo = JSON.parse(metaInfoStr);

        // 解析公共字段
        metaFields.url = metaInfo.url || '';
        metaFields.responseType = metaInfo.responseType || '';

        // HTTP 字段
        if (form.nodeType === 'HTTP') {
          metaFields.method = metaInfo.method || 'POST';
          // 解析headers对象为数组
          const headersObj = metaInfo.headers || {};
          metaFields.headers = Object.entries(headersObj).map(([key, value]) => ({
            key,
            value
          }));
        }

        // WEBSERVICE 字段
        if (form.nodeType === 'WEBSERVICE') {
          metaFields.soupTemplate = metaInfo.soupTemplate || '';
          metaFields.dataType = metaInfo.dataType || '';
          metaFields.escape = metaInfo.escape || false;
          metaFields.elementName = metaInfo.elementName || '';
          metaFields.isCDATA = metaInfo.isCDATA !== undefined ? metaInfo.isCDATA : true;
          // objectTag数组转为字符串
          metaFields.objectTagStr = (metaInfo.objectTag || []).join(',');
          // 解析headers
          const headersObj = metaInfo.headers || {};
          metaFields.headers = Object.entries(headersObj).map(([key, value]) => ({
            key,
            value
          }));
        }

        // DATABASE 字段
        if (form.nodeType === 'DATABASE') {
          metaFields.driverClassName = metaInfo.driverClassName || '';
          metaFields.operation = metaInfo.operation || 'SELECT';
          metaFields.dbUrl = metaInfo.url || ''; // 后端用url字段
          metaFields.username = metaInfo.username || '';
          metaFields.password = metaInfo.password || '';
          metaFields.sqlTemplate = metaInfo.sqlTemplate || '';
        }
      } catch (error) {
        console.error('解析metaInfo失败:', error);
        Message.error('解析节点元数据失败');
        resetMetaFields();
      }
    };

    // 构建metaInfo JSON字符串
    const buildMetaInfoFromFields = () => {
      const metaInfo = {};

      if (form.nodeType === 'HTTP') {
        metaInfo.url = metaFields.url;
        metaInfo.method = metaFields.method;
        if (metaFields.responseType) metaInfo.responseType = metaFields.responseType;
        // 将headers数组转换为对象
        metaInfo.headers = {};
        metaFields.headers.forEach(header => {
          if (header.key && header.value) {
            metaInfo.headers[header.key] = header.value;
          }
        });
      } else if (form.nodeType === 'WEBSERVICE') {
        metaInfo.url = metaFields.url;
        if (metaFields.soupTemplate) metaInfo.soupTemplate = metaFields.soupTemplate;
        if (metaFields.dataType) metaInfo.dataType = metaFields.dataType;
        if (metaFields.responseType) metaInfo.responseType = metaFields.responseType;
        if (metaFields.escape !== undefined) metaInfo.escape = metaFields.escape;
        if (metaFields.elementName) metaInfo.elementName = metaFields.elementName;
        metaInfo.isCDATA = metaFields.isCDATA;
        // objectTagStr字符串转数组
        if (metaFields.objectTagStr) {
          metaInfo.objectTag = metaFields.objectTagStr.split(',').map(s => s.trim()).filter(s => s);
        }
        // 将headers数组转换为对象
        if (metaFields.headers.length > 0) {
          metaInfo.headers = {};
          metaFields.headers.forEach(header => {
            if (header.key && header.value) {
              metaInfo.headers[header.key] = header.value;
            }
          });
        }
      } else if (form.nodeType === 'DATABASE') {
        metaInfo.driverClassName = metaFields.driverClassName;
        metaInfo.operation = metaFields.operation;
        metaInfo.url = metaFields.dbUrl; // 前端dbUrl，后端存为url
        metaInfo.username = metaFields.username;
        metaInfo.password = metaFields.password;
        metaInfo.sqlTemplate = metaFields.sqlTemplate;
      }

      return JSON.stringify(metaInfo);
    };

    // 添加Header
    const addHeader = () => {
      metaFields.headers.push({ key: '', value: '' });
    };

    // 删除Header
    const removeHeader = (index) => {
      metaFields.headers.splice(index, 1);
    };

    onMounted(() => {
      fetchData();
      fetchWorkflows();
    });

    return {
      nodes,
      loading,
      pagination,
      dialog,
      jsonViewer,
      jsonImportDialog,
      nodeImportExport,
      filterFlowId,
      workflowOptions,
      form,
      rules,
      formRef,
      metaFormRef,
      metaFields,
      metaRules,
      getFlowName,
      handleFilterChange,
      resetFilters,
      handleAdd,
      handleEdit,
      handleCopy,
      handleDelete,
      handleImportCommand,
      handleJsonImport,
      resetForm,
      submitForm,
      handleNodeTypeChange,
      addHeader,
      removeHeader
    };
  }
};
</script>

<style scoped>
.workflow-node-list {
  padding: 20px;
}

.workflow-node-list h2 {
  margin-bottom: 20px;
  color: #333;
}

.el-table {
  margin-top: 10px;
}

.el-table .cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

::v-deep .el-tag--info {
  background-color: #e8f4fd;
  color: #1890ff;
}

/* 左右分栏布局 */
.node-config-container {
  display: flex;
  gap: 24px;
  max-height: 600px;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 标题样式 */
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 2px solid #409eff;
}

/* 左侧基础信息区域 */
.form-section {
  flex: 0 0 300px;
  min-width: 300px;
}

/* 右侧元信息配置区域 */
.meta-config-section {
  flex: 1;
  min-width: 0;
  border-left: 2px solid #e4e7ed;
  padding-left: 20px;
}

/* 空状态提示 */
.empty-meta-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 20px;
  color: #909399;
  font-size: 14px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.empty-meta-tip i {
  font-size: 18px;
}

/* 元数据结构化表单样式 */
.headers-editor {
  background-color: #f9fafc;
  padding: 15px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  max-width: 100%;
  overflow: hidden;
}

.header-row {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
  gap: 10px;
  max-width: 100%;
}

.header-row:last-child {
  margin-bottom: 0;
}
</style>
