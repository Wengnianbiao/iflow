<template>
  <div class="quick-start">
    <div class="intro-section">
      <p class="intro-text">
        本指南将带您完成从0到1完成接口对接的全过程，只需5个步骤，即可实现接口的可视化配置与调用。
      </p>
    </div>

    <el-steps :active="currentStep" finish-status="success" align-center class="steps-wrapper">
      <el-step title="1、配置工作流节点" icon="el-icon-edit"></el-step>
      <el-step title="2、配置工作流并关联节点" icon="el-icon-connection"></el-step>
      <el-step title="3、配置节点参数映射" icon="el-icon-share"></el-step>
      <el-step title="4、Mock测试验证" icon="el-icon-view"></el-step>
      <el-step title="5、生产环境调用" icon="el-icon-s-promotion"></el-step>
    </el-steps>

    <div class="step-content">
      <!-- 步骤1: 配置接口节点 -->
      <el-card v-show="currentStep === 0" shadow="hover" class="step-card">
        <div class="step-description">
          <p>创建工作流节点，配置目标接口的基本信息：接口名称、协议类型、元数据等</p>
          <div style="margin-top: 15px;">
            <el-button type="primary" size="small" @click="openInNewTab('/workflow/nodes')">
              <i class="el-icon-setting"></i> 前往工作流节点页面配置（新窗口打开）
            </el-button>
          </div>
        </div>

        <el-divider content-position="left">操作指引</el-divider>
        
        <div class="guide-content">
          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step0.item1 = !collapsedSteps.step0.item1">
              <div class="guide-number">1</div>
              <div class="guide-text">
                <h4>
                  新增节点
                  <i :class="['collapse-icon', collapsedSteps.step0.item1 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step0.item1">
              <p>点击"新增节点"按钮</p>
            </div>
          </div>

          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step0.item2 = !collapsedSteps.step0.item2">
              <div class="guide-number">2</div>
              <div class="guide-text">
                <h4>
                  填写节点表单字段
                  <i :class="['collapse-icon', collapsedSteps.step0.item2 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step0.item2">
              
              <el-alert
                title="配置提示"
                type="success"
                :closable="false"
                show-icon
                style="margin: 15px 0;"
              >
                <div slot="default">
                  <ul class="tips-list">
                    <li><strong>必填字段</strong>：节点名称、节点类型、节点类型、节点元数据配置</li>
                    <li><strong>建议填写</strong>：关键字字段（方便日志查询）</li>
                    <li><strong>高级功能</strong>：拆分字段（三方接口仅支持对象数据类型）、参数过滤表达式（条件执行）</li>
                  </ul>
                </div>
              </el-alert>

              <!-- 节点名称 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">1</span>
                    <h5>节点名称</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">为节点起一个清晰易懂的名称，建议使用业务场景描述，方便后续管理</p>
                <div class="field-examples">
                  <div class="example-label">推荐命名示例：</div>
                  <ul class="example-list">
                    <li>创建PACS申请单</li>
                    <li>创建档案</li>
                    <li>LIS状态推送jarvis</li>
                    <li>拟退账单</li>
                  </ul>
                </div>
              </div>

              <!-- 所属工作流 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">2</span>
                    <h5>所属工作流</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">事实上，接口节点并不强制归属于某个工作流，一个节点接口可以被多个工作流所使用调度。此配置仅是为了当接口节点仅被一个工作流所调度时方便筛选检索</p>
              </div>

              <!-- 节点类型与元数据配置 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">3</span>
                    <h5>节点类型与元数据配置</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">根据目标接口的协议类型选择对应的节点类型，并配置相应的元数据，务必使用JSON格式配置元数据信息</p>
                
                <div class="type-options">
                  <el-card 
                    shadow="hover" 
                    class="type-option" 
                    :class="{ 'type-selected': nodeConfigTab === 'http' }"
                    @click.native="nodeConfigTab = 'http'"
                  >
                    <div class="type-name">HTTP</div>
                    <p class="type-brief">适用于RESTful API、HTTP接口</p>
                    <div class="type-usage">
                      <span class="usage-label">使用场景：</span>
                      <span>JSON格式的接口,jarvis的入参都为JSON格式</span>
                    </div>
                  </el-card>
                  <el-card 
                    shadow="hover" 
                    class="type-option"
                    :class="{ 'type-selected': nodeConfigTab === 'webservice' }"
                    @click.native="nodeConfigTab = 'webservice'"
                  >
                    <div class="type-name">WEBSERVICE</div>
                    <p class="type-brief">适用于SOAP协议的WebService服务</p>
                    <div class="type-usage">
                      <span class="usage-label">使用场景：</span>
                      <span>XML格式的SOAP接口</span>
                    </div>
                  </el-card>
                  <el-card 
                    shadow="hover" 
                    class="type-option"
                    :class="{ 'type-selected': nodeConfigTab === 'database' }"
                    @click.native="nodeConfigTab = 'database'"
                  >
                    <div class="type-name">数据库</div>
                    <p class="type-brief">适用于直连数据库执行SQL操作</p>
                    <div class="type-usage">
                      <span class="usage-label">使用场景：</span>
                      <span>数据库查询、数据同步、SQL执行</span>
                    </div>
                  </el-card>
                </div>

                <div style="margin-top: 20px;">                  
                  <!-- HTTP节点配置示例 -->
                  <div v-show="nodeConfigTab === 'http'" style="margin-bottom: 20px;">
                    <div style="margin-bottom: 15px;">
                      <div style="margin-bottom: 8px;">
                        <span style="font-weight: bold; color: #303133; font-size: 13px;">缺省值版本（仅url，method和headers使用默认值）：</span>
                      </div>
                      <div class="code-with-copy">
                        <pre class="code-block">{{ httpConfigExamples.default }}</pre>
                        <el-button 
                          class="copy-btn" 
                          size="mini" 
                          icon="el-icon-document-copy"
                          @click="copyToClipboard(httpConfigExamples.default)"
                        >复制</el-button>
                      </div>
                    </div>
                    
                    <div>
                      <div style="margin-bottom: 8px;">
                        <span style="font-weight: bold; color: #303133; font-size: 13px;">完整版本（包含所有字段和认证信息）：</span>
                      </div>
                      <div class="code-with-copy">
                        <pre class="code-block">{{ httpConfigExamples.full }}</pre>
                        <el-button 
                          class="copy-btn" 
                          size="mini" 
                          icon="el-icon-document-copy"
                          @click="copyToClipboard(httpConfigExamples.full)"
                        >复制</el-button>
                      </div>
                    </div>
                  </div>
                  
                  <!-- HTTP节点配置 -->
                  <div v-show="nodeConfigTab === 'http'">
                    <div class="item-tip warning" style="margin-bottom: 15px;">
                      <i class="el-icon-warning"></i>
                      <span>框架仅支持静态的请求头信息配置，对于动态的请求头接口请使用SPI扩展对接</span>
                    </div>
                    
                    <div class="config-item">
                      <h5>url <span class="required">*必填</span></h5>
                      <p class="item-desc">目标接口的完整URL地址，仅支持HTTP协议</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">http://10.192.50.204:8088/PACS/HssExmeReceive</span></li>
                          <li><span class="code-inline">http://gapi-c2f.seenew.info/hbos-test/open-api/gw-api/hbos-business-thirdparty-integration/common/rhc/createResidentOrgRegistInfo</span></li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>method <span class="optional">可选</span></h5>
                      <p class="item-desc">HTTP请求方法，缺省值为POST，支持GET、POST方法</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">POST</span> - 缺省值，适用于大多数接口</li>
                          <li><span class="code-inline">GET</span> - 适用于查询类接口</li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>headers <span class="optional">可选</span></h5>
                      <p class="item-desc">JSON对象格式的HTTP请求头信息，用于设置内容类型、认证信息等，缺省值为 <span class="code-inline">{"Content-Type": "application/json"}</span>，不填写时使用缺省值，如需添加认证信息或其他请求头，可完整配置</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">{"Content-Type": "application/json"}</span> - 缺省值</li>
                          <li><span class="code-inline">{"Content-Type": "application/json", "App-Key": "mvTLtFJGmectBac8unwIf1bF"}</span> - 包含认证信息</li>
                        </ul>
                      </div>
                    </div>
                  </div>
                  
                  <!-- WebService节点配置示例 -->
                  <div v-show="nodeConfigTab === 'webservice'" style="margin-bottom: 20px;">
                    <div style="margin-bottom: 15px;">
                      <div style="margin-bottom: 8px;">
                        <span style="font-weight: bold; color: #303133; font-size: 13px;">缺省值版本（仅必填字段，method和headers使用默认值且三方响应为业务数据XML不包含soap信封）：</span>
                      </div>
                      <div class="code-with-copy">
                        <pre class="code-block">{{ webserviceConfigExamples.default }}</pre>
                        <el-button 
                          class="copy-btn" 
                          size="mini" 
                          icon="el-icon-document-copy"
                          @click="copyToClipboard(webserviceConfigExamples.default)"
                        >复制</el-button>
                      </div>
                    </div>
                    
                    <div style="margin-bottom: 15px;">
                      <div style="margin-bottom: 8px;">
                        <span style="font-weight: bold; color: #303133; font-size: 13px;">常规版本（包含url、soupTemplate、elementName）：</span>
                      </div>
                      <div class="code-with-copy">
                        <pre class="code-block">{{ webserviceConfigExamples.normal }}</pre>
                        <el-button 
                          class="copy-btn" 
                          size="mini" 
                          icon="el-icon-document-copy"
                          @click="copyToClipboard(webserviceConfigExamples.normal)"
                        >复制</el-button>
                      </div>
                    </div>
                    
                    <div>
                      <div style="margin-bottom: 8px;">
                        <span style="font-weight: bold; color: #303133; font-size: 13px;">完整版本（包含所有字段和响应配置）：</span>
                      </div>
                      <div class="code-with-copy">
                        <pre class="code-block">{{ webserviceConfigExamples.full }}</pre>
                        <el-button 
                          class="copy-btn" 
                          size="mini" 
                          icon="el-icon-document-copy"
                          @click="copyToClipboard(webserviceConfigExamples.full)"
                        >复制</el-button>
                      </div>
                    </div>
                  </div>
                  
                  <!-- WebService节点配置 -->
                  <div v-show="nodeConfigTab === 'webservice'">
                    <div class="item-tip warning" style="margin-bottom: 15px;">
                      <i class="el-icon-warning"></i>
                      <span>框架只处理较为规范的SOAP协议，对于完全不遵守SOAP协议规范去封装请求和响应的厂商接口建议通过SPI拓展对接</span>
                    </div>
                    <h4 style="color: #303133; font-size: 15px; margin: 20px 0 15px 0; font-weight: 600; border-left: 4px solid #409eff; padding-left: 10px;">请求配置</h4>
                    
                    <div class="config-item">
                      <h5>url <span class="required">*必填</span></h5>
                      <p class="item-desc">WebService接口的完整URL地址，用于发送SOAP请求</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">http://10.20.0.141:8805/soup-api/pacs_apply</span></li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>method <span class="optional">可选</span></h5>
                      <p class="item-desc">HTTP请求方法，缺省值为POST</p>
                    </div>

                    <div class="config-item">
                      <h5>headers <span class="optional">可选</span></h5>
                      <p class="item-desc">HTTP请求头，JSON对象格式，用于设置内容类型、认证信息等，缺省值为 <span class="code-inline">{"Content-Type": "text/xml;charset=UTF-8"}</span>，不填写时使用缺省值</p>
                    </div>

                    <div class="config-item">
                      <h5>soupTemplate <span class="required">*必填</span></h5>
                      <p class="item-desc">SOAP请求模板，使用 <span class="code-inline">${inputParam}</span> 作为占位符，框架会将节点参数配置转换后的业务数据填充到该位置，生成完整的SOAP请求消息</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">&lt;soapenv:Envelope&gt;...&lt;her:pInput&gt;&lt;![CDATA[${inputParam}]]&gt;&lt;/her:pInput&gt;...&lt;/soapenv:Envelope&gt;</span></li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>dataType <span class="optional">可选</span></h5>
                      <p class="item-desc">业务数据格式，值为 <span class="code-inline">json</span> 或 <span class="code-inline">xml</span>，用于标识请求中业务数据的格式类型，当业务数据在CDATA中且为JSON格式时需设置为json</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">json</span> - 业务数据为JSON格式</li>
                          <li><span class="code-inline">xml</span> - 业务数据为XML格式</li>
                        </ul>
                      </div>
                    </div>

                    <h4 style="color: #303133; font-size: 15px; margin: 30px 0 15px 0; font-weight: 600; border-left: 4px solid #67c23a; padding-left: 10px;">响应配置</h4>
                    <p style="color: #606266; margin-bottom: 15px; font-size: 13px;">针对响应，我们只关心业务数据，以下配置用于从SOAP响应中定位和解析业务数据</p>

                    <div class="config-item">
                      <h5>escape <span class="optional">可选</span></h5>
                      <p class="item-desc">是否使用转义符替换字符串中的特殊字符，用于批量过滤转义字符，当响应中包含转义字符需要还原时设置为true</p>
                    </div>

                    <div class="config-item">
                      <h5>elementName <span class="optional">可选</span></h5>
                      <p class="item-desc">根据标签名称定位到业务数据，当响应中业务数据所在的标签名唯一时，可直接通过标签名定位</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">request</span> - 通过request标签定位业务数据</li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>namespace <span class="optional">可选</span></h5>
                      <p class="item-desc">命名空间URI，与 <span class="code-inline">namespaceElementName</span> 和 <span class="code-inline">namespaceDataElementName</span> 组合使用，用于定位带命名空间的业务数据</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">http://herenit.com</span></li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>namespaceElementName <span class="optional">可选</span></h5>
                      <p class="item-desc">命名空间下的元素名，与 <span class="code-inline">namespace</span> 和 <span class="code-inline">namespaceDataElementName</span> 组合使用，用于定位业务数据</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">MessageIn</span></li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>namespaceDataElementName <span class="optional">可选</span></h5>
                      <p class="item-desc">业务数据元素名，支持递归路径（使用.分隔），与 <span class="code-inline">namespace</span> 和 <span class="code-inline">namespaceElementName</span> 组合使用，用于从响应中提取业务数据</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">pInput</span> - 直接元素名</li>
                          <li><span class="code-inline">param.request</span> - 递归路径</li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>isCDATA <span class="optional">可选</span></h5>
                      <p class="item-desc">业务数据是否为CDATA格式，缺省值为true。在解析业务数据时，用于标识三方接口响应是否将业务数据包裹在CDATA标签中，当业务数据在CDATA标签中时设置为true，否则设置为false</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">true</span> - 业务数据在CDATA标签中（缺省值）</li>
                          <li><span class="code-inline">false</span> - 业务数据不在CDATA标签中</li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>objectTag <span class="optional">可选</span></h5>
                      <p class="item-desc">用于标识哪些标签是对象结构，数组格式，当配置了该字段后，指定的标签会被解析为对象而非数组。对于嵌套的标签，如果不配置该字段，框架会按照数组解析</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">["PatientInfo", "OrderInfo"]</span> - 指定多个标签为对象结构</li>
                        </ul>
                      </div>
                    </div>
                  </div>

                  <!-- 数据库节点配置示例 -->
                  <div v-show="nodeConfigTab === 'database'" style="margin-bottom: 20px;">
                    <div>
                      <div style="margin-bottom: 8px;">
                        <span style="font-weight: bold; color: #303133; font-size: 13px;">配置示例（所有字段均为必填）：</span>
                      </div>
                      <div class="code-with-copy">
                        <pre class="code-block">{{ databaseConfigExamples.full }}</pre>
                        <el-button 
                          class="copy-btn" 
                          size="mini" 
                          icon="el-icon-document-copy"
                          @click="copyToClipboard(databaseConfigExamples.full)"
                        >复制</el-button>
                      </div>
                    </div>
                  </div>
                  
                  <!-- 数据库节点配置 -->
                  <div v-show="nodeConfigTab === 'database'">
                    <div class="config-item">
                      <h5>driverClassName <span class="required">*必填</span></h5>
                      <p class="item-desc">数据库驱动类名，用于建立数据库连接</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">com.microsoft.sqlserver.jdbc.SQLServerDriver</span> - SQL Server</li>
                          <li><span class="code-inline">com.mysql.cj.jdbc.Driver</span> - MySQL</li>
                          <li><span class="code-inline">org.opengauss.Driver</span> - OpenGauss</li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>operation <span class="required">*必填</span></h5>
                      <p class="item-desc">数据库操作类型，支持 SELECT、INSERT、UPDATE、DELETE</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">SELECT</span> - 查询操作</li>
                          <li><span class="code-inline">INSERT</span> - 插入操作</li>
                          <li><span class="code-inline">UPDATE</span> - 更新操作</li>
                          <li><span class="code-inline">DELETE</span> - 删除操作</li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>url <span class="required">*必填</span></h5>
                      <p class="item-desc">数据库连接URL，包含数据库地址、端口、数据库名等连接信息</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">jdbc:sqlserver://10.20.4.245:1433;DatabaseName=HEMS_V5;encrypt=false;trustServerCertificate=false</span> - SQL Server</li>
                          <li><span class="code-inline">jdbc:mysql://localhost:3306/mydb</span> - MySQL</li>
                          <li><span class="code-inline">jdbc:opengauss://172.16.16.147:5433/mydb?currentSchema=hems</span> - OpenGauss</li>
                        </ul>
                      </div>
                    </div>

                    <div class="config-item">
                      <h5>username <span class="required">*必填</span></h5>
                      <p class="item-desc">数据库用户名，用于连接数据库的认证信息</p>
                    </div>

                    <div class="config-item">
                      <h5>password <span class="required">*必填</span></h5>
                      <p class="item-desc">数据库密码，用于连接数据库的认证信息</p>
                    </div>

                    <div class="config-item">
                      <h5>sqlTemplate <span class="required">*必填</span></h5>
                      <p class="item-desc">SQL模板，通过参数占位符 <span class="code-inline">{paramName}</span>，框架会将节点参数配置转换后的值填充到占位符位置</p>
                      <div class="field-examples">
                        <div class="example-label">示例：</div>
                        <ul class="example-list">
                          <li><span class="code-inline">SELECT * FROM ReportDetail WHERE patid= {PatientCode}</span> - 单个参数</li>
                          <li><span class="code-inline">SELECT * FROM ReportDetail WHERE patid= {PatientCode} and Applyno IN {ApplyNoList}</span> - 多个参数（包含数组）</li>
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 关键字字段 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">4</span>
                    <h5>关键字字段</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">用于日志记录和数据追踪的关键标识字段名，方便后续查询和排查问题</p>
                <div class="field-examples">
                  <div class="example-label">配置说明：</div>
                  <ul class="example-list">
                    <li>默认根路径为上游入参</li>
                    <li>使用 <span class="code-inline">.</span> 符号访问嵌套对象的字段</li>
                    <li>不支持数组下标访问，只能配置对象字段路径</li>
                  </ul>
                </div>
                <div class="field-examples">
                  <div class="example-label">配置示例：</div>
                  <ul class="example-list">
                    <li><span class="code-inline">patientCode</span> - 获取根路径下的patientCode字段</li>
                    <li><span class="code-inline">patientInfo.patientCode</span> - 获取patientInfo对象中的patientCode字段</li>
                    <li><span class="code-inline">request.header.requestId</span> - 获取多层嵌套对象的requestId字段</li>
                  </ul>
                </div>
              </div>

              <!-- 拆分字段 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">5</span>
                    <h5>拆分字段</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">当三方接口不支持数组类型批量推送数据时，需要将数组业务数据拆分成对象类型，框架会根据数组大小自动调用相应次数的接口</p>
                
                <div class="usage-scenarios">
                  <h5 class="scenario-title">使用场景：</h5>
                  
                  <!-- 场景一 -->
                  <div class="scenario-card">
                    <div class="scenario-header">
                      <span class="scenario-badge">场景一</span>
                      <h5>申请单发送场景</h5>
                    </div>
                    <p class="scenario-desc">当三方接口不支持批量接收申请单数组时，需要将items数组拆分为单个对象，框架会根据items数组的大小自动调用相同次数的接口</p>
                    <div class="scenario-example">
                      <div class="example-label">实际案例：</div>
                      <p class="example-desc">申请单发送 - 拆分字段填写 <span class="code-inline">items</span>，框架会将items数组中的每个元素拆分为独立的对象，然后根据数组长度调用相应次数的接口</p>
                    </div>
                  </div>

                  <!-- 场景二 -->
                  <div class="scenario-card">
                    <div class="scenario-header">
                      <span class="scenario-badge">场景二</span>
                      <h5>数据上报场景</h5>
                    </div>
                    <p class="scenario-desc">通过数据库查询出上报数据后，框架默认使用data作为查询结果集，若三方接口不支持批量接收，需要对data数组进行拆分上报</p>
                    <div class="scenario-example">
                      <div class="example-label">实际案例：</div>
                      <p class="example-desc">数据上报 - 拆分字段填写 <span class="code-inline">data</span>，框架会将数据库查询返回的data数组拆分为单个对象，然后逐个上报给三方接口</p>
                    </div>
                  </div>
                </div>

                <div class="field-tip warning">
                  <i class="el-icon-warning"></i>
                  <span>拆分字段务必是数组类型，并且在节点参数配置时此字段为对象类型</span>
                </div>
              </div>

              <!-- 参数过滤表达式 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">6</span>
                    <h5>参数过滤表达式</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">使用SpEL表达式过滤业务数据，只有符合条件的数据才会调用该节点，减少无效调用</p>
                
                <div class="usage-scenarios">
                  <h5 class="scenario-title">使用场景：</h5>
                  
                  <!-- 场景一 -->
                  <div class="scenario-card">
                    <div class="scenario-header">
                      <span class="scenario-badge">场景一</span>
                      <h5>对上游入参的数组进行过滤</h5>
                    </div>
                    <p class="scenario-desc">针对数组类型的入参进行过滤处理，按照条件筛选数组元素，如果过滤后为空则不调用接口</p>
                    <div class="scenario-example">
                      <div class="example-label">实际案例：</div>
                      <p class="example-desc">申请单分发 - 把入参中的items按照不同科室进行分发，过滤PACS科室的项目，如果没有对应科室项目则不调用接口</p>
                      <div class="code-with-copy">
                        <pre class="code-block">(#tempItems = #data['items']?.?[departType == 'PACS']) == null || #tempItems.isEmpty() ? #data.clear() : #data.put('items', #tempItems)</pre>
                        <el-button 
                          class="copy-btn" 
                          size="mini" 
                          icon="el-icon-document-copy"
                          @click="copyToClipboard(`(#tempItems = #data['items']?.?[departType == 'PACS']) == null || #tempItems.isEmpty() ? #data.clear() : #data.put('items', #tempItems)`)"
                        >复制</el-button>
                      </div>
                      <div class="code-explanation">
                        <p><strong>表达式解释：</strong></p>
                        <ul>
                          <li><span class="code-inline">#data['items']?.?[departType == 'PACS']</span> - 从items数组中过滤出PACS科室的项目</li>
                          <li><span class="code-inline">#tempItems.isEmpty()</span> - 判断过滤后是否为空</li>
                          <li><span class="code-inline">#data.clear()</span> - 为空时清空入参，不调用接口</li>
                          <li><span class="code-inline">#data.put('items', #tempItems)</span> - 不为空时更新items为过滤后的结果</li>
                        </ul>
                      </div>
                    </div>
                  </div>

                  <!-- 场景二 -->
                  <div class="scenario-card">
                    <div class="scenario-header">
                      <span class="scenario-badge">场景二</span>
                      <h5>根据入参字段值决定是否调用接口</h5>
                    </div>
                    <p class="scenario-desc">根据入参中某个字段的值进行判断，决定是否调用该节点接口，实现接口编排和路由分发</p>
                    
                    <div class="scenario-example">
                      <div class="example-label">实际案例①：账单收费通知</div>
                      <p class="example-desc">一个对外接口根据字段值决定调用收费成功通知还是退费成功通知</p>
                      <div class="code-with-copy">
                        <pre class="code-block">#data['notifyType'] == 'CHARGE_SUCCESS' ? #data : #data.clear()</pre>
                        <el-button 
                          class="copy-btn" 
                          size="mini" 
                          icon="el-icon-document-copy"
                          @click="copyToClipboard(`#data['notifyType'] == 'CHARGE_SUCCESS' ? #data : #data.clear()`)"
                        >复制</el-button>
                      </div>
                    </div>

                    <div class="scenario-example">
                      <div class="example-label">实际案例②：报告获取接口分发</div>
                      <p class="example-desc">统一通过一个接口调用，根据厂商类型字段决定调度到哪个具体接口</p>
                      <div class="code-with-copy">
                        <pre class="code-block">#data['ServiceProviderType'] == 'PACS' ? #data : #data.clear()</pre>
                        <el-button 
                          class="copy-btn" 
                          size="mini" 
                          icon="el-icon-document-copy"
                          @click="copyToClipboard(`#data['ServiceProviderType'] == 'PACS' ? #data : #data.clear()`)"
                        >复制</el-button>
                      </div>
                      <div class="code-explanation">
                        <p><strong>表达式解释：</strong></p>
                        <ul>
                          <li><span class="code-inline">#data['ServiceProviderType'] == 'PACS'</span> - 判断厂商类型是否为PACS</li>
                          <li><span class="code-inline">? #data</span> - 条件成立时保留入参，调用接口</li>
                          <li><span class="code-inline">: #data.clear()</span> - 条件不成立时清空入参，不调用接口</li>
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="field-tip">
                  <i class="el-icon-info"></i>
                  <span>具体的分发依据字段可根据体软配置进行调整，以上示例仅供参考。</span>
                </div>
              </div>
            </div>
          </div>

          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step0.item3 = !collapsedSteps.step0.item3">
              <div class="guide-number">3</div>
              <div class="guide-text">
                <h4>
                  配置节点调度规则（可选）
                  <i :class="['collapse-icon', collapsedSteps.step0.item3 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step0.item3">
              <p>如需多节点流转，可单独在节点调度页面进行配置</p>
              <el-button type="text" size="small" @click="navigateTo('/workflow/node/schedule')">
                前往节点调度配置页面 <i class="el-icon-arrow-right"></i>
              </el-button>
            </div>
          </div>
        </div>

        <div class="step-actions">
          <el-button type="primary" @click="nextStep">下一步</el-button>
        </div>
      </el-card>

      <!-- 步骤2: 关联工作流 -->
      <el-card v-show="currentStep === 1" shadow="hover" class="step-card">
        <div class="step-description">
          <p>工作流是暴露给调用方的调用入口，将创建的节点关联到工作流中就可以实现接口调度</p>
          <div style="margin-top: 15px;">
            <el-button type="primary" size="small" @click="openInNewTab('/workflow')">
              <i class="el-icon-setting"></i> 前往工作流页面配置（新窗口打开）
            </el-button>
          </div>
        </div>
    
        <el-divider content-position="left">操作指引</el-divider>
        
        <div class="guide-content">
          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step1.item1 = !collapsedSteps.step1.item1">
              <div class="guide-number">1</div>
              <div class="guide-text">
                <h4>
                  新增工作流
                  <i :class="['collapse-icon', collapsedSteps.step1.item1 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step1.item1">
              <p>点击"新增工作流"按钮</p>
            </div>
          </div>

          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step1.item2 = !collapsedSteps.step1.item2">
              <div class="guide-number">2</div>
              <div class="guide-text">
                <h4>
                  填写工作流表单字段
                  <i :class="['collapse-icon', collapsedSteps.step1.item2 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step1.item2">
              <el-alert
                title="配置提示"
                type="success"
                :closable="false"
                show-icon
                style="margin: 15px 0;"
              >
                <div slot="default">
                  <ul class="tips-list">
                    <li><strong>必填字段</strong>：工作流名称、接口URI、参数类型、首节点</li>
                  </ul>
                </div>
              </el-alert>

              <!-- 工作流名称 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">1</span>
                    <h5>工作流名称</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">为工作流起一个清晰易懂的名称，建议使用业务场景描述，方便后续管理和识别</p>
                <div class="field-examples">
                  <div class="example-label">推荐命名示例：</div>
                  <ul class="example-list">
                    <li>新增账单</li>
                    <li>建档</li>
                    <li>申请单分发</li>
                    <li>报告获取</li>
                    <li>账单状态通知</li>
                  </ul>
                </div>
              </div>

              <!-- 接口URI -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">2</span>
                    <h5>接口URI</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">定义对外暴露的接口路径，调用方通过该路径调用工作流</p>
                <div class="field-examples">
                  <div class="example-label-no-check">配置方式：</div>
                  <ul class="config-method-list">
                    <li><strong>jarvis：</strong>使用jarvis入参中的BusinessMethod字段值作为接口URI
                      <div class="jarvis-examples-grid">
                        <div class="jarvis-example-item">查询档案接口：<span class="code-inline">QueryArchives</span></div>
                        <div class="jarvis-example-item">建档接口：<span class="code-inline">CreateArchives</span></div>
                        <div class="jarvis-example-item">更新档案接口：<span class="code-inline">UpdateArchives</span></div>
                        <div class="jarvis-example-item">挂号接口：<span class="code-inline">Registration</span></div>
                        <div class="jarvis-example-item">账单创建接口：<span class="code-inline">SyncHisChagSheet</span></div>
                        <div class="jarvis-example-item">账单删除接口：<span class="code-inline">SyncHisDelChagSheet</span></div>
                        <div class="jarvis-example-item">拟退账单接口：<span class="code-inline">SyncHisRevokeChagSheet</span></div>
                        <div class="jarvis-example-item">取消拟退接口：<span class="code-inline">SyncHisCancelRevokeChagSheet</span></div>
                        <div class="jarvis-example-item">支付状态查询接口：<span class="code-inline">SyncHisQueryChagSheetStatus</span></div>
                        <div class="jarvis-example-item">退费状态查询接口：<span class="code-inline">SyncHisQueryChagSheetRefundStatus</span></div>
                        <div class="jarvis-example-item">申请单创建接口：<span class="code-inline">SyncPacsLisNewApply</span></div>
                        <div class="jarvis-example-item">申请单取消接口：<span class="code-inline">SyncPacsLisCancelApply</span></div>
                        <div class="jarvis-example-item">团体建档接口：<span class="code-inline">GroupCreateArchives</span></div>
                        <div class="jarvis-example-item">团体档案更新接口：<span class="code-inline">GroupUpdateArchives</span></div>
                        <div class="jarvis-example-item">报告获取接口：<span class="code-inline">GetItemResult</span></div>
                      </div>
                    </li>
                    <li><strong>三方接口：</strong>使用<span class="code-inline">/agent-open-api/**</span>格式，通配符部分可自定
                      <ul class="sub-list">
                        <li>示例：<span class="code-inline">/agent-open-api/pacs-status</span></li>
                        <li>示例：<span class="code-inline">/agent-open-api/lis-report</span></li>
                      </ul>
                    </li>
                    <li><strong>自定义Controller：</strong>可通过编写Controller来实现自定义接口路径</li>
                  </ul>
                </div>
              </div>

              <!-- 参数类型 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">3</span>
                    <h5>参数类型</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">根据调用方入参格式选择JSON或XML，框架会根据参数类型进行相应的解析处理</p>
                <div class="field-examples">
                  <div class="example-label">参数类型说明：</div>
                  <ul class="example-list">
                    <li><strong>JSON：</strong>适用于RESTful协议
                      <ul class="sub-list">
                        <li>框架会自动解析为key-value结构</li>
                      </ul>
                    </li>
                    <li><strong>XML：</strong>适用于SOAP协议
                      <ul class="sub-list">
                        <li>框架会自动解析为key-value结构</li>
                        <li>需要通过入参元信息来定位业务数据标签</li>
                      </ul>
                    </li>
                  </ul>
                </div>
                <div class="field-tip warning">
                  <i class="el-icon-warning"></i>
                  <span><strong>XML特殊说明：</strong>1、不支持同名标签处理（比如同名标签依赖属性值区分）；2、对于不规范的XML结构，只能通过自定义Controller去处理解析</span>
                </div>
              </div>

              <!-- 入参元信息 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">4</span>
                    <h5>入参元信息</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">专门用于XML格式的入参解析，用于从调用方的XML入参中定位和提取业务数据。当工作流的参数类型为XML时，需要配置此字段来告诉框架如何解析业务数据</p>
                <div class="field-examples">
                  <div class="example-label">配置说明：</div>
                  <ul class="example-list">
                    <li><strong>elementName：</strong>根据标签名称定位业务数据
                      <ul class="sub-list">
                        <li>当XML入参中业务数据所在的标签名唯一时，可直接通过标签名定位</li>
                        <li>示例：<span class="code-inline">"elementName": "request"</span></li>
                      </ul>
                    </li>
                    <li><strong>namespace + namespaceElementName + namespaceDataElementName：</strong>用于定位带命名空间的业务数据
                      <ul class="sub-list">
                        <li><strong>namespace：</strong>命名空间URI，如 <span class="code-inline">"http://herenit.com"</span></li>
                        <li><strong>namespaceElementName：</strong>命名空间下的元素名，如 <span class="code-inline">"MessageIn"</span></li>
                        <li><strong>namespaceDataElementName：</strong>业务数据元素名，支持递归路径（使用.分隔），如 <span class="code-inline">"pInput"</span> 或 <span class="code-inline">"param.request"</span></li>
                      </ul>
                    </li>
                    <li><strong>isCDATA：</strong>业务数据是否在CDATA标签中
                      <ul class="sub-list">
                        <li><span class="code-inline">true</span> - 业务数据在CDATA标签中（缺省值）</li>
                        <li><span class="code-inline">false</span> - 业务数据不在CDATA标签中</li>
                      </ul>
                    </li>
                    <li><strong>objectTag：</strong>指定哪些标签应解析为对象而非数组
                      <ul class="sub-list">
                        <li>示例：<span class="code-inline">["PatientInfo", "OrderInfo"]</span></li>
                      </ul>
                    </li>
                  </ul>
                </div>
              </div>

              <!-- 首节点 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">5</span>
                    <h5>首节点</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">根据调用方需要调用的接口，按需配置首节点，支持单节点或多节点配置</p>
                <div class="field-examples">
                  <div class="example-label">配置场景：</div>
                  <ul class="example-list">
                    <li><strong>单节点：</strong>适用于单一业务场景
                      <ul class="sub-list">
                        <li>建档</li>
                        <li>创建账单</li>
                        <li>患者信息查询</li>
                      </ul>
                    </li>
                    <li><strong>多节点：</strong>适用于复杂业务场景
                      <ul class="sub-list">
                        <li>创建申请单（需要同时调用多个接口）</li>
                        <li>报告获取（需要从多个系统获取报告结果）</li>
                        <li>账单状态通知（需要分别通知jarvis收费和退费）</li>
                      </ul>
                    </li>
                  </ul>
                </div>
                <div class="field-tip warning">
                  <i class="el-icon-warning"></i>
                  <span><strong>多节点编排逻辑：</strong>框架会并行处理各个节点，并且会把相同的入参分发到各个节点，需要节点配置参数过滤表达式来判断是否需要截断节点调用或参数过滤</span>
                </div>
              </div>

              <!-- 状态 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">6</span>
                    <h5>状态</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">控制工作流的启用/禁用状态，禁用后该工作流将无法被调用</p>
                <div class="field-examples">
                  <div class="example-label">状态说明：</div>
                  <ul class="example-list">
                    <li><strong>启用：</strong>工作流正常可用，上游系统可以调用该接口</li>
                    <li><strong>禁用：</strong>工作流暂时不可用，可用于临时关闭接口或维护场景</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="step-actions">
          <el-button @click="prevStep">上一步</el-button>
          <el-button type="primary" @click="nextStep">下一步</el-button>
        </div>
      </el-card>

      <!-- 步骤3: 参数映射配置 -->
      <el-card v-show="currentStep === 2" shadow="hover" class="step-card">
        <div class="step-description">
          <p>配置上下游接口的参数映射关系，实现参数结构的自动转换。参数映射就做两件事：<strong>入参转换</strong> 和 <strong>出参转换</strong>，场景描述如下：</p>
        </div>
        
        <div class="usage-scenarios" style="margin-top: 5px;">
          <!-- 场景一 -->
          <div class="scenario-card">
            <div class="scenario-header">
              <span class="scenario-badge">场景一</span>
              <h5>账单接口流转（Jarvis → HIS）</h5>
            </div>
            <p class="scenario-desc">将Jarvis创建账单的入参转换为HIS需要的入参，再将HIS的响应转换为Jarvis需要的响应</p>
            <ul class="sub-list">
              <li>前置处理：将Jarvis创建账单的入参 → 转换为HIS需要的入参</li>
              <li>后置处理：将HIS的响应 → 转换为Jarvis需要的响应</li>
            </ul>
          </div>

          <!-- 场景二 -->
          <div class="scenario-card">
            <div class="scenario-header">
              <span class="scenario-badge">场景二</span>
              <h5>状态通知接口（LIS → Jarvis）</h5>
            </div>
            <p class="scenario-desc">LIS调用框架暴露的接口，将LIS状态通知入参转换为Jarvis需要的格式，再将Jarvis的出参转换为给LIS的响应</p>
            <ul class="sub-list">
              <li>前置处理：将LIS状态通知入参 → 转换为Jarvis需要的状态通知入参</li>
              <li>后置处理：将Jarvis的出参 → 转换为给LIS的响应</li>
            </ul>
          </div>
        </div>

        <div style="margin-top: 15px;">
          <el-button type="primary" size="small" @click="openInNewTab('/workflow/node/param/config')">
            <i class="el-icon-setting"></i> 前往节点参数配置页面（新窗口打开）
          </el-button>
        </div>

        <el-divider content-position="left">操作指引</el-divider>
                
        <div class="guide-content">
          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step2.item1 = !collapsedSteps.step2.item1">
              <div class="guide-number">1</div>
              <div class="guide-text">
                <h4>
                  新增节点参数配置
                  <i :class="['collapse-icon', collapsedSteps.step2.item1 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step2.item1">
              <p>点击'新增'按钮新增节点配置项</p>
            </div>
          </div>
        
          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step2.item2 = !collapsedSteps.step2.item2">
              <div class="guide-number">2</div>
              <div class="guide-text">
                <h4>
                  配置前置处理（请求参数映射）
                  <i :class="['collapse-icon', collapsedSteps.step2.item2 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step2.item2">
              <p>将调用方的入参转换为目标接口需要的请求参数格式，映射的节点数量应与目标接口的请求参数数量相同</p>
                      
              <el-divider content-position="left">配置字段说明</el-divider>
                      
              <el-alert
                title="配置提示"
                type="success"
                :closable="false"
                show-icon
                style="margin: 15px 0;"
              >
                <div slot="default">
                  <ul class="tips-list">
                    <li><strong>必填字段</strong>：节点、处理类型、源参数类型、目标参数、目标参数类型、映射类型</li>
                    <li><strong>字段值约束</strong>：处理类型必须选择“前置处理”</li>
                    <li><strong>建议填写</strong>：参数描述（方便理解和维护）</li>
                  </ul>
                </div>
              </el-alert>
        
              <!-- 节点 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">1</span>
                    <h5>节点</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">选择要配置参数映射的节点，即在步骤1中创建的工作流节点</p>
              </div>

              <!-- 处理类型 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">2</span>
                    <h5>处理类型</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">选择参数处理的阶段，前置处理对应请求参数映射</p>
                <div class="field-examples">
                  <div class="example-label">字段值约束：</div>
                  <ul class="example-list">
                    <li>必须选择：<strong>前置处理</strong></li>
                  </ul>
                </div>
              </div>

              <!-- 目标参数 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">3</span>
                    <h5>目标参数</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">目标接口需要的请求参数名称</p>
              </div>
        
              <!-- 目标参数类型 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">4</span>
                    <h5>目标参数类型</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">目标参数的数据类型，决定了如何构建参数结构</p>
                <div class="field-examples">
                  <div class="example-label">类型说明：</div>
                  <ul class="example-list">
                    <li><strong>STRING、INTEGER、LONG、BOOLEAN：</strong>基础数据类型</li>
                    <li><strong>OBJECT：</strong>对象类型，需要配置子节点构建嵌套结构</li>
                    <li><strong>ARRAY：</strong>数组类型，需要配置虚拟节点和子节点</li>
                    <li><strong>NONE：</strong>无类型，用于固定值或表达式映射</li>
                  </ul>
                </div>
              </div>

              <!-- 映射类型与规则 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">5</span>
                    <h5>映射类型与规则</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">定义参数如何映射，包括映射方式和具体规则</p>
                <div class="field-examples">
                  <div class="example-label">映射类型详解：</div>
                  <ul class="example-list">
                    <li><strong>NAME（值映射）：</strong>直接取值，最常用的映射方式
                      <ul class="sub-list">
                        <li>适用场景：字段名称不同但数据类型相同的直接映射</li>
                        <li>实战案例：将Jarvis的 <span class="code-inline">patientCode</span> 映射到HIS的 <span class="code-inline">patientId</span></li>
                        <li>映射规则：可为空，框架会自动按照源参数名取值</li>
                      </ul>
                    </li>
                    <li><strong>CONSTANT（固定值）：</strong>不依赖源数据，使用固定配置值
                      <ul class="sub-list">
                        <li>适用场景：固定的配置值、默认值、系统标识等</li>
                        <li>实战案例1：设置 <span class="code-inline">systemId</span> 为 <span class="code-inline">APP0013</span></li>
                        <li>实战案例2：设置 <span class="code-inline">version</span> 为 <span class="code-inline">1.0</span></li>
                        <li>映射规则：填写固定值，如 <span class="code-inline">APP0013</span>、<span class="code-inline">1.0</span></li>
                        <li>配置要点：源参数类型必须设置为 <span class="code-inline">NONE</span>，源参数名可为空</li>
                      </ul>
                    </li>
                    <li><strong>EXPRESSION（表达式）：</strong>使用SpEL表达式进行复杂转换
                      <ul class="sub-list">
                        <li>适用场景：数据类型转换、字符串拼接、条件判断、计算逻辑等</li>
                        <li>上下文变量：<span class="code-inline">#data</span>（当前层级数据）、<span class="code-inline">#parent</span>（父层级数据）、<span class="code-inline">#source</span>（根数据源）</li>
                        <li>映射规则：填写SpEL表达式</li>
                        <li>实战案例1：判断匿名 - <span class="code-inline">#source['identitycard'] == null ? '1' : '0'</span></li>
                        <li>实战案例2：类型转换 - <span class="code-inline">#source['patientCode'].toString()</span></li>
                        <li>实战案例3：字符拼接 - <span class="code-inline">'HIS_' + #source['patientId']</span></li>
                      </ul>
                    </li>
                  </ul>
                  <el-alert type="info" :closable="false" style="margin-top: 15px;">
                    <div slot="default">
                      <strong>完整配置示例：</strong>查看 <router-link to="/product-solutions" style="color: #409eff; font-weight: bold;">产品解决方案</router-link> 中的完整配置案例
                    </div>
                  </el-alert>
                </div>
              </div>
        
              <!-- 源参数名 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">6</span>
                    <h5>源参数名</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">调用方传入的参数字段名，支持嵌套路径（使用<span class="code-inline">.</span>符号分隔）</p>
                <div class="field-examples">
                  <div class="example-label">配置场景：</div>
                  <ul class="example-list">
                    <li><strong>直接字段：</strong>示例：<span class="code-inline">patientCode</span></li>
                    <li><strong>嵌套字段：</strong>示例：<span class="code-inline">request.patientId</span>、<span class="code-inline">patientInfo.name</span></li>
                    <li><strong>固定值映射：</strong>源参数可为空，源参数类型设置为NONE</li>
                  </ul>
                </div>
              </div>
        
              <!-- 源参数类型 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">6</span>
                    <h5>源参数名</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">调用方传入的参数字段名，支持嵌套路径（使用<span class="code-inline">.</span>符号分隔）</p>
                <div class="field-examples">
                  <div class="example-label">配置场景：</div>
                  <ul class="example-list">
                    <li><strong>直接字段：</strong>示例：<span class="code-inline">patientCode</span></li>
                    <li><strong>嵌套字段：</strong>示例：<span class="code-inline">request.patientId</span>、<span class="code-inline">patientInfo.name</span></li>
                    <li><strong>固定值映射：</strong>源参数可为空，源参数类型设置为NONE</li>
                  </ul>
                </div>
              </div>
        
              <!-- 源参数类型 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">7</span>
                    <h5>源参数类型</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">源参数的数据类型</p>
                <div class="field-examples">
                  <div class="example-label">类型说明：</div>
                  <ul class="example-list">
                    <li><strong>STRING、INTEGER、LONG、BOOLEAN：</strong>基础数据类型</li>
                    <li><strong>OBJECT：</strong>对象类型，用于嵌套结构</li>
                    <li><strong>ARRAY：</strong>数组类型</li>
                    <li><strong>NONE：</strong>无类型，用于固定值或表达式映射</li>
                  </ul>
                </div>
              </div>
        
              <!-- 父节点 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">8</span>
                    <h5>父节点</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">用于构建嵌套结构，选择当前参数的父节点，顶级节点不需要填写</p>
              </div>
        
              <!-- 参数描述 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">9</span>
                    <h5>参数描述</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">填写该参数的业务含义，方便理解和后续维护</p>
              </div>
            </div>
          </div>
        
          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step2.item3 = !collapsedSteps.step2.item3">
              <div class="guide-number">3</div>
              <div class="guide-text">
                <h4>
                  配置后置处理（响应参数映射）
                  <i :class="['collapse-icon', collapsedSteps.step2.item3 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step2.item3">
              <p>将目标接口的响应数据转换为上游调用方期望的响应参数格式，节点数量应与返回给上游的响应参数数量相同</p>
                      
              <el-divider content-position="left">配置字段说明</el-divider>
                      
              <el-alert
                title="配置提示"
                type="success"
                :closable="false"
                show-icon
                style="margin: 15px 0;"
              >
                <div slot="default">
                  <ul class="tips-list">
                    <li><strong>必填字段</strong>：节点、处理类型、目标参数、目标参数类型、映射类型、映射来源、源参数类型</li>
                    <li><strong>字段值约束</strong>：处理类型必须选择“后置处理”</li>
                    <li><strong>建议填写</strong>：参数描述（方便理解和维护）</li>
                  </ul>
                </div>
              </el-alert>
        
              <!-- 节点 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">1</span>
                    <h5>节点</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">选择要配置参数映射的节点，即在步骤1中创建的工作流节点</p>
              </div>

              <!-- 处理类型 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">2</span>
                    <h5>处理类型</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">选择参数处理的阶段，后置处理对应响应参数映射</p>
                <div class="field-examples">
                  <div class="example-label">字段值约束：</div>
                  <ul class="example-list">
                    <li>必须选择：<strong>后置处理</strong></li>
                  </ul>
                </div>
              </div>

              <!-- 目标参数 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">3</span>
                    <h5>目标参数</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">返回给上游调用方的响应参数名称</p>
              </div>
        
              <!-- 目标参数类型 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">4</span>
                    <h5>目标参数类型</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">目标参数的数据类型，决定了如何构建响应结构</p>
                <div class="field-examples">
                  <div class="example-label">类型说明：</div>
                  <ul class="example-list">
                    <li><strong>STRING、INTEGER、LONG、BOOLEAN：</strong>基础数据类型</li>
                    <li><strong>OBJECT：</strong>对象类型，需要配置子节点构建嵌套结构</li>
                    <li><strong>ARRAY：</strong>数组类型，需要配置虚拟节点和子节点</li>
                    <li><strong>NONE：</strong>无类型，用于固定值或表达式映射</li>
                  </ul>
                </div>
              </div>

              <!-- 映射类型与规则 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">5</span>
                    <h5>映射类型与规则</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">定义参数如何映射，后置处理支持从响应或原始入参中取值</p>
                <div class="field-examples">
                  <div class="example-label">映射类型详解：</div>
                  <ul class="example-list">
                    <li><strong>NAME（值映射）：</strong>直接取值
                      <ul class="sub-list">
                        <li>适用场景：将响应数据或入参直接映射到返回结果</li>
                        <li>实战案例1：将HIS响应的 <span class="code-inline">data</span> 映射到Jarvis响应的 <span class="code-inline">result</span></li>
                        <li>实战案例2：将原始入参的 <span class="code-inline">requestId</span> 回显到响应中</li>
                        <li>映射规则：可为空</li>
                      </ul>
                    </li>
                    <li><strong>CONSTANT（固定值）：</strong>使用固定配置值
                      <ul class="sub-list">
                        <li>适用场景：设置默认响应码、成功消息等</li>
                        <li>实战案例1：设置 <span class="code-inline">code</span> 为 <span class="code-inline">200</span></li>
                        <li>实战案例2：设置 <span class="code-inline">message</span> 为 <span class="code-inline">成功</span></li>
                        <li>映射规则：填写固定值，如 <span class="code-inline">200</span>、<span class="code-inline">成功</span></li>
                        <li>配置要点：源参数类型必须设置为 <span class="code-inline">NONE</span></li>
                      </ul>
                    </li>
                    <li><strong>EXPRESSION（表达式）：</strong>使用SpEL表达式进行复杂转换
                      <ul class="sub-list">
                        <li>适用场景：响应状态码转换、数据聚合、条件判断等</li>
                        <li>上下文变量：<span class="code-inline">#data</span>（当前层级数据）、<span class="code-inline">#response</span>（响应数据根）、<span class="code-inline">#input</span>（原始入参根）</li>
                        <li>映射规则：填写SpEL表达式</li>
                        <li>实战案例1：状态码转换 - <span class="code-inline">#response['returnCode'] == "1" ? "200" : "-1"</span></li>
                        <li>实战案例2：默认值 - <span class="code-inline">#response['code'] != null ? #response['code'].toString() : '200'</span></li>
                        <li>实战案例3：消息处理 - <span class="code-inline">#response['message'] != null ? #response['message'] : '成功'</span></li>
                      </ul>
                    </li>
                  </ul>
                </div>
              </div>
        
              <!-- 映射来源 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">6</span>
                    <h5>映射来源</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">后置处理特有字段，指定数据的来源</p>
                <div class="field-examples">
                  <div class="example-label">映射来源说明：</div>
                  <ul class="example-list">
                    <li><strong>RESPONSE：</strong>从目标接口的响应数据中取值
                      <ul class="sub-list">
                        <li>适用场景：将目标接口返回的数据映射到上游响应</li>
                        <li>源参数填写目标接口响应中的字段名</li>
                      </ul>
                    </li>
                    <li><strong>INPUT：</strong>从原始入参中取值
                      <ul class="sub-list">
                        <li>适用场景：将请求时的入参回显到响应中（如：请求ID、时间戳等）</li>
                        <li>源参数填写上游调用方入参中的字段名</li>
                      </ul>
                    </li>
                  </ul>
                </div>
              </div>
        
              <!-- 源参数 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">7</span>
                    <h5>源参数</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">根据映射来源决定源参数的来源，支持嵌套路径（使用<span class="code-inline">.</span>符号分隔）</p>
                <div class="field-examples">
                  <div class="example-label">配置场景：</div>
                  <ul class="example-list">
                    <li><strong>mappingSource=RESPONSE：</strong>源参数是目标接口响应中的字段
                      <ul class="sub-list">
                        <li>示例：<span class="code-inline">data</span>、<span class="code-inline">result.patientId</span></li>
                      </ul>
                    </li>
                    <li><strong>mappingSource=INPUT：</strong>源参数是原始入参中的字段
                      <ul class="sub-list">
                        <li>示例：<span class="code-inline">requestId</span>、<span class="code-inline">request.timestamp</span></li>
                      </ul>
                    </li>
                    <li><strong>固定值映射：</strong>源参数可为空，源参数类型设置为NONE</li>
                  </ul>
                </div>
              </div>
        
              <!-- 源参数类型 -->
              <div class="field-item required-field">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">8</span>
                    <h5>源参数类型</h5>
                    <el-tag size="mini" type="danger">必填</el-tag>
                  </div>
                </div>
                <p class="field-desc">源参数的数据类型</p>
                <div class="field-examples">
                  <div class="example-label">类型说明：</div>
                  <ul class="example-list">
                    <li><strong>STRING、INTEGER、LONG、BOOLEAN：</strong>基础数据类型</li>
                    <li><strong>OBJECT：</strong>对象类型，用于嵌套结构</li>
                    <li><strong>ARRAY：</strong>数组类型</li>
                    <li><strong>NONE：</strong>无类型，用于固定值或表达式映射</li>
                  </ul>
                </div>
              </div>

              <!-- 父节点 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">9</span>
                    <h5>父节点</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">用于构建嵌套结构，选择当前参数的父节点，顶级节点不需要填写</p>
              </div>
        
              <!-- 参数描述 -->
              <div class="field-item">
                <div class="field-header">
                  <div class="field-title">
                    <span class="field-number">10</span>
                    <h5>参数描述</h5>
                    <el-tag size="mini" type="info">可选</el-tag>
                  </div>
                </div>
                <p class="field-desc">填写该参数的业务含义，方便理解和后续维护</p>
              </div>
            </div>
          </div>
        </div>

        <div class="step-actions">
          <el-button @click="prevStep">上一步</el-button>
          <el-button type="primary" @click="nextStep">下一步</el-button>
        </div>
      </el-card>

      <!-- 步骤4: Mock测试验证 -->
      <el-card v-show="currentStep === 3" shadow="hover" class="step-card">
        <div class="step-description">
          <p>在真实调用前，使用Mock数据测试配置的正确性，快速发现问题并修正。</p>
        </div>

        <el-divider content-position="left">操作指引</el-divider>
        
        <div class="guide-content">
          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step3.item1 = !collapsedSteps.step3.item1">
              <div class="guide-number">1</div>
              <div class="guide-text">
                <h4>
                  配置Mock响应数据
                  <i :class="['collapse-icon', collapsedSteps.step3.item1 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step3.item1">
              <p>导航至：<code>Mock → Mock响应配置</code></p>
              <el-button type="text" size="small" @click="navigateTo('/mock/config')">
                前往配置 <i class="el-icon-arrow-right"></i>
              </el-button>
              <ul class="config-list">
                <li><strong>选择节点：</strong>选择要Mock的节点</li>
                <li><strong>场景名称：</strong>定义Mock场景（如：成功场景、失败场景）</li>
                <li><strong>Mock响应：</strong>填写JSON格式的模拟响应数据</li>
                <li><strong>启用状态：</strong>开启Mock</li>
              </ul>
            </div>
          </div>

          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step3.item2 = !collapsedSteps.step3.item2">
              <div class="guide-number">2</div>
              <div class="guide-text">
                <h4>
                  执行Mock调用测试
                  <i :class="['collapse-icon', collapsedSteps.step3.item2 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step3.item2">
              <p>导航至：<code>Mock → Mock调用测试</code></p>
              <el-button type="text" size="small" @click="navigateTo('/mock/log')">
                前往测试 <i class="el-icon-arrow-right"></i>
              </el-button>
              <ul class="config-list">
                <li><strong>选择节点和Mock场景</strong></li>
                <li><strong>填写业务数据：</strong>输入模拟的上游请求数据</li>
                <li><strong>点击调用：</strong>执行Mock调用</li>
              </ul>
            </div>
          </div>

          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step3.item3 = !collapsedSteps.step3.item3">
              <div class="guide-number">3</div>
              <div class="guide-text">
                <h4>
                  查看参数映射结果
                  <i :class="['collapse-icon', collapsedSteps.step3.item3 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step3.item3">
              <p>在调用结果中查看各阶段的参数转换情况</p>
              <ul class="config-list">
                <li><strong>业务数据：</strong>原始输入</li>
                <li><strong>请求参数：</strong>前置处理后的参数（发送给目标接口）</li>
                <li><strong>远程响应：</strong>Mock响应数据</li>
                <li><strong>响应参数：</strong>后置处理后的最终返回</li>
              </ul>
            </div>
          </div>
        </div>

        <el-alert
          title="验证重点"
          type="success"
          :closable="false"
          show-icon
        >
          <div slot="default">
            <p><strong>重点检查以下内容：</strong></p>
            <ul class="check-list">
              <li>✓ 请求参数结构是否符合目标接口要求</li>
              <li>✓ 字段名称、类型是否正确</li>
              <li>✓ 嵌套结构是否正确构建</li>
              <li>✓ 响应参数是否正确映射</li>
            </ul>
          </div>
        </el-alert>

        <div class="tip-section">
          <i class="el-icon-info"></i>
          <span><strong>小技巧：</strong>可以配置多个Mock场景（成功、失败、异常等），全面测试参数映射的健壮性。</span>
        </div>

        <div class="step-actions">
          <el-button @click="prevStep">上一步</el-button>
          <el-button type="primary" @click="nextStep">下一步</el-button>
        </div>
      </el-card>

      <!-- 步骤5: 线上真实调用 -->
      <el-card v-show="currentStep === 4" shadow="hover" class="step-card">
        <div class="step-description">
          <p>Mock测试通过后，关闭Mock开关，进行真实的接口调用，并通过日志验证调用流程。</p>
        </div>

        <el-divider content-position="left">操作指引</el-divider>
        
        <div class="guide-content">
          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step4.item1 = !collapsedSteps.step4.item1">
              <div class="guide-number">1</div>
              <div class="guide-text">
                <h4>
                  关闭Mock开关
                  <i :class="['collapse-icon', collapsedSteps.step4.item1 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step4.item1">
              <p>返回Mock配置页面，将Mock配置设置为"禁用"状态</p>
              <el-button type="text" size="small" @click="navigateTo('/mock/config')">
                前往关闭Mock <i class="el-icon-arrow-right"></i>
              </el-button>
            </div>
          </div>

          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step4.item2 = !collapsedSteps.step4.item2">
              <div class="guide-number">2</div>
              <div class="guide-text">
                <h4>
                  上游系统发起调用
                  <i :class="['collapse-icon', collapsedSteps.step4.item2 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step4.item2">
              <p>通过工作流配置的接口URI进行真实调用</p>
              <div class="api-example">
                <div class="api-title">调用示例</div>
                <pre class="api-code">POST http://your-server:8080/agent-open-api/your-workflow-uri
Content-Type: application/json

{
  "patientId": "123456",
  "name": "张三"
}</pre>
              </div>
            </div>
          </div>

          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step4.item3 = !collapsedSteps.step4.item3">
              <div class="guide-number">3</div>
              <div class="guide-text">
                <h4>
                  查看接口调用日志
                  <i :class="['collapse-icon', collapsedSteps.step4.item3 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step4.item3">
              <p>导航至：<code>接口调用日志</code></p>
              <el-button type="text" size="small" @click="navigateTo('/invoke/log')">
                查看调用日志 <i class="el-icon-arrow-right"></i>
              </el-button>
              <ul class="config-list">
                <li><strong>筛选节点：</strong>选择对应的节点</li>
                <li><strong>查看详情：</strong>点击日志记录查看完整的调用链路</li>
                <li><strong>验证内容：</strong>
                  <ul class="sub-list">
                    <li>业务数据：上游传入的原始数据</li>
                    <li>请求参数：前置处理后发送给目标接口的参数</li>
                    <li>远程响应：目标接口返回的原始响应</li>
                    <li>响应参数：后置处理后返回给上游的最终数据</li>
                    <li>调用时长：接口响应时间</li>
                  </ul>
                </li>
              </ul>
            </div>
          </div>

          <div class="guide-item">
            <div class="guide-header" @click="collapsedSteps.step4.item4 = !collapsedSteps.step4.item4">
              <div class="guide-number">4</div>
              <div class="guide-text">
                <h4>
                  问题排查与优化
                  <i :class="['collapse-icon', collapsedSteps.step4.item4 ? 'el-icon-arrow-down' : 'el-icon-arrow-up']"></i>
                </h4>
              </div>
            </div>
            <div class="guide-content-body" v-show="!collapsedSteps.step4.item4">
              <ul class="config-list">
                <li>如果调用失败，检查远程响应中的错误信息</li>
                <li>如果参数格式不对，返回参数配置页面调整映射规则</li>
                <li>如果响应数据不符合预期，检查后置处理配置</li>
                <li>调用成功后，可以优化调度规则、添加过滤条件等</li>
              </ul>
            </div>
          </div>
        </div>

        <el-alert
          title="恭喜！接口对接完成"
          type="success"
          :closable="false"
          show-icon
        >
          <div slot="default">
            <p>至此，您已完成完整的接口对接流程！后续可以：</p>
            <ul class="check-list">
              <li>✓ 添加更多节点构建复杂工作流</li>
              <li>✓ 配置条件流转实现业务分支</li>
              <li>✓ 使用参数过滤优化调用逻辑</li>
              <li>✓ 监控日志优化接口性能</li>
            </ul>
          </div>
        </el-alert>

        <div class="complete-section">
          <div class="complete-icon">
            <i class="el-icon-success"></i>
          </div>
          <h3>接口对接完成！</h3>
          <p>现在可以返回各个配置页面进行更高级的配置，或开始下一个接口的对接。</p>
        </div>

        <div class="step-actions">
          <el-button @click="prevStep">上一步</el-button>
          <el-button type="success" @click="resetSteps">重新开始</el-button>
        </div>
      </el-card>
    </div>
  
  </div>
</template>

<script>
export default {
  name: 'QuickStart',
  data() {
    return {
      currentStep: 0,
      nodeConfigTab: 'http',
      // 可折叠映射类型卡片状态
      expandedCards: {
        pre: {
          name: false,
          constant: false,
          expression: false
        },
        post: {
          name: false,
          constant: false,
          expression: false
        }
      },
      collapsedSteps: {
        step0: {
          item1: true,
          item2: true,
          item3: true
        },
        step1: {
          item1: true,
          item2: true
        },
        step2: {
          item1: true,
          item2: true,
          item3: true,
          item4: true
        },
        step3: {
          item1: true,
          item2: true,
          item3: true
        },
        step4: {
          item1: true,
          item2: true,
          item3: true,
          item4: true
        }
      },
      mappingExamples: [
        {
          sourceParam: 'request.patientId',
          targetParam: 'patientCode',
          mappingType: '值映射(NAME)',
          mappingRule: '-'
        },
        {
          sourceParam: 'request.name',
          targetParam: 'patientName',
          mappingType: '值映射(NAME)',
          mappingRule: '-'
        },
        {
          sourceParam: '-',
          targetParam: 'source',
          mappingType: '固定值(CONSTANT)',
          mappingRule: 'SYSTEM_A'
        },
        {
          sourceParam: 'request.idCard',
          targetParam: 'certNo',
          mappingType: '表达式(EXPRESSION)',
          mappingRule: '#data["idCard"] ?: #data["phone"]'
        }
      ],
      httpConfigExamples: {
        default: '{\n  "url": "http://10.192.50.204:8088/PACS/HssExmeReceive"\n}',
        full: '{\n  "url": "http://gapi-c2f.seenew.info/hbos-test/open-api/gw-api/hbos-business-thirdparty-integration/common/rhc/createResidentOrgRegistInfo",\n  "method": "POST",\n  "headers": {\n    "Content-Type": "application/json",\n    "App-Key": "mvTLtFJGmectBac8unwIf1bF"\n  }\n}'
      },
      webserviceConfigExamples: {
        default: '{\n  "url": "http://10.20.0.141:8805/soup-api/pacs_apply",\n  "soupTemplate": "<soapenv:Envelope xmlns:soapenv=\\"http://schemas.xmlsoap.org/soap/envelope/\\" xmlns:her=\\"http://herenit.com\\">\\n  <soapenv:Header></soapenv:Header>\\n  <soapenv:Body>\\n    <her:MessageIn>\\n      <her:DataType>XML</her:DataType>\\n      <her:pInput><![CDATA[${inputParam}]]></her:pInput>\\n    </her:MessageIn>\\n  </soapenv:Body>\\n</soapenv:Envelope>"\n}',
        normal: '{\n  "url": "http://10.20.0.141:8805/soup-api/pacs_apply",\n  "soupTemplate": "<soapenv:Envelope xmlns:soapenv=\\"http://schemas.xmlsoap.org/soap/envelope/\\" xmlns:her=\\"http://herenit.com\\">\\n  <soapenv:Header></soapenv:Header>\\n  <soapenv:Body>\\n    <her:MessageIn>\\n      <her:DataType>XML</her:DataType>\\n      <her:pInput><![CDATA[${inputParam}]]></her:pInput>\\n    </her:MessageIn>\\n  </soapenv:Body>\\n</soapenv:Envelope>",\n  "elementName": "request"\n}',
        full: '{\n  "url": "http://10.20.0.141:8805/soup-api/pacs_apply",\n  "method": "POST",\n  "headers": {\n    "Content-Type": "text/xml;charset=UTF-8"\n  },\n  "soupTemplate": "<soapenv:Envelope xmlns:soapenv=\\"http://schemas.xmlsoap.org/soap/envelope/\\" xmlns:her=\\"http://herenit.com\\">\\n  <soapenv:Header></soapenv:Header>\\n  <soapenv:Body>\\n    <her:MessageIn>\\n      <her:DataType>XML</her:DataType>\\n      <her:pInput><![CDATA[${inputParam}]]></her:pInput>\\n    </her:MessageIn>\\n  </soapenv:Body>\\n</soapenv:Envelope>",\n  "dataType": "xml",\n  "escape": false,\n  "elementName": "request",\n  "namespace": "http://herenit.com",\n  "namespaceElementName": "MessageIn",\n  "namespaceDataElementName": "pInput",\n  "isCDATA": true,\n  "objectTag": ["PatientInfo", "OrderInfo"]\n}'
      },
      databaseConfigExamples: {
        full: '{\n  "driverClassName": "com.microsoft.sqlserver.jdbc.SQLServerDriver",\n  "operation": "SELECT",\n  "url": "jdbc:sqlserver://10.20.4.245:1433;DatabaseName=HEMS_V5;encrypt=false;trustServerCertificate=false",\n  "username": "sa",\n  "password": "Hjk@2022!@",\n  "sqlTemplate": "SELECT * FROM ReportDetail WHERE patid= {PatientCode} and Applyno IN {ApplyNoList}"\n}'
      }
    };
  },
  methods: {
    // 切换映射类型卡片展开/收起状态
    toggleMappingCard(stage, type) {
      this.expandedCards[stage][type] = !this.expandedCards[stage][type];
    },
    nextStep() {
      if (this.currentStep < 4) {
        this.currentStep++;
        this.scrollToTop();
      }
    },
    prevStep() {
      if (this.currentStep > 0) {
        this.currentStep--;
        this.scrollToTop();
      }
    },
    resetSteps() {
      this.currentStep = 0;
      this.scrollToTop();
    },
    navigateTo(path) {
      this.$router.push(path);
    },
    openInNewTab(path) {
      const { origin } = window.location;
      const url = `${origin}/#${path}`;
      window.open(url, '_blank');
    },
    scrollToTop() {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    },
    copyToClipboard(text) {
      // 创建临时textarea元素
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      
      try {
        document.execCommand('copy');
        this.$message.success('表达式已复制到剪贴板');
      } catch (err) {
        this.$message.error('复制失败，请手动复制');
      } finally {
        document.body.removeChild(textarea);
      }
    }
  }
};
</script>

<style scoped>
.quick-start {
  padding: 20px;
}

.intro-section {
  text-align: center;
  margin-bottom: 30px;
}

.intro-section h2 {
  color: #303133;
  margin-bottom: 10px;
}

.intro-text {
  font-size: 15px;
  color: #606266;
  line-height: 1.8;
}

.steps-wrapper {
  margin: 40px 0;
}

.step-content {
  margin-top: 30px;
}

.step-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.step-icon {
  font-size: 24px;
  color: #409eff;
}

.step-title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.step-description {
  padding: 15px 0;
  font-size: 20px;
  color: #303133;
  font-weight: 500;
  line-height: 1.8;
}

.guide-content {
  margin: 20px 0;
}

.guide-item {
  margin-bottom: 20px;
  border-bottom: 1px dashed #e4e7ed;
  padding-bottom: 20px;
}

.guide-item:last-child {
  border-bottom: none;
}

.guide-header {
  display: flex;
  align-items: center;
  gap: 15px;
  cursor: pointer;
  padding: 8px;
  margin: -8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.guide-header:hover {
  background-color: #f5f7fa;
}

.guide-header .guide-text {
  flex: 1;
}

.collapse-icon {
  color: #409eff;
  font-size: 18px;
  font-weight: bold;
  transition: transform 0.3s, color 0.2s;
  flex-shrink: 0;
  margin-left: 4px;
}

.collapse-icon:hover {
  color: #66b1ff;
}

.guide-content-body {
  margin-top: 15px;
  padding-left: 51px;
}

.guide-number {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 18px;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
}

.guide-text {
  flex: 1;
}

.guide-text h4 {
  margin: 0;
  color: #303133;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.guide-text p {
  margin: 8px 0;
  color: #606266;
  line-height: 1.6;
}

.guide-text code {
  background-color: #f5f7fa;
  padding: 2px 8px;
  border-radius: 3px;
  color: #e6a23c;
  font-family: 'Courier New', monospace;
}

/* 公共样式已移至 src/assets/common.css */

.example-section {
  margin: 20px 0;
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 6px;
}

.example-title {
  font-weight: bold;
  color: #303133;
  margin-bottom: 10px;
}

.example-code {
  background-color: #1e1e1e;
  color: #f8f8f2;
  padding: 15px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  overflow-x: auto;
  margin: 0;
}

.api-example {
  margin-top: 10px;
}

.api-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 5px;
}

