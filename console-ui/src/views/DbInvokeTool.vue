<template>
  <div class="db-invoke-tool">
    <!-- 数据源配置区 -->
    <el-card class="config-card">
      <div slot="header" class="card-header">
        <span>数据源配置</span>
        <el-button 
          type="success" 
          size="small"
          @click="handleTestConnection"
          :loading="testingConnection"
          :disabled="!datasource.url"
        >
          <i class="el-icon-connection" v-if="!testingConnection"></i>
          测试连接
        </el-button>
      </div>
      
      <el-form :model="datasource" label-width="100px" size="small">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="数据库类型">
              <el-select v-model="datasource.dbType" placeholder="选择数据库类型" style="width: 100%">
                <el-option label="MySQL" value="mysql"></el-option>
                <el-option label="PostgreSQL" value="postgresql"></el-option>
                <el-option label="Oracle" value="oracle"></el-option>
                <el-option label="SQL Server" value="sqlserver"></el-option>
                <el-option label="达梦" value="dm"></el-option>
                <el-option label="人大金仓" value="kingbase"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="连接URL">
              <el-input 
                v-model="datasource.url" 
                placeholder="jdbc:mysql://localhost:3306/dbname"
              >
                <template slot="prepend">
                  <el-button size="mini" @click="generateUrl">生成</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="主机地址">
              <el-input v-model="datasource.host" placeholder="localhost"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="端口">
              <el-input v-model="datasource.port" placeholder="3306"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="数据库名">
              <el-input v-model="datasource.database" placeholder="test"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Schema">
              <el-input v-model="datasource.schema" placeholder="可选"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="用户名">
              <el-input v-model="datasource.username" placeholder="root"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="密码">
              <el-input v-model="datasource.password" type="password" placeholder="password" show-password></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大行数">
              <el-input-number v-model="maxRows" :min="1" :max="10000" style="width: 100%"></el-input-number>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- SQL查询区 -->
    <el-card class="query-card">
      <div slot="header" class="card-header">
        <span>SQL查询</span>
        <div>
          <el-tag size="mini" type="warning" style="margin-right: 10px;">仅支持SELECT语句</el-tag>
          <el-button 
            type="primary" 
            size="small"
            @click="handleExecute"
            :loading="executing"
            :disabled="!datasource.url || !sql.trim()"
          >
            <i class="el-icon-video-play" v-if="!executing"></i>
            {{ executing ? '执行中...' : '执行查询' }}
          </el-button>
        </div>
      </div>

      <!-- SQL输入区 -->
      <div class="sql-editor-section">
        <div class="editor-toolbar">
          <el-button size="mini" @click="formatSql">格式化SQL</el-button>
          <el-button size="mini" @click="clearSql">清空</el-button>
          <el-button size="mini" icon="el-icon-full-screen" @click="showSqlDialog = true">放大</el-button>
          <div class="toolbar-hint">
            <el-tag size="mini" type="info">Ctrl+Enter 快速执行</el-tag>
          </div>
        </div>
        <el-input
          ref="sqlInput"
          v-model="sql"
          type="textarea"
          :rows="6"
          placeholder="请输入SELECT语句，如: SELECT * FROM table_name WHERE id = 1 LIMIT 100"
          class="sql-textarea"
          @keydown.native="handleKeydown"
        ></el-input>
      </div>

      <!-- 常用SQL模板 -->
      <div class="sql-templates">
        <span class="template-label">快捷模板:</span>
        <el-button size="mini" type="text" @click="insertTemplate('select')">SELECT *</el-button>
        <el-button size="mini" type="text" @click="insertTemplate('selectColumns')">SELECT 字段</el-button>
        <el-button size="mini" type="text" @click="insertTemplate('count')">COUNT</el-button>
        <el-button size="mini" type="text" @click="insertTemplate('join')">JOIN</el-button>
        <el-button size="mini" type="text" @click="insertTemplate('group')">GROUP BY</el-button>
      </div>
    </el-card>

    <!-- 结果展示区 -->
    <el-card class="result-card" v-if="hasResult">
      <div slot="header" class="card-header">
        <span>查询结果</span>
        <div class="result-meta">
          <el-tag :type="result.success ? 'success' : 'danger'" effect="dark" size="small">
            {{ result.success ? '成功' : '失败' }}
          </el-tag>
          <el-tag type="info" effect="plain" size="small" v-if="result.time">
            耗时: {{ result.time }}ms
          </el-tag>
          <el-tag type="info" effect="plain" size="small" v-if="result.rows">
            返回: {{ result.rows.length }} 行
          </el-tag>
        </div>
      </div>

      <!-- 查询结果表格 -->
      <div v-if="result.success && result.rows && result.rows.length > 0">
        <div class="result-toolbar">
          <el-button size="mini" @click="exportCsv">导出CSV</el-button>
          <el-button size="mini" @click="copyResult">复制数据</el-button>
        </div>
        <el-table 
          :data="result.rows" 
          stripe 
          border
          size="small"
          max-height="400"
          class="result-table"
        >
          <el-table-column
            v-for="col in result.columns"
            :key="col"
            :prop="col"
            :label="col"
            min-width="120"
            show-overflow-tooltip
          >
            <template #default="scope">
              <span 
                class="cell-value"
                :class="{ 'null-value': scope.row[col] === null }"
              >
                {{ scope.row[col] === null ? 'NULL' : scope.row[col] }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 执行成功但无数据 -->
      <div v-else-if="result.success && (!result.rows || result.rows.length === 0)">
        <el-empty description="查询成功，无返回数据">
          <template slot="image">
            <i class="el-icon-document-checked" style="font-size: 60px; color: #67c23a;"></i>
          </template>
        </el-empty>
      </div>

      <!-- 执行失败 -->
      <div v-else-if="!result.success" class="error-result">
        <el-alert
          :title="result.errorMessage || '执行失败'"
          type="error"
          :closable="false"
          show-icon
        >
          <template v-if="result.errorDetail">
            <pre class="error-detail">{{ result.errorDetail }}</pre>
          </template>
        </el-alert>
      </div>
    </el-card>

    <!-- 空状态 -->
    <el-card class="result-card" v-else>
      <el-empty description="配置数据源并输入SELECT语句执行查询">
        <template slot="image">
          <i class="el-icon-data-analysis" style="font-size: 60px; color: #909399;"></i>
        </template>
      </el-empty>
    </el-card>

    <!-- SQL放大对话框 -->
    <el-dialog title="编辑SQL" :visible.sync="showSqlDialog" width="70%">
      <el-input
        v-model="sql"
        type="textarea"
        :rows="20"
        placeholder="请输入SELECT语句"
      ></el-input>
      <span slot="footer">
        <el-button @click="formatSql">格式化</el-button>
        <el-button type="primary" @click="showSqlDialog = false">确定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { executeSql } from '@/api/invokeTool';

export default {
  name: 'DbInvokeTool',
  data() {
    return {
      datasource: {
        dbType: 'mysql',
        url: '',
        host: 'localhost',
        port: '3306',
        database: '',
        schema: '',
        username: '',
        password: ''
      },
      sql: '',
      maxRows: 100,
      executing: false,
      testingConnection: false,
      showSqlDialog: false,
      hasResult: false,
      result: {
        success: false,
        rows: [],
        columns: [],
        time: 0,
        errorMessage: '',
        errorDetail: ''
      },
      sqlTemplates: {
        select: 'SELECT * FROM table_name WHERE 1=1 LIMIT 100',
        selectColumns: 'SELECT id, name, create_time FROM table_name WHERE 1=1 LIMIT 100',
        count: 'SELECT COUNT(*) as total FROM table_name WHERE 1=1',
        join: 'SELECT a.*, b.name FROM table_a a LEFT JOIN table_b b ON a.id = b.a_id WHERE 1=1 LIMIT 100',
        group: 'SELECT column1, COUNT(*) as cnt FROM table_name GROUP BY column1 ORDER BY cnt DESC LIMIT 100'
      },
      defaultPorts: {
        mysql: '3306',
        postgresql: '5432',
        oracle: '1521',
        sqlserver: '1433',
        dm: '5236',
        kingbase: '54321'
      }
    };
  },
  watch: {
    'datasource.dbType'(newType) {
      this.datasource.port = this.defaultPorts[newType] || '3306';
    }
  },
  methods: {
    generateUrl() {
      const { dbType, host, port, database, schema } = this.datasource;
      let url = '';
      switch (dbType) {
        case 'mysql':
          url = `jdbc:mysql://${host}:${port}/${database}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai`;
          break;
        case 'postgresql':
          url = `jdbc:postgresql://${host}:${port}/${database}`;
          if (schema) url += `?currentSchema=${schema}`;
          break;
        case 'oracle':
          url = `jdbc:oracle:thin:@${host}:${port}:${database}`;
          break;
        case 'sqlserver':
          url = `jdbc:sqlserver://${host}:${port};databaseName=${database}`;
          break;
        case 'dm':
          url = `jdbc:dm://${host}:${port}/${database}`;
          break;
        case 'kingbase':
          url = `jdbc:kingbase8://${host}:${port}/${database}`;
          break;
        default:
          url = `jdbc:mysql://${host}:${port}/${database}`;
      }
      this.datasource.url = url;
    },
    async handleTestConnection() {
      if (!this.datasource.url) {
        this.$message.warning('请先配置数据源连接URL');
        return;
      }
      
      this.testingConnection = true;
      try {
        // 使用一个简单的测试SQL来验证连接
        const response = await executeSql({
          ...this.datasource,
          sql: 'SELECT 1',
          maxRows: 1
        });
        
        if (response.data && response.data.code === 200) {
          this.$message.success('连接成功');
        } else {
          this.$message.error(response.data?.message || '连接失败');
        }
      } catch (error) {
        this.$message.error('连接测试失败: ' + (error.response?.data?.message || error.message || '未知错误'));
      } finally {
        this.testingConnection = false;
      }
    },
    handleKeydown(e) {
      if (e.ctrlKey && e.key === 'Enter') {
        e.preventDefault();
        this.handleExecute();
      }
    },
    async handleExecute() {
      if (!this.datasource.url) {
        this.$message.warning('请先配置数据源');
        return;
      }
      if (!this.sql.trim()) {
        this.$message.warning('请输入SQL语句');
        return;
      }
      
      // 验证是否为SELECT语句
      const trimmedSql = this.sql.trim().toUpperCase();
      if (!trimmedSql.startsWith('SELECT')) {
        this.$message.error('仅支持SELECT查询语句');
        return;
      }

      this.executing = true;
      this.hasResult = false;
      const startTime = Date.now();

      try {
        const response = await executeSql({
          ...this.datasource,
          sql: this.sql.trim(),
          maxRows: this.maxRows
        });

        const time = Date.now() - startTime;

        if (response.data && response.data.code === 200) {
          const data = response.data.data || {};
          this.result = {
            success: true,
            rows: data.rows || [],
            columns: data.columns || (data.rows && data.rows.length > 0 ? Object.keys(data.rows[0]) : []),
            time: time,
            errorMessage: '',
            errorDetail: ''
          };
          this.$message.success('查询成功');
        } else {
          this.result = {
            success: false,
            rows: [],
            columns: [],
            time: time,
            errorMessage: response.data?.message || '执行失败',
            errorDetail: ''
          };
          this.$message.error('执行失败: ' + (response.data?.message || '未知错误'));
        }
      } catch (error) {
        this.result = {
          success: false,
          rows: [],
          columns: [],
          time: Date.now() - startTime,
          errorMessage: error.response?.data?.message || error.message || '执行失败',
          errorDetail: ''
        };
        this.$message.error('执行失败: ' + (error.response?.data?.message || error.message || '未知错误'));
      } finally {
        this.executing = false;
        this.hasResult = true;
      }
    },
    formatSql() {
      let formatted = this.sql
        .replace(/\s+/g, ' ')
        .replace(/\s*,\s*/g, ', ')
        .replace(/\(\s+/g, '(')
        .replace(/\s+\)/g, ')')
        .replace(/\bSELECT\b/gi, '\nSELECT')
        .replace(/\bFROM\b/gi, '\nFROM')
        .replace(/\bWHERE\b/gi, '\nWHERE')
        .replace(/\bAND\b/gi, '\n  AND')
        .replace(/\bOR\b/gi, '\n  OR')
        .replace(/\bORDER BY\b/gi, '\nORDER BY')
        .replace(/\bGROUP BY\b/gi, '\nGROUP BY')
        .replace(/\bHAVING\b/gi, '\nHAVING')
        .replace(/\bLIMIT\b/gi, '\nLIMIT')
        .replace(/\bJOIN\b/gi, '\nJOIN')
        .replace(/\bLEFT JOIN\b/gi, '\nLEFT JOIN')
        .replace(/\bRIGHT JOIN\b/gi, '\nRIGHT JOIN')
        .replace(/\bINNER JOIN\b/gi, '\nINNER JOIN')
        .replace(/\bON\b/gi, '\n  ON')
        .replace(/\bUNION\b/gi, '\nUNION')
        .trim();
      this.sql = formatted;
    },
    clearSql() {
      this.sql = '';
    },
    insertTemplate(type) {
      this.sql = this.sqlTemplates[type] || '';
    },
    exportCsv() {
      if (!this.result.rows || this.result.rows.length === 0) return;
      
      const headers = this.result.columns.join(',');
      const rows = this.result.rows.map(row => 
        this.result.columns.map(col => {
          const val = row[col];
          if (val === null) return '';
          if (typeof val === 'string' && (val.includes(',') || val.includes('"') || val.includes('\n'))) {
            return '"' + val.replace(/"/g, '""') + '"';
          }
          return val;
        }).join(',')
      ).join('\n');
      
      const csv = headers + '\n' + rows;
      const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = 'query_result_' + new Date().toISOString().slice(0, 10) + '.csv';
      link.click();
      this.$message.success('已导出CSV文件');
    },
    copyResult() {
      if (!this.result.rows || this.result.rows.length === 0) return;
      
      const text = JSON.stringify(this.result.rows, null, 2);
      navigator.clipboard.writeText(text).then(() => {
        this.$message.success('已复制到剪贴板');
      }).catch(() => {
        this.$message.error('复制失败');
      });
    }
  }
};
</script>

<style scoped>
.db-invoke-tool {
  padding: 20px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.config-card,
.query-card,
.result-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sql-editor-section {
  margin-bottom: 15px;
}

.editor-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  align-items: center;
}

.toolbar-hint {
  margin-left: auto;
}

.sql-textarea :deep(textarea) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.sql-templates {
  display: flex;
  align-items: center;
  gap: 5px;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
}

.template-label {
  color: #909399;
  font-size: 13px;
}

.result-meta {
  display: flex;
  gap: 10px;
}

.result-toolbar {
  margin-bottom: 10px;
  display: flex;
  gap: 10px;
}

.result-table {
  width: 100%;
}

.cell-value {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
}

.null-value {
  color: #909399;
  font-style: italic;
}

.error-result {
  padding: 20px 0;
}

.error-detail {
  margin-top: 10px;
  padding: 10px;
  background: #fef0f0;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 200px;
  overflow: auto;
}

@media (max-width: 768px) {
  .el-col {
    margin-bottom: 10px;
  }
}
</style>
