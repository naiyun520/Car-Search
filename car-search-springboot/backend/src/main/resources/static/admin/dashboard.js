let chartInstances = []

function disposeCharts() {
  chartInstances.forEach(chart => { try { chart.dispose() } catch (e) {} })
  chartInstances = []
}
window.disposeCharts = disposeCharts

function mountChart(id, option) {
  const el = document.getElementById(id)
  if (!el) return null
  if (typeof echarts === 'undefined') {
    el.innerHTML = '<div class="chart-fallback">图表组件未加载，请确认 echarts.min.js 已上传并强制刷新（Ctrl+F5）</div>'
    return null
  }
  try {
    const chart = echarts.init(el)
    chart.setOption(option)
    chartInstances.push(chart)
    return chart
  } catch (e) {
    el.innerHTML = '<div class="chart-fallback">图表渲染失败，请强制刷新（Ctrl+F5）后重试</div>'
    return null
  }
}

function disposeChartById(id) {
  const el = document.getElementById(id)
  if (!el || typeof echarts === 'undefined') return
  const chart = echarts.getInstanceByDom(el)
  if (chart) {
    try { chart.dispose() } catch (e) {}
    chartInstances = chartInstances.filter(c => c !== chart)
  }
}

window.addEventListener('resize', () => chartInstances.forEach(chart => chart.resize()))

const fmtMoney = value => '¥' + Number(value || 0).toFixed(2)

const axisLabel = { color: '#778399', fontSize: 11 }
const splitLine = { lineStyle: { color: '#eef2f7' } }
const axisLine = { lineStyle: { color: '#e8edf4' } }

let financeRequestId = 0
let financeData = null
let financeMetric = 'profit'

const financeMetrics = [
  { key: 'net', label: '实收', color: '#175cd3' },
  { key: 'refunds', label: '退款', color: '#d84b43' },
  { key: 'profit', label: '盈利', color: '#0a9362' },
  { key: 'cost', label: '接口成本', color: '#f59e0b' }
]

const financeMetricButtons = () => financeMetrics.map(m =>
  `<button type="button" data-metric="${m.key}" class="${m.key === financeMetric ? 'active' : ''}">${m.label}</button>`
).join('')

function renderFinanceBars() {
  const el = document.getElementById('chart-compare')
  if (!el) return
  if (!financeData) {
    el.innerHTML = '<div class="chart-fallback">正在加载…</div>'
    return
  }
  const metric = financeMetrics.find(m => m.key === financeMetric) || financeMetrics[0]
  disposeChartById('chart-compare')
  mountChart('chart-compare', {
    grid: { left: 8, right: 8, top: 30, bottom: 0, containLabel: true },
    tooltip: { trigger: 'axis', valueFormatter: v => '¥' + Number(v || 0).toFixed(2) },
    xAxis: { type: 'category', data: financeData.dates || [], axisLabel, axisLine, axisTick: { show: false } },
    yAxis: { type: 'value', name: metric.label, splitLine, axisLabel },
    series: [
      { name: metric.label, type: 'bar', barMaxWidth: 26, data: financeData[metric.key] || [], itemStyle: { color: metric.color, borderRadius: [4, 4, 0, 0] } }
    ]
  })
}

async function loadFinance(start, end) {
  const requestId = ++financeRequestId
  const el = document.getElementById('chart-compare')
  if (el) el.innerHTML = '<div class="chart-fallback">正在加载…</div>'
  const qs = [start && 'start=' + encodeURIComponent(start), end && 'end=' + encodeURIComponent(end)].filter(Boolean).join('&')
  let data
  try {
    data = await api('/dashboard-finance' + (qs ? '?' + qs : ''))
  } catch (e) {
    if (requestId !== financeRequestId) return
    if (el) el.innerHTML = '<div class="chart-fallback">经营数据加载失败，请重试</div>'
    return
  }
  if (requestId !== financeRequestId) return
  financeData = data
  const startInput = document.getElementById('finance-start')
  const endInput = document.getElementById('finance-end')
  if (startInput && !startInput.value) startInput.value = data.start || ''
  if (endInput && !endInput.value) endInput.value = data.end || ''
  renderFinanceBars()
}

