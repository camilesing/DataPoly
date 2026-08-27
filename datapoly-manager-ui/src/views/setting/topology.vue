<template>
  <div class="topology-container">
    <!-- Top title bar -->
    <div class="top-bar">
      <div class="title-area">
        <span class="title-icon">🗂️</span>
        <span class="title-text">{{ $t('setting.clusterTopology') }}</span>
        <span v-if="lastRefreshTime" class="last-refresh">{{ $t('setting.lastRefresh') }} {{ lastRefreshTime }}</span>
      </div>
      <div class="legend-area">
        <div class="legend-item">
          <span class="legend-dot manager-dot"></span>
          <div class="legend-info">
            <span class="legend-label">Manager</span>
            <span class="legend-desc">{{ $t('setting.managerNode') }}</span>
          </div>
        </div>
        <div class="legend-item">
          <span class="legend-dot gateway-dot"></span>
          <div class="legend-info">
            <span class="legend-label">Gateway</span>
            <span class="legend-desc">{{ $t('setting.gatewayNode') }}</span>
          </div>
        </div>
        <div class="legend-item">
          <span class="legend-dot executor-dot"></span>
          <div class="legend-info">
            <span class="legend-label">Executor</span>
            <span class="legend-desc">{{ $t('setting.executorNode') }}</span>
          </div>
        </div>
        <button class="refresh-btn" :class="{ spinning: isLoading }" @click="refreshData">
          <span class="btn-icon">↻</span> {{ $t('setting.refresh') }}
        </button>
      </div>
    </div>

    <!-- Stats cards -->
    <div class="stats-bar">
      <div class="stat-card manager-card">
        <span class="stat-num">{{ managerCount }}</span>
        <span class="stat-label">{{ $t('setting.managerNode') }}</span>
      </div>
      <div class="stat-card gateway-card">
        <span class="stat-num">{{ gatewayCount }}</span>
        <span class="stat-label">{{ $t('setting.gatewayNode') }}</span>
      </div>
      <div class="stat-card executor-card">
        <span class="stat-num">{{ executorCount }}</span>
        <span class="stat-label">{{ $t('setting.executorNode') }}</span>
      </div>
      <div class="stat-card total-card">
        <span class="stat-num">{{ totalCount }}</span>
        <span class="stat-label">{{ $t('setting.totalNodes') }}</span>
      </div>
    </div>

    <!-- Chart area -->
    <div class="chart-wrapper">
      <div v-if="isLoading" class="loading-mask">
        <div class="spinner"></div>
        <span class="loading-text">{{ $t('setting.loading') }}</span>
      </div>
      <div v-if="isEmpty && !isLoading" class="empty-state">
        <div class="empty-icon">📡</div>
        <div class="empty-text">{{ $t('setting.noData') }}</div>
        <div class="empty-sub">{{ $t('setting.refreshTip') }}</div>
      </div>
      <div id="topology-chart" ref="topologyChart"></div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts';

