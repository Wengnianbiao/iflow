<template>
  <div class="mapping-node" :class="{ 'has-children': hasChildren, 'is-expanded': isExpanded }">
    <!-- 映射卡片 -->
    <div class="node-card" :style="{ paddingLeft: level * 16 + 'px' }">
      <!-- 树形连接线 -->
      <div v-if="level > 0" class="tree-connector"></div>
      
      <!-- 紧凑型卡片 -->
      <div 
        class="compact-card" 
        :class="{ 'has-children': hasChildren, 'clickable': hasChildren }"
        @click="handleClick"
      >
        <!-- 左侧源参数区域 -->
        <div class="source-section">
          <div class="param-info">
            <i :class="getTypeIcon(node.sourceParamType)" class="type-icon" :style="{color: getTypeColor(node.sourceParamType)}"></i>
            <span class="param-name">{{ node.sourceParamKey || '(无源参数)' }}</span>
          </div>
          <el-tag size="mini" :type="getTypeTagType(node.sourceParamType)" class="type-tag">
            {{ node.sourceParamType }}
          </el-tag>
        </div>

        <!-- 中间映射区域 -->
        <div class="mapping-section">
          <div class="mapping-badge">
            <el-tooltip placement="top" effect="dark">
              <div slot="content" class="tooltip-content">
                <div class="tooltip-item"><span class="label">映射类型:</span> {{ getMappingTypeLabel(node.mappingType) }}</div>
                <div v-if="node.mappingRule" class="tooltip-item">
                  <span class="label">映射规则:</span>
                  <pre class="rule-pre">{{ node.mappingRule }}</pre>
                </div>
                <div v-if="node.paramDesc" class="tooltip-item"><span class="label">描述:</span> {{ node.paramDesc }}</div>
                <div v-if="node.mappingSource" class="tooltip-item">
                  <span class="label">数据源:</span> {{ node.mappingSource === 'INPUT' ? '原始入参' : '下游响应' }}
                </div>
              </div>
              <el-tag 
                size="mini" 
                :type="getMappingTypeTagType(node.mappingType)"
                class="mapping-tag"
              >
                {{ getMappingTypeLabel(node.mappingType) }}
              </el-tag>
            </el-tooltip>
          </div>
          <div class="arrow-container">
            <div class="arrow-line"></div>
          </div>
        </div>

        <!-- 右侧目标参数区域 -->
        <div class="target-section">
          <div class="param-info">
            <i :class="getTypeIcon(node.targetParamType)" class="type-icon" :style="{color: getTypeColor(node.targetParamType)}"></i>
            <span class="param-name">{{ node.targetParamKey }}</span>
            <el-badge v-if="hasChildren" :value="node.children.length" class="children-badge" type="info"></el-badge>
          </div>
          <div class="param-meta">
            <el-tag size="mini" :type="getTypeTagType(node.targetParamType)" class="type-tag">
              {{ node.targetParamType }}
            </el-tag>
            <el-tag v-if="processType === 'POST_PROCESS' && node.mappingSource" size="mini" type="info" class="mapping-source-tag">
              {{ node.mappingSource === 'INPUT' ? '原始入参' : '下游响应' }}
            </el-tag>
            <span v-if="node.paramDesc" class="param-desc">{{ node.paramDesc }}</span>
          </div>
        </div>

        <!-- 右侧空白区域，用于填充 -->
        <div class="spacer"></div>

        <!-- 操作按钮区域 - 右对齐 -->
        <div class="action-buttons">
          <el-tooltip content="复制" placement="top">
            <el-button 
              type="info" 
              size="mini" 
              circle 
              icon="el-icon-document-copy"
              @click.stop="handleCopy"
            ></el-button>
          </el-tooltip>
          <el-tooltip content="编辑" placement="top">
            <el-button 
              type="primary" 
              size="mini" 
              circle 
              icon="el-icon-edit"
              @click.stop="handleEdit"
            ></el-button>
          </el-tooltip>
          <el-tooltip content="添加子节点" placement="top" v-if="canAddChild">
            <el-button 
              type="success" 
              size="mini" 
              circle 
              icon="el-icon-plus"
              @click.stop="handleAddChild"
            ></el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button 
              type="danger" 
              size="mini" 
              circle 
              icon="el-icon-delete"
              @click.stop="handleDelete"
            ></el-button>
          </el-tooltip>
        </div>

        <!-- 展开/折叠指示器 -->
        <div v-if="hasChildren" class="expand-indicator">
          <i :class="isExpanded ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" class="expand-icon"></i>
        </div>
      </div>
    </div>

    <!-- 子节点容器 -->
    <transition name="expand">
      <div v-if="hasChildren && isExpanded" class="children-wrapper">
        <mapping-node
          v-for="(child, index) in node.children"
          :key="child.configId || index"
          :node="child"
          :level="level + 1"
          :expanded-nodes="expandedNodes"
          :process-type="processType"
          @toggle-expand="handleToggleExpand"
          @edit-node="handleEditNode"
          @copy-node="handleCopyNode"
          @delete-node="handleDeleteNode"
          @add-child="handleAddChildNode"
          :get-type-icon="getTypeIcon"
          :get-type-color="getTypeColor"
          :get-type-tag-type="getTypeTagType"
          :get-mapping-type-label="getMappingTypeLabel"
          :get-mapping-type-tag-type="getMappingTypeTagType"
        />
      </div>
    </transition>
  </div>
