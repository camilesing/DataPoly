<template>
  <el-card>
    <el-row>
      <el-col :span="2">
        <el-button type="primary"
                   size="mini"
                   @click="handleShowTree">
          {{ $t('common2.metadataView') }}
          <i class="el-icon-d-arrow-left"
             v-if="showTree"></i>
          <i class="el-icon-d-arrow-right"
             v-else></i>
        </el-button>
      </el-col>
      <el-col :span="22">
        <div style="float:right">
          <el-button type="danger"
                     v-if="showVersionDetail"
                     size="mini"
                     @click="handleExitShowVersionDetail">
            <i class="el-icon-question">
              {{ $t('common2.exitVersionView') }}</i>
          </el-button>

          <el-button type="warning"
                     size="mini"
                     @click="handleHelp">
            <i class="el-icon-question">
              {{ $t('common2.help') }}</i>
          </el-button>
          <el-button type="primary"
                     size="mini"
                     @click="handleGoBack">
            <i class="el-icon-d-arrow-left">
              {{ $t('common2.back') }}</i>
          </el-button>

          <el-button type="primary"
                     size="mini"
                     v-if="!isOnlyShowDetail"
                     icon="el-icon-arrow-left"
                     @click="handleDebug">
            {{ $t('common2.debug') }}
          </el-button>
          <el-button type="primary"
                     size="mini"
                     v-if="!isOnlyShowDetail"
                     icon="el-icon-arrow-left"
                     @click="handleSave">
            {{ $t('common2.save') }}
          </el-button>
          <el-button type="primary"
                     size="mini"
                     v-if="isOnlyShowDetail"
                     icon="el-icon-top"
                     @click="handleShowVersionList">
            {{ $t('common2.version') }}
          </el-button>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter=2>
      <el-col :span="4"
              v-if="showTree">
        <div class="grid-content bg-purple">
          <el-select :placeholder="$t('common2.selectDatasource')"
                     v-model="createParam.dataSourceId"
                     @change="loadTreeData">
            <el-option v-for="(item,index) in connectionList"
                       :key="index"
                       :label="`[${item.id}]${item.name}`"
                       :value="item.id"></el-option>
          </el-select>
          <el-tree ref="tree"
                   :empty-text="$t('common2.emptySelectDatasource')"
                   style="min-height: 500px; max-height: 800px; overflow: auto;"
                   :indent=6
                   :data="treeData"
                   :props="props"
                   :load="loadNode"
                   :expand-on-click-node="true"
                   :highlight-current="true"
                   :render-content="renderContent"
                   lazy>
          </el-tree>
        </div>
      </el-col>
      <el-col :span='showTree?20:24'>
        <div class="grid-content bg-purple">
          <el-form size="mini"
                   :model="createParam"
                   :rules="rules"
                   label-position='left'
                   ref="form">
            <el-tabs type="border-card"
                     v-model="tabActiveName">
              <el-tab-pane :label="$t('common2.sqlConfig')"
                           name="basic">
                <el-row>
                  <el-col :span="6">
                    <el-form-item :label="$t('common2.execute')"
                                  label-width="65px">
                      <el-radio-group size="small"
                                      @change="agreeEngineChange"
                                      v-model="createParam.engine">
                        <el-radio-button label="SQL"
                                         :disabled="$route.query.id>0 && createParam.engine==='SCRIPT'">{{ $t('common2.sqlStatement') }}</el-radio-button>
                        <el-radio-button label="SCRIPT"
                                         :disabled="$route.query.id>0 && createParam.engine==='SQL'">{{ $t('common2.groovyScript') }}</el-radio-button>
                      </el-radio-group>
                    </el-form-item>
                  </el-col>
                  <el-col :span="6">
                    <el-input-number v-model="editorHeightNum"
                                     size="small"
                                     :step="20"
                                     step-strictly></el-input-number>
                  </el-col>
                </el-row>
                <el-row v-if="createParam.engine==='SQL'">
                  <el-col :span="24">
                    <el-form-item label-width="65px">
                      <span slot="label"
                            style="display:inline-block;">
                        {{ $t('common2.statement') }}
                        <el-tooltip effect="dark"
                                    :content="$t('common2.parseInputParamsTip')"
                                    placement="bottom">
                          <i class='el-icon-question' />
                        </el-tooltip>
                      </span>
                      <multi-sql-editer ref="sqlEditors"
                                        :editorHeightNum="editorHeightNum"
                                        :tableHints="tableHints"
                                        :tabSqls="createParam.sqls"
                                        :canAddSql="!isOnlyShowDetail"></multi-sql-editer>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row v-if="createParam.engine==='SCRIPT'">
                  <el-col :span="24">
                    <el-form-item label-width="65px">
                      <span slot="label"
                            style="display:inline-block;">
                        {{ $t('common2.script') }}
                        <el-tooltip effect="dark"
                                    :content="$t('common2.parseScriptTip')"
                                    placement="bottom">
                          <i class='el-icon-question' />
                        </el-tooltip>
                      </span>
                      <script-editer ref="scriptEditer"
                                     :editorHeightNum="editorHeightNum"
                                     :content="createParam.script"></script-editer>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-tabs type="border-card"
                         tab-position="left">
                  <el-tab-pane :label="$t('common2.inputParams')">
                    <el-row>
                      <el-col :span="3"
                              v-if="createParam.engine==='SQL'">
                        <el-button type="primary"
                                   size="mini"
                                   icon="el-icon-arrow-down"
                                   v-if="!isOnlyShowDetail"
                                   @click="handleParseInputParams">
                          {{ $t('common2.parseInputParams') }}
                        </el-button>
                      </el-col>
                      <el-col :span="3">
                        <el-button type="primary"
                                   size="mini"
                                   icon="el-icon-arrow-down"
                                   v-if="!isOnlyShowDetail"
                                   @click="handleAddInputParams">
                          {{ $t('common2.addInputParams') }}
                        </el-button>
                      </el-col>
                      <el-col :span="3">
                        <el-button type="primary"
                                   size="mini"
                                   icon="el-icon-arrow-down"
                                   v-if="!isOnlyShowDetail"
                                   @click="handleAddPagableParams">
                          {{ $t('common2.pageParams') }}
                        </el-button>
                      </el-col>
                      <el-col :span="15">
                      </el-col>
                    </el-row>
                    <el-table :data="inputParams"
                              :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                              size="mini"
                              default-expand-all
                              row-key="id"
                              :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
                              border>
                      <template slot="empty">
                        <span>{{ $t('common2.afterSqlParse') }}</span>
                      </template>
                      <el-table-column :label="$t('common2.paramName')"
                                       min-width="35%">
                        <template slot-scope="scope">
                          <el-input v-model="scope.row.name"
                                    type="string"
                                    :disabled="isOnlyShowDetail"> </el-input>
                        </template>
                      </el-table-column>
                      <el-table-column :label="$t('common2.paramLocation')"
                                       min-width="25%">
                        <template slot-scope="scope">
                          <el-select v-model="scope.row.location"
                                     :disabled="isOnlyShowDetail">
                            <el-option label='header'
                                       value='REQUEST_HEADER'></el-option>
                            <el-option label='body'
                                       value='REQUEST_BODY'></el-option>
                            <el-option label='query'
                                       value='REQUEST_FORM'></el-option>
                          </el-select>
                        </template>
                      </el-table-column>
                      <el-table-column :label="$t('common2.paramType')"
                                       min-width="25%">
                        <template slot-scope="scope">
                          <el-select v-model="scope.row.type"
                                     :disabled="isOnlyShowDetail">
                            <el-option v-for="(item,index) in paramTypeList"
                                       :key="index"
                                       :label="item.name"
                                       :value="item.value"
                                       v-if="shouldInputShowOption(item,scope.row)"></el-option>
                          </el-select>
                        </template>
                      </el-table-column>
                      <el-table-column :label="$t('common2.isArray')"
                                       min-width="10%">
                        <template slot-scope="scope">
                          <el-checkbox v-model="scope.row.isArray"
                                       :disabled="isOnlyShowDetail"></el-checkbox>
                        </template>
                      </el-table-column>
                      <el-table-column :label="$t('common2.required')"
                                       min-width="10%">
                        <template slot-scope="scope">
                          <el-checkbox v-model="scope.row.required"
                                       :disabled="isOnlyShowDetail"></el-checkbox>
                        </template>
                      </el-table-column>
                      <el-table-column :label="$t('common2.defaultValue')"
                                       min-width="25%">
                        <template slot-scope="scope">
                          <el-input v-model="scope.row.defaultValue"
                                    type="string"
                                    :disabled="isOnlyShowDetail "></el-input>
                        </template>
                      </el-table-column>
                      <el-table-column :label="$t('common2.description')"
                                       min-width="25%">
                        <template slot-scope="scope">
                          <el-input v-model="scope.row.remark"
                                    type="string"
                                    :disabled="isOnlyShowDetail"></el-input>
                        </template>
                      </el-table-column>
                      <el-table-column :label="$t('common2.operation')"
                                       v-if="!isOnlyShowDetail"
                                       min-width="25%">
                        <template slot-scope="scope">
                          <el-link icon="el-icon-plus"
                                   v-if="scope.row.type=='OBJECT' && scope.row.location=='REQUEST_BODY'"
                                   @click="addInputSubParamsItem(scope.row)"></el-link>
                          &nbsp;&nbsp;&nbsp;&nbsp;
                          <el-link icon="el-icon-delete"
                                   @click="deleteInputParamsItem(scope.$index,scope.row)"></el-link>
                        </template>
                      </el-table-column>
                    </el-table>
                  </el-tab-pane>
                  <el-tab-pane :label="$t('common2.outputParams')">
                    <el-button type="primary"
                               size="mini"
                               icon="el-icon-arrow-down"
                               v-if="!isOnlyShowDetail"
                               @click="handleAddOutputParams">
                      {{ $t('common2.addOutputParams') }}
                    </el-button>
                    <el-table :data="outputParams"
                              :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                              size="mini"
                              default-expand-all
                              row-key="id"
                              :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
                              border>
                      <template slot="empty">
                        <span>{{ $t('common2.afterDebugSuccess') }}</span>
                      </template>
                      <el-table-column :label="$t('common2.paramName')"
                                       min-width="25%">
                        <template slot-scope="scope">
                          <el-input v-model="scope.row.name"
                                    type="string"
                                    :disabled="isOnlyShowDetail"> </el-input>
                        </template>
                      </el-table-column>
                      <el-table-column :label="$t('common2.paramType')"
                                       min-width="25%">
                        <template slot-scope="scope">
                          <el-select v-model="scope.row.type"
                                     :disabled="isOnlyShowDetail">
                            <el-option v-for="(item,index) in paramTypeList"
                                       :key="index"
                                       :label="item.name"
                                       :value="item.value"
                                       v-if="shouldOutputShowOption(item,scope.row)"></el-option>
                          </el-select>
                        </template>
                      </el-table-column>
                      <el-table-column :label="$t('common2.description')"
                                       min-width="25%">
                        <template slot-scope="scope">
                          <el-input v-model="scope.row.remark"
                                    type="string"
                                    :disabled="isOnlyShowDetail"></el-input>
                        </template>
                      </el-table-column>
                      <el-table-column :label="$t('common2.operation')"
                                       v-if="!isOnlyShowDetail"
                                       min-width="25%">
                        <template slot-scope="scope">
                          <el-link icon="el-icon-plus"
                                   v-if="scope.row.type=='OBJECT'"
                                   @click="addOutputSubParamsItem(scope.row)"></el-link>
                          &nbsp;&nbsp;&nbsp;&nbsp;
                          <el-link icon="el-icon-delete"
                                   @click="deleteOutputParamsItem(scope.$index,scope.row)"></el-link>
                        </template>
                      </el-table-column>
                    </el-table>
                  </el-tab-pane>
                </el-tabs>
              </el-tab-pane>
              <el-tab-pane :label="$t('common2.interfaceConfig')"
                           name="detail">
                <el-row>
                  <el-col :span="12">
                    <el-form-item :label="$t('common2.path')"
                                  label-width="65px"
                                  style="width:80%"
                                  :required=true
                                  prop="path">
                      <el-input v-model="createParam.path"
                                :disabled="isOnlyShowDetail || $route.query.id>0">
                        <template slot="prepend">{{gatewayApiPrefix}}</template>
                      </el-input>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item :label="$t('common2.method')"
                                  label-width="65px"
                                  style="width:50%"
                                  :required=true
                                  prop="method">
                      <el-select v-model="createParam.method"
                                 :disabled="isOnlyShowDetail || $route.query.id>0">
                        <el-option label="GET"
                                   value="GET"></el-option>
                        <el-option label="PUT"
                                   value="PUT"></el-option>
                        <el-option label="POST"
                                   value="POST"></el-option>
                        <el-option label="DELETE"
                                   value="DELETE"></el-option>
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row>
                  <el-col :span="12">
                    <el-form-item :label="$t('mcp.name')"
                                  label-width="65px"
                                  :required=true
                                  prop="name">
                      <el-input v-model="createParam.name"
                                auto-complete="off"
                                style="width:75%"
                                :disabled="isOnlyShowDetail"></el-input>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item :label="$t('common2.type')"
                                  style="width:80%"
                                  label-width="65px"
                                  :required=true
                                  prop="contentType">
                      <el-select v-model="createParam.contentType"
                                 :disabled="isOnlyShowDetail">
                        <el-option v-for="(item,index) in contentTypes"
                                   :key="index"
                                   :label="item"
                                   :value="item"></el-option>
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row>
                  <el-col :span="12">
                    <el-form-item :label="$t('common2.module')"
                                  label-width="65px"
                                  :required=true
                                  style="width:80%"
                                  prop="module">
                      <el-select v-model="createParam.module"
                                 :placeholder="$t('common2.pleaseSelect')"
                                 :disabled="isOnlyShowDetail">
                        <el-option v-for="(item,index) in moduleList"
                                   :key="index"
                                   :label="`[${item.id}]${item.name}`"
                                   :value="item.id"></el-option>
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item :label="$t('common2.auth')"
                                  label-width="65px"
                                  style="width:80%"
                                  :required=true
                                  prop="group">
                      <el-select v-model="createParam.group"
                                 :placeholder="$t('common2.pleaseSelect')"
                                 :disabled="isOnlyShowDetail">
                        <el-option v-for="(item,index) in groupList"
                                   :key="index"
                                   :label="`[${item.id}]${item.name}`"
                                   :value="item.id"></el-option>
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row>
                  <el-col :span="24">
                    <el-form-item :label="$t('common2.description')"
                                  label-width="60px"
                                  prop="description"
                                  style="width:100%">
                      <el-input type="textarea"
                                v-model="createParam.description"
                                auto-complete="off"></el-input>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-tab-pane>
              <el-tab-pane :label="$t('common2.outputFormat')"
                           name="outputParams">
                <el-row>
                  <el-col :span="12">
                    <div>
                      <el-form-item label-width="120px"
                                    prop="namingStrategy"
                                    style="width:60%">
                        <span slot="label"
                              style="display:inline-block;">
                          {{ $t('common2.namingStrategy') }}
                          <el-tooltip effect="dark"
                                      :content="$t('common2.namingStrategyTip')"
                                      placement="top">
                            <i class='el-icon-question' />
                          </el-tooltip>
                        </span>
                        <el-select v-model="createParam.namingStrategy"
                                   :placeholder="$t('common2.pleaseSelect')"
                                   :disabled="isOnlyShowDetail">
                          <el-option v-for="(item,index) in responseNamingStrategy"
                                     :key="index"
                                     :label="`[${item.key}]${item.value}`"
                                     :value="`${item.key}`"></el-option>
                        </el-select>
                      </el-form-item>
                    </div>
                  </el-col>
                </el-row>
                <el-row>
                  <el-col :span="12">
                    <el-form-item :label="$t('common2.dataFormat')"
                                  label-width="120px"
                                  prop="formatMap"
                                  style="width:60%">
                      <div v-for="item in createParam.formatMap"
                           :key="item.key"
                           v-bind="item">
                        {{item.remark}}:
                        <el-input type="text"
                                  :key="item.key"
                                  v-model="item.value"
                                  :value="item.value"> </el-input>
                      </div>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-tab-pane>
              <el-tab-pane :label="$t('common2.cacheConfig')"
                           name="cacheConfig">
                <el-form-item :label="$t('common2.cacheMethod')"
                              label-width="120px"
                              style="width:60%">
                  <el-select v-model="createParam.cacheKeyType"
                             style="width:40%"
                             :disabled="isOnlyShowDetail">
                    <el-option v-for="item in cacheKeyTypeList"
                               :key="item.value"
                               :label="item.name"
                               :value="item.value"></el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label-width="120px"
                              style="width:60%"
                              v-show="createParam.cacheKeyType==='SPEL'">
                  <span slot="label"
                        style="display:inline-block;">
                    {{ $t('common2.spelExpression') }}
                    <el-tooltip effect="dark"
                                :content="$t('common2.spelTip')"
                                placement="top">
                      <i class='el-icon-question' />
                    </el-tooltip>
                  </span>
                  <el-input v-model="createParam.cacheKeyExpr"
                            auto-complete="off"
                            style="width:75%"
                            :disabled="isOnlyShowDetail"></el-input>
                </el-form-item>
                <el-form-item :label="$t('common2.expireSeconds')"
                              label-width="120px"
                              style="width:60%"
                              v-show="createParam.cacheKeyType==='SPEL'|| createParam.cacheKeyType==='AUTO'">
                  <el-input-number v-model="createParam.cacheExpireSeconds"
                                   size="small"
                                   :min="10"
                                   :step="1"
                                   :disabled="isOnlyShowDetail"
                                   step-strictly></el-input-number>
                </el-form-item>
              </el-tab-pane>
              <el-tab-pane :label="$t('common2.authConfig')"
                           name="authen">
                <el-row>
                  <el-col :span="24">
                    <el-form-item :label="$t('common2.isPublic')">
                      <el-switch v-model="createParam.open"
                                 active-color="#13ce66"
                                 :active-value="true"
                                 :inactive-value="false"
                                 :active-text="$t('setting.open')"
                                 :inactive-text="$t('setting.closeText')"
                                 :disabled="isOnlyShowDetail">
                      </el-switch>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-tab-pane>
              <el-tab-pane :label="$t('common2.alarmConfig')"
                           name="alarm">
                <el-row>
                  <el-col :span="24">
                    <el-form-item :label="$t('common2.isAlarm')">
                      <el-switch v-model="createParam.alarm"
                                 active-color="#13ce66"
                                 :active-value="true"
                                 :inactive-value="false"
                                 :active-text="$t('setting.open')"
                                 :inactive-text="$t('setting.closeText')"
                                 :disabled="isOnlyShowDetail">
                      </el-switch>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-tab-pane>
              <el-tab-pane :label="$t('common2.flowControl')"
                           name="flowControl">
                <el-row>
                  <el-col :span="24">
                    <el-form-item :label="$t('common2.isFlowControl')">
                      <el-switch v-model="createParam.flowStatus"
                                 active-color="#13ce66"
                                 :active-value="true"
                                 :inactive-value="false"
                                 :active-text="$t('setting.open')"
                                 :inactive-text="$t('setting.closeText')"
                                 :disabled="isOnlyShowDetail">
                      </el-switch>
                    </el-form-item>
                    <div v-show="createParam.flowStatus">
                      <el-form-item :label="$t('common2.thresholdType')">
                        <el-radio-group size="small"
                                        v-model="createParam.flowGrade"
                                        :disabled="isOnlyShowDetail"
                                        border>
                          <el-radio :label="1">{{ $t('common2.qps') }}</el-radio>
                          <el-radio :label="0">{{ $t('common2.concurrentThreads') }}</el-radio>
                        </el-radio-group>
                      </el-form-item>
                      <el-form-item :label="$t('common2.singleMachineThreshold')">
                        <el-input-number v-model="createParam.flowCount"
                                         size="small"
                                         :step="1"
                                         :disabled="isOnlyShowDetail"
                                         step-strictly></el-input-number>
                      </el-form-item>
                    </div>
                  </el-col>
                </el-row>
              </el-tab-pane>
            </el-tabs>
          </el-form>
        </div>
      </el-col>
    </el-row>

    <el-drawer :title="$t('common2.debug')"
               :visible.sync="showDebugDrawer"
               direction="ltr"
               size="65%"
               :with-header="true">
      <el-card>
        <el-row>
          <el-col :span="24">
            <el-table :data="debugParams"
                      :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                      row-key="id"
                      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
                      default-expand-all
                      size="mini"
                      border>
              <el-table-column :label="$t('common2.paramName')"
                               min-width="35%">
                <template slot-scope="scope">
                  <el-input v-model="scope.row.name"
                            :disabled="true"
                            type="string"> </el-input>
                </template>
              </el-table-column>
              <el-table-column :label="$t('common2.paramType')"
                               min-width="25%">
                <template slot-scope="scope">
                  <el-select v-model="scope.row.type"
                             :disabled="true">
                    <el-option v-for="(item,index) in paramTypeList"
                               :key="index"
                               :label="item.name"
                               :value="item.value"></el-option>
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column :label="$t('common2.isArray')"
                               min-width="10%">
                <template slot-scope="scope">
                  <el-checkbox v-model="scope.row.isArray"
                               :disabled="true"></el-checkbox>
                </template>
              </el-table-column>
              <el-table-column :label="$t('common2.required')"
                               min-width="10%">
                <template slot-scope="scope">
                  <el-checkbox v-model="scope.row.required"
                               :disabled="true"></el-checkbox>
                </template>
              </el-table-column>
              <el-table-column :label="$t('common2.description')"
                               min-width="25%">
                <template slot-scope="scope">
                  <el-input v-model="scope.row.remark"
                            :disabled="true"
                            type="string"></el-input>
                </template>
              </el-table-column>
              <el-table-column :label="$t('common2.defaultValue')"
                               min-width="50%">
                <template slot-scope="scope">
                  <div v-if="scope.row.isArray">
                    <el-row v-if="scope.row.type=='OBJECT'">
                      <el-input v-model="scope.row.value"
                                :disabled="true"
                                type="string"></el-input>
                    </el-row>
                    <el-row v-else
                            :gutter="24">
                      <div style="display: inline-flex;flex-direction: row ;justify-content: left;align-items: center"
                           v-for="(arrayItemValue,arrayItemIndex) in scope.row.arrayValues"
                           :key="arrayItemIndex">
                        <el-col :span="4"><button @click="delArrayValuesItem(scope.row.arrayValues,arrayItemIndex)">-</button></el-col>
                        <el-col :span="16"><el-input v-model="scope.row.arrayValues[arrayItemIndex]"
                                    :disabled="scope.row.type=='OBJECT'"
                                    type="string"></el-input></el-col>
                      </div>
                      <div style="display: inline-flex;flex-direction: row ;justify-content: left;align-items: center">
                        <el-col :span="4"><button @click="addArrayValuesItem(scope.row)">+</button></el-col>
                      </div>
                    </el-row>
                  </div>
                  <div v-else>
                    <el-input v-model="scope.row.value"
                              :disabled="scope.row.type=='OBJECT'"
                              type="string"></el-input>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-col>
        </el-row>
        <el-row>
          <el-col>
            <el-tooltip effect="dark"
                        :content="$t('common2.objectArrayDebugTip')"
                        placement="bottom">
              <i class='el-icon-question' />
            </el-tooltip>
            <div style="float: right; padding: 25px">
              <el-button type="primary"
                         size="mini"
                         icon="el-icon-arrow-left"
                         @click="handleExecuteDebug">
                {{ $t('common2.executeDebug') }}
              </el-button>
            </div>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-tabs type="border-card">
              <el-tab-pane :label="$t('common2.executeResult')">
                <json-viewer :value="debugResponse"
                             :expand-depth=5
                             copyable
                             boxed
                             sort></json-viewer>
              </el-tab-pane>
              <el-tab-pane :label="$t('common2.executeInfo')">
                <div class="debug-console-log-text">
                  {{debugConsoleLog}}<br />
                </div>
              </el-tab-pane>
            </el-tabs>
          </el-col>
        </el-row>
      </el-card>
    </el-drawer>

    <el-drawer :title="$t('common2.versionList')"
               :visible.sync="showVersionDrawer"
               direction="ltr"
               size="40%"
               :with-header="true">
      <el-card>
        <el-table :header-cell-style="{background:'#eef1f6',color:'#606266'}"
                  :data="versionList"
                  size="small"
                  border>
          <el-table-column :label="$t('common2.version')"
                           min-width="10%">
            <template slot-scope="scope">
              V{{ scope.row.version }}
            </template>
          </el-table-column>
          <el-table-column prop="online"
                           :label="$t('common2.online')"
                           :formatter="boolFormatOnline"
                           min-width="10%"></el-table-column>
          <el-table-column prop="createTime"
                           :label="$t('common2.time')"
                           min-width="30%"> </el-table-column>
          <el-table-column prop="description"
                           :label="$t('common2.description')"
                           show-overflow-tooltip
                           min-width="30%"></el-table-column>
          <el-table-column :label="$t('common2.view')"
                           min-width="10%">
            <template slot-scope="scope">
              <el-link icon="el-icon-view"
                       @click="handleShowVersionDetail(scope.$index, scope.row)"></el-link>
            </template>
          </el-table-column>
          <el-table-column :label="$t('common2.rollback')"
                           min-width="10%">
            <template slot-scope="scope">
              <el-link icon="el-icon-male"
                       @click="handleRevertVersionDetail(scope.$index, scope.row)"></el-link>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-drawer>

  </el-card>
