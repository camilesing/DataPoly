<template>
  <div class="asideBarItem-container">
    <!-- Render recursively if it has children -->
    <el-submenu :index="router.path" v-if="hasChildrenAndShow(router)">
      <template slot="title">
      <i :class="router.icon"></i>
      <span slot="title">{{ $t(router.name) }}</span>
      </template>
      <!-- Render child menus recursively -->
      <asideBarItem v-for="(child, childKey) in router.children" :key="child.path" :router="child"></asideBarItem>
    </el-submenu>
    <!-- Otherwise render as a single-level menu item -->
    <el-menu-item v-else :key="router.path" :index="router.path" @click="saveActivePath(router.path)">
       <i :class="router.icon"></i>
       <span>{{ $t(router.name) }}</span>
    </el-menu-item>
  </div>
</template>

<script>
export default {
  name: "asideBarItem",
  props: {
    router: {
      type: Object
    },
  },
  components: {},
  data() {
    return {
    };
  },
  computed: {
    // router () {
    //   return this.$router.options.routes
    // }
  },
  watch: {},
  methods: {
    hasChildrenAndShow(router){
      if(router.hidden){
        return false
      }

      return router.hasOwnProperty('children');
    },
    saveActivePath(path) {
      //alert(path);
      this.$emit('setActivePath',path);
    },
  },
  created() {
  },
  mounted() {}
};
</script>

<style scoped>
.el-menu-item.is-active {
  background-color: #1890ff !important;
}

/* Hide label text */
.el-menu--collapse .asideBarItem-container span{
  display: none;
}
/* Hide the arrow icon */
.el-menu--collapse .asideBarItem-container .el-submenu__title .el-submenu__icon-arrow{
  display: none;
}

</style>
