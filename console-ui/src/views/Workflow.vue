<template>
  <div class="workflow-list">
    <!-- 操作按钮区 -->
    <div style="margin-bottom: 10px; text-align: right;">
      <el-dropdown trigger="click" @command="handleImportCommand" style="margin-right: 10px;">
        <el-button type="primary">
          导入 <i class="el-icon-arrow-down el-icon--right"></i>
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
      <el-button type="success" @click="handleAdd">新增</el-button>
      
      <!-- 隐藏的文件上传 -->
      <el-upload
        ref="importUploadRef"
        :show-file-list="false"
        :before-upload="handleImportUpload"
        accept=".json"
        style="display: none;"
      >
      </el-upload>
    </div>

    <!-- 工作流表格 -->
    <el-table 
      :data="workflows" 
      style="width: 100%" 
      v-loading="loading" 
      fit="true"
    >
      <el-table-column prop="flowName" label="工作流名称" min-width="200"></el-table-column>
      <el-table-column prop="interfaceUri" label="接口URI" min-width="250"></el-table-column>
      <el-table-column prop="contentType" label="入参类型" min-width="150"></el-table-column>
      <el-table-column label="首节点" min-width="200">
        <template #default="scope">
          <el-tag 
            v-for="nodeName in scope.row.firstFlowNodeNames" 
            :key="nodeName" 
            size="small" 
            style="margin-right: 5px;"
          >
            {{ nodeName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="350">
        <template #default="scope">
          <el-button size="small" type="warning" @click="handleExport(scope.row)">导出</el-button>
          <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button size="small" @click="handleCopy(scope.row)">复制</el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
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
      width="600px"
      @close="resetForm"
    >
      <el-form
        :model="form"
        :rules="rules"
        ref="formRef"
        label-width="120px"
      >
        <el-form-item label="工作流名称" prop="flowName">
          <el-input v-model="form.flowName" placeholder="请输入工作流名称"></el-input>
        </el-form-item>

        <el-form-item label="接口URI" prop="interfaceUri">
          <el-input v-model="form.interfaceUri" placeholder="请输入接口URI"></el-input>
        </el-form-item>

        <el-form-item label="入参类型" prop="contentType">
          <el-select v-model="form.contentType" placeholder="请选择入参类型">
            <el-option label="JSON" value="JSON"></el-option>
            <el-option label="XML" value="XML"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="入参元信息" prop="contentMetaInfo">
          <el-input 
            v-model="form.contentMetaInfo" 
            type="textarea"
            :rows="3"
            placeholder="请输入入参元信息">
          </el-input>
        </el-form-item>

        <el-form-item label="首节点" prop="firstFlowNodes">
          <el-select
            v-model="form.firstFlowNodes"
            multiple
            placeholder="请选择首节点"
            style="width: 100%"
          >
            <el-option
              v-for="node in nodeOptions"
              :key="node.nodeId"
              :label="node.nodeName"
              :value="node.nodeId"
            ></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <span slot="footer" class="dialog-footer">
        <el-button @click="dialog.visible.value = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </span>
    </el-dialog>

    <!-- JSON文本导入对话框 -->
    <el-dialog
      title="从json文本导入工作流"
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
import { ref, reactive, onMounted, getCurrentInstance } from 'vue';
import { getWorkflows, getAllNodes, createWorkflow, updateWorkflow, deleteWorkflow, exportWorkflow, importWorkflow } from '@/api/workflow';
import { usePagination } from '@/composables/usePagination';
import { useTableLoading } from '@/composables/useTableLoading';
import { useDialog } from '@/composables/useDialog';

export default {
  name: 'WorkFlowList',
  setup() {
    const { proxy } = getCurrentInstance();
    const workflows = ref([]);
    const nodeOptions = ref([]);
    const nodeMap = ref(new Map());
    const currentEditingFlowId = ref(null);
    const formRef = ref(null);
    const importUploadRef = ref(null);

    const { loading, withLoading } = useTableLoading();
    const dialog = useDialog();

    const jsonImportDialog = reactive({
      visible: false,
      content: ''
    });

    const form = reactive({
      flowName: '',
      interfaceUri: '',
      contentType: 'JSON',
      contentMetaInfo: '',
      firstFlowNodes: [],
      status: 1
    });

    const rules = {
      flowName: [
        { required: true, message: '请输入工作流名称', trigger: 'blur' }
      ],
      interfaceUri: [
        { required: true, message: '请输入接口URI', trigger: 'blur' }
      ],
      contentType: [
        { required: true, message: '请选择入参类型', trigger: 'change' },
        { 
          type: 'enum', 
          enum: ['JSON', 'XML'], 
          message: '入参类型只能是 JSON 或 XML', 
          trigger: 'change' 
        }
      ]
    };

    const fetchData = async () => {
      await withLoading(async () => {
        const params = {
          pageNum: pagination.pageNum.value,
          pageSize: pagination.pageSize.value
        };
        const response = await getWorkflows(params);
        
        if (response.status === 200 && response.data.code === '200') {
          const pageList = response.data.rsp;
          workflows.value = (pageList.rows || []).map(workflow => ({
            ...workflow,
            firstFlowNodeNames: workflow.firstFlowNodes.map(id => 
              nodeMap.value.get(id)
            )
          }));
          pagination.total.value = pageList.total || 0;
        }
      });
    };

    const pagination = usePagination({ fetchData });

    const fetchNodeList = async () => {
      try {
        const response = await getAllNodes();
        if (response.status === 200 && response.data.code === '200') {
          nodeOptions.value = response.data.rsp || [];
          const map = new Map();
          nodeOptions.value.forEach(node => {
            map.set(node.nodeId, node.nodeName);
          });
          nodeMap.value = map;
          await fetchData();
        }
      } catch (error) {
        console.error('获取节点列表失败:', error);
      }
    };

    const handleAdd = () => {
      resetForm();
      dialog.open('新增工作流');
    };

    const handleEdit = (row) => {
      Object.assign(form, {
        flowName: row.flowName,
        interfaceUri: row.interfaceUri,
        contentType: row.contentType,
        contentMetaInfo: row.contentMetaInfo,
        firstFlowNodes: row.firstFlowNodes,
        status: row.status
      });
      currentEditingFlowId.value = row.flowId;
      dialog.open('编辑工作流');
    };

    const handleCopy = (row) => {
      Object.assign(form, {
        flowName: row.flowName + '（副本）',
        interfaceUri: row.interfaceUri,
        contentType: row.contentType,
        contentMetaInfo: row.contentMetaInfo,
        firstFlowNodes: row.firstFlowNodes,
        status: row.status
      });
      currentEditingFlowId.value = null;
      dialog.open('复制工作流');
    };

    const handleDelete = async (row) => {
      try {
        await proxy.$confirm(
          `确定要删除工作流 "${row.flowName}" 吗？删除后无法恢复！`,
          '删除确认',
          {
            confirmButtonText: '确定删除',
            cancelButtonText: '取消',
            type: 'warning'
          }
        );
        
        await deleteWorkflow(row.flowId);
        proxy.$message.success('删除成功');
        await fetchData();
      } catch (error) {
        if (error !== 'cancel') {
          console.error('删除失败:', error);
          proxy.$message.error('删除失败：' + (error.message || '未知错误'));
        }
      }
    };

    const handleExport = async (row) => {
      try {
        const response = await exportWorkflow(row.flowId);
        if (response.status === 200 && response.data.code === '200') {
          const exportData = response.data.rsp;
          const jsonStr = JSON.stringify(exportData, null, 2);
          const blob = new Blob([jsonStr], { type: 'application/json' });
          const url = window.URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = `${row.flowName || 'workflow'}.json`;
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);
          window.URL.revokeObjectURL(url);
        }
      } catch (error) {
        console.error('导出失败:', error);
      }
    };

    const handleImportCommand = (command) => {
      if (command === 'file') {
        // 触发文件选择
        importUploadRef.value.$children[0].$refs.input.click();
      } else if (command === 'json') {
        // 打开JSON文本对话框
        jsonImportDialog.visible = true;
        jsonImportDialog.content = '';
      }
    };

    const handleImportUpload = async (file) => {
      try {
        const response = await importWorkflow(file);
        if (response.status === 200 && response.data.code === '200') {
          await fetchNodeList();
        }
      } catch (error) {
        console.error('导入失败:', error);
      }
      return false;
    };

    const handleJsonImport = async () => {
      try {
        if (!jsonImportDialog.content.trim()) {
          proxy.$message.warning('请输入JSON文本');
          return;
        }

        // 验证JSON格式
        let jsonData;
        try {
          jsonData = JSON.parse(jsonImportDialog.content);
        } catch (e) {
          proxy.$message.error('JSON格式错误：' + e.message);
          return;
        }

        // 将JSON数据转换为Blob，然后转换为File对象
        const blob = new Blob([JSON.stringify(jsonData)], { type: 'application/json' });
        const file = new File([blob], 'import.json', { type: 'application/json' });

        const response = await importWorkflow(file);
        if (response.status === 200 && response.data.code === '200') {
          proxy.$message.success('导入成功');
          jsonImportDialog.visible = false;
          jsonImportDialog.content = '';
          await fetchNodeList();
        }
      } catch (error) {
        console.error('导入失败:', error);
        proxy.$message.error('导入失败：' + (error.message || '未知错误'));
      }
    };

    const resetForm = () => {
      formRef.value?.resetFields();
      Object.assign(form, {
        flowName: '',
        interfaceUri: '',
        contentType: 'JSON',
        contentMetaInfo: '',
        firstFlowNodes: [],
        status: 1
      });
      currentEditingFlowId.value = null;
    };

    const submitForm = async () => {
      const valid = await formRef.value.validate().catch(() => false);
      if (!valid) return;

      try {
        let response;
        if (currentEditingFlowId.value) {
          const formData = { ...form, flowId: currentEditingFlowId.value };
          response = await updateWorkflow(formData);
        } else {
          response = await createWorkflow(form);
        }

        if (response.status === 200 && response.data.code === '200') {
          dialog.close();
          await fetchData();
        }
      } catch (error) {
        console.error('保存失败:', error);
      }
    };

    onMounted(() => {
      fetchNodeList();
    });

    return {
      workflows,
      loading,
      pagination,
      dialog,
      jsonImportDialog,
      form,
      rules,
      formRef,
      nodeOptions,
      handleAdd,
      handleEdit,
      handleCopy,
      handleDelete,
      handleExport,
      handleImportCommand,
      handleImportUpload,
      handleJsonImport,
      importUploadRef,
      resetForm,
      submitForm
    };
  }
};
</script>

<style scoped>
.workflow-list {
  padding: 20px;
  width: 100%;
}

.workflow-list h2 {
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
</style>