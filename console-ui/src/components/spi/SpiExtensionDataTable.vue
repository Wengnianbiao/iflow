<template>
  <el-table :data="data" style="width: 100%; margin-bottom: 20px" v-loading="loading">
    <template slot="empty">
      <div style="padding: 30px 0;">
        <p style="color: #909399; margin: 0;">暂无扩展配置，点击「新增扩展」开始创建</p>
      </div>
    </template>
    <el-table-column label="扩展类型" width="140">
      <template slot-scope="scope">
        <el-tag :type="getExtensionTypeTag(scope.row.spiInterface)" size="small">
          {{ getExtensionTypeName(scope.row.spiInterface) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="关联对象" min-width="140" show-overflow-tooltip>
      <template slot-scope="scope">
        <div class="association-cell">
          <div class="node-tag-list">
            <template v-if="associationList(scope.row).length === 0">
              <el-tag
                size="small"
                type="info"
                effect="plain"
                class="node-tag node-tag-empty"
              >
                -
              </el-tag>
            </template>
            <template v-else>
              <el-tag
                v-for="(label, idx) in associationList(scope.row)"
                :key="idx"
                size="small"
                type="info"
                effect="plain"
                class="node-tag"
                :title="label"
              >
                {{ label }}
              </el-tag>
            </template>
          </div>
        </div>
      </template>
    </el-table-column>
    <el-table-column prop="extensionName" label="扩展名称" width="150" />
    <el-table-column prop="className" label="实现类" min-width="200" show-overflow-tooltip />
    <el-table-column label="扩展方式" width="100" align="center">
      <template slot-scope="scope">
        <el-tag :type="scope.row.extensionMode === 'ONLINE' ? 'success' : 'primary'" size="small">
          {{ scope.row.extensionMode === 'ONLINE' ? '在线扩展' : '项目扩展' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="description" label="描述" min-width="60" show-overflow-tooltip />
    <el-table-column prop="enabled" label="状态" width="80" align="center">
      <template slot-scope="scope">
        <el-tag :type="scope.row.enabled === 1 ? 'success' : 'info'" size="small">
          {{ scope.row.enabled === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="220" fixed="right">
      <template slot-scope="scope">
        <el-button size="mini" @click="$emit('edit', scope.row)">编辑</el-button>
        <el-button
          v-if="scope.row.enabled === 1"
          size="mini"
          type="danger"
          @click="$emit('toggle-enabled', scope.row)"
        >禁用</el-button>
        <el-button
          v-else
          size="mini"
          type="success"
          @click="$emit('toggle-enabled', scope.row)"
        >启用</el-button>
        <el-button size="mini" type="danger" @click="$emit('delete', scope.row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script>
import { getExtensionTypeName, getExtensionTypeTag } from '@/utils/spiExtensionMetadata';

export default {
  name: 'SpiExtensionDataTable',
  props: {
    data: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    /** @returns {string[]} 列表单元展示用标签（节点名或工作流名+URI 等） */
    associatedTags: { type: Function, required: true }
  },
  methods: {
    getExtensionTypeName,
    getExtensionTypeTag,
    associationList(row) {
      const tags = this.associatedTags(row);
      return Array.isArray(tags) ? tags : [];
    }
  }
};
</script>

<style scoped>
/* 限制在单元格内，避免长文案撑破表格布局（flex 子项需 min-width:0 才能收缩） */
.association-cell {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.node-tag-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 28px;
  min-width: 0;
  width: 100%;
}

.node-tag {
  max-width: 100%;
  box-sizing: border-box;
}

/* el-tag 默认 inline，长文本不截断；限制为块级并省略 */
.node-tag.el-tag {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.node-tag-empty {
  max-width: none;
  white-space: normal;
  color: #b0b8c6;
}

.node-tag-empty.node-tag.el-tag {
  display: inline-block;
}
</style>
