<template>
  <div>
    <el-card>
      <div class="group-list-top">
        <div class="left-search-input-group">
          <div class="left-search-input">
            <el-input :placeholder="$t('module.searchPlaceholder')"
                      size="mini"
                      v-model="searchText"
                      :clearable=true
                      style="width:300px"
                      @change="searchByKeyword">
            </el-input>
          </div>
        </div>
        <div class="right-add-button-group">
          <el-button type="primary"
                     size="mini"
                     icon="el-icon-document-add"
                     @click="addGroup">{{ $t('mcp.add') }}</el-button>
        </div>
      </div>

      <el-table :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                :data="tableData"
                size="small"
                border>
        <el-table-column prop="id"
                         :label="$t('mcp.id')"
                         min-width="5%"></el-table-column>
        <el-table-column prop="name"
                         :label="$t('mcp.name')"
                         show-overflow-tooltip
                         min-width="20%"></el-table-column>
        <el-table-column prop="createTime"
                         :label="$t('mcp.createTime')"
                         min-width="20%"></el-table-column>
        <el-table-column prop="updateTime"
                         :label="$t('mcp.updateTime')"
                         show-overflow-tooltip
                         min-width="20%"></el-table-column>
        <el-table-column :label="$t('mcp.operation')"
                         min-width="35%">
          <template slot-scope="scope">
            <el-button-group>
              <el-button size="small"
                         type="danger"
                         icon="el-icon-document"
                         @click="handleShowToken(scope.$index, scope.row)"
                         round>{{ $t('mcp.view') }}</el-button>
              <el-button size="small"
                         type="warning"
                         icon="el-icon-edit"
                         @click="handleUpdate(scope.$index, scope.row)"
                         round>{{ $t('mcp.edit') }}</el-button>
              <el-button size="small"
                         type="success"
                         icon="el-icon-delete"
                         @click="handleDelete(scope.$index, scope.row)"
                         round>{{ $t('mcp.delete') }}</el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
      <div class="page"
           align="right">
        <el-pagination @size-change="handleSizeChange"
                       @current-change="handleCurrentChange"
                       :current-page="currentPageNum"
                       :page-sizes="[5, 10, 20, 40]"
                       :page-size="currentPageSize"
                       layout="total, sizes, prev, pager, next, jumper"
                       :total="totalItemCount"></el-pagination>
      </div>

      <el-dialog :title="$t('mcp.view')"
                 :visible.sync="ShowTokenDialog">
        <el-form size="mini">
          <el-form-item :label="$t('mcp.mcpToken')"
                        label-width="100px"
                        style="width:100%">
            <el-input type="input"
                      style="width:60%"
                      :spellcheck="false"
                      id="tokenTextInput"
                      v-model="clientTokenValue"></el-input>
            <el-button @click="handleCopyTokenText">{{ $t('mcp.clickToCopy') }}</el-button>
          </el-form-item>
          <el-form-item :label="$t('mcp.sseAddress')"
                        label-width="100px"
                        style="width:100%">
            <el-tooltip effect="dark"
                        :content="$t('mcp.sseTip')"
                        placement="bottom">
              <i class='el-icon-question' />
            </el-tooltip>
            <el-input type="input"
                      style="width:80%"
                      :spellcheck="false"
                      id="sseAddressTextInput"
                      v-model="serverSseUrlAddress"></el-input>
            <el-button style="width:10%"
                       @click="handleCopySseAddressText">{{ $t('mcp.clickToCopy') }}</el-button>
          </el-form-item>
          <el-form-item :label="$t('mcp.streamAddress')"
                        label-width="100px"
                        style="width:100%">
            <el-tooltip effect="dark"
                        :content="$t('mcp.streamTip')"
                        placement="bottom">
              <i class='el-icon-question' />
            </el-tooltip>
            <el-input type="input"
                      style="width:80%"
                      :spellcheck="false"
                      id="streamAddressTextInput"
                      v-model="serverStreamUrlAddress"></el-input>
            <el-button style="width:10%"
                       @click="handleCopyStreamAddressText">{{ $t('mcp.clickToCopy') }}</el-button>
          </el-form-item>
        </el-form>
        <span slot="footer">
          <el-button @click="ShowTokenDialog = false">{{ $t('common.cancel') }}</el-button>
        </span>
      </el-dialog>

      <el-dialog :title="$t('setting.addInfo')"
                 :visible.sync="createFormVisible"
                 :showClose="false"
                 :before-close="handleClose">
        <el-form :model="createform"
                 size="mini"
                 status-icon
                 :rules="rules"
                 ref="createform">
          <el-form-item :label="$t('mcp.name')"
                        label-width="120px"
                        :required=true
                        prop="name"
                        style="width:85%">
            <el-input v-model="createform.name"
                      auto-complete="off"></el-input>
          </el-form-item>
        </el-form>
        <div slot="footer"
             class="dialog-footer">
          <el-button @click="createFormVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary"
                     @click="handleCreate">{{ $t('common.confirm') }}</el-button>
        </div>
      </el-dialog>

      <el-dialog :title="$t('setting.updateInfo')"
                 :visible.sync="updateFormVisible"
                 :showClose="false"
                 :before-close="handleClose">
        <el-form :model="updateform"
                 size="mini"
                 status-icon
                 :rules="rules"
                 ref="updateform">
          <el-form-item :label="$t('mcp.name')"
                        label-width="120px"
                        :required=true
                        prop="name"
                        style="width:85%">
            <el-input v-model="updateform.name"
                      auto-complete="off"></el-input>
          </el-form-item>
        </el-form>
        <div slot="footer"
             class="dialog-footer">
          <el-button @click="updateFormVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary"
                     @click="handleSave">{{ $t('common.confirm') }}</el-button>
        </div>
      </el-dialog>
    </el-card>
  </div>