.api-code {
  background-color: #1e1e1e;
  color: #f8f8f2;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  overflow-x: auto;
  margin: 0;
}

.check-list {
  margin: 10px 0;
  padding-left: 20px;
}

.check-list li {
  margin: 5px 0;
  color: #67c23a;
  line-height: 1.6;
}

.tip-section {
  margin: 15px 0;
  padding: 12px 15px;
  background-color: #ecf5ff;
  border-left: 4px solid #409eff;
  display: flex;
  align-items: center;
  gap: 10px;
}

.tip-section i {
  font-size: 18px;
  color: #409eff;
}

.complete-section {
  text-align: center;
  padding: 30px 0;
}

.complete-icon {
  margin-bottom: 20px;
}

.complete-icon i {
  font-size: 64px;
  color: #67c23a;
}

.complete-section h3 {
  color: #303133;
  margin-bottom: 10px;
}

.complete-section p {
  color: #606266;
  font-size: 14px;
}

.step-actions {
  margin-top: 30px;
  text-align: center;
}

.quick-nav {
  margin-top: 30px;
}

.nav-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* 节点配置样式 */
.config-item {
  margin: 20px 0;
  padding-bottom: 20px;
  border-bottom: 1px dashed #e4e7ed;
}

.config-item:last-child {
  border-bottom: none;
}

