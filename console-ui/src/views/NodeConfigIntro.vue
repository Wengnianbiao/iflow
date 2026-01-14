<template>
  <div class="node-config-intro">
    <div class="page-header">
      <h2>节点配置介绍</h2>
      <p class="subtitle">详细了解不同节点类型的配置项说明和节点调度配置</p>
    </div>

    <el-tabs v-model="activeTab" type="card">
      <!-- HTTP节点配置 -->
      <el-tab-pane label="HTTP节点配置" name="http">
        <el-card shadow="never">
          <div slot="header" class="section-header">
            <i class="el-icon-link"></i>
            <span>HTTP节点元数据配置</span>
          </div>

          <div class="config-item">
            <h3>url <span class="required">*必填</span></h3>
            <p class="item-desc">目标接口的完整URL地址，支持HTTP和HTTPS协议</p>
            <div class="item-example">
              <span class="example-label">示例：</span>
              <code>http://api.example.com/pacs/createOrder</code>
            </div>
          </div>

          <div class="config-item">
            <h3>method <span class="required">*必填</span></h3>
            <p class="item-desc">HTTP请求方法</p>
            <div class="item-options">
              <el-tag size="small">GET</el-tag>
              <el-tag size="small" type="success">POST</el-tag>
              <el-tag size="small" type="warning">PUT</el-tag>
              <el-tag size="small" type="danger">DELETE</el-tag>
            </div>
            <div class="item-tip">
              <i class="el-icon-info"></i>
              <span>大部分接口对接使用POST方法</span>
            </div>
          </div>

          <div class="config-item">
            <h3>headers <span class="optional">可选</span></h3>
            <p class="item-desc">HTTP请求头，JSON对象格式，常用于设置认证信息、内容类型等</p>
            <div class="item-example">
              <span class="example-label">示例：</span>
              <pre class="code-block">{
  "Content-Type": "application/json",
  "Authorization": "Bearer your_token_here",
  "X-Request-ID": "{{requestId}}"
}</pre>
            </div>
          </div>

          <div class="config-item">
            <h3>timeout <span class="optional">可选</span></h3>
            <p class="item-desc">请求超时时间，单位：毫秒，默认30000ms（30秒）</p>
            <div class="item-example">
              <span class="example-label">示例：</span>
              <code>5000</code>
              <span class="example-note">（5秒超时）</span>
            </div>
          </div>

          <div class="complete-example">
            <div class="example-header">
              <i class="el-icon-document"></i>
              <span>完整配置示例</span>
            </div>
            <pre class="code-block full-example">{
  "url": "http://api.hospital.com/pacs/createOrder",
  "method": "POST",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "timeout": 10000
}</pre>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- WebService节点配置 -->
      <el-tab-pane label="WebService节点配置" name="webservice">
        <el-card shadow="never">
          <div slot="header" class="section-header">
            <i class="el-icon-message"></i>
            <span>WebService节点元数据配置</span>
          </div>

          <div class="config-item">
            <h3>wsdlUrl <span class="required">*必填</span></h3>
            <p class="item-desc">WebService的WSDL地址</p>
            <div class="item-example">
              <span class="example-label">示例：</span>
              <code>http://api.example.com/services/PatientService?wsdl</code>
            </div>
          </div>

          <div class="config-item">
            <h3>namespace <span class="required">*必填</span></h3>
            <p class="item-desc">WebService命名空间，通常在WSDL文档的targetNamespace中定义</p>
            <div class="item-example">
              <span class="example-label">示例：</span>
              <code>http://service.hospital.com/patient</code>
            </div>
          </div>

          <div class="config-item">
            <h3>methodName <span class="required">*必填</span></h3>
            <p class="item-desc">要调用的WebService方法名</p>
            <div class="item-example">
              <span class="example-label">示例：</span>
              <code>CreatePacsOrder</code>
            </div>
          </div>

          <div class="config-item">
            <h3>soapAction <span class="optional">可选</span></h3>
            <p class="item-desc">SOAP操作的URI，部分WebService需要指定</p>
            <div class="item-example">
              <span class="example-label">示例：</span>
              <code>http://service.hospital.com/patient/CreatePacsOrder</code>
            </div>
          </div>

          <div class="complete-example">
            <div class="example-header">
              <i class="el-icon-document"></i>
              <span>完整配置示例</span>
            </div>
            <pre class="code-block full-example">{
  "wsdlUrl": "http://api.hospital.com/services/PatientService?wsdl",
  "namespace": "http://service.hospital.com/patient",
  "methodName": "CreatePacsOrder",
  "soapAction": "http://service.hospital.com/patient/CreatePacsOrder"
}</pre>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 数据库节点配置 -->
      <el-tab-pane label="数据库节点配置" name="database">
        <el-card shadow="never">
          <div slot="header" class="section-header">
            <i class="el-icon-coin"></i>
            <span>数据库节点元数据配置</span>
          </div>

          <div class="config-item">
            <h3>dataSource <span class="required">*必填</span></h3>
            <p class="item-desc">数据源名称，对应application.yml中配置的数据源</p>
            <div class="item-example">
              <span class="example-label">示例：</span>
              <code>hems</code>
              <span class="example-note">（对应spring.datasource.hems配置）</span>
            </div>
          </div>

          <div class="config-item">
            <h3>sql <span class="required">*必填</span></h3>
            <p class="item-desc">要执行的SQL语句，支持参数占位符</p>
            <div class="item-example">
              <span class="example-label">查询示例：</span>
              <pre class="code-block">SELECT * FROM patient WHERE patient_code = #{patientCode}</pre>
            </div>
            <div class="item-example">
              <span class="example-label">插入示例：</span>
              <pre class="code-block">INSERT INTO pacs_order (patient_code, order_no, status) 
