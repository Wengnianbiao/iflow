<template>
  <div class="extension-manager">
    <!-- 工具栏 -->
    <el-form :inline="true" class="toolbar">
      <el-form-item>
        <el-button type="primary" @click="showTypeSelector" icon="el-icon-plus">新增扩展</el-button>
        <el-button @click="loadExtensionList" icon="el-icon-refresh">刷新列表</el-button>
      </el-form-item>
      <el-form-item>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索扩展名称 / 实现类"
          prefix-icon="el-icon-search"
          clearable
          style="width: 240px;"
        ></el-input>
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
      :data="filteredExtensionList"
      style="width: 100%; margin-bottom: 20px"
      v-loading="tableLoading"
    >
      <template slot="empty">
        <div style="padding: 30px 0;">
          <p style="color: #909399; margin: 0;">暂无扩展配置，点击「新增扩展」开始创建</p>
        </div>
      </template>
      <el-table-column label="扩展类型" width="120">
        <template slot-scope="scope">
          <el-tag :type="getExtensionTypeTag(scope.row.spiInterface)" size="small">
            {{ getExtensionTypeName(scope.row.spiInterface) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联节点" min-width="120" show-overflow-tooltip>
        <template slot-scope="scope">
          <span v-if="!Array.isArray(scope.row.nodeId) || scope.row.nodeId.length === 0">-</span>
          <span v-else-if="scope.row.nodeId.length === 1">
            {{ getNodeNameById(scope.row.nodeId[0]) }}
          </span>
          <div v-else style="display: flex; flex-wrap: wrap; gap: 5px;">
            <el-tag v-for="id in scope.row.nodeId" :key="id" size="small" type="info">
              {{ getNodeNameById(id) }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="extensionName" label="扩展名称" width="150"></el-table-column>
      <el-table-column prop="className" label="实现类" min-width="200" show-overflow-tooltip></el-table-column>
      <el-table-column label="扩展方式" width="100" align="center">
        <template slot-scope="scope">
          <el-tooltip
            v-if="scope.row.extensionMode === 'ONLINE'"
            content="点击查看源码"
            placement="top"
          >
            <el-tag
              type="success"
              size="small"
              style="cursor:pointer"
              @click="handleViewCode(scope.row)"
            >在线扩展</el-tag>
          </el-tooltip>
          <el-tag v-else type="primary" size="small">项目扩展</el-tag>
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
      <el-table-column label="操作" width="220" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button
            v-if="scope.row.enabled === 1"
            size="mini"
            type="danger"
            @click="handleToggleEnabled(scope.row)"
          >禁用</el-button>
          <el-button
            v-else
            size="mini"
            type="success"
            @click="handleToggleEnabled(scope.row)"
          >启用</el-button>
          <el-button size="mini" type="danger" @click="handleDelete(scope.row)">删除</el-button>
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
        <el-divider content-position="left">基础信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="关联节点" prop="nodeId" required>
              <el-select v-model="interfaceExtensionForm.nodeId" placeholder="请选择要关联的节点（单选）" style="width: 100%" filterable>
                <el-option v-for="node in nodeList" :key="node.nodeId" :label="node.nodeName" :value="node.nodeId"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="扩展名称" prop="extensionName">
              <el-input v-model="interfaceExtensionForm.extensionName" placeholder="例如: pacs"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="实现类全名" prop="className">
              <el-input v-model="interfaceExtensionForm.className" placeholder="例如: com.helianhealth.agent.custom.MyRemoteInvoker"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="描述" prop="description">
              <el-input v-model="interfaceExtensionForm.description" placeholder="请输入扩展描述"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">扩展配置</el-divider>

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
          <div class="code-editor-wrapper">
            <div class="code-line-numbers" ref="interfaceLineNumbers">
              <div v-for="n in getLineCount(interfaceExtensionForm.sourceCode)" :key="n" class="line-number">{{ n }}</div>
            </div>
            <textarea
              class="code-textarea"
              v-model="interfaceExtensionForm.sourceCode"
              placeholder="请输入Java源代码..."
              spellcheck="false"
              @scroll="syncScroll('interface')"
              ref="interfaceCodeArea"
            ></textarea>
          </div>
          <div class="code-toolbar">
            <el-button size="mini" @click="applyTemplate('interfaceRemoteInvoker')" icon="el-icon-document">使用模板</el-button>
          </div>
        </el-form-item>

        <!-- 项目扩展接入指引 -->
        <div v-if="interfaceExtensionForm.extensionMode === 'PROJECT'" style="margin-bottom: 20px;">
          <el-alert title="项目接入指引" type="info" :closable="false">
            <el-steps direction="vertical" :space="50" style="margin-top: 12px;">
              <el-step title="添加依赖" description="在你的业务项目中引入 interface-agent SDK 依赖"></el-step>
              <el-step title="实现接口" description="创建类实现 InterfaceRemoteInvoker 接口，编写 remoteInvoke 方法"></el-step>
              <el-step title="注册到 Spring" description="使用 @Component 注解，确保类名与上方「实现类全名」一致"></el-step>
              <el-step title="部署生效" description="重启应用后，系统将自动发现并加载该扩展"></el-step>
            </el-steps>
          </el-alert>
        </div>
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

        <el-divider content-position="left">基础信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="关联节点" prop="nodeId" required>
              <el-select
                v-model="filterExtensionForm.nodeId"
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
              <el-input v-model="filterExtensionForm.extensionName" placeholder="例如: header"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="实现类全名" prop="className">
              <el-input v-model="filterExtensionForm.className" placeholder="例如: com.helianhealth.agent.custom.HeaderFilter"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="描述" prop="description">
              <el-input v-model="filterExtensionForm.description" placeholder="请输入扩展描述"></el-input>
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
            <div v-for="(param, index) in filterExtensionForm.extensionParamList" :key="index" class="param-item">
              <el-input v-model="param.key" placeholder="参数名（key）" style="width: 40%; margin-right: 10px;"></el-input>
              <el-input v-model="param.value" placeholder="参数值（value）" style="width: 40%; margin-right: 10px;"></el-input>
              <el-button type="danger" icon="el-icon-delete" size="small" circle @click="removeExtensionParam(index)"></el-button>
            </div>
            <el-button type="text" icon="el-icon-plus" @click="addExtensionParam" style="margin-top: 10px;">添加参数</el-button>
          </div>
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
          <div class="code-editor-wrapper">
            <div class="code-line-numbers" ref="filterLineNumbers">
              <div v-for="n in getLineCount(filterExtensionForm.sourceCode)" :key="n" class="line-number">{{ n }}</div>
            </div>
            <textarea
              class="code-textarea"
              v-model="filterExtensionForm.sourceCode"
              placeholder="请输入Java源代码..."
              spellcheck="false"
              @scroll="syncScroll('filter')"
              ref="filterCodeArea"
            ></textarea>
          </div>
          <div class="code-toolbar">
            <el-button size="mini" @click="applyTemplate('agentFilter')" icon="el-icon-document">使用模板</el-button>
          </div>
        </el-form-item>

        <!-- 项目扩展接入指引 -->
        <div v-if="filterExtensionForm.extensionMode === 'PROJECT'" style="margin-bottom: 20px;">
          <el-alert title="项目接入指引" type="success" :closable="false">
            <el-steps direction="vertical" :space="50" style="margin-top: 12px;">
              <el-step title="添加依赖" description="在你的业务项目中引入 interface-agent SDK 依赖"></el-step>
              <el-step title="实现接口" description="创建类实现 AgentFilter 接口，编写 invoke 方法"></el-step>
              <el-step title="注册到 Spring" description="使用 @Component 注解，确保类名与上方「实现类全名」一致"></el-step>
              <el-step title="部署生效" description="重启应用后，系统将自动发现并加载该过滤器扩展"></el-step>
            </el-steps>
          </el-alert>
        </div>
      </el-form>

      <div slot="footer">
        <el-button @click="filterExtensionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveFilterExtension" :loading="saveLoading">保存</el-button>
      </div>
    </el-dialog>

    <!-- 代码预览对话框 -->
    <el-dialog
      :title="codePreviewTitle"
      :visible.sync="codePreviewVisible"
      :width="codePreviewFullscreen ? '100%' : '70%'"
      :top="codePreviewFullscreen ? '0' : '6vh'"
      :fullscreen="codePreviewFullscreen"
      :close-on-click-modal="true"
    >
      <template slot="title">
        <span>{{ codePreviewTitle }}</span>
        <el-button
          size="mini"
          :icon="codePreviewFullscreen ? 'el-icon-copy-document' : 'el-icon-full-screen'"
          circle
          style="margin-left: 12px"
          @click="codePreviewFullscreen = !codePreviewFullscreen"
        ></el-button>
      </template>
      <div class="code-preview-wrapper" :style="codePreviewFullscreen ? 'max-height: calc(100vh - 100px)' : ''">
        <div class="code-line-numbers">
          <div v-for="n in getLineCount(codePreviewContent)" :key="n" class="line-number">{{ n }}</div>
        </div>
        <pre class="code-preview-content"><code v-html="highlightedPreviewCode"></code></pre>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { Message, MessageBox } from 'element-ui';
import hljs from 'highlight.js/lib/core';
import java from 'highlight.js/lib/languages/java';
import 'highlight.js/styles/github.css';
import {
  getInterfaceExtensions,
  registerInterfaceExtension,
  updateInterfaceExtension,
  getFilterExtensions,
  registerFilterExtension,
  updateFilterExtension,
  disableExtension,
  enableExtension,
  deleteExtension,
  getExtensionDetail
} from '@/api/extension';
import { getAllNodes } from '@/api/workflow';

hljs.registerLanguage('java', java);

export default {
  name: 'NodeExtensionConfig',
  data() {
    return {
      extensionList: [],
      nodeList: [],
      filterType: '',
      searchKeyword: '',
      tableLoading: false,

      typeSelectorVisible: false,

      interfaceExtensionDialogVisible: false,
      interfaceExtensionForm: {
        codeId: null, nodeId: null, extensionName: '', className: '',
        sourceCode: '', description: '', enabled: 1, extensionMode: 'ONLINE'
      },
      interfaceExtensionRules: {
        nodeId: [{ required: true, message: '请选择关联节点', trigger: 'change' }],
        extensionName: [{ required: true, message: '请输入扩展名称', trigger: 'blur' }],
        className: [{ required: true, message: '请输入实现类全名', trigger: 'blur' }],
        extensionMode: [{ required: true, message: '请选择扩展方式', trigger: 'change' }]
      },

      filterExtensionDialogVisible: false,
      filterExtensionForm: {
        codeId: null, nodeId: [], extensionName: '', className: '',
        sourceCode: '', description: '', enabled: 1, extensionMode: 'ONLINE',
        extensionParamList: []
      },
      filterExtensionRules: {
        nodeId: [{ required: true, message: '请选择关联节点', trigger: 'change' }],
        extensionName: [{ required: true, message: '请输入扩展名称', trigger: 'blur' }],
        className: [{ required: true, message: '请输入实现类全名', trigger: 'blur' }],
        extensionMode: [{ required: true, message: '请选择扩展方式', trigger: 'change' }]
      },

      codePreviewVisible: false,
      codePreviewFullscreen: false,
      codePreviewTitle: '',
      codePreviewContent: '',

      dialogTitle: '',
      isEdit: false,
      saveLoading: false,

      templates: {
        interfaceRemoteInvoker: `package com.helianhealth.agent.extension;

import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CustomInvoker implements InterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(Invocation invocation, WorkflowNode node) {
        // TODO: 实现自定义调用逻辑
        log.info("自定义调用: {}", node.getNodeName());
        return new HashMap<>();
    }
}`,
        agentFilter: `package com.helianhealth.agent.extension;

import com.helianhealth.agent.invocation.remote.filter.AgentFilter;
import com.helianhealth.agent.invocation.remote.filter.AgentFilterChain;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class CustomFilter implements AgentFilter {

    @Override
    public Map<String, Object> invoke(Invocation invocation, WorkflowNode node, AgentFilterChain chain) {
        // TODO: 实现请求预处理逻辑
        log.info("Filter 前置处理: {}", node.getNodeName());

        // 调用下一个过滤器或实际调用
        Map<String, Object> result = chain.doFilter(invocation, node);

        // TODO: 实现响应后处理逻辑
        log.info("Filter 后置处理");
        return result;
    }
}`
      }
    };
  },
  computed: {
    filteredExtensionList() {
      if (!this.searchKeyword) return this.extensionList;
      const kw = this.searchKeyword.toLowerCase();
      return this.extensionList.filter(item =>
        (item.extensionName && item.extensionName.toLowerCase().includes(kw)) ||
        (item.className && item.className.toLowerCase().includes(kw))
      );
    },
    highlightedPreviewCode() {
      if (!this.codePreviewContent) return '';
      try {
        return hljs.highlight(this.codePreviewContent, { language: 'java' }).value;
      } catch {
        const div = document.createElement('div');
        div.textContent = this.codePreviewContent;
        return div.innerHTML;
      }
    }
  },
  mounted() {
    this.loadExtensionList();
    this.loadNodeList();
  },
  methods: {
    getLineCount(code) {
      if (!code) return 1;
      return Math.max(code.split('\n').length, 1);
    },
    syncScroll(type) {
      const areaRef = type === 'interface' ? 'interfaceCodeArea' : 'filterCodeArea';
      const lineRef = type === 'interface' ? 'interfaceLineNumbers' : 'filterLineNumbers';
      if (this.$refs[areaRef] && this.$refs[lineRef]) {
        this.$refs[lineRef].scrollTop = this.$refs[areaRef].scrollTop;
      }
    },

    async loadExtensionList() {
      this.tableLoading = true;
      try {
        let combinedList = [];
        if (!this.filterType || this.filterType === 'InterfaceRemoteInvoker') {
          const interfaceResp = await getInterfaceExtensions();
          if (interfaceResp && interfaceResp.data) {
            const result = interfaceResp.data;
            const interfaceList = Array.isArray(result.data) ? result.data :
                                 Array.isArray(result) ? result : [];
            combinedList = combinedList.concat(interfaceList);
          }
        }
        if (!this.filterType || this.filterType === 'AgentFilter') {
          const filterResp = await getFilterExtensions();
          if (filterResp && filterResp.data) {
            const result = filterResp.data;
            const filterList = Array.isArray(result.data) ? result.data :
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

    async loadNodeList() {
      try {
        const response = await getAllNodes();
        if (response && response.data) {
          const result = response.data;
          this.nodeList = Array.isArray(result.data) ? result.data :
                          Array.isArray(result) ? result : [];
        } else {
          this.nodeList = [];
        }
      } catch (error) {
        this.nodeList = [];
        Message.error('加载节点列表失败，请刷新重试');
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

      if (type === 'InterfaceRemoteInvoker') {
        this.dialogTitle = '新增接口扩展';
        this.interfaceExtensionForm = {
          codeId: null, nodeId: null, extensionName: '', className: '',
          sourceCode: '', description: '', enabled: 1, extensionMode: 'ONLINE'
        };
        this.interfaceExtensionDialogVisible = true;
        this.$nextTick(() => {
          if (this.$refs.interfaceExtensionForm) this.$refs.interfaceExtensionForm.clearValidate();
        });
      } else if (type === 'AgentFilter') {
        this.dialogTitle = '新增过滤器扩展';
        this.filterExtensionForm = {
          codeId: null, nodeId: [], extensionName: '', className: '',
          sourceCode: '', description: '', enabled: 1, extensionMode: 'ONLINE',
          extensionParamList: []
        };
        this.filterExtensionDialogVisible = true;
        this.$nextTick(() => {
          if (this.$refs.filterExtensionForm) this.$refs.filterExtensionForm.clearValidate();
        });
      }
    },

    async handleEdit(row) {
      this.isEdit = true;
      let detailRow = row;
      try {
        const detailResp = await getExtensionDetail(row.codeId);
        if (detailResp && detailResp.data) detailRow = detailResp.data;
      } catch (error) {
        console.error('Failed to load extension detail:', error);
      }

      let mode = detailRow.extensionMode || detailRow.extensionLocation || (detailRow.usePageCode === 1 ? 'ONLINE' : 'PROJECT') || 'ONLINE';
      let nodeId = detailRow.nodeId;
      if (nodeId && !Array.isArray(nodeId)) {
        try { nodeId = typeof nodeId === 'string' ? JSON.parse(nodeId) : [nodeId]; } catch (e) { nodeId = []; }
      } else if (!nodeId) {
        nodeId = [];
      }

      if (detailRow.spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') {
        this.dialogTitle = '编辑接口扩展';
        this.interfaceExtensionForm = {
          ...detailRow,
          nodeId: Array.isArray(nodeId) && nodeId.length > 0 ? nodeId[0] : null,
          extensionMode: mode
        };
        this.interfaceExtensionDialogVisible = true;
        this.$nextTick(() => {
          if (this.$refs.interfaceExtensionForm) this.$refs.interfaceExtensionForm.clearValidate();
        });
      } else if (detailRow.spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') {
        this.dialogTitle = '编辑过滤器扩展';
        this.filterExtensionForm = {
          ...detailRow,
          nodeId: nodeId,
          extensionMode: mode,
          extensionParamList: this.convertJsonToParamList(detailRow.extensionParamValue)
        };
        this.filterExtensionDialogVisible = true;
        this.$nextTick(() => {
          if (this.$refs.filterExtensionForm) this.$refs.filterExtensionForm.clearValidate();
        });
      }
    },

    async handleViewCode(row) {
      try {
        const resp = await getExtensionDetail(row.codeId);
        const detail = (resp && resp.data) ? resp.data : row;
        this.codePreviewTitle = `${row.extensionName} - 源代码`;
        this.codePreviewContent = detail.sourceCode || '// 暂无源代码';
        this.codePreviewVisible = true;
      } catch (error) {
        Message.error('获取代码详情失败');
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
          await disableExtension(row.codeId);
        } else {
          await enableExtension(row.codeId);
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
        await deleteExtension(row.codeId);
        Message.success('删除成功');
        this.loadExtensionList();
      } catch (error) {
        if (error !== 'cancel') {
          Message.error('删除失败：' + (error.message || '未知错误'));
        }
      }
    },

    async handleSaveInterfaceExtension() {
      try {
        await this.$refs.interfaceExtensionForm.validate();
      } catch (error) { return; }

      if (!this.interfaceExtensionForm.extensionMode) {
        Message.error('请选择扩展方式'); return;
      }
      if (this.interfaceExtensionForm.extensionMode === 'ONLINE') {
        if (!this.interfaceExtensionForm.sourceCode || !this.interfaceExtensionForm.sourceCode.trim()) {
          Message.error('在线扩展模式下，请输入Java源代码'); return;
        }
      }

      this.saveLoading = true;
      try {
        const submitData = {
          ...this.interfaceExtensionForm,
          nodeId: [this.interfaceExtensionForm.nodeId]
        };
        if (submitData.extensionMode === 'PROJECT') delete submitData.sourceCode;

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

    async handleSaveFilterExtension() {
      try {
        await this.$refs.filterExtensionForm.validate();
      } catch (error) { return; }

      if (!this.filterExtensionForm.extensionMode) {
        Message.error('请选择扩展方式'); return;
      }
      if (this.filterExtensionForm.extensionMode === 'ONLINE') {
        if (!this.filterExtensionForm.sourceCode || !this.filterExtensionForm.sourceCode.trim()) {
          Message.error('在线扩展模式下，请输入Java源代码'); return;
        }
      }

      this.saveLoading = true;
      try {
        const submitData = { ...this.filterExtensionForm };
        submitData.extensionParamValue = this.convertParamListToJson(submitData.extensionParamList);
        delete submitData.extensionParamList;
        if (submitData.extensionMode === 'PROJECT') delete submitData.sourceCode;

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

    getNodeNameById(id) {
      const node = this.nodeList.find(n => n.nodeId === id);
      return node ? node.nodeName : `节点${id}`;
    },
    getExtensionTypeName(spiInterface) {
      if (spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') return '接口扩展';
      if (spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') return '过滤器扩展';
      return '未知类型';
    },
    getExtensionTypeTag(spiInterface) {
      if (spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') return 'primary';
      if (spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') return 'success';
      return '';
    },

    applyTemplate(templateType) {
      const targetForm = templateType === 'interfaceRemoteInvoker' ? 'interfaceExtensionForm' : 'filterExtensionForm';
      if (this[targetForm].sourceCode.trim()) {
        MessageBox.confirm('应用模板将覆盖当前代码，是否继续？', '提示', {
          confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
        }).then(() => {
          this[targetForm].sourceCode = this.templates[templateType];
          Message.success('模板已应用');
        }).catch(() => {});
      } else {
        this[targetForm].sourceCode = this.templates[templateType];
        Message.success('模板已应用');
      }
    },

    addExtensionParam() {
      this.filterExtensionForm.extensionParamList.push({ key: '', value: '' });
    },
    removeExtensionParam(index) {
      this.filterExtensionForm.extensionParamList.splice(index, 1);
    },
    convertParamListToJson(paramList) {
      if (!paramList || paramList.length === 0) return null;
      const paramObj = {};
      paramList.forEach(item => {
        if (item.key && item.key.trim()) paramObj[item.key.trim()] = item.value || '';
      });
      return Object.keys(paramObj).length > 0 ? JSON.stringify(paramObj) : null;
    },
    convertJsonToParamList(jsonStr) {
      if (!jsonStr || (typeof jsonStr === 'string' && !jsonStr.trim())) return [];
      try {
        const paramObj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
        if (typeof paramObj !== 'object' || paramObj === null) return [];
        return Object.keys(paramObj).map(key => ({ key, value: paramObj[key] }));
      } catch (e) {
        console.error('解析 extensionParamValue 失败:', e);
        return [];
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

/* 类型选择卡片 */
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

/* 代码编辑器 - 浅色风格 */
.code-editor-wrapper {
  display: flex;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  background: #fafafa;
  height: 400px;
}

.code-line-numbers {
  width: 45px;
  flex-shrink: 0;
  background: #f5f7fa;
  padding: 10px 0;
  text-align: right;
  overflow: hidden;
  user-select: none;
  border-right: 1px solid #e4e7ed;
}

.line-number {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 20px;
  color: #c0c4cc;
  padding-right: 8px;
}

.code-textarea {
  flex: 1;
  background: #fafafa;
  color: #303133;
  border: none;
  outline: none;
  resize: none;
  padding: 10px 14px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 20px;
  tab-size: 4;
  white-space: pre;
  overflow: auto;
}

.code-textarea::placeholder {
  color: #c0c4cc;
}

.code-toolbar {
  margin-top: 10px;
  text-align: right;
}

/* 代码预览 */
.code-preview-wrapper {
  display: flex;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  background: #fafafa;
  max-height: 70vh;
}

.code-preview-wrapper .code-line-numbers {
  padding-top: 14px;
}

.code-preview-content {
  flex: 1;
  margin: 0;
  padding: 14px;
  overflow: auto;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 20px;
  background: transparent;
  color: #303133;
}

.code-preview-content code {
  background: transparent;
  font-family: inherit;
}

/* 参数列表 */
.param-list {
  width: 100%;
}

.param-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}
</style>

<style>
/* 全局样式 - 强制覆盖Element UI嵌套submenu的默认padding */
.el-menu .el-menu .el-submenu__title {
  padding-left: 50px !important;
}
</style>
