<template>
  <div class="dashbord">
    <el-row class="infoCrads">
      <el-col :span="6">
        <div class="cardItem">
          <div class="cardItem_txt">
            <CountTo class="cardItem_p0 color-green1"
                     :startVal="startVal"
                     :endVal="statistics.totalCount"
                     :duration="2000"></CountTo>
            <p class="cardItem_p1">{{ $t('dashboard.devInterfaceCount') }}</p>
          </div>
          <div class="cardItem_icon">
            <i class="el-icon-s-grid color-green1"></i>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="cardItem">
          <div class="cardItem_txt">
            <CountTo class="cardItem_p0 color-blue"
                     :startVal="startVal"
                     :endVal="statistics.openCount"
                     :duration="2000"></CountTo>
            <p class="cardItem_p1">{{ $t('dashboard.openInterfaceCount') }}</p>
          </div>
          <div class="cardItem_icon">
            <i class="el-icon-s-data color-blue"></i>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="cardItem">
          <div class="cardItem_txt">
            <CountTo class="cardItem_p0 color-green2"
                     :startVal="startVal"
                     :endVal="statistics.publishCount"
                     :duration="2000"></CountTo>
            <p class="cardItem_p1">{{ $t('dashboard.onlineInterfaceCount') }}</p>
          </div>
          <div class="cardItem_icon">
            <i class="el-icon-loading color-green2"></i>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="cardItem">
          <div class="cardItem_txt">
            <CountTo class="cardItem_p0 color-red"
                     :startVal="startVal"
                     :endVal="statistics.datasourceCount"
                     :duration="2000"></CountTo>
            <p class="cardItem_p1">{{ $t('dashboard.datasourceTotal') }}</p>
          </div>
          <div class="cardItem_icon">
            <i class="el-icon-office-building color-red"></i>
          </div>
        </div>
      </el-col>
    </el-row>
    <el-tabs type="border-card" class="dash-tabs" @tab-click="handleTabClick">
      <el-tab-pane :label="$t('dashboard.callOverview')">
        <el-row :gutter="12" class="filter-row">
          <el-col :span="8">
            <span>{{ $t('dashboard.timeRange') }}:</span>
            <el-select v-model="selectDays"
                       @change="selectChangedRangeTime"
                       :placeholder="$t('dashboard.selectTimeRange')">
              <el-option v-for="item in optionDays"
                         :key="item.value"
                         :label="item.label"
                         :value="item.value">
              </el-option>
            </el-select>
          </el-col>
          <el-col :span="8">
            <span>{{ $t('dashboard.topNCount') }}:</span>
            <el-select v-model="topNum"
                       @change="selectChangedTopNum"
                       :placeholder="$t('dashboard.selectTopN')">
              <el-option v-for="item in optionTopN"
                         :key="item.value"
                         :label="item.label"
                         :value="item.value">
              </el-option>
            </el-select>
          </el-col>
          <el-col :span="8">
          </el-col>
        </el-row>
        <el-row :gutter="12" class="chart-row">
          <el-col :span="12">
            <div id="topPathChart" class="chart-panel"></div>
          </el-col>
          <el-col :span="12">
            <div id="topAppChart" class="chart-panel"></div>
          </el-col>
        </el-row>
        <el-row :gutter="12" class="chart-row">
          <el-col :span="12">
            <div id="topAddrChart" class="chart-panel"></div>
          </el-col>
          <el-col :span="12">
            <div id="pieChart" class="chart-panel"></div>
          </el-col>
        </el-row>
        <el-row :gutter="12" class="chart-row">
          <el-col :span="24">
            <div id="barChart" class="chart-panel chart-panel-full"></div>
          </el-col>
        </el-row>
      </el-tab-pane>
      <el-tab-pane :label="$t('dashboard.devOverview')">
        <el-row :gutter="12" class="chart-row">
          <el-col :span="12">
            <div id="dsTypeChart" class="chart-panel"></div>
          </el-col>
          <el-col :span="12">
            <div id="engineChart" class="chart-panel"></div>
          </el-col>
        </el-row>
        <el-row :gutter="12" class="chart-row">
          <el-col :span="12">
            <div id="methodChart" class="chart-panel"></div>
          </el-col>
          <el-col :span="12">
            <div id="moduleChart" class="chart-panel"></div>
          </el-col>
        </el-row>
        <el-row :gutter="12" class="chart-row">
          <el-col :span="24">
            <div id="dsApiChart" class="chart-panel chart-panel-full"></div>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script>
import CountTo from "vue-count-to";