VALUES (#{patientCode}, #{orderNo}, 'CREATED')</pre>
            </div>
          </div>

          <div class="config-item">
            <h3>操作类型</h3>
            <p class="item-desc">根据SQL类型自动识别，无需手动配置</p>
            <div class="item-options">
              <el-tag size="small" type="info">SELECT - 查询操作</el-tag>
              <el-tag size="small" type="success">INSERT - 插入操作</el-tag>
              <el-tag size="small" type="warning">UPDATE - 更新操作</el-tag>
              <el-tag size="small" type="danger">DELETE - 删除操作</el-tag>
            </div>
          </div>

          <div class="complete-example">
            <div class="example-header">
              <i class="el-icon-document"></i>
              <span>完整配置示例</span>
            </div>
            <pre class="code-block full-example">{
  "dataSource": "hems",
  "sql": "SELECT patient_name, patient_code, id_card FROM patient WHERE patient_code = #{patientCode}"
}</pre>
          </div>

          <el-alert
            title="安全提示"
            type="warning"
            :closable="false"
            show-icon
          >
            <div slot="default">
              <ul class="tips-list">
                <li>使用参数占位符 <code>#{paramName}</code> 防止SQL注入</li>
                <li>避免使用 <code>SELECT *</code>，明确指定需要的字段</li>
                <li>写操作（INSERT/UPDATE/DELETE）需谨慎，建议先在测试环境验证</li>
              </ul>
            </div>
          </el-alert>
        </el-card>
      </el-tab-pane>

      <!-- 节点调度配置 -->
      <el-tab-pane label="节点调度配置" name="schedule">
        <el-card shadow="never">
          <div slot="header" class="section-header">
            <i class="el-icon-s-operation"></i>
            <span>节点调度配置说明</span>
          </div>

          <el-alert
            title="什么是节点调度？"
            type="info"
            :closable="false"
            show-icon
          >
            <div slot="default">
              <p>节点调度用于配置多节点工作流的流转规则，包括：</p>
              <ul class="tips-list">
                <li>根据条件决定下一步执行哪个节点</li>
                <li>配置下游节点的入参来源</li>
                <li>对业务数据进行预过滤</li>
              </ul>
            </div>
          </el-alert>

          <div class="config-item">
            <h3>调度表达式（schedule_expr）</h3>
            <p class="item-desc">使用SpEL表达式配置条件流转，返回下一跳节点ID数组</p>
            <div class="item-example">
              <span class="example-label">示例1 - 单节点流转：</span>
              <pre class="code-block">[2]</pre>
              <span class="example-note">执行完当前节点后，流转到节点ID为2的节点</span>
            </div>
            <div class="item-example">
              <span class="example-label">示例2 - 条件流转：</span>
              <pre class="code-block">#data['code'] == '200' ? [2] : [3]</pre>
              <span class="example-note">响应码为200时流转到节点2，否则流转到节点3</span>
            </div>
            <div class="item-example">
              <span class="example-label">示例3 - 并行流转：</span>
              <pre class="code-block">[2, 3, 4]</pre>
              <span class="example-note">同时执行节点2、3、4，结果自动合并</span>
            </div>
          </div>

          <div class="config-item">
            <h3>参数来源类型（schedule_param_source_type）</h3>
            <p class="item-desc">配置下游节点的入参数据来源</p>
            <div class="param-sources">
              <el-card shadow="hover" class="source-card">
                <h4>ORIGINAL（原始入参）</h4>
                <p>下游节点使用最初的业务数据作为入参</p>
                <div class="source-scenario">
                  <span class="scenario-label">使用场景：</span>
                  <span>多个节点需要使用相同的原始数据</span>
                </div>
              </el-card>
              <el-card shadow="hover" class="source-card">
                <h4>PREVIOUS（上游响应）</h4>
                <p>下游节点使用上一个节点的响应结果作为入参</p>
                <div class="source-scenario">
                  <span class="scenario-label">使用场景：</span>
                  <span>下游节点依赖上游节点的返回数据</span>
                </div>
              </el-card>
            </div>
          </div>

          <div class="config-item">
            <h3>参数过滤表达式（param_filter_expr）</h3>
            <p class="item-desc">使用SpEL表达式过滤业务数据，只处理符合条件的数据</p>
            <div class="item-example">
              <span class="example-label">示例1 - 过滤VIP患者：</span>
              <pre class="code-block">#data['vipLevel'] > 3</pre>
              <span class="example-note">只有VIP等级大于3的患者才会调用该节点</span>
            </div>
            <div class="item-example">
              <span class="example-label">示例2 - 过滤特定状态：</span>
              <pre class="code-block">#data['status'] == 'PENDING'</pre>
              <span class="example-note">只处理状态为PENDING的数据</span>
            </div>
          </div>

          <div class="config-item">
            <h3>拆分字段（split_field）</h3>
            <p class="item-desc">指定数组字段进行拆分，对每个元素并行执行节点</p>
            <div class="item-example">
              <span class="example-label">示例：</span>
              <pre class="code-block">patients</pre>
              <span class="example-note">将patients数组拆分，对每个患者并行调用接口</span>
            </div>
            <div class="item-tip">
              <i class="el-icon-warning"></i>
              <span>适用于批量处理场景，可大幅提升性能</span>
            </div>
          </div>

          <div class="complete-example">
            <div class="example-header">
              <i class="el-icon-document"></i>
              <span>完整调度配置示例</span>
            </div>
            <div class="example-desc">
              场景：查询患者信息后，根据VIP等级决定调用高端或普通体检接口
            </div>
            <pre class="code-block full-example">{
  "schedule_expr": "#data['vipLevel'] >= 3 ? [2] : [3]",
  "schedule_param_source_type": "PREVIOUS",
  "param_filter_expr": "#data['status'] == 'ACTIVE'",
  "keyword_field": "patientCode"
}</pre>
            <div class="example-explanation">
              <p><strong>说明：</strong></p>
              <ul>
                <li>只处理状态为ACTIVE的患者</li>
                <li>VIP等级≥3的流转到节点2（高端体检接口）</li>
                <li>VIP等级&lt;3的流转到节点3（普通体检接口）</li>
                <li>下游节点使用上游查询的患者信息作为入参</li>
              </ul>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
export default {
  name: 'NodeConfigIntro',
  data() {
    return {
      activeTab: 'http'
    };
  },
  methods: {
    navigateTo(path) {
      this.$router.push(path);
    },
    goBack() {
      this.$router.go(-1);
    }
  }
};
</script>

<style scoped>
.node-config-intro {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: 30px;
}

.page-header h2 {
  color: #303133;
  margin-bottom: 10px;
}

.subtitle {
  color: #606266;
  font-size: 14px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: bold;
  font-size: 16px;
}

.section-header i {
  font-size: 20px;
  color: #409eff;
}

.config-item {
  margin: 30px 0;
  padding-bottom: 30px;
  border-bottom: 1px dashed #e4e7ed;
}

.config-item:last-child {
  border-bottom: none;
}

.config-item h3 {
  color: #303133;
  font-size: 16px;
  margin-bottom: 10px;
}

.required {
  color: #f56c6c;
  font-size: 12px;
  font-weight: normal;
  margin-left: 5px;
}

.optional {
  color: #909399;
  font-size: 12px;
  font-weight: normal;
  margin-left: 5px;
}

.item-desc {
  color: #606266;
  line-height: 1.8;
  margin-bottom: 15px;
}

.item-example {
  margin: 10px 0;
  padding: 12px 15px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.example-label {
  font-weight: bold;
  color: #303133;
  margin-right: 8px;
}

.example-note {
  color: #909399;
  font-size: 13px;
  margin-left: 8px;
}

.code-block {
  background-color: #1e1e1e;
  color: #f8f8f2;
  padding: 12px 15px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  overflow-x: auto;
  margin: 8px 0;
}

.full-example {
  margin-top: 10px;
}

.item-options {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin: 10px 0;
}

.item-tip {
  margin-top: 10px;
  padding: 10px 15px;
  background-color: #ecf5ff;
  border-left: 4px solid #409eff;
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-tip i {
  color: #409eff;
  font-size: 16px;
}

.complete-example {
  margin: 30px 0;
  padding: 20px;
  background-color: #f9f9f9;
  border-radius: 8px;
}

.example-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 15px;
}

.example-header i {
  font-size: 18px;
  color: #409eff;
}

.example-desc {
  color: #606266;
  margin-bottom: 10px;
  font-style: italic;
}

.example-explanation {
  margin-top: 15px;
  padding: 15px;
  background-color: #fff;
  border-radius: 4px;
}

.example-explanation p {
  margin-bottom: 10px;
  color: #303133;
}

.example-explanation ul {
  margin: 10px 0;
  padding-left: 20px;
}

.example-explanation li {
  color: #606266;
  line-height: 1.8;
  margin: 5px 0;
}

.param-sources {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin: 20px 0;
}

.source-card {
  transition: transform 0.3s;
}

.source-card:hover {
  transform: translateY(-3px);
}

.source-card h4 {
  color: #409eff;
  margin-bottom: 10px;
}

.source-card p {
  color: #606266;
  line-height: 1.6;
  margin-bottom: 15px;
}

.source-scenario {
  padding-top: 15px;
  border-top: 1px dashed #e4e7ed;
  font-size: 13px;
}

.scenario-label {
  color: #909399;
  font-weight: bold;
}

.tips-list {
  list-style: none;
  padding: 0;
  margin: 10px 0 0 0;
}

.tips-list li {
  padding: 5px 0;
  color: #606266;
  line-height: 1.6;
  position: relative;
  padding-left: 20px;
}

.tips-list li:before {
  content: "•";
  position: absolute;
  left: 0;
  color: #409eff;
  font-weight: bold;
}

.tips-list code {
  background-color: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  color: #e6a23c;
  font-family: 'Courier New', monospace;
}

.bottom-nav {
  margin-top: 40px;
  padding-top: 20px;
  border-top: 2px solid #e4e7ed;
  text-align: center;
  display: flex;
  justify-content: center;
  gap: 15px;
}
</style>
