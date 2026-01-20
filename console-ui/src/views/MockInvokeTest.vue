<template>
  <div class="mock-invoke">
    <el-card class="invoke-card">
      <el-form 
        :model="form" 
        :rules="rules" 
        ref="formRef" 
        label-width="120px"
      >
        <el-form-item label="选择节点" prop="nodeId">
          <el-select 
            v-model="form.nodeId" 
            placeholder="请选择节点"
            filterable
            style="width: 100%"
            @change="handleNodeChange"
          >
            <el-option
              v-for="node in nodeOptions"
              :key="node.nodeId"
              :label="node.nodeName"
              :value="node.nodeId"
            ></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="选择Mock场景" prop="mockId">
          <el-select 
            v-model="form.mockId" 
            placeholder="请先选择节点"
            filterable
            style="width: 100%"
            :disabled="!form.nodeId"
          >
            <el-option
              v-for="mock in mockOptions"
              :key="mock.mockId"
              :label="mock.caseName"
              :value="mock.mockId"
            >
              <span>{{ mock.caseName }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px">
                {{ mock.enabled ? '启用' : '禁用' }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="请求参数" prop="businessData">
          <div class="template-selector">
            <div class="template-cards">
              <div 
                v-for="(name, key) in templateNames" 
                :key="key"
                class="template-card"
                :class="{ 'active': selectedTemplate === key }"
                @click="applyJarvisTemplate(key)"
              >
                <div class="card-content">
                  <div class="card-name">{{ name }}</div>
                  <el-tag 
                    size="mini" 
                    :type="templateDirections[key] === 'jarvis -> 三方' ? 'success' : 'warning'"
                  >
                    {{ templateDirections[key] }}
                  </el-tag>
                </div>
              </div>
            </div>
          </div>
          <div class="input-wrapper">
            <el-input
              v-model="form.businessData"
              type="textarea"
              :rows="12"
              placeholder='请选择上方模板或手动输入JSON格式的请求参数，例：{"patientCode": "26011900017", "patientName": "张三"}'
              style="margin-top: 15px;"
            ></el-input>
            <el-button 
              class="expand-button"
              icon="el-icon-full-screen"
              size="mini"
              circle
              @click="showExpandDialog = true"
              title="放大查看"
            ></el-button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button 
            type="primary" 
            @click="handleInvoke"
            :loading="invoking"
          >
            {{ invoking ? '调用中...' : '调用Mock' }}
          </el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button @click="handleFormat">格式JSON</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 响应结果浮动窗口 -->
    <div 
      class="result-float-window" 
      v-show="responseData"
      :style="{ left: windowPosition.x + 'px', top: windowPosition.y + 'px' }"
    >
      <div 
        class="float-header"
        @mousedown="startDrag"
      >
        <div class="header-title">
          <span>响应结果</span>
          <span class="header-tip">(参数映射详情见接口调用日志)</span>
        </div>
        <div class="float-actions">
          <el-button 
            size="mini" 
            icon="el-icon-document-copy"
            @click.stop="copyResponse"
            circle
          ></el-button>
          <el-button 
            size="mini" 
            icon="el-icon-close"
            @click.stop="responseData = null"
            circle
          ></el-button>
        </div>
      </div>
      <div class="float-content">
        <json-viewer
          v-if="parsedResponse"
          :value="parsedResponse"
          :expand-depth="3"
          :copyable="false"
          boxed
          sort
        ></json-viewer>
        <pre v-else>{{ responseData }}</pre>
      </div>
    </div>

    <!-- 错误信息 -->
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      closable
      @close="errorMessage = ''"
      style="margin-top: 20px"
    >
    </el-alert>

    <!-- 放大查看对话框 -->
    <el-dialog
      title="请求参数"
      :visible.sync="showExpandDialog"
      width="70%"
      top="5vh"
      :close-on-click-modal="false"
      class="expand-dialog"
    >
      <div class="expand-content">
        <el-input
          v-model="form.businessData"
          type="textarea"
          :rows="25"
          placeholder='请输入JSON格式的请求参数'
        ></el-input>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="handleExpandFormat">格式化JSON</el-button>
        <el-button type="primary" @click="showExpandDialog = false">确定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue';
import { Message } from 'element-ui';
import { getNodeListUnpaged } from '@/api/workflownode';
import { getMockConfigsByNodeId, invokeMock } from '@/api/mock';
import { jarvisTemplates, templateNames, templateDirections } from '@/config/jarvisTemplates';
import JsonViewer from 'vue-json-viewer';
import 'vue-json-viewer/style.css';

export default {
  name: 'MockInvokeTest',
  components: {
    JsonViewer
  },
  setup() {
    const formRef = ref(null);
    const nodeOptions = ref([]);
    const mockOptions = ref([]);
    const invoking = ref(false);
    const responseData = ref(null);
    const parsedResponse = ref(null);
    const errorMessage = ref('');
    const selectedTemplate = ref(null);
    const windowPosition = ref({ x: window.innerWidth - 520, y: window.innerHeight - 620 });
    const isDragging = ref(false);
    const dragStart = ref({ x: 0, y: 0 });
    const showExpandDialog = ref(false);

    const form = reactive({
      nodeId: null,
      mockId: null,
      businessData: '{}'
    });

    const validateJson = (rule, value, callback) => {
      if (!value || value.trim() === '') {
        callback(new Error('请输入请求参数'));
        return;
      }
      try {
        JSON.parse(value);
        callback();
      } catch (error) {
        callback(new Error('请输入有效的JSON格式'));
      }
    };

    const rules = {
      nodeId: [
        { required: true, message: '请选择节点', trigger: 'change' }
      ],
      mockId: [
        { required: true, message: '请选择Mock场景', trigger: 'change' }
      ],
      businessData: [
        { required: true, message: '请输入请求参数', trigger: 'blur' },
        { validator: validateJson, trigger: 'blur' }
      ]
    };

    const loadNodeList = async () => {
      try {
        const response = await getNodeListUnpaged();
        if (response.status === 200 && response.data.code === '200') {
          nodeOptions.value = response.data.rsp || [];
        }
      } catch (error) {
        console.error('获取节点列表失败:', error);
        Message.error('获取节点列表失败');
      }
    };

    const handleNodeChange = async (nodeId) => {
      form.mockId = null;
      mockOptions.value = [];
      
      if (!nodeId) return;

      try {
        const response = await getMockConfigsByNodeId(nodeId);
        if (response.status === 200 && response.data.code === '200') {
          mockOptions.value = response.data.rsp || [];
        }
      } catch (error) {
        console.error('获取Mock配置失败:', error);
        Message.error('获取Mock配置列表失败');
      }
    };

    const handleInvoke = async () => {
      const valid = await formRef.value.validate().catch(() => false);
      if (!valid) return;

      invoking.value = true;
      responseData.value = null;
      parsedResponse.value = null;
      errorMessage.value = '';

      try {
        const businessData = JSON.parse(form.businessData);
        const response = await invokeMock(form.nodeId, form.mockId, businessData);
        
        if (response.status === 200 && response.data.code === '200') {
          responseData.value = JSON.stringify(response.data.rsp, null, 2);
          parsedResponse.value = response.data.rsp;
          Message.success('Mock调用成功');
        } else {
          errorMessage.value = response.data.msg || 'Mock调用失败';
        }
      } catch (error) {
        console.error('Mock调用失败:', error);
        errorMessage.value = error.message || 'Mock调用失败';
      } finally {
        invoking.value = false;
      }
    };

    const handleReset = () => {
      formRef.value?.resetFields();
      form.nodeId = null;
      form.mockId = null;
      form.businessData = '{}';
      mockOptions.value = [];
      responseData.value = null;
      parsedResponse.value = null;
      errorMessage.value = '';
      selectedTemplate.value = null;
    };

    const handleFormat = () => {
      try {
        const obj = JSON.parse(form.businessData);
        form.businessData = JSON.stringify(obj, null, 2);
        Message.success('JSON格式化成功');
      } catch (error) {
        Message.error('请输入有效的JSON格式');
      }
    };

    const handleExpandFormat = () => {
      try {
        const obj = JSON.parse(form.businessData);
        form.businessData = JSON.stringify(obj, null, 2);
        Message.success('JSON格式化成功');
      } catch (error) {
        Message.error('请输入有效的JSON格式');
      }
    };

    const applyJarvisTemplate = (templateKey) => {
      const template = jarvisTemplates[templateKey];
      if (template) {
        form.businessData = JSON.stringify(template, null, 2);
        selectedTemplate.value = templateKey;
        const templateName = templateNames[templateKey];
        Message.success(`已应用${templateName}模板`);
      }
    };

    const copyResponse = async () => {
      if (!responseData.value) return;
      
      try {
        await navigator.clipboard.writeText(responseData.value);
        Message.success('复制成功');
      } catch (error) {
        // fallback
        const textarea = document.createElement('textarea');
        textarea.value = responseData.value;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        try {
          document.execCommand('copy');
          Message.success('复制成功');
        } catch (err) {
          Message.error('复制失败');
        }
        document.body.removeChild(textarea);
      }
    };

    const startDrag = (e) => {
      isDragging.value = true;
      dragStart.value = {
        x: e.clientX - windowPosition.value.x,
        y: e.clientY - windowPosition.value.y
      };
      
      const onMouseMove = (e) => {
        if (!isDragging.value) return;
        windowPosition.value = {
          x: e.clientX - dragStart.value.x,
          y: e.clientY - dragStart.value.y
        };
      };
      
      const onMouseUp = () => {
        isDragging.value = false;
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
      };
      
      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
    };

    onMounted(() => {
      loadNodeList();
    });

    return {
      form,
      formRef,
      rules,
      nodeOptions,
      mockOptions,
      invoking,
      responseData,
      parsedResponse,
      errorMessage,
      templateNames,
      templateDirections,
      selectedTemplate,
      windowPosition,
      showExpandDialog,
      handleNodeChange,
      handleInvoke,
      handleReset,
      handleFormat,
      handleExpandFormat,
      copyResponse,
      applyJarvisTemplate,
      startDrag
    };
  }
};
</script>

<style scoped>
.mock-invoke {
  padding: 20px;
}

h2 {
  margin-bottom: 20px;
}

.invoke-card {
  margin-bottom: 20px;
}

/* 响应结果浮动窗口 */
.result-float-window {
  position: fixed;
  width: 500px;
  max-height: 600px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.float-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #409eff;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: move;
  user-select: none;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-tip {
  font-size: 12px;
  font-weight: normal;
  opacity: 0.85;
}

.float-actions {
  display: flex;
  gap: 8px;
}

.float-actions .el-button {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.3);
  color: #fff;
}

.float-actions .el-button:hover {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
}

.float-content {
  flex: 1;
  overflow: auto;
  padding: 16px;
  background: #f5f7fa;
}

.float-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-size: 12px;
}

.result-card {
  margin-top: 20px;
}

.response-content {
  max-height: 600px;
  overflow: auto;
  background: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
}

.response-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}

/* 模板选择器样式 */
.template-selector {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px;
  background: #fff;
}

.template-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 6px;
  max-height: 200px;
  overflow-y: auto;
}

.template-card {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 6px 8px;
  cursor: pointer;
  transition: all 0.3s;
  background: #fff;
}

.template-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
  transform: translateY(-2px);
}

.template-card.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-name {
  font-size: 11px;
  color: #303133;
  font-weight: 500;
  line-height: 1.2;
}

.template-card.active .card-name {
  color: #409eff;
}

.template-card .el-tag {
  align-self: flex-start;
}

/* 输入框包装器 */
.input-wrapper {
  position: relative;
  margin-top: 15px;
}

.expand-button {
  position: absolute;
  top: 20px;
  right: 10px;
  z-index: 10;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #dcdfe6;
}

.expand-button:hover {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}

/* 放大对话框样式 */
.expand-dialog .el-dialog__body {
  padding: 20px;
}

.expand-content {
  width: 100%;
}

.expand-content .el-textarea {
  font-family: 'Monaco', 'Menlo', 'Consolas', 'Courier New', monospace;
  font-size: 13px;
}

.expand-content .el-textarea__inner {
  line-height: 1.6;
}
</style>
