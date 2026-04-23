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
          <div class="node-tag-list">
            <el-tag
              v-if="!Array.isArray(scope.row.nodeId) || scope.row.nodeId.length === 0"
              size="small"
              type="info"
              effect="plain"
              class="node-tag node-tag-empty"
            >
              -
            </el-tag>
            <el-tag
              v-for="id in (Array.isArray(scope.row.nodeId) ? scope.row.nodeId : [])"
              v-else
              :key="id"
              size="small"
              type="info"
              effect="plain"
              class="node-tag"
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
          <el-button size="small" type="primary" plain icon="el-icon-edit-outline" @click="openCodeEditor('interface')">
            {{ interfaceExtensionForm.sourceCode ? '编辑源代码' : '编写源代码' }}
          </el-button>
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
          <el-button size="small" type="primary" plain icon="el-icon-edit-outline" @click="openCodeEditor('filter')">
            {{ filterExtensionForm.sourceCode ? '编辑源代码' : '编写源代码' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div slot="footer">
        <el-button @click="filterExtensionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveFilterExtension" :loading="saveLoading">保存</el-button>
      </div>
    </el-dialog>

    <!-- 源代码编辑弹窗 -->
    <el-dialog
      :title="codeEditorTitle"
      :visible.sync="codeEditorVisible"
      :width="codeEditorFullscreen ? '100%' : '80%'"
      :top="codeEditorFullscreen ? '0' : '3vh'"
      :fullscreen="codeEditorFullscreen"
      :close-on-click-modal="false"
      append-to-body
      @opened="initCodemirror"
    >
      <template slot="title">
        <span>{{ codeEditorTitle }}</span>
        <el-button
          size="mini"
          :icon="codeEditorFullscreen ? 'el-icon-copy-document' : 'el-icon-full-screen'"
          circle
          style="margin-left: 12px"
          @click="toggleFullscreen"
        ></el-button>
      </template>
      <div class="code-editor-container" :style="codeEditorFullscreen ? 'height: calc(100vh - 120px)' : ''">
        <div class="code-toolbar">
          <div class="code-editor-stats">
            <el-tag size="mini" type="success" effect="plain">CodeMirror</el-tag>
            <el-tag size="mini" effect="plain">Java</el-tag>
            <el-tag size="mini" effect="plain">{{ editorStats.lines }} 行</el-tag>
            <el-tag size="mini" effect="plain">{{ editorStats.chars }} 字符</el-tag>
          </div>
          <div class="code-toolbar-actions">
            <el-button size="mini" @click="applyTemplateToEditor" icon="el-icon-document">使用模板</el-button>
          </div>
        </div>
        <div class="code-editor-wrapper">
          <textarea ref="codemirrorArea"></textarea>
        </div>
        <div class="code-editor-footer">
          <el-button size="small" @click="codeEditorVisible = false">取消</el-button>
          <el-button size="small" type="primary" @click="confirmCodeEditor">确定</el-button>
        </div>
      </div>
      <div slot="footer" style="display:none;"></div>
    </el-dialog>
  </div>
</template>

<script>
import { Message, MessageBox } from 'element-ui';
import CodeMirror from 'codemirror';
import 'codemirror/lib/codemirror.css';
import 'codemirror/theme/material-darker.css';
import 'codemirror/mode/clike/clike';
import 'codemirror/addon/edit/matchbrackets';
import 'codemirror/addon/edit/closebrackets';
import 'codemirror/addon/fold/foldcode';
import 'codemirror/addon/fold/foldgutter';
import 'codemirror/addon/fold/foldgutter.css';
import 'codemirror/addon/fold/brace-fold';
import 'codemirror/addon/fold/comment-fold';
import 'codemirror/addon/selection/active-line';
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

      codeEditorVisible: false,
      codeEditorFullscreen: false,
      codeEditorTitle: '',
      codeEditorContent: '',
      codeEditorTarget: '',

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
    editorStats() {
      const code = this.codeEditorContent || '';
      return {
        lines: Math.max((code.match(/\n/g) || []).length + 1, 1),
        chars: code.length
      };
    }
  },
  mounted() {
    this.loadExtensionList();
    this.loadNodeList();
  },
  beforeDestroy() {
    if (this.cmInstance) {
      this.cmInstance.toTextArea();
      this.cmInstance = null;
    }
  },
  methods: {
    initCodemirror() {
      this.$nextTick(() => {
        if (this.cmInstance) {
          this.cmInstance.setValue(this.codeEditorContent || '');
          this.cmInstance.refresh();
          this.cmInstance.focus();
          return;
        }
        this.cmInstance = CodeMirror.fromTextArea(this.$refs.codemirrorArea, {
          mode: 'text/x-java',
          theme: 'material-darker',
          lineNumbers: true,
          matchBrackets: true,
          autoCloseBrackets: true,
          foldGutter: true,
          gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
          styleActiveLine: true,
          indentUnit: 4,
          tabSize: 4,
          indentWithTabs: false,
          lineWrapping: false,
          extraKeys: {
            'Tab': (cm) => {
              if (cm.somethingSelected()) {
                cm.indentSelection('add');
              } else {
                cm.replaceSelection('    ', 'end');
              }
            },
            'Shift-Tab': (cm) => cm.indentSelection('subtract')
          }
        });
        this.cmInstance.setValue(this.codeEditorContent || '');
        this.cmInstance.on('change', (cm) => {
          this.codeEditorContent = cm.getValue();
        });
        this.cmInstance.setSize('100%', '100%');
        this.cmInstance.focus();
      });
    },
    toggleFullscreen() {
      this.codeEditorFullscreen = !this.codeEditorFullscreen;
      this.$nextTick(() => {
        if (this.cmInstance) this.cmInstance.refresh();
      });
    },
    openCodeEditor(target) {
      this.codeEditorTarget = target;
      if (target === 'interface') {
        this.codeEditorTitle = '编辑源代码 - 接口扩展';
        this.codeEditorContent = this.interfaceExtensionForm.sourceCode || '';
      } else {
        this.codeEditorTitle = '编辑源代码 - 过滤器扩展';
        this.codeEditorContent = this.filterExtensionForm.sourceCode || '';
      }
      this.codeEditorFullscreen = false;
      this.codeEditorVisible = true;
    },

    confirmCodeEditor() {
      if (this.codeEditorTarget === 'interface') {
        this.interfaceExtensionForm.sourceCode = this.codeEditorContent;
      } else {
        this.filterExtensionForm.sourceCode = this.codeEditorContent;
      }
      this.codeEditorVisible = false;
    },

    applyTemplateToEditor() {
      const templateKey = this.codeEditorTarget === 'interface' ? 'interfaceRemoteInvoker' : 'agentFilter';
      const apply = () => {
        this.codeEditorContent = this.templates[templateKey];
        if (this.cmInstance) this.cmInstance.setValue(this.codeEditorContent);
      };
      if (this.codeEditorContent && this.codeEditorContent.trim()) {
        MessageBox.confirm('使用模板将覆盖当前代码，是否继续？', '提示', {
          confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
        }).then(apply).catch(() => {});
      } else {
        apply();
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

.node-tag-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 28px;
}

.node-tag {
  max-width: 220px;
}

.node-tag-empty {
  color: #b0b8c6;
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

.code-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.code-stats-inline {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

/* 代码编辑器整体容器 - flex 纵向布局 */
.code-editor-container {
  display: flex;
  flex-direction: column;
  height: calc(80vh - 120px);
}

.code-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  flex-shrink: 0;
}

.code-editor-stats {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.code-editor-wrapper {
  border: 1px solid #d1d5db;
  border-radius: 4px;
  overflow: hidden;
  flex: 1;
  min-height: 0;
}

.code-editor-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 12px;
  flex-shrink: 0;
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

/* CodeMirror 在 dialog 中的全局适配 */
.code-editor-wrapper .CodeMirror {
  height: 100%;
  font-family: 'JetBrains Mono', 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
