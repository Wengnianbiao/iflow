<template>
  <el-dialog
    :title="dialogTitle"
    :visible.sync="dialogVisible"
    width="80%"
    top="5vh"
    :close-on-click-modal="false"
  >
    <el-form :model="formModel" label-width="120px" ref="interfaceExtensionForm" :rules="rules">
      <el-divider content-position="left">基础信息</el-divider>

      <el-row
        v-if="formModel.spiType === 'OpenApiRouteHandler' || formModel.spiType === 'JarvisRouteHandler'"
        :gutter="20"
      >
        <el-col :span="24">
          <el-form-item label="关联工作流">
            <el-select
              v-model="formModel.selectedFlowId"
              filterable
              clearable
              placeholder="请选择关联工作流"
              style="width: 100%"
            >
              <el-option
                v-for="workflow in workflowsForRouteBinding"
                :key="workflow.flowId"
                :label="`${workflow.flowName} (${workflow.interfaceUri})`"
                :value="workflow.flowId"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 工作流节点扩展：关联节点在基础信息最前 -->
      <template v-if="formModel.spiType === 'InterfaceRemoteInvoker'">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="关联节点" prop="bindingIds" required>
              <el-select v-model="remoteBindingNodeId" placeholder="请选择要关联的节点（单选）" style="width: 100%" filterable>
                <el-option v-for="node in nodeList" :key="node.nodeId" :label="node.nodeName" :value="node.nodeId"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="扩展名称" prop="extensionName">
              <el-input v-model="formModel.extensionName" placeholder="例如: pacs"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="实现类全名" prop="className">
              <el-input v-model="formModel.className" placeholder="例如: com.helianhealth.agent.custom.MyRemoteInvoker"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="描述" prop="description">
              <el-input v-model="formModel.description" placeholder="请输入扩展描述"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- Open API / Jarvis 工作流路由扩展（与节点扩展字段顺序无关，保持原样） -->
      <template v-else-if="formModel.spiType === 'OpenApiRouteHandler' || formModel.spiType === 'JarvisRouteHandler'">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="实现类全名" prop="className">
              <el-input v-model="formModel.className" placeholder="例如: com.helianhealth.agent.custom.MyRemoteInvoker"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="描述" prop="description">
              <el-input v-model="formModel.description" placeholder="请输入扩展描述"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="扩展名称" prop="extensionName">
              <el-input v-model="formModel.extensionName" placeholder="例如: pacs"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <el-divider content-position="left">扩展配置</el-divider>

      <el-form-item label="扩展方式" prop="extensionMode" required>
        <el-radio-group v-model="formModel.extensionMode">
          <el-radio label="ONLINE">
            <span style="font-weight: 600;">在线扩展</span>
            <span style="color: #909399; font-size: 12px; margin-left: 8px;">（在管理页面编写代码）</span>
          </el-radio>
          <el-radio label="PROJECT">
            <span style="font-weight: 600;">项目扩展</span>
            <span style="color: #909399; font-size: 12px; margin-left: 8px;">（在业务项目中实现接口）</span>
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="Java源代码" prop="sourceCode" v-if="formModel.extensionMode === 'ONLINE'">
        <el-button size="small" type="primary" plain icon="el-icon-edit-outline" @click="$emit('open-code-editor')">
          {{ formModel.sourceCode ? '编辑源代码' : '编写源代码' }}
        </el-button>
      </el-form-item>
    </el-form>

    <div slot="footer">
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submit" :loading="saveLoading">保存</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { cloneFormData } from '@/utils/formClone';

export default {
  name: 'InterfaceExtensionFormDialog',
  props: {
    visible: { type: Boolean, default: false },
    dialogTitle: { type: String, default: '' },
    form: { type: Object, required: true },
    rules: { type: Object, required: true },
    nodeList: { type: Array, default: () => [] },
    workflowsForRouteBinding: { type: Array, default: () => [] },
    saveLoading: { type: Boolean, default: false }
  },
  data() {
    return {
      formModel: {}
    };
  },
  computed: {
    /** 远程 Invoker：bindingIds 仅存 0 或 1 个节点 ID */
    remoteBindingNodeId: {
      get() {
        const b = this.formModel.bindingIds;
        return Array.isArray(b) && b.length ? b[0] : null;
      },
      set(v) {
        this.$set(this.formModel, 'bindingIds', v != null ? [v] : []);
      }
    },
    dialogVisible: {
      get() {
        return this.visible;
      },
      set(v) {
        this.$emit('update:visible', v);
      }
    },
  },
  watch: {
    visible(v) {
      if (v) {
        this.formModel = cloneFormData(this.form);
        if (!Array.isArray(this.formModel.bindingIds)) {
          this.formModel.bindingIds = [];
        }
      }
    }
  },
  methods: {
    validate() {
      return this.$refs.interfaceExtensionForm.validate();
    },
    clearValidate() {
      this.$nextTick(() => {
        if (this.$refs.interfaceExtensionForm) this.$refs.interfaceExtensionForm.clearValidate();
      });
    },
    async submit() {
      try {
        await this.validate();
        this.$emit('update:form', cloneFormData(this.formModel));
        this.$emit('save');
      } catch (e) {
        /* validation failed */
      }
    }
  }
};
</script>