</template>

<script>
export default {
  name: 'MappingNode',
  props: {
    node: {
      type: Object,
      required: true
    },
    level: {
      type: Number,
      default: 0
    },
    expandedNodes: {
      type: Set,
      default: () => new Set()
    },
    processType: {
      type: String,
      default: 'PRE_PROCESS'
    },
    getTypeIcon: {
      type: Function,
      required: true
    },
    getTypeColor: {
      type: Function,
      required: true
    },
    getTypeTagType: {
      type: Function,
      required: true
    },
    getMappingTypeLabel: {
      type: Function,
      required: true
    },
    getMappingTypeTagType: {
      type: Function,
      required: true
    }
  },
  computed: {
    hasChildren() {
      return this.node.children && this.node.children.length > 0;
    },
    isExpanded() {
      return this.expandedNodes.has(this.node.configId);
    },
    canAddChild() {
      // 只有 OBJECT 和 ARRAY 类型可以添加子节点
      return ['OBJECT', 'ARRAY', 'PURE_ARRAY'].includes(this.node.targetParamType);
    }
  },
  methods: {
    handleClick() {
      // 单击展开/折叠子节点（仅当有子节点时）
      if (this.hasChildren) {
        this.handleToggle();
      }
    },
    handleToggle() {
      this.$emit('toggle-expand', this.node.configId);
    },
    handleToggleExpand(configId) {
      this.$emit('toggle-expand', configId);
    },
    handleEdit() {
      this.$emit('edit-node', this.node);
    },
    handleCopy() {
      this.$emit('copy-node', this.node);
    },
    handleDelete() {
      this.$emit('delete-node', this.node);
    },
    handleAddChild() {
      this.$emit('add-child', this.node);
    },
    // 向上传递子节点事件
    handleEditNode(node) {
      this.$emit('edit-node', node);
    },
    handleCopyNode(node) {
      this.$emit('copy-node', node);
    },
    handleDeleteNode(node) {
      this.$emit('delete-node', node);
    },
    handleAddChildNode(node) {
      this.$emit('add-child', node);
    }
  }
};
</script>

<style scoped>
.mapping-node {
  position: relative;
  margin-bottom: 6px;
}

.node-card {
  position: relative;
  padding-left: 0;
}

.tree-connector {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 2px;
  background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
  z-index: 0;
}

.tree-connector::before {
  content: '';
  position: absolute;
  left: -3px;
  top: 20px;
  width: 8px;
  height: 2px;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
}

.compact-card {
  position: relative;
  display: flex;
  align-items: center;
  background: white;
  border-radius: 6px;
  padding: 8px 10px 8px 60px;
  gap: 10px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
  transition: all 0.2s ease;
  z-index: 1;
  border: 1px solid #e9ecef;
  width: 100%;
  max-width: 1200px;
}

.compact-card.clickable {
  cursor: pointer;
}

.compact-card:hover {
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);
  transform: translateY(-1px);
  border-color: #667eea;
}

.compact-card.has-children {
  border-left: 3px solid #667eea;
}

