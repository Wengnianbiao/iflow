<template>
  <el-dialog
    title="选择扩展类型"
    :visible.sync="dialogVisible"
    width="500px"
    :close-on-click-modal="false"
  >
    <div class="type-selector">
      <el-card
        v-if="!isFilterPage && !isWorkflowPage"
        class="type-card"
        shadow="hover"
        @click.native="emitSelect('InterfaceRemoteInvoker')"
      >
        <div class="type-icon">
          <i class="el-icon-connection" style="font-size: 48px; color: #409EFF;"></i>
        </div>
        <h3>工作流节点扩展</h3>
      </el-card>

      <el-card
        v-if="!isNodePage && !isWorkflowPage"
        class="type-card"
        shadow="hover"
        @click.native="emitSelect('AgentFilter')"
      >
        <div class="type-icon">
          <i class="el-icon-s-operation" style="font-size: 48px; color: #67C23A;"></i>
        </div>
        <h3>过滤器扩展</h3>
      </el-card>

      <el-card
        v-if="!isNodePage && !isFilterPage"
        class="type-card"
        shadow="hover"
        @click.native="emitSelect('OpenApiRouteHandler')"
      >
        <div class="type-icon">
          <i class="el-icon-share" style="font-size: 48px; color: #E6A23C;"></i>
        </div>
        <h3>Open API工作流扩展</h3>
      </el-card>

      <el-card
        v-if="!isNodePage && !isFilterPage"
        class="type-card"
        shadow="hover"
        @click.native="emitSelect('JarvisRouteHandler')"
      >
        <div class="type-icon">
          <i class="el-icon-chat-line-square" style="font-size: 48px; color: #F56C6C;"></i>
        </div>
        <h3>Jarvis工作流扩展</h3>
      </el-card>
    </div>
  </el-dialog>
</template>

<script>
export default {
  name: 'ExtensionTypeSelectorDialog',
  props: {
    visible: { type: Boolean, default: false },
    isFilterPage: { type: Boolean, default: false },
    isNodePage: { type: Boolean, default: false },
    isWorkflowPage: { type: Boolean, default: false }
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
  methods: {
    emitSelect(type) {
      this.$emit('select', type);
    }
  }
};
</script>

<style scoped>
.type-selector {
  display: flex;
  justify-content: space-around;
  gap: 20px;
  padding: 20px 0;
}

.type-card {
  flex: 1;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}

.type-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.type-icon {
  margin-bottom: 15px;
}

.type-card h3 {
  margin: 10px 0;
  font-size: 18px;
  color: #303133;
}

.type-desc {
  color: #909399;
  font-size: 12px;
  margin-bottom: 15px;
}

.type-features {
  list-style: none;
  padding: 0;
  margin: 0;
  text-align: left;
}

.type-features li {
  padding: 5px 0;
  color: #606266;
  font-size: 14px;
}

.type-features li i {
  color: #67C23A;
  margin-right: 5px;
}
</style>
