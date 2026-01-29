<template>
  <div class="param-mapping-demo">
    <el-card class="demo-header">
      <div slot="header" class="clearfix">
        <span style="font-size: 18px; font-weight: bold;">
          <i class="el-icon-data-line"></i> 参数映射演示
        </span>
        <div class="header-desc">
          通过实际案例演示参数映射的处理过程
        </div>
      </div>

      <!-- 控制按钮 -->
      <div class="demo-controls">
        <el-button 
          type="primary" 
          @click="startDemo" 
          :disabled="isPlaying"
          icon="el-icon-video-play"
        >
          开始演示
        </el-button>
        <el-button 
          @click="resetDemo" 
          icon="el-icon-refresh-left"
        >
          重置
        </el-button>
        <el-button 
          @click="prevStep" 
          :disabled="currentStep === 0 || isPlaying"
          icon="el-icon-back"
        >
          上一步
        </el-button>
        <el-button 
          @click="nextStep" 
          :disabled="currentStep >= steps.length - 1 || isPlaying"
          icon="el-icon-right"
        >
          下一步
        </el-button>
        
        <div class="step-indicator">
          步骤 {{ currentStep + 1 }} / {{ steps.length }}
        </div>
      </div>
    </el-card>

    <!-- 演示区域 -->
    <el-row :gutter="20" class="demo-content">
      <!-- 左侧：原始入参 -->
      <el-col :span="8">
        <el-card class="data-panel source-panel" shadow="hover">
          <div slot="header">
            <i class="el-icon-download"></i> 原始入参
          </div>
          <div class="json-container">
            <pre v-highlighter="sourceData">{{ JSON.stringify(sourceData, null, 2) }}</pre>
          </div>
        </el-card>
      </el-col>

      <!-- 中间：映射过程 -->
      <el-col :span="8">
        <el-card class="data-panel process-panel" shadow="hover">
          <div slot="header">
            <i class="el-icon-s-operation"></i> 映射过程
          </div>
          
          <!-- 步骤说明 -->
          <div class="step-description">
            <el-alert
              :title="steps[currentStep].title"
              type="info"
              :closable="false"
              show-icon
            >
              <div>{{ steps[currentStep].description }}</div>
            </el-alert>
          </div>

          <!-- 映射规则展示 -->
          <div class="mapping-rules">
            <transition-group name="list" tag="div">
              <div 
                v-for="(mapping, index) in visibleMappings" 
                :key="mapping.key"
                class="mapping-item"
                :class="{ 'active': index === activeMappingIndex }"
              >
                <div class="mapping-content">
                  <div class="mapping-source">
                    <el-tag size="small" type="warning">源</el-tag>
                    <code>{{ mapping.source }}</code>
                    <el-tag size="mini" effect="plain">{{ mapping.sourceType }}</el-tag>
                  </div>
                  
                  <div class="mapping-arrow">
                    <i class="el-icon-right"></i>
                    <span class="mapping-type-label">{{ mapping.mappingType }}</span>
                  </div>
                  
                  <div class="mapping-target">
                    <el-tag size="small" type="success">目标</el-tag>
                    <code>{{ mapping.target }}</code>
                    <el-tag size="mini" effect="plain">{{ mapping.targetType }}</el-tag>
                  </div>
                </div>
                
                <div class="mapping-rule" v-if="mapping.rule">
                  <span class="rule-label">规则：</span>
                  <code>{{ mapping.rule }}</code>
                </div>
              </div>
            </transition-group>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：目标输出 -->
      <el-col :span="8">
        <el-card class="data-panel target-panel" shadow="hover">
          <div slot="header">
            <i class="el-icon-upload2"></i> 目标输出
          </div>
          <div class="json-container">
            <pre v-highlighter="targetData">{{ JSON.stringify(targetData, null, 2) }}</pre>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部：详细说明 -->
    <el-card class="demo-footer" v-if="currentStep < steps.length">
      <div class="step-detail">
        <h4>{{ steps[currentStep].detailTitle }}</h4>
        <p v-html="steps[currentStep].detailContent"></p>
      </div>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'ParamMappingDemo',
  
  data() {
    return {
      currentStep: 0,
      isPlaying: false,
      activeMappingIndex: -1,
      
      // 原始入参数据
      sourceData: {
        patientCode: "P20260128001",
        hisPatientId: "HIS123456",
        patientName: "张三",
        age: 35,
        birthday: "1989-05-20",
        sex: "男"
      },
      
      // 目标输出数据（逐步构建）
      targetData: {},
      
      // 演示步骤
      steps: [
        {
          title: "步骤1：字段直接映射",
          description: "将源字段直接映射到目标字段（NAME类型）",
          detailTitle: "NAME 映射类型说明",
          detailContent: "NAME 类型表示<strong>字段名直接映射</strong>，即目标字段的值直接从源字段获取，无需任何转换。<br>适用场景：字段名称不同但数据类型和含义相同的情况。",
          mappings: [
            {
              key: 'mapping1',
              source: 'patientCode',
              sourceType: 'STRING',
              target: 'patientCode',
              targetType: 'STRING',
              mappingType: 'NAME',
              rule: null
            }
          ]
        },
        {
          title: "步骤2：字段名称转换",
          description: "源字段名与目标字段名不同的映射",
          detailTitle: "字段重命名",
          detailContent: "通过 NAME 映射将 <code>hisPatientId</code> 映射到 <code>outPatientCode</code>。<br>这是最常见的映射场景，用于适配不同系统的字段命名规范。",
          mappings: [
            {
              key: 'mapping2',
              source: 'hisPatientId',
              sourceType: 'STRING',
              target: 'outPatientCode',
              targetType: 'STRING',
              mappingType: 'NAME',
              rule: null
            }
          ]
        },
        {
          title: "步骤3：类型转换映射",
          description: "将年龄从数字转换为字符串",
          detailTitle: "类型自动转换",
          detailContent: "源字段 <code>age</code> 是 INTEGER 类型，目标字段需要 STRING 类型。<br>系统会<strong>自动进行类型转换</strong>：<code>35 → \"35\"</code>",
          mappings: [
            {
              key: 'mapping3',
              source: 'age',
              sourceType: 'INTEGER',
              target: 'age',
              targetType: 'STRING',
              mappingType: 'NAME',
              rule: null
            }
          ]
        },
        {
          title: "步骤4：表达式映射",
          description: "使用SpEL表达式进行字段拼接",
          detailTitle: "EXPRESSION 表达式映射",
          detailContent: "使用 <strong>SpEL 表达式</strong>将姓名和年龄拼接成一个描述字段：<br><code>#{patientName} + '，' + #{age} + '岁'</code><br>表达式支持字符串拼接、数学运算、条件判断等复杂逻辑。",
          mappings: [
            {
              key: 'mapping4',
              source: 'patientName, age',
              sourceType: 'STRING,INTEGER',
              target: 'patientInfo',
              targetType: 'STRING',
              mappingType: 'EXPRESSION',
              rule: "#{patientName} + '，' + #{age} + '岁'"
            }
          ]
        },
        {
          title: "步骤5：固定值映射",
          description: "为性别字段设置固定值",
          detailTitle: "CONSTANT 固定值映射",
          detailContent: "CONSTANT 类型用于设置<strong>固定常量值</strong>，不从源数据获取。<br>适用场景：默认值、标识字段、固定配置等。<br>本例中固定设置 <code>sex = \"MALE\"</code>",
          mappings: [
            {
              key: 'mapping5',
              source: '-',
              sourceType: 'NONE',
              target: 'sex',
              targetType: 'STRING',
              mappingType: 'CONSTANT',
              rule: 'MALE'
            }
          ]
        },
        {
          title: "步骤6：单值映射（字典转换）",
          description: "将性别中文转换为英文代码",
          detailTitle: "SINGLE_MAP 单值映射",
          detailContent: "SINGLE_MAP 用于<strong>字典值转换</strong>，类似 switch-case 语句。<br>本例将性别中文转换为英文代码：<br><code>男 → MALE</code><br><code>女 → FEMALE</code><br>适用于枚举值转换、状态码映射等场景。",
          mappings: [
            {
              key: 'mapping6',
              source: 'sex',
              sourceType: 'STRING',
              target: 'genderCode',
              targetType: 'STRING',
              mappingType: 'SINGLE_MAP',
              rule: '{"男":"MALE","女":"FEMALE"}'
            }
          ]
        },
        {
          title: "完成！",
          description: "所有映射规则已应用完毕",
          detailTitle: "映射完成",
          detailContent: "✅ 已成功将原始入参转换为目标格式。<br>右侧显示了最终的输出结果，包含了所有映射规则的处理结果。",
          mappings: []
        }
      ],
      
      // 当前可见的映射规则
      visibleMappings: []
    };
  },
  
  methods: {
    // 开始自动演示
    async startDemo() {
      this.resetDemo();
      this.isPlaying = true;
      
      for (let i = 0; i <= this.steps.length - 1; i++) {
        if (!this.isPlaying) break;
        
        this.currentStep = i;
        await this.applyCurrentStep();
        await this.sleep(2500);
      }
      
      this.isPlaying = false;
    },
    
    // 重置演示
    resetDemo() {
      this.currentStep = 0;
      this.isPlaying = false;
      this.targetData = {};
      this.visibleMappings = [];
      this.activeMappingIndex = -1;
    },
    
    // 下一步
    async nextStep() {
      if (this.currentStep < this.steps.length - 1) {
        this.currentStep++;
        await this.applyCurrentStep();
      }
    },
    
    // 上一步
    prevStep() {
      if (this.currentStep > 0) {
        this.currentStep--;
        this.rebuildTargetData();
      }
    },
    
    // 应用当前步骤
    async applyCurrentStep() {
      const step = this.steps[this.currentStep];
      
      if (step.mappings && step.mappings.length > 0) {
        // 添加当前步骤的映射规则到可见列表
        this.visibleMappings = [...this.visibleMappings, ...step.mappings];
        
        // 高亮当前映射
        this.activeMappingIndex = this.visibleMappings.length - 1;
        
        // 应用映射到目标数据
        await this.sleep(300);
        step.mappings.forEach(mapping => {
          this.applyMapping(mapping);
        });
        
        await this.sleep(500);
        this.activeMappingIndex = -1;
      }
    },
    
    // 应用单个映射规则
    applyMapping(mapping) {
      switch (mapping.mappingType) {
        case 'NAME':
          // 直接映射
          if (this.sourceData[mapping.source] !== undefined) {
            this.targetData[mapping.target] = this.sourceData[mapping.source];
          }
          break;
          
        case 'EXPRESSION':
          // 表达式映射
          if (mapping.target === 'patientInfo') {
            this.targetData[mapping.target] = 
              `${this.sourceData.patientName}，${this.sourceData.age}岁`;
          }
          break;
          
        case 'CONSTANT':
          // 固定值
          this.targetData[mapping.target] = mapping.rule;
          break;
          
        case 'SINGLE_MAP':
          // 字典映射
          try {
            const mapRule = JSON.parse(mapping.rule);
            const sourceValue = this.sourceData[mapping.source];
            this.targetData[mapping.target] = mapRule[sourceValue] || sourceValue;
          } catch (e) {
            console.error('Failed to parse mapping rule:', e);
          }
          break;
      }
      
      // 触发响应式更新
      this.targetData = { ...this.targetData };
    },
    
    // 重建目标数据（用于回退）
    rebuildTargetData() {
      this.targetData = {};
      this.visibleMappings = [];
      
      for (let i = 0; i <= this.currentStep; i++) {
        const step = this.steps[i];
        if (step.mappings) {
          this.visibleMappings = [...this.visibleMappings, ...step.mappings];
          step.mappings.forEach(mapping => {
            this.applyMapping(mapping);
          });
        }
      }
    },
    
    // 延迟函数
    sleep(ms) {
      return new Promise(resolve => setTimeout(resolve, ms));
    }
  },
  
  directives: {
    // JSON 高亮指令
    highlighter: {
      inserted(el, binding) {
        const data = binding.value;
        if (data && Object.keys(data).length > 0) {
          el.classList.add('has-data');
        }
      },
      update(el, binding) {
        const data = binding.value;
        if (data && Object.keys(data).length > 0) {
          el.classList.add('has-data');
        } else {
          el.classList.remove('has-data');
        }
      }
    }
  }
};
</script>

