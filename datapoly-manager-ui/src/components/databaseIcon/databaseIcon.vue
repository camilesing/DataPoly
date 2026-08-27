<template>
  <div style="display: inline-block">
    <!-- Existing font icons (iconfont) -->
    <i class="iconfont icon-my-SQL colored_db"
       v-if="type == 'MYSQL'"></i>
    <i class="iconfont icon-MariaDB-chanpinicon colored_db"
       v-else-if="type == 'MARIADB'"></i>
    <i class="iconfont icon-oracle-copy-png colored_db"
       v-else-if="type == 'ORACLE'"></i>
    <i class="iconfont icon-SQLServer colored_db"
       v-else-if="type == 'SQLSERVER'"></i>
    <i class="iconfont icon-postgresql colored_db"
       v-else-if="type == 'POSTGRESQL'"></i>
    <i class="iconfont icon-DB2 colored_db"
       v-else-if="type == 'DB2'"></i>
    <i class="iconfont icon-hive colored_db"
       v-else-if="type == 'HIVE'"></i>
    <i class="iconfont icon-clickhouse2 colored_db"
       v-else-if="type == 'CLICKHOUSE'"></i>
    <i class="iconfont icon-http colored_db"
       v-else-if="type == 'HTTP'"></i>
    <!-- Newer database icons using SVG symbols -->
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'DM'">
      <use xlink:href="#icon-dm"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'KINGBASE'">
      <use xlink:href="#icon-kingbase"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'OSCAR'">
      <use xlink:href="#icon-oscar"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'GBASE8A'">
      <use xlink:href="#icon-gbase8a"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'SYBASE'">
      <use xlink:href="#icon-sybase"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'IMPALA'">
      <use xlink:href="#icon-impala"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'INCEPTOR'">
      <use xlink:href="#icon-inceptor"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'SQLITE3'">
      <use xlink:href="#icon-sqlite3"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'OPENGAUSS'">
      <use xlink:href="#icon-opengauss"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'DORIS'">
      <use xlink:href="#icon-doris"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'STARROCKS'">
      <use xlink:href="#icon-starrocks"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'OCEANBASE'">
      <use xlink:href="#icon-oceanbase"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'TDENGINE'">
      <use xlink:href="#icon-tdengine"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'MONGODB'">
      <use xlink:href="#icon-mongodb"></use>
    </svg>
    <svg class="svg-icon colored_db" aria-hidden="true"
         v-else-if="type == 'ELASTICSEARCH'">
      <use xlink:href="#icon-elasticsearch"></use>
    </svg>
    <!-- Default icon -->
    <i class="iconfont icon-OTHER colored_db"
       v-else></i>
  </div>
</template>

<script>
export default {
  name: "databaseIcon",
  props: ['type']
}
</script>

<style scoped>
.colored_db {
  color: #0698a5;
  font-size: 18px;
  font-weight: 500;
  margin-right: 5px;
}

.svg-icon {
  width: 1.2em;
  height: 1.2em;
  vertical-align: -0.15em;
  fill: currentColor;
  overflow: hidden;
}
</style>
