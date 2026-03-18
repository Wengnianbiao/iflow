// router/index.js
import Vue from 'vue';
import VueRouter from 'vue-router';
import WorkFlowList from '@/views/Workflow.vue';
import WorkflowNode from '@/views/WorkflowNode.vue';
import InvokeLog from '@/views/InvokeLog.vue';

Vue.use(VueRouter);

const routes = [
  {
    path: '/',
    redirect: '/invoke/log' // 默认跳转到接口调用日志
  },
  {
    path: '/dashboard',
    name: 'Dashboard', 
    component: () => import('@/views/Dashboard.vue'),
    meta: {
      breadcrumb: [
        { name: '接口看板', path: '/dashboard', icon: 'el-icon-house' }
      ]
    }
  },
  {
    path: '/workflow',
    name: 'Workflow',
    component: WorkFlowList,
    meta: {
      breadcrumb: [
        { name: '工作流管理', path: null, icon: 'el-icon-document' },
        { name: '工作流列表', path: '/workflow' }
      ]
    }
  },
  {
    path: '/workflow/nodes',
    name: 'WorkflowNode',
    component: WorkflowNode,
    meta: {
      breadcrumb: [
        { name: '工作流管理', path: null, icon: 'el-icon-document' },
        { name: '工作流节点', path: '/workflow/nodes' }
      ]
    }
  },
  {
    path: '/workflow/node/param/config',
    name: 'NodeParamConfig',
    component: () => import('@/views/NodeParamConfig.vue'),
    meta: {
      breadcrumb: [
        { name: '工作流管理', path: null, icon: 'el-icon-document' },
        { name: '节点参数配置', path: '/workflow/node/param/config' }
      ]
    }
  },
  {
    path: '/workflow/node/schedule',
    name: 'NodeSchedule',
    component: () => import('@/views/NodeSchedule.vue'),
    meta: {
      breadcrumb: [
        { name: '工作流管理', path: null, icon: 'el-icon-document' },
        { name: '节点调度管理', path: '/workflow/node/schedule' }
      ]
    }
  },
  {
    path: '/guide/node-config-intro',
    name: 'NodeConfigIntro',
    component: () => import('@/views/NodeConfigIntro.vue'),
    meta: {
      breadcrumb: [
        { name: '配置手册', path: null, icon: 'el-icon-reading' },
        { name: '节点配置介绍', path: '/guide/node-config-intro' }
      ]
    }
  },
  {
    path: '/guide/quick-start',
    name: 'QuickStart',
    component: () => import('@/views/QuickStart.vue'),
    meta: {
      breadcrumb: [
        { name: '配置手册', path: null, icon: 'el-icon-reading' },
        { name: '快速开始', path: '/guide/quick-start' }
      ]
    }
  },
  {
    path: '/guide/solutions',
    name: 'ProductSolutions',
    component: () => import('@/views/ProductSolutions.vue'),
    meta: {
      breadcrumb: [
        { name: '配置手册', path: null, icon: 'el-icon-reading' },
        { name: '产品介绍', path: '/guide/solutions' }
      ]
    }
  },
  {
    path: '/demo/param-mapping',
    name: 'ParamMappingDemo',
    component: () => import('@/views/ParamMappingDemo.vue'),
    meta: {
      breadcrumb: [
        { name: '配置手册', path: null, icon: 'el-icon-reading' },
        { name: '参数映射演示', path: '/demo/param-mapping' }
      ]
    }
  },
  {
    path: '/workflow/node/param/guide',
    name: 'NodeParamGuide',
    component: () => import('@/views/NodeConfigIntro.vue'),
    meta: {
      breadcrumb: [
        { name: '配置手册', path: null, icon: 'el-icon-reading' },
        { name: '参数配置教程', path: '/workflow/node/param/guide' }
      ]
    }
  },
  {
    path: '/mock/guide',
    name: 'MockGuide',
    component: () => import('@/views/MockGuide.vue'),
    meta: {
      breadcrumb: [
        { name: 'Mock', path: null, icon: 'el-icon-set-up' },
        { name: 'Mock调试说明', path: '/mock/guide' }
      ]
    }
  },
  {
    path: '/mock/config',
    name: 'MockConfig',
    component: () => import('@/views/MockConfig.vue'),
    meta: {
      breadcrumb: [
        { name: 'Mock', path: null, icon: 'el-icon-set-up' },
        { name: 'Mock场景管理', path: '/mock/config' }
      ]
    }
  },
  {
    path: '/mock/test',
    name: 'MockInvokeTest',
    component: () => import('@/views/MockInvokeTest.vue'),
    meta: {
      breadcrumb: [
        { name: 'Mock', path: null, icon: 'el-icon-set-up' },
        { name: 'Mock调用测试', path: '/mock/test' }
      ]
    }
  },
  {
    path: '/jarvis/templates',
    name: 'JarvisTemplates',
    component: () => import('@/views/JarvisTemplates.vue'),
    meta: {
      breadcrumb: [
        { name: 'Mock', path: null, icon: 'el-icon-set-up' },
        { name: '入参模板', path: '/jarvis/templates' }
      ]
    }
  },
  {
    path: '/invoke/log', 
    name: 'InvokeLog',
    component: InvokeLog,
    meta: {
      breadcrumb: [
        { name: '接口调用日志', path: '/invoke/log', icon: 'el-icon-document-copy' }
      ]
    }
  },
  {
    path: '/invoke/tool',
    name: 'ApiInvokeTool',
    component: () => import('@/views/ApiInvokeTool.vue'),
    meta: {
      breadcrumb: [
        { name: '调用工具', path: null, icon: 'el-icon-s-promotion' },
        { name: 'HTTP调用', path: '/invoke/tool' }
      ]
    }
  },
  {
    path: '/invoke/db',
    name: 'DbInvokeTool',
    component: () => import('@/views/DbInvokeTool.vue'),
    meta: {
      breadcrumb: [
        { name: '调用工具', path: null, icon: 'el-icon-s-promotion' },
        { name: '数据库查询', path: '/invoke/db' }
      ]
    }
  },
  {
    path: '/node/extension',
    name: 'NodeExtensionConfig',
    component: () => import('@/views/NodeExtensionConfig.vue'),
    meta: {
      breadcrumb: [
        { name: 'SPI节点扩展', path: '/node/extension', icon: 'el-icon-cpu' }
      ]
    }
  }
];

const router = new VueRouter({
  mode: 'hash',
  base: process.env.BASE_URL,
  routes
});

export default router;
