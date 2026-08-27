<template>
  <el-dropdown @command="handleSetLanguage" trigger="click">
    <span class="language-dropdown">
      <i class="el-icon-language"></i>
      <span class="language-name">{{ languageName }}</span>
      <i class="el-icon-arrow-down el-icon--right"></i>
    </span>
    <el-dropdown-menu slot="dropdown">
      <el-dropdown-item command="zh-CN" :disabled="locale === 'zh-CN'">
        简体中文
      </el-dropdown-item>
      <el-dropdown-item command="en-US" :disabled="locale === 'en-US'">
        English
      </el-dropdown-item>
    </el-dropdown-menu>
  </el-dropdown>
</template>

<script>
export default {
  name: 'LanguageSwitch',
  data() {
    return {
      locale: 'zh-CN'
    }
  },
  computed: {
    languageName() {
      return this.locale === 'zh-CN' ? '简体中文' : 'English'
    }
  },
  created() {
    this.locale = localStorage.getItem('locale') || this.getDefaultLocale()
  },
  methods: {
    getDefaultLocale() {
      const browserLang = navigator.language || 'zh-CN'
      return browserLang.startsWith('en') ? 'en-US' : 'zh-CN'
    },
    handleSetLanguage(lang) {
      this.locale = lang
      this.$i18n.locale = lang
      localStorage.setItem('locale', lang)
      this.$message({
        message: this.locale === 'zh-CN' ? '语言切换成功' : 'Language switched successfully',
        type: 'success'
      })
    }
  }
}
</script>

<style scoped>
.language-dropdown {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #606266;
  font-size: 14px;
  padding: 0 10px;
}

.language-dropdown:hover {
  color: #409EFF;
}

.language-name {
  margin: 0 5px;
}
</style>
