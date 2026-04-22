<template>
  <div class="api-invoke-tool">
    <!-- 请求配置区 -->
    <el-card class="request-card">
      <div slot="header" class="card-header">
        <span>HTTP接口调用</span>
      </div>
      
      <!-- URL和方法输入行 -->
      <div class="url-bar">
        <el-select v-model="request.method" class="method-select" placeholder="方法">
          <el-option
            v-for="method in httpMethods"
            :key="method.value"
            :label="method.label"
            :value="method.value"
          >
            <span :style="{ color: method.color }">{{ method.label }}</span>
          </el-option>
        </el-select>
        <el-input 
          v-model="request.url" 
          class="url-input"
          placeholder="请输入完整URL，如: https://api.example.com/users"
          clearable
          @keyup.enter.native="handleSend"
        >
          <template slot="prepend">
            <el-tooltip content="请求地址" placement="top">
              <i class="el-icon-link"></i>
            </el-tooltip>
          </template>
        </el-input>
        <el-button 
          type="primary" 
          @click="handleSend" 
          :loading="loading"
          class="send-button"
        >
          <i class="el-icon-s-promotion" v-if="!loading"></i>
          {{ loading ? '发送中...' : '发送' }}
        </el-button>
      </div>

      <!-- 请求配置Tabs -->
      <el-tabs v-model="activeTab" class="request-tabs">
        <!-- 查询参数Tab -->
        <el-tab-pane label="Query参数" name="params">
          <div class="param-list">
            <div 
              v-for="(param, index) in request.queryParams" 
              :key="index"
              class="param-item"
            >
              <el-checkbox v-model="param.enabled" class="param-checkbox"></el-checkbox>
              <el-input v-model="param.key" placeholder="参数名" class="param-key"></el-input>
              <el-input v-model="param.value" placeholder="参数值" class="param-value"></el-input>
              <el-button 
                type="text" 
                icon="el-icon-delete" 
                @click="removeParam('queryParams', index)"
                class="param-delete"
              ></el-button>
            </div>
            <el-button type="text" icon="el-icon-plus" @click="addParam('queryParams')">
              添加参数
            </el-button>
          </div>
        </el-tab-pane>

        <!-- 请求头Tab -->
        <el-tab-pane label="Headers" name="headers">
          <div class="param-list">
            <div 
              v-for="(header, index) in request.headers" 
              :key="index"
              class="param-item"
            >
              <el-checkbox v-model="header.enabled" class="param-checkbox"></el-checkbox>
              <el-autocomplete
                v-model="header.key"
                :fetch-suggestions="queryHeaderSuggestions"
                placeholder="Header名称"
                class="param-key"
              ></el-autocomplete>
              <el-input v-model="header.value" placeholder="Header值" class="param-value"></el-input>
              <el-button 
                type="text" 
                icon="el-icon-delete" 
                @click="removeParam('headers', index)"
                class="param-delete"
              ></el-button>
            </div>
            <el-button type="text" icon="el-icon-plus" @click="addParam('headers')">
              添加Header
            </el-button>
          </div>
        </el-tab-pane>

        <!-- 请求体Tab -->
        <el-tab-pane label="Body" name="body">
          <div class="body-type-selector">
            <el-radio-group v-model="request.bodyType" size="small">
              <el-radio-button label="none">none</el-radio-button>
              <el-radio-button label="json">JSON</el-radio-button>
              <el-radio-button label="form-data">form-data</el-radio-button>
              <el-radio-button label="x-www-form-urlencoded">x-www-form-urlencoded</el-radio-button>
              <el-radio-button label="raw">raw</el-radio-button>
            </el-radio-group>
          </div>
          
          <!-- JSON Body -->
          <div v-if="request.bodyType === 'json'" class="body-editor">
            <div class="editor-toolbar">
              <el-button size="mini" @click="formatJson">格式化JSON</el-button>
              <el-button size="mini" @click="compactJson">压缩JSON</el-button>
              <el-button size="mini" icon="el-icon-full-screen" @click="showBodyDialog = true">放大</el-button>
            </div>
            <el-input
              v-model="request.body"
              type="textarea"
              :rows="10"
              placeholder='输入JSON格式的请求体，如: {"name": "test", "value": 123}'
            ></el-input>
          </div>

          <!-- Form Data -->
          <div v-if="request.bodyType === 'form-data' || request.bodyType === 'x-www-form-urlencoded'" class="param-list">
            <div 
              v-for="(item, index) in request.formData" 
              :key="index"
              class="param-item"
            >
              <el-checkbox v-model="item.enabled" class="param-checkbox"></el-checkbox>
              <el-input v-model="item.key" placeholder="字段名" class="param-key"></el-input>
              <el-input v-model="item.value" placeholder="字段值" class="param-value"></el-input>
              <el-button 
                type="text" 
                icon="el-icon-delete" 
                @click="removeParam('formData', index)"
                class="param-delete"
              ></el-button>
            </div>
            <el-button type="text" icon="el-icon-plus" @click="addParam('formData')">
              添加字段
            </el-button>
          </div>

          <!-- Raw Body -->
          <div v-if="request.bodyType === 'raw'" class="body-editor">
            <el-input
              v-model="request.body"
              type="textarea"
              :rows="10"
              placeholder="输入原始请求体内容"
            ></el-input>
          </div>

          <!-- None -->
          <div v-if="request.bodyType === 'none'" class="body-none">
            <el-empty description="该请求没有请求体"></el-empty>
          </div>
        </el-tab-pane>

        <!-- 配置Tab -->
        <el-tab-pane label="设置" name="settings">
          <el-form label-width="100px" size="small">
            <el-form-item label="超时时间">
              <el-input-number 
                v-model="request.timeout" 
                :min="1000" 
                :max="300000" 
                :step="1000"
              ></el-input-number>
              <span class="setting-hint">毫秒 (默认30秒)</span>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 响应展示区 -->
    <el-card class="response-card" v-if="hasResponse">
      <div slot="header" class="card-header">
        <span>响应结果</span>
        <div class="response-meta">
          <el-tag :type="statusTagType" effect="dark" size="small">
            {{ response.status }} {{ response.statusText }}
          </el-tag>
          <el-tag type="info" effect="plain" size="small">
            耗时: {{ response.time }}ms
          </el-tag>
          <el-tag type="info" effect="plain" size="small" v-if="response.size">
            大小: {{ formatSize(response.size) }}
          </el-tag>
        </div>
      </div>

      <el-tabs v-model="responseTab" class="response-tabs">
        <!-- 响应体Tab -->
        <el-tab-pane label="Body" name="body">
          <div class="response-toolbar">
            <el-button size="mini" @click="copyResponse">复制响应</el-button>
            <el-button size="mini" @click="formatResponseJson">格式化</el-button>
            <el-button size="mini" icon="el-icon-full-screen" @click="showResponseDialog = true">放大</el-button>
          </div>
          <div class="response-body">
            <pre><code>{{ formattedResponseBody }}</code></pre>
          </div>
        </el-tab-pane>

        <!-- 响应头Tab -->
        <el-tab-pane label="Headers" name="headers">
          <el-table :data="responseHeadersList" stripe size="small">
            <el-table-column prop="key" label="Header名称" width="250"></el-table-column>
            <el-table-column prop="value" label="Header值"></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 空状态 -->
    <el-card class="response-card" v-else>
      <el-empty description="点击发送按钮开始测试接口">
        <template slot="image">
          <i class="el-icon-s-promotion" style="font-size: 60px; color: #909399;"></i>
        </template>
      </el-empty>
    </el-card>

    <!-- Body放大对话框 -->
    <el-dialog title="编辑请求体" :visible.sync="showBodyDialog" width="70%">
      <el-input
        v-model="request.body"
        type="textarea"
        :rows="20"
        placeholder="输入请求体内容"
      ></el-input>
      <span slot="footer">
        <el-button @click="formatJson">格式化JSON</el-button>
        <el-button type="primary" @click="showBodyDialog = false">确定</el-button>
      </span>
    </el-dialog>

    <!-- 响应放大对话框 -->
    <el-dialog title="响应详情" :visible.sync="showResponseDialog" width="80%">
      <div class="response-body large">
        <pre><code>{{ formattedResponseBody }}</code></pre>
      </div>
      <span slot="footer">
        <el-button @click="copyResponse">复制</el-button>
        <el-button type="primary" @click="showResponseDialog = false">关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { proxyInvoke } from '@/api/invokeTool';

