<template>
  <div class="mock-guide">
    <el-card>
      <div slot="header">
        <h2 style="margin: 0">Mock使用说明</h2>
      </div>

      <div class="guide-content">
        <div class="section-intro">
          <h3>Interface-Agent场景下的Mock解决方案</h3>
          <p class="intro-text">
            Mock功能用于模拟下游系统的响应数据，无需真实调用三方系统，即可验证参数映射配置是否正确。
          </p>
        </div>

        <div class="mechanism-box">
          <div class="mechanism-title">🤖 工作机制</div>
          <div class="mechanism-content">
            AI在自动化配置后会主动进行Mock验证，确保参数映射符合预期。<br>
            人工校验作为最终兜底，用于解决AI验证未覆盖的边界场景。
          </div>
        </div>

        <div class="problem-section">
          <div class="problem-title">✅ 解决的问题</div>
          <ul class="problem-list">
            <li><span class="highlight">快速验证</span>：配置完参数映射后，立即查看转换结果是否符合预期，AI能快速判断</li>
            <li><span class="highlight">安全调试</span>：不会真实调用下游接口，避免产生脏数据或触发业务逻辑</li>
            <li><span class="highlight">完整流程</span>：仍然执行前置/后置映射，可以在调用日志中查看完整的参数转换过程</li>
          </ul>
        </div>

        <h3>如何使用Mock?</h3>
        <el-steps :active="3" process-status="finish" align-center style="margin: 20px 0">
          <el-step title="① 创建Mock场景" description="在Mock响应配置页面创建Mock数据"></el-step>
          <el-step title="② 关联工作流节点" description="将Mock配置绑定到具体节点"></el-step>
          <el-step title="③ 选择Mock调用" description="在Mock调用测试页面选择节点和场景"></el-step>
        </el-steps>

        <h3>实际操作示例</h3>
        <div style="background: #f5f7fa; padding: 20px; border-radius: 4px; margin: 10px 0">
          <p style="margin: 0 0 10px 0"><strong>场景</strong>:你配置了一个“账单创建”节点，需要验证jarvis的数据是否正确转换为HIS系统要求的格式。</p>
          
          <p style="margin: 15px 0 10px 0; font-weight: bold">步骤1：创建Mock配置</p>
          <ul style="margin: 5px 0; padding-left: 25px">
            <li>进入【Mock响应配置】页面，点击【新增Mock】</li>
            <li>选择<strong>工作流节点</strong>：账单创建节点</li>
            <li>填写<strong>场景名称</strong>：成功场景</li>
            <li>配置<strong>Mock响应数据</strong>：HIS系统的标准响应格式，例：
              <pre style="background: #282c34; color: #abb2bf; padding: 10px; border-radius: 4px; margin-top: 5px">{"code": "200", "message": "成功", "data": {"orderId": "202501200001"}}</pre>
            </li>
            <li>点击保存</li>
          </ul>

          <p style="margin: 15px 0 10px 0; font-weight: bold">步骤2：进行Mock调用测试</p>
          <ul style="margin: 5px 0; padding-left: 25px">
            <li>进入【Mock调用测试】页面</li>
            <li>选择<strong>节点</strong>：账单创建节点</li>
            <li>选择<strong>Mock场景</strong>：成功场景</li>
            <li>输入<strong>请求参数</strong>：可以选择上方模板（如“账单创建”），或手动输入jarvis格式的数据</li>
            <li>点击【调用Mock】按钮</li>
            <li>查看右下角浮动窗口的<strong>响应结果</strong></li>
          </ul>

          <p style="margin: 15px 0 10px 0; font-weight: bold">步骤3：查看参数映射详情</p>
          <ul style="margin: 5px 0; padding-left: 25px">
            <li>进入【接口调用日志】页面</li>
            <li>找到刚才的调用记录</li>
            <li>查看<strong>前置映射结果</strong>：确认jarvis数据转换为HIS格式是否正确</li>
            <li>查看<strong>后置映射结果</strong>：HIS响应转换为jarvis格式是否正确</li>
            <li>如果发现问题，返回工作流配置页面修改参数映射规则</li>
          </ul>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'MockGuide'
};
</script>

<style scoped>
.mock-guide {
  padding: 20px;
}

.guide-content {
  line-height: 1.8;
}

/* 章节介绍 */
.section-intro {
  margin-bottom: 25px;
}

.section-intro h3 {
  margin: 0 0 12px 0;
  color: #303133;
  font-size: 20px;
  font-weight: 600;
}

.intro-text {
  margin: 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

/* 工作机制框 */
.mechanism-box {
  background: linear-gradient(135deg, #e8f4fd 0%, #f0f9ff 100%);
  border-left: 4px solid #409eff;
  border-radius: 6px;
  padding: 16px 20px;
  margin-bottom: 25px;
}

.mechanism-title {
  font-size: 14px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 8px;
}

.mechanism-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
}

/* 问题列表 */
.problem-section {
  margin-bottom: 25px;
}

.problem-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.problem-list {
  margin: 0;
  padding-left: 20px;
  list-style: none;
}

.problem-list li {
  margin: 10px 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  position: relative;
  padding-left: 8px;
}

.problem-list li::before {
  content: '•';
  color: #409eff;
  font-weight: bold;
  position: absolute;
  left: -12px;
}

.problem-list .highlight {
  color: #409eff;
  font-weight: 600;
}

.guide-content h3 {
  margin: 25px 0 15px 0;
  color: #303133;
  font-size: 18px;
  border-left: 4px solid #409eff;
  padding-left: 10px;
}

.guide-content p {
  margin: 10px 0;
  color: #606266;
}

.guide-content ul,
.guide-content ol {
  margin: 10px 0;
  padding-left: 25px;
  color: #606266;
}

.guide-content li {
  margin: 8px 0;
}

.guide-content code {
  padding: 2px 6px;
  background: #f5f7fa;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
}

.guide-content pre {
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
}
</style>
