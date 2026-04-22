<!-- src/components/Navbar.vue -->
<template>
  <div class="navbar">
    <!-- 项目名称和折叠按钮在同一行 -->
    <div class="navbar-header">
      <div class="project-title" v-if="!isCollapse">
        <span>Interface Agent</span>
      </div>
      <div class="collapse-toggle" @click="handleToggleCollapse">
        <i :class="isCollapse ? 'el-icon-s-unfold' : 'el-icon-s-fold'"></i>
      </div>
    </div>

    <el-menu
      :default-active="activeIndex"
      class="el-menu-vertical-demo"
      mode="vertical"
      @select="handleSelect"
      background-color="#545c64"
      text-color="#fff"
      active-text-color="#ffd04b"
      :collapse="isCollapse"
      :collapse-transition="false"
      style="height: calc(100% - 50px); overflow-y: auto;"
    >
      <el-menu-item index="1">
        <i class="el-icon-house"></i>
        <span slot="title">接口看板</span>
      </el-menu-item>
      <el-submenu index="2">
        <template slot="title">
          <i class="el-icon-document"></i>
          <span>工作流管理</span>
        </template>
        <el-menu-item index="2-1">工作流列表</el-menu-item>
        <el-submenu index="2-2">
          <template slot="title">工作流节点列表</template>
          <el-menu-item index="2-2-1">节点配置</el-menu-item>
          <el-menu-item index="2-2-2">节点调度</el-menu-item>
        </el-submenu>
        <el-menu-item index="2-3">节点参数配置列表</el-menu-item>
      </el-submenu>
      <el-menu-item index="3">
        <i class="el-icon-document-copy"></i>
        <span slot="title">接口调用日志</span>
      </el-menu-item>
      <el-submenu index="9">
        <template slot="title">
          <i class="el-icon-s-promotion"></i>
          <span>调用工具</span>
        </template>
        <el-menu-item index="9-1">HTTP调用</el-menu-item>
        <el-menu-item index="9-2">数据库查询</el-menu-item>
      </el-submenu>
      <el-submenu index="4">
        <template slot="title">
          <i class="el-icon-set-up"></i>
          <span>Mock</span>
        </template>
        <el-menu-item index="4-0">Mock调试说明</el-menu-item>
        <el-menu-item index="4-1">Mock场景管理</el-menu-item>
        <el-menu-item index="4-2">Mock调用测试</el-menu-item>
      </el-submenu>
      <el-menu-item index="5">
        <i class="el-icon-cpu"></i>
        <span slot="title">SPI节点扩展</span>
      </el-menu-item>
      <el-submenu index="6">
        <template slot="title">
          <i class="el-icon-reading"></i>
          <span>配置手册</span>
        </template>
        <el-menu-item index="6-1">快速开始</el-menu-item>
        <el-menu-item index="6-3">参数映射演示</el-menu-item>
      </el-submenu>
      <el-menu-item index="7">
        <i class="el-icon-setting"></i>
        <span slot="title">系统设置</span>
      </el-menu-item>
      <el-submenu index="8">
        <template slot="title">
          <i class="el-icon-user"></i>
          <span>用户中心</span>
        </template>
        <el-menu-item index="8-1">个人信息</el-menu-item>
        <el-menu-item index="8-2">修改密码</el-menu-item>
        <el-menu-item index="8-3">退出登录</el-menu-item>
      </el-submenu>
    </el-menu>
  </div>
</template>

