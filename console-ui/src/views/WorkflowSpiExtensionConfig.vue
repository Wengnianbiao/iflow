<template>
  <div class="extension-manager">
    <SpiExtensionToolbar
      :search-keyword.sync="searchKeyword"
      @add="showTypeSelector"
      @refresh="loadExtensionList"
    />
    <SpiExtensionDataTable
      :data="filteredExtensionList"
      :loading="tableLoading"
      :associated-tags="getSpiAssociatedTags"
      @edit="handleEdit"
      @toggle-enabled="handleToggleEnabled"
      @delete="handleDelete"
    />
    <SpiExtensionDialogStack ref="spiDialogStack" stack-mode="workflow" />
  </div>
</template>

<script>
import { Message, MessageBox } from 'element-ui';
import {
  getJarvisWorkflowRouteExtensions,
  getWorkflowExtensionBindings,
  getWorkflowRouteExtensions,
  registerInterfaceExtension,
  registerJarvisWorkflowRouteExtension,
  registerWorkflowRouteExtension,
  updateInterfaceExtension,
  updateJarvisWorkflowRouteExtension,
  updateWorkflowRouteExtension
} from '@/api/extension';
import { getAllNodes, getAllWorkflowsUnpaged } from '@/api/workflow';
import SpiExtensionToolbar from '@/components/spi/SpiExtensionToolbar.vue';
import SpiExtensionDataTable from '@/components/spi/SpiExtensionDataTable.vue';
import SpiExtensionDialogStack from '@/components/spi/SpiExtensionDialogStack.vue';
import { SPI_EXTENSION_TEMPLATES } from '@/utils/spiExtensionTemplates';
import { getExtensionTypeName, getExtensionTypeTag } from '@/utils/spiExtensionMetadata';
import {
  disableExtensionRequest as disableReq,
  enableExtensionRequest as enableReq,
  deleteExtensionRequest as deleteReq,
  loadExtensionDetailRequest
} from '@/utils/spiExtensionCrud';
import { parseBindingIds, resolveExtensionMode } from '@/utils/spiExtensionFormParse';
import { computeWorkflowsForRouteBinding } from '@/utils/workflowEntryFilter';
import { normalizeExtensionListResponse } from '@/utils/spiExtensionListResponse';