<style scoped>
.param-mapping-demo {
  padding: 20px;
  min-height: calc(100vh - 60px);
  background: #f5f7fa;
}

.demo-header {
  margin-bottom: 20px;
}

.header-desc {
  margin-top: 8px;
  color: #909399;
  font-size: 14px;
}

.demo-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.step-indicator {
  margin-left: auto;
  font-weight: bold;
  color: #409EFF;
  font-size: 16px;
}

.demo-content {
  margin-bottom: 20px;
}

.data-panel {
  height: 600px;
  display: flex;
  flex-direction: column;
}

.data-panel >>> .el-card__header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-weight: bold;
  padding: 15px 20px;
}

.source-panel >>> .el-card__header {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.target-panel >>> .el-card__header {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.data-panel >>> .el-card__body {
  flex: 1;
  overflow: auto;
  padding: 15px;
}

.json-container {
  height: 100%;
}

.json-container pre {
  margin: 0;
  padding: 15px;
  background: #2c3e50;
  color: #ecf0f1;
  border-radius: 4px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  min-height: 100%;
}

.json-container pre.has-data {
  animation: fadeIn 0.5s ease-in;
}

@keyframes fadeIn {
  from { opacity: 0.3; }
  to { opacity: 1; }
}

.process-panel {
  background: #fafafa;
}

.step-description {
  margin-bottom: 20px;
}

.mapping-rules {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.mapping-item {
  background: white;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  padding: 15px;
  transition: all 0.3s ease;
}

.mapping-item.active {
  border-color: #409EFF;
  background: #ecf5ff;
  box-shadow: 0 2px 12px 0 rgba(64, 158, 255, 0.3);
  transform: scale(1.02);
}

.mapping-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mapping-source,
.mapping-target {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mapping-source code,
.mapping-target code {
  background: #f5f7fa;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.mapping-arrow {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 18px;
  padding-left: 24px;
}

.mapping-type-label {
  font-size: 12px;
  color: #409EFF;
  font-weight: bold;
}

.mapping-rule {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #dcdfe6;
  font-size: 12px;
}

.rule-label {
  color: #909399;
  margin-right: 5px;
}

.mapping-rule code {
  background: #fff3cd;
  padding: 2px 6px;
  border-radius: 3px;
  color: #856404;
  font-size: 12px;
}

.demo-footer {
  background: #f9fafc;
}

.step-detail h4 {
  margin: 0 0 10px 0;
  color: #303133;
  font-size: 16px;
}

.step-detail p {
  margin: 0;
  color: #606266;
  line-height: 1.8;
  font-size: 14px;
}

/* 列表动画 */
.list-enter-active, .list-leave-active {
  transition: all 0.5s ease;
}

.list-enter {
  opacity: 0;
  transform: translateY(-20px);
}

.list-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
