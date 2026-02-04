<template>
  <div class="invoke-time-board">
    <!-- 今日接口调用统计 -->
    <el-card class="stats-card" shadow="never">
      <div slot="header" class="card-header">
        <div class="header-left">
          <i class="el-icon-data-line"></i>
          <span class="card-title">今日接口调用统计</span>
        </div>
        <el-button 
          size="small"
          icon="el-icon-refresh" 
          @click="refreshData"
          :loading="loading"
        >
          刷新
        </el-button>
      </div>

      <div v-loading="loading">
        <el-table 
          v-if="todayStats.length > 0"
          :data="todayStats" 
          style="width: 100%"
          :header-cell-style="{ background: '#fafafa', color: '#606266' }"
        >
          <el-table-column label="接口名称" min-width="200">
            <template slot-scope="scope">
              <div class="interface-name">
                <i class="el-icon-connection"></i>
                {{ scope.row.nodeName }}
              </div>
            </template>
          </el-table-column>
          
          <el-table-column label="调用次数" width="120" align="center">
            <template slot-scope="scope">
              <el-tag type="primary" size="medium">{{ scope.row.count }}</el-tag>
            </template>
          </el-table-column>
          
          <el-table-column label="平均耗时" width="120" align="center">
            <template slot-scope="scope">
              <span :class="['time-value', getTimeClass(scope.row.averageTime)]">
                {{ Math.round(scope.row.averageTime) }} ms
              </span>
            </template>
          </el-table-column>
          
          <el-table-column label="最快" width="100" align="center">
            <template slot-scope="scope">
              <span class="time-value fast">{{ scope.row.minTime }} ms</span>
            </template>
          </el-table-column>
          
          <el-table-column label="最慢" width="100" align="center">
            <template slot-scope="scope">
              <span :class="['time-value', scope.row.maxTime > 1000 ? 'very-slow' : 'slow']">
                {{ scope.row.maxTime }} ms
              </span>
            </template>
          </el-table-column>
        </el-table>
        
        <div v-else class="empty-state">
          <i class="el-icon-info"></i>
          <span>今日暂无调用记录</span>
        </div>
      </div>
    </el-card>

    <!-- 最近7日调用统计 -->
    <el-card class="stats-card" shadow="never">
      <div slot="header" class="card-header">
        <div class="header-left">
          <i class="el-icon-date"></i>
          <span class="card-title">最近7日调用统计</span>
        </div>
      </div>

      <div v-loading="loading">
        <el-table 
          v-if="last7DaysStats.length > 0"
          :data="last7DaysStats" 
          style="width: 100%"
          :header-cell-style="{ background: '#fafafa', color: '#606266' }"
        >
          <el-table-column label="接口名称" min-width="200">
            <template slot-scope="scope">
              <div class="interface-name">
                <i class="el-icon-connection"></i>
                {{ scope.row.nodeName }}
              </div>
            </template>
          </el-table-column>
          
          <el-table-column label="调用次数" width="120" align="center">
            <template slot-scope="scope">
              <el-tag type="info" size="medium">{{ scope.row.count }}</el-tag>
            </template>
          </el-table-column>
          
          <el-table-column label="平均耗时" width="120" align="center">
            <template slot-scope="scope">
              <span :class="['time-value', getTimeClass(scope.row.averageTime)]">
                {{ Math.round(scope.row.averageTime) }} ms
              </span>
            </template>
          </el-table-column>
          
          <el-table-column label="最快" width="100" align="center">
            <template slot-scope="scope">
              <span class="time-value fast">{{ scope.row.minTime }} ms</span>
            </template>
          </el-table-column>
          
          <el-table-column label="最慢" width="100" align="center">
            <template slot-scope="scope">
              <span :class="['time-value', scope.row.maxTime > 1000 ? 'very-slow' : 'slow']">
                {{ scope.row.maxTime }} ms
              </span>
            </template>
          </el-table-column>
        </el-table>
        
        <div v-else class="empty-state">
          <i class="el-icon-info"></i>
          <span>最近7日暂无调用记录</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { fetchTodayStats, fetchLast7DaysStats } from '@/api/invokeLog';

export default {
  name: 'InvokeTimeBoard',
  data() {
    return {
      todayStats: [],
      last7DaysStats: [],
      loading: false
    };
  },
  mounted() {
    this.fetchStatsData();
  },
  methods: {
    async fetchStatsData() {
      this.loading = true;
      try {
        const [todayRes, last7DaysRes] = await Promise.all([
          fetchTodayStats(),
          fetchLast7DaysStats()
        ]);

        if (todayRes.status === 200 && todayRes.data.code === '200') {
          this.todayStats = todayRes.data.rsp || [];
        }

        if (last7DaysRes.status === 200 && last7DaysRes.data.code === '200') {
          this.last7DaysStats = last7DaysRes.data.rsp || [];
        }
      } catch (error) {
        console.error('获取统计数据失败:', error);
        this.$message.error('获取统计数据失败: ' + error.message);
      } finally {
        this.loading = false;
      }
    },

    refreshData() {
      this.fetchStatsData();
    },

    getTimeClass(time) {
      if (time < 100) return 'fast';
      if (time < 500) return 'normal';
      if (time < 1000) return 'slow';
      return 'very-slow';
    }
  }
};
</script>

<style scoped>
.invoke-time-board {
  padding: 0;
}

.stats-card {
  border-radius: 4px;
  border: 1px solid #ebeef5;
  margin-bottom: 20px;
}

.stats-card:last-child {
  margin-bottom: 0;
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-left i {
  font-size: 18px;
  color: #409eff;
}

.card-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

/* 接口名称 */
.interface-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: #303133;
}

.interface-name i {
  color: #909399;
  font-size: 14px;
}

/* 时间值 */
.time-value {
  font-weight: 600;
  font-size: 14px;
}

.time-value.fast {
  color: #67c23a;
}

.time-value.normal {
  color: #409eff;
}

.time-value.slow {
  color: #e6a23c;
}

.time-value.very-slow {
  color: #f56c6c;
}

/* 空状态 */
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: #909399;
  font-size: 14px;
}

.empty-state i {
  font-size: 18px;
}

/* 表格样式优化 */
::v-deep .el-table {
  font-size: 14px;
}

::v-deep .el-table th {
  font-weight: 500;
  padding: 14px 0;
}

::v-deep .el-table td {
  padding: 16px 0;
}

::v-deep .el-table tbody tr:hover > td {
  background-color: #f5f7fa;
}

::v-deep .el-table__empty-block {
  min-height: 100px;
}
</style>
