<template>
  <div class="invoke-log">
    <div style="margin-bottom: 20px; background-color: #f5f7fa; padding: 20px; border-radius: 4px;">
      <el-form :inline="true" :model="searchForm" style="display: flex; justify-content: center; flex-wrap: wrap; gap: 10px;">
        <el-form-item label="接口名称">
          <el-select
            v-model="searchForm.nodeName"
            ref="nodeSelectRef"
            placeholder="请选择或输入接口名称"
            clearable
            filterable
            allow-create
            default-first-option
            @blur="handleNodeBlur"
            style="width: 240px;"
          >
            <el-option
              v-for="node in nodeOptions"
              :key="node.nodeId"
              :label="node.nodeName"
              :value="node.nodeName"
            ></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="请输入关键字" clearable></el-input>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>

        <el-form-item>
          <el-switch
            v-model="debugMode"
            active-text="调试模式"
            inactive-text="标准模式"
            active-color="#E6A23C"
          ></el-switch>
        </el-form-item>
      </el-form>
    </div>

    <!-- 日志表格 -->
    <el-table :data="logList" style="width: 100%" v-loading="loading">
      <el-table-column key="nodeName" label="接口名称" min-width="100">
        <template #default="scope">
          {{ scope.row.nodeName || scope.row.nodeId || '-' }}
        </template>
      </el-table-column>

      <el-table-column key="keyword" label="关键字" min-width="100">
        <template #default="scope">
          {{ scope.row.keyword || '-' }}
        </template>
      </el-table-column>

      <el-table-column key="businessData" label="上游业务数据" min-width="180">
        <template #default="scope">
          <json-preview-cell
            :content="scope.row.businessData"
            @click="jsonViewer.open($event, '上游业务数据')"
          />
        </template>
      </el-table-column>

      <el-table-column key="paramBeforeInvoke" label="接口调用入参" min-width="180">
        <template #default="scope">
          <json-preview-cell
            :content="scope.row.paramBeforeInvoke"
            @click="jsonViewer.open($event, '接口调用入参')"
          />
        </template>
      </el-table-column>

      <el-table-column key="remoteInvokeResponse" label="接口原始响应" min-width="180">
        <template #default="scope">
          <json-preview-cell
            :content="scope.row.remoteInvokeResponse"
            @click="jsonViewer.open($event, '接口原始响应')"
          />
        </template>
      </el-table-column>

      <el-table-column key="remoteInvokeResponseParsed" label="接口原始响应解析" min-width="180" v-if="debugMode">
        <template #default="scope">
          <json-preview-cell
            :content="scope.row.remoteInvokeResponseParsed"
            @click="jsonViewer.open($event, '接口原始响应解析')"
          />
        </template>
      </el-table-column>

      <el-table-column key="paramAfterInvoke" label="响应上游出参" min-width="180">
        <template #default="scope">
          <json-preview-cell
            :content="scope.row.paramAfterInvoke"
            @click="jsonViewer.open($event, '响应上游出参')"
          />
        </template>
      </el-table-column>

      <el-table-column key="invokeTime" label="调用耗时" min-width="100">
        <template #default="scope">
          {{ scope.row.invokeTime }} ms
        </template>
      </el-table-column>

      <el-table-column key="createTime" label="接口调用时间" min-width="180">
        <template #default="scope">
          {{ formatDateTime(scope.row.createTime) }}
        </template>
      </el-table-column>

      <el-table-column key="errorMessage" label="异常信息" min-width="200" v-if="debugMode">
        <template #default="scope">
          <span v-if="scope.row.errorMessage" class="error-text" @click="jsonViewer.open(scope.row.errorMessage, '异常信息')">
            {{ scope.row.errorMessage }}
          </span>
          <span v-else style="color: #67C23A;">成功</span>
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
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue';
import { fetchLogList, fetchNodeList } from '@/api/invokeLog';
import { usePagination } from '@/composables/usePagination';
import { useTableLoading } from '@/composables/useTableLoading';
import { useJsonViewer } from '@/composables/useJsonViewer';
import JsonViewerDialog from '@/components/common/JsonViewerDialog.vue';
import JsonPreviewCell from '@/components/common/JsonPreviewCell.vue';

export default {
  name: 'InvokeLog',
  components: {
    JsonViewerDialog,
    JsonPreviewCell
  },
  setup() {
    const searchForm = reactive({
      nodeName: '',
      keyword: ''
    });

    const logList = ref([]);
    const nodeOptions = ref([]);
    const nodeSelectRef = ref(null);
    const debugMode = ref(false);

    const { loading, withLoading } = useTableLoading();
    const jsonViewer = useJsonViewer();

    const fetchData = async () => {
      await withLoading(async () => {
        const params = {
          nodeName: searchForm.nodeName || undefined,
          keyword: searchForm.keyword || undefined,
          pageNum: pagination.pageNum.value,
          pageSize: pagination.pageSize.value
        };
        const response = await fetchLogList(params);
        if (response.status === 200 && response.data.code === '200') {
          const pageList = response.data.rsp;
          logList.value = pageList.rows || [];
          pagination.total.value = pageList.total || 0;
        }
      });
    };

    const pagination = usePagination({ fetchData });

    const loadNodeList = async () => {
      try {
        const response = await fetchNodeList();
        if (response.status === 200 && response.data.code === '200') {
          nodeOptions.value = response.data.rsp || [];
          await fetchData();
        }
      } catch (error) {
        console.error('获取节点列表失败:', error);
      }
    };

    const handleSearch = () => {
      pagination.pageNum.value = 1;
      fetchData();
    };

    const handleReset = () => {
      searchForm.nodeName = '';
      searchForm.keyword = '';
      pagination.pageNum.value = 1;
      fetchData();
    };

    const handleNodeBlur = () => {
      // 当输入框失焦时，如果有输入内容但没有选中选项，自动确认输入值
      const inputValue = nodeSelectRef.value?.$refs?.input?.value || nodeSelectRef.value?.$refs?.reference?.$refs?.input?.value;
      if (inputValue && inputValue !== searchForm.nodeName) {
        searchForm.nodeName = inputValue;
      }
    };

    const formatDateTime = (timestamp) => {
      if (!timestamp) return '';
      const date = new Date(timestamp);
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      const milliseconds = String(date.getMilliseconds()).padStart(3, '0');

      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}.${milliseconds}`;
    };

    onMounted(() => {
      loadNodeList();
    });

    return {
      searchForm,
      logList,
      nodeOptions,
      nodeSelectRef,
      loading,
      pagination,
      jsonViewer,
      debugMode,
      handleSearch,
      handleReset,
      handleNodeBlur,
      formatDateTime
    };
  }
};
</script>

<style scoped>
.invoke-log {
  padding: 20px;
}

.invoke-log h2 {
  margin-bottom: 20px;
  color: #333;
  font-size: 18px;
}

.search-form {
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 20px;
}

.el-table {
  margin-top: 10px;
  border: 1px solid #ebeef5;
}

.el-table .cell {
  padding: 12px 10px;
}

.el-select {
  width: 240px;
}

.error-text {
  color: #F56C6C;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  cursor: pointer;
}
</style>
