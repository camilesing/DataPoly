<template>
  <div>
    <el-card>
      <div class="connection-list-top">
        <div class="left-search-input-group">
          <div class="left-search-input">
            <el-input :placeholder="$t('datasource.searchPlaceholder')"
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
                     @click="addConnection">{{ $t('common.add') }}</el-button>
        </div>
      </div>

      <el-dialog :title="$t('datasource.selectDbType')"
                 :visible.sync="dbTypeDialogVisible"
                 width="800px"
                 center>
        <div class="db-type-grid">
          <div v-for="item in databaseType"
               :key="item.type"
               class="db-type-item"
               :class="{'db-type-item-selected': selectedDbType === item.type}"
               @click="selectDbType(item.type)">
            <databaseIcon :type="item.type"></databaseIcon>
            <span>{{ item.type }}</span>
          </div>
        </div>
        <div slot="footer" class="dialog-footer">
          <el-button type="primary" :disabled="!selectedDbType" @click="goToForm">{{ $t('common.next') }}</el-button>
        </div>
      </el-dialog>

      <el-table :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                :data="tableData"
                size="small"
                border>
        <el-table-column prop="id"
                         :label="$t('datasource.id')"
                         min-width="5%"></el-table-column>
        <el-table-column prop="name"
                         :label="$t('datasource.name')"
                         show-overflow-tooltip
                         min-width="20%"></el-table-column>
        <el-table-column prop="createTime"
                         :label="$t('datasource.createTime')"
                         min-width="18%"></el-table-column>
        <el-table-column :label="$t('datasource.type')"
                         show-overflow-tooltip
                         min-width="15%">
          <template slot-scope="scope">
            <databaseIcon :type="scope.row.type"></databaseIcon>
            <span>{{ scope.row.type }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="url"
                         :label="$t('datasource.jdbcUrl')"
                         show-overflow-tooltip
                         min-width="15%"></el-table-column>
        <el-table-column prop="username"
                         :label="$t('datasource.username')"
                         show-overflow-tooltip
                         min-width="10%"></el-table-column>
        <el-table-column :label="$t('common.operation')"
                         min-width="35%">
          <template slot-scope="scope">
            <el-button-group>
              <el-button size="small"
                         type="danger"
                         icon="el-icon-video-play"
                         @click="handleTest(scope.$index, scope.row)"
                         round>{{ $t('datasource.test') }}</el-button>
              <el-button size="small"
                         type="primary"
                         icon="el-icon-document"
                         @click="handleMore(scope.$index, scope.row)"
                         round>{{ $t('datasource.detail') }}</el-button>
              <el-button size="small"
                         type="warning"
                         icon="el-icon-edit"
                         @click="handleUpdate(scope.$index, scope.row)"
                         round>{{ $t('common.edit') }}</el-button>
              <el-button size="small"
                         type="success"
                         icon="el-icon-delete"
                         @click="handleDelete(scope.$index, scope.row)"
                         round>{{ $t('common.delete') }}</el-button>
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

      <el-dialog :title="$t('datasource.detailTitle')"
                 :visible.sync="dialogFormVisible"
                 :showClose="false"
                 :before-close="handleClose">
        <el-form :model="queryForm"
                 size="mini">
          <el-form-item :label="$t('datasource.name')"
                        label-width="120px"
                        style="width:85%">
            <el-input v-model="queryForm.name"
                      auto-complete="off"
                      :readonly=true></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.type')"
                        label-width="120px"
                        style="width:85%">
            <el-input v-model="queryForm.type"
                      auto-complete="off"
                      :readonly=true></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.driver')"
                        label-width="120px"
                        style="width:85%">
            <el-input v-model="queryForm.driver"
                      auto-complete="off"
                      :readonly=true></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.driverVersion')"
                        label-width="120px"
                        style="width:85%">
            <el-input v-model="queryForm.version"
                      auto-complete="off"
                      :readonly=true></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.jdbcUrl')"
                        label-width="120px"
                        style="width:85%">
            <el-input type="textarea"
                      :rows="6"
                      :spellcheck="false"
                      v-model="queryForm.url"
                      auto-complete="off"
                      :readonly=true></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.username')"
                        label-width="120px"
                        style="width:85%">
            <el-input v-model="queryForm.username"
                      auto-complete="off"
                      :readonly=true></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.password')"
                        label-width="120px"
                        style="width:85%">
            <el-input type="password"
                      v-model="queryForm.password"
                      auto-complete="off"
                      :readonly=true></el-input>
          </el-form-item>
          <el-divider content-position="center">{{ $t('datasource.poolConfig') }}</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('datasource.maxPoolSize')" label-width="120px">
                <el-input v-model="queryForm.poolConfig.maximumPoolSize" :readonly=true :placeholder="$t('datasource.default') + ' 10'"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('datasource.minIdle')" label-width="120px">
                <el-input v-model="queryForm.poolConfig.minimumIdle" :readonly=true :placeholder="$t('datasource.default') + ' 10'"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('datasource.maxLifetime')" label-width="120px">
                <el-input v-model="queryForm.poolConfig.maxLifetime" :readonly=true :placeholder="$t('datasource.default') + ' 3600000'"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('datasource.connectionTimeout')" label-width="120px">
                <el-input v-model="queryForm.poolConfig.connectionTimeout" :readonly=true :placeholder="$t('datasource.default') + ' 60000'"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('datasource.idleTimeout')" label-width="120px">
                <el-input v-model="queryForm.poolConfig.idleTimeout" :readonly=true :placeholder="$t('datasource.default') + ' 60000'"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <div slot="footer"
             class="dialog-footer">
          <el-button @click="dialogFormVisible = false">{{ $t('common.close') }}</el-button>
        </div>
      </el-dialog>

      <el-dialog :title="$t('datasource.addTitle')"
                 :visible.sync="createFormVisible"
                 :showClose="false"
                 :before-close="handleClose">
        <el-form :model="createform"
                 size="mini"
                 status-icon
                 :rules="rules"
                 ref="createform">
          <el-form-item :label="$t('datasource.name')"
                        label-width="120px"
                        :required=true
                        prop="name"
                        style="width:85%">
            <el-input v-model="createform.name"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.type')"
                        label-width="120px"
                        :required=true
                        prop="type"
                        style="width:85%">
            <el-input v-model="createform.type"
                       readonly
                       class="db-type-input">
              <template slot="prefix">
                <databaseIcon :type="createform.type" style="line-height:32px;margin-left:4px;"></databaseIcon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.driverVersion')"
                        label-width="120px"
                        :required=true
                        prop="version"
                        style="width:85%">
            <el-select v-model="createform.version"
                       :placeholder="$t('datasource.selectVersion')">
              <el-option v-for="(item,index) in connectionDriver"
                         :key="index"
                         :label="item.driverVersion"
                         :value="item.driverVersion"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('datasource.jdbcUrl')"
                        label-width="120px"
                        :required=true
                        prop="url"
                        style="width:85%">
            <el-alert :title="$t('datasource.example') + ':'"
                      type="warning"
                      :description="createform.sample">
            </el-alert>
            <el-input type="textarea"
                      :rows="6"
                      :spellcheck="false"
                      :placeholder="$t('datasource.inputJdbcUrl')"
                      v-model="createform.url"
                      auto-complete="off">
            </el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.username')"
                        label-width="120px"
                        prop="username"
                        style="width:85%">
            <el-input v-model="createform.username"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.password')"
                        label-width="120px"
                        prop="password"
                        style="width:85%">
            <el-input type="password"
                      v-model="createform.password"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-divider content-position="center">{{ $t('datasource.poolConfig') }}</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('datasource.maxPoolSize')" label-width="120px">
                <el-input v-model.number="createform.poolConfig.maximumPoolSize" :placeholder="$t('datasource.default') + ' 10'" clearable></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('datasource.minIdle')" label-width="120px">
                <el-input v-model.number="createform.poolConfig.minimumIdle" :placeholder="$t('datasource.default') + ' 10'" clearable></el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('datasource.maxLifetime')" label-width="120px">
                <el-input v-model.number="createform.poolConfig.maxLifetime" :placeholder="$t('datasource.default') + ' 3600000'" clearable></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('datasource.connectionTimeout')" label-width="120px">
                <el-input v-model.number="createform.poolConfig.connectionTimeout" :placeholder="$t('datasource.default') + ' 60000'" clearable></el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('datasource.idleTimeout')" label-width="120px">
                <el-input v-model.number="createform.poolConfig.idleTimeout" :placeholder="$t('datasource.default') + ' 60000'" clearable></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <div slot="footer"
             class="dialog-footer">
          <el-button @click="goBackToDbType">{{ $t('common.previous') }}</el-button>
          <el-button type="success"
                     @click="handlePreTest(createform,'createform')">{{ $t('datasource.test') }}</el-button>
          <el-button type="primary"
                     @click="handleCreate">{{ $t('common.confirm') }}</el-button>
          <el-button type="info"
                     @click="createFormVisible = false">{{ $t('common.cancel') }}</el-button>
        </div>
      </el-dialog>

      <el-dialog :title="$t('datasource.editTitle')"
                 :visible.sync="updateFormVisible"
                 :showClose="false"
                 :before-close="handleClose">
        <el-form :model="updateform"
                 size="mini"
                 status-icon
                 :rules="rules"
                 ref="updateform">
          <el-form-item :label="$t('datasource.name')"
                        label-width="120px"
                        :required=true
                        prop="name"
                        style="width:85%">
            <el-input v-model="updateform.name"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.type')"
                        label-width="120px"
                        :required=true
                        prop="type"
                        style="width:85%">
            <el-select v-model="updateform.type"
                       class="db-type-select"
                       @change="selectChangedDriverVersion"
                       :placeholder="$t('datasource.selectDb')" disabled>
              <template slot="prefix" v-if="updateform.type">
                <databaseIcon :type="updateform.type" style="line-height:32px;margin-left:4px;"></databaseIcon>
              </template>
              <el-option v-for="(item,index) in databaseType"
                         :key="index"
                         :label="item.type"
                         :value="item.type">
                <span style="display:inline-flex;align-items:center;gap:6px;">
                  <databaseIcon :type="item.type"></databaseIcon>
                  <span>{{ item.type }}</span>
                </span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('datasource.driverVersion')"
                        label-width="120px"
                        :required=true
                        prop="version"
                        style="width:85%">
            <el-select v-model="updateform.version"
                       :placeholder="$t('datasource.selectVersion')">
              <el-option v-for="(item,index) in connectionDriver"
                         :key="index"
                         :label="item.driverVersion"
                         :value="item.driverVersion"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('datasource.jdbcUrl')"
                        label-width="120px"
                        :required=true
                        prop="url"
                        style="width:85%">
            <el-input type="textarea"
                      :rows="6"
                      :spellcheck="false"
                      v-model="updateform.url"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.username')"
                        label-width="120px"
                        style="width:85%">
            <el-input v-model="updateform.username"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-form-item :label="$t('datasource.password')"
                        label-width="120px"
                        style="width:85%">
            <el-input type="password"
                      v-model="updateform.password"
                      auto-complete="off"></el-input>
          </el-form-item>
          <el-divider content-position="center">{{ $t('datasource.poolConfig') }}</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('datasource.maxPoolSize')" label-width="120px">
                <el-input v-model.number="updateform.poolConfig.maximumPoolSize" :placeholder="$t('datasource.default') + ' 10'" clearable></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('datasource.minIdle')" label-width="120px">
                <el-input v-model.number="updateform.poolConfig.minimumIdle" :placeholder="$t('datasource.default') + ' 10'" clearable></el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('datasource.maxLifetime')" label-width="120px">
                <el-input v-model.number="updateform.poolConfig.maxLifetime" :placeholder="$t('datasource.default') + ' 3600000'" clearable></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('datasource.connectionTimeout')" label-width="120px">
                <el-input v-model.number="updateform.poolConfig.connectionTimeout" :placeholder="$t('datasource.default') + ' 60000'" clearable></el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('datasource.idleTimeout')" label-width="120px">
                <el-input v-model.number="updateform.poolConfig.idleTimeout" :placeholder="$t('datasource.default') + ' 60000'" clearable></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <div slot="footer"
             class="dialog-footer">
          <el-button type="success"
                     @click="handlePreTest(updateform,'updateform')">{{ $t('datasource.test') }}</el-button>
          <el-button type="primary"
                     @click="handleSave">{{ $t('common.confirm') }}</el-button>
          <el-button type="info"
                     @click="updateFormVisible = false">{{ $t('common.cancel') }}</el-button>
        </div>
      </el-dialog>
    </el-card>
  </div>
</template>

<script>
import databaseIcon from "@/components/databaseIcon/databaseIcon";

export default {
  name: "datasource",
  components: {
    databaseIcon
  },
  data () {
    // Connection pool defaults, kept in sync with backend DataSourceUtils constants
    const POOL_CONFIG_DEFAULTS = {
      maximumPoolSize: 10,
      minimumIdle: 10,
      maxLifetime: 3600000,
      connectionTimeout: 60000,
      idleTimeout: 60000
    };

    return {
      loading: true,
      keyword: null,
      lists: [],
      currentPage: 1,
      pageSize: 10,
      totalCount: 2,
      databaseType: [],
      connectionDriver: [],
      tableData: [
      ],
      defaultForm: {
        id: 0,
        title: "",
        type: "",
        diver: "",
        version: "",
        username: "",
        password: "",
        poolConfig: POOL_CONFIG_DEFAULTS
      },
      queryForm: {
        title: "",
        type: "",
        url: "",
        diver: "",
        version: "",
        username: "",
        password: "",
        poolConfig: POOL_CONFIG_DEFAULTS
      },
      createform: {
        title: "",
        type: "",
        diver: "",
        sample: "",
        url: "",
        version: "",
        username: "",
        password: "",
        poolConfig: POOL_CONFIG_DEFAULTS
      },
      updateform: {
        id: 0,
        title: "",
        type: "",
        diver: "",
        version: "",
        username: "",
        password: "",
        poolConfig: POOL_CONFIG_DEFAULTS
      },
      poolConfigDefaults: POOL_CONFIG_DEFAULTS,
      rules: {
        name: [
          {
            required: true,
            message: this.$t('datasource.nameRequired'),
            trigger: "blur"
          }
        ],
        type: [
          {
            required: true,
            message: this.$t('datasource.typeRequired'),
            trigger: "change"
          }
        ],
        version: [
          {
            required: true,
            message: this.$t('datasource.versionRequired'),
            trigger: "change"
          }
        ],
        url: [
          {
            required: true,
            message: this.$t('datasource.urlRequired'),
            trigger: "blur"
          }
        ]
      },
      dialogFormVisible: false,
      createFormVisible: false,
      updateFormVisible: false,
      dbTypeDialogVisible: false,
      selectedDbType: null
    }
  },
  methods: {
    loadData: function () {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/datasource/list",
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
          alert(this.$t('datasource.loadFailed') + res.data.message);
        }
      },
        function () {
          console.log("load connection list failed");
        }
      );
    },
    searchByKeyword: function () {
      this.currentPage = 1;
      this.loadData();
    },
    loadDatabaseTypes: function () {
      this.databaseType = [];
      this.$http({
        method: "GET",
        url: "/datapoly/manager/api/v1/datasource/types"
      }).then(
        res => {
          if (0 === res.data.code) {
            this.databaseType = res.data.data;
          } else {
            alert(this.$t('datasource.loadFailed') + res.data.message);
          }
        },
        function () {
          console.log("failed");
        }
      );
    },
    handleClose (done) {
    },
    handleDelete: function (index, row) {
      this.$confirm(
        this.$t('datasource.confirmDelete') + row.id + '?',
        this.$t('common.info'),
        {
          confirmButtonText: this.$t('common.confirm'),
          cancelButtonText: this.$t('common.cancel'),
          type: "warning"
        }
      ).then(() => {
        this.$http.delete(
          "/datapoly/manager/api/v1/datasource/delete/" + row.id
        ).then(res => {
          //console.log(res);
          if (0 === res.data.code) {
            this.loadData();
          } else {
            alert(this.$t('datasource.deleteFailed') + res.data.message);
          }
        });
      });
    },
    handleMore: function (index, row) {
      this.dialogFormVisible = true;
      this.queryForm = JSON.parse(JSON.stringify(row));
    },
    handleTest: function (index, row) {
      this.$http.get(
        "/datapoly/manager/api/v1/datasource/test/" + row.id
      ).then(res => {
        if (0 === res.data.code) {
          this.$alert(this.$t('datasource.connectionSuccess'), this.$t('common.info'),
            {
              confirmButtonText: this.$t('common.confirm'),
              type: "success"
            }
          );
        } else {
          this.$alert(res.data.message, this.$t('common.error'),
            {
              confirmButtonText: this.$t('common.confirm'),
              type: "error"
            }
          );
        }
      });
    },
    addConnection: function () {
      this.selectedDbType = null;
      this.dbTypeDialogVisible = true;
    },
    selectDbType: function (type) {
      this.selectedDbType = type;
    },
    goToForm: function () {
      if (!this.selectedDbType) return;
      this.dbTypeDialogVisible = false;
      this.createform = {
        type: this.selectedDbType,
        poolConfig: { ...this.poolConfigDefaults}
      };
      this.selectChangedDriverVersion(this.selectedDbType);
      this.createFormVisible = true;
    },
    goBackToDbType: function () {
      this.createFormVisible = false;
      this.dbTypeDialogVisible = true;
    },
    handlePreTest: function (form, refName,) {
      let driverClass = "";
      if (this.databaseType.length > 0) {
        for (let i = 0; i < this.databaseType.length; i++) {
          if (this.databaseType[i].type == form.type) {
            driverClass = this.databaseType[i].driver;
            break;
          }
        }
      }

      this.$refs[refName].validate(valid => {
        if (valid) {
          const requestData = {
            name: form.name,
            type: form.type,
            version: form.version,
            driver: driverClass,
            url: form.url,
            username: form.username,
            password: form.password,
            poolConfig : form.poolConfig,
          };

          this.$http({
            method: "POST",
            headers: {
              'Content-Type': 'application/json'
            },
            url: "/datapoly/manager/api/v1/datasource/preTest",
            data: JSON.stringify(requestData)
          }).then(res => {
            if (0 === res.data.code) {
              this.$alert(this.$t('datasource.testSuccess'), this.$t('datasource.testResult'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "info"
                }
              );
            } else {
              this.$alert(res.data.message, this.$t('datasource.testFailed'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "error"
                }
              );
            }
          });
        } else {
          this.$alert(this.$t('datasource.checkInput'), this.$t('common.info'),
            {
              confirmButtonText: this.$t('common.confirm'),
              type: "info"
            }
          );
        }
      });
    },
    handleCreate: function () {
      let driverClass = "";
      if (this.databaseType.length > 0) {
        for (let i = 0; i < this.databaseType.length; i++) {
          if (this.databaseType[i].type == this.createform.type) {
            driverClass = this.databaseType[i].driver;
            break;
          }
        }
      }

      this.$refs['createform'].validate(valid => {
        if (valid) {
          const requestData = {
            name: this.createform.name,
            type: this.createform.type,
            version: this.createform.version,
            driver: driverClass,
            url: this.createform.url,
            username: this.createform.username,
            password: this.createform.password,
            poolConfig : this.createform.poolConfig,
          };

          this.$http({
            method: "POST",
            headers: {
              'Content-Type': 'application/json'
            },
            url: "/datapoly/manager/api/v1/datasource/create",
            data: JSON.stringify(requestData)
          }).then(res => {
            if (0 === res.data.code) {
              this.createFormVisible = false;
              this.$message(this.$t('datasource.addSuccess'));
              this.createform = JSON.parse(JSON.stringify(this.defaultForm));
              this.loadData();
            } else {
              this.$alert(res.data.message, this.$t('datasource.addFailed'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "error"
                }
              );
            }
          });
        } else {
          this.$alert(this.$t('datasource.checkInput'), this.$t('common.info'),
            {
              confirmButtonText: this.$t('common.confirm'),
              type: "info"
            }
          );
        }
      });
    },
    selectChangedDriverVersion: function (value) {
      this.connectionDriver = [];
      this.$http.get(
        "/datapoly/manager/api/v1/datasource/" + value + "/drivers"
      ).then(res => {
        if (0 === res.data.code) {
          this.connectionDriver = res.data.data;
          let varDatabaseType = this.databaseType.find(
            (item) => {
              return item.type === value;
            });
          if (varDatabaseType) {
            this.createform.sample = varDatabaseType.sample;
          }
        } else {
          this.$message.error(this.$t('datasource.loadDriverFailed') + res.data.message);
          this.connectionDriver = [];
        }
      });
    },
    handleUpdate: function (index, row) {
      this.updateform = JSON.parse(JSON.stringify(row));
      this.$http.get(
        "/datapoly/manager/api/v1/datasource/" + this.updateform.type + "/drivers"
      ).then(res => {
        if (0 === res.data.code) {
          this.connectionDriver = res.data.data;
        } else {
          this.$message.error(this.$t('datasource.loadDriverFailed') + res.data.message);
          this.connectionDriver = [];
        }
      });
      this.updateFormVisible = true;
    },
    handleSave: function () {
      let driverClass = "";
      if (this.databaseType.length > 0) {
        for (let i = 0; i < this.databaseType.length; i++) {
          if (this.databaseType[i].type == this.updateform.type) {
            driverClass = this.databaseType[i].driver;
            break;
          }
        }
      }

      this.$refs['updateform'].validate(valid => {
        if (valid) {
          const requestData = {
            id: this.updateform.id,
            name: this.updateform.name,
            type: this.updateform.type,
            version: this.updateform.version,
            driver: driverClass,
            url: this.updateform.url,
            username: this.updateform.username,
            password: this.updateform.password,
            poolConfig : this.updateform.poolConfig,
          };
          this.$http({
            method: "POST",
            headers: {
              'Content-Type': 'application/json'
            },
            url: "/datapoly/manager/api/v1/datasource/update",
            data: JSON.stringify(requestData)
          }).then(res => {
            if (0 === res.data.code) {
              this.updateFormVisible = false;
              this.$message(this.$t('datasource.updateSuccess'));
              this.updateform = JSON.parse(JSON.stringify(this.defaultForm));
              this.loadData();
            } else {
              this.$alert(res.data.message, this.$t('datasource.updateFailed'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "error"
                }
              );
            }
          });
        } else {
          this.$alert(this.$t('datasource.checkInput'), this.$t('common.info'),
            {
              confirmButtonText: this.$t('common.confirm'),
              type: "info"
            }
          );
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
    }
  },
  created () {
    this.loadDatabaseTypes();
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
.connection-list-top {
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
  margin-left: auto;
  margin: 10px 5px;
}

/* DB type selector: show icon in prefix when selected */
/deep/ .db-type-select .el-input__inner {
  padding-left: 36px;
}
/deep/ .db-type-select .el-input__prefix {
  display: flex;
  align-items: center;
}

/* DB type selection dialog styles */
.db-type-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 16px;
  padding: 16px;
}

.db-type-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.db-type-item:hover {
  border-color: #409eff;
  background-color: #f5f7fa;
  transform: scale(1.05);
}

.db-type-item-selected {
  border-color: #409eff;
  background-color: #ecf5ff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.db-type-item span {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}
</style>