.config-item h5 {
  color: #303133;
  font-size: 15px;
  margin-bottom: 8px;
  font-weight: 600;
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
  line-height: 1.6;
  margin-bottom: 12px;
  font-size: 14px;
}

.item-example {
  margin: 10px 0;
  padding: 10px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.example-label {
  font-weight: bold;
  color: #303133;
  margin-right: 8px;
  font-size: 13px;
}

.example-note {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}

.code-block {
  background-color: #1e1e1e;
  color: #f8f8f2;
  padding: 10px 12px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  overflow-x: auto;
  margin: 8px 0;
}

.item-options {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 10px 0;
}

.item-tip {
  margin-top: 10px;
  padding: 8px 12px;
  background-color: #ecf5ff;
  border-left: 4px solid #409eff;
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: 4px;
}

.item-tip i {
  color: #409eff;
  font-size: 14px;
}

.item-tip.warning {
  background-color: #fef0f0;
  border-left-color: #f56c6c;
}

.item-tip.warning i {
  color: #f56c6c;
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
  font-size: 13px;
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
  font-size: 12px;
}

/* 字段项样式 */
.field-item {
  margin: 20px 0;
  padding: 15px;
  background-color: #f9f9f9;
  border-radius: 8px;
  border-left: 4px solid #e4e7ed;
}

.field-item.required-field {
  border-left-color: #409eff;
  background-color: #f0f9ff;
}

.field-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.field-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.field-title h5 {
  margin: 0;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.field-number {
  width: 22px;
  height: 22px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
  flex-shrink: 0;
}

.field-examples {
  margin-top: 12px;
  padding: 12px;
  background-color: #fff;
  border-radius: 6px;
}

.example-list {
  list-style: none;
  padding: 0;
  margin: 10px 0 0 0;
}

.example-list > li {
  padding: 6px 0;
  color: #606266;
  line-height: 1.6;
  font-size: 13px;
}

.example-list li:before {
  content: none;
}

.sub-list {
  list-style: none;
  padding-left: 20px;
  margin: 8px 0;
}

.sub-list li {
  padding: 4px 0;
  position: relative;
  padding-left: 20px;
}

.sub-list li:before {
  content: "✓";
  position: absolute;
  left: 0;
  color: #67c23a;
  font-weight: bold;
}

.type-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 15px;
  margin: 15px 0;
}

.type-option {
  transition: transform 0.3s, box-shadow 0.3s, border-color 0.3s;
  cursor: pointer;
  border: 2px solid transparent;
}

.type-option:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.type-option.type-selected {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.type-name {
  font-size: 15px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 8px;
}

.type-brief {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 10px;
}

.type-usage {
  padding-top: 10px;
  border-top: 1px dashed #e4e7ed;
  font-size: 12px;
}

.usage-label {
  color: #909399;
  font-weight: bold;
}

.usage-scenarios {
  margin-top: 5px;
}

.scenario-title {
  color: #303133;
  font-size: 14px;
  margin-bottom: 15px;
  font-weight: bold;
}

.scenario-card {
  margin: 15px 0;
  padding: 15px;
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.scenario-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.scenario-badge {
  padding: 4px 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 12px;
  font-size: 12px;
  font-weight: bold;
  white-space: nowrap;
}

.scenario-header h5 {
  margin: 0;
  color: #303133;
  font-size: 14px;
}

.scenario-desc {
  color: #606266;
  line-height: 1.6;
  margin-bottom: 12px;
  font-size: 13px;
}

.scenario-example {
  margin-top: 12px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 6px;
  border-left: 3px solid #409eff;
}

.scenario-example .example-label {
  font-weight: bold;
  color: #409eff;
  margin-bottom: 6px;
  font-size: 13px;
}

.scenario-example .example-desc {
  color: #606266;
  line-height: 1.6;
  margin-bottom: 8px;
  font-size: 13px;
}

.code-explanation {
  margin-top: 12px;
  padding: 10px;
  background-color: #fff;
  border-radius: 4px;
}

.code-explanation p {
  margin: 0 0 8px 0;
  color: #303133;
  font-size: 13px;
}

.code-explanation ul {
  margin: 0;
  padding-left: 20px;
}

.code-explanation li {
  color: #606266;
  line-height: 1.6;
  margin: 4px 0;
  font-size: 13px;
}

.code-with-copy {
  position: relative;
  margin: 10px 0;
}

.code-with-copy .code-block {
  margin: 0;
  padding-right: 70px;
}

.copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 10;
}

.example-label-no-check {
  font-weight: bold;
  color: #303133;
  margin-right: 8px;
  font-size: 13px;
  margin-bottom: 10px;
}

.config-method-list {
  list-style: none;
  padding: 0;
  margin: 10px 0 0 0;
}

.config-method-list li {
  padding: 8px 0;
  color: #606266;
  line-height: 1.6;
  font-size: 13px;
}

.jarvis-examples-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px 20px;
  margin-top: 10px;
  padding-left: 0;
}

.jarvis-example-item {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .jarvis-examples-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .jarvis-examples-grid {
    grid-template-columns: 1fr;
  }
  .jarvis-example-item {
    white-space: normal;
  }
}

/* 公共样式已移至 src/assets/common.css */

/* ========== 映射类型卡片样式 ========== */
.mapping-type-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-bottom: 15px;
  overflow: hidden;
  transition: all 0.3s ease;
}

