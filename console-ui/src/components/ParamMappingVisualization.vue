<template>
  <el-dialog
    :visible.sync="dialogVisible"
    width="95%"
    top="3vh"
    :close-on-click-modal="false"
    :show-close="true"
    custom-class="mapping-visualization-dialog"
  >
    <!-- 顶部控制栏 -->
    <div class="control-bar">
      <el-radio-group v-model="activeProcessType" size="small" @change="handleProcessTypeChange">
        <el-radio-button label="PRE_PROCESS">前置处理</el-radio-button>
        <el-radio-button label="POST_PROCESS">后置处理</el-radio-button>
      </el-radio-group>

      <div class="info-text">
        <span v-if="activeProcessType === 'PRE_PROCESS'">
          <i class="el-icon-info"></i> 前置处理: 上游入参 → 下游入参
        </span>
        <span v-else>
          <i class="el-icon-info"></i> 后置处理: 下游响应 → 上游响应
        </span>
      </div>

      <div class="view-controls">
        <el-button size="small" @click="handleAddRoot" icon="el-icon-plus">新增配置</el-button>
        <el-button size="small" @click="expandAll" icon="el-icon-sort">展开全部</el-button>
        <el-button size="small" @click="collapseAll" icon="el-icon-rank">折叠全部</el-button>
        <el-button size="small" @click="zoomOut" icon="el-icon-zoom-out" :disabled="zoomLevel <= 0.5">缩小</el-button>
        <el-button size="small" @click="zoomIn" icon="el-icon-zoom-in" :disabled="zoomLevel >= 1.5">放大</el-button>
        <span class="zoom-level">{{ Math.round(zoomLevel * 100) }}%</span>
      </div>
    </div>

    <!-- 可视化容器 -->
    <div class="visualization-container" v-loading="loading">
      <el-empty v-if="treeMappings.length === 0" description="暂无参数映射配置">
        <el-button type="primary" size="small" @click="handleAddRoot">新增配置</el-button>
      </el-empty>

      <!-- 树形结构展示 -->
      <div class="mapping-tree-wrapper" :style="{ transform: `scale(${zoomLevel})`, transformOrigin: 'left top' }">
        <mapping-node
          v-for="(node, index) in treeMappings"
          :key="node.configId || index"
          :node="node"
          :level="0"
          :expanded-nodes="expandedNodes"
          :process-type="activeProcessType"
          @toggle-expand="toggleExpand"
          @edit-node="handleEditNode"
          @copy-node="handleCopyNode"
          @delete-node="handleDeleteNode"
          @add-child="handleAddChild"
          :get-type-icon="getTypeIcon"
          :get-type-color="getTypeColor"
          :get-type-tag-type="getTypeTagType"
          :get-mapping-type-label="getMappingTypeLabel"
          :get-mapping-type-tag-type="getMappingTypeTagType"
        />
      </div>
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button @click="dialogVisible = false">关 闭</el-button>
    </span>

    <!-- 编辑/新增对话框 -->
    <el-dialog
      :title="editDialogTitle"
      :visible.sync="editDialogVisible"
      width="600px"
      append-to-body
      @close="resetEditForm"
    >
      <el-form
        :model="editForm"
        :rules="editRules"
        ref="editFormRef"
        label-width="120px"
      >
        <el-form-item label="处理类型" prop="processType">
          <el-select v-model="editForm.processType" placeholder="请选择处理类型" disabled>
            <el-option label="前置处理" value="PRE_PROCESS"></el-option>
            <el-option label="后置处理" value="POST_PROCESS"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="映射来源" prop="mappingSource" v-if="editForm.processType === 'POST_PROCESS'">
          <el-select v-model="editForm.mappingSource" placeholder="请选择映射来源" clearable>
            <el-option label="上游响应" value="RESPONSE"></el-option>
            <el-option label="原始入参" value="INPUT"></el-option>
          </el-select>
          <el-tooltip
            content="映射来源用于指定目标参数从哪里获取：上游响应表示来自接口响应结果；原始入参表示来自原始请求参数"
            placement="top"
            effect="light"
          >
            <i class="el-icon-question" style="color: orange; margin-left: 10px; cursor: pointer;"></i>
          </el-tooltip>
        </el-form-item>

        <el-form-item
          label="源参数名"
          prop="sourceParamKey"
          v-if="editForm.sourceParamType !== 'NONE'"
        >
          <el-input v-model="editForm.sourceParamKey" placeholder="请输入源参数名"></el-input>
        </el-form-item>

        <el-form-item label="源参数类型" prop="sourceParamType">
          <el-select v-model="editForm.sourceParamType" placeholder="请选择源参数类型">
            <el-option label="字符串" value="STRING"></el-option>
            <el-option label="数字INT" value="INTEGER"></el-option>
            <el-option label="数字LONG" value="LONG"></el-option>
            <el-option label="数字DOUBLE" value="DOUBLE"></el-option>
            <el-option label="布尔" value="BOOLEAN"></el-option>
            <el-option label="对象" value="OBJECT"></el-option>
            <el-option label="数组" value="ARRAY"></el-option>
            <el-option label="纯数组" value="PURE_ARRAY"></el-option>
            <el-option label="无" value="NONE"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="目标参数名" prop="targetParamKey">
          <el-input v-model="editForm.targetParamKey" placeholder="请输入目标参数名"></el-input>
        </el-form-item>

        <el-form-item label="目标参数类型" prop="targetParamType">
          <el-select v-model="editForm.targetParamType" placeholder="请选择目标参数类型">
            <el-option label="字符串" value="STRING"></el-option>
            <el-option label="数字INT" value="INTEGER"></el-option>
            <el-option label="数字LONG" value="LONG"></el-option>
            <el-option label="数字DOUBLE" value="DOUBLE"></el-option>
            <el-option label="布尔" value="BOOLEAN"></el-option>
            <el-option label="对象" value="OBJECT"></el-option>
            <el-option label="数组" value="ARRAY"></el-option>
            <el-option label="纯数组" value="PURE_ARRAY"></el-option>
            <el-option label="无" value="NONE"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item
          label="保留单元素Key"
          prop="keepSingleElementKey"
          v-if="editForm.targetParamType === 'OBJECT'"
        >
          <el-switch
            v-model="editForm.keepSingleElementKey"
            active-text=""
            inactive-text="">
          </el-switch>
          <el-tooltip
            content="当对象中只有一个元素时是否保留key"
            placement="top"
            effect="light"
          >
            <i class="el-icon-question" style="color: orange; margin-left: 14px; cursor: pointer;"></i>
          </el-tooltip>
        </el-form-item>

        <el-form-item label="父参数" prop="parentId">
          <el-select v-model="editForm.parentId" placeholder="请选择父参数" clearable>
            <el-option
              v-for="config in parentOptions"
              :key="config.configId"
              :label="`${config.targetParamKey} (${config.paramDesc || '无描述'})`"
              :value="config.configId"
            ></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="映射类型" prop="mappingType">
          <el-select v-model="editForm.mappingType" placeholder="请选择映射类型">
            <el-option label="固定值" value="CONSTANT"></el-option>
            <el-option label="值映射" value="NAME"></el-option>
            <el-option label="表达式" value="EXPRESSION"></el-option>
            <el-option label="Bean表达式" value="BEAN_EXPRESSION"></el-option>
            <el-option label="单值映射" value="SINGLE_MAP"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="映射规则" prop="mappingRule">
          <el-input
            v-model="editForm.mappingRule"
            type="textarea"
            :rows="4"
            placeholder="请输入映射规则"
          ></el-input>
        </el-form-item>

        <el-form-item label="参数描述" prop="paramDesc">
          <el-input
            v-model="editForm.paramDesc"
            type="textarea"
            :rows="2"
            placeholder="请输入参数描述，用于说明目标参数的业务用途"
          ></el-input>
        </el-form-item>
      </el-form>

      <span slot="footer" class="dialog-footer">
        <el-button @click="editDialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitEditForm" :loading="submitLoading">确 定</el-button>
      </span>
    </el-dialog>
  </el-dialog>