export default {
  name: 'TopologyView',
  data () {
    return {
      chart: null,
      nodes: [],
      links: [],
      isLoading: false,
      isEmpty: false,
      lastRefreshTime: null
    };
  },
  computed: {
    managerCount () {
      return this.nodes.filter(n => n.role === 'Manager').length;
    },
    gatewayCount () {
      return this.nodes.filter(n => n.role === 'Gateway').length;
    },
    executorCount () {
      return this.nodes.filter(n => n.role === 'Executor').length;
    },
    totalCount () {
      return this.nodes.length;
    }
  },
  mounted () {
    this.initChart();
    this.loadNodeData().then(() => {
      this.renderChart();
    });
    window.addEventListener('resize', this.handleResize);
  },
  beforeDestroy () {
    window.removeEventListener('resize', this.handleResize);
    if (this.chart) {
      this.chart.dispose();
    }
  },
  methods: {
    initChart () {
      if (this.chart) {
        this.chart.dispose();
      }
      this.chart = echarts.init(this.$refs.topologyChart, null, { renderer: 'canvas' });
    },

    loadNodeData () {
      this.isLoading = true;
      this.isEmpty = false;
      return this.$http.get('/datapoly/manager/api/v1/node/topology').then(res => {
        if (0 === res.data.code) {
          const managerNodes = [];
          const gatewayNodes = [];
          const executorNodes = [];

          for (const node of res.data.data) {
            const base = {
              id: node.instanceId,
              name: node.host + ':' + node.port,
              address: node.host,
              port: node.port,
              memory: node.memory || 0,
              cpu: node.cpu || 0,
              disk: node.disk || 0,
              status: node.status || 'normal'
            };
            if (node.serviceId.includes('MANAGER')) {
              managerNodes.push({ ...base, role: 'Manager' });
            } else if (node.serviceId.includes('GATEWAY')) {
              gatewayNodes.push({ ...base, role: 'Gateway' });
            } else if (node.serviceId.includes('EXECUTOR')) {
              executorNodes.push({ ...base, role: 'Executor' });
            }
          }

          this.nodes = [...managerNodes, ...gatewayNodes, ...executorNodes];

          this.links = [];
          managerNodes.forEach(manager => {
            gatewayNodes.forEach(gateway => {
              this.links.push({ source: manager.id, target: gateway.id, type: 'mg' });
            });
          });
          gatewayNodes.forEach(gateway => {
            executorNodes.forEach(executor => {
              this.links.push({ source: gateway.id, target: executor.id, type: 'ge' });
            });
          });

          this.isEmpty = this.nodes.length === 0;
          this.lastRefreshTime = new Date().toLocaleTimeString();
        } else {
          this.$message({ message: this.$t('setting.loadFailedTip') + res.data.message, type: 'error' });
        }
      }).catch(() => {
        this.$message({ message: this.$t('setting.networkFailed'), type: 'error' });
      }).finally(() => {
        this.isLoading = false;
      });
    },

    calculatePositions () {
      const positions = {};
      const el = this.$refs.topologyChart;
      const width = (el && el.clientWidth) || 900;
      const height = (el && el.clientHeight) || 600;

      const managerNodes = this.nodes.filter(n => n.role === 'Manager');
      const gatewayNodes = this.nodes.filter(n => n.role === 'Gateway');
      const executorNodes = this.nodes.filter(n => n.role === 'Executor');

      const rowY = {
        manager: height * 0.15,
        gateway: height * 0.45,
        executor: height * 0.78
      };

      const placeRow = (arr, y) => {
        const total = arr.length;
        arr.forEach((node, i) => {
          const spacing = width / (total + 1);
          positions[node.id] = { x: spacing * (i + 1), y };
        });
      };

      placeRow(managerNodes, rowY.manager);
      placeRow(gatewayNodes, rowY.gateway);
      placeRow(executorNodes, rowY.executor);

      return positions;
    },

    renderChart () {
      if (!this.chart) return;
      if (this.nodes.length === 0) return;

      const chartDom = this.$refs.topologyChart;
      const height = chartDom ? chartDom.clientHeight : 600;
      const positions = this.calculatePositions();

      const roleStyle = {
        Manager: {
          color: 'rgba(59, 130, 246, 0.92)',
          border: '#93c5fd',
          shadow: 'rgba(59,130,246,0.5)',
          size: 90
        },
        Gateway: {
          color: 'rgba(16, 185, 129, 0.92)',
          border: '#6ee7b7',
          shadow: 'rgba(16,185,129,0.5)',
          size: 80
        },
        Executor: {
          color: 'rgba(245, 158, 11, 0.92)',
          border: '#fcd34d',
          shadow: 'rgba(245,158,11,0.5)',
          size: 75
        }
      };

      const seriesData = this.nodes.map(node => {
        const style = roleStyle[node.role];
        const pos = positions[node.id] || { x: 0, y: 0 };

        let fillColor = style.color;
        if (node.status === 'warning') fillColor = 'rgba(239,68,68,0.88)';

        return {
          id: node.id,
          name: node.name,
          x: pos.x,
          y: pos.y,
          role: node.role,
          address: node.address,
          port: node.port,
          memVal: node.memory,
          cpuVal: node.cpu,
          diskVal: node.disk,
          status: node.status,
          symbolSize: style.size,
          symbol: 'roundRect',
          itemStyle: {
            color: fillColor,
            borderColor: style.border,
            borderWidth: 3,
            shadowBlur: 18,
            shadowColor: style.shadow,
            shadowOffsetX: 0,
            shadowOffsetY: 4
          },
          label: {
            show: true,
            position: 'inside',
            formatter: node.name,
            fontSize: 11,
            fontWeight: 'bold',
            color: '#fff',
            textShadowColor: 'rgba(0,0,0,0.3)',
            textShadowBlur: 3
          },
          emphasis: {
            itemStyle: {
              borderWidth: 5,
              shadowBlur: 28,
              shadowColor: style.shadow
            },
            label: {
              fontSize: 12
            }
          }
        };
      });

      const layerLabels = [
        { x: 18, y: height * 0.15, text: this.$t('common2.managementLayer'), color: '#3b82f6' },
        { x: 18, y: height * 0.45, text: this.$t('common2.gatewayLayer'), color: '#10b981' },
        { x: 18, y: height * 0.78, text: this.$t('common2.executorLayer'), color: '#f59e0b' }
      ].map((l, idx) => ({
        id: '__layer_' + idx,
        name: l.text,
        x: l.x,
        y: l.y,
        symbolSize: 1,
        symbol: 'none',
        label: {
          show: true,
          position: 'right',
          formatter: l.text,
          fontSize: 13,
          fontWeight: 'bold',
          color: l.color,
          backgroundColor: 'rgba(255,255,255,0.15)',
          borderRadius: 4,
          padding: [3, 8]
        },
        itemStyle: { opacity: 0 },
        emphasis: { disabled: true }
      }));

      const option = {
        backgroundColor: {
          type: 'linear',
          x: 0, y: 0, x2: 1, y2: 1,
          colorStops: [
            { offset: 0, color: '#0f1e3a' },
            { offset: 0.5, color: '#142850' },
            { offset: 1, color: '#0d2137' }
          ]
        },
        tooltip: {
          trigger: 'item',
          backgroundColor: 'rgba(15,30,58,0.95)',
          borderColor: 'rgba(100,160,255,0.3)',
          borderWidth: 1,
          textStyle: { color: '#e2e8f0', fontSize: 13 },
          formatter: function (params) {
            if (params.dataType === 'edge') {
              return '<span style="color:#94a3b8">' + params.data.source + '</span>' +
                     '<span style="color:#60a5fa"> → </span>' +
                     '<span style="color:#94a3b8">' + params.data.target + '</span>';
            }
            var d = params.data;
            if (!d.role) return '';
            var roleColors = { Manager: '#60a5fa', Gateway: '#34d399', Executor: '#fbbf24' };
            var color = roleColors[d.role] || '#fff';
            var statusText = d.status === 'warning' ? '⚠️ Warning' : '✅ Normal';
            return '<div style="min-width:200px;padding:4px 0">' +
              '<div style="font-size:15px;font-weight:bold;color:' + color + ';margin-bottom:8px">' + d.role + ' · ' + d.name + '</div>' +
              '<div style="color:#94a3b8;margin-bottom:6px">Status: ' + statusText + '</div>' +
              '<div style="display:flex;gap:12px;margin-top:6px">' +
                '<div style="flex:1"><div style="color:#94a3b8;font-size:11px">Memory</div>' +
                '<div style="color:#f1f5f9;font-size:14px;font-weight:600">' + d.memVal + '%</div>' +
                '<div style="height:4px;background:#1e3a5f;border-radius:2px;margin-top:3px">' +
                '<div style="height:4px;width:' + d.memVal + '%;background:#60a5fa;border-radius:2px"></div></div></div>' +
                '<div style="flex:1"><div style="color:#94a3b8;font-size:11px">CPU</div>' +
                '<div style="color:#f1f5f9;font-size:14px;font-weight:600">' + d.cpuVal + '%</div>' +
                '<div style="height:4px;background:#1e3a5f;border-radius:2px;margin-top:3px">' +
                '<div style="height:4px;width:' + d.cpuVal + '%;background:#34d399;border-radius:2px"></div></div></div>' +
                '<div style="flex:1"><div style="color:#94a3b8;font-size:11px">Disk</div>' +
                '<div style="color:#f1f5f9;font-size:14px;font-weight:600">' + d.diskVal + '%</div>' +
                '<div style="height:4px;background:#1e3a5f;border-radius:2px;margin-top:3px">' +
                '<div style="height:4px;width:' + d.diskVal + '%;background:#fbbf24;border-radius:2px"></div></div></div>' +
              '</div></div>';
          }
        },
        series: [
          {
            type: 'graph',
            layout: 'none',
            symbol: 'roundRect',
            roam: true,
            zoom: 0.9,
            edgeSymbol: ['none', 'arrow'],
            edgeSymbolSize: [4, 10],
            data: seriesData.concat(layerLabels),
            links: this.links.map(function (link) {
              return {
                source: link.source,
                target: link.target,
                lineStyle: {
                  color: link.type === 'mg'
                    ? new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                      { offset: 0, color: 'rgba(59,130,246,0.8)' },
                      { offset: 1, color: 'rgba(16,185,129,0.8)' }
                    ])
                    : new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                      { offset: 0, color: 'rgba(16,185,129,0.8)' },
                      { offset: 1, color: 'rgba(245,158,11,0.8)' }
                    ]),
                  width: 2.5,
                  opacity: 0.75,
                  curveness: 0.05,
                  shadowBlur: 6,
                  shadowColor: link.type === 'mg'
                    ? 'rgba(59,130,246,0.4)'
                    : 'rgba(245,158,11,0.3)'
                }
              };
            }),
            lineStyle: {
              color: '#4b78c8',
              opacity: 0.6,
              width: 2,
              curveness: 0.05
            },
            emphasis: {
              focus: 'adjacency',
              lineStyle: {
                width: 4,
                opacity: 1
              }
            },
            animationDuration: 1200,
            animationEasingUpdate: 'elasticOut'
          }
        ]
      };

      this.chart.setOption(option, true);
    },

    handleResize () {
      if (this.chart) {
        this.chart.resize();
        this.renderChart();
      }
    },

    refreshData () {
      this.loadNodeData().then(() => {
        this.renderChart();
        this.$message({ message: this.$t('setting.refreshSuccess'), type: 'success', duration: 1500 });
      });
    }
  }
};
</script>