.mapping-type-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.mapping-type-card .card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f5f7fa;
  cursor: pointer;
  transition: background 0.3s ease;
  user-select: none;
}

.mapping-type-card .card-header:hover {
  background: #ecf5ff;
}

.mapping-type-card .card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #303133;
}

.mapping-type-card .toggle-icon {
  font-size: 14px;
  color: #909399;
  transition: transform 0.3s ease;
}

.mapping-type-card .toggle-icon.expanded {
  transform: rotate(90deg);
}

.mapping-type-card .card-summary {
  font-size: 13px;
  color: #909399;
}

.mapping-type-card .card-content {
  padding: 16px;
  background: #ffffff;
  border-top: 1px solid #e4e7ed;
}

/* 映射规则提示框 */
.mapping-rule-box {
  background: #ecf5ff;
  padding: 10px 12px;
  border-radius: 4px;
  margin-bottom: 15px;
  font-size: 13px;
  line-height: 1.6;
}

.mapping-rule-box.important {
  background: #fef0f0;
  border-left: 3px solid #f56c6c;
}

.mapping-rule-box .rule-label {
  font-weight: bold;
  color: #303133;
  margin-right: 8px;
}

.mapping-rule-box .rule-value {
  color: #606266;
}

.mapping-rule-box code {
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  color: #e6a23c;
  font-family: 'Courier New', monospace;
}