</template>

<script>
import { ref, reactive, computed, watch } from 'vue';
import { Message, MessageBox } from 'element-ui';
import MappingNode from './MappingNode.vue';
import { getNodeParamsUnpaged, createNodeParam, updateNodeParam, deleteNodeParam } from '@/api/nodeParam';

export default {
  name: 'ParamMappingVisualization',
  components: {
    MappingNode
  },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    nodeId: {
      type: [String, Number],
      required: true
    },
    nodeName: {
      type: String,
      default: ''
    },
    configList: {
      type: Array,
      default: () => []
    }
  },
  setup(props, { emit }) {
    const dialogVisible = ref(false);
    const activeProcessType = ref('PRE_PROCESS');
    const loading = ref(false);
    const expandedNodes = ref(new Set());
    const zoomLevel = ref(1);

    // 编辑对话框相关
    const editDialogVisible = ref(false);
    const editDialogTitle = ref('新增参数配置');
    const editFormRef = ref(null);
    const submitLoading = ref(false);
    const parentOptions = ref([]);

    // 本地配置列表（用于刷新后更新）
    const localConfigList = ref([]);

    const editForm = reactive({
      configId: '',
      nodeId: '',
      parentId: '',
      sourceParamKey: '',
      sourceParamType: '',
      targetParamKey: '',
      targetParamType: '',
      keepSingleElementKey: false,
      processType: '',
      mappingType: '',
      mappingRule: '',
      paramDesc: '',
      mappingSource: ''
    });

    const editRules = {
      sourceParamType: [
        { required: true, message: '请选择源参数类型', trigger: 'change' }
      ],
      targetParamKey: [
        { required: true, message: '请输入目标参数名', trigger: 'blur' }
      ],
      targetParamType: [
        { required: true, message: '请选择目标参数类型', trigger: 'change' }
      ],
      processType: [
        { required: true, message: '请选择处理类型', trigger: 'change' }
      ],
      mappingType: [
        { required: true, message: '请选择映射类型', trigger: 'change' }
      ]
    };

    // 监听visible变化
    watch(() => props.visible, (val) => {
      dialogVisible.value = val;
      if (val) {
        activeProcessType.value = 'PRE_PROCESS';
        localConfigList.value = [...props.configList];
        buildTreeMappings();
        // 默认展开所有节点
        expandAll();
      }
    });

    watch(dialogVisible, (val) => {
      if (!val) {
        emit('update:visible', false);
      }
    });

    // 监听源参数类型变化
    watch(() => editForm.sourceParamType, (newVal) => {
      if (newVal === 'NONE') {
        editForm.sourceParamKey = '';
      }
    });

    // 过滤当前处理类型的配置
    const currentConfigs = computed(() => {
      return localConfigList.value.filter(config =>
        config.processType === activeProcessType.value &&
        config.nodeId === props.nodeId
      );
    });

    // 树形结构的映射列表
    const treeMappings = ref([]);

    const buildTreeMappings = () => {
      const configs = currentConfigs.value;
      const configMap = new Map();

      // 构建配置映射
      configs.forEach(config => {
        configMap.set(config.configId, {
          ...config,
          children: []
        });
      });

      // 构建树形结构
      const rootNodes = [];
      configs.forEach(config => {
        const node = configMap.get(config.configId);
        if (config.parentId && configMap.has(config.parentId)) {
          // 有父节点，添加到父节点的children
          const parent = configMap.get(config.parentId);
          parent.children.push(node);
        } else {
          // 根节点
          rootNodes.push(node);
        }
      });

      // 对每个节点的children按sort排序
      const sortChildren = (node) => {
        if (node.children && node.children.length > 0) {
          node.children.sort((a, b) => (a.sort || 0) - (b.sort || 0));
          node.children.forEach(child => sortChildren(child));
        }
      };

      rootNodes.forEach(node => sortChildren(node));
      rootNodes.sort((a, b) => (a.sort || 0) - (b.sort || 0));

      treeMappings.value = rootNodes;
    };

    const handleProcessTypeChange = () => {
      buildTreeMappings();
      expandAll();
    };

    const toggleExpand = (configId) => {
      const newSet = new Set(expandedNodes.value);
      if (newSet.has(configId)) {
        newSet.delete(configId);
      } else {
        newSet.add(configId);
      }
      // 强制触发响应式更新
      expandedNodes.value = new Set(newSet);
    };

    const expandAll = () => {
      const allIds = new Set();
      const collectIds = (nodes) => {
        nodes.forEach(node => {
          if (node.children && node.children.length > 0) {
            allIds.add(node.configId);
            collectIds(node.children);
          }
        });
      };
      collectIds(treeMappings.value);
      expandedNodes.value = new Set(allIds);
    };

    const collapseAll = () => {
      expandedNodes.value = new Set();
    };

    const zoomIn = () => {
      if (zoomLevel.value < 1.5) {
        zoomLevel.value = Math.min(zoomLevel.value + 0.1, 1.5);
      }
    };

    const zoomOut = () => {
      if (zoomLevel.value > 0.5) {
        zoomLevel.value = Math.max(zoomLevel.value - 0.1, 0.5);
      }
    };

    // 刷新数据
    const refreshData = async () => {
      try {
        loading.value = true;
        const response = await getNodeParamsUnpaged({ nodeId: props.nodeId });
        if (response.status === 200 && response.data.code === '200') {
          localConfigList.value = response.data.rsp || [];
          buildTreeMappings();
          // 通知父组件数据已更新
          emit('data-updated', localConfigList.value);
        }
      } catch (error) {
        console.error('刷新数据失败:', error);
      } finally {
        loading.value = false;
      }
    };

    // 更新父参数选项
    const updateParentOptions = () => {
      parentOptions.value = localConfigList.value.filter(config =>
        config.processType === editForm.processType &&
        config.nodeId === props.nodeId &&
        (!editForm.configId || config.configId !== editForm.configId) &&
        ['OBJECT', 'ARRAY', 'PURE_ARRAY'].includes(config.targetParamType)
      );
    };

    // 重置编辑表单
    const resetEditForm = () => {
      if (editFormRef.value) {
        editFormRef.value.resetFields();
      }
      Object.assign(editForm, {
        configId: '',
        nodeId: props.nodeId,
        parentId: '',
        sourceParamKey: '',
        sourceParamType: '',
        targetParamKey: '',
        targetParamType: '',
        keepSingleElementKey: false,
        processType: activeProcessType.value,
        mappingType: '',
        mappingRule: '',
        paramDesc: '',
        mappingSource: ''
      });
    };

    // 新增根节点配置
    const handleAddRoot = () => {
      resetEditForm();
      editForm.nodeId = props.nodeId;
      editForm.processType = activeProcessType.value;
      editDialogTitle.value = '新增参数配置';
      updateParentOptions();
      editDialogVisible.value = true;
    };

    // 添加子节点
    const handleAddChild = (parentNode) => {
      resetEditForm();
      editForm.nodeId = props.nodeId;
      editForm.processType = activeProcessType.value;
      editForm.parentId = parentNode.configId;
      editDialogTitle.value = `新增子参数 - 父节点: ${parentNode.targetParamKey}`;
      updateParentOptions();
      editDialogVisible.value = true;
    };

    // 编辑节点
    const handleEditNode = (node) => {
      Object.assign(editForm, {
        configId: node.configId,
        nodeId: node.nodeId,
        parentId: node.parentId || '',
        sourceParamKey: node.sourceParamKey || '',
        sourceParamType: node.sourceParamType || '',
        targetParamKey: node.targetParamKey || '',
        targetParamType: node.targetParamType || '',
        keepSingleElementKey: node.keepSingleElementKey || false,
        processType: node.processType,
        mappingType: node.mappingType || '',
        mappingRule: node.mappingRule || '',
        paramDesc: node.paramDesc || '',
        mappingSource: node.mappingSource || ''
      });
      editDialogTitle.value = `编辑参数配置 - ${node.targetParamKey}`;
      updateParentOptions();
      editDialogVisible.value = true;
    };

    // 复制节点
    const handleCopyNode = (node) => {
      Object.assign(editForm, {
        configId: '', // 清空ID表示新增
        nodeId: node.nodeId,
        parentId: node.parentId || '',
        sourceParamKey: node.sourceParamKey || '',
        sourceParamType: node.sourceParamType || '',
        targetParamKey: node.targetParamKey || '',
        targetParamType: node.targetParamType || '',
        keepSingleElementKey: node.keepSingleElementKey || false,
        processType: node.processType,
        mappingType: node.mappingType || '',
        mappingRule: node.mappingRule || '',
        paramDesc: node.paramDesc || '',
        mappingSource: node.mappingSource || ''
      });
      editDialogTitle.value = `复制参数配置 - ${node.targetParamKey}`;
      updateParentOptions();
      editDialogVisible.value = true;
    };

    // 删除节点
    const handleDeleteNode = async (node) => {
      // 检查是否有子节点
      if (node.children && node.children.length > 0) {
        Message.warning('该节点存在子节点，请先删除子节点');
        return;
      }

      try {
        await MessageBox.confirm(
          `确定要删除参数配置 "${node.targetParamKey}" 吗?`,
          '提示',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }
        );

        loading.value = true;
        await deleteNodeParam(node.configId);
        Message.success('删除成功');
        await refreshData();
      } catch (error) {
        if (error !== 'cancel') {
          console.error('删除失败:', error);
          Message.error('删除失败');
        }
      } finally {
        loading.value = false;
      }
    };

    // 提交编辑表单
    const submitEditForm = async () => {
      if (!editFormRef.value) return;

      const valid = await editFormRef.value.validate().catch(() => false);
      if (!valid) return;

      try {
        submitLoading.value = true;
        const submitData = { ...editForm };

        // 处理特殊字段
        if (submitData.sourceParamType === 'NONE') {
          submitData.sourceParamKey = null;
        }

        if (submitData.parentId === '') {
          submitData.parentId = null;
        }

        if (submitData.targetParamType !== 'OBJECT') {
          delete submitData.keepSingleElementKey;
        }

        if (submitData.processType !== 'POST_PROCESS') {
          delete submitData.mappingSource;
        } else if (submitData.mappingSource === null || submitData.mappingSource === '') {
          submitData.mappingSource = null;
        }

        let response;
        if (submitData.configId) {
          response = await updateNodeParam(submitData);
        } else {
          response = await createNodeParam(submitData);
        }

        if (response.status === 200 && response.data.code === '200') {
          Message.success(submitData.configId ? '更新成功' : '创建成功');
          editDialogVisible.value = false;
          await refreshData();
        }
      } catch (error) {
        console.error('保存失败:', error);
        Message.error('保存失败');
      } finally {
        submitLoading.value = false;
      }
    };

    const getTypeIcon = (type) => {
      const iconMap = {
        'STRING': 'el-icon-document',
        'INTEGER': 'el-icon-data-line',
        'LONG': 'el-icon-data-line',
        'DOUBLE': 'el-icon-data-line',
        'BOOLEAN': 'el-icon-circle-check',
        'OBJECT': 'el-icon-folder-opened',
        'ARRAY': 'el-icon-menu',
        'PURE_ARRAY': 'el-icon-menu',
        'NONE': 'el-icon-minus'
      };
      return iconMap[type] || 'el-icon-document';
    };

    const getTypeColor = (type) => {
      const colorMap = {
        'STRING': '#409EFF',
        'INTEGER': '#67C23A',
        'LONG': '#67C23A',
        'DOUBLE': '#67C23A',
        'BOOLEAN': '#E6A23C',
        'OBJECT': '#F56C6C',
        'ARRAY': '#909399',
        'PURE_ARRAY': '#909399',
        'NONE': '#C0C4CC'
      };
      return colorMap[type] || '#409EFF';
    };

    const getTypeTagType = (type) => {
      const tagMap = {
        'STRING': 'primary',
        'INTEGER': 'success',
        'LONG': 'success',
        'DOUBLE': 'success',
        'BOOLEAN': 'warning',
        'OBJECT': 'danger',
        'ARRAY': 'info',
        'PURE_ARRAY': 'info',
        'NONE': 'info'
      };
      return tagMap[type] || '';
    };

    const getMappingTypeLabel = (type) => {
      const labelMap = {
        'NAME': '值映射',
        'CONSTANT': '固定值',
        'EXPRESSION': '表达式',
        'BEAN_EXPRESSION': 'Bean表达式',
        'SINGLE_MAP': '单值映射'
      };
      return labelMap[type] || type;
    };

    const getMappingTypeTagType = (type) => {
      const tagMap = {
        'NAME': 'primary',
        'CONSTANT': 'success',
        'EXPRESSION': 'warning',
        'BEAN_EXPRESSION': 'danger',
        'SINGLE_MAP': 'info'
      };
      return tagMap[type] || '';
    };

    return {
      dialogVisible,
      activeProcessType,
      loading,
      treeMappings,
      expandedNodes,
      zoomLevel,
      // 编辑对话框
      editDialogVisible,
      editDialogTitle,
      editForm,
      editRules,
      editFormRef,
      submitLoading,
      parentOptions,
      // 方法
      handleProcessTypeChange,
      toggleExpand,
      expandAll,
      collapseAll,
      zoomIn,
      zoomOut,
      handleAddRoot,
      handleAddChild,
      handleEditNode,
      handleCopyNode,
      handleDeleteNode,
      resetEditForm,
      submitEditForm,
      getTypeIcon,
      getTypeColor,
      getTypeTagType,
      getMappingTypeLabel,
      getMappingTypeTagType
    };
  }
};
</script>