</template>

<script>
import qs from "qs";

export default {
  name: "group",
  components: {
  },
  data () {
    return {
      loading: true,
      lists: [],
      tableData: [
      ],
      currentPageNum: 1,
      currentPageSize: 10,
      totalItemCount: 0,
      searchText: '',
      ShowTokenDialog: false,
      clientTokenValue: '',
      serverSseUrlAddress: '',
      serverStreamUrlAddress: '',
      createform: {
        name: "",
      },
      updateform: {
        id: 0,
        name: "",
      },
      rules: {
        name: [
          {
            required: true,
            message: this.$t('mcp.nameRequired'),
            trigger: "blur"
          }
        ]
      },
      createFormVisible: false,
      updateFormVisible: false
    }
  },
  methods: {
    loadData: function () {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1//mcp/client/listAll",
        data: JSON.stringify({
          page: this.currentPageNum,
          size: this.currentPageSize,
          searchText: this.searchText
        })
      }).then(res => {
        if (0 === res.data.code) {
          this.totalItemCount = res.data.pagination.total
          this.tableData = res.data.data;
        } else {
          alert(this.$t('mcp.loadFailed') + res.data.message);
        }
      }
      );
    },
    loadManagerAddress: function (token) {
      this.$http({
        method: "GET",
        url: "/datapoly/manager/api/v1/mcp/client/endpoint"
      }).then(
        res => {
          this.serverSseUrlAddress = '';
          this.serverStreamUrlAddress = '';
          if (0 === res.data.code) {
            this.serverSseUrlAddress = res.data.data.sseAddrPrefix + token;
            this.serverStreamUrlAddress = res.data.data.streamAddrPrefix + token;
          } else {
            if (res.data.message) {
              alert(this.$t('mcp.loadFailed') + res.data.message);
            }
          }
        }
      );
    },
    handleClose (done) {
    },
    handleDelete: function (index, row) {
      this.$confirm(
        this.$t('mcp.confirmDeleteClient') + row.id + this.$t('common2.toVersion'),
        this.$t('common.warning'),
        {
          confirmButtonText: this.$t('common.confirm'),
          cancelButtonText: this.$t('common.cancel'),
          type: "warning"
        }
      ).then(() => {
        this.$http.delete(
          "/datapoly/manager/api/v1/mcp/client/delete/" + row.id
        ).then(res => {
          if (0 === res.data.code) {
            this.loadData();
          } else {
            alert(this.$t('mcp.deleteFailed') + res.data.message);
          }
        });
      });
    },
    addGroup: function () {
      this.createFormVisible = true;
      this.createform = {};
    },
    handleCreate: function () {
      this.$refs['createform'].validate(valid => {
        if (valid) {
          this.$http({
            method: "POST",
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded'
            },
            url: "/datapoly/manager/api/v1/mcp/client/create",
            data: qs.stringify({
              name: this.createform.name
            })
          }).then(res => {
            if (0 === res.data.code) {
              this.createFormVisible = false;
              this.$message(this.$t('mcp.addSuccess'));
              this.createform = {};
              this.loadData();
            } else {
              alert(this.$t('mcp.addFailedClient') + res.data.message);
            }
          });
        } else {
          alert(this.$t('common.checkInput'));
        }
      });
    },
    handleShowToken: function (index, row) {
      this.loadManagerAddress(row.token);
      this.clientTokenValue = row.token;
      this.ShowTokenDialog = true
    },
    handleCopyTokenText: function () {
      document.getElementById("tokenTextInput").select()
      document.execCommand("copy")
      this.$message.success(this.$t('mcp.tokenCopied'))
    },
    handleCopySseAddressText: function () {
      document.getElementById("sseAddressTextInput").select()
      document.execCommand("copy")
      this.$message.success(this.$t('mcp.sseCopied'))
    },
    handleCopyStreamAddressText: function () {
      document.getElementById("streamAddressTextInput").select()
      document.execCommand("copy")
      this.$message.success(this.$t('mcp.streamCopied'))
    },
    handleUpdate: function (index, row) {
      this.updateform = JSON.parse(JSON.stringify(row));
      this.updateFormVisible = true;
    },
    handleSave: function () {
      this.$refs['updateform'].validate(valid => {
        if (valid) {
          this.$http({
            method: "POST",
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded'
            },
            url: "/datapoly/manager/api/v1/mcp/client/update/" + this.updateform.id,
            data: qs.stringify({
              name: this.updateform.name,
            })
          }).then(res => {
            if (0 === res.data.code) {
              this.updateFormVisible = false;
              this.$message(this.$t('mcp.updateSuccess'));
              this.loadData();
              this.updateform = {};
            } else {
              alert(this.$t('message.operationFailed') + res.data.message);
            }
          });
        } else {
          alert(this.$t('common.checkInput'));
        }
      });
    },
    handleSizeChange: function (pageSize) {
      this.currentPageSize = pageSize;
      this.loadData();
    },
    handleCurrentChange: function (currentPage) {
      this.currentPageNum = currentPage;
      this.loadData();
    },
    searchByKeyword: function () {
      this.currentPage = 1;
      this.loadData();
    },
  },
  mounted () {
    this.loadData();
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
.group-list-top {
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
  width: 300px;
  margin-right: auto;
  margin: 10px 5px;
}
.right-add-button-group {
  width: 100px;
  margin-right: 5px;
  margin: 10px 5px;
}
</style>
