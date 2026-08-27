<template>
  <div>
    <el-card>
      <div class="assignment-list-top">
        <div class="left-search-input">
          <el-radio-group v-model="online"
                          @change="handleSearch"
                          size="small">
            <el-radio-button :label="false">{{ $t('interface.developing') }}</el-radio-button>
            <el-radio-button :label="true">{{ $t('interface.online') }}</el-radio-button>
          </el-radio-group>
          <el-select v-model="groupId"
                     size="mini"
                     :clearable="true"
                     style="width:15%"
                     :placeholder="$t('interface.selectGroup')">
            <el-option v-for="(item,index) in groupLists"
                       :key="index"
                       :label="item.name"
                       :value="item.id"></el-option>
          </el-select>
          <el-select v-model="moduleId"
                     size="mini"
                     :clearable="true"
                     style="width:15%"
                     :placeholder="$t('interface.selectModule')">
            <el-option v-for="(item,index) in moduleLists"
                       :key="index"
                       :label="item.name"
                       :value="item.id"></el-option>
          </el-select>
          <el-select v-model="open"
                     size="mini"
                     :clearable="true"
                     style="width:10%"
                     :placeholder="$t('interface.isPublic')">
            <el-option :key=true
                       :label="$t('common.yes')"
                       :value=true></el-option>
            <el-option :key=false
                       :label="$t('common.no')"
                       :value=false></el-option>
          </el-select>
          <el-input :placeholder="$t('interface.searchByName')"
                    size="mini"
                    v-model="keyword"
                    :clearable=true
                    style="width:15%"
                    @change="searchByKeyword">
          </el-input>
          <el-button type="primary"
                     size="mini"
                     icon="el-icon-search"
                     @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-switch v-model="apiDocStatus"
                     active-color="#13ce66"
                     inactive-color="#ff4949"
                     :active-value=true
                     :inactive-value=false
                     v-if="online"
                     :active-text="$t('interface.docOn')"
                     :inactive-text="$t('interface.docOff')"
                     @change="hanldeSwitchApiDoc()">
          </el-switch>
        </div>
        <div class="right-button-group">
          <el-button type="warning"
                     size="mini"
                     :disabled="isSelected"
                     plain
                     icon="el-icon-download"
                     @click="handleBatchExport">{{ $t('interface.export') }}</el-button>
          <el-upload :action="uploadAssignmentPath"
                     accept="application/json"
                     :http-request="handleFileUpload"
                     v-if="!online"
                     :multiple="false"
                     :show-file-list="false"
                     :auto-upload="true">
            <el-button type="warning"
                       size="mini"
                       plain
                       v-if="!online"
                       icon="el-icon-upload2">{{ $t('interface.import') }}</el-button>
          </el-upload>
          <el-button type="warning"
                     size="mini"
                     :disabled="apiDocStatus==false"
                     v-if="online"
                     icon="el-icon-document-add"
                     @click="openOnlineApiDoc">{{ $t('interface.onlineDoc') }}</el-button>
          <el-button type="primary"
                     size="mini"
                     v-if="!online"
                     icon="el-icon-document-add"
                     @click="handleCreate">{{ $t('interface.newInterface') }}</el-button>
        </div>
      </div>

      <el-table :header-cell-style="{background:'#eef1f6',color:'#606266',whiteSpace:'nowrap'}"
                :data="tableData"
                size="small"
                @selection-change="handleSelectionChange"
                style="width:100%"
                table-layout="fixed"
                border>
        <el-table-column prop="id"
                         type="selection"
                         width="60"></el-table-column>
        <el-table-column prop="id"
                         :label="$t('interface.id')"
                         width="80"></el-table-column>
        <el-table-column prop="name"
                         :label="$t('interface.name')"
                         show-overflow-tooltip
                         min-width="200">
          <template slot-scope="scope">
            <el-link class="btn-text"
                     type="primary"
                     @click="handleDetail(scope.$index, scope.row)">{{ scope.row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column :label="$t('interface.path')"
                         show-overflow-tooltip
                         min-width="300">
          <template slot-scope="scope">
            <el-tag size="medium"
                    class="name-wrapper-tag">{{ scope.row.method }}</el-tag>
            {{ scope.row.path }}
          </template>
        </el-table-column>
        <el-table-column prop="moduleName"
                         :label="$t('interface.module')"
                         show-overflow-tooltip
                         min-width="120"></el-table-column>
        <el-table-column prop="groupName"
                         :label="$t('interface.group')"
                         show-overflow-tooltip
                         min-width="120"></el-table-column>
        <el-table-column :label="$t('interface.engine')"
                         min-width="100">
          <template slot-scope="scope">
            <el-tag size="medium"
                    class="name-wrapper-tag">{{ scope.row.engine }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status"
                         :label="$t('interface.online')"
                         :formatter="boolFormatPublish"
                         show-overflow-tooltip
                         v-if="online"
                         min-width="80"></el-table-column>
        <el-table-column prop="open"
                         :label="$t('interface.public')"
                         :formatter="boolFormatOpen"
                         v-if="online"
                         show-overflow-tooltip
                         min-width="80"></el-table-column>
        <el-table-column prop="alarm"
                         :label="$t('interface.alarm')"
                         :formatter="boolFormatAlarm"
                         v-if="online"
                         show-overflow-tooltip
                         min-width="80"></el-table-column>
        <el-table-column prop="createTime"
                         :label="$t('interface.createTime')"
                         min-width="180"></el-table-column>
        <el-table-column :label="$t('common.operation')"
                         fixed="right"
                         min-width="350">
          <template slot-scope="scope">
            <el-button-group>
              <el-button size="small"
                         type="primary"
                         icon="el-icon-timer"
                         v-if="scope.row.status===false"
                         @click="handleOnline(scope.$index, scope.row)"
                         round>{{ $t('interface.goOnline') }}</el-button>
              <el-button size="small"
                         type="info"
                         icon="el-icon-delete-location"
                         v-if="scope.row.status===true"
                         @click="handleOffline(scope.$index, scope.row)"
                         round>{{ $t('interface.goOffline') }}</el-button>
              <el-button size="small"
                         type="warning"
                         icon="el-icon-edit"
                         @click="handleUpdate(scope.$index, scope.row)"
                         round>{{ $t('common.edit') }}</el-button>
              <el-button size="small"
                         type="success"
                         icon="el-icon-position"
                         @click="handlePublish(scope.$index, scope.row)"
                         round>{{ $t('interface.publish') }}</el-button>
              <el-button size="small"
                         type="danger"
                         icon="el-icon-delete"
                         v-if="scope.row.status===false"
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
    </el-card>

    <el-dialog :title="$t('interface.publishNewVersion')"
               :visible.sync="publishDialogVisible"
               :showClose="false"
               width="40%"
               :before-close="handleClose">
      <el-form size="mini"
               status-icon>
        <el-form-item :label="$t('interface.versionDesc')"
                      label-width="120px"
                      :required=true
                      style="width:85%">
          <el-input type="textarea"
                    :autosize="{ minRows: 4, maxRows: 10 }"
                    v-model="publishVersionDesc"
                    auto-complete="off"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer"
           class="dialog-footer">
        <el-button @click="publishDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary"
                   @click="handlePublishVersion">{{ $t('interface.publish') }}</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="$t('interface.selectOnlineVersion')"
               :visible.sync="versionDialogVisible"
               :showClose="false"
               width="40%"
               :before-close="handleClose">
      <el-table :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                :data="versions"
                highlight-current-row
                size="mini"
                border>
        <template slot="empty">
          <span>{{ $t('interface.versionEmpty') }}</span>
        </template>
        <el-table-column :label="$t('interface.selectVersion')"
                         min-width="120">
          <template slot-scope="scope">
            <el-radio v-model="selectCommitId"
                      :label="scope.row.commitId">V{{ scope.row.version }}</el-radio>
          </template>
        </el-table-column>
        <el-table-column prop="createTime"
                         :label="$t('interface.generateTime')"
                         min-width="180"> </el-table-column>
        <el-table-column prop="description"
                         :label="$t('interface.versionDesc')"
                         show-overflow-tooltip
                         min-width="200"></el-table-column>
      </el-table>
      <div slot="footer"
           class="dialog-footer">
        <el-button @click="versionDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary"
                   @click="handleDeploy">{{ $t('interface.deploy') }}</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="$t('interface.selectOpenApiDocType')"
               :visible.sync="selectOpenApiDocsDialogVisible"
               :showClose="false"
               width="20%"
               :before-close="handleClose">
      <el-select v-model="selectedOpenApiDocType"
                 size="mini"
                 style="width:95%"
                 :placeholder="$t('interface.selectDocType')">
        <el-option v-for="(item,index) in openApiDocs"
                   :key="index"
                   :label="item.key"
                   :value="item.key"></el-option>
      </el-select>
      <div slot="footer"
           class="dialog-footer">
        <el-button @click="selectOpenApiDocsDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary"
                   @click="handleOpenApiDoc">{{ $t('interface.open') }}</el-button>
      </div>
    </el-dialog>

  </div>
</template>

<script>
export default {

  data () {
    return {
      loading: true,
      currentPage: 1,
      pageSize: 10,
      totalCount: 2,
      keyword: null,
      groupId: null,
      moduleId: null,
      online: false,
      open: null,
      apiDocStatus: true,
      groupLists: [],
      moduleLists: [],
      tableData: [],
      publishDialogVisible: false,
      publishVersionDesc: '',
      versionDialogVisible: false,
      selectRowId: 0,
      selectCommitId: 0,
      versions: [],
      openApiDocs: [{ key: "swagger" }, { key: "knife4j" }],
      selectOpenApiDocsDialogVisible: false,
      selectedOpenApiDocType: "swagger",
      isSelected: true,
      idsSelected: [],
      uploadAssignmentPath: process.env.API_ROOT + '/datapoly/manager/api/v1/assignment/import',
    };
  },
  methods: {
    handleClose (done) {
    },
    loadData: function () {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/list",
        data: window.JSON.stringify(
          {
            groupId: this.groupId,
            moduleId: this.moduleId,
            online: this.online,
            open: this.open,
            searchText: this.keyword,
            page: this.currentPage,
            size: this.pageSize
          }
        )
      }).then(res => {
        if (0 === res.data.code) {
          this.currentPage = res.data.pagination.page;
          this.pageSize = res.data.pagination.size;
          this.totalCount = res.data.pagination.total;
          this.tableData = res.data.data;
        } else {
          alert(this.$t('interface.loadFailed') + res.data.message);
        }
      }
      );
    },
    loadGroupList () {
      this.groupLists = [];
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
      }).then(
        res => {
          if (0 === res.data.code) {
            this.groupLists = res.data.data;
          } else {
            alert(this.$t('interface.loadFailed') + res.data.message);
          }
        }
      );
    },
    loadModuleList () {
      this.moduleLists = [];
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
            this.moduleLists = res.data.data;
          } else {
            alert(this.$t('interface.loadFailed') + res.data.message);
          }
        }
      );
    },
    loadApiDocOpenStatus () {
      this.$http.get(
        "/datapoly/manager/api/v1/param/value/query?key=apiDocOpen"
      ).then(res => {
        if (0 === res.data.code) {
          this.apiDocStatus = res.data.data;
        } else {
          if (res.data.message) {
            alert(this.$t('interface.operationFailed') + res.data.message);
          }
        }
      });
    },
    searchByKeyword: function () {
      this.currentPage = 1;
      this.loadData();
    },
    hanldeSwitchApiDoc: function () {
      this.$http.post(
        "/datapoly/manager/api/v1/param/value/update?key=apiDocOpen&value=" + this.apiDocStatus
      ).then(res => {
        if (0 === res.data.code) {
          this.loadApiDocOpenStatus();
        } else {
          if (res.data.message) {
            alert(this.$t('interface.operationFailed') + res.data.message);
          }
        }
      });
    },
    boolFormatPublish (row) {
      if (row.status === true) {
        return "V" + row.version;
      } else {
        return this.$t('common.no');
      }
    },
    boolFormatOpen (row) {
      if (row.open === true) {
        return this.$t('common.yes');
      } else {
        return this.$t('common.no');
      }
    },
    boolFormatAlarm (row) {
      if (row.alarm === true) {
        return this.$t('interface.on');
      } else {
        return this.$t('interface.off');
      }
    },
    handleSearch: function () {
      this.loadData();
    },
    handleCreate: function () {
      this.$router.push('/interface/create')
    },
    openOnlineApiDoc: function () {
      this.selectedOpenApiDocType = this.openApiDocs[0].key;
      this.selectOpenApiDocsDialogVisible = true;
    },
    handleOpenApiDoc: function () {
      this.$http({
        method: "GET",
        url: "/datapoly/manager/api/v1/node/gateway"
      }).then(
        res => {
          if (0 === res.data.code) {
            if (res.data.data && typeof res.data.data === 'string') {
              var url = res.data.data + '/apidoc/' + this.selectedOpenApiDocType + '/index.html';
              window.open(url, '_blank');
            }
          } else {
            if (res.data.message) {
              alert(this.$t('message.operationFailed') + res.data.message);
            }
          }
          this.selectOpenApiDocsDialogVisible = false;
        }
      );
    },
    handleDetail: function (index, row) {
      this.$router.push({ path: '/interface/detail', query: { id: row.id } })
    },
    handleUpdate: function (index, row) {
      this.$router.push({ path: '/interface/update', query: { id: row.id } })
    },
    handleDelete: function (index, row) {
      this.$confirm(
        this.$t('interface.confirmDelete') + row.id + "?",
        this.$t('common.warning'),
        {
          confirmButtonText: this.$t('common.confirm'),
          cancelButtonText: this.$t('common.cancel'),
          type: "warning"
        }
      ).then(() => {
        this.$http.delete(
          "/datapoly/manager/api/v1/assignment/delete/" + row.id
        ).then(res => {
          if (0 === res.data.code) {
            this.loadData();
          } else {
            if (res.data.message) {
              alert(this.$t('interface.deleteFailed') + res.data.message);
            }
          }
        });
      });
    },
    handlePublish: function (index, row) {
      this.selectRowId = row.id
      this.publishDialogVisible = true
    },
    handlePublishVersion: function () {
      if (!this.publishVersionDesc) {
        this.$alert(this.$t('interface.versionDescRequired'), this.$t('common.error'),
          {
            confirmButtonText: this.$t('common.confirm'),
            type: "error"
          }
        );
        return;
      }
      this.$http({
        method: "PUT",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/publish",
        data: JSON.stringify({
          id: this.selectRowId,
          description: this.publishVersionDesc,
        })
      }).then(res => {
        if (0 === res.data.code) {
          this.selectRowId = null;
          this.publishVersionDesc = null;
          this.publishDialogVisible = false
          this.$message(this.$t('interface.publishSuccess'));
        } else {
          if (res.data.message) {
            alert(this.$t('interface.publishFailed') + res.data.message);
          }
        }
      });
    },
    handleOnline: function (index, row) {
      this.$http({
        method: "GET",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/version/list/" + row.id,
      }).then(res => {
        if (0 === res.data.code) {
          this.versions = res.data.data;
          this.selectRowId = row.id;
          this.versionDialogVisible = true;
        } else {
          if (res.data.message) {
            alert(this.$t('interface.getVersionListFailed') + res.data.message);
          }
        }
      });
    },
    handleDeploy: function () {
      if (!this.selectCommitId || this.selectCommitId <= 0) {
        this.$alert(this.$t('interface.selectVersionFirst'), this.$t('common.error'),
          {
            confirmButtonText: this.$t('common.confirm'),
            type: "error"
          }
        );
        return;
      }
      this.$http({
        method: "PUT",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/deploy/" + this.selectRowId + "?commitId=" + this.selectCommitId,
      }).then(res => {
        if (0 === res.data.code) {
          this.selectRowId = null;
          this.selectCommitId = null;
          this.versionDialogVisible = false;
          this.$message(this.$t('interface.onlineSuccess'));
          this.loadData();
        } else {
          if (res.data.message) {
            alert(this.$t('interface.onlineFailed') + res.data.message);
          }
        }
      });
    },
    handleOffline: function (index, row) {
      this.$http({
        method: "PUT",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/retire/" + row.id,
      }).then(res => {
        if (0 === res.data.code) {
          this.$message(this.$t('interface.offlineSuccess'));
          this.loadData();
        } else {
          if (res.data.message) {
            alert(this.$t('interface.offlineFailed') + res.data.message);
          }
        }
      });
    },
    handleSelectionChange (arr) {
      if (arr.length > 0) {
        this.isSelected = false;
        for (var item of arr) {
          if (!this.idsSelected.includes(item.id)) {
            this.idsSelected.push(item.id);
          }
        }
      } else {
        this.isSelected = true;
        this.idsSelected = []
      }
    },
    handleBatchExport: function () {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/export",
        data: JSON.stringify(this.idsSelected),
        responseType: 'blob',
      }).then(resp => {
        const headers = resp.headers;
        const contentType = headers['content-type'];
        if (!resp.data) {
          console.error('响应异常：', resp);
          return false;
        } else {
          const blob = new Blob([resp.data], { type: contentType });
          this.downloadFile(blob, "datapoly_interfaces.json");
        }
      });
    },
    downloadFile: function (blob, fileName) {
      if ('download' in document.createElement('a')) {
        const link = document.createElement('a');
        link.href = window.URL.createObjectURL(blob); // download URL
        link.download = fileName; // file name on save
        link.style.display = 'none';
        document.body.appendChild(link);
        link.click(); // trigger download
        window.URL.revokeObjectURL(link.href); // release the blob
        document.body.removeChild(link); // remove element after download
      } else {
        // IE10+ download
        window.navigator.msSaveBlob(blob, fileName);
      }
    },
    handleFileUpload: function (options) {
      const formData = new FormData();
      formData.append("file", options.file);
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'multipart/form-data'
        },
        url: "/datapoly/manager/api/v1/assignment/import",
        data: formData,
      })
        .then(res => {
          if (0 === res.data.code) {
            this.$message.success(this.$t('interface.importSuccess'));
            this.loadData();
          } else {
            if (res.data.message) {
              this.$alert(this.$t('interface.importFailed') + res.data.message, this.$t('common.error'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "error"
                }
              );
            }
          }
        })
        .catch(err => {
          this.$message.error(this.$t('interface.importFailed') + err);
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
    this.loadGroupList();
    this.loadModuleList();
    this.loadApiDocOpenStatus();
    this.loadData();
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

.el-table ::v-deep .el-table__body-wrapper {
  overflow-x: auto;
}

.el-table ::v-deep .el-table__body {
  min-width: 1500px;
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

.el-input.is-disabled .el-input__inner {
  background-color: #f5f7fa;
  border-color: #e4e7ed;
  color: #c0c4cc;
  cursor: pointer;
}

.assignment-list-top {
  width: 100%;
  display: flex;
  align-items: center;
  padding: 5px;
}

.left-search-input {
  flex: 1;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

.right-button-group {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  margin-left: 10px;
  gap: 5px;
}
</style>