export default {
  name: 'ApiInvokeTool',
  data() {
    return {
      activeTab: 'params',
      responseTab: 'body',
      loading: false,
      showBodyDialog: false,
      showResponseDialog: false,
      httpMethods: [
        { value: 'GET', label: 'GET', color: '#67c23a' },
        { value: 'POST', label: 'POST', color: '#e6a23c' },
        { value: 'PUT', label: 'PUT', color: '#409eff' },
        { value: 'DELETE', label: 'DELETE', color: '#f56c6c' },
        { value: 'PATCH', label: 'PATCH', color: '#909399' },
        { value: 'HEAD', label: 'HEAD', color: '#909399' },
        { value: 'OPTIONS', label: 'OPTIONS', color: '#909399' }
      ],
      commonHeaders: [
        'Content-Type',
        'Authorization',
        'Accept',
        'Accept-Language',
        'Cache-Control',
        'User-Agent',
        'X-Requested-With',
        'X-API-Key',
        'X-Auth-Token'
      ],
      request: {
        method: 'GET',
        url: '',
        queryParams: [{ key: '', value: '', enabled: true }],
        headers: [{ key: 'Content-Type', value: 'application/json', enabled: true }],
        bodyType: 'json',
        body: '',
        formData: [{ key: '', value: '', enabled: true }],
        timeout: 30000
      },
      response: {
        status: null,
        statusText: '',
        headers: {},
        data: null,
        time: 0,
        size: 0
      },
      hasResponse: false,
      responseFormatted: true
    };
  },
  computed: {
    statusTagType() {
      if (!this.response.status) return 'info';
      if (this.response.status >= 200 && this.response.status < 300) return 'success';
      if (this.response.status >= 300 && this.response.status < 400) return 'warning';
      return 'danger';
    },
    formattedResponseBody() {
      if (!this.response.data) return '';
      if (typeof this.response.data === 'string') {
        try {
          const parsed = JSON.parse(this.response.data);
          return this.responseFormatted 
            ? JSON.stringify(parsed, null, 2) 
            : JSON.stringify(parsed);
        } catch {
          return this.response.data;
        }
      }
      return this.responseFormatted 
        ? JSON.stringify(this.response.data, null, 2) 
        : JSON.stringify(this.response.data);
    },
    responseHeadersList() {
      if (!this.response.headers) return [];
      return Object.entries(this.response.headers).map(([key, value]) => ({
        key,
        value: Array.isArray(value) ? value.join(', ') : value
      }));
    }
  },
  methods: {
    addParam(type) {
      this.request[type].push({ key: '', value: '', enabled: true });
    },
    removeParam(type, index) {
      if (this.request[type].length > 1) {
        this.request[type].splice(index, 1);
      } else {
        this.request[type][0] = { key: '', value: '', enabled: true };
      }
    },
    queryHeaderSuggestions(queryString, cb) {
      const results = queryString
        ? this.commonHeaders.filter(h => h.toLowerCase().includes(queryString.toLowerCase()))
        : this.commonHeaders;
      cb(results.map(h => ({ value: h })));
    },
    formatJson() {
      try {
        const parsed = JSON.parse(this.request.body);
        this.request.body = JSON.stringify(parsed, null, 2);
      } catch (e) {
        this.$message.error('JSON格式错误: ' + e.message);
      }
    },
    compactJson() {
      try {
        const parsed = JSON.parse(this.request.body);
        this.request.body = JSON.stringify(parsed);
      } catch (e) {
        this.$message.error('JSON格式错误: ' + e.message);
      }
    },
    formatResponseJson() {
      this.responseFormatted = true;
    },
    copyResponse() {
      const text = this.formattedResponseBody;
      navigator.clipboard.writeText(text).then(() => {
        this.$message.success('已复制到剪贴板');
      }).catch(() => {
        this.$message.error('复制失败');
      });
    },
    formatSize(bytes) {
      if (bytes < 1024) return bytes + ' B';
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
      return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
    },
    buildRequestParams() {
      // 构建查询参数
      const queryParams = {};
      this.request.queryParams
        .filter(p => p.enabled && p.key)
        .forEach(p => { queryParams[p.key] = p.value; });

      // 构建请求头
      const headers = {};
      this.request.headers
        .filter(h => h.enabled && h.key)
        .forEach(h => { headers[h.key] = h.value; });

      // 构建请求体
      let body = null;
      if (this.request.method !== 'GET') {
        if (this.request.bodyType === 'json' && this.request.body) {
          try {
            body = JSON.parse(this.request.body);
          } catch {
            body = this.request.body;
          }
        } else if (this.request.bodyType === 'form-data' || this.request.bodyType === 'x-www-form-urlencoded') {
          const formBody = {};
          this.request.formData
            .filter(f => f.enabled && f.key)
            .forEach(f => { formBody[f.key] = f.value; });
          body = formBody;
          
          if (this.request.bodyType === 'x-www-form-urlencoded') {
            headers['Content-Type'] = 'application/x-www-form-urlencoded';
          }
        } else if (this.request.bodyType === 'raw') {
          body = this.request.body;
        }
      }

      return {
        url: this.request.url,
        method: this.request.method,
        headers,
        body,
        queryParams,
        timeout: this.request.timeout
      };
    },
    async handleSend() {
      if (!this.request.url) {
        this.$message.warning('请输入请求URL');
        return;
      }

      // URL格式验证
      try {
        new URL(this.request.url);
      } catch {
        this.$message.warning('请输入有效的URL地址');
        return;
      }

      this.loading = true;
      this.hasResponse = false;
      const startTime = Date.now();

      try {
        const params = this.buildRequestParams();
        const result = await proxyInvoke(params);
        
        // 处理代理响应
        if (result.data && result.data.code === 200) {
          const proxyResult = result.data.data || {};
          this.response = {
            status: proxyResult.status || 200,
            statusText: proxyResult.statusText || 'OK',
            headers: proxyResult.headers || {},
            data: proxyResult.body,
            time: Date.now() - startTime,
            size: JSON.stringify(proxyResult.body || '').length
          };
          this.$message.success(`请求成功，状态码: ${this.response.status}`);
        } else {
          this.response = {
            status: result.data?.data?.status || 0,
            statusText: 'Error',
            headers: {},
            data: result.data?.message || '请求失败',
            time: Date.now() - startTime,
            size: 0
          };
          this.$message.error('请求失败: ' + (result.data?.message || '未知错误'));
        }
      } catch (error) {
        console.error('请求失败:', error);
        this.hasResponse = true;
        
        if (error.response) {
          // 有响应但状态码表示错误
          this.response = {
            status: error.response.status,
            statusText: error.response.statusText,
            headers: error.response.headers || {},
            data: error.response.data,
            time: Date.now() - startTime,
            size: 0
          };
        } else {
          // 网络错误或其他错误
          this.response = {
            status: 0,
            statusText: 'Error',
            headers: {},
            data: {
              error: error.message || '请求失败',
              hint: error.message?.includes('Network Error') 
                ? '可能是CORS限制，请尝试使用"后端代理"模式' 
                : '请检查URL是否正确或网络是否正常'
            },
            time: Date.now() - startTime,
            size: 0
          };
        }
        this.$message.error('请求失败: ' + (error.message || '未知错误'));
      } finally {
        this.loading = false;
        this.hasResponse = true;
        this.responseTab = 'body';
      }
    }
  }
};
</script>

