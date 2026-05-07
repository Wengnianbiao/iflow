<template>
  <el-form :inline="true" class="spi-extension-toolbar">
    <el-form-item>
      <el-button type="primary" icon="el-icon-plus" @click="$emit('add')">新增扩展</el-button>
      <el-button icon="el-icon-refresh" @click="$emit('refresh')">刷新列表</el-button>
    </el-form-item>
    <el-form-item>
      <el-input
        :value="searchKeyword"
        placeholder="搜索扩展名称 / 实现类"
        prefix-icon="el-icon-search"
        clearable
        style="width: 240px;"
        @input="$emit('update:searchKeyword', $event)"
      />
    </el-form-item>
  </el-form>
</template>

<script>
export default {
  name: 'SpiExtensionToolbar',
  props: {
    searchKeyword: { type: String, default: '' }
  }
};
</script>

<style scoped>
.spi-extension-toolbar {
  margin-bottom: 15px;
}
</style>
