<template>
  <el-container class="index-container">
    <el-aside :width="isCollapse?'64px':'250px'">
      <!-- Left logo -->
      <div class="title">
        <img src="../assets/LOGO.svg" />
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
  background: var(--dp-bg-layout);
  color: var(--dp-text-primary);
  text-align: left;
  border-right: 1px solid var(--dp-border);
}

.el-aside .title {
  height: 60px;
  line-height: 64px;
  background: linear-gradient(180deg, rgba(59, 130, 246, 0.08) 0%, transparent 100%);
  text-align: center;
}

.el-aside .title img {
  width: 50px;
  height: 50px;
  vertical-align: middle;
  filter: drop-shadow(0 0 6px rgba(34, 211, 238, 0.35));
}

.el-aside .title .title-text {
  color: var(--dp-text-primary);
  font-weight: 600;
  font-size: 20px;
  vertical-align: middle;
  letter-spacing: 1px;
  background: linear-gradient(135deg, #e2e8f0 20%, #22d3ee 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.el-header {
  padding: 0px;
  color: var(--dp-text-primary);
  width: 100%;
  height: 60px;
  background: var(--dp-bg-layout);
  display: flex;
  align-items: center;
  line-height: 60px;
  border-bottom: 1px solid var(--dp-border);
  box-shadow: 0 1px 12px rgba(2, 6, 18, 0.35);
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
  color: var(--dp-text-secondary);
  margin-right: 20px;
  cursor: pointer;
  transition: color 0.2s ease;
}

.el-header .collapse:hover {
  color: var(--dp-primary-light);
}

.el-main {
  padding: 0px;
  float: left;
  background-color: var(--dp-bg-page);
}
</style>