<style scoped>
.api-invoke-tool {
  padding: 20px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.request-card,
.response-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.url-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.method-select {
  width: 120px;
}

.method-select :deep(.el-input__inner) {
  font-weight: bold;
}

.url-input {
  flex: 1;
}

.send-button {
  width: 100px;
}

.request-tabs {
  margin-top: 10px;
}

.param-list {
  padding: 10px 0;
}

.param-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.param-checkbox {
  width: 30px;
}

.param-key {
  flex: 1;
}

.param-value {
  flex: 2;
}

.param-delete {
  color: #f56c6c;
}

.body-type-selector {
  margin-bottom: 15px;
}

.body-editor {
  margin-top: 10px;
}

.editor-toolbar {
  margin-bottom: 10px;
  display: flex;
  gap: 10px;
}

.body-none {
  padding: 40px 0;
}

.setting-hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.response-meta {
  display: flex;
  gap: 10px;
}

.response-toolbar {
  margin-bottom: 10px;
  display: flex;
  gap: 10px;
}

.response-body {
  background: #1e1e1e;
  border-radius: 4px;
  padding: 15px;
  overflow: auto;
  max-height: 400px;
}

.response-body.large {
  max-height: 60vh;
}

.response-body pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.response-body code {
  color: #d4d4d4;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}

/* 响应式布局 */
@media (max-width: 768px) {
  .url-bar {
    flex-direction: column;
  }
  
  .method-select {
    width: 100%;
  }
  
  .param-item {
    flex-wrap: wrap;
  }
  
  .param-key,
  .param-value {
    flex: 100%;
  }
}
</style>
