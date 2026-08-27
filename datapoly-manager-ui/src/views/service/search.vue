<template>
  <el-card>
    <el-container class="app-container">
      <!-- Header area -->
      <el-header class="app-header">
        <div class="header-content">
          <div class="search-box">
            <el-input v-model="searchKeyword"
                      :placeholder="$t('service.searchPlaceholder')"
                      class="search-input"
                      @keyup.enter="handleSearch">
            </el-input>
            <el-button type="primary"
                       class="search-btn"
                       @click="handleSearch">
              {{ $t('common.search') }}
            </el-button>
          </div>
        </div>
      </el-header>

      <el-container>
        <!-- Left sidebar -->
        <el-aside width="280px"
                  class="app-aside">
          <div class="filter-section">
            <h3 class="filter-title">{{ $t('service.moduleFilter') }}</h3>
            <el-checkbox-group v-model="selectedModuleList"
                               class="checkbox-group">
              <el-checkbox v-for="item in moduleItemList"
                           :key="item.id"
                           :label="item.id"
                           @change="handleSearch"
                           class="filter-checkbox">
                {{ item.name }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </el-aside>

        <!-- Right content area -->
        <el-main class="app-main">
          <!-- Card grid layout -->
          <div class="card-grid">
            <el-card v-for="item in allData"
                     :key="item.id"
                     class="content-card"
                     @click.native="handleGoDetail(item)"
                     :body-style="{ padding: '0px' }">
              <div class="card-content">
                <h4 class="card-title">【{{item.id}}】{{ item.name }}</h4>
                <el-tooltip class="item"
                            effect="dark"
                            :content="$t('service.requireAuth')"
                            placement="top-end">
                  <i v-if="!item.open"
                     class="el-icon-user operator-icon"
                     style="float: right;"></i></el-tooltip>
                <div class="card-meta">
                  <span class="card-date">{{ $t('service.onlineTime') }}{{ item.createTime }}</span>
                </div>
                <el-tag>{{ item.method }}</el-tag><span>{{ item.path }}</span>
                <div class="card-meta">
                  <span class="card-tag">{{ $t('service.onlineVersion') }}<el-tag>V{{ item.version }}</el-tag></span>
                </div>
                <p class="card-desc">{{ item.description }}</p>
                <div class="card-meta">
                  <span class="card-tag">{{ $t('service.moduleName') }}{{ item.moduleName }}</span>
                  <span class="card-tag">{{ $t('service.authGroup') }}{{ item.groupName }}</span>
                </div>
              </div>
            </el-card>
          </div>

          <!-- Pagination -->
          <div class="pagination-section">
            <el-pagination @size-change="handleSizeChange"
                           @current-change="handleCurrentChange"
                           :current-page="currentPage"
                           :page-sizes="[12, 24, 36, 48]"
                           :page-size="pageSize"
                           layout="total, sizes, prev, pager, next, jumper"
                           :total="totalCount"
                           class="custom-pagination">
            </el-pagination>
          </div>
        </el-main>
      </el-container>
    </el-container>
  </el-card>
</template>

<script>
export default {
  name: 'ServiceSearch',
  data () {
    return {
      searchKeyword: '',
      moduleItemList: [],
      selectedModuleList: [],
      currentPage: 1,
      pageSize: 12,
      totalCount: 0,
      allData: [],
    }
  },
  methods: {
    loadModules: function () {
      this.moduleItemList = [];
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/module/listAll",
        data: JSON.stringify({
          page: 1,
          size: 2147483647,
          searchText: null
        })
      }).then(
        res => {
          if (0 === res.data.code) {
            this.moduleItemList = res.data.data || [];
          } else {
            this.moduleItemList = [];
            if (res.data.message) {
              alert(this.$t('service.loadModuleListFailed') + res.data.message);
            }
          }
        }
      );
    },
    loadInterface: function () {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/search",
        data: window.JSON.stringify(
          {
            moduleIds: this.selectedModuleList,
            searchText: this.searchKeyword,
            page: this.currentPage,
            size: this.pageSize
          }
        )
      }).then(res => {
        if (0 === res.data.code) {
          this.currentPage = res.data.pagination.page;
          this.pageSize = res.data.pagination.size;
          this.totalCount = res.data.pagination.total;
          this.allData = res.data.data;
        } else {
          alert(this.$t('interface.loadFailed') + res.data.message);
        }
      }
      );
    },
    handleSearch () {
      this.currentPage = 1
      this.loadInterface();
    },
    handleSizeChange (val) {
      this.pageSize = val
      this.currentPage = 1
      this.loadInterface();
    },
    handleCurrentChange (val) {
      this.currentPage = val
      this.loadInterface();
    },
    handleGoDetail (row) {
      this.$router.push("/service/detail?id=" + row.id + "&commitId=" + row.commitId);
    }
  },
  created () {
    this.loadModules();
    this.loadInterface();
  }
}
</script>

<style scoped>
.el-card {
  width: 100%;
  height: 100%;
  overflow: auto;
}

.app-container {
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.app-header {
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  padding: 0 30px;
}

.header-content {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
}

.header-title {
  color: #333;
  font-size: 24px;
  font-weight: 600;
  margin: 0;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: 400px;
}

.search-input >>> .el-input__inner {
  border-radius: 20px;
  padding-left: 40px;
}

.search-btn {
  border-radius: 20px;
  padding: 12px 24px;
}

.app-aside {
  background: rgba(255, 255, 255, 0.9);
  border-right: 1px solid #e6e6e6;
  padding: 20px;
}

.filter-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.filter-title {
  color: #333;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
}

.checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-checkbox {
  display: block;
  padding: 8px 0;
}

.app-main {
  background: #f5f7fa;
  padding: 24px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.content-card {
  border-radius: 12px;
  transition: all 0.3s ease;
  cursor: pointer;
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.content-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.content-card:hover .card-img {
  transform: scale(1.05);
}

.card-content {
  padding: 16px;
}

.card-title {
  color: #333;
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px 0;
  line-height: 1.4;
}

.card-desc {
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}

.card-date {
  color: #999;
}

.card-tag {
  color: #409eff;
  background: rgba(64, 158, 255, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
}

.pagination-section {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

.custom-pagination >>> .el-pager li {
  border-radius: 8px;
  margin: 0 4px;
}

.custom-pagination >>> .el-pager li.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}
</style>