/* Tab页签 */
.mapping-tabs {
  margin-top: 15px;
}

.mapping-tabs .el-tabs__content {
  padding: 15px 10px;
}

/* 案例项 */
.case-item {
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px dashed #e4e7ed;
}

.case-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.case-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.case-badge {
  display: inline-block;
  padding: 2px 8px;
  background: #409eff;
  color: #ffffff;
  font-size: 12px;
  border-radius: 3px;
  font-weight: 500;
}

.case-title {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
}

.case-content {
  padding-left: 0;
}

.case-desc {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #fafafa;
  border-left: 3px solid #409eff;
  border-radius: 4px;
}

/* JSON结构展示 */
.json-structure {
  margin-bottom: 15px;
}

.json-title {
  font-size: 13px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 6px;
}

.json-code {
  background: #282c34;
  color: #abb2bf;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Courier New', monospace;
  line-height: 1.6;
  overflow-x: auto;
  margin: 0;
}

/* 配置演示 */
.config-demo {
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  margin-top: 10px;
}

.config-row {
  display: flex;
  align-items: center;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px dotted #e4e7ed;
}

.config-row:last-child {
  border-bottom: none;
}

.config-row.highlight {
  background: #fff7e6;
  padding: 8px;
  margin: 8px -12px 0 -12px;
  border-radius: 4px;
  border: 1px solid #ffd591;
}

