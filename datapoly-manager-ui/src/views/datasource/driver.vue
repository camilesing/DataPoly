<template>
  <div>
    <el-card>
      <div class="container">
        <el-card class="box-card">
          <div slot="header"
               align="center"
               class="clearfix">
            <span><b>{{ $t('common2.driverVersionNumber') }}</b></span>
          </div>
          <div class="navsBox">
            <ul>
              <li v-for="(item,index) in connectionTypes"
                  :key="index"
                  @click="handleChooseClick(item.type,index)"
                  :class="{active:index==isActive}">
                  <databaseIcon :type="item.type"></databaseIcon>
                  [{{item.id}}]{{item.type}}</li>
            </ul>
          </div>
        </el-card>

        <div class="contentBox">
          <div align="right"
               style="margin:10px 5px;"
               width="95%">
            <el-button type="primary"
                       size="mini"
                       icon="el-icon-document-add"
                       @click="dialogVisible=true">{{ $t('common2.addDriverJar') }}</el-button>
          </div>
          <el-table :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                    :data="versionDrivers"
                    size="small"
                    stripe
                    border>
            <template slot="empty">
              <span>{{ $t('common2.clickSelectType') }}</span>
            </template>
            <el-table-column property="driverVersion"
                             :label="$t('common2.driverVersionNumber')"
                             min-width="20%"></el-table-column>
            <el-table-column property="driverClass"
                             :label="$t('common2.driverClass')"
                             min-width="40%"></el-table-column>
            <el-table-column property="jarFiles"
                             :formatter="formatJarFileList"
                             :label="$t('common2.driverJarName')"
                             min-width="40%"></el-table-column>
          </el-table>
        </div>
      </div>
    </el-card>
    <el-dialog :title="$t('common2.addDriverJar')"
               :visible.sync="dialogVisible"
               width="40%"
               :before-close="handleClose">
      <span>{{ $t('common2.driverPathTip') }}</span>
      <span></span>
      <span>{{ $t('common2.driverDependTip') }}</span>
      <span slot="footer"
            class="dialog-footer">
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary"
                   @click="dialogVisible = false">{{ $t('common.confirm') }}</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import databaseIcon from "@/components/databaseIcon/databaseIcon";
export default {
  data () {
    return {
      dialogVisible: false,
      loading: true,
      connectionTypes: [],
      versionDrivers: [],
      isActive: -1,
    };
  },
  components: {
    databaseIcon
  },
  methods: {
    loadConnectionTypes: function () {
      this.$http({
        method: "GET",
        url: "/datapoly/manager/api/v1/datasource/types"
      }).then(res => {
        if (0 === res.data.code) {
          this.connectionTypes = res.data.data;
          this.handleChooseClick('MYSQL', 0);
        } else {
          if (res.data.message) {
            alert(this.$t('common2.initDriverFailed') + res.data.message);
          }
        }
      }
      );
    },
    handleChooseClick: function (type, index) {
      this.isActive = index;
      this.$http.get(
        "/datapoly/manager/api/v1/datasource/" + type + "/drivers"
      ).then(res => {
        if (0 === res.data.code) {
          this.versionDrivers = res.data.data;
        } else {
          if (res.data.message) {
            alert(this.$t('common2.queryDriverFailed') + res.data.message);
          }
        }
      });
    },
    handleClose (done) {
      this.$confirm(this.$t('common2.confirmClose'))
        .then(_ => {
          done();
        })
        .catch(_ => { });
    },
    formatJarFileList: function (row, column) {
      let jarFiles = row[column.property];
      return jarFiles.join(';\n');
    }
  },
  created () {
    this.loadConnectionTypes();
  },
  beforeDestroy () {
  },
};
</script>

<style scoped>
.el-card,
.el-message {
  width: 100%;
  height: 100%;
  overflow: auto;
}

.el-table {
  width: 100%;
  border-collapse: collapse;
}

.demo-table-expand {
  font-size: 0;
}

.demo-table-expand label {
  width: 90px;
  color: #99a9bf;
}

.demo-table-expand .el-form-item {
  margin-right: 0;
  margin-bottom: 0;
  width: 50%;
}

.filter {
  margin: 10px;
}

.container {
  display: flex;
  height: 100%;
}

.container > * {
  float: left; /* lay out horizontally */
}

.container .el-card {
  width: 35%;
  height: 100%;
  overflow: auto;
}

.container .el-card__header {
  padding: 8px 10px;
  border-bottom: 1px solid #ebeef5;
  box-sizing: border-box;
}

.container .navsBox ul {
  margin: 0;
  padding-left: 10px;
}

.container .navsBox ul li {
  list-style: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrop;
  cursor: pointer; /* pointer on hover */
  padding: 10px 0;
  border-bottom: 1px solid #e0e0e0;
  width: 100%;
}

.container .navsBox .active {
  background: #bcbcbe6e;
  color: rgb(46, 28, 88);
}

.container .contentBox {
  padding: 10px;
  width: calc(100% - 250px);
}
</style>