function renderCharts(data) {
  const trend = data.trend || { dates: [], orders: [], sales: [], profit: [] }

  mountChart('chart-orders', {
    grid: { left: 8, right: 8, top: 30, bottom: 0, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: trend.dates, axisLabel, axisLine, axisTick: { show: false } },
    yAxis: { type: 'value', splitLine, axisLabel, minInterval: 1 },
    series: [{ name: '订单', type: 'line', smooth: true, symbolSize: 6, data: trend.orders, lineStyle: { width: 2, color: '#175cd3' }, itemStyle: { color: '#175cd3' }, areaStyle: { color: 'rgba(23,92,211,0.08)' } }]
  })

  mountChart('chart-money', {
    grid: { left: 8, right: 8, top: 40, bottom: 0, containLabel: true },
    tooltip: { trigger: 'axis' },
    legend: { data: ['实收', '利润'], top: 0, right: 0, textStyle: { color: '#59677c', fontSize: 12 } },
    xAxis: { type: 'category', data: trend.dates, axisLabel, axisLine, axisTick: { show: false } },
    yAxis: { type: 'value', splitLine, axisLabel },
    series: [
      { name: '实收', type: 'line', smooth: true, symbolSize: 5, data: trend.sales, lineStyle: { width: 2, color: '#0a9362' }, itemStyle: { color: '#0a9362' } },
      { name: '利润', type: 'line', smooth: true, symbolSize: 5, data: trend.profit, lineStyle: { width: 2, color: '#f59e0b' }, itemStyle: { color: '#f59e0b' }, areaStyle: { color: 'rgba(245,158,11,0.08)' } }
    ]
  })

  const pieBase = (name, list) => ({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 笔 ({d}%)' },
    legend: { bottom: 0, type: 'scroll', textStyle: { color: '#59677c', fontSize: 11 } },
    series: [{ name, type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'], avoidLabelOverlap: true, itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 }, label: { fontSize: 11, color: '#59677c' }, data: list }]
  })

  mountChart('chart-service', pieBase('服务', data.service_distribution || []))
  mountChart('chart-status', Object.assign(pieBase('状态', data.status_distribution || []), {
    color: ['#175cd3', '#f59e0b', '#a96600', '#0a9362', '#d84b43', '#8b5cf6', '#64748b', '#94a3b8', '#e11d48']
  }))
}

loaders.dashboard = async () => {
  disposeCharts()
  const data = await api('/dashboard')
  const p = data.periods || {}
  console.log('[dashboard] periods.today:', JSON.stringify(p.today))
  financeMetric = 'profit'
  financeData = null
  $('#content').innerHTML = `
    <div class="stats dashboard-stats">
      <div class="stat"><span>累计用户</span><b>${data.users ?? 0}</b></div>
      <div class="stat"><span>累计订单</span><b>${data.total_orders ?? 0}</b></div>
      <div class="stat"><span>今日订单</span><b>${p.today ? p.today.orders : 0}</b></div>
      <div class="stat revenue"><span>今日实收</span><b>${fmtMoney(p.today && p.today.revenue)}</b></div>
      <div class="stat refund"><span>今日退款</span><b>${fmtMoney(p.today && p.today.refunds)}</b></div>
      <div class="stat cost"><span>今日接口成本</span><b>${fmtMoney(p.today && p.today.cost)}</b></div>
      <div class="stat profit"><span>今日实时盈利</span><b>${fmtMoney(p.today && p.today.profit)}</b></div>
      <div class="stat"><span>待处理订单</span><b>${data.pending_orders ?? 0}</b></div>
      <div class="stat"><span>待处理异常</span><b>${data.failed_orders ?? 0}</b></div>
    </div>
    <div class="panel chart-panel">
      <div class="panel-head"><div><h3>经营数据</h3><p class="hint">默认展示最近一周，可切换指标与日期范围。</p></div><div class="finance-filter"><input type="date" id="finance-start" aria-label="开始日期"><span>至</span><input type="date" id="finance-end" aria-label="结束日期"></div></div>
      <div class="metric-tabs" id="finance-metric">${financeMetricButtons()}</div>
      <div id="chart-compare" class="chart-box"></div>
    </div>
    <div class="chart-grid">
      <div class="panel chart-panel"><div class="panel-head"><h3>近14天订单趋势</h3></div><div id="chart-orders" class="chart-box"></div></div>
      <div class="panel chart-panel"><div class="panel-head"><h3>近14天实收与利润</h3></div><div id="chart-money" class="chart-box"></div></div>
      <div class="panel chart-panel"><div class="panel-head"><h3>服务销量分布</h3></div><div id="chart-service" class="chart-box"></div></div>
      <div class="panel chart-panel"><div class="panel-head"><h3>订单状态分布</h3></div><div id="chart-status" class="chart-box"></div></div>
    </div>`
  renderCharts(data)
  loadFinance('', '')
  const startInput = $('#finance-start')
  const endInput = $('#finance-end')
  if (startInput && endInput) {
    const apply = () => {
      const s = startInput.value
      const e = endInput.value
      if (!s || !e) return
      if (s > e) {
        const box = document.getElementById('chart-compare')
        if (box) box.innerHTML = '<div class="chart-fallback">开始日期不能晚于结束日期</div>'
        return
      }
      loadFinance(s, e)
    }
    startInput.onchange = apply
    endInput.onchange = apply
  }
  const metricEl = $('#finance-metric')
  if (metricEl) {
    metricEl.onclick = event => {
      const btn = event.target.closest('[data-metric]')
      if (!btn) return
      metricEl.querySelectorAll('button').forEach(b => b.classList.remove('active'))
      btn.classList.add('active')
      financeMetric = btn.dataset.metric
      renderFinanceBars()
    }
  }
}
