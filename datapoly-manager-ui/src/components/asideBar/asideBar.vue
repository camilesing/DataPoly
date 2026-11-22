<template>
  <div class="aside-container">
    <el-row class="tac">
      <el-col :span="24">
        <!-- Left navigation -->
        <el-menu
          :router="true"
          unique-opened
          @open="handleOpen"
          @close="handleClose"
          :collapse="collapsed"
          :default-active="initActivePath"
        >
          <!-- Loop over routes, delegating each to the child component -->
          <asideBarItem v-for="router in routers" :router="router" v-if="showBarItem(router)" @setActivePath='setActivePath' :key="router.path"></asideBarItem>
        </el-menu>
      </el-col>
    </el-row>
  </div>
</template>
 
<script>
import asideBarItem from "@/components/asideBar/asideBarItem";
import { can, isAdmin } from "@/utils/permissions";

// 菜单项 → 权限模块（PermissionModule）：无读取权限的模块整项隐藏。
// 未列出的路由（仪表盘、关于、个人中心）对任何登录者可见；用户管理只看 ADMIN。
const ROUTE_MODULES = {
  "/interface/module": "API_MODULE",
  "/interface/list": "API",
  "/service/search": "API",
  "/datasource/driver": "DATASOURCE",
  "/datasource/list": "DATASOURCE",
  "/setting/group": "API_GROUP",
  "/setting/client": "APP_CLIENT",
  "/setting/firewall": "FIREWALL",
  "/setting/alarm": "ALARM",
  "/setting/topology": "NODE",
  "/setting/url": "NODE",
  "/mcp/client": "MCP_CLIENT",
  "/mcp/tool": "MCP_TOOL"
};
const ADMIN_ONLY_ROUTES = ["/user/manage"];

export default {
  name: "asideBar",
  components: {
    asideBarItem
  },
  data() {
    return {
      collapsed:false,
      initActivePath:'/dashboard'
    };
  },
  computed: {
    routers() {
      //console.log(this.$router.options);
      // Route list from $router.options
      return this.$router.options.routes[0].children;
    },
  },
  watch: {},
  methods: {
    showBarItem(router){
      if(router.hidden){
        return false
      }
      if(ADMIN_ONLY_ROUTES.indexOf(router.path) !== -1){
        return isAdmin();
      }
      const module = ROUTE_MODULES[router.path];
      if(module){
        return can(module, "R");
      }
      // A folder entry (e.g. /interface, /setting) stays as long as one child is visible
      if(router.children && router.children.length){
        return router.children.some(child => !child.hidden && this.routeAllowed(child));
      }
      return true;
    },
    routeAllowed(route){
      if(ADMIN_ONLY_ROUTES.indexOf(route.path) !== -1){
        return isAdmin();
      }
      const module = ROUTE_MODULES[route.path];
      return module ? can(module, "R") : true;
    },
    handleOpen(key, keyPath) {
      //console.log(key, keyPath, 'handleOpen');
    },
    handleClose(key, keyPath) {
      //console.log(key, keyPath, 'handleClose');
    },
    updateCollapse(collapse){
      this.collapsed=collapse;
      //console.log("=========="+this.isCollapse);
    },
    setActivePath(path){
      this.initActivePath=path;
      window.sessionStorage.setItem("activePath", path);
      //console.log("update active path "+this.initActivePath);
    },
    getActivePath(){
      return window.sessionStorage.getItem("activePath");
    }
  },
  created() {
    this.initActivePath = this.getActivePath();
    //console.log("init active path "+this.initActivePath);
  },
  mounted() {
    // console.log(this.$router.options.routes[1])
  }
};
</script>
 
 <style scoped>
.aside-container {
  padding-top: 2px;
}

.el-menu {
  padding: 0;
  border-right: none;
}

</style>