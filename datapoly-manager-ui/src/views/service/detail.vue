<template>
  <el-card>
    <div class="container">
      <div>
        <el-button type="primary"
                   size="mini"
                   @click="handleGoBack">
          <i class="el-icon-d-arrow-left">
            {{ $t('common2.back') }}</i>
        </el-button>
      </div>
      <el-tabs type="border-card" @tab-click="handleStatTabClick">
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
          <el-row class="log-filter-row">
            <el-col :span="6">
              <span class="filter-label">{{ $t('service.statusCode') }}:</span>
              <el-select v-model="logStatusCode"
                         :placeholder="$t('service.allStatus')"
                         size="small"
                         clearable
                         style="width: 130px"
                         @change="handleLogFilterChange">
                <el-option label="200" :value="200"></el-option>
                <el-option label="400" :value="400"></el-option>
                <el-option label="401" :value="401"></el-option>
                <el-option label="403" :value="403"></el-option>
                <el-option label="404" :value="404"></el-option>
                <el-option label="500" :value="500"></el-option>
                <el-option label="502" :value="502"></el-option>
                <el-option label="503" :value="503"></el-option>
              </el-select>
            </el-col>
            <el-col :span="10">
              <span class="filter-label">{{ $t('service.timeRange') }}:</span>
              <el-date-picker v-model="logStartTime"
                              type="datetime"
                              value-format="yyyy-MM-dd HH:mm:ss"
                              :placeholder="$t('service.startTime')"
                              size="small"
                              style="width: 170px"
                              @change="handleLogFilterChange">
              </el-date-picker>
              <span style="margin:0 6px;color:#999;">~</span>
              <el-date-picker v-model="logEndTime"
                              type="datetime"
                              value-format="yyyy-MM-dd HH:mm:ss"
                              :placeholder="$t('service.endTime')"
                              size="small"
                              style="width: 170px"
                              @change="handleLogFilterChange">
              </el-date-picker>
            </el-col>
            <el-col :span="4">
              <el-button size="small"
                         type="primary"
                         icon="el-icon-search"
                         @click="handleLogFilterSearch">{{ $t('common.search') }}</el-button>
              <el-button size="small"
                         @click="handleLogFilterReset">{{ $t('common.reset') }}</el-button>
            </el-col>
          </el-row>
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
        <el-tab-pane :label="$t('service.callStatistics')">
          <el-row class="stat-filter-row">
            <el-col :span="8">
              <span class="stat-label">{{ $t('service.timeRange') }}
                <el-tooltip :content="$t('service.timeRangeHint')" placement="top">
                  <i class="el-icon-question" style="color:#409eff;cursor:pointer;font-size:14px;margin-left:4px;"></i>
                </el-tooltip>
              </span>
              <el-select v-model="statDays"
                         @change="handleStatDaysChange"
                         :placeholder="$t('service.selectTimeRange')"
                         size="small"
                         style="width: 140px">
                <el-option v-for="item in optionDays"
                           :key="item.value"
                           :label="item.label"
                           :value="item.value">
                </el-option>
              </el-select>
            </el-col>
            <el-col :span="16">
            </el-col>
          </el-row>
          <el-row :gutter="12" class="chart-row">
            <el-col :span="12">
              <div id="statusRatioChart" class="chart-panel"></div>
            </el-col>
            <el-col :span="12">
              <div id="dailyTrendChart" class="chart-panel"></div>
            </el-col>
          </el-row>
          <el-row class="stat-filter-row">
            <el-col :span="8">
              <span class="stat-label">{{ $t('service.date') }}
                <el-tooltip :content="$t('service.dateHint')" placement="top">
                  <i class="el-icon-question" style="color:#409eff;cursor:pointer;font-size:14px;margin-left:4px;"></i>
                </el-tooltip>
              </span>
              <el-date-picker v-model="statDate"
                              type="date"
                              value-format="yyyy-MM-dd"
                              :placeholder="$t('service.selectDate')"
                              size="small"
                              @change="handleStatDateChange"
                              style="width: 180px">
              </el-date-picker>
            </el-col>
            <el-col :span="16">
            </el-col>
          </el-row>
          <el-row :gutter="12" class="chart-row">
            <el-col :span="24">
              <div id="hourlyTrendChart" class="chart-panel chart-panel-full"></div>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
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
      statDays: 7,
      statDate: null,
      optionDays: [
        { label: '1', value: 1 },
        { label: '3', value: 3 },
        { label: '7', value: 7 },
        { label: '30', value: 30 },
      ],
      statusRatioChart: null,
      dailyTrendChart: null,
      hourlyTrendChart: null,
      statusRatioData: {
        title: { text: '' },
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { orient: 'vertical', left: 'right' },
        color: ['#40c9c6', '#36a3f7', '#f4516c', '#34bfa3', '#e6a23c', '#9b59b6'],
        series: [{
          name: '',
          type: 'pie',
          radius: ['35%', '55%'],
          center: ['50%', '55%'],
          avoidLabelOverlap: false,
          label: { show: false },
          emphasis: {
            label: { show: true, fontSize: '14', fontWeight: 'bold' },
            itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' }
          },
          labelLine: { show: false },
          data: []
        }]
      },
      dailyTrendData: {
        title: { text: '' },
        tooltip: { trigger: 'axis' },
        legend: { data: [{ name: '', textStyle: { color: '#000' } }, { name: '', textStyle: { color: '#000' } }] },
        color: ['#36a3f7', '#40c9c6'],
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', boundaryGap: true, data: [], axisLabel: { interval: 0, textStyle: { color: '#000', fontSize: 10 }, margin: 8 }, axisLine: { show: true, lineStyle: { color: 'rgb(2,121,253)' } }, axisTick: { show: false } },
        yAxis: { type: 'value' },
        series: [
          { name: '', type: 'bar', barWidth: '30%', data: [] },
          { name: '', type: 'bar', barWidth: '30%', data: [] }
        ]
      },
      hourlyTrendData: {
        title: { text: '' },
        tooltip: { trigger: 'axis' },
        color: ['#36a3f7'],
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', boundaryGap: true, data: [], axisLabel: { interval: 0, textStyle: { color: '#000', fontSize: 10 } }, axisLine: { show: true, lineStyle: { color: 'rgb(2,121,253)' } } },
        yAxis: { type: 'value' },
        series: [{ name: '', type: 'bar', barWidth: '60%', data: [] }]
      },
      logStatusCode: null,
      logStartTime: null,
      logEndTime: null,
    };
  },
  components: { JsonViewer },
  methods: {
    handleClose () { },
    handleGoBack: function () {
      this.$router.go(-1);
    },
    loadGetwayApiPrefix: function () {
      this.$http({
        method: "GET",
        url: "/datapoly/manager/api/v1/node/prefix"
      }).then(
        res => {
          if (0 === res.data.code) {
            if (res.data.data && typeof res.data.data === 'string') {
              this.gatewayApiPrefix = res.data.data || "";
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
      var params = "?page=" + this.currentAccessPageNum + "&size=" + this.currentAccessPageSize;
      if (this.logStatusCode !== null && this.logStatusCode !== '') {
        params += "&statusCode=" + this.logStatusCode;
      }
      if (this.logStartTime) {
        params += "&startTime=" + this.logStartTime;
      }
      if (this.logEndTime) {
        params += "&endTime=" + this.logEndTime;
      }
      this.$http.get(
        "/datapoly/manager/api/v1/overview/log/" + this.currentInterfaceId + params
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
    handleLogFilterChange: function () {
      this.currentAccessPageNum = 1;
    },
    handleLogFilterSearch: function () {
      this.reloadAccessLogList();
    },
    handleLogFilterReset: function () {
      this.logStatusCode = null;
      this.logStartTime = null;
      this.logEndTime = null;
      this.currentAccessPageNum = 1;
      this.reloadAccessLogList();
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
      if (this.selectCommitId == this.currentCommitId) {
        this.$alert(this.$t('service.sameVersionWarning'), this.$t('service.parseError'),
          {
            confirmButtonText: this.$t('service.confirm'),
            type: "error"
          }
        );
        return;
      }
      console.log("selectCommitId=" + this.selectCommitId + ",currentCommitId=" + this.currentCommitId);
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
          this.reloadIntefaceDetail();
          this.$message(this.$t('service.switchSuccess'));
        } else {
          if (res.data.message) {
            alert(this.$t('service.onlineFailed') + res.data.message);
          }
        }
      });
    },
    loadStatusRatio: function () {
      this.$http.get("/datapoly/manager/api/v1/overview/api/"
        + this.currentInterfaceId + "/status-ratio/" + this.statDays)
        .then(res => {
          if (0 === res.data.code) {
            var list = res.data.data.map(item => ({ name: item.name, value: item.count }));
            this.statusRatioData.series[0].data = list;
            this.statusRatioChart.setOption(this.statusRatioData, true);
          }
        });
    },
    loadDailyTrend: function () {
      this.$http.get("/datapoly/manager/api/v1/overview/api/"
        + this.currentInterfaceId + "/daily-trend/" + this.statDays)
        .then(res => {
          if (0 === res.data.code) {
            var lists = res.data.data;
            var xAxisData = [];
            var y1AxisData = [];
            var y2AxisData = [];
            for (var i = 0; i < lists.length; i++) {
              xAxisData.push(lists[i].ofDate);
              y1AxisData.push(lists[i].total);
              y2AxisData.push(lists[i].success);
            }
            this.dailyTrendData.xAxis.data = xAxisData;
            this.dailyTrendData.series[0].data = y1AxisData;
            this.dailyTrendData.series[1].data = y2AxisData;
            this.dailyTrendChart.setOption(this.dailyTrendData, true);
          }
        });
    },
    loadHourlyTrend: function () {
      if (!this.statDate) {
        return;
      }
      this.$http.get("/datapoly/manager/api/v1/overview/api/"
        + this.currentInterfaceId + "/hourly-trend?date=" + this.statDate)
        .then(res => {
          if (0 === res.data.code) {
            var lists = res.data.data;
            var hourData = [];
            var countData = [];
            for (var h = 0; h < 24; h++) {
              hourData.push(h + ':00');
              var found = lists.find(function (item) { return item.hour === h; });
              countData.push(found ? found.count : 0);
            }
            this.hourlyTrendData.xAxis.data = hourData;
            this.hourlyTrendData.series[0].data = countData;
            this.hourlyTrendChart.setOption(this.hourlyTrendData, true);
          }
        });
    },
    loadCallStatistics: function () {
      this.loadStatusRatio();
      this.loadDailyTrend();
      this.loadHourlyTrend();
    },
    handleStatDaysChange: function () {
      this.loadStatusRatio();
      this.loadDailyTrend();
    },
    handleStatDateChange: function () {
      this.loadHourlyTrend();
    },
    initStatChartsLocale: function () {
      this.optionDays = [
        { label: this.$t('dashboard.1day'), value: 1 },
        { label: this.$t('dashboard.3days'), value: 3 },
        { label: this.$t('dashboard.7days'), value: 7 },
        { label: this.$t('dashboard.30days'), value: 30 },
      ];
      this.statusRatioData.title.text = this.$t('service.httpStatusRatio');
      this.statusRatioData.series[0].name = this.$t('service.httpStatusRatio');
      this.dailyTrendData.title.text = this.$t('service.dailyTrend');
      this.dailyTrendData.legend.data[0].name = this.$t('dashboard.total');
      this.dailyTrendData.legend.data[1].name = this.$t('dashboard.successCount');
      this.dailyTrendData.series[0].name = this.$t('dashboard.total');
      this.dailyTrendData.series[1].name = this.$t('dashboard.successCount');
      this.hourlyTrendData.title.text = this.$t('service.hourlyTrend');
      this.hourlyTrendData.series[0].name = this.$t('service.callCount');
    },
    handleStatTabClick: function () {
      this.$nextTick(() => {
        this.resizeStatCharts();
      });
    },
    resizeStatCharts: function () {
      var charts = [this.statusRatioChart, this.dailyTrendChart, this.hourlyTrendChart];
      for (var i = 0; i < charts.length; i++) {
        if (charts[i]) {
          charts[i].resize();
        }
      }
    },
  },
  created () {
    this.currentInterfaceId = this.$route.query.id;
    this.currentCommitId = this.$route.query.commitId;
    this.reloadIntefaceDetail();
    this.reloadAccessLogList();
  },
  mounted () {
    this.initStatChartsLocale();
    this.$nextTick(() => {
      var today = new Date();
      var y = today.getFullYear();
      var m = (today.getMonth() + 1).toString().padStart(2, '0');
      var d = today.getDate().toString().padStart(2, '0');
      this.statDate = y + '-' + m + '-' + d;
      this.statusRatioChart = this.$echarts.init(document.getElementById("statusRatioChart"));
      this.dailyTrendChart = this.$echarts.init(document.getElementById("dailyTrendChart"));
      this.hourlyTrendChart = this.$echarts.init(document.getElementById("hourlyTrendChart"));
      this.loadCallStatistics();
      window.addEventListener('resize', this.resizeStatCharts);
      this.$nextTick(() => this.resizeStatCharts());
      setTimeout(() => this.resizeStatCharts(), 300);
    });
  },
  computed: {
    currentLocale () {
      return this.$i18n.locale;
    }
  },
  watch: {
    currentLocale () {
      this.$nextTick(() => {
        this.initStatChartsLocale();
        this.loadCallStatistics();
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

.stat-filter-row {
  margin: 0 0 16px 0;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
}

.stat-label {
  font-size: 14px;
  margin-right: 8px;
}

.log-filter-row {
  margin: 0 0 12px 0;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
  display: flex;
  align-items: center;
}

.filter-label {
  font-size: 13px;
  color: #606266;
  margin-right: 6px;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-panel {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  width: 100%;
  height: 350px;
}

.chart-panel-full {
  height: 380px;
}
</style>