<style scoped>
.topology-container {
  width: 100%;
  height: 88vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #0f1e3a 0%, #142850 50%, #0d2137 100%);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: rgba(255, 255, 255, 0.05);
  border-bottom: 1px solid rgba(100, 160, 255, 0.15);
  backdrop-filter: blur(8px);
  flex-shrink: 0;
}

.title-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-icon {
  font-size: 20px;
}

.title-text {
  font-size: 18px;
  font-weight: 700;
  color: #e2e8f0;
  letter-spacing: 1px;
}

.last-refresh {
  font-size: 12px;
  color: #64748b;
  margin-left: 8px;
}

.legend-area {
  display: flex;
  align-items: center;
  gap: 20px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  flex-shrink: 0;
  box-shadow: 0 0 6px currentColor;
}

.manager-dot {
  background: #3b82f6;
  color: #3b82f6;
}

.gateway-dot {
  background: #10b981;
  color: #10b981;
}

.executor-dot {
  background: #f59e0b;
  color: #f59e0b;
}

.legend-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.legend-label {
  color: #cbd5e1;
  font-size: 13px;
  font-weight: 600;
}

.legend-desc {
  color: #64748b;
  font-size: 11px;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 18px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.5px;
  transition: all 0.25s ease;
  box-shadow: 0 2px 10px rgba(59, 130, 246, 0.4);
}

