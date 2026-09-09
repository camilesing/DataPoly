import Vue from 'vue'
import Router from 'vue-router'
import extension from '@extension'

Vue.use(Router);

/////////////////////////////////////////////////////////////////////////
// Route configuration
// Menu names use menu.xxx keys, translated via $t() in layout.vue
/////////////////////////////////////////////////////////////////////////
const constantRouter = new Router({
  routes: [
    {
      path: '/',
      name: 'menu.dashboard',
      component: () => import('@/views/layout'),
      redirect: '/dashboard',
      children: [
        {
          path: '/dashboard',
          name: 'menu.overview',
          icon: "el-icon-menu",
          component: () => import('@/views/dashboard/index')
        },
        {
          path: '/datasource',
          name: 'menu.datasource',
          icon: "el-icon-coin",
          component: () => import('@/views/datasource/index'),
          children: [
            {
              path: '/datasource/driver',
              name: 'menu.driverConfig',
              icon: "el-icon-help",
              component: () => import('@/views/datasource/driver'),
            },
            {
              path: '/datasource/list',
              name: 'menu.connectionManage',
              icon: "el-icon-bank-card",
              component: () => import('@/views/datasource/list')
            }
          ]
        },
        {
          path: '/setting',
          name: 'menu.setting',
          icon: "el-icon-s-tools",
          component: () => import('@/views/setting/index'),
          children: [
            {
              path: '/setting/group',
              name: 'menu.authGroup',
              icon: "el-icon-tickets",
              component: () => import('@/views/setting/group'),
            },
            {
              path: '/setting/client',
              name: 'menu.clientApp',
              icon: "el-icon-pie-chart",
              component: () => import('@/views/setting/client')
            },
            {
              path: '/setting/firewall',
              name: 'menu.accessControl',
              icon: "el-icon-notebook-2",
              component: () => import('@/views/setting/firewall')
            },
            {
              path: '/setting/alarm',
              name: 'menu.alarmConfig',
              icon: "el-icon-message-solid",
              component: () => import('@/views/setting/alarm')
            },
            {
              path: '/setting/topology',
              name: 'menu.topology',
              icon: "el-icon-link",
              component: () => import('@/views/setting/topology')
            }
          ]
        },
        {
          path: '/interface',
          name: 'menu.interface',
          icon: "el-icon-edit-outline",
          component: () => import('@/views/interface/index'),
          children: [
            {
              path: '/interface/module',
              name: 'menu.moduleConfig',
              icon: "el-icon-folder",
              component: () => import('@/views/interface/module'),
            },
            {
              path: '/interface/list',
              name: 'menu.interfaceConfig',
              icon: "el-icon-refrigerator",
              component: () => import('@/views/interface/list'),
            }
          ]
        },
        {
          path: '/service',
          name: 'menu.service',
          icon: "el-icon-school",
          component: () => import('@/views/service/index'),
          children: [
            {
              path: '/service/search',
              name: 'menu.onlineInterface',
              icon: "el-icon-lightning",
              component: () => import('@/views/service/search'),
            }
          ]
        },
        {
          path: '/mcp',
          name: 'menu.mcp',
          icon: "el-icon-s-promotion",
          component: () => import('@/views/mcp/index'),
          children: [
            {
              path: '/mcp/client',
              name: 'menu.tokenConfig',
              icon: "el-icon-s-platform",
              component: () => import('@/views/mcp/client'),
            },
            {
              path: '/mcp/tool',
              name: 'menu.toolConfig',
              icon: "el-icon-setting",
              component: () => import('@/views/mcp/tool'),
            }
          ]
        },
        {
          path: '/aboutme',
          name: 'menu.about',
          icon: "el-icon-s-custom",
          component: () => import('@/views/aboutme/readme')
        },
        {
          path: '/user/self',
          name: 'menu.personal',
          hidden: true,
          component: () => import('@/views/user/self')
        },
        {
          path: '/interface/create',
          name: 'menu.createInterface',
          hidden: true,
          component: () => import('@/views/interface/create')
        },
        {
          path: '/interface/update',
          name: 'menu.updateInterface',
          hidden: true,
          component: () => import('@/views/interface/update')
        },
        {
          path: '/interface/detail',
          name: 'menu.viewInterface',
          hidden: true,
          component: () => import('@/views/interface/detail')
        },
        {
          path: '/service/detail',
          name: 'menu.interfaceDetail',
          hidden: true,
          component: () => import('@/views/service/detail')
        },
        // Pages provided by the local UI extension (datapoly-extension-ui);
        // the '@extension' entry is an empty stub when the directory is absent.
        ...extension.routes
      ],
    },

    {
      path: '/login',
      name: 'menu.login',
      component: () => import('@/views/login')
    }
  ]
});

export default constantRouter;