<script>
export default {
  name: 'NavHeader',
  props: {
    isCollapse: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      activeIndex: '1'
    };
  },
  methods: {
    handleToggleCollapse() {
      this.$emit('toggle-collapse');
    },
    handleSelect(key) {
      const currentPath = this.$route.path;
      switch (key) {
        case '1':
          if (currentPath !== '/dashboard') {
            this.$router.push('/dashboard');
          }
          break;
        case '2-1':
          if (currentPath !== '/workflow') {
            this.$router.push('/workflow');
          }
          break;
        case '2-2-1':
          if (currentPath !== '/workflow/nodes') {
            this.$router.push('/workflow/nodes');
          }
          break;
        case '2-2-2':
          if (currentPath !== '/workflow/node/schedule') {
            this.$router.push('/workflow/node/schedule');
          }
          break;
        case '2-3':
          if (currentPath !== '/workflow/node/param/config') {
            this.$router.push('/workflow/node/param/config');
          }
          break;
        case '3':
          if (currentPath !== '/invoke/log') {
            this.$router.push('/invoke/log');
          }
          break;
        case '9-1':
          if (currentPath !== '/invoke/tool') {
            this.$router.push('/invoke/tool');
          }
          break;
        case '9-2':
          if (currentPath !== '/invoke/db') {
            this.$router.push('/invoke/db');
          }
          break;
        case '4-0':
          if (currentPath !== '/mock/guide') {
            this.$router.push('/mock/guide');
          }
          break;
        case '4-1':
          if (currentPath !== '/mock/config') {
            this.$router.push('/mock/config');
          }
          break;
        case '4-2':
          if (currentPath !== '/mock/test') {
            this.$router.push('/mock/test');
          }
          break;
        case '5':
          if (currentPath !== '/node/extension') {
            this.$router.push('/node/extension');
          }
          break;
        case '6-1':
          if (currentPath !== '/guide/quick-start') {
            this.$router.push('/guide/quick-start');
          }
          break;
        case '6-3':
          if (currentPath !== '/demo/param-mapping') {
            this.$router.push('/demo/param-mapping');
          }
          break;
        case '7':
          if (currentPath !== '/settings') {
            this.$router.push('/settings');
          }
          break;
        case '8-3':
          console.log('退出登录');
          break;
        default:
          console.log('未知菜单项');
      }
    }
  },
  mounted() {
    const currentPath = this.$route.path;
    if (currentPath === '/' || currentPath === '/dashboard') {
      this.activeIndex = '1';
    } else if (currentPath === '/workflow') {
      this.activeIndex = '2-1';
    } else if (currentPath === '/workflow/nodes') {
      this.activeIndex = '2-2-1';
    } else if (currentPath === '/workflow/node/schedule') {
      this.activeIndex = '2-2-2';
    } else if (currentPath === '/workflow/node/param/config') {
      this.activeIndex = '2-3';
    } else if (currentPath === '/demo/param-mapping') {
      this.activeIndex = '6-3';
    } else if (currentPath === '/guide/quick-start') {
      this.activeIndex = '6-1';
    } else if (currentPath === '/invoke/log') {
      this.activeIndex = '3';
    } else if (currentPath === '/invoke/tool') {
      this.activeIndex = '9-1';
    } else if (currentPath === '/invoke/db') {
      this.activeIndex = '9-2';
    } else if (currentPath === '/mock/guide') {
      this.activeIndex = '4-0';
    } else if (currentPath === '/mock/config') {
      this.activeIndex = '4-1';
    } else if (currentPath === '/mock/test') {
      this.activeIndex = '4-2';
    } else if (currentPath === '/node/extension') {
      this.activeIndex = '5';
    } else if (currentPath === '/settings') {
      this.activeIndex = '7';
    }
  },
  watch: {
    '$route'(to) {
      if (to.path === '/' || to.path === '/dashboard') {
        this.activeIndex = '1';
      } else if (to.path === '/workflow') {
        this.activeIndex = '2-1';
      } else if (to.path === '/workflow/nodes') {
        this.activeIndex = '2-2-1';
      } else if (to.path === '/workflow/node/schedule') {
        this.activeIndex = '2-2-2';
      } else if (to.path === '/workflow/node/param/config') {
        this.activeIndex = '2-3';
      } else if (to.path === '/demo/param-mapping') {
        this.activeIndex = '6-3';
      } else if (to.path === '/guide/quick-start') {
        this.activeIndex = '6-1';
      } else if (to.path === '/invoke/log') {
        this.activeIndex = '3';
      } else if (to.path === '/invoke/tool') {
        this.activeIndex = '9-1';
      } else if (to.path === '/invoke/db') {
        this.activeIndex = '9-2';
      } else if (to.path === '/mock/guide') {
        this.activeIndex = '4-0';
      } else if (to.path === '/mock/config') {
        this.activeIndex = '4-1';
      } else if (to.path === '/mock/test') {
        this.activeIndex = '4-2';
      } else if (to.path === '/node/extension') {
        this.activeIndex = '5';
      } else if (to.path === '/settings') {
        this.activeIndex = '7';
      }
    }
  }
};
</script>

<style scoped>
.navbar {
  height: 100vh;
  width: 100%;
  position: relative;
  background-color: #545c64;
}

/* 顶部区域：项目名称和折叠按钮 */
.navbar-header {
  height: 50px;
  display: flex;
  align-items: center;
  background-color: #434a50;
  justify-content: flex-end;
}

/* 项目标题样式 */
.project-title {
  text-align: left;
  padding-left: 20px;
  padding-right: 15px;
  margin-right: auto;
  color: #fff;
  font-size: 17px;
  font-weight: bold;
  letter-spacing: 0.5px;
  overflow: hidden;
  white-space: nowrap;
}

.project-title-short {
  font-size: 16px;
  font-weight: bold;
}

/* 折叠按钮样式 */
.collapse-toggle {
  width: 30px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #fff;
  font-size: 16px;
  transition: background-color 0.3s;
  margin-right: 5px;
}

.collapse-toggle:hover {
  background-color: #409eff;
}

.collapse-toggle i {
  font-size: 20px;
}

.el-menu {
  border-right: none;
  height: calc(100% - 50px);
}

.el-menu-item,
.el-submenu {
  margin: 0;
  padding: 0;
}

.el-menu-item.is-active {
  background-color: #409eff !important;
}

.el-menu-item i,
.el-submenu i {
  margin-right: 10px;
}

.el-submenu .el-menu {
  background-color: #545c64;
}

/* 所有子菜单项统一左边距 */
.el-submenu > .el-menu > .el-menu-item {
  padding-left: 50px !important;
  min-width: 200px;
}

/* 嵌套的submenu标题 - 强制覆盖Element UI默认样式 */
::v-deep .el-submenu > .el-menu > .el-submenu > .el-submenu__title {
  padding-left: 50px !important;
}

/* 三级菜单项（更深一层的缩进） */
.el-submenu .el-submenu > .el-menu > .el-menu-item {
  padding-left: 70px !important;
}

/* 折叠状态下的样式调整 */
.el-menu--collapse {
  width: 64px;
}

.el-menu--collapse .el-submenu__title span {
  display: none;
}

.el-menu--collapse .el-submenu .el-menu {
  min-width: 200px;
}
</style>

<style>
/* 全局样式 - 强制覆盖Element UI嵌套submenu的默认padding */
.el-menu .el-menu .el-submenu__title {
  padding-left: 50px !important;
}
</style>
