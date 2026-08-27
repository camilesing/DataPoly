<template>
  <div>
    <h3>{{ $t('about.title') }}</h3>
    <el-tabs v-model="activeName">
      <el-tab-pane :label="$t('about.systemFunction')"
                   name="first">
        <div class="content_tag">
          <el-tag type="danger">{{ $t('about.sqlInterface') }}</el-tag>
          <el-tag type="success">{{ $t('about.scriptInterface') }}</el-tag>
        </div>
        <div class="content_card">
          <el-card class="box-card">
            <div class="text item">
              <p>
                {{ $t('about.subtitle') }}
              </p>
              <ul>
                <li>{{ $t('about.projectHosting') }}</li>
                <p>
                  {{ $t('about.github') }}：<a href="https://github.com/camilesing/datapoly">https://github.com/camilesing/datapoly</a>
                </p>
                <li>{{ $t('about.buildRestfulSql') }}</li>
                <p>
                  {{ $t('about.buildRestfulSqlDetail') }}
                </p>
                <li>{{ $t('about.buildRestfulScript') }}</li>
                <p>
                  {{ $t('about.buildRestfulScriptDetail') }}
                </p>
                <li>{{ $t('about.tokenAuth') }}</li>
                <p>
                  {{ $t('about.tokenAuthDetail') }}
                </p>
                <li>{{ $t('about.onlineDoc') }}</li>
                <p>
                  {{ $t('about.onlineDocDetail') }}
                </p>
                <li>{{ $t('about.flowControl') }}</li>
                <p>
                  {{ $t('about.flowControlDetail') }}
                </p>
                <li>{{ $t('about.cacheConfig') }}</li>
                <p>
                  {{ $t('about.cacheConfigDetail') }}
                </p>
                <li>{{ $t('about.alarmIntegration') }}</li>
                <p>
                  {{ $t('about.alarmIntegrationDetail') }}
                </p>
                <li>{{ $t('about.restfulForward') }}</li>
                <p>
                  {{ $t('about.restfulForwardDetail') }}
                </p>
                <li>{{ $t('about.docDatabase') }}</li>
                <p>
                  {{ $t('about.docDatabaseDetail') }}
                </p>
                <li>{{ $t('about.versionControl') }}</li>
                <p>
                  {{ $t('about.versionControlDetail') }}
                </p>
                <li>{{ $t('about.batchImportExport') }}</li>
                <p>
                  {{ $t('about.batchImportExportDetail') }}
                </p>
                <li>{{ $t('about.mcpTool') }}</li>
                <p>
                  {{ $t('about.mcpToolDetail') }}
                </p>
              </ul>
            </div>
          </el-card>
        </div>
      </el-tab-pane>
      <el-tab-pane :label="$t('about.supportedDatasource')"
                   name="second">
        <div class="content_tag">
          <el-tag type="success">JDBC</el-tag>
          <el-tag type="danger">datapoly</el-tag>
        </div>
        <div class="content_card">
          <el-card class="box-card">
            <div class="text item">
              <p>
                {{ $t('about.supported20plus') }}
              </p>
              <ul>
                <li>{{ $t('about.oracle') }}
                </li>
                <li>{{ $t('about.mysql') }}
                </li>
                <li>{{ $t('about.mariadb') }}
                </li>
                <li>{{ $t('about.sqlserver') }}
                </li>
                <li>{{ $t('about.postgresql') }}
                </li>
                <li>{{ $t('about.db2') }}
                </li>
                <li>{{ $t('about.sybase') }}
                </li>
                <li>{{ $t('about.dameng') }}
                </li>
                <li>{{ $t('about.kingbase') }}
                </li>
                <li>{{ $t('about.highgo') }}
                </li>
                <li>{{ $t('about.shentong') }}
                </li>
                <li>{{ $t('about.gbase') }}
                </li>
                <li>{{ $t('about.hive') }}
                </li>
                <li>{{ $t('about.impala') }}
                </li>
                <li>{{ $t('about.doris') }}
                </li>
                <li>{{ $t('about.sqlite') }}
                </li>
                <li>{{ $t('about.opengauss') }}
                </li>
                <li>{{ $t('about.clickhouse') }}
                </li>
                <li>{{ $t('about.starrocks') }}
                </li>
                <li>{{ $t('about.oceanbase') }}
                </li>
                <li>{{ $t('about.tdengine') }}
                </li>
                <li>{{ $t('about.mongodb') }}
                </li>
                <li>{{ $t('about.elasticsearch') }}
                </li>
                <li>{{ $t('about.httpRestful') }}
                </li>
              </ul>
            </div>
          </el-card>
        </div>
      </el-tab-pane>
      <el-tab-pane :label="$t('about.devTechStack')"
                   name="third">
        <div class="content_tag">
          <el-tag type="success">SpringBoot</el-tag>
          <el-tag type="danger">SpringCloud</el-tag>
          <el-tag type="success">Mybatis</el-tag>
          <el-tag type="danger">Vue/ElementUI</el-tag>
        </div>
        <div class="content_card">
          <el-card class="box-card">
            <div class="text item">
              <p>
                datapoly
              <pre>
            └── datapoly
                ├── datapoly-common                     // {{ $t('about.moduleCommon') }}
                ├── datapoly-mcp                        // {{ $t('about.moduleMcp') }}
                ├── datapoly-template                   // {{ $t('about.moduleTemplate') }}
                ├── datapoly-cache                      // {{ $t('about.moduleCache') }}
                ├── datapoly-persistence                // {{ $t('about.modulePersistence') }}
                ├── datapoly-core                       // {{ $t('about.moduleCore') }}
                ├── datapoly-gateway                    // {{ $t('about.moduleGateway') }}
                ├── datapoly-executor                   // {{ $t('about.moduleExecutor') }}
                ├── datapoly-manager                    // {{ $t('about.moduleManager') }}
                ├── datapoly-manager-ui                 // {{ $t('about.moduleUi') }}
                ├── datapoly-dist                       // {{ $t('about.moduleDist') }}
                </pre>
              </p>
              <ul>
                <li>{{ $t('about.springboot') }}</li>
                <p>
                  {{ $t('about.springbootDetail') }}
                </p>
                <li>{{ $t('about.springcloud') }}</li>
                <p>
                  {{ $t('about.springcloudDetail') }}
                </p>
                <li>{{ $t('about.mybatis') }}</li>
                <p>
                  {{ $t('about.mybatisDetail') }}
                </p>
                <p>
                  {{ $t('about.persistenceDetail') }}
                </p>
                <li>{{ $t('about.vueElement') }}</li>
                <p>
                  {{ $t('about.vueDetail') }}
                </p>
                <p>
                  {{ $t('about.uiDetail') }}
                </p>
              </ul>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

    </el-tabs>
  </div>
</template>

<script>
export default {
  data () {
    return {
      activeName: 'first'
    };
  }
}
</script>

<style scoped>
div h3 {
  text-align: center;
}

.content_tag {
  text-align: center;
}

.content_card {
  padding-top: 20px;
}
</style>