</template>

<script>
import multiSqlEditer from '@/components/codeEditer/multiSqlEditer'
import scriptEditer from '@/components/codeEditer/scriptEditer'
import urlencode from "urlencode";
import qs from "qs";
import JsonViewer from 'vue-json-viewer';
import Vue from "vue";

export default {
  name: "common",
  data () {
    return {
      tabActiveName: 'basic',
      groupList: [],
      moduleList: [],
      connectionList: [],
      paramTypeList: [],
      contentTypes: ['application/x-www-form-urlencoded', 'application/json'],
      cacheKeyTypeList: [],
      showTree: true,
      editorHeightNum: 300,
      createParam: {
        id: null,
        name: null,
        description: null,
        dataSourceId: null,
        group: null,
        module: null,
        method: null,
        path: null,
        contentType: null,
        engine: 'SQL',
        sqls: [],
        script: '',
        open: false,
        namingStrategy: 'CAMEL_CASE',
        formatMap: null,
        open: false,
        alarm: false,
        flowStatus: false,
        flowGrade: 1,
        flowCount: 5,
        cacheKeyType: 'NONE',
        cacheKeyExpr: '',
        cacheExpireSeconds: '300',
      },
      showDebugDrawer: false,
      gatewayApiPrefix: 'http://127.0.0.1:8081/api/',
      treeData: [],
      props: {
        label: 'label',
        children: 'children',
        disabled: false,
        isLeaf: false
      },
      tableHints: {
        'mysql': ['user']
      },
      keywordHints: [],
      inputParams: [],
      debugParams: [],
      debugResponse: {},
      debugConsoleLog: "",
      outputParams: [],
      responseNamingStrategy: [],
      responseTypeFormat: [],
      showVersionDrawer: false,
      versionList: [],
      showVersionDetail: false,
      rules: {
        name: [
          {
            required: true,
            message: this.$t('common2.nameCannotEmpty'),
            trigger: "blur"
          }
        ],
        dataSourceId: [
          {
            required: true,
            message: this.$t('common2.datasourceRequired'),
            trigger: "change"
          }
        ],
        group: [
          {
            required: true,
            message: this.$t('common2.groupRequired'),
            trigger: "change"
          }
        ],
        module: [
          {
            required: true,
            message: this.$t('common2.moduleRequired'),
            trigger: "change"
          }
        ],
        method: [
          {
            required: true,
            message: this.$t('common2.methodRequired'),
            trigger: "change"
          }
        ],
        path: [
          {
            required: true,
            message: this.$t('common2.pathRequired'),
            trigger: "blur"
          }
        ],
        contentType: [
          {
            required: true,
            message: this.$t('common2.contentTypeRequired'),
            trigger: "change"
          }
        ],
      },
    }
  },
  props: {
    isOnlyShowDetail: {
      type: Boolean,
      default: false
    }
  },
  components: { multiSqlEditer, scriptEditer, JsonViewer },
  methods: {
    initParamTypeList () {
      this.paramTypeList = [
        { name: this.$t('common2.integer'), value: "LONG" },
        { name: this.$t('common2.float'), value: "DOUBLE" },
        { name: this.$t('common2.string'), value: "STRING" },
        { name: this.$t('common2.date'), value: "DATE" },
        { name: this.$t('common2.time'), value: "TIME" },
        { name: this.$t('common2.boolean'), value: "BOOLEAN" },
        { name: this.$t('common2.object'), value: "OBJECT" }
      ];
    },
    initCacheKeyTypeList () {
      this.cacheKeyTypeList = [
        { name: this.$t('common2.disabled'), value: "NONE" },
        { name: "AUTO", value: "AUTO" },
        { name: "SpEL", value: "SPEL" },
      ];
    },
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
    isUpdatePage: function () {
      if (this.$route.query.id) {
        return true;
      }
      return false;
    },
    applyAssignmentDetail: function (detail) {
      let mergedFormatMap = this.mergeFormatMap(detail.formatMap);
      this.createParam = {
        id: detail.id,
        name: detail.name,
        description: detail.description,
        method: detail.method,
        path: detail.path,
        contentType: detail.contentType,
        open: detail.open,
        group: detail.groupId,
        module: detail.moduleId,
        dataSourceId: detail.datasourceId,
        engine: detail.engine,
        sqls: [],
        script: "",
        namingStrategy: detail.namingStrategy,
        formatMap: mergedFormatMap,
        open: detail.open,
        alarm: detail.alarm,
        flowStatus: detail.flowStatus,
        flowGrade: detail.flowGrade,
        flowCount: detail.flowCount,
        cacheKeyType: detail.cacheKeyType,
        cacheKeyExpr: detail.cacheKeyExpr,
        cacheExpireSeconds: detail.cacheExpireSeconds,
      }
      this.inputParams = []
      if (detail.params) {
        this.inputParams = detail.params
        for (let item of this.inputParams) {
          if (!item.id) {
            Vue.set(item, 'id', this.uuid());
          }
        }
      }
      this.outputParams = detail.outputs || [];

      if (detail.sqlList && detail.sqlList.length > 0) {
        if (this.createParam.engine === 'SQL') {
          if (this.$refs.sqlEditors) {
            this.$refs.sqlEditors.resetEditor();
          }
          this.createParam.sqls = detail.sqlList.map(obj => obj['sqlText'])
        } else {
          this.createParam.script = detail.sqlList[0].sqlText
          if (this.$refs.scriptEditer) {
            this.$refs.scriptEditer.resetEditor(this.createParam.script)
          }
        }
      }
    },
    loadAssignmentDetail: function () {
      if (!this.isUpdatePage()) {
        return;
      }
      this.$http.get(
        "/datapoly/manager/api/v1/assignment/detail/" + this.$route.query.id
      ).then(res => {
        if (0 === res.data.code) {
          this.showTree = false;
          let detail = res.data.data;
          this.applyAssignmentDetail(detail);
        } else {
          if (res.data.message) {
            alert(this.$t('common2.queryFailed') + res.data.message);
          }
        }
      });
    },
    loadConnections: function () {
      this.connectionList = [];
      this.$http({
        method: "GET",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/datasource/list/name",
      }).then(
        res => {
          if (0 === res.data.code) {
            this.connectionList = res.data.data || [];
          } else {
            if (res.data.message) {
              alert(this.$t('common2.loadDataFailed') + res.data.message);
              this.connectionList = [];
            }
          }
        }
      );
    },
    loadGroups: function () {
      this.groupList = [];
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
            this.groupList = res.data.data || [];
          } else {
            if (res.data.message) {
              alert(this.$t('common2.loadDataFailed') + res.data.message);
              this.groupList = [];
            }
          }
        }
      );
    },
    loadModules: function () {
      this.moduleList = [];
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
            this.moduleList = res.data.data || [];
          } else {
            if (res.data.message) {
              alert(this.$t('common2.loadDataFailed') + res.data.message);
              this.moduleList = [];
            }
          }
        }
      );
    },
    loadGateway: function () {
      this.$http({
        method: "GET",
        url: "/datapoly/manager/api/v1/node/prefix"
      }).then(
        res => {
          if (0 === res.data.code) {
            if (res.data.data && typeof res.data.data === 'string') {
              this.gatewayApiPrefix = res.data.data;
            }
          } else {
            if (res.data.message) {
              alert(this.$t('common2.loadDataFailed') + res.data.message);
            }
          }
        }
      );
    },
    loadKeywordHints: function () {
      this.$http.get(
        "/datapoly/manager/api/v1/assignment/completions"
      ).then(res => {
        if (0 === res.data.code) {
          this.keywordHints = res.data.data;
        }
      });
    },
    loadResponseNamingStrategy: function () {
      this.$http.get(
        "/datapoly/manager/api/v1/assignment/response-naming-strategy"
      ).then(res => {
        if (0 === res.data.code) {
          this.responseNamingStrategy = res.data.data;
        }
      });
    },
    loadResponseTypeFormat: function () {
      return this.$http.get(
        "/datapoly/manager/api/v1/assignment/response-type-format"
      ).then(res => {
        if (0 === res.data.code) {
          this.responseTypeFormat = res.data.data;
          if (!this.createParam.formatMap) {
            this.createParam.formatMap = res.data.data;
          }
        }
      });
    },
    mergeFormatMap: function (existingFormatMap) {

      if (!existingFormatMap || existingFormatMap.length === 0) {
        return this.responseTypeFormat || [];
      }

      if (!this.responseTypeFormat || this.responseTypeFormat.length === 0) {
        return existingFormatMap;
      }

      const merged = [...this.responseTypeFormat];
      const existingMap = {};

      existingFormatMap.forEach(item => {
        existingMap[item.key] = item.value;
      });

      merged.forEach(item => {
        if (existingMap.hasOwnProperty(item.key)) {
          item.value = existingMap[item.key];
        }
      });
      return merged;
    },
    loadTreeData: function () {
      if (this.createParam.dataSourceId && this.createParam.dataSourceId > 0 && this.showTree) {
        this.treeData = []
        setTimeout(() => {
          this.$http({
            method: "GET",
            url: "/datapoly/manager/api/v1/datasource/schemas/get/" + this.createParam.dataSourceId
          }).then(
            res => {
              if (0 === res.data.code) {
                for (let element of res.data.data) {
                  let obj = new Object();
                  obj['label'] = element;
                  obj['parent'] = null;
                  obj['value'] = element;
                  obj['hasChild'] = true;
                  obj['type'] = 'DATABASE';
                  this.treeData.push(obj);
                }
              } else {
                this.$alert(this.$t('common2.loadFailed') + res.data.message, this.$t('common2.dataLoadFailed'));
              }
            }
          );
        }, 500);
      }
    },
    loadNode: function (node, resolve) {
      setTimeout(() => {
        if (node.level === 1) {
          let tableView = [
            {
              'label': this.$t('common2.typeTable'),
              'parent': this.createParam.dataSourceId,
              'value': node.label,
              'hasChild': true,
              'type': 'TABLE',
            },
            {
              'label': this.$t('common2.typeView'),
              'parent': this.createParam.dataSourceId,
              'value': node.label,
              'hasChild': true,
              'type': 'VIEW',
            }
          ]
          resolve(tableView);
        } else if (node.level === 2) {
          this.loadTablesList(resolve, this.createParam.dataSourceId, node.data.value, node.data.type)
        } else if (node.level === 3) {
          this.loadColumnList(resolve, this.createParam.dataSourceId, node.data.value, node.data.label)
        } else {
          resolve([]);
        }
      }, 500);
    },
    loadTablesList: function (resolve, id, schema, type) {
      var tableType = 'VIEW' === type ? 'views' : 'tables'
      this.$http({
        method: "GET",
        url: "/datapoly/manager/api/v1/datasource/" + tableType + "/get/" + id + "?schema=" + urlencode(schema)
      }).then(
        res => {
          if (0 === res.data.code) {
            let tableList = []
            let nameList = []
            for (let element of res.data.data) {
              let obj = new Object();
              obj['label'] = element;
              obj['parent'] = id;
              obj['value'] = schema;
              obj['hasChild'] = true;
              obj['type'] = type;
              tableList.push(obj);
              nameList.push(element)
            }

            if (this.tableHints[schema] && Array.isArray(this.tableHints[schema])) {
              this.tableHints[schema].push(nameList)
            } else {
              this.tableHints[schema] = nameList;
            }
            if (this.$refs.sqlEditors) {
              this.$refs.sqlEditors.setTableHints(this.tableHints)
            }
            return resolve(tableList);
          } else {
            this.$alert(this.$t('common2.loadFailed') + res.data.message, this.$t('common2.dataLoadFailed'));
          }
        }
      );
    },
    loadColumnList: function (resolve, id, schema, table) {
      this.$http({
        method: "GET",
        url: "/datapoly/manager/api/v1/datasource/columns/get/" + id + "?schema=" + urlencode(schema) + "&table=" + urlencode(table)
      }).then(
        res => {
          if (0 === res.data.code) {
            let columnList = []
            for (let element of res.data.data) {
              let obj = new Object();
              obj['label'] = element.name;
              obj['parent'] = table;
              obj['value'] = element.name;
              obj['hasChild'] = false;
              obj['type'] = element.type;
              columnList.push(obj);
            }
            return resolve(columnList);
          } else {
            this.$alert(this.$t('common2.loadFailed') + res.data.message, this.$t('common2.dataLoadFailed'));
          }
        }
      );
    },
    renderContent (h, { node, data, store }) {
      if (node.level === 1) {
        return (
          <div class="custom-tree-node">
            <i class="iconfont icon-shujuku1"></i>
            <el-tooltip class="item" effect="light" placement="left">
              <div slot="content">{node.label}</div>
              <span>{data.label}</span>
            </el-tooltip>
          </div>
        );
      } else if (node.level === 2) {
        var icon_pic = "iconfont icon-shitu_biaoge";
        if (data.type === 'VIEW') {
          icon_pic = "iconfont icon-viewList"
        }
        return (
          <div class="custom-tree-node">
            <i class={icon_pic}></i>
            <span>{data.label}</span>
          </div>
        );
      } else if (node.level === 3) {
        var icon_pic = "iconfont icon-shitu_biaoge";
        if (data.type === 'VIEW') {
          icon_pic = "iconfont icon-viewList"
        }
        return (
          <div class="custom-tree-node">
            <i class={icon_pic}></i>
            <el-tooltip class="item" effect="light" placement="left">
              <div slot="content">{node.label}</div>
              <span>{data.label}</span>
            </el-tooltip>
          </div>
        );
      } else {
        return (
          <div class="custom-tree-node">
            <i class="el-icon-set-up"></i>
            <el-tooltip class="item" effect="light" placement="left">
              <div slot="content">{data.type}</div>
              <span>{data.label}({data.type})</span>
            </el-tooltip>
          </div>
        );
      }

    },
    handleNodeClick: function () {

    },
    handleHelp: function () {
      const url = 'https://mybatis.org/mybatis-3/zh/dynamic-sql.html';
      window.open(url, '_blank');
    },
    handleGoBack: function () {
      this.$router.go(-1);
    },
    handleShowTree: function () {
      let status = !this.showTree;
      this.showTree = status;
      if (this.showTree) {
        this.loadTreeData();
      }
    },
    handleParseInputParams: function () {
      var currTabSql = this.$refs.sqlEditors.queryCurrentTabSql()
      if (/^\s*$/.test(currTabSql)) {
        alert(this.$t('common2.sqlCannotEmpty'))
        return
      }
      this.$http({
        method: "POST",
        url: "/datapoly/manager/api/v1/assignment/parse",
        data: qs.stringify({
          sql: currTabSql
        }),
      }).then(
        res => {
          if (0 === res.data.code) {
            if (res.data.data && res.data.data.length === 0) {
              this.$alert(this.$t('common2.parseInputEmpty'), this.$t('common2.parseError'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "error"
                }
              );
              return
            }
            for (let item of res.data.data) {
              if (!this.inputParams.find(i => i.name === item.name)) {
                let type = "STRING";
                let children = [];
                if (item.children) {
                  for (let child of item.children) {
                    children.push(
                      {
                        id: this.uuid(),
                        name: child.name,
                        location: 'REQUEST_BODY',
                        type: "STRING",
                        isArray: child.isArray,
                        required: true,
                        defaultValue: "",
                        remark: "",
                      }
                    );
                    type = "OBJECT";
                  }
                }
                this.inputParams.push(
                  {
                    id: this.uuid(),
                    name: item.name,
                    location: 'REQUEST_BODY',
                    type: type,
                    isArray: item.isArray,
                    required: true,
                    defaultValue: "",
                    remark: "",
                    children: children,
                  }
                )
              }
            };
          } else {
            if (res.data.message) {
              this.$alert(res.data.message, this.$t('common2.parseError'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "error"
                }
              );
            }
          }
        }
      );
    },
    agreeEngineChange: function () {
      if (this.createParam.engine === 'SCRIPT') {
        this.$refs.scriptEditer.setTableHints(this.keywordHints);
      }
    },
    handleAddInputParams: function () {
      this.inputParams.push(
        {
          id: this.uuid(),
          name: "",
          location: 'REQUEST_BODY',
          type: "STRING",
          isArray: false,
          required: true,
          defaultValue: "",
          remark: ""
        }
      )
    },
    handleAddPagableParams: function () {
      var add = false
      if (!this.inputParams.find(item => item.name === 'apiPageNum')) {
        add = true
        this.inputParams.push(
          {
            id: this.uuid(),
            name: "apiPageNum",
            type: "LONG",
            location: 'REQUEST_FORM',
            isArray: false,
            required: true,
            defaultValue: "1",
            remark: this.$t('common2.pageNum')
          }
        );
      }
      if (!this.inputParams.find(item => item.name === 'apiPageSize')) {
        add = true
        this.inputParams.push(
          {
            id: this.uuid(),
            name: "apiPageSize",
            type: "LONG",
            location: 'REQUEST_FORM',
            isArray: false,
            required: true,
            defaultValue: "10",
            remark: this.$t('common2.pageSize')
          },
        )
      }
      if (!add) {
        this.$alert(this.$t('common2.pageParamsExist'), this.$t('common.warning'),
          {
            confirmButtonText: this.$t('common.confirm'),
            type: "info"
          }
        );
      }
    },
    shouldInputShowOption: function (item, row) {
      if (this.getInputParamsParentRow(row)) {
        return item.value != 'OBJECT';
      }
      return true;
    },
    getInputParamsParentRow: function (childRow) {
      for (const row of this.inputParams) {
        if (row.children && row.children.includes(childRow)) {
          return row;
        }
      }
      return null;
    },
    deleteInputParamsItem: function (idx, row) {
      const index = this.inputParams.indexOf(row);
      if (index !== -1) {
        this.inputParams.splice(index, 1);
      } else {
        this.deleteInputSubParamsItem(idx, row);
      }
    },
    deleteInputSubParamsItem: function (index, childRow) {
      const parentRow = this.getInputParamsParentRow(childRow);
      if (parentRow) {
        const childIndex = parentRow.children.indexOf(childRow);
        if (childIndex !== -1) {
          parentRow.children.splice(childIndex, 1);
        } else {
          console.warn('Child not found');
        }
      }
    },
    addInputSubParamsItem: function (row) {
      const index = this.inputParams.findIndex(item => row == item)
      if (index !== -1) {
        if (!this.inputParams[index].children) {
          Vue.set(this.inputParams[index], 'children', []);
        }
        this.inputParams[index].location = 'REQUEST_BODY';
        this.inputParams[index].type = 'OBJECT';
        this.inputParams[index].children.push(
          {
            id: this.uuid(),
            name: "",
            type: "STRING",
            location: 'REQUEST_BODY',
            isArray: false,
            required: true,
            defaultValue: "",
            remark: ""
          },
        );
      } else {
        row.type = 'STRING';
        this.$alert(this.$t('common2.selectNested'), this.$t('common.warning'),
          {
            confirmButtonText: this.$t('common.confirm'),
            type: "info"
          }
        );
      }
    },
    checkSqlsOrScriptEmpty: function (sqls) {
      if (sqls === null || sqls === undefined || !Array.isArray(sqls) || sqls.length === 0 || sqls.includes('')) {
        return true
      }
      for (let str in sqls) {
        if (str === null || str === undefined || str.trim() === '' || str.trim().length === 0) {
          return true
        }
      }
      return false
    },
    handleSave: function () {
      this.$refs.form.validate(valid => {
        if (valid) {
          var sqls = []
          var isSql = true;
          if (this.createParam.engine === 'SQL') {
            isSql = true
            sqls = this.$refs.sqlEditors.queryContent()
          } else {
            isSql = false
            sqls = this.$refs.scriptEditer.queryContent()
          }

          if (!this.createParam.dataSourceId) {
            this.$alert(this.$t('common2.selectDatasourceTip'), this.$t('common2.parseError'),
              {
                confirmButtonText: this.$t('common.confirm'),
                type: "error"
              }
            );
            return
          }

          if (this.checkSqlsOrScriptEmpty(sqls)) {
            this.$alert(isSql ? this.$t('common2.checkSqlContent') : this.$t('common2.checkScriptContent'), this.$t('common2.parseError'),
              {
                confirmButtonText: this.$t('common.confirm'),
                type: "error"
              }
            );
          } else {
            if (this.isUpdatePage()) {
              this.handleUpdateSave(sqls);
            } else {
              this.handleCreateSave(sqls);
            }
          }
        } else {
          alert(this.$t('common2.checkInput'));
        }
      });
    },
    handleCreateSave: function (sqls) {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/create",
        data: JSON.stringify({
          groupId: this.createParam.group,
          moduleId: this.createParam.module,
          datasourceId: this.createParam.dataSourceId,
          name: this.createParam.name,
          description: this.createParam.description,
          method: this.createParam.method,
          contentType: this.createParam.contentType,
          path: this.createParam.path,
          open: this.createParam.open,
          namingStrategy: this.createParam.namingStrategy,
          formatMap: this.createParam.formatMap,
          open: this.createParam.open,
          alarm: this.createParam.alarm,
          flowStatus: this.createParam.flowStatus,
          flowGrade: this.createParam.flowGrade,
          flowCount: this.createParam.flowCount,
          cacheKeyType: this.createParam.cacheKeyType,
          cacheKeyExpr: this.createParam.cacheKeyExpr,
          cacheExpireSeconds: this.createParam.cacheExpireSeconds,
          engine: this.createParam.engine,
          contextList: sqls,
          params: this.inputParams,
          outputs: this.outputParams
        })
      }).then(
        res => {
          if (0 === res.data.code) {
            this.$router.push({ path: '/interface/list' });
            this.$message(this.$t('common2.addSuccess'));
          } else {
            if (res.data.message) {
              this.$alert(res.data.message, this.$t('common2.parseError'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "error"
                }
              );
            }
          }
        }
      );
    },
    handleUpdateSave: function (sqls) {
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/update",
        data: JSON.stringify({
          id: this.createParam.id,
          groupId: this.createParam.group,
          moduleId: this.createParam.module,
          datasourceId: this.createParam.dataSourceId,
          name: this.createParam.name,
          description: this.createParam.description,
          method: this.createParam.method,
          contentType: this.createParam.contentType,
          path: this.createParam.path,
          open: this.createParam.open,
          namingStrategy: this.createParam.namingStrategy,
          formatMap: this.createParam.formatMap,
          open: this.createParam.open,
          alarm: this.createParam.alarm,
          flowStatus: this.createParam.flowStatus,
          flowGrade: this.createParam.flowGrade,
          flowCount: this.createParam.flowCount,
          cacheKeyType: this.createParam.cacheKeyType,
          cacheKeyExpr: this.createParam.cacheKeyExpr,
          cacheExpireSeconds: this.createParam.cacheExpireSeconds,
          engine: this.createParam.engine,
          contextList: sqls,
          params: this.inputParams,
          outputs: this.outputParams
        })
      }
      )
      .then(
        res => {
          if (0 === res.data.code) {
            this.$router.push({ path: '/interface/list' });
            this.$message(this.$t('common2.updateSuccess'));
          } else {
            if (res.data.message) {
              this.$alert(res.data.message, this.$t('common2.parseError'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "error"
                }
              );
            }
          }
        }
      );
    },
    handleDebug: function () {
      this.debugResponse = {}
      this.debugConsoleLog = ""
      var sqls = []
      var isSql = true;
      if (this.createParam.engine === 'SQL') {
        isSql = true
        sqls = this.$refs.sqlEditors.queryContent()
      } else {
        isSql = false
        sqls = this.$refs.scriptEditer.queryContent()
      }

      if (!this.createParam.dataSourceId) {
        this.$alert(this.$t('common2.selectDatasourceTip'), this.$t('common2.parseError'),
          {
            confirmButtonText: this.$t('common.confirm'),
            type: "error"
          }
        );
        return
      }

      if (this.checkSqlsOrScriptEmpty(sqls)) {
        this.$alert(isSql ? this.$t('common2.checkSqlContent') : this.$t('common2.checkScriptContent'), this.$t('common2.parseError'),
          {
            confirmButtonText: this.$t('common.confirm'),
            type: "error"
          }
        );
      } else {
        this.debugParams = []
        this.inputParams.forEach(item => {
          if (item.children && item.children.length > 0) {
            for (let it of item.children) {
              if (!it.arrayValues) {
                Vue.set(it, 'arrayValues', []);
              }
            }
          }
          this.debugParams.push(
            {
              id: item.id,
              name: item.name,
              type: item.type,
              isArray: item.isArray,
              required: item.required,
              defaultValue: item.defaultValue,
              remark: item.remark,
              value: null,
              arrayValues: [],
              children: item.children
            },
          )
        })
        this.showDebugDrawer = true
      }
    },
    addArrayValuesItem: function (row) {
      row.arrayValues.push('');
    },
    delArrayValuesItem: function (array, index) {
      array.splice(index, 1);
    },
    handleExecuteDebug: function () {
      var sqls = []
      if (this.createParam.engine === 'SQL') {
        sqls = this.$refs.sqlEditors.queryContent()
      } else {
        sqls = this.$refs.scriptEditer.queryContent()
      }

      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/assignment/debug",
        data: JSON.stringify({
          dataSourceId: this.createParam.dataSourceId,
          engine: this.createParam.engine,
          namingStrategy: this.createParam.namingStrategy,
          formatMap: this.createParam.formatMap,
          contextList: sqls,
          paramValues: this.debugParams
        })
      }).then(
        res => {
          if (0 === res.data.code) {
            this.debugResponse = res.data.data.answer;
            this.debugConsoleLog = res.data.data.logs;
            let arr = res.data.data.types;
            if (Array.isArray(arr) && arr.length === 0) {
              this.$alert(this.$t('common2.resultSetEmpty'), this.$t('common.warning'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "info"
                }
              );
            } else {
              var paramNameRemarkMap = new Map();
              for (let one of this.outputParams) {
                paramNameRemarkMap.set(one.name, one.remark);
                if (one.children) {
                  for (let subOne of one.children) {
                    paramNameRemarkMap.set(one.name + "." + subOne.name, subOne.remark);
                  }
                }
              }

              this.outputParams = [];
              for (let item of arr) {
                var remark = item.remark || paramNameRemarkMap.get(item.name);
                if (item.children) {
                  for (let one of item.children) {
                    one.remark = one.remark || paramNameRemarkMap.get(item.name + "." + one.name);
                  }
                }
                this.outputParams.push(
                  {
                    id: item.id,
                    name: item.name,
                    type: item.type,
                    isArray: item.isArray,
                    remark: remark,
                    children: item.children,
                  }
                )
              }
            }
          } else {
            if (res.data.message) {
              this.$alert(res.data.message, this.$t('common2.parseError'),
                {
                  confirmButtonText: this.$t('common.confirm'),
                  type: "error"
                }
              );
            }
          }
        }
      );
    },
    handleAddOutputParams: function () {
      this.outputParams.push(
        {
          name: '',
          type: 'STRING',
          remark: null
        },
      )
    },
    shouldOutputShowOption: function (item, row) {
      if (this.getOutputParamsParentRow(row)) {
        return item.value != 'OBJECT';
      }
      return true;
    },
    getOutputParamsParentRow: function (childRow) {
      for (const row of this.outputParams) {
        if (row.children && row.children.includes(childRow)) {
          return row;
        }
      }
      return null;
    },
    addOutputSubParamsItem: function (row) {
      const index = this.outputParams.findIndex(item => row == item)
      if (index !== -1) {
        if (!this.outputParams[index].children) {
          Vue.set(this.outputParams[index], 'children', []);
        }
        this.outputParams[index].location = 'REQUEST_BODY';
        this.outputParams[index].type = 'OBJECT';
        this.outputParams[index].children.push(
          {
            id: this.uuid(),
            name: "",
            type: "STRING",
            location: 'REQUEST_BODY',
            isArray: false,
            required: true,
            defaultValue: "",
            remark: ""
          },
        );
      } else {
        row.type = 'STRING';
        this.$alert(this.$t('common2.selectNested'), this.$t('common.warning'),
          {
            confirmButtonText: this.$t('common.confirm'),
            type: "info"
          }
        );
      }
    },
    deleteOutputParamsItem: function (idx, row) {
      const index = this.outputParams.indexOf(row);
      if (index !== -1) {
        this.outputParams.splice(index, 1);
      } else {
        this.deleteOutputSubParamsItem(idx, row);
      }
    },
    deleteOutputSubParamsItem: function (index, childRow) {
      const parentRow = this.getOutputParamsParentRow(childRow);
      if (parentRow) {
        const childIndex = parentRow.children.indexOf(childRow);
        if (childIndex !== -1) {
          parentRow.children.splice(childIndex, 1);
        } else {
          console.warn('Child not found');
        }
      }
    },
    handleShowVersionList: function () {
      this.$http({
        method: "GET",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/version/list/" + + this.$route.query.id,
      }).then(res => {
        if (0 === res.data.code) {
          this.versionList = res.data.data;
          this.showVersionDrawer = true;
        } else {
          if (res.data.message) {
            alert(this.$t('service.getVersionListFailed') + res.data.message);
          }
        }
      });
    },
    boolFormatOnline: function (row) {
      if (row.online === true) {
        return this.$t('common.yes');
      } else {
        return "-";
      }
    },
    handleShowVersionDetail: function (index, row) {
      this.$http({
        method: "GET",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/version/show/" + row.commitId,
      }).then(res => {
        if (0 === res.data.code) {
          this.showVersionDrawer = false;
          this.showTree = false;
          let detail = res.data.data.detail;
          this.applyAssignmentDetail(detail);
          this.showVersionDetail = true
          this.$message(this.$t('common2.switchVersionSuccess') + row.version + this.$t('common2.switchVersionEnd'));
        } else {
          if (res.data.message) {
            alert(this.$t('common2.queryFailed') + res.data.message);
          }
        }
      });
    },
    handleExitShowVersionDetail: function () {
      this.loadAssignmentDetail();
      this.showVersionDetail = false;
      this.$message(this.$t('common2.exitVersionSuccess'));
    },
    handleRevertVersionDetail: function (index, row) {
      this.$confirm(
        this.$t('common2.confirmRollback') + row.version + this.$t('common2.toVersion'),
        this.$t('common2.rollbackTip'),
        {
          confirmButtonText: this.$t('common.confirm'),
          cancelButtonText: this.$t('common.cancel'),
          type: "warning"
        }
      ).then(() => {
        this.$http({
          method: "GET",
          headers: {
            'Content-Type': 'application/json'
          },
          url: "/datapoly/manager/api/v1/version/revert/" + this.$route.query.id + "?commitId=" + row.commitId,
        }).then(res => {
          if (0 === res.data.code) {
            this.showVersionDrawer = false;
            this.loadAssignmentDetail();
            this.$message(this.$t('common2.rollbackSuccess'));
          } else {
            if (res.data.message) {
              alert(this.$t('common2.rollbackFailed') + res.data.message);
            }
          }
        });
      });
    }
  },
  async created () {
    this.initParamTypeList();
    this.initCacheKeyTypeList();
    await this.loadResponseTypeFormat();
    this.loadAssignmentDetail();
    this.loadConnections();
    this.loadGroups();
    this.loadModules();
    this.loadGateway();
    this.loadKeywordHints();
    this.loadTreeData();
    this.loadResponseNamingStrategy();
  },
}
</script>