.config-label {
  color: #909399;
  min-width: 120px;
  font-size: 12px;
}

.config-row code {
  background: #ffffff;
  padding: 3px 8px;
  border-radius: 3px;
  font-size: 12px;
  color: #409eff;
  font-family: 'Courier New', monospace;
  border: 1px solid #e4e7ed;
}

.config-row.highlight code {
  background: #ffffff;
  color: #e6a23c;
  font-weight: 600;
  border-color: #ffd591;
}

.config-row .empty-value {
  color: #c0c4cc;
  font-style: italic;
}

/* 配置步骤 */
.config-steps {
  margin-top: 15px;
}

.step-title {
  font-size: 13px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 10px;
}

.step-item {
  display: flex;
  gap: 12px;
  margin-bottom: 15px;
  padding: 15px;
  background: #f9fafc;
  border-radius: 6px;
  border-left: 3px solid #409eff;
}

.step-num {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  background: #409eff;
  color: #ffffff;
  border-radius: 50%;
  font-size: 12px;
  font-weight: bold;
}

.step-content {
  flex: 1;
}

.step-desc {
  color: #606266;
  font-size: 13px;
  margin-bottom: 10px;
}

/* 提示框 */
.tip-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 4px;
  margin-top: 15px;
  font-size: 13px;
  color: #e6a23c;
}

.tip-box i {
  font-size: 16px;
  flex-shrink: 0;
}

/* 表达式解析 */
.expression-explain {
  background: #f4f4f5;
  padding: 12px;
  border-radius: 4px;
  margin-top: 12px;
}

.explain-title {
  font-size: 13px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 8px;
}

.explain-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.explain-list li {
  padding: 4px 0;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
}

.explain-list code {
  background: #ffffff;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  color: #67c23a;
  font-family: 'Courier New', monospace;
  border: 1px solid #e4e7ed;
}

/* 动画效果 */
.slide-fade-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-enter {
  transform: translateY(-10px);
  opacity: 0;
}

.slide-fade-leave-to {
  transform: translateY(-5px);
  opacity: 0;
}
</style>
