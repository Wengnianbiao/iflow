<template>
  <div class="extension-manager">
    <!-- 工具栏 -->
    <el-form :inline="true" class="toolbar">
      <el-form-item>
        <el-button type="primary" @click="showTypeSelector" icon="el-icon-plus">新增扩展</el-button>
        <el-button @click="loadExtensionList" icon="el-icon-refresh">刷新列表</el-button>
      </el-form-item>
      <el-form-item label="扩展类型筛选">
        <el-select v-model="filterType" placeholder="全部" @change="loadExtensionList" clearable>
          <el-option label="接口扩展" value="InterfaceRemoteInvoker"></el-option>
          <el-option label="过滤器扩展" value="AgentFilter"></el-option>
        </el-select>
      </el-form-item>
    </el-form>

    <!-- SPI扩展列表 -->
    <el-table
      :data="extensionList"
      border
      style="width: 100%; margin-bottom: 20px"
      v-loading="tableLoading"
    >
      <el-table-column prop="codeId" label="ID" width="80"></el-table-column>
      <el-table-column label="扩展类型" width="120">
        <template slot-scope="scope">
          <el-tag :type="getExtensionTypeTag(scope.row.spiInterface)" size="small">
            {{ getExtensionTypeName(scope.row.spiInterface) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联节点" min-width="200">
        <template slot-scope="scope">
          <span v-if="!Array.isArray(scope.row.nodeId) || scope.row.nodeId.length === 0">-</span>
          <span v-else-if="!Array.isArray(scope.row.nodeId) || scope.row.nodeId.length === 1">
            {{ getNodeName(scope.row.nodeId) }}
          </span>
          <div v-else style="display: flex; flex-wrap: wrap; gap: 5px;">
            <el-tag
              v-for="id in scope.row.nodeId"
              :key="id"
              size="small"
              type="info"
            >
              {{ getNodeNameById(id) }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="extensionName" label="扩展名称" width="150"></el-table-column>
      <el-table-column prop="className" label="实现类" min-width="200" show-overflow-tooltip></el-table-column>
      <el-table-column label="扩展方式" width="100" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.extensionMode === 'ONLINE' ? 'success' : 'primary'" size="small">
            {{ scope.row.extensionMode === 'ONLINE' ? '在线扩展' : '项目扩展' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip></el-table-column>
      <el-table-column prop="enabled" label="状态" width="80" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.enabled === 1 ? 'success' : 'info'" size="small">
            {{ scope.row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" @click="handleEdit(scope.row)" icon="el-icon-edit">编辑</el-button>
          <el-button
            v-if="scope.row.enabled === 1"
            size="mini"
            type="danger"
            @click="handleDisable(scope.row)"
            icon="el-icon-close"
          >禁用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 扩展类型选择对话框 -->
    <el-dialog
      title="选择扩展类型"
      :visible.sync="typeSelectorVisible"
      width="500px"
      :close-on-click-modal="false"
    >
      <div class="type-selector">
        <el-card 
          class="type-card" 
          shadow="hover"
          @click.native="selectExtensionType('InterfaceRemoteInvoker')"
        >
          <div class="type-icon">
            <i class="el-icon-connection" style="font-size: 48px; color: #409EFF;"></i>
          </div>
          <h3>接口扩展</h3>
          <p class="type-desc">InterfaceRemoteInvoker</p>
          <ul class="type-features">
            <li><i class="el-icon-check"></i> 单节点绑定</li>
            <li><i class="el-icon-check"></i> 自定义远程调用逻辑</li>
            <li><i class="el-icon-check"></i> 更新 remote_extension 字段</li>
          </ul>
        </el-card>

        <el-card 
          class="type-card" 
          shadow="hover"
          @click.native="selectExtensionType('AgentFilter')"
        >
          <div class="type-icon">
            <i class="el-icon-s-operation" style="font-size: 48px; color: #67C23A;"></i>
          </div>
          <h3>过滤器扩展</h3>
          <p class="type-desc">AgentFilter</p>
          <ul class="type-features">
            <li><i class="el-icon-check"></i> 多节点绑定</li>
            <li><i class="el-icon-check"></i> 请求/响应拦截处理</li>
            <li><i class="el-icon-check"></i> 更新 extension_param 字段</li>
          </ul>
        </el-card>
      </div>
    </el-dialog>

    <!-- 接口扩展表单对话框 -->
    <el-dialog
      :title="dialogTitle"
      :visible.sync="interfaceExtensionDialogVisible"
      width="80%"
      top="5vh"
      :close-on-click-modal="false"
    >
      <el-form :model="interfaceExtensionForm" label-width="120px" ref="interfaceExtensionForm" :rules="interfaceExtensionRules">
        <el-alert
          title="接口扩展（InterfaceRemoteInvoker）"
          type="info"
          :closable="false"
          style="margin-bottom: 20px;"
        >
          <p>功能：自定义远程调用逻辑</p>
          <p>特性：单节点绑定，自动更新节点的 remote_extension 字段</p>
        </el-alert>

        <el-form-item label="关联节点" prop="nodeId" required>
          <el-select
            v-model="interfaceExtensionForm.nodeId"
            placeholder="请选择要关联的节点（单选）"
            style="width: 100%"
            filterable
          >
            <el-option
              v-for="node in nodeList"
              :key="node.nodeId"
              :label="node.nodeName"
              :value="node.nodeId"
            ></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="扩展名称" prop="extensionName">
          <el-input
            v-model="interfaceExtensionForm.extensionName"
            placeholder="例如: pacs"
          ></el-input>
        </el-form-item>

        <el-form-item label="实现类全名" prop="className">
          <el-input
            v-model="interfaceExtensionForm.className"
            placeholder="例如: com.helianhealth.agent.custom.MyRemoteInvoker"
          ></el-input>
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="interfaceExtensionForm.description"
            type="textarea"
            :rows="2"
            placeholder="请输入扩展描述"
          ></el-input>
        </el-form-item>

        <el-form-item label="扩展方式" prop="extensionMode" required>
          <el-radio-group v-model="interfaceExtensionForm.extensionMode">
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

        <el-form-item label="Java源代码" prop="sourceCode" v-if="interfaceExtensionForm.extensionMode === 'ONLINE'">
          <el-input
            v-model="interfaceExtensionForm.sourceCode"
            type="textarea"
            :rows="20"
            placeholder="请输入Java源代码..."
            style="font-family: 'Consolas', 'Monaco', 'Courier New', monospace;"
          ></el-input>
          <div class="code-toolbar">
            <el-button size="mini" @click="applyTemplate('interfaceRemoteInvoker')" icon="el-icon-document">使用模板</el-button>
          </div>
        </el-form-item>

        <el-form-item label="状态" prop="enabled">
          <el-switch
            v-model="interfaceExtensionForm.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          ></el-switch>
        </el-form-item>
      </el-form>

      <div slot="footer">
        <el-button @click="interfaceExtensionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveInterfaceExtension" :loading="saveLoading">保存</el-button>
      </div>
    </el-dialog>

    <!-- 过滤器扩展表单对话框 -->
    <el-dialog
      :title="dialogTitle"
      :visible.sync="filterExtensionDialogVisible"
      width="80%"
      top="5vh"
      :close-on-click-modal="false"
    >
      <el-form :model="filterExtensionForm" label-width="120px" ref="filterExtensionForm" :rules="filterExtensionRules">
        <el-alert
          title="过滤器扩展（AgentFilter）"
          type="success"
          :closable="false"
          style="margin-bottom: 20px;"
        >
          <p>功能：实现请求/响应拦截处理逻辑</p>
          <p>特性：多节点绑定，自动更新节点的 extension_param 字段（key: "filter.{extensionName}", value: "true"）</p>
        </el-alert>

        <el-form-item label="关联节点" prop="nodeId" required>
          <el-select
            v-model="filterExtensionForm.nodeId"
            placeholder="请选择要关联的节点（可多选）"
            style="width: 100%"
            filterable
            multiple
            collapse-tags
          >
            <el-option
              v-for="node in nodeList"
              :key="node.nodeId"
              :label="node.nodeName"
              :value="node.nodeId"
            ></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="扩展名称" prop="extensionName">
          <el-input
            v-model="filterExtensionForm.extensionName"
            placeholder="例如: header"
          ></el-input>
        </el-form-item>

        <el-form-item label="实现类全名" prop="className">
          <el-input
            v-model="filterExtensionForm.className"
            placeholder="例如: com.helianhealth.agent.custom.HeaderFilter"
          ></el-input>
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="filterExtensionForm.description"
            type="textarea"
            :rows="2"
            placeholder="请输入扩展描述"
          ></el-input>
        </el-form-item>

        <el-form-item label="扩展方式" prop="extensionMode" required>
          <el-radio-group v-model="filterExtensionForm.extensionMode">
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

        <el-form-item label="Java源代码" prop="sourceCode" v-if="filterExtensionForm.extensionMode === 'ONLINE'">
          <el-input
            v-model="filterExtensionForm.sourceCode"
            type="textarea"
            :rows="20"
            placeholder="请输入Java源代码..."
            style="font-family: 'Consolas', 'Monaco', 'Courier New', monospace;"
          ></el-input>
          <div class="code-toolbar">
            <el-button size="mini" @click="applyTemplate('agentFilter')" icon="el-icon-document">使用模板</el-button>
          </div>
        </el-form-item>

        <el-form-item label="状态" prop="enabled">
          <el-switch
            v-model="filterExtensionForm.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          ></el-switch>
        </el-form-item>
      </el-form>

      <div slot="footer">
        <el-button @click="filterExtensionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveFilterExtension" :loading="saveLoading">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { Message, MessageBox } from 'element-ui';
import {
  getInterfaceExtensions,
  registerInterfaceExtension,
  updateInterfaceExtension,
  getFilterExtensions,
  registerFilterExtension,
  updateFilterExtension,
  disableExtension
} from '@/api/extension';
import { getAllNodes } from '@/api/workflow';

export default {
  name: 'NodeExtensionConfig',
  data() {
    return {
      // 列表数据
      extensionList: [],
      nodeList: [],
      filterType: '',
      tableLoading: false,

      // 类型选择对话框
      typeSelectorVisible: false,

      // 接口扩展对话框
      interfaceExtensionDialogVisible: false,
      interfaceExtensionForm: {
        codeId: null,
        nodeId: null,
        extensionName: '',
        className: '',
        sourceCode: '',
        description: '',
        enabled: 1,
        extensionMode: 'ONLINE'
      },
      interfaceExtensionRules: {
        nodeId: [{ required: true, message: '请选择关联节点', trigger: 'change' }],
        extensionName: [{ required: true, message: '请输入扩展名称', trigger: 'blur' }],
        className: [{ required: true, message: '请输入实现类全名', trigger: 'blur' }],
        extensionMode: [{ required: true, message: '请选择扩展方式', trigger: 'change' }]
      },

      // 过滤器扩展对话框
      filterExtensionDialogVisible: false,
      filterExtensionForm: {
        codeId: null,
        nodeId: [],
        extensionName: '',
        className: '',
        sourceCode: '',
        description: '',
        enabled: 1,
        extensionMode: 'ONLINE'
      },
      filterExtensionRules: {
        nodeId: [{ required: true, message: '请选择关联节点', trigger: 'change' }],
        extensionName: [{ required: true, message: '请输入扩展名称', trigger: 'blur' }],
        className: [{ required: true, message: '请输入实现类全名', trigger: 'blur' }],
        extensionMode: [{ required: true, message: '请选择扩展方式', trigger: 'change' }]
      },

      // 通用状态
      dialogTitle: '',
      isEdit: false,
      saveLoading: false,

      // 代码模板
      templates: {
        interfaceRemoteInvoker: `package com.helianhealth.agent.extension;

import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowNode;
import java.util.Map;

/**
 * 自定义远程调用实现示例
 */
public class CustomInvoker implements InterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(InterfaceWorkflowNode node, Map<String, Object> businessData) {
        // TODO: 实现自定义调用逻辑
        System.out.println("自定义调用: " + node.getNodeName());
        return businessData;
    }
}`,
        agentFilter: `package com.helianhealth.agent.extension;

import com.helianhealth.agent.invocation.remote.filter.AgentFilter;
import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowNode;
import java.util.Map;

/**
 * 自定义过滤器实现示例
 */
public class CustomFilter implements AgentFilter {

    @Override
    public Map<String, Object> invoke(InterfaceWorkflowNode node, Map<String, Object> businessData) {
        // TODO: 实现请求预处理逻辑
        System.out.println("Filter 前置处理: " + node.getNodeName());
        
        // 调用下一个过滤器或实际调用
        Map<String, Object> result = businessData;
        
        // TODO: 实现响应后处理逻辑
        System.out.println("Filter 后置处理");
        return result;
    }
}`
      }
    };
  },
  mounted() {
    this.loadExtensionList();
    this.loadNodeList();
  },
  methods: {
    // 加载扩展列表（根据筛选条件）
    async loadExtensionList() {
      this.tableLoading = true;
      try {
        let combinedList = [];
        
        if (!this.filterType || this.filterType === 'InterfaceRemoteInvoker') {
          const interfaceResp = await getInterfaceExtensions();
          if (interfaceResp && interfaceResp.data) {
            const result = interfaceResp.data;
            const interfaceList = Array.isArray(result.rsp) ? result.rsp :
                                 Array.isArray(result.data) ? result.data :
                                 Array.isArray(result) ? result : [];
            combinedList = combinedList.concat(interfaceList);
          }
        }
        
        if (!this.filterType || this.filterType === 'AgentFilter') {
          const filterResp = await getFilterExtensions();
          if (filterResp && filterResp.data) {
            const result = filterResp.data;
            const filterList = Array.isArray(result.rsp) ? result.rsp :
                              Array.isArray(result.data) ? result.data :
                              Array.isArray(result) ? result : [];
            combinedList = combinedList.concat(filterList);
          }
        }
        
        this.extensionList = combinedList;
      } catch (error) {
        Message.error('加载扩展列表失败：' + (error.message || '未知错误'));
        this.extensionList = [];
      } finally {
        this.tableLoading = false;
      }
    },

    // 加载节点列表
    async loadNodeList() {
      try {
        const response = await getAllNodes();
        if (response && response.data) {
          const result = response.data;
          this.nodeList = Array.isArray(result.rsp) ? result.rsp :
                          Array.isArray(result.data) ? result.data :
                          Array.isArray(result) ? result : [];
        } else {
          this.nodeList = [];
        }
      } catch (error) {
        this.nodeList = [];
        Message.error('加载节点列表失败，请刷新重试');
      }
    },

    // 显示类型选择器
    showTypeSelector() {
      if (!this.nodeList || this.nodeList.length === 0) {
        this.loadNodeList();
      }
      this.typeSelectorVisible = true;
    },

    // 选择扩展类型
    selectExtensionType(type) {
      this.typeSelectorVisible = false;
      this.isEdit = false;
      
      if (type === 'InterfaceRemoteInvoker') {
        this.dialogTitle = '新增接口扩展';
        this.interfaceExtensionForm = {
          codeId: null,
          nodeId: null,
          extensionName: '',
          className: '',
          sourceCode: '',
          description: '',
          enabled: 1,
          extensionMode: 'ONLINE'
        };
        this.interfaceExtensionDialogVisible = true;
        this.$nextTick(() => {
          if (this.$refs.interfaceExtensionForm) {
            this.$refs.interfaceExtensionForm.clearValidate();
          }
        });
      } else if (type === 'AgentFilter') {
        this.dialogTitle = '新增过滤器扩展';
        this.filterExtensionForm = {
          codeId: null,
          nodeId: [],
          extensionName: '',
          className: '',
          sourceCode: '',
          description: '',
          enabled: 1,
          extensionMode: 'ONLINE'
        };
        this.filterExtensionDialogVisible = true;
        this.$nextTick(() => {
          if (this.$refs.filterExtensionForm) {
            this.$refs.filterExtensionForm.clearValidate();
          }
        });
      }
    },

    // 编辑扩展
    handleEdit(row) {
      this.isEdit = true;
      
      // 处理扩展方式字段兼容
      let mode = row.extensionMode || row.extensionLocation || (row.usePageCode === 1 ? 'ONLINE' : 'PROJECT') || 'ONLINE';
      
      // 处理 nodeId
      let nodeId = row.nodeId;
      if (nodeId && !Array.isArray(nodeId)) {
        try {
          nodeId = typeof nodeId === 'string' ? JSON.parse(nodeId) : [nodeId];
        } catch (e) {
          nodeId = [];
        }
      } else if (!nodeId) {
        nodeId = [];
      }
      
      if (row.spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') {
        this.dialogTitle = '编辑接口扩展';
        this.interfaceExtensionForm = {
          ...row,
          nodeId: Array.isArray(nodeId) && nodeId.length > 0 ? nodeId[0] : null,
          extensionMode: mode
        };
        this.interfaceExtensionDialogVisible = true;
        this.$nextTick(() => {
          if (this.$refs.interfaceExtensionForm) {
            this.$refs.interfaceExtensionForm.clearValidate();
          }
        });
      } else if (row.spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') {
        this.dialogTitle = '编辑过滤器扩展';
        this.filterExtensionForm = {
          ...row,
          nodeId: nodeId,
          extensionMode: mode
        };
        this.filterExtensionDialogVisible = true;
        this.$nextTick(() => {
          if (this.$refs.filterExtensionForm) {
            this.$refs.filterExtensionForm.clearValidate();
          }
        });
      }
    },

    // 保存接口扩展
    async handleSaveInterfaceExtension() {
      try {
        await this.$refs.interfaceExtensionForm.validate();
      } catch (error) {
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
        const submitData = {
          ...this.interfaceExtensionForm,
          nodeId: [this.interfaceExtensionForm.nodeId]  // 转为数组
        };
        
        if (submitData.extensionMode === 'PROJECT') {
          delete submitData.sourceCode;
        }
        
        if (this.isEdit) {
          await updateInterfaceExtension(submitData);
          Message.success('更新成功，已热刷新');
        } else {
          await registerInterfaceExtension(submitData);
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

    // 保存过滤器扩展
    async handleSaveFilterExtension() {
      try {
        await this.$refs.filterExtensionForm.validate();
      } catch (error) {
        return;
      }

      if (!this.filterExtensionForm.extensionMode) {
        Message.error('请选择扩展方式');
        return;
      }

      if (this.filterExtensionForm.extensionMode === 'ONLINE') {
        if (!this.filterExtensionForm.sourceCode || !this.filterExtensionForm.sourceCode.trim()) {
          Message.error('在线扩展模式下，请输入Java源代码');
          return;
        }
      }

      this.saveLoading = true;
      try {
        const submitData = { ...this.filterExtensionForm };
        
        if (submitData.extensionMode === 'PROJECT') {
          delete submitData.sourceCode;
        }
        
        if (this.isEdit) {
          await updateFilterExtension(submitData);
          Message.success('更新成功，已热刷新');
        } else {
          await registerFilterExtension(submitData);
          Message.success('新增成功，已注册到系统');
        }
        this.filterExtensionDialogVisible = false;
        this.loadExtensionList();
      } catch (error) {
        Message.error('保存失败：' + (error.message || '未知错误'));
      } finally {
        this.saveLoading = false;
      }
    },

    // 禁用扩展
    async handleDisable(row) {
      try {
        await MessageBox.confirm(
          `确定要禁用扩展 "${row.extensionName}" 吗？禁用后将从运行时卸载。`,
          '警告',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }
        );

        await disableExtension(row.codeId);
        
        Message.success('禁用成功');
        this.loadExtensionList();
      } catch (error) {
        if (error !== 'cancel') {
          Message.error('禁用失败：' + (error.message || '未知错误'));
        }
      }
    },

    // 根据节点ID获取节点名称
    getNodeName(nodeId) {
      if (!nodeId) return '-';
      
      if (Array.isArray(nodeId)) {
        if (nodeId.length === 0) return '-';
        return nodeId.map(id => {
          const node = this.nodeList.find(n => n.nodeId === id);
          return node ? node.nodeName : `节点${id}`;
        }).join(', ');
      }
      
      const node = this.nodeList.find(n => n.nodeId === nodeId);
      return node ? node.nodeName : `节点${nodeId}`;
    },

    // 根据单个节点ID获取节点名称
    getNodeNameById(id) {
      const node = this.nodeList.find(n => n.nodeId === id);
      return node ? node.nodeName : `节点${id}`;
    },

    // 获取扩展类型名称
    getExtensionTypeName(spiInterface) {
      if (spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') {
        return '接口扩展';
      } else if (spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') {
        return '过滤器扩展';
      }
      return '未知类型';
    },

    // 获取扩展类型标签颜色
    getExtensionTypeTag(spiInterface) {
      if (spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') {
        return 'primary';
      } else if (spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') {
        return 'success';
      }
      return '';
    },

    // 应用模板
    applyTemplate(templateType) {
      const targetForm = templateType === 'interfaceRemoteInvoker' ? 'interfaceExtensionForm' : 'filterExtensionForm';
      
      if (this[targetForm].sourceCode.trim()) {
        MessageBox.confirm('应用模板将覆盖当前代码，是否继续？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this[targetForm].sourceCode = this.templates[templateType];
          Message.success('模板已应用');
        }).catch(() => {});
      } else {
        this[targetForm].sourceCode = this.templates[templateType];
        Message.success('模板已应用');
      }
    }
  }
};
</script>

<style scoped>
.extension-manager {
  padding: 20px;
}

.toolbar {
  margin-bottom: 15px;
}

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

.code-toolbar {
  margin-top: 10px;
  text-align: right;
}
</style>