<style scoped>
.el-card {
  width: 100%;
  height: 100%;
  overflow: auto;
}

.tip-content {
  font-size: 12px;
}

.name-mapper-table,
.name-mapper-table table tr th,
.name-mapper-table table tr td {
  border-collapse: collapse;
  border: 1px solid #e0dddd;
  width: 100%;
}

.el-descriptions__body
  .el-descriptions__table
  .el-descriptions-row
  .el-descriptions-item__label {
  min-width: 20px;
  max-width: 60px;
}

.custom-tree-node span {
  font-size: 12px;
}

.el-col .el-select {
  width: 98%;
}
.el-tree {
  overflow: auto;
}
.el-tree-node__content {
  height: 20px;
}
.el-drawer__wrapper {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
  margin: 0;
}
/deep/ .el-input.is-disabled .el-input__inner {
  color: #5f5e5e !important;
}
/deep/.el-table .cell {
  box-sizing: border-box;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: normal;
  word-break: break-all;
  line-height: 23px;
  padding-right: 10px;
  display: flex;
  flex-direction: row;
}
/deep/.el-table
  .cell
  .el-checkbox__input.is-disabled.is-checked
  .el-checkbox__inner {
  background-color: #1464dd;
  border-color: #f4f5f8;
}
.debug-console-log-text {
  white-space: pre-line;
}
</style>
