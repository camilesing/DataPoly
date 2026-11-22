<template>
  <div>
    <el-card>
      <div class="manage-top">
        <div class="left-search-input">
          <el-input :placeholder="$t('userManage.searchPlaceholder')"
                    size="mini"
                    v-model="keyword"
                    @change="searchByKeyword"
                    :clearable="true"
                    style="width:300px">
          </el-input>
        </div>
        <div class="right-add-button-group">
          <el-button type="primary"
                     size="mini"
                     icon="el-icon-document-add"
                     @click="addUser">{{ $t('userManage.add') }}</el-button>
        </div>
      </div>

      <el-table :header-cell-style="{background:'var(--dp-bg-inset)',color:'var(--dp-text-secondary)'}"
                :data="tableData"
                size="small"
                border>
        <el-table-column prop="id"
                         :label="$t('setting.id')"
                         min-width="6%"></el-table-column>
        <el-table-column prop="username"
                         :label="$t('userManage.username')"
                         show-overflow-tooltip
                         min-width="14%"></el-table-column>
        <el-table-column prop="realName"
                         :label="$t('userManage.realName')"
                         show-overflow-tooltip
                         min-width="14%"></el-table-column>
        <el-table-column prop="email"
                         :label="$t('userManage.email')"
                         show-overflow-tooltip
                         min-width="16%"></el-table-column>
        <el-table-column prop="role"
                         :label="$t('userManage.role')"
                         min-width="10%">
          <template slot-scope="scope">
            <el-tag :type="scope.row.role === 'ADMIN' ? 'danger' : 'info'"
                    effect="dark"
                    size="mini">{{ scope.row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="locked"
                         :label="$t('userManage.locked')"
                         min-width="10%">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.locked"
                    type="warning"
                    effect="dark"
                    size="mini">{{ $t('userManage.lockedYes') }}
            </el-tag>
            <el-tag v-else
                    type="success"
                    effect="dark"
                    size="mini">{{ $t('userManage.lockedNo') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime"
                         :label="$t('setting.createTime')"
                         min-width="16%"></el-table-column>
        <el-table-column :label="$t('setting.operation')"
                         width="300">
          <template slot-scope="scope">
            <el-button-group>
              <el-button size="small"
                         type="primary"
                         icon="el-icon-edit"
                         @click="handleUpdate(scope.$index, scope.row)"
                         round>{{ $t('common.edit') }}</el-button>
              <el-button size="small"
                         type="success"
                         icon="el-icon-key"
                         @click="handleResetPassword(scope.row)"
                         round>{{ $t('userManage.resetPassword') }}</el-button>
              <el-button size="small"
                         type="warning"
                         icon="el-icon-s-check"
                         @click="handlePermissions(scope.row)"
                         round>{{ $t('userManage.permissions') }}</el-button>
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

      <!-- 创建 / 编辑 -->
      <el-dialog :title="editing ? $t('userManage.editUser') : $t('userManage.addUser')"
                 :visible.sync="formVisible"
                 :showClose="false"
                 :before-close="() => {}">
        <el-form :model="form"
                 size="mini"
                 status-icon
                 :rules="formRules"
                 ref="userForm">
          <el-form-item :label="$t('userManage.username')"
                        label-width="120px"
                        :required="true"
                        prop="username"
                        style="width:85%">
            <el-input v-model="form.username"
                      :disabled="editing"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item v-if="!editing"
                        :label="$t('userManage.password')"
                        label-width="120px"
                        :required="true"
                        prop="password"
                        style="width:85%">
            <el-input v-model="form.password"
                      type="password"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item :label="$t('userManage.realName')"
                        label-width="120px"
                        prop="realName"
                        style="width:85%">
            <el-input v-model="form.realName"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item :label="$t('userManage.email')"
                        label-width="120px"
                        prop="email"
                        style="width:85%">
            <el-input v-model="form.email"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item :label="$t('userManage.role')"
                        label-width="120px"
                        :required="true"
                        prop="role"
                        style="width:85%">
            <el-select v-model="form.role">
              <el-option label="USER" value="USER"></el-option>
              <el-option label="ADMIN" value="ADMIN"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item v-if="editing"
                        :label="$t('userManage.locked')"
                        label-width="120px"
                        style="width:85%">
            <el-switch v-model="form.locked"></el-switch>
          </el-form-item>
        </el-form>
        <div slot="footer"
             class="dialog-footer">
          <el-button @click="formVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary"
                     @click="handleSave">{{ $t('common.confirm') }}</el-button>
        </div>
      </el-dialog>

      <!-- 重置口令 -->
      <el-dialog :title="$t('userManage.resetPasswordFor') + resetTarget"
                 :visible.sync="resetVisible">
        <el-form size="mini"
                 status-icon
                 :rules="resetRules"
                 ref="resetForm">
          <el-form-item :label="$t('userManage.newPassword')"
                        label-width="120px"
                        :required="true"
                        prop="newPassword"
                        style="width:85%">
            <el-input v-model="resetPassword.newPassword"
                      type="password"
                      auto-complete="off"></el-input>
          </el-form-item>
        </el-form>
        <div slot="footer"
             class="dialog-footer">
          <el-button @click="resetVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary"
                     @click="handleSaveReset">{{ $t('common.confirm') }}</el-button>
        </div>
      </el-dialog>

      <!-- 权限矩阵：只对 USER 有意义（ADMIN 全通过，后端不读配置行） -->
      <el-dialog :title="$t('userManage.permissionsFor') + permissionTarget"
                 :visible.sync="permissionVisible"
                 width="720px">
        <div v-if="permissionTargetRole === 'ADMIN'"
             style="margin-bottom:10px;">
          <el-alert :title="$t('userManage.adminBypassNote')"
                    type="info"
                    :closable="false"></el-alert>
        </div>
        <el-table :data="permissionRows"
                  size="mini"
                  border
                  max-height="420">
          <el-table-column :label="$t('userManage.module')"
                           min-width="35%">
            <template slot-scope="scope">{{ $t('userManage.modules.' + scope.row.module) }}</template>
          </el-table-column>
          <el-table-column v-for="action in actions"
                           :key="action"
                           :label="action"
                           min-width="13%">
            <template slot-scope="scope">
              <el-checkbox v-model="scope.row.actions[action]"
                           :disabled="permissionTargetRole === 'ADMIN'"></el-checkbox>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top:8px;color:var(--dp-text-secondary);font-size:12px;">
          {{ $t('userManage.defaultNote') }}
        </div>
        <div slot="footer"
             class="dialog-footer">
          <el-button @click="permissionVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary"
                     @click="handleSavePermissions">{{ $t('common.confirm') }}</el-button>
        </div>
      </el-dialog>
    </el-card>
  </div>
</template>

<script>
// 管理员用户台：账号 CRUD、重置口令、按「模块×操作」配置 USER 的权限矩阵。
// 后端判据在 manager 的 PermissionService（无配置行=默认只读），这里只是它的编辑器。
const MODULES = ["DATASOURCE", "API", "API_GROUP", "API_MODULE", "APP_CLIENT", "DATA_TASK",
  "FIREWALL", "MCP_CLIENT", "MCP_TOOL", "NODE", "OVERVIEW", "SYSTEM_PARAM", "ALARM", "VERSION"];

export default {
  name: "userManage",
  data () {
    return {
      actions: ["R", "C", "U", "D", "X"],
      modules: MODULES,
      keyword: null,
      tableData: [],
      currentPage: 1,
      pageSize: 10,
      totalCount: 0,
      formVisible: false,
      editing: false,
      form: {},
      resetVisible: false,
      resetTarget: "",
      resetPassword: { newPassword: "" },
      permissionVisible: false,
      permissionTarget: "",
      permissionTargetRole: "USER",
      permissionRows: []
    };
  },
  computed: {
    formRules () {
      return {
        username: [
          { required: true, message: this.$t('userManage.usernameRequired'), trigger: "blur" }
        ],
        password: [
          { required: true, message: this.$t('userManage.passwordRequired'), trigger: "blur" },
          { min: 8, max: 64, message: this.$t('userManage.passwordLength'), trigger: "blur" }
        ],
        role: [
          { required: true, message: this.$t('userManage.roleRequired'), trigger: "change" }
        ]
      };
    },
    resetRules () {
      return {
        newPassword: [
          { required: true, message: this.$t('userManage.passwordRequired'), trigger: "blur" },
          { min: 8, max: 64, message: this.$t('userManage.passwordLength'), trigger: "blur" }
        ]
      };
    }
  },
  methods: {
    loadData () {
      this.$http({
        method: "POST",
        headers: { 'Content-Type': 'application/json' },
        url: "/datapoly/manager/api/v1/user/list",
        data: JSON.stringify({
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
    searchByKeyword () {
      this.currentPage = 1;
      this.loadData();
    },
    handleSizeChange (pageSize) {
      this.pageSize = pageSize;
      this.loadData();
    },
    handleCurrentChange (currentPage) {
      this.currentPage = currentPage;
      this.loadData();
    },
    addUser () {
      this.editing = false;
      this.form = { username: "", password: "", realName: "", email: "", role: "USER" };
      this.formVisible = true;
    },
    handleUpdate (index, row) {
      this.editing = true;
      this.form = {
        id: row.id,
        username: row.username,
        realName: row.realName,
        email: row.email,
        role: row.role,
        locked: !!row.locked
      };
      this.formVisible = true;
    },
    handleSave () {
      this.$refs['userForm'].validate(valid => {
        if (!valid) {
          return;
        }
        const url = this.editing
          ? "/datapoly/manager/api/v1/user/update"
          : "/datapoly/manager/api/v1/user/create";
        this.$http({
          method: "POST",
          headers: { 'Content-Type': 'application/json' },
          url: url,
          data: JSON.stringify(this.form)
        }).then(res => {
          if (0 === res.data.code) {
            this.formVisible = false;
            this.loadData();
          } else {
            alert(res.data.message);
          }
        });
      });
    },
    handleResetPassword (row) {
      this.resetTarget = row.username;
      this.resetPassword = { newPassword: "" };
      this.resetVisible = true;
    },
    handleSaveReset () {
      this.$refs['resetForm'].validate(valid => {
        if (!valid) {
          return;
        }
        this.$http({
          method: "POST",
          headers: { 'Content-Type': 'application/json' },
          url: "/datapoly/manager/api/v1/user/resetPassword",
          data: JSON.stringify({
            username: this.resetTarget,
            newPassword: this.resetPassword.newPassword
          })
        }).then(res => {
          if (0 === res.data.code) {
            this.resetVisible = false;
            this.$message(this.$t('userManage.resetDone'));
          } else {
            alert(res.data.message);
          }
        });
      });
    },
    handlePermissions (row) {
      this.permissionTarget = row.username;
      this.permissionTargetRole = row.role;
      this.$http.get("/datapoly/manager/api/v1/user/permission/list", {
        params: { username: row.username }
      }).then(res => {
        if (0 !== res.data.code) {
          alert(res.data.message);
          return;
        }
        const configured = {};
        (res.data.data || []).forEach(entry => {
          configured[entry.module] = entry.actions || [];
        });
        this.permissionRows = this.modules.map(module => {
          const actions = {};
          this.actions.forEach(action => {
            actions[action] = (configured[module] || []).indexOf(action) !== -1;
          });
          return { module: module, actions: actions };
        });
        this.permissionVisible = true;
      });
    },
    handleSavePermissions () {
      const items = [];
      this.permissionRows.forEach(row => {
        const granted = this.actions.filter(action => row.actions[action]);
        if (granted.length > 0) {
          items.push({ module: row.module, actions: granted });
        }
      });
      this.$http({
        method: "POST",
        headers: { 'Content-Type': 'application/json' },
        url: "/datapoly/manager/api/v1/user/permission/update",
        data: JSON.stringify({ username: this.permissionTarget, items: items })
      }).then(res => {
        if (0 === res.data.code) {
          this.permissionVisible = false;
          this.$message(this.$t('userManage.permissionSaved'));
        } else {
          alert(res.data.message);
        }
      });
    }
  },
  created () {
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
.manage-top {
  width: 100%;
  display: flex;
  justify-content: space-between;
}
.left-search-input {
  width: 60%;
  margin: 10px 5px;
}
.right-add-button-group {
  width: 100px;
  margin-left: auto;
  margin: 10px 5px;
}
</style>