export default {
  name: 'WorkflowSpiExtensionConfig',
  components: {
    SpiExtensionToolbar,
    SpiExtensionDataTable,
    SpiExtensionDialogStack
  },
  provide() {
    return {
      spiExtensionPage: this
    };
  },
  data() {
    return {
      extensionList: [],
      nodeList: [],
      workflowList: [],
      searchKeyword: '',
      tableLoading: false,

      typeSelectorVisible: false,

      interfaceExtensionDialogVisible: false,
      interfaceExtensionForm: {
        configId: null,
        bindingIds: [],
        extensionName: '',
        className: '',
        sourceCode: '',
        description: '',
        enabled: 1,
        extensionMode: 'ONLINE',
        spiType: 'OpenApiRouteHandler',
        selectedFlowId: null
      },
      interfaceExtensionRules: {
        extensionName: [{ required: true, message: '请输入扩展名称', trigger: 'blur' }],
        className: [{ required: true, message: '请输入实现类全名', trigger: 'blur' }],
        extensionMode: [{ required: true, message: '请选择扩展方式', trigger: 'change' }]
      },

      codeEditorVisible: false,
      codeEditorTitle: '',
      codeEditorContent: '',
      codeEditorTarget: '',

      dialogTitle: '',
      isEdit: false,
      saveLoading: false,
      originalExtensionName: '',
      routeExtensionAssociationLabels: {}
    };
  },
  computed: {
    isWorkflowPage() {
      return true;
    },
    isNodePage() {
      return false;
    },
    isFilterPage() {
      return false;
    },
    filteredExtensionList() {
      if (!this.searchKeyword) return this.extensionList;
      const kw = this.searchKeyword.toLowerCase();
      return this.extensionList.filter(
        item =>
          (item.extensionName && item.extensionName.toLowerCase().includes(kw)) ||
          (item.className && item.className.toLowerCase().includes(kw))
      );
    },
    workflowsForRouteBinding() {
      return computeWorkflowsForRouteBinding(
        this.workflowList,
        this.interfaceExtensionForm.spiType,
        this.interfaceExtensionForm.selectedFlowId
      );
    }
  },
  mounted() {
    this.loadExtensionList();
    this.loadNodeList();
    this.loadWorkflowList();
  },
  methods: {
    getExtensionTypeName,
    getExtensionTypeTag,

    mergeInterfaceExtensionForm(payload) {
      Object.assign(this.interfaceExtensionForm, payload);
    },

    ifaceFormDialog() {
      const stack = this.$refs.spiDialogStack;
      return stack && stack.$refs.interfaceFormRef;
    },
    javaEditorRef() {
      const stack = this.$refs.spiDialogStack;
      return stack && stack.$refs.javaSourceEditorRef;
    },

    openCodeEditor(target) {
      this.codeEditorTarget = target;
      this.codeEditorTitle = '编辑源代码 - 工作流扩展';
      const dlg = this.interfaceExtensionDialogVisible ? this.ifaceFormDialog() : null;
      const m = dlg ? dlg.formModel : null;
      this.codeEditorContent =
        m && m.sourceCode != null ? m.sourceCode : this.interfaceExtensionForm.sourceCode || '';
      this.codeEditorVisible = true;
    },

    confirmCodeEditor(code) {
      const text = typeof code === 'string' ? code : this.codeEditorContent;
      this.interfaceExtensionForm.sourceCode = text;
      const dlg = this.ifaceFormDialog();
      if (this.interfaceExtensionDialogVisible && dlg && dlg.formModel) {
        dlg.formModel.sourceCode = text;
      }
    },

    applyTemplateToEditor() {
      const dlg = this.ifaceFormDialog();
      const spiType =
        this.interfaceExtensionDialogVisible && dlg ? dlg.formModel.spiType : this.interfaceExtensionForm.spiType;
      const templateKey =
        spiType === 'OpenApiRouteHandler'
          ? 'workflowRouteHandler'
          : spiType === 'JarvisRouteHandler'
            ? 'jarvisRouteHandler'
            : 'interfaceRemoteInvoker';
      const apply = () => {
        this.codeEditorContent = SPI_EXTENSION_TEMPLATES[templateKey];
        this.$nextTick(() => {
          const ed = this.javaEditorRef();
          if (ed) ed.setEditorValue(this.codeEditorContent);
        });
      };
      if (this.codeEditorContent && this.codeEditorContent.trim()) {
        MessageBox.confirm('使用模板将覆盖当前代码，是否继续？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
          .then(apply)
          .catch(() => {});
      } else {
        apply();
      }
    },

    async loadExtensionList() {
      this.tableLoading = true;
      try {
        const [openApi, jarvis] = await Promise.all([
          getWorkflowRouteExtensions(),
          getJarvisWorkflowRouteExtensions()
        ]);
        this.extensionList = [
          ...normalizeExtensionListResponse(openApi),
          ...normalizeExtensionListResponse(jarvis)
        ];
        await this.refreshRouteExtensionAssociationLabels();
      } catch (error) {
        Message.error('加载扩展列表失败：' + (error.message || '未知错误'));
        this.extensionList = [];
      } finally {
        this.tableLoading = false;
      }
    },

    async loadNodeList() {
      try {
        const response = await getAllNodes();
        if (response && response.data) {
          const result = response.data;
          this.nodeList = Array.isArray(result.data) ? result.data : Array.isArray(result) ? result : [];
        } else {
          this.nodeList = [];
        }
      } catch (error) {
        this.nodeList = [];
        Message.error('加载节点列表失败，请刷新重试');
      }
    },

    async loadWorkflowList() {
      try {
        const response = await getAllWorkflowsUnpaged();
        const result = response && response.data ? response.data : [];
        const list = Array.isArray(result.data) ? result.data : Array.isArray(result) ? result : [];
        this.workflowList = list;
      } catch (error) {
        Message.error('加载工作流列表失败：' + (error.message || '未知错误'));
        this.workflowList = [];
      }
    },

    showTypeSelector() {
      if (!this.nodeList || this.nodeList.length === 0) {
        this.loadNodeList();
      }
      this.typeSelectorVisible = true;
    },

    selectExtensionType(type) {
      this.typeSelectorVisible = false;
      this.isEdit = false;

      if (type === 'InterfaceRemoteInvoker' || type === 'OpenApiRouteHandler' || type === 'JarvisRouteHandler') {
        this.dialogTitle =
          type === 'OpenApiRouteHandler'
            ? '新增Open API工作流扩展'
            : type === 'JarvisRouteHandler'
              ? '新增Jarvis工作流扩展'
              : '新增工作流节点扩展';
        this.interfaceExtensionForm = {
          configId: null,
          bindingIds: [],
          extensionName: '',
          className: '',
          sourceCode: '',
          description: '',
          enabled: 1,
          extensionMode: 'ONLINE',
          spiType: type,
          selectedFlowId: null
        };
        this.originalExtensionName = '';
        this.interfaceExtensionDialogVisible = true;
        this.$nextTick(() => {
          const d = this.ifaceFormDialog();
          if (d) d.clearValidate();
        });
      }
    },

    async handleEdit(row) {
      this.isEdit = true;
      let detailRow = row;
      try {
        const detailResp = await loadExtensionDetailRequest(row);
        if (detailResp && detailResp.data) detailRow = detailResp.data;
      } catch (error) {
        console.error('Failed to load extension detail:', error);
      }

      const mode = resolveExtensionMode(detailRow);
      const bindingIds = parseBindingIds(detailRow);

      if (
        detailRow.spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker' ||
        detailRow.spiInterface === 'com.helianhealth.agent.api.OpenApiRouteHandler' ||
        detailRow.spiInterface === 'com.helianhealth.agent.api.JarvisRouteHandler'
      ) {
        const spiType =
          detailRow.spiInterface === 'com.helianhealth.agent.api.OpenApiRouteHandler'
            ? 'OpenApiRouteHandler'
            : detailRow.spiInterface === 'com.helianhealth.agent.api.JarvisRouteHandler'
              ? 'JarvisRouteHandler'
              : 'InterfaceRemoteInvoker';
        this.dialogTitle =
          spiType === 'OpenApiRouteHandler'
            ? '编辑Open API工作流扩展'
            : spiType === 'JarvisRouteHandler'
              ? '编辑Jarvis工作流扩展'
              : '编辑工作流节点扩展';
        const selectedFlowId =
          spiType === 'OpenApiRouteHandler' || spiType === 'JarvisRouteHandler'
            ? await this.loadWorkflowBindingsForExtension(detailRow.extensionName)
            : null;
        this.interfaceExtensionForm = {
          ...detailRow,
          bindingIds:
            spiType === 'InterfaceRemoteInvoker'
              ? (Array.isArray(bindingIds) ? [...bindingIds] : [])
              : [],
          extensionMode: mode,
          spiType,
          selectedFlowId
        };
        this.originalExtensionName = detailRow.extensionName || '';
        this.interfaceExtensionDialogVisible = true;
        this.$nextTick(() => {
          const d = this.ifaceFormDialog();
          if (d) d.clearValidate();
        });
      }
    },

    async handleToggleEnabled(row) {
      const action = row.enabled === 1 ? '禁用' : '启用';
      try {
        await MessageBox.confirm(
          `确定要${action}扩展 "${row.extensionName}" 吗？${row.enabled === 1 ? '禁用后将从运行时卸载。' : '启用后将重新加载到运行时。'}`,
          '警告',
          { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
        );
        if (row.enabled === 1) {
          await disableReq(row);
        } else {
          await enableReq(row);
        }
        Message.success(`${action}成功`);
        this.loadExtensionList();
      } catch (error) {
        if (error !== 'cancel') {
          Message.error(`${action}失败：` + (error.message || '未知错误'));
        }
      }
    },

    async handleDelete(row) {
      try {
        await MessageBox.confirm(
          `确定要删除扩展 "${row.extensionName}" 吗？此操作将物理删除配置且不可恢复。`,
          '警告',
          { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
        );
        await deleteReq(row);
        Message.success('删除成功');
        this.loadExtensionList();
      } catch (error) {
        if (error !== 'cancel') {
          Message.error('删除失败：' + (error.message || '未知错误'));
        }
      }
    },

    async handleSaveInterfaceExtension() {
      if (
        this.interfaceExtensionForm.spiType !== 'OpenApiRouteHandler' &&
        this.interfaceExtensionForm.spiType !== 'JarvisRouteHandler' &&
        (!this.interfaceExtensionForm.bindingIds || !this.interfaceExtensionForm.bindingIds.length)
      ) {
        Message.error('请选择关联节点');
        return;
      }
      if (!this.interfaceExtensionForm.extensionMode) {
        Message.error('请选择扩展方式');
        return;
      }
      if (this.interfaceExtensionForm.extensionMode === 'ONLINE') {
        if (!this.interfaceExtensionForm.sourceCode || !this.interfaceExtensionForm.sourceCode.trim()) {
          Message.error('在线扩展模式下，请输入Java源代码');
          return;
        }
      }

      this.saveLoading = true;
      try {
        const bindFlowId = this.interfaceExtensionForm.selectedFlowId || null;
        const isRouteType =
          this.interfaceExtensionForm.spiType === 'OpenApiRouteHandler' ||
          this.interfaceExtensionForm.spiType === 'JarvisRouteHandler';
        const submitData = {
          ...this.interfaceExtensionForm,
          bindingIds: isRouteType
            ? (bindFlowId != null ? [bindFlowId] : [])
            : (Array.isArray(this.interfaceExtensionForm.bindingIds) &&
                this.interfaceExtensionForm.bindingIds.length
                ? this.interfaceExtensionForm.bindingIds
                : [])
        };
        delete submitData.selectedFlowId;
        if (submitData.extensionMode === 'PROJECT') delete submitData.sourceCode;

        const isWorkflowType = this.interfaceExtensionForm.spiType === 'OpenApiRouteHandler';
        const isJarvisWorkflowType = this.interfaceExtensionForm.spiType === 'JarvisRouteHandler';
        if (this.isEdit) {
          if (isWorkflowType) {
            await updateWorkflowRouteExtension(submitData);
          } else if (isJarvisWorkflowType) {
            await updateJarvisWorkflowRouteExtension(submitData);
          } else {
            await updateInterfaceExtension(submitData);
          }
          Message.success('更新成功，已热刷新');
        } else {
          if (isWorkflowType) {
            await registerWorkflowRouteExtension(submitData);
          } else if (isJarvisWorkflowType) {
            await registerJarvisWorkflowRouteExtension(submitData);
          } else {
            await registerInterfaceExtension(submitData);
          }
          Message.success('新增成功，已注册到系统');
        }
        this.interfaceExtensionDialogVisible = false;
        this.loadExtensionList();
      } catch (error) {
        Message.error('保存失败：' + (error.message || '未知错误'));
      } finally {
        this.saveLoading = false;
      }
    },

    getNodeNameById(id) {
      const node = this.nodeList.find(n => n.nodeId === id);
      return node ? node.nodeName : `节点${id}`;
    },

    getSpiAssociatedTags(row) {
      if (
        row.spiInterface === 'com.helianhealth.agent.api.OpenApiRouteHandler' ||
        row.spiInterface === 'com.helianhealth.agent.api.JarvisRouteHandler'
      ) {
        const name = row.extensionName != null ? String(row.extensionName).trim() : '';
        const label = name ? this.routeExtensionAssociationLabels[name] : '';
        return label ? [label] : [];
      }
      const ids = Array.isArray(row.bindingIds) ? row.bindingIds : [];
      return ids.map(id => this.getNodeNameById(id));
    },

    async refreshRouteExtensionAssociationLabels() {
      try {
        const response = await getWorkflowExtensionBindings();
        const result = response && response.data ? response.data : [];
        const list = Array.isArray(result.data) ? result.data : Array.isArray(result) ? result : [];
        const map = {};
        for (const b of list) {
          const extName = b.routeExtension != null ? String(b.routeExtension).trim() : '';
          if (!extName) continue;
          const flowName = b.flowName || (b.flowId != null ? `工作流${b.flowId}` : '工作流');
          const uri = b.interfaceUri != null ? String(b.interfaceUri) : '';
          map[extName] = uri ? `${flowName} (${uri})` : flowName;
        }
        this.routeExtensionAssociationLabels = map;
      } catch (error) {
        this.routeExtensionAssociationLabels = {};
        console.error('加载工作流路由扩展绑定失败:', error);
      }
    },

    async loadWorkflowBindingsForExtension(extensionName) {
      try {
        const response = await getWorkflowExtensionBindings();
        const result = response && response.data ? response.data : [];
        const list = Array.isArray(result.data) ? result.data : Array.isArray(result) ? result : [];
        const matched = list.find(item => item.routeExtension === extensionName);
        return matched ? matched.flowId : null;
      } catch (error) {
        Message.error('加载扩展关联工作流失败：' + (error.message || '未知错误'));
        return null;
      }
    }
  }
};
</script>

<style scoped>
.extension-manager {
  padding: 20px;
}
</style>
