/*
 * DataPoly Manager UI — shared ECharts dark palette.
 *
 * Keeps chart colors in lockstep with src/styles/variables.css. The three
 * chart-bearing pages (dashboard, service/detail, setting/topology) merge
 * these fragments into their inline ECharts options.
 */

export const CHART_COLORS = [
  '#22d3ee', // cyan
  '#3b82f6', // electric blue
  '#818cf8', // indigo
  '#34d399', // green
  '#fbbf24', // amber
  '#f87171', // red
  '#f472b6'  // pink
]

export const CHART_TEXT_COLOR = '#94a3b8'
export const CHART_TEXT_PRIMARY = '#e2e8f0'
export const CHART_SPLIT_LINE = 'rgba(148, 163, 184, 0.12)'
export const CHART_AXIS_LINE = 'rgba(148, 163, 184, 0.25)'

/* merge into a chart's option: option = { ...option, ...CHART_BASE } */
export const CHART_BASE = {
  color: CHART_COLORS,
  textStyle: {
    color: CHART_TEXT_COLOR
  }
}

/* category/value axis fragment: axis = { ...axis, ...CHART_AXIS } */
export const CHART_AXIS = {
  axisLine: {
    lineStyle: { color: CHART_AXIS_LINE }
  },
  axisLabel: {
    color: CHART_TEXT_COLOR
  },
  splitLine: {
    lineStyle: { color: CHART_SPLIT_LINE }
  },
  axisTick: {
    lineStyle: { color: CHART_AXIS_LINE }
  }
}

/* tooltip fragment: tooltip = { ...tooltip, ...CHART_TOOLTIP } */
export const CHART_TOOLTIP = {
  backgroundColor: 'rgba(13, 20, 38, 0.92)',
  borderColor: 'rgba(59, 130, 246, 0.35)',
  textStyle: {
    color: CHART_TEXT_PRIMARY
  }
}

/* legend fragment: legend = { ...legend, ...CHART_LEGEND } */
export const CHART_LEGEND = {
  textStyle: {
    color: CHART_TEXT_COLOR
  }
}