.refresh-btn:hover {
  background: linear-gradient(135deg, #60a5fa, #3b82f6);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.6);
  transform: translateY(-1px);
}

.refresh-btn:active {
  transform: translateY(0);
}

.btn-icon {
  font-size: 16px;
  display: inline-block;
  transition: transform 0.5s;
}

.refresh-btn.spinning .btn-icon {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.stats-bar {
  display: flex;
  gap: 12px;
  padding: 10px 24px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.02);
  border-bottom: 1px solid rgba(100, 160, 255, 0.08);
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 20px;
  border-radius: 10px;
  border: 1px solid transparent;
  min-width: 90px;
  transition: transform 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-num {
  font-size: 26px;
  font-weight: 800;
  line-height: 1;
}

.stat-label {
  font-size: 11px;
  margin-top: 4px;
  white-space: nowrap;
}

.manager-card {
  background: rgba(59, 130, 246, 0.12);
  border-color: rgba(59, 130, 246, 0.3);
}
.manager-card .stat-num { color: #60a5fa; }
.manager-card .stat-label { color: #93c5fd; }

.gateway-card {
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.3);
}
.gateway-card .stat-num { color: #34d399; }
.gateway-card .stat-label { color: #6ee7b7; }

.executor-card {
  background: rgba(245, 158, 11, 0.12);
  border-color: rgba(245, 158, 11, 0.3);
}
.executor-card .stat-num { color: #fbbf24; }
.executor-card .stat-label { color: #fcd34d; }

.total-card {
  background: rgba(148, 163, 184, 0.1);
  border-color: rgba(148, 163, 184, 0.25);
}
.total-card .stat-num { color: #e2e8f0; }
.total-card .stat-label { color: #94a3b8; }

.chart-wrapper {
  flex: 1;
  position: relative;
  padding: 8px;
  overflow: hidden;
}

#topology-chart {
  width: 100%;
  height: 100%;
  border-radius: 12px;
}

.loading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(10, 20, 40, 0.6);
  border-radius: 12px;
  z-index: 10;
  gap: 16px;
}

.spinner {
  width: 44px;
  height: 44px;
  border: 4px solid rgba(59, 130, 246, 0.2);
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-text {
  color: #94a3b8;
  font-size: 14px;
}

.empty-state {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  z-index: 5;
}

.empty-icon {
  font-size: 56px;
  opacity: 0.5;
}

.empty-text {
  font-size: 18px;
  color: #64748b;
  font-weight: 600;
}

.empty-sub {
  font-size: 13px;
  color: #475569;
}
</style>
