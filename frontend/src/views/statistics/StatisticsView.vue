<template>
  <div class="page-container">
    <PageHeader title="统计报表" subtitle="数据口径由后端统一聚合，与 Excel 导出一致" />

    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="日期区间">
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="开始" end-placeholder="结束" :clearable="false" style="width: 260px" />
        </el-form-item>
        <el-form-item label="学院">
          <el-input v-model="query.college" placeholder="如 计算机学院" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="实验类型">
          <el-select v-model="query.experimentTypeId" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in types" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验室">
          <el-select v-model="query.labId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="item in labs" :key="item.id" :label="`${item.labNo} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="loadAll">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="export-row">
        <span class="text-secondary">导出（与页面同一份聚合结果）：</span>
        <el-button v-for="item in EXPORT_TYPES" :key="item.value" size="small" plain
                   :loading="exporting === item.value" @click="handleExport(item.value)">
          {{ item.label }}
        </el-button>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :xs="24" :sm="12" :lg="6" v-for="card in cards" :key="card.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value tabular">{{ card.value }}</div>
          <div class="stat-hint text-secondary">{{ card.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" v-loading="loading">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" header="资源占用率（Top 10 实验室）">
          <div ref="occupancyRef" class="chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" header="实验类型占比">
          <div ref="typeRef" class="chart" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="mt-16">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" header="预约成功率">
          <div ref="successRef" class="chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header>
            <div class="chart-header">
              <span>预约趋势</span>
              <el-radio-group v-model="groupBy" size="small" @change="loadTrend">
                <el-radio-button value="day">按天</el-radio-button>
                <el-radio-button value="week">按周</el-radio-button>
                <el-radio-button value="month">按月</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendRef" class="chart" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  EXPORT_TYPES,
  exportStatistics,
  getExperimentTypeRatio,
  getOccupancy,
  getOverview,
  getReservationTrend,
  getSuccessRate,
  type StatisticsOverview
} from '@/api/statistics'
import { listExperimentTypes, listLaboratoryOptions, type ExperimentTypeItem, type LaboratoryOption } from '@/api/resource'

/** 图表统一色板（暖赭 6 色，取自 axonhub 的 .claude 方案） */
const CHART_COLORS = ['#b05d2e', '#7a381d', '#2d6a4f', '#9c6644', '#d4a373', '#7c3aed']
const AXIS_LABEL_STYLE = { color: '#5c564f', fontSize: 12 }
const AXIS_LINE_STYLE = { lineStyle: { color: '#d8d4cf' } }
const SPLIT_LINE_STYLE = { lineStyle: { color: '#e3dfd9' } }
const CHART_BORDER = '#f5f5f4'

const loading = ref(false)
const exporting = ref<string | null>(null)
const overview = ref<StatisticsOverview | null>(null)
const types = ref<ExperimentTypeItem[]>([])
const labs = ref<LaboratoryOption[]>([])
const groupBy = ref<'day' | 'week' | 'month'>('day')

const today = new Date()
const firstDay = new Date(today.getFullYear(), today.getMonth(), 1)
const range = ref<[string, string]>([formatDate(firstDay), formatDate(today)])
const query = reactive<{ college: string; experimentTypeId: number | null; labId: number | null }>({
  college: '',
  experimentTypeId: null,
  labId: null
})

function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const cards = computed(() => [
  { label: '预约总数', value: overview.value?.reservationTotal ?? 0, hint: '含历史全部预约' },
  { label: '待审核', value: overview.value?.pendingAudit ?? 0, hint: 'PENDING 状态' },
  { label: '今日预约', value: overview.value?.todayReservation ?? 0, hint: formatDate(today) },
  { label: '排队中', value: overview.value?.queueTotal ?? 0, hint: '含待确认候补' }
])

const occupancyRef = ref<HTMLElement>()
const typeRef = ref<HTMLElement>()
const successRef = ref<HTMLElement>()
const trendRef = ref<HTMLElement>()
const charts: echarts.ECharts[] = []

function setChart(el: HTMLElement | undefined, option: echarts.EChartsOption): void {
  if (!el) {
    return
  }
  const chart = echarts.getInstanceByDom(el) ?? echarts.init(el)
  if (!charts.includes(chart)) {
    charts.push(chart)
  }
  chart.setOption(option, true)
}

function baseQuery() {
  return {
    beginDate: range.value[0],
    endDate: range.value[1],
    college: query.college || null,
    experimentTypeId: query.experimentTypeId,
    labId: query.labId
  }
}

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    const [overviewData, occupancy, ratio, success] = await Promise.all([
      getOverview(),
      getOccupancy(baseQuery()),
      getExperimentTypeRatio(baseQuery()),
      getSuccessRate(baseQuery())
    ])
    overview.value = overviewData
    await nextTick()
    setChart(occupancyRef.value, {
      color: CHART_COLORS,
      tooltip: { trigger: 'axis' },
      grid: { left: 8, right: 16, top: 20, bottom: 60, containLabel: true },
      xAxis: {
        type: 'category',
        data: occupancy.byLab.slice(0, 10).map((item) => item.labNo),
        axisLabel: { ...AXIS_LABEL_STYLE, rotate: 30 },
        axisLine: AXIS_LINE_STYLE,
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        max: 1,
        axisLabel: { ...AXIS_LABEL_STYLE, formatter: (v: number) => `${Math.round(v * 100)}%` },
        splitLine: SPLIT_LINE_STYLE
      },
      series: [{
        type: 'bar',
        name: '占用率',
        barWidth: 32,
        data: occupancy.byLab.slice(0, 10).map((item) => item.occupancyRate),
        itemStyle: { color: '#b05d2e', borderRadius: [6, 6, 0, 0] }
      }]
    })
    setChart(typeRef.value, {
      color: CHART_COLORS,
      tooltip: { trigger: 'item', formatter: '{b}: {c} 次（{d}%）' },
      legend: {
        bottom: 0,
        icon: 'circle',
        itemWidth: 8,
        itemHeight: 8,
        itemGap: 16,
        textStyle: AXIS_LABEL_STYLE
      },
      series: [{
        type: 'pie',
        radius: ['35%', '65%'],
        itemStyle: { borderColor: CHART_BORDER, borderWidth: 2 },
        data: ratio.items.map((item) => ({ name: item.name, value: item.count })),
        label: { formatter: '{b}\n{d}%', color: '#5c564f', fontSize: 12 }
      }]
    })
    setChart(successRef.value, {
      color: CHART_COLORS,
      tooltip: {},
      legend: {
        bottom: 0,
        icon: 'circle',
        itemWidth: 8,
        itemHeight: 8,
        itemGap: 16,
        textStyle: AXIS_LABEL_STYLE
      },
      series: [{
        type: 'pie',
        radius: ['50%', '70%'],
        label: { show: false },
        itemStyle: { borderColor: CHART_BORDER, borderWidth: 2 },
        data: [
          { name: '已通过', value: success.approved, itemStyle: { color: '#2d6a4f' } },
          { name: '已驳回', value: success.rejected, itemStyle: { color: '#ef4444' } },
          { name: '已取消', value: success.canceled, itemStyle: { color: '#8a8078' } },
          {
            name: '其他',
            value: Math.max(0, success.total - success.approved - success.rejected - success.canceled),
            itemStyle: { color: '#e3dfd9' }
          }
        ]
      }],
      graphic: [{
        type: 'text',
        left: 'center',
        top: '42%',
        style: {
          text: `${Math.round(success.successRate * 100)}%`,
          fill: '#292524',
          fontSize: 22,
          fontWeight: 'bold'
        }
      }]
    })
    await loadTrend()
  } finally {
    loading.value = false
  }
}

async function loadTrend(): Promise<void> {
  const trend = await getReservationTrend({ ...baseQuery(), groupBy: groupBy.value })
  await nextTick()
  setChart(trendRef.value, {
    color: CHART_COLORS,
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 16,
      textStyle: AXIS_LABEL_STYLE
    },
    grid: { left: 8, right: 16, top: 44, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: trend.items.map((item) => item.period),
      axisLabel: AXIS_LABEL_STYLE,
      axisLine: AXIS_LINE_STYLE,
      axisTick: { show: false }
    },
    yAxis: { type: 'value', axisLabel: AXIS_LABEL_STYLE, splitLine: SPLIT_LINE_STYLE },
    series: [
      {
        type: 'line',
        name: '总数',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2 },
        // 只给「总数」一条加面积渐变，避免四条线互相干扰
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(176, 93, 46, 0.18)' },
            { offset: 1, color: 'rgba(176, 93, 46, 0)' }
          ])
        },
        data: trend.items.map((item) => item.total)
      },
      {
        type: 'line',
        name: '已通过',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2 },
        data: trend.items.map((item) => item.approved)
      },
      {
        type: 'line',
        name: '已驳回',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2 },
        data: trend.items.map((item) => item.rejected)
      },
      {
        type: 'line',
        name: '已取消',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2 },
        data: trend.items.map((item) => item.canceled)
      }
    ]
  })
}

function handleReset(): void {
  query.college = ''
  query.experimentTypeId = null
  query.labId = null
  range.value = [formatDate(firstDay), formatDate(today)]
  void loadAll()
}

async function handleExport(type: string): Promise<void> {
  exporting.value = type
  try {
    const blob = await exportStatistics({ ...baseQuery(), type })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `统计报表_${type}_${Date.now()}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } finally {
    exporting.value = null
  }
}

function handleResize(): void {
  charts.forEach((chart) => chart.resize())
}

watch(range, () => {
  void loadAll()
})

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  const [typeList, labList] = await Promise.all([listExperimentTypes({}), listLaboratoryOptions({})])
  types.value = typeList
  labs.value = labList
  await loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach((chart) => chart.dispose())
})
</script>

<style scoped>
.stat-row {
  margin-bottom: var(--lr-card-gap);
}

.stat-card {
  text-align: left;
}

.stat-label {
  color: var(--lr-text-secondary);
  font-size: var(--lr-font-sm);
}

.stat-value {
  margin: 6px 0;
  font-size: var(--lr-font-2xl);
  font-weight: 700;
  color: var(--lr-text-primary);
  letter-spacing: -0.02em;
}

.stat-hint {
  font-size: var(--lr-font-xs);
}

.chart {
  width: 100%;
  height: 300px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.export-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}
</style>
