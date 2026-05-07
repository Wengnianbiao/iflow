<template>
  <div class="spi-extension-dialog-stack">
    <ExtensionTypeSelectorDialog
      :visible="page.typeSelectorVisible"
      :is-filter-page="page.isFilterPage"
      :is-node-page="page.isNodePage"
      :is-workflow-page="page.isWorkflowPage"
      @update:visible="v => (page.typeSelectorVisible = v)"
      @select="page.selectExtensionType"
    />

    <InterfaceExtensionFormDialog
      v-if="stackMode !== 'filter'"
      ref="interfaceFormRef"
      :visible="page.interfaceExtensionDialogVisible"
      :dialog-title="page.dialogTitle"
      :form="page.interfaceExtensionForm"
      :rules="page.interfaceExtensionRules"
      :node-list="page.nodeList"
      :workflows-for-route-binding="page.workflowsForRouteBinding"
      :save-loading="page.saveLoading"
      @update:visible="v => (page.interfaceExtensionDialogVisible = v)"
      @update:form="page.mergeInterfaceExtensionForm"
      @open-code-editor="page.openCodeEditor('interface')"
      @save="page.handleSaveInterfaceExtension"
    />

    <FilterExtensionFormDialog
      v-if="stackMode === 'filter'"
      ref="filterFormRef"
      :visible="page.filterExtensionDialogVisible"
      :dialog-title="page.dialogTitle"
      :form="page.filterExtensionForm"
      :rules="page.filterExtensionRules"
      :node-list="page.nodeList"
      :save-loading="page.saveLoading"
      @update:visible="v => (page.filterExtensionDialogVisible = v)"
      @update:form="page.mergeFilterExtensionForm"
      @open-code-editor="page.openCodeEditor('filter')"
      @save="page.handleSaveFilterExtension"
    />

    <JavaSourceEditorDialog
      ref="javaSourceEditorRef"
      :visible="page.codeEditorVisible"
      :title="page.codeEditorTitle"
      :code="page.codeEditorContent"
      @update:visible="v => (page.codeEditorVisible = v)"
      @update:code="v => (page.codeEditorContent = v)"
      @apply-template="page.applyTemplateToEditor"
      @confirm="page.confirmCodeEditor"
    />
  </div>
</template>

<script>
import ExtensionTypeSelectorDialog from '@/components/spi/ExtensionTypeSelectorDialog.vue';
import InterfaceExtensionFormDialog from '@/components/spi/InterfaceExtensionFormDialog.vue';
import FilterExtensionFormDialog from '@/components/spi/FilterExtensionFormDialog.vue';
import JavaSourceEditorDialog from '@/components/spi/JavaSourceEditorDialog.vue';

export default {
  name: 'SpiExtensionDialogStack',
  components: {
    ExtensionTypeSelectorDialog,
    InterfaceExtensionFormDialog,
    FilterExtensionFormDialog,
    JavaSourceEditorDialog
  },
  inject: {
    spiExtensionPage: { default: null }
  },
  props: {
    /** workflow：接口+过滤器相关弹窗按需由父页面挂载；此处 node=仅节点扩展 / filter=仅过滤器 */
    stackMode: {
      type: String,
      required: true,
      validator: v => ['workflow', 'node', 'filter'].includes(v)
    }
  },
  computed: {
    page() {
      const p = this.spiExtensionPage;
      if (!p) {
        throw new Error('SpiExtensionDialogStack: provide() spiExtensionPage');
      }
      return p;
    }
  }
};
</script>
