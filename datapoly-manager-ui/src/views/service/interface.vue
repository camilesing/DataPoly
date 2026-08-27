<template>
  <el-card>
    <div ref="box"
         class="box">
      <div class="resizable"
           :style="{ width: leftWidth + 'px' }">
        <el-scrollbar style="min-height: 500px; max-height: 800px; overflow-x: hidden; overflow-y: auto;">
          <el-tree ref="tree"
                   :empty-text="$t('service.emptyContent')"
                   :indent=4
                   :data="treeData"
                   :props="props"
                   :load="loadNode"
                   :expand-on-click-node="true"
                   :highlight-current="true"
                   :render-content="renderContent"
                   @node-click="handleTreeNodeClick"
                   lazy>
          </el-tree>
        </el-scrollbar>
      </div>
      <div class="resizer"
           :style="{ height: resizerHeight + 'px' }"
           @mousedown="startResize">⋮</div>
      <div class="resizable"
           :style="{ width: rightWidth + 'px' }">
        <div v-if="!showDetail">
          <el-table :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                    :data="tableData"
                    size="small"
                    border>
            <el-table-column prop="name"
                             :label="$t('service.interfaceName')"
                             show-overflow-tooltip
                             min-width="30%">
              <template slot-scope="scope">
                [{{ scope.row.id }}]{{ scope.row.name }}
              </template>
            </el-table-column>
            <el-table-column prop="path"
                             :label="$t('service.interfacePath')"
                             show-overflow-tooltip
                             min-width="20%">
              <template slot-scope="scope">
                <el-tag size="medium"
                        class="name-wrapper-tag">{{ scope.row.method }}</el-tag>
                {{ scope.row.path }}
              </template>
            </el-table-column>
            <el-table-column :label="$t('service.engine')"
                             min-width="10%">
              <template slot-scope="scope">
                <el-tag size="medium"
                        class="name-wrapper-tag">{{ scope.row.engine }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime"
                             :label="$t('service.createTime')"
                             min-width="18%"></el-table-column>
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
        </div>
        <el-tabs v-if="showDetail"
                 type="border-card">
          <el-tab-pane :label="$t('service.interfaceDefinition')">
            <el-row class="detail-row">
              <el-col :span="4">
                <i class="el-icon-key">{{ $t('service.currentVersion') }}</i>
              </el-col>
              <el-col :span="20">
                <el-tag size="small">V{{interfaceDetail.version}}</el-tag>
                <el-button size="small"
                           type="danger"
                           icon="el-icon-timer"
                           @click="handleSwitchVersion(interfaceDetail)"
                           round>{{ $t('service.switchVersion') }}</el-button>
              </el-col>
            </el-row>
            <el-row class="detail-row">
              <el-col :span="4">
                <i class="el-icon-mic">{{ $t('service.commitId') }}</i>
              </el-col>
              <el-col :span="20">
                <el-tag size="small">{{interfaceDetail.commitId}}</el-tag>
              </el-col>
            </el-row>
            <el-row class="detail-row">
              <el-col :span="4">
                <i class="el-icon-user">{{ $t('service.interfaceNameLabel') }}</i>
              </el-col>
              <el-col :span="20">
                <el-tag size="small">{{interfaceDetail.name}}</el-tag>
              </el-col>
            </el-row>
            <el-row class="detail-row">
              <el-col :span="4">
                <i class="el-icon-attract">{{ $t('service.interfacePathLabel') }}</i>
              </el-col>
              <el-col :span="20">
                <el-tag size="small"
                        type="danger">{{interfaceDetail.method}}</el-tag>
                <el-tag size="small"
                        type="warning">{{interfaceDetail.path}}</el-tag>
              </el-col>
            </el-row>
            <el-row class="detail-row">
              <el-col :span="4">
                <i class="el-icon-tickets">{{ $t('service.requestType') }}</i>
              </el-col>
              <el-col :span="20">
                <el-tag size="small"
                        type="success">{{interfaceDetail.contentType}}</el-tag>
              </el-col>
            </el-row>
            <el-row class="detail-row">
              <el-col :span="4">
                <i class="el-icon-s-check">{{ $t('service.accessAuth') }}</i>
              </el-col>
              <el-col :span="20">
                <el-tag size="small"
                        type="danger">{{boolTypeFormat(!interfaceDetail.open)}}</el-tag>
              </el-col>
            </el-row>
            <el-row class="detail-row">
              <el-col :span="4">
                <i class="el-icon-help">{{ $t('service.interfaceDesc') }}</i>
              </el-col>
              <el-col :span="20">
                <el-tag size="small"
                        type="info">{{interfaceDetail.description}}</el-tag>
              </el-col>
            </el-row>
            <el-row class="detail-row">
              <el-col :span="4">
                <i class="el-icon-postcard">{{ $t('service.requestParams') }}</i>
              </el-col>
              <el-col :span="20">
                <el-table :data="interfaceDetail.inputParams"
                          :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                          size="mini"
                          default-expand-all
                          row-key="id"
                          :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
                          border>
                  <template slot="empty">
                    <span>{{ $t('service.requestParamsEmpty') }}</span>
                  </template>
                  <el-table-column :label="$t('service.paramName')"
                                   prop="name"
                                   min-width="40%">
                  </el-table-column>
                  <el-table-column :label="$t('service.paramLocation')"
                                   prop="location"
                                   min-width="15%">
                    <template slot-scope="scope">
                      {{enumTypeLocationFormat(scope.row.location)}}
                    </template>
                  </el-table-column>
                  <el-table-column :label="$t('service.paramType')"
                                   prop="type"
                                   min-width="15%">
                    <template slot-scope="scope">
                      {{enumTypeValueFormat(scope.row.type)}}
                    </template>
                  </el-table-column>
                  <el-table-column :label="$t('service.isArray')"
                                   min-width="15%">
                    <template slot-scope="scope">
                      {{boolTypeFormat(scope.row.isArray)}}
                    </template>
                  </el-table-column>
                  <el-table-column :label="$t('service.required')"
                                   min-width="15%">
                    <template slot-scope="scope">
                      {{boolTypeFormat(scope.row.required)}}
                    </template>
                  </el-table-column>
                  <el-table-column :label="$t('service.defaultValue')"
                                   prop="defaultValue"
                                   min-width="20%">
                    <template slot-scope="scope">
                      {{scope.row.defaultValue}}
                    </template>
                  </el-table-column>
                  <el-table-column :label="$t('service.description')"
                                   prop="remark"
                                   min-width="25%">
                    <template slot-scope="scope">
                      {{scope.row.remark}}
                    </template>
                  </el-table-column>
                </el-table>
              </el-col>
            </el-row>
            <el-row class="detail-row">
              <el-col :span="4">
                <i class="el-icon-chat-line-round">{{ $t('service.responseParams') }}</i>
              </el-col>
              <el-col :span="20">
                <el-table :data="interfaceDetail.outputParams"
                          :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                          size="mini"
                          default-expand-all
                          row-key="id"
                          :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
                          border>
                  <template slot="empty">
                    <span>{{ $t('service.responseParamsEmpty') }}</span>
                  </template>
                  <el-table-column :label="$t('service.paramName')"
                                   prop="name"
                                   min-width="25%">
                  </el-table-column>
                  <el-table-column :label="$t('service.paramType')"
                                   min-width="25%">
                    <template slot-scope="scope">
                      {{enumTypeValueFormat(scope.row.type)}}
                    </template>
                  </el-table-column>
                  <el-table-column :label="$t('service.description')"
                                   min-width="25%">
                    <template slot-scope="scope">
                      {{scope.row.remark}}
                    </template>
                  </el-table-column>
                </el-table>
              </el-col>
            </el-row>
          </el-tab-pane>
          <el-tab-pane :label="$t('service.accessLog')">
            <el-table :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                      :data="accessLogData"
                      size="small"
                      border>
              <el-table-column prop="createTime"
                               :label="$t('service.time')"
                               min-width="20%"></el-table-column>
              <el-table-column :label="$t('service.clientAddress')"
                               prop="ipAddr"
                               :show-overflow-tooltip="true"
                               min-width="15%">
              </el-table-column>
              <el-table-column :label="$t('service.executorAddress')"
                               prop="executorAddr"
                               :show-overflow-tooltip="true"
                               min-width="15%">
              </el-table-column>
              <el-table-column :label="$t('service.gatewayAddress')"
                               prop="gatewayAddr"
                               :show-overflow-tooltip="true"
                               min-width="15%">
              </el-table-column>
              <el-table-column :label="$t('service.statusCode')"
                               prop="status"
                               :show-overflow-tooltip="true"
                               min-width="12%">
              </el-table-column>
              <el-table-column :label="$t('service.duration')"
                               prop="duration"
                               :show-overflow-tooltip="true"
                               min-width="12%">
              </el-table-column>
              <el-table-column :label="$t('service.caller')"
                               prop="clientApp"
                               :show-overflow-tooltip="true"
                               min-width="15%">
              </el-table-column>
              <el-table-column prop="userAgent"
                               :label="'UserAgent'"
                               :show-overflow-tooltip="true"
                               min-width="15%">
              </el-table-column>
              <el-table-column :label="$t('service.operation')"
                               min-width="20%">
                <template slot-scope="scope">
                  <el-link class="btn-text"
                           type="primary"
                           @click="handleShowParam(scope.$index, scope.row)">{{ $t('service.inputParams') }}</el-link>
                  <label v-if="scope.row.exception"
                         class="btn-style">&nbsp;|&nbsp;</label>
                  <el-link class="btn-text"
                           v-if="scope.row.exception"
                           type="primary"
                           @click="handleShowException(scope.$index, scope.row)">{{ $t('service.exception') }}</el-link>
                </template>
              </el-table-column>
            </el-table>
            <div class="page"
                 align="right">
              <el-pagination @size-change="handleAccessSizeChange"
                             @current-change="handleAccessCurrentChange"
                             :current-page="currentAccessPageNum"
                             :page-sizes="[5, 10, 20, 40]"
                             :page-size="currentAccessPageSize"
                             layout="total, sizes, prev, pager, next, jumper"
                             :total="totalAccessItemCount"></el-pagination>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <el-dialog :title="$t('service.requestParamsDialog')"
               :visible.sync="showParamDialogVisible"
               :showClose="false">
      <json-viewer :value="requestParameters"
                   :expand-depth=10
                   copyable
                   boxed
                   sort></json-viewer>
      <div slot="footer"
           class="dialog-footer">
        <el-button type="info"
                   @click="showParamDialogVisible = false">{{ $t('service.close') }}</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="$t('service.exceptionDialog')"
               :visible.sync="showExceptDialogVisible"
               :showClose="false">
      <el-input type="textarea"
                :rows="20"
                :spellcheck="false"
                v-model="exeptionText"></el-input>
      <div slot="footer"
           class="dialog-footer">
        <el-button type="info"
                   @click="showExceptDialogVisible = false">{{ $t('service.close') }}</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="$t('service.switchOnlineVersion')"
               :visible.sync="versionDialogVisible"
               :showClose="false"
               width="40%"
               :before-close="handleClose">
      <el-table :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                :data="versionList"
                highlight-current-row
                size="mini"
                border>
        <template slot="empty">
          <span>{{ $t('service.versionEmptyTip') }}</span>
        </template>
        <el-table-column :label="$t('service.selectVersion')"
                         min-width="10%">
          <template slot-scope="scope">
            <el-radio v-model="selectCommitId"
                      :label="scope.row.commitId">V{{ scope.row.version }}</el-radio>
          </template>
        </el-table-column>
        <el-table-column prop="createTime"
                         :label="$t('service.generateTime')"
                         min-width="15%"> </el-table-column>
        <el-table-column prop="description"
                         :label="$t('service.versionDesc')"
                         show-overflow-tooltip
                         min-width="20%"></el-table-column>
      </el-table>
      <div slot="footer"
           class="dialog-footer">
        <el-button @click="versionDialogVisible = false">{{ $t('service.close') }}</el-button>
        <el-button type="primary"
                   @click="handleDeployVersion">{{ $t('service.switch') }}</el-button>
      </div>
    </el-dialog>

  </el-card>
</template>

<script>
import '@/assets/sysicon/iconfont.js'
import JsonViewer from 'vue-json-viewer';

export default {
  data () {
    return {
      paramLocation: [
        { name: this.$t('service.query'), value: "REQUEST_FORM" },
        { name: this.$t('service.body'), value: "REQUEST_BODY" },
        { name: this.$t('service.header'), value: "REQUEST_HEADER" }
      ],
      paramTypeList: [
        { name: this.$t('service.integer'), value: "LONG" },
        { name: this.$t('service.float'), value: "DOUBLE" },
        { name: this.$t('service.string'), value: "STRING" },
        { name: this.$t('service.date'), value: "DATE" },
        { name: this.$t('service.timeType'), value: "TIME" },
        { name: this.$t('service.object'), value: "OBJECT" }
      ],
      leftWidth: 0, // initial width of the left pane
      rightWidth: 0, // initial width of the right pane
      resizerHeight: 0,
      startX: 0, // pointer position on mouse down
      treeData: [],
      props: {
        label: 'label',
        children: 'children',
        isLeaf: 'leaf'
      },
      showDetail: false,
      tableData: [],
      currentModuleId: 0,
      currentPageNum: 1,
      currentPageSize: 10,
      totalItemCount: 0,
      interfaceDetail: {},
      gatewayApiPrefix: null,
      currentInterfaceId: 0,
      currentCommitId: 0,
      accessLogData: [],
      currentAccessPageNum: 1,
      currentAccessPageSize: 10,
      totalAccessItemCount: 0,
      showParamDialogVisible: false,
      requestParameters: null,
      showExceptDialogVisible: false,
      exeptionText: null,
      versionDialogVisible: false,
      versionList: [],
      selectCommitId: null,
    };
  },
  components: { JsonViewer },
  mounted () {
    window.addEventListener('resize', this.initResize);
    this.initResize();
  },
  methods: {
    handleClose () { },
    initResize () {
      if (this.$refs.box && this.$refs.box.clientWidth) {
        const width = this.$refs.box.clientWidth;
        const height = this.$refs.box.clientHeight;
        this.resizerHeight = height;
        this.leftWidth = Math.floor(width * 0.25) - 5;
        this.rightWidth = Math.floor(width * 0.75) - 5;
      }
    },
    startResize (event) {
      this.startX = event.clientX;
      document.addEventListener("mousemove", this.onResize);
      document.addEventListener("mouseup", this.stopResize);
    },
    onResize (event) {
      const diff = event.clientX - this.startX;
      this.leftWidth += diff;
      this.rightWidth -= diff;
      this.startX = event.clientX;
    },
    stopResize () {
      document.removeEventListener("mousemove", this.onResize);
      document.removeEventListener("mouseup", this.stopResize);
    },
    loadNode: function (node, resolve) {
      if (node.level > 1) {
        return resolve([]);
      }
      if (node.level === 0) {
        this.loadModuleListAll(resolve)
      } else {
        setTimeout(() => {
          this.loadInterfaceList(resolve, node.data.value)
        }, 500);
      }
    },
    loadModuleListAll: function (resolve) {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/module/listAll",
        data: JSON.stringify({
        })
      }).then(res => {
        if (0 === res.data.code) {
          let moduleList = []
          for (let element of res.data.data) {
            moduleList.push(
              {
                'label': element.name,
                'parent': 0,
                'value': element.id,
                'leaf': false,
              }
            )
          }
          resolve(moduleList)
        } else {
          resolve([])
          if (res.data.message) {
            alert(this.$t('service.loadModuleListFailed') + res.data.message);
          }
        }
      });
    },
    loadInterfaceList: function (resolve, id) {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/list",
        data: window.JSON.stringify(
          {
            moduleId: id,
            online: true,
            page: 1,
            size: 2147483647
          }
        )
      }).then(res => {
        if (0 === res.data.code) {
          let interfaceList = []
          for (let element of res.data.data) {
            interfaceList.push(
              {
                'label': element.name,
                'parent': id,
                'value': element.id,
                'commitId': element.commitId,
                'leaf': true,
              }
            )
          }
          resolve(interfaceList)
        } else {
          resolve([])
          alert(this.$t('service.loadInterfaceListFailed') + res.data.message);
        }
      }
      );
    },
    renderContent (h, { node, data, store }) {
      if (node.level === 1) {
        return (
          <div class="iconfont icon-wenjianxitong-86">
            <span class="tree-node-text">{data.label}</span>
          </div>
        );
      } else {
        return (
          <div class="iconfont icon-wenjianxitong3">
            <span class="tree-node-text">{data.label}</span>
          </div>
        );
      }
    },
    handleTreeNodeClick (data) {
      if (data.parent > 0) {
        this.currentInterfaceId = data.value
        this.currentCommitId = data.commitId
        this.showDetail = true
        this.reloadIntefaceDetail()
        this.reloadAccessLogList()
      } else {
        this.showDetail = false
        this.currentModuleId = data.value;
        this.reloadInterfaceList()
      }
    },
    reloadInterfaceList () {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/list",
        data: window.JSON.stringify(
          {
            moduleId: this.currentModuleId,
            online: true,
            page: this.currentPageNum,
            size: this.currentPageSize
          }
        )
      }).then(res => {
        if (0 === res.data.code) {
          this.tableData = []
          this.totalItemCount = res.data.pagination.total
          for (let element of res.data.data) {
            this.tableData.push(
              {
                'id': element.id,
                'name': element.name,
                'path': element.path,
                'method': element.method,
                'engine': element.engine,
                'createTime': element.createTime
              }
            )
          }
        }
      }
      );
    },
    loadGetwayApiPrefix: function () {
      this.$http({
        method: "GET",
        url: "/datapoly/manager/api/v1/node/prefix"
      }).then(
        res => {
          if (0 === res.data.code) {
            if (res.data.data && typeof res.data.data === 'string') {
              this.gatewayApiPrefix = res.data.data;
            }
          }
        }
      );
    },
    reloadIntefaceDetail: function () {
      if (!this.gatewayApiPrefix) {
        this.loadGetwayApiPrefix();
      }
      this.$http.get(
        "/datapoly/manager/api/v1/version/show/" + this.currentCommitId
      ).then(res => {
        if (0 === res.data.code) {
          let detail = res.data.data.detail;
          this.interfaceDetail = {
            id: detail.id,
            version: detail.version,
            commitId: detail.commitId,
            name: detail.name,
            description: detail.description,
            method: detail.method,
            path: this.gatewayApiPrefix + detail.path,
            contentType: detail.contentType,
            open: detail.open,
            group: detail.groupId,
            module: detail.moduleId,
            dataSourceId: detail.datasourceId,
            engine: detail.engine,
            inputParams: detail.params,
            outputParams: detail.outputs || [],
          }
        }
      });
    },
    reloadAccessLogList: function () {
      this.$http.get(
        "/datapoly/manager/api/v1/overview/log/" + this.currentInterfaceId
        + "?page=" + this.currentAccessPageNum + "&size=" + this.currentAccessPageSize
      ).then(res => {
        if (0 === res.data.code) {
          this.totalAccessItemCount = res.data.pagination.total
          this.accessLogData = res.data.data;
        }
      });
    },
    handleShowParam: function (index, row) {
      this.requestParameters = row.parameters;
      this.showParamDialogVisible = true;
    },
    handleShowException: function (index, row) {
      this.exeptionText = row.exception;
      this.showExceptDialogVisible = true;
    },
    handleSizeChange: function (pageSize) {
      this.currentPageSize = pageSize;
      this.reloadInterfaceList()
    },
    handleCurrentChange: function (currentPage) {
      this.currentPageNum = currentPage;
      this.reloadInterfaceList()
    },
    handleAccessSizeChange: function (pageSize) {
      this.currentAccessPageSize = pageSize;
      this.reloadAccessLogList()
    },
    handleAccessCurrentChange: function (currentPage) {
      this.currentAccessPageNum = currentPage;
      this.reloadAccessLogList()
    },
    boolTypeFormat (value) {
      if (value === true) {
        return this.$t('service.yes');
      } else {
        return this.$t('service.no');
      }
    },
    returnUnknownValue () {
      return this.$t('service.unknown');
    },
    enumTypeLocationFormat (value) {
      for (const item of this.paramLocation) {
        if (item.value === value) {
          return item.name;
        }
      }
      return this.returnUnknownValue();
    },
    enumTypeValueFormat (value) {
      for (const item of this.paramTypeList) {
        if (item.value === value) {
          return item.name;
        }
      }
      return this.returnUnknownValue();
    },
    handleSwitchVersion (detail) {
      this.$http({
        method: "GET",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/version/list/" + detail.id,
      }).then(res => {
        if (0 === res.data.code) {
          this.versionList = res.data.data;
          this.versionDialogVisible = true;
        } else {
          if (res.data.message) {
            alert(this.$t('service.getVersionListFailed') + res.data.message);
          }
        }
      });
    },
    handleDeployVersion () {
      if (!this.selectCommitId || this.selectCommitId <= 0) {
        this.$alert(this.$t('service.selectVersionFirst'), this.$t('service.parseError'),
          {
            confirmButtonText: this.$t('service.confirm'),
            type: "error"
          }
        );
        return;
      }
      if (this.selectCommitId === this.currentCommitId) {
        this.$alert(this.$t('service.sameVersionWarning'), this.$t('service.parseError'),
          {
            confirmButtonText: this.$t('service.confirm'),
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
        url: "/datapoly/manager/api/v1/assignment/deploy/" + this.interfaceDetail.id + "?commitId=" + this.selectCommitId,
      }).then(res => {
        if (0 === res.data.code) {
          this.currentCommitId = this.selectCommitId
          this.selectCommitId = null;
          this.versionDialogVisible = false;
          this.$message(this.$t('service.switchSuccess'));
          this.reloadIntefaceDetail();
        } else {
          if (res.data.message) {
            alert(this.$t('service.onlineFailed') + res.data.message);
          }
        }
      });
    }
  },
};
</script>

<style scoped>
.el-card {
  width: 100%;
  height: 100%;
  overflow: auto;
}

.tree-node-text {
  overflow: hidden; /* hide overflow */
  white-space: nowrap; /* prevent wrapping */
  text-overflow: ellipsis; /* show ellipsis on overflow */
}

.box {
  width: 100%;
  height: 100%;
  display: flex;
  vertical-align: top; /* align to top */
  align-items: flex-start;
}

.resizable {
  height: 100%;
  padding: 0px;
  display: inline-block;
}

.resizer {
  width: 5px;
  height: 200px;
  cursor: ew-resize;
  display: inline-block;
  border-left: 1px solid #dcdfe6;
  margin-left: 5px;
  margin-right: 2px;
}

.resizer:hover {
  background-color: #699eff;
}

.detail-row {
  font-size: 13px;
  padding: 2px;
}

.btn-style {
  color: #e9e9f3;
}

.btn-text {
  font-size: 12px;
  color: #6873ce;
}
</style>
