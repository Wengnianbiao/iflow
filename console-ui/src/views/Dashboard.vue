<!-- src/views/Dashboard.vue -->
<template>
  <div class="dashboard">
    <!-- 数据概览卡片区 -->
    <div class="stats-overview">
      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon workflow-icon">
          <i class="el-icon-document"></i>
        </div>
        <div class="stat-content">
          <div class="stat-label">工作流总数</div>
          <div class="stat-value">{{ workflowCount }}</div>
        </div>
        <div class="stat-footer">
          <el-button type="text" size="small" @click="goToWorkflow">查看详情 <i class="el-icon-arrow-right"></i></el-button>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon node-icon">
          <i class="el-icon-share"></i>
        </div>
        <div class="stat-content">
          <div class="stat-label">接口节点总数</div>
          <div class="stat-value">{{ nodeCount }}</div>
        </div>
        <div class="stat-footer">
          <el-button type="text" size="small" @click="goToNode">查看详情 <i class="el-icon-arrow-right"></i></el-button>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon invoke-icon">
          <i class="el-icon-document-copy"></i>
        </div>
        <div class="stat-content">
          <div class="stat-label">今日调用次数</div>
          <div class="stat-value">{{ todayInvokeCount }}</div>
        </div>
        <div class="stat-footer">
          <el-button type="text" size="small" @click="goToInvokeLog">查看日志 <i class="el-icon-arrow-right"></i></el-button>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon active-icon">
          <i class="el-icon-success"></i>
        </div>
        <div class="stat-content">
          <div class="stat-label">启用工作流</div>
          <div class="stat-value">{{ activeWorkflowCount }}</div>
        </div>
        <div class="stat-footer">
          <el-button type="text" size="small" @click="goToWorkflow">管理工作流 <i class="el-icon-arrow-right"></i></el-button>
        </div>
      </el-card>
    </div>

    <!-- 接口调用统计看板 -->
    <LatestInvokeTime />
  </div>
</template>

<script>
import LatestInvokeTime from '@/components/InvokeTimeBoard.vue';
import { getAllWorkflowsUnpaged } from '@/api/workflow';
import { getNodeListUnpaged } from '@/api/workflownode';
import { fetchTodayStats } from '@/api/invokeLog';

export default {
  name: 'SystemDashboard',
  components: {
    LatestInvokeTime
  },
  data() {
    return {
      workflowCount: 0,
      nodeCount: 0,
      todayInvokeCount: 0,
      activeWorkflowCount: 0
    };
  },
  mounted() {
    this.loadStatistics();
  },
  methods: {
    async loadStatistics() {
      await Promise.all([
        this.loadWorkflowStats(),
        this.loadNodeStats(),
        this.loadInvokeStats()
      ]);
    },

    async loadWorkflowStats() {
      try {
        const response = await getAllWorkflowsUnpaged();
        if (response.status === 200 && response.data.code === 200) {
          const workflows = response.data.data || [];
          this.workflowCount = workflows.length;
          this.activeWorkflowCount = workflows.filter(w => w.status === 1).length;
        }
      } catch (error) {
        console.error('获取工作流统计失败:', error);
      }
    },

    async loadNodeStats() {
      try {
        const response = await getNodeListUnpaged();
        if (response.status === 200 && response.data.code === 200) {
          const nodes = response.data.data || [];
          this.nodeCount = nodes.length;
        }
      } catch (error) {
        console.error('获取节点统计失败:', error);
      }
    },

    async loadInvokeStats() {
      try {
        // 直接调用今日统计接口
        const response = await fetchTodayStats();
        if (response.status === 200 && response.data.code === 200) {
          const stats = response.data.data || [];
          // 累加所有接口的调用次数
          this.todayInvokeCount = stats.reduce((sum, stat) => sum + stat.count, 0);
        }
      } catch (error) {
        console.error('获取调用统计失败:', error);
        this.todayInvokeCount = 0;
      }
    },

    goToWorkflow() {
      this.$router.push('/workflow');
    },

    goToNode() {
      this.$router.push('/workflow/nodes');
    },

    goToInvokeLog() {
      this.$router.push('/invoke/log');
    }
  }
};
</script>

<style scoped>
.dashboard {
  padding: 25px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
}

/* 数据概览卡片区 */
.stats-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.stat-card {
  cursor: default;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: white;
  margin-bottom: 15px;
}

.workflow-icon {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.node-icon {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.invoke-icon {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.active-icon {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.stat-content {
  margin-bottom: 15px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #303133;
}

.stat-footer {
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.stat-footer .el-button {
  padding: 0;
  color: #409eff;
  font-size: 13px;
}

.stat-footer .el-button:hover {
  color: #66b1ff;
}

/* 响应式布局 */
@media (max-width: 1200px) {
  .stats-overview {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .dashboard {
    padding: 15px;
  }
  
  .stats-overview {
    grid-template-columns: 1fr;
  }
}
</style>