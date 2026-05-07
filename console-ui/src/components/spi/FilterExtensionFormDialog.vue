<template>
  <el-dialog
    :title="dialogTitle"
    :visible.sync="dialogVisible"
    width="80%"
    top="5vh"
    :close-on-click-modal="false"
  >
    <el-form :model="formModel" label-width="120px" ref="filterExtensionForm" :rules="rules">
      <el-divider content-position="left">基础信息</el-divider>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="关联节点" prop="bindingIds" required>
            <el-select
              v-model="formModel.bindingIds"
              placeholder="请选择要关联的节点（可多选）"
              style="width: 100%"
              filterable
              multiple
              collapse-tags
            >
              <el-option v-for="node in nodeList" :key="node.nodeId" :label="node.nodeName" :value="node.nodeId"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="扩展名称" prop="extensionName">
            <el-input v-model="formModel.extensionName" placeholder="例如: header"></el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="实现类全名" prop="className">
            <el-input v-model="formModel.className" placeholder="例如: com.helianhealth.agent.custom.HeaderFilter"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="描述" prop="description">
            <el-input v-model="formModel.description" placeholder="请输入扩展描述"></el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">扩展配置</el-divider>

      <el-form-item>
        <span slot="label">
          扩展上下文
          <el-tooltip content="配置扩展执行时需要的参数，如未配置则默认使用 &quot;true&quot;" placement="top">
            <i class="el-icon-question" style="color: #909399; margin-left: 4px;"></i>
          </el-tooltip>
        </span>
        <div class="param-list">
          <div v-for="(param, index) in formModel.extensionParamList" :key="index" class="param-item">
            <el-input v-model="param.key" placeholder="参数名（key）" style="width: 40%; margin-right: 10px;"></el-input>
            <el-input v-model="param.value" placeholder="参数值（value）" style="width: 40%; margin-right: 10px;"></el-input>
            <el-button type="danger" icon="el-icon-delete" size="small" circle @click="removeParam(index)"></el-button>
          </div>
          <el-button type="text" icon="el-icon-plus" @click="addParam" style="margin-top: 10px;">添加参数</el-button>
        </div>
      </el-form-item>

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
  name: 'FilterExtensionFormDialog',
  props: {
    visible: { type: Boolean, default: false },
    dialogTitle: { type: String, default: '' },
    form: { type: Object, required: true },
    rules: { type: Object, required: true },
    nodeList: { type: Array, default: () => [] },
    saveLoading: { type: Boolean, default: false }
  },
  data() {
    return {
      formModel: {}
    };
  },
  computed: {
    dialogVisible: {
      get() {
        return this.visible;
      },
      set(v) {
        this.$emit('update:visible', v);
      }
    }
  },
  watch: {
    visible(v) {
      if (v) {
        this.formModel = cloneFormData(this.form);
        if (!Array.isArray(this.formModel.bindingIds)) {
          this.formModel.bindingIds = [];
        }
        if (!Array.isArray(this.formModel.extensionParamList)) {
          this.formModel.extensionParamList = [];
        }
      }
    }
  },
  methods: {
    addParam() {
      this.formModel.extensionParamList.push({ key: '', value: '' });
    },
    removeParam(index) {
      this.formModel.extensionParamList.splice(index, 1);
    },
    validate() {
      return this.$refs.filterExtensionForm.validate();
    },
    clearValidate() {
      this.$nextTick(() => {
        if (this.$refs.filterExtensionForm) this.$refs.filterExtensionForm.clearValidate();
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

<style scoped>
.param-list {
  width: 100%;
}

.param-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}
</style>