export default {
  name: "Dashboard",
  components: {
    CountTo
  },
  data () {
    return {
      startVal: 0,
      statistics: {},
      optionDays: [
        { label: '1', value: 1 },
        { label: '3', value: 3 },
        { label: '7', value: 7 },
        { label: '30', value: 30 },
      ],
      optionTopN: [
        { label: 'Top3 ', value: 3 },
        { label: 'Top5 ', value: 5 },
        { label: 'Top6 ', value: 6 },
        { label: 'Top8 ', value: 8 },
        { label: 'Top10 ', value: 10 },
      ],
      selectDays: 7,
      topNum: 6,
      barChart: null,
      pieChart: null,
      topPathChart: null,
      topAppChart: null,
      topAddrChart: null,
      dsTypeChart: null,
      engineChart: null,
      methodChart: null,
      moduleChart: null,
      dsApiChart: null,
      dsTypeData: {
        title: { text: '' },
        tooltip: { trigger: 'item' },
        legend: { orient: 'vertical', left: 'right' },
        series: [{
          name: '',
          type: 'pie',
          radius: '55%',
          data: [],
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)'
            }
          }
        }]
      },
      engineData: {
        title: { text: '' },
        tooltip: { trigger: 'item' },
        legend: { orient: 'vertical', left: 'right' },
        color: ['#40c9c6', '#36a3f7'],
        series: [{
          name: '',
          type: 'pie',
          radius: '55%',
          data: [],
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)'
            }
          }
        }]
      },
      methodData: {
        title: { text: '' },
        tooltip: { trigger: 'item' },
        legend: { orient: 'vertical', left: 'right' },
        color: ['#40c9c6', '#36a3f7', '#f4516c', '#34bfa3', '#e6a23c'],
        series: [{
          name: '',
          type: 'pie',
          radius: '55%',
          data: [],
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)'
            }
          }
        }]
      },
      moduleData: {
        title: { text: '' },
        color: ['#36a3f7'],
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' }
        },
        legend: {},
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'value', boundaryGap: [0, 0.01] },
        yAxis: { type: 'category', data: [] },
        series: [{ type: 'bar', data: [] }]
      },
      dsApiData: {
        title: { text: '' },
        color: ['#36a3f7'],
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' }
        },
        legend: {},
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'value', boundaryGap: [0, 0.01] },
        yAxis: { type: 'category', data: [] },
        series: [{ type: 'bar', data: [] }]
      },
      barChartData: {
        title: {
          text: ''
        },
        tooltip: {
          trigger: "axis"
        },
        legend: {
          data: [
            {
              name: '',
              textStyle: {
                color: '#000'
              }
            },
            {
              name: '',
              textStyle: {
                color: '#000'
              }
            }
          ]
        },
        grid: {
          left: "3%",
          right: "4%",
          bottom: "3%",
          containLabel: true
        },
        xAxis: {
          type: "category",
          boundaryGap: true,
          data: [],
          axisLabel: {
            interval: 0,
            textStyle: {
              color: '#000',
              fontSize: 10
            },
            margin: 8
          },
          axisLine: {
            show: true,
            lineStyle: {
              color: 'rgb(2,121,253)'
            }
          },
          axisTick: {
            show: false,
          }
        },
        yAxis: {
          type: "value"
        },
        series: [
          {
            name: '',
            type: "bar",
            barWidth: '8%',
            data: []
          },
          {
            name: '',
            type: "bar",
            barWidth: '8%',
            data: []
          }
        ]
      },
      pieChartData: {
        title: {
          text: ''
        },
        tooltip: {
          trigger: 'item'
        },
        legend: {
          orient: 'vertical',
          left: 'right',
        },
        series: [
          {
            name: '',
            type: 'pie',
            radius: '55%',
            data: [
              { value: 0, name: '' },
              { value: 0, name: '' },
            ],
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)'
              }
            }
          }
        ]
      },
      topPathData: {
        title: {
          text: ''
        },
        color: ['#40c9c6'],
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        legend: {},
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'value',
          boundaryGap: [0, 0.01]
        },
        yAxis: {
          type: 'category',
          data: []
        },
        series: [
          {
            type: 'bar',
            data: [12, 44, 55, 67, 89, 112]
          }
        ]
      },
      topAppData: {
        title: {
          text: ''
        },
        color: ['#36a3f7'],
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        legend: {},
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'value',
          boundaryGap: [0, 0.01]
        },
        yAxis: {
          type: 'category',
          data: []
        },
        series: [
          {
            type: 'bar',
            data: [12, 44, 55, 67, 89, 112]
          }
        ]
      },
      topAddrData: {
        title: {
          text: ''
        },
        color: ['#34bfa3'],
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        legend: {},
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'value',
          boundaryGap: [0, 0.01]
        },
        yAxis: {
          type: 'category',
          data: []
        },
        series: [
          {
            type: 'bar',
            data: [12, 44, 55, 67, 89, 112]
          }
        ]
      }
    };
  },
  methods: {
    loadTotal: function () {
      this.$http.get("/datapoly/manager/api/v1/overview/counter")
        .then(
          res => {
            if (0 === res.data.code) {
              this.statistics = res.data.data;
            }
          }
        );
    },
    loadData: function () {
      this.$http.get("/datapoly/manager/api/v1/overview/trend/" + this.selectDays)
        .then(
          res => {
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
              this.barChartData.xAxis.data = xAxisData;
              this.barChartData.series[0].data = y1AxisData;
              this.barChartData.series[1].data = y2AxisData;

              this.barChart.setOption(this.barChartData, true);
            }
          }
        );

      this.$http.get("/datapoly/manager/api/v1/overview/ratio/" + this.selectDays)
        .then(
          res => {
            if (0 === res.data.code) {
              var result = res.data.data;
              var list = []
              result.forEach(item => list.push({ name: item.name, value: item.count }))
              this.pieChartData.series[0].data = list
              this.pieChart.setOption(this.pieChartData, true);
            }
          }
        );

      this.$http.get("/datapoly/manager/api/v1/overview/top/path/" + this.selectDays + "?n=" + this.topNum)
        .then(
          res => {
            if (0 === res.data.code) {
              var result = res.data.data;
              this.topPathData.yAxis.data = result.map(t => t.name).reverse()
              this.topPathData.series[0].data = result.map(t => t.count).reverse()
              this.topPathData.title.text = 'TOP' + this.topNum + this.$t('dashboard.topInterface')
              this.topPathChart.setOption(this.topPathData, true);
            }
          }
        );

      this.$http.get("/datapoly/manager/api/v1/overview/top/client/" + this.selectDays + "?n=" + this.topNum)
        .then(
          res => {
            if (0 === res.data.code) {
              var result = res.data.data;
              this.topAppData.yAxis.data = result.map(t => t.name).reverse()
              this.topAppData.series[0].data = result.map(t => t.count).reverse()
              this.topAppData.title.text = 'TOP' + this.topNum + this.$t('dashboard.topApp')
              this.topAppChart.setOption(this.topAppData, true);
            }
          }
        );

      this.$http.get("/datapoly/manager/api/v1/overview/top/addr/" + this.selectDays + "?n=" + this.topNum)
        .then(
          res => {
            if (0 === res.data.code) {
              var result = res.data.data;
              this.topAddrData.yAxis.data = result.map(t => t.name).reverse()
              this.topAddrData.series[0].data = result.map(t => t.count).reverse()
              this.topAddrData.title.text = 'TOP' + this.topNum + this.$t('dashboard.topAddr')
              this.topAddrChart.setOption(this.topAddrData, true);
            }
          }
        );

    },
    loadDashboardStats: function () {
      this.$http.get("/datapoly/manager/api/v1/overview/datasource-type-ratio")
        .then(res => {
          if (0 === res.data.code) {
            var list = res.data.data.map(item => ({ name: item.name, value: item.count }));
            this.dsTypeData.series[0].data = list;
            this.dsTypeChart.setOption(this.dsTypeData, true);
          }
        });

      this.$http.get("/datapoly/manager/api/v1/overview/engine-ratio")
        .then(res => {
          if (0 === res.data.code) {
            var list = res.data.data.map(item => ({ name: item.name, value: item.count }));
            this.engineData.series[0].data = list;
            this.engineChart.setOption(this.engineData, true);
          }
        });

      this.$http.get("/datapoly/manager/api/v1/overview/method-ratio")
        .then(res => {
          if (0 === res.data.code) {
            var list = res.data.data.map(item => ({ name: item.name, value: item.count }));
            this.methodData.series[0].data = list;
            this.methodChart.setOption(this.methodData, true);
          }
        });

      this.$http.get("/datapoly/manager/api/v1/overview/datasource-api-count")
        .then(res => {
          if (0 === res.data.code) {
            var result = res.data.data;
            this.dsApiData.yAxis.data = result.map(t => t.name).reverse();
            this.dsApiData.series[0].data = result.map(t => t.count).reverse();
            this.dsApiChart.setOption(this.dsApiData, true);
          }
        });

      this.$http.get("/datapoly/manager/api/v1/overview/module-api-count")
        .then(res => {
          if (0 === res.data.code) {
            var result = res.data.data;
            this.moduleData.yAxis.data = result.map(t => t.name).reverse();
            this.moduleData.series[0].data = result.map(t => t.count).reverse();
            this.moduleChart.setOption(this.moduleData, true);
          }
        });
    },
    selectChangedRangeTime: function () {
      this.loadData();
    },
    selectChangedTopNum: function () {
      this.loadData();
    },
    initTranslations () {
      this.optionDays = [
        { label: this.$t('dashboard.1day'), value: 1 },
        { label: this.$t('dashboard.3days'), value: 3 },
        { label: this.$t('dashboard.7days'), value: 7 },
        { label: this.$t('dashboard.30days'), value: 30 },
      ];
      this.barChartData.title.text = this.$t('dashboard.trendStats');
      this.barChartData.legend.data[0].name = this.$t('dashboard.total');
      this.barChartData.legend.data[1].name = this.$t('dashboard.successCount');
      this.barChartData.series[0].name = this.$t('dashboard.total');
      this.barChartData.series[1].name = this.$t('dashboard.successCount');
      this.pieChartData.title.text = this.$t('dashboard.failRate');
      this.pieChartData.series[0].name = this.$t('dashboard.operationStatus');
      this.topPathData.title.text = this.$t('dashboard.topInterface');
      this.topAppData.title.text = this.$t('dashboard.topApp');
      this.topAddrData.title.text = this.$t('dashboard.topAddr');
      this.dsTypeData.title.text = this.$t('dashboard.datasourceTypeRatio');
      this.dsTypeData.series[0].name = this.$t('dashboard.datasourceTypeRatio');
      this.engineData.title.text = this.$t('dashboard.engineRatio');
      this.engineData.series[0].name = this.$t('dashboard.engineRatio');
      this.methodData.title.text = this.$t('dashboard.methodRatio');
      this.methodData.series[0].name = this.$t('dashboard.methodRatio');
      this.moduleData.title.text = this.$t('dashboard.moduleApiCount');
      this.moduleData.series[0].name = this.$t('dashboard.moduleApiCount');
      this.dsApiData.title.text = this.$t('dashboard.datasourceApiCount');
    },
    updateChartsLocale () {
      this.initTranslations();
      this.loadData();
      this.loadDashboardStats();
    },
    handleTabClick: function () {
      this.$nextTick(() => {
        this.resizeAllCharts();
      });
    },
    resizeAllCharts: function () {
      var charts = [this.barChart, this.pieChart, this.topPathChart,
        this.topAppChart, this.topAddrChart, this.dsTypeChart,
        this.engineChart, this.methodChart, this.moduleChart, this.dsApiChart];
      for (var i = 0; i < charts.length; i++) {
        if (charts[i]) {
          charts[i].resize();
        }
      }
    }
  },
  computed: {
    currentLocale () {
      return this.$i18n.locale
    }
  },
  watch: {
    currentLocale () {
      this.$nextTick(() => {
        this.updateChartsLocale();
      });
    }
  },
  created () {
    this.initTranslations();
    this.loadTotal();
  },
  mounted () {
    this.barChart = this.$echarts.init(document.getElementById("barChart"));
    this.pieChart = this.$echarts.init(document.getElementById("pieChart"));
    this.topPathChart = this.$echarts.init(document.getElementById("topPathChart"));
    this.topAppChart = this.$echarts.init(document.getElementById("topAppChart"));
    this.topAddrChart = this.$echarts.init(document.getElementById("topAddrChart"));
    this.dsTypeChart = this.$echarts.init(document.getElementById("dsTypeChart"));
    this.engineChart = this.$echarts.init(document.getElementById("engineChart"));
    this.methodChart = this.$echarts.init(document.getElementById("methodChart"));
    this.moduleChart = this.$echarts.init(document.getElementById("moduleChart"));
    this.dsApiChart = this.$echarts.init(document.getElementById("dsApiChart"));
    this.loadData();
    this.loadDashboardStats();
    window.addEventListener('resize', this.resizeAllCharts);
    this.$nextTick(() => this.resizeAllCharts());
    setTimeout(() => this.resizeAllCharts(), 300);
  }
};
</script>

<style scoped>
.dashbord {
  background-color: #f0f3f4;
}

.color-green1 {
  color: #40c9c6 !important;
}
.color-blue {
  color: #36a3f7 !important;
}
.color-red {
  color: #f4516c !important;
}
.color-green2 {
  color: #34bfa3 !important;
}

.infoCrads {
  margin: 0 0 16px 0;
}

.infoCrads .el-col {
  padding: 0 8px;
}

.infoCrads .el-col .cardItem {
  height: 100px;
  background: #fff;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.cardItem {
  color: #666;
}

.cardItem .cardItem_txt {
  float: left;
}

.cardItem .cardItem_txt .cardItem_p0 {
  font-size: 28px;
  font-weight: bold;
  margin: 0;
}

.cardItem .cardItem_txt .cardItem_p1 {
  font-size: 14px;
  margin: 4px 0 0 0;
  color: #999;
}

.cardItem .cardItem_icon {
  font-size: 48px;
  font-weight: bold;
}

.dash-tabs {
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
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

#dsApiChart {
  height: 420px;
}

#moduleChart {
  height: 420px;
}

.filter-row {
  margin-bottom: 16px;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
}
</style>
