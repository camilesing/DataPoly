<template>
  <el-container class="index-container">
    <el-aside :width="isCollapse?'64px':'250px'">
      <!-- Left logo -->
      <div class="title">
        <img src="../assets/logo.svg" />
        <span class="title-text"
              v-if="!isCollapse">{{title}}</span>
      </div>
      <!-- Left navigation menu -->
      <asideBar ref="asideBar"></asideBar>
    </el-aside>
    <el-main>
      <el-header>
        <!-- Collapse toggle icon -->
        <div @click="handleToggleCollapse"
             class="collapse">
          <i class="el-icon-s-unfold"
             v-if="isCollapse"></i>
          <i class="el-icon-s-fold"
             v-else></i>
        </div>

        <!-- Breadcrumb -->
        <breadcrumb></breadcrumb>
        <!-- Right header area -->
        <div class="header-right">
          <languageSwitch></languageSwitch>
          <userDropdown></userDropdown>
        </div>
      </el-header>

      <!-- Main content area -->
      <viewMain></viewMain>
    </el-main>
  </el-container>
</template>

<script>
import asideBar from "@/components/asideBar/asideBar";
import breadcrumb from "@/components/breadcrumb/index";
import userDropdown from "@/components/userDropdown/index"
import viewMain from "@/views/viewer";
import languageSwitch from "@/components/languageSwitch/index";

export default {
  name: "home",
  components: {
    asideBar,
    breadcrumb,
    userDropdown,
    viewMain,
    languageSwitch
  },
  data () {
    return {
      title: "DataPoly",
      isCollapse: null
    };
  },
  computed: {},
  watch: {},
  methods: {
    handleToggleCollapse () {
      let status = !this.isCollapse;
      this.isCollapse = status;
      this.$refs.asideBar.updateCollapse(status);
    }
  },
  created () {
  },
  mounted () { }
};
</script>

<style scoped>
.index-container {
  height: 100%;
}

.el-aside {
  float: left;
  background: #002140;
  color: #333;
  text-align: left;
}

.el-aside .title {
  height: 60px;
  background: #001529;
  line-height: 64px;
  background: #002140;
  text-align: center;
}

.el-aside .title img {
  width: 50px;
  height: 50px;
  vertical-align: middle;
}

.el-aside .title .title-text {
  color: #fff;
  font-weight: 600;
  font-size: 20px;
  vertical-align: middle;
}

.el-header {
  padding: 0px;
  color: #ffffff;
  width: 100%;
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  line-height: 60px;
}

.el-header .header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  padding-right: 20px;
}

.el-header .header-right > * {
  vertical-align: middle;
}

.el-header .collapse {
  display: flex;
  align-items: center;
  height: 100%;
  padding-left: 2px;
  font-size: 22px;
  color: #838383;
  margin-right: 20px;
  cursor: pointer;
}

.el-main {
  padding: 0px;
  float: left;
  background-color: #eaedf1;
}
</style>