<style scoped>
.mapping-visualization-dialog {
  height: 92vh;
}

.control-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 10px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 6px;
  flex-wrap: wrap;
  gap: 10px;
  box-shadow: 0 3px 10px rgba(102, 126, 234, 0.2);
}

.control-bar >>> .el-radio-button__inner {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.3);
  color: white;
}

.control-bar >>> .el-radio-button__orig-radio:checked + .el-radio-button__inner {
  background: white;
  color: #667eea;
  border-color: white;
}

.info-text {
  color: white;
  font-size: 13px;
  flex: 1;
  font-weight: 500;
}

.info-text i {
  margin-right: 6px;
  font-size: 16px;
}

.view-controls {
  display: flex;
  gap: 8px;
  align-items: center;
}

.zoom-level {
  color: white;
  font-size: 12px;
  margin-left: 4px;
  min-width: 40px;
  text-align: center;
}

.view-controls >>> .el-button {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.3);
  color: white;
}

.view-controls >>> .el-button:hover {
  background: white;
  color: #667eea;
  border-color: white;
}

.visualization-container {
  height: calc(92vh - 150px);
  overflow: auto;
  background: linear-gradient(180deg, #f8f9fa 0%, #e9ecef 100%);
  padding: 8px;
  position: relative;
}

.mapping-tree-wrapper {
  max-width: 1200px;
  margin: 0;
  margin-left: 120px;
  width: calc(100% - 120px);
  display: flex;
  flex-direction: column;
  transition: transform 0.2s ease;
}

/* 自定义滚动条 */
.visualization-container::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.visualization-container::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.05);
  border-radius: 3px;
}

.visualization-container::-webkit-scrollbar-thumb {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 3px;
}

.visualization-container::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
}
</style>
