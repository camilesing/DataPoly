<template>
  <div>
    <el-card>
      <div class="group-list-top">
        <div class="left-search-input-group">
          <div class="left-search-input">
            <el-input :placeholder="$t('module.searchPlaceholder')"
                      size="mini"
                      v-model="searchText"
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
                     @click="addGroup">{{ $t('setting.addGroup') }}</el-button>
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
                         :label="$t('setting.groupName')"
                         show-overflow-tooltip
                         min-width="20%"></el-table-column>
        <el-table-column prop="createTime"
                         :label="$t('setting.createTime')"
                         min-width="20%"></el-table-column>
        <el-table-column prop="updateTime"
                         :label="$t('setting.updateTime')"
                         show-overflow-tooltip
                         min-width="20%"></el-table-column>
        <el-table-column :label="$t('setting.operation')"
                         min-width="35%">
          <template slot-scope="scope">
            <el-button-group>
              <el-button size="small"
                         type="danger"
                         icon="el-icon-document"
                         @click="handleRelation(scope.$index, scope.row)"
                         round>{{ $t('setting.relation') }}</el-button>
              <el-button size="small"
                         type="warning"
                         icon="el-icon-edit"
                         @click="handleUpdate(scope.$index, scope.row)"
                         round>{{ $t('setting.edit') }}</el-button>
              <el-button size="small"
                         type="success"
                         v-if="scope.row.id!==1"
                         icon="el-icon-delete"
                         @click="handleDelete(scope.$index, scope.row)"
                         round>{{ $t('setting.deleteBtn') }}</el-button>
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

      <el-dialog :title="$t('setting.addInfo')"
                 :visible.sync="createFormVisible"
                 :showClose="false"
                 :before-close="handleClose">
        <el-form :model="createform"
                 size="mini"
                 status-icon
                 :rules="rules"
                 ref="createform">
          <el-form-item :label="$t('setting.groupName')"
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
          <el-form-item :label="$t('setting.groupName')"
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

      <el-dialog :title="$t('setting.modifyRelation')"
                 :visible.sync="relationFormVisible"
                 :showClose="false"
                 :before-close="handleClose">
        <el-alert :title="$t('setting.relationTip')"
                  type="warning"
                  show-icon>
        </el-alert>
        <el-tree :data="moduleAssignments"
                 :show-checkbox="true"
                 ref="relationTree"
                 node-key="id"
                 :default-checked-keys="initCheckedKeys"
                 highlight-current
                 :props="defaultProps">
        </el-tree>
        <div slot="footer"
             class="dialog-footer">
          <el-button type="primary"
                     @click="relationFormVisible = false">{{ $t('common.close') }}</el-button>
          <el-button type="danger"
                     v-if="currentGroupId!==1"
                     @click="handleRelationSave">{{ $t('setting.modifyRelation') }}</el-button>
        </div>
      </el-dialog>

    </el-card>
  </div>
</template>

<script>
import qs from "qs";
import Vue from "vue";

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
      createform: {
        title: "",
      },
      updateform: {
        id: 0,
        title: "",
      },
      rules: {
        name: [
          {
            required: true,
            message: this.$t('setting.nameRequiredTip'),
            trigger: "blur"
          }
        ]
      },
      createFormVisible: false,
      updateFormVisible: false,
      relationFormVisible: false,
      moduleAssignments: [],
      initCheckedKeys: [],
      currentGroupId: 0,
      defaultProps: {
        children: 'children',
        label: 'name'
      }
    }
  },
  methods: {
    uuid: function () {
      var s = [];
      var hexDigits = "0123456789abcdef";
      for (var i = 0; i < 36; i++) {
        s[i] = hexDigits.substr(Math.floor(Math.random() * 0x10), 1);
      }
      s[14] = "4";
      s[19] = hexDigits.substr((s[19] & 0x3) | 0x8, 1);
      s[8] = s[13] = s[18] = s[23] = "-";

      var uuid = s.join("");
      return uuid;
    },
    loadData: function () {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/group/listAll",
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
          alert(this.$t('setting.loadFailed') + res.data.message);
        }
      }
      );
    },
    handleClose (done) {
    },
    handleDelete: function (index, row) {
      this.$confirm(
        this.$t('setting.confirmDeleteGroup') + row.id + this.$t('common2.toVersion'),
        this.$t('common.warning'),
        {
          confirmButtonText: this.$t('common.confirm'),
          cancelButtonText: this.$t('common.cancel'),
          type: "warning"
        }
      ).then(() => {
        this.$http.delete(
          "/datapoly/manager/api/v1/group/delete/" + row.id
        ).then(res => {
          if (0 === res.data.code) {
            this.loadData();
          } else {
            alert(this.$t('setting.deleteFailed') + res.data.message);
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
            url: "/datapoly/manager/api/v1/group/create",
            data: qs.stringify({
              name: this.createform.name
            })
          }).then(res => {
            if (0 === res.data.code) {
              this.createFormVisible = false;
              this.$message(this.$t('setting.addSuccessTip'));
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
    handleRelation: function (index, row) {
      this.moduleAssignments = [];
      this.$http.get(
        "/datapoly/manager/api/v1/module/moduleTree/" + row.id
      ).then(res => {
        if (0 === res.data.code) {
          this.initCheckedKeys = [];
          this.currentGroupId = row.id;
          this.moduleAssignments = res.data.data;
          for (let item of this.moduleAssignments) {
            Vue.set(item, 'id', this.uuid());
            if (item.children) {
              for (let one of item.children) {
                if (one.selected) {
                  this.initCheckedKeys.push(one.id);
                }
              }
            }
          }
        }
      });
      this.relationFormVisible = true;
    },
    handleRelationSave: function () {
      let checkedKeys = this.$refs.relationTree.getCheckedKeys(true);
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/group/" + this.currentGroupId,
        data: checkedKeys
      }).then(res => {
        if (0 === res.data.code) {
          this.relationFormVisible = false;
          this.$message(this.$t('setting.modifyRelationSuccess'));
        } else {
          alert(this.$t('setting.updateFailed') + res.data.message);
        }
      });
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
            url: "/datapoly/manager/api/v1/group/update/" + this.updateform.id,
            data: qs.stringify({
              name: this.updateform.name,
            })
          }).then(res => {
            if (0 === res.data.code) {
              this.updateFormVisible = false;
              this.$message(this.$t('setting.updateSuccessTip'));
              this.loadData();
              this.updateform = {};
            } else {
              alert(this.$t('setting.updateFailed') + res.data.message);
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
