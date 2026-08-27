<template>
  <div>
    <el-card>
      <div class="client-list-top">
        <div class="left-search-input-group">
          <div class="left-search-input">
            <el-select v-model="groupId"
                       size="mini"
                       @change="searchByKeyword"
                       :clearable="true"
                       style="width:200px"
                       :placeholder="$t('setting.selectAuthGroup')">
              <el-option v-for="(item,index) in groups"
                         :key="index"
                         :label="item.name"
                         :value="item.id"></el-option>
            </el-select>
            <el-input :placeholder="$t('setting.searchPlaceholder')"
                      size="mini"
                      v-model="keyword"
                      @change="searchByKeyword"
                      :clearable=true
                      style="width:300px">
            </el-input>
          </div>
        </div>
        <div class="right-add-button-group">
          <el-button type="primary"
                     size="mini"
                     icon="el-icon-document-add"
                     @click="addClient">{{ $t('setting.add') }}</el-button>
        </div>
      </div>

      <el-table :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                :data="tableData"
                size="small"
                border>
        <el-table-column prop="id"
                         :label="$t('setting.id')"
                         min-width="5%"></el-table-column>
        <el-table-column prop="name"
                         :label="$t('setting.appName')"
                         show-overflow-tooltip
                         min-width="20%"></el-table-column>
        <el-table-column prop="description"
                         :label="$t('setting.description')"
                         show-overflow-tooltip
                         min-width="10%"></el-table-column>
        <el-table-column prop="appKey"
                         :label="$t('setting.appAccount')"
                         show-overflow-tooltip
                         min-width="12%"></el-table-column>
        <el-table-column prop="expireDuration"
                         :label="$t('setting.expireDuration')"
                         :formatter="stringFormatExpireDuration"
                         show-overflow-tooltip
                         min-width="18%"></el-table-column>
        <el-table-column prop="isExpired"
                         :label="$t('setting.isExpired')"
                         show-overflow-tooltip
                         min-width="10%">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.isExpired"
                    type="danger"
                    effect="dark"
                    size="mini">{{ $t('setting.expired') }}
            </el-tag>
            <el-tag v-if="!scope.row.isExpired"
                    type="primary"
                    effect="dark"
                    size="mini">{{ $t('setting.notExpired') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tokenAlive"
                         :label="$t('setting.tokenLife')"
                         :formatter="stringFormatTokenAlive"
                         show-overflow-tooltip
                         min-width="18%"></el-table-column>
        <el-table-column prop="createTime"
                         :label="$t('setting.createTime')"
                         min-width="18%">
        </el-table-column>
        <el-table-column :label="$t('setting.operation')"
                         min-width="40%">
          <template slot-scope="scope">
            <el-button-group>
              <el-button size="small"
                         type="danger"
                         icon="el-icon-document"
                         @click="handleAuthorize(scope.$index, scope.row)"
                         round>{{ $t('setting.authorize') }}</el-button>
              <el-button size="small"
                         type="warning"
                         icon="el-icon-document"
                         @click="handleShowSecret(scope.$index, scope.row)"
                         round>{{ $t('setting.secret') }}</el-button>
              <el-button size="small"
                         type="success"
                         icon="el-icon-delete"
                         @click="handleDelete(scope.$index, scope.row)"
                         round>{{ $t('setting.delete') }}</el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
      <div class="page"
           align="right">
        <el-pagination @size-change="handleSizeChange"
                       @current-change="handleCurrentChange"
                       :current-page="currentPage"
                       :page-sizes="[5, 10, 20, 40]"
                       :page-size="pageSize"
                       layout="total, sizes, prev, pager, next, jumper"
                       :total="totalCount"></el-pagination>
      </div>

      <el-dialog :title="$t('setting.addAppInfo')"
                 :visible.sync="createFormVisible"
                 :showClose="false"
                 :before-close="handleClose">
        <el-form :model="createform"
                 size="mini"
                 status-icon
                 :rules="rules"
                 ref="createform">
          <el-form-item :label="$t('setting.appName')"
                        label-width="120px"
                        :required=true
                        prop="name"
                        style="width:85%">
            <el-input v-model="createform.name"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item :label="$t('setting.description')"
                        label-width="120px"
                        prop="description"
                        style="width:85%">
            <el-input type="textarea"
                      :rows="6"
                      :spellcheck="false"
                      :placeholder="$t('setting.description')"
                      v-model="createform.description"
                      auto-complete="off">
            </el-input>
          </el-form-item>
          <el-form-item :label="$t('setting.appAccount')"
                        label-width="120px"
                        prop="appKey"
                        style="width:85%">
            <el-input v-model="createform.appKey"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item :label="$t('setting.expireDuration')"
                        label-width="120px"
                        prop="expireTime"
                        style="width:85%">
            <el-select v-model="createform.expireTime">
              <el-option :label="$t('setting.neverExpire')"
                         value="EXPIRE_FOR_EVER"></el-option>
              <el-option :label="$t('setting.onceExpire')"
                         value="EXPIRE_ONLY_ONCE"></el-option>
              <el-option :label="'5' + $t('common.minutes')"
                         value="EXPIRE_05_MIN"></el-option>
              <el-option :label="'30' + $t('common.minutes')"
                         value="EXPIRE_30_MIN"></el-option>
              <el-option :label="'1' + $t('common.hour')"
                         value="EXPIRE_01_HOUR"></el-option>
              <el-option :label="'12' + $t('common.hours')"
                         value="EXPIRE_12_HOUR"></el-option>
              <el-option :label="'1' + $t('common.day')"
                         value="EXPIRE_01_DAY"></el-option>
              <el-option :label="'15' + $t('common.days')"
                         value="EXPIRE_15_DAY"></el-option>
              <el-option :label="'1' + $t('common.month')"
                         value="EXPIRE_01_MOUTH"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label-width="120px"
                        prop="tokenAlive"
                        style="width:35%">
            <span slot="label"
                  style="display:inline-block;">
              {{ $t('setting.tokenLife') }}
              <el-tooltip effect="dark"
                          :content="$t('mcp.toolDescTip')"
                          placement="bottom">
                <i class='el-icon-question' />
              </el-tooltip>
            </span>
            <el-select v-model="createform.tokenAlive">
              <el-option :label="$t('setting.shortTerm')"
                         value="PERIOD"></el-option>
              <el-option :label="$t('setting.longTerm')"
                         value="LONGEVITY"></el-option>
            </el-select>
          </el-form-item>
        </el-form>
        <div slot="footer"
             class="dialog-footer">
          <el-button @click="createFormVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary"
                     @click="handleCreate">{{ $t('common.confirm') }}</el-button>
        </div>
      </el-dialog>

      <el-dialog :title="$t('setting.viewSecret')"
                 :visible.sync="ShowSecretDialog">
        <el-input type="input"
                  style="width:55%"
                  id="secretTextInput"
                  v-model="clientSecret"></el-input>
        <el-button @click="handleCopyText">{{ $t('setting.clickToCopy') }}</el-button>
        <span slot="footer">
          <el-button @click="ShowSecretDialog = false">{{ $t('common.cancel') }}</el-button>
        </span>
      </el-dialog>

      <el-dialog :title="$t('setting.authGroup')"
                 :visible.sync="showAuthDialog"
                 @open="loadAllGroups">
        <el-checkbox-group v-model="selectList">
          <el-checkbox v-for="item in groups"
                       :label="item.id"
                       :key="item.id">{{ item.name }}</el-checkbox>
        </el-checkbox-group>
        <span slot="footer">
          <el-button @click="showAuthDialog = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary"
                     @click="handleSaveAuth()">{{ $t('setting.saveAuth') }}</el-button>
        </span>
      </el-dialog>

    </el-card>
  </div>
</template>

<script>

export default {
  name: "client",
  components: {
  },
  data () {
    return {
      loading: true,
      groupId: null,
      keyword: null,
      lists: [],
      currentPage: 1,
      pageSize: 10,
      totalCount: 0,
      tableData: [
      ],
      groups: [],
      clientId: 0,
      selectList: [],
      showAuthDialog: false,
      ShowSecretDialog: false,
      clientSecret: '',
      createform: {
        name: "",
        description: "",
        appKey: "",
        expireTime: "",
        tokenAlive: "",
      },
      rules: {
        name: [
          {
            required: true,
            message: this.$t('setting.nameRequired'),
            trigger: "blur"
          }
        ],
        appKey: [
          {
            required: true,
            message: this.$t('setting.accountRequired'),
            trigger: "blur"
          }
        ],
        expireTime: [
          {
            required: true,
            message: this.$t('setting.expireRequired'),
            trigger: "change"
          }
        ],
        tokenAlive: [
          {
            required: true,
            message: this.$t('setting.tokenRequired'),
            trigger: "change"
          }
        ]
      },
      createFormVisible: false
    }
  },
  methods: {
    loadData: function () {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/client/list",
        data: JSON.stringify({
          groupId: this.groupId,
          searchText: this.keyword,
          page: this.currentPage,
          size: this.pageSize
        })
      }).then(res => {
        if (0 === res.data.code) {
          this.currentPage = res.data.pagination.page;
          this.pageSize = res.data.pagination.size;
          this.totalCount = res.data.pagination.total;
          this.tableData = res.data.data;
        } else {
          alert(this.$t('setting.loadFailed') + res.data.message);
        }
      });
    },
    loadAllGroups: function () {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/group/listAll",
        data: JSON.stringify({
          page: 1,
          size: 2147483647,
          searchText: null
        })
      }).then((res) => {
        if (0 === res.data.code) {
          this.groups = res.data.data
        } else {
          alert(this.$t('setting.loadFailed') + res.data.message);
        }
      }).catch((error) => {
      })
    },
    stringFormatExpireDuration (row, column) {
      if (row.expireDuration === "FOR_EVER") {
        return this.$t('setting.neverExpire');
      } else if (row.expireDuration === "ONLY_ONCE") {
        return this.$t('setting.onceExpire');
      } else if (row.expireDuration === "TIME_VALUE") {
        return row.expireAtStr;
      }
      return "-";
    },
    stringFormatTokenAlive (row, column) {
      if (row.tokenAlive === "LONGEVITY") {
        return this.$t('setting.longTerm');
      } else if (row.tokenAlive === "PERIOD") {
        return this.$t('setting.shortTerm');
      }
      return "-";
    },
    searchByKeyword: function () {
      this.currentPage = 1;
      this.loadData();
    },
    handleClose (done) {
    },
    handleDelete: function (index, row) {
      this.$confirm(
        this.$t('setting.confirmDelete') + row.id + this.$t('common2.toVersion'),
        this.$t('common.warning'),
        {
          confirmButtonText: this.$t('common.confirm'),
          cancelButtonText: this.$t('common.cancel'),
          type: "warning"
        }
      ).then(() => {
        this.$http.delete(
          "/datapoly/manager/api/v1/client/delete/" + row.id
        ).then(res => {
          if (0 === res.data.code) {
            this.loadData();
          } else {
            alert(this.$t('setting.deleteFailed') + res.data.message);
          }
        });
      });
    },
    addClient: function () {
      this.createFormVisible = true;
      this.createform = {};
    },
    handleCreate: function () {
      this.$refs['createform'].validate(valid => {
        if (valid) {
          this.$http({
            method: "POST",
            headers: {
              'Content-Type': 'application/json'
            },
            url: "/datapoly/manager/api/v1/client/create",
            data: JSON.stringify({
              name: this.createform.name,
              description: this.createform.description,
              appKey: this.createform.appKey,
              expireTime: this.createform.expireTime,
              tokenAlive: this.createform.tokenAlive,
            })
          }).then(res => {
            if (0 === res.data.code) {
              this.createFormVisible = false;
              this.$message(this.$t('setting.addSuccess'));
              this.createform = {};
              this.loadData();
            } else {
              alert(this.$t('setting.addFailed') + res.data.message);
            }
          });
        } else {
          alert(this.$t('common.checkInput'));
        }
      });
    },
    handleSizeChange: function (pageSize) {
      this.loading = true;
      this.pageSize = pageSize;
      this.loadData();
    },
    handleCurrentChange: function (currentPage) {
      this.loading = true;
      this.currentPage = currentPage;
      this.loadData();
    },
    handleShowSecret: function (index, row) {
      this.ShowSecretDialog = true
      this.$http.get("/datapoly/manager/api/v1/client/secret/" + row.id)
        .then((res) => {
          if (0 === res.data.code) {
            this.clientSecret = res.data.data
          } else {
            alert(this.$t('message.operationFailed') + res.data.message)
          }
        })
    },
    handleCopyText: function () {
      var d = document.getElementById("secretTextInput")
      d.select()
      document.execCommand("copy")
      this.$message.success(this.$t('setting.copySuccess'))
    },
    handleAuthorize: function (index, row) {
      this.showAuthDialog = true
      this.$http.get("/datapoly/manager/api/v1/client/auth/" + row.id)
        .then((res) => {
          this.selectList = []
          this.clientId = row.id
          for (let item of res.data) {
            this.selectList.push(item.id)
          }
        })
    },
    handleSaveAuth: function () {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/client/auth/create",
        data: JSON.stringify({
          id: this.clientId,
          groupIds: this.selectList
        })
      }).then(res => {
        if (0 === res.data.code) {
          this.showAuthDialog = false
        } else {
          alert(this.$t('message.operationFailed') + res.data.message);
        }
      });
    }
  },
  created () {
    this.loadData();
    this.loadAllGroups();
  }
};
</script>

<style scoped>
.el-table {
  width: 100%;
  height: 100%;
}
.el-card,
.el-message {
  width: 100%;
  height: 100%;
  overflow: auto;
}
.client-list-top {
  width: 100%;
  display: flex;
  justify-content: space-between;
}

.left-search-input-group {
  width: calc(100% - 100px);
  margin-right: auto;
  display: flex;
  justify-content: space-between;
}
.left-search-input {
  width: 80%;
  margin-right: auto;
  margin: 10px 5px;
}
.right-add-button-group {
  width: 100px;
  margin-left: auto;
  margin: 10px 5px;
}
</style>