/* 源参数区域 */
.source-section {
  flex: 0 0 150px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 8px;
  background: linear-gradient(135deg, #f0f4ff 0%, #e8f0ff 100%);
  border-radius: 4px;
  border: 1px solid #d0e0ff;
}

.param-info {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.type-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.param-name {
  font-size: 12px;
  font-weight: 600;
  color: #2c3e50;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.type-tag {
  font-size: 10px;
  height: 18px;
  line-height: 18px;
  padding: 0 6px;
}

/* 映射区域 */
.mapping-section {
  flex: 0 0 90px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  position: relative;
}

.mapping-badge {
  z-index: 2;
}

.mapping-tag {
  font-size: 10px;
  height: 20px;
  line-height: 20px;
  padding: 0 8px;
  cursor: pointer;
  font-weight: 500;
}

.arrow-container {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.arrow-line {
  flex: 1;
  height: 2px;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  position: relative;
}

.arrow-line::after {
  content: '';
  position: absolute;
  right: -6px;
  top: 50%;
  transform: translateY(-50%);
  width: 0;
  height: 0;
  border-left: 6px solid #764ba2;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}

/* 目标参数区域 */
.target-section {
  flex: 1;
  min-width: 0;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 8px;
  background: linear-gradient(135deg, #fff5f5 0%, #ffe8e8 100%);
  border-radius: 4px;
  border: 1px solid #ffd0d0;
}

.target-section .param-info {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  width: 100%;
}

.target-section .param-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  word-break: break-all;
}

.target-section .param-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  width: 100%;
}

.mapping-source-tag {
  font-size: 9px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
}

.target-section .param-desc {
  font-size: 10px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.children-badge {
  margin-left: 4px;
}

/* 空白占位区域 */
.spacer {
  flex: 1;
  min-width: 20px;
}

/* 操作按钮区域 - 右对齐 */
.action-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
  flex-shrink: 0;
  margin-left: auto;
}

.compact-card:hover .action-buttons {
  opacity: 1;
}

.action-buttons .el-button {
  width: 28px;
  height: 28px;
  padding: 0;
  font-size: 14px;
}

.action-buttons .el-button--primary {
  background: #409EFF;
  border-color: #409EFF;
}

.action-buttons .el-button--success {
  background: #67C23A;
  border-color: #67C23A;
}

.action-buttons .el-button--danger {
  background: #F56C6C;
  border-color: #F56C6C;
}

.action-buttons .el-button--info {
  background: #909399;
  border-color: #909399;
}

/* 展开/折叠指示器 */
.expand-indicator {
  flex: 0 0 20px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-left: 8px;
}

.expand-icon {
  font-size: 14px;
  color: #667eea;
  transition: transform 0.2s;
  font-weight: bold;
}

.is-expanded .expand-icon {
  transform: rotate(0deg);
}

/* 展开控制 - 保留旧样式兼容 */
.expand-control {
  flex: 0 0 22px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.3);
}

.expand-control:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.expand-icon {
  font-size: 12px;
  color: white;
  transition: transform 0.2s;
}

.is-expanded .expand-icon {
  transform: rotate(90deg);
}

/* 子节点容器 */
.children-wrapper {
  margin-left: 16px;
  margin-top: 4px;
  padding-left: 12px;
  border-left: 2px dashed #d0e0ff;
}

/* 过渡动画 */
.expand-enter-active {
  transition: all 0.25s ease;
  overflow: hidden;
}

.expand-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}

.expand-enter {
  opacity: 0;
  max-height: 0;
  transform: translateY(-10px);
}

.expand-leave-to {
  opacity: 0;
  max-height: 0;
  transform: translateY(-10px);
}

.expand-enter-to,
.expand-leave {
  opacity: 1;
  max-height: 2000px;
  transform: translateY(0);
}

/* Tooltip样式 */
.tooltip-content {
  max-width: 350px;
  font-size: 12px;
}

.tooltip-item {
  margin-bottom: 8px;
}

.tooltip-item:last-child {
  margin-bottom: 0;
}

.tooltip-item .label {
  font-weight: 600;
  color: #409EFF;
  margin-right: 6px;
}

.rule-pre {
  background: rgba(0, 0, 0, 0.05);
  padding: 6px;
  border-radius: 4px;
  margin-top: 4px;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 响应式优化 */
@media (max-width: 1600px) {
  .compact-card {
    max-width: 1300px;
  }
  
  .source-section {
    flex: 0 0 140px;
  }
  
  .mapping-section {
    flex: 0 0 85px;
  }
}

@media (max-width: 1200px) {
  .compact-card {
    flex-wrap: wrap;
    padding: 12px;
  }
  
  .source-section,
  .target-section {
    flex: 1 1 calc(50% - 6px);
  }
  
  .mapping-section {
    flex: 1 1 100%;
    order: 3;
    margin-top: 8px;
  }
  
  .expand-control {
    position: absolute;
    top: 8px;
    right: 8px;
  }
}
</style>
