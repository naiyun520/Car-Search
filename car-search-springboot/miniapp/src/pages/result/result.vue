<template>
  <view class="page result-page">
    <!-- ===== 首次进入：加载中 ===== -->
    <view class="loading-screen" v-if="loading">
      <view class="loading-ring" />
      <view class="loading-title">正在加载查询结果</view>
      <view class="loading-sub">正在读取订单信息，请稍候</view>
    </view>

    <!-- ===== 查询中状态 ===== -->
    <view class="querying-screen" v-else-if="isQuerying">
      <view class="query-visual" @click="manualRefresh">
        <view class="query-ring ring-back" />
        <view class="query-ring ring-progress" />
        <view class="car-mark">
          <view class="car-roof" />
          <view class="car-body"><view class="car-window" /></view>
          <view class="car-wheel wheel-left" /><view class="car-wheel wheel-right" />
        </view>
      </view>
      <view class="query-title">正在为您查询中...</view>
      <view class="query-subtitle">查询结果需一定时间，请耐心等待</view>

      <view class="query-steps">
        <view class="query-step"><view class="step-dot">✓</view><text>支付信息</text></view>
        <view class="step-line" />
        <view class="query-step active"><view class="step-dot"><view /></view><text>查询数据</text></view>
        <view class="step-line muted" />
        <view class="query-step pending"><view class="step-dot">3</view><text>生成报告</text></view>
      </view>

      <view class="query-wait">已等待 {{ elapsed }} 秒 · 下拉页面可刷新状态</view>
      <view class="security-note">
        <view class="shield-mark">✓</view>
        <view><view class="security-title">数据安全保障</view><view class="security-sub">全程加密传输，保障您的隐私安全</view></view>
      </view>
    </view>

    <!-- ===== 结果已过期（一级查询结果仅保留 24 小时） ===== -->
    <view class="status-banner banner-destroyed" v-else-if="isExpired">
      <view class="banner-icon">!</view>
      <view class="banner-main">
        <view class="banner-title">查询结果已过期</view>
        <view class="banner-sub">{{ order.message || '查询结果仅保留 24 小时，如需再次查看请重新查询' }}</view>
      </view>
    </view>

    <!-- ===== 查询完成 / 未查询到 ===== -->
    <template v-else>
      <view class="status-banner" :class="isMissing ? 'banner-missing' : 'banner-ok'">
        <view class="banner-icon">{{ isMissing ? 'i' : '✓' }}</view>
        <view class="banner-main">
          <view class="banner-title">{{ isDemo ? '示例报告' : (isMissing ? '未查询到信息' : '查询完成') }}</view>
          <view class="banner-sub">{{ isDemo ? '以下为模拟数据，仅供预览参考，非真实查询结果' : (isMissing ? '本次查询未返回可展示的数据' : `结果生成时间 ${order.queried_at || '-'}`) }}</view>
        </view>
      </view>

      <!-- 结果有效期：一级查询 24 小时、二级查询长期可看 -->
      <view class="retention-tip" v-if="retentionText">
        <text class="retention-icon">◷</text><text>{{ retentionText }}</text>
      </view>

      <!-- 未查询到 → 退款指引 -->
      <view class="card block" v-if="isMissing">
        <view class="block-title">退款与售后指引</view>
        <template v-if="order.client_platform === 'ios'">
          <view class="block-text">iOS 用户请自行参考 Apple 官方退款说明，并以 Apple 的最终处理结果为准。</view>
          <button class="ghost-btn block-btn" @click="openAppleRefund">查看 Apple 退款说明</button>
        </template>
        <template v-else-if="order.auto_refund_enabled">
          <view class="block-text">本次查询未查询到数据，系统将自动发起退款。退款由微信支付在 3 分钟内自动原路返回，请注意查收。</view>
          <view class="refund-pill">{{ refundText }}</view>
        </template>
        <template v-else>
          <view class="block-text">当前安卓退款需要联系客服，请凭订单编号联系客服处理。</view>
        </template>
      </view>

      <!-- ===== 有结果 → 专业权威展示 ===== -->
      <template v-if="!isMissing && order.result?.length">
        <!-- 品牌车型卡片（结果中带 brand_id / brand_logo 时展示） -->
        <view class="card brand-card" v-if="brandCard">
          <view class="brand-badge" v-if="brandCard.status">{{ brandCard.status }}</view>
          <image
            v-if="brandCard.logo && !logoFailed"
            class="brand-logo"
            :src="brandCard.logo"
            mode="aspectFit"
            @error="logoFailed = true"
          />
          <view v-else class="brand-logo brand-logo-fallback">{{ brandCard.initial }}</view>
          <view class="brand-info">
            <view class="brand-name">{{ brandCard.name }}</view>
            <view class="brand-line" v-if="brandCard.vin">
              <text class="brand-line-label">VIN</text>
              <text class="brand-line-value">{{ brandCard.vin }}</text>
            </view>
            <view class="brand-line" v-if="brandCard.plate">
              <text class="brand-line-label">车牌号</text>
              <text class="brand-line-value">{{ brandCard.plate }}</text>
            </view>
          </view>
        </view>

        <!-- 报告图片卡片：结果中值为图片 URL 的标量字段单独成卡，无图片自动隐藏 -->
        <template v-for="img in imageCards" :key="img.key">
          <view class="card image-card">
            <view class="block-title block-title-row">
              <text class="block-title-text">{{ img.label }}</text>
              <view class="image-copy" @click.stop="copyImageUrl(img.value)">复制链接</view>
            </view>
            <image
              v-if="!imgFailed[img.key]"
              class="report-image"
              :src="img.value"
              mode="widthFix"
              @click="previewImageCard(img.value)"
              @error="imgFailed[img.key] = true"
            />
            <view v-else class="image-fallback" @click="copyImageUrl(img.value)">{{ img.value }}</view>
          </view>
        </template>

        <!-- 商业险上险查询：到期区间解构卡 + 车架号独立卡（key 组合识别，见 insurance computed） -->
        <template v-if="insurance">
          <view class="card block insurance-card">
            <view class="block-title block-title-row">
              <text class="block-title-text">商业险到期时间</text>
              <view v-if="insuranceStatusText" class="ins-status" :class="'ins-' + insuranceStatusText.tone">{{ insuranceStatusText.text }}</view>
            </view>
            <view class="ins-main">{{ insuranceEnd.main }}</view>
            <view class="ins-months" v-if="insuranceEnd.months">{{ insuranceEnd.months }}</view>
          </view>
          <view class="card block vin-card" v-if="insurance.vinItem">
            <view class="block-title block-title-row">
              <text class="block-title-text">车架号</text>
              <view class="vin-copy" @click.stop="copyVin">复制</view>
            </view>
            <text class="vin-value" selectable>{{ formatVal(insurance.vinItem.value) }}</text>
          </view>
        </template>

        <!-- 理赔定损报告：是否全损 / 重大事故类型 / 理赔金额区间专属卡（key 组合识别，见 claimReport computed） -->
        <view class="card block claim-card" v-if="claimReport">
          <view class="block-title"><text class="block-title-text">定损结论</text></view>
          <view class="claim-status-row">
            <view class="claim-status" v-if="claimReport.loss">
              <text class="claim-status-label">是否全损</text>
              <text class="claim-status-value" :class="claimLossTone">{{ claimReport.lossText }}</text>
            </view>
            <view class="claim-status" v-if="claimReport.accident">
              <text class="claim-status-label">重大事故类型</text>
              <text class="claim-status-value" :class="claimAccidentTone">{{ claimReport.accidentText }}</text>
            </view>
          </view>
          <view class="claim-claims" v-if="claimReport.claimsText">
            <text class="claim-status-label">理赔金额区间及次数</text>
            <view class="claim-claims-main">{{ claimReport.claimsText.main }}</view>
            <view class="claim-claims-count" v-if="claimReport.claimsText.countText">{{ claimReport.claimsText.countText }}</view>
          </view>
        </view>

        <!-- 核验结论横幅（人车关系 / ETC 核验 / 人车核验 check_result） -->
        <view class="conclusion card" v-if="conclusion" :class="conclusion.toneClass">
          <view class="conclusion-icon">{{ conclusion.icon }}</view>
          <view class="conclusion-main">
            <view class="conclusion-label">{{ conclusion.label }}</view>
            <view class="conclusion-value">{{ conclusion.value }}</view>
            <view class="conclusion-hint">{{ conclusion.hint }}</view>
          </view>
        </view>

        <!-- 车辆档案头部（品牌 + 型号） -->
        <view class="card block profile-head" v-if="profile">
          <view class="profile-brand">
            <view class="profile-badge">{{ profile.badge }}</view>
            <view class="profile-name">{{ profile.name }}</view>
          </view>
          <view class="profile-model" v-if="profile.model">{{ profile.model }}</view>
          <view class="profile-chips" v-if="profile.chips.length">
            <view class="chip" v-for="(chip, i) in profile.chips" :key="i">
              <text class="chip-label">{{ chip.label }}</text>
              <text class="chip-value">{{ chip.value }}</text>
            </view>
          </view>
        </view>

        <!-- 统计大数字卡（过户次数 / 名下车辆 / ETC 数量） -->
        <view class="stat-grid" v-if="statCards.length">
          <view class="stat-card card" v-for="item in statCards" :key="item.key">
            <view class="stat-value">{{ item.value }}</view>
            <view class="stat-label">{{ item.label }}</view>
          </view>
        </view>

        <!-- 车辆参数档案：decode_data 内层 标题:内容 对扁平化为参数网格 -->
        <view class="card block" v-if="decodeCard">
          <view class="block-title"><text class="block-title-text">{{ decodeCard.label }}</text></view>
          <view class="field-grid">
            <view class="field-cell" v-for="(d, i) in decodeCard.fields" :key="i">
              <text class="field-label">{{ d.title }}</text>
              <text class="field-value">{{ d.content }}</text>
            </view>
          </view>
        </view>

        <!-- 结果分区 Tab 条（分区 ≥ 2 时展示） -->
        <view class="tab-bar" v-if="showTabs">
          <scroll-view scroll-x class="tab-scroll" :show-scrollbar="false">
            <view class="tab-track">
              <view
                class="tab-item"
                :class="{ active: activeTab === i }"
                v-for="(s, i) in sections"
                :key="s.key"
                @click="activeTab = i"
              >
                <text class="tab-label">{{ s.label }}</text>
                <view class="tab-underline" />
              </view>
            </view>
          </scroll-view>
        </view>

        <!-- 当前分区内容（有 Tab 时只渲染选中分区，否则线性排列） -->
        <template v-for="s in visibleSections" :key="s.key">
          <!-- 标量字段网格 -->
          <view class="card block" v-if="s.kind === 'fields'">
            <view class="block-title"><text class="block-title-text">{{ s.label }}</text></view>
            <view class="field-grid">
              <view class="field-cell" v-for="item in s.items" :key="item.key">
                <text class="field-label">{{ item.label }}</text>
                <text class="field-value">{{ formatVal(item.value) }}</text>
              </view>
            </view>
          </view>

          <!-- 安全风险独立卡片（新能源报告：等级胶囊 + 解析正文） -->
          <view class="card block safety-block" v-else-if="s.kind === 'safety'">
            <view class="block-title"><text class="block-title-text">{{ s.label }}</text></view>
            <view class="safety-level" v-if="safetyLevel">
              <text class="safety-level-label">{{ safetyLevel.label }}</text>
              <text class="safety-level-value" :class="'risk-' + riskTone(safetyLevel.value)">{{ formatVal(safetyLevel.value) }}</text>
            </view>
            <view class="safety-narrate" v-if="safetyNarrate">
              <text class="safety-narrate-label">{{ safetyNarrate.label }}</text>
              <text class="safety-narrate-text">{{ formatVal(safetyNarrate.value) }}</text>
            </view>
          </view>

          <!-- 数组字段：卡片行（角色推断）/ 传统行 -->
          <view class="card block" v-else-if="s.kind === 'array'">
            <view class="block-title block-title-row">
              <text class="block-title-text">{{ s.group.label }}（{{ s.group.value.length }} 条）</text>
              <view class="block-toggle" v-if="showToggle(s.group)" @click.stop="toggleExpand(s.key)">
                {{ expandedRows[s.key] ? '收起' : '查看全部' }}
              </view>
            </view>

            <!-- 方案A：推断出 title/main 角色时渲染记录卡片 -->
            <view class="rec-cards" v-if="s.roles">
              <view class="rec-card" v-for="(row, idx) in visibleRows(s.group)" :key="idx">
                <view class="rec-left">
                  <view class="rec-title">{{ formatVal(row[s.roles.title.key]) }}</view>
                  <view class="rec-left-label">{{ s.roles.title.label }}</view>
                </view>
                <view class="rec-center">
                  <view class="rec-main">
                    <text class="rec-main-label">{{ splitLabel(s.roles.main.label).label }}</text>
                    <text class="rec-main-value">{{ formatVal(row[s.roles.main.key]) }}</text>
                    <text class="rec-main-unit" v-if="splitLabel(s.roles.main.label).unit">{{ splitLabel(s.roles.main.label).unit }}</text>
                  </view>
                  <view class="rec-notes" v-if="s.roles.rest.length">
                    <text class="rec-note" v-for="c in s.roles.rest" :key="c.key">
                      {{ c.label }}<text class="rec-note-value">{{ formatVal(row[c.key]) }}</text>
                    </text>
                  </view>
                </view>
                <view class="rec-right" v-if="s.roles.badge && badgeTextFor(s.roles, row)">
                  <text class="rec-badge" :class="'badge-' + badgeTone(badgeTextFor(s.roles, row))">{{ badgeTextFor(s.roles, row) }}</text>
                </view>
              </view>
            </view>

            <!-- 条形统计图（两列：文本标签 + 数值，如维保明细统计） -->
            <view v-else-if="s.bar" class="bar-chart">
              <view class="bar-row" v-for="(row, idx) in visibleRows(s.group)" :key="idx">
                <text class="bar-label">{{ formatVal(row[s.bar.label.key]) }}</text>
                <view class="bar-track">
                  <view class="bar-fill" :style="{ width: barWidth(s.bar, row) + '%' }" />
                </view>
                <text class="bar-num">{{ formatVal(row[s.bar.value.key]) }}</text>
              </view>
            </view>

            <!-- 传统行渲染（无角色 / 列不足 / 非对象行） -->
            <view v-else-if="isObjectArray(s.group.value)" class="list-rows">
              <view class="list-row" v-for="(row, idx) in visibleRows(s.group)" :key="idx">
                <template v-if="s.group.columns && s.group.columns.length">
                  <view class="list-cell" v-for="col in s.group.columns" :key="col.key">
                    <text class="list-label">{{ col.label }}</text>
                    <view class="list-value icon-cell" v-if="iconChip(col, row)">
                      <view class="icon-chip" :class="'chip-' + iconChip(col, row).tone">
                        <text class="icon-chip-glyph">{{ iconChip(col, row).glyph }}</text>
                      </view>
                      <text class="icon-chip-text">{{ iconChip(col, row).text }}</text>
                    </view>
                    <text class="list-value" v-else>{{ formatVal(row[col.key]) }}</text>
                  </view>
                </template>
                <template v-else>
                  <view class="list-cell" v-for="(v, k) in row" :key="k">
                    <text class="list-label">{{ k }}</text>
                    <text class="list-value">{{ formatVal(v) }}</text>
                  </view>
                </template>
              </view>
            </view>
            <view v-else class="list-simple">{{ visibleRows(s.group).join('、') }}</view>
          </view>

          <!-- 嵌套对象字段 -->
          <view class="card block" v-else>
            <view class="block-title"><text class="block-title-text">{{ s.label }}</text></view>
            <view class="list-rows">
              <view class="list-row">
                <view class="list-cell" v-for="(v, k) in s.group.value" :key="k">
                  <text class="list-label">{{ k }}</text>
                  <text class="list-value">{{ formatVal(v) }}</text>
                </view>
              </view>
            </view>
          </view>
        </template>

        <!-- 订单信息 -->
        <view class="card block order-block">
          <view class="block-title">订单信息</view>
          <view class="order-line"><text>服务名称</text><text>{{ order.service_name || '-' }}</text></view>
          <view class="order-line"><text>订单编号</text><text selectable>{{ order.order_no }}</text></view>
          <view class="order-line" v-if="order.queried_at"><text>完成时间</text><text>{{ order.queried_at }}</text></view>
        </view>

        <!-- 重要提示 -->
        <view class="card block notice-block">
          <view class="notice-head"><view class="notice-dot" />重要提示</view>
          <view class="notice-text">{{ noticeText }}</view>
        </view>

        <view class="disclaimer">数据仅供合法授权场景参考，不作为行政、司法或交易决策的唯一依据。请勿截图传播包含个人敏感信息的查询结果。</view>
      </template>
    </template>

    <button class="primary-btn back-btn" v-if="isDemo && !loading" @click="goDemoQuery">立即查询新能源车信息</button>
    <button class="primary-btn back-btn" v-if="!loading" @click="backHome">返回首页</button>
  </view>
</template>

<script setup>
import { computed, ref, watch, onUnmounted } from 'vue'
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { checkoutRequest, request } from '../../utils/request'

const APPLE_REFUND_URL='https://support.apple.com/zh-cn/118223'
const order=ref({})
const elapsed=ref(0)
// 首次进入的加载态：数据回来前不渲染任何结果结构，避免空内容闪一下
const loading=ref(true)
const isQuerying=computed(()=>order.value.status==='querying')
const isMissing=computed(()=>['query_failed','refunding','refunded'].includes(order.value.status)&&!order.value.result?.length)
// 一级查询结果自生成起保留 24 小时，到期后后端不再返回结果（旧接口字段 result_destroyed 一并兼容）
const isExpired=computed(()=>order.value.result_expired===true||order.value.result_destroyed===true)
const refundText=computed(()=>({requesting:'退款申请提交中',processing:'退款处理中',refunded:'退款已完成',failed:'退款暂未成功，系统将自动重试'})[order.value.refund_status]||'系统将自动发起退款')

/* ===== 结果有效期展示：一级保留 24 小时、二级长期可看 ===== */
const remainBase=ref(0)
const remainLoadedAt=ref(0)
const nowTick=ref(Date.now())
let tickTimer=null
watch(order,o=>{
  remainBase.value=Number(o?.result_remaining_seconds||0)
  remainLoadedAt.value=Date.now()
  nowTick.value=Date.now()
})
const remainSeconds=computed(()=>{
  if(order.value.result_retention!=='limited')return null
  return Math.max(0,remainBase.value-Math.floor((nowTick.value-remainLoadedAt.value)/1000))
})
const retentionText=computed(()=>{
  const o=order.value
  if(isDemo.value||isExpired.value||o.status!=='success'||!o.result?.length)return ''
  if(o.result_retention==='permanent')return '本报告长期有效，可随时在订单记录中再次查看'
  if(o.result_retention==='limited'&&o.result_expires_at)return `结果保留至 ${o.result_expires_at}（剩余 ${durationText(remainSeconds.value)}）`
  return ''
})
function durationText(sec){
  const s=Math.max(0,Number(sec)||0)
  if(s<=0)return '已到期'
  if(s>=3600){
    const h=Math.floor(s/3600),m=Math.floor((s%3600)/60)
    return m>0?`${h} 小时 ${m} 分钟`:`${h} 小时`
  }
  return `${Math.max(1,Math.floor(s/60))} 分钟`
}
const noticeText=computed(()=>{
  if(isDemo.value)return '本页为报告示例，数据均为模拟生成，仅用于展示报告结构与内容。实际查询结果以付费查询返回为准。'
  if(order.value.result_retention==='permanent')return '本报告支持长期查看，可随时从订单记录再次进入。请勿截图传播包含个人敏感信息的查询结果。'
  return '查询结果自生成时间起保留 24 小时，到期后需重新查询。请及时保存重要信息。'
})

/* ===== 示例报告（?demo=battery）：首页「查看报告示例」入口 =====
   模拟新能源电池业务（id=31 energy_create_order）的报告数据，结构与后端
   ci_service.result_schema 平铺输出保持一致；纯前端假数据，不请求后端、不轮询。 */
const isDemo=ref(false)
function demoItem(key,label,value){return{key,label,value}}
function buildBatteryDemo(){
  return {
    status:'success',
    order_no:'DEMO-SAMPLE-0001',
    service_name:'新能源车信息查询（示例）',
    queried_at:'2026-10-28 10:30',
    result:[
      demoItem('order_id','查询单号','NE20261028001234'),
      demoItem('result.vin','车架号','LGWEF4A51NF123456'),
      demoItem('result.brand','品牌','比亚迪'),
      demoItem('query_status_text','查询状态','查询成功'),
      demoItem('result.query_time','查询时间','2026-10-28 10:30'),
      demoItem('result.market_name','车型名称','汉EV 2025款 尊享型'),
      demoItem('result.market_price','市场价','17.98万'),
      demoItem('result.rate_mileage','标称续航(km)','605'),
      demoItem('result.display_mileage','表显里程(km)','52318'),
      demoItem('result.manufacturer_date','生产年份','2024'),
      demoItem('result.refer_rate_mileage','当前参考续航(km)','580'),
      demoItem('result.refer_rate_mileage_assess','参考续航对比','正常'),
      demoItem('result.list_v2.battery_info.battery_type','电池类型','磷酸铁锂'),
      demoItem('result.list_v2.battery_health.battery_soh','电池健康度(%)','96.5'),
      demoItem('result.list_v2.battery_info.rate_capacity','标称电池容量','85.4kWh'),
      demoItem('result.list_v2.battery_charging.fast_ratio','快充占比','32%'),
      demoItem('result.list_v2.battery_info.nominal_energy','系统标称能量','85.4kWh'),
      demoItem('result.list_v2.battery_health.battery_soh_level','电池健康度等级','优秀'),
      demoItem('result.list_v2.battery_charging.total_charge_soc','循环次数','486'),
      demoItem('result.list_v2.battery_info.battery_manufacturer','电池厂商','弗迪电池'),
      demoItem('result.list_v2.battery_charging.fast_ratio_assess','快充占比评估','正常'),
      demoItem('result.list_v2.battery_charging.total_charge_count','总充电次数','620'),
      demoItem('result.list_v2.battery_charging.discharging_suggest','充放电建议','建议日常电量保持 20%~90%，避免长期满电或亏电停放；每周至少一次慢充满以校准电量计。'),
      demoItem('result.list_v2.battery_charging.battery_habit_assess','电池使用习惯评估','良好'),
      demoItem('result.list_v2.battery_health.volume_score_recession','电池衰退速率','0.8%/年'),
      demoItem('result.list_v2.battery_charging.discharging_attention','充放电注意事项','高温天气尽量选择阴凉处充电；快充后建议静置 10 分钟再行驶；长期停放请保持电量 50% 左右。'),
      {
        key:'result.list_v2.vehicle_info.check_display_mileage_list[]',
        label:'里程核验记录',
        display:'array',
        columns:[
          {key:'month',label:'核验时间'},
          {key:'hasErr',label:'是否异常'},
          {key:'displayMileage',label:'当月表显里程(km)'},
          {key:'remark',label:'核验备注'}
        ],
        value:[
          {month:'2026-09',hasErr:'正常',displayMileage:'52318',remark:'里程连续无异常'},
          {month:'2026-08',hasErr:'正常',displayMileage:'51102',remark:'里程连续无异常'},
          {month:'2026-07',hasErr:'正常',displayMileage:'49870',remark:'里程连续无异常'},
          {month:'2026-06',hasErr:'正常',displayMileage:'48515',remark:'里程连续无异常'},
          {month:'2026-05',hasErr:'异常',displayMileage:'47980',remark:'当月里程偏少，建议核对'},
          {month:'2026-04',hasErr:'正常',displayMileage:'46210',remark:'里程连续无异常'}
        ]
      },
      demoItem('result.list_v2.battery_health.volume_score_recession_level','安全风险水平等级','低'),
      demoItem('result.list_v2.battery_health.volume_score_recession_narrate','安全风险水平解析','电池衰退速率处于同车型正常区间，暂无安全风险，建议按周期继续关注。')
    ]
  }
}
/** 示例页 CTA：跳转该业务的真实查询页 */
function goDemoQuery(){uni.navigateTo({url:'/pages/query/query?code=energy_create_order'})}

/* ===== 结果分区（专业权威展示） ===== */
// 核验结论字段：state / status（后端已映射为 匹配/不匹配/查询成功无结果）；check_result 人车核验（后端映射 一致/不一致/查无）
const CONCLUSION_KEYS = new Set(['state', 'status', 'check_result'])
// count：个人/企业名下车辆状况查询的查得车辆总数（全库仅这两个业务使用顶层 count，嵌套的 content.count 不受影响）
const STAT_KEYS = new Set(['mingxia_num', 'guohu_num', 'count'])
// 车辆档案头部字段
const PROFILE_BADGE = { name: '车辆品牌', type: '车辆种类', usage: '使用性质' }

/* ===== 商业险上险查询（end_date 业务）：到期区间解构 + 车架号独立卡 =====
   识别：结果含 end_date key（当前仅该业务）；/checkout/status 不返回 service code，
   用结果 key 组合识别。status/end_date/vin 三个 key 由下方专属卡消费，不再进核验横幅与字段网格。 */
const insurance = computed(() => {
  const items = order.value.result || []
  const endItem = items.find(it => it.key === 'end_date')
  if (!endItem) return null
  return {
    endItem,
    statusItem: items.find(it => it.key === 'status'),
    vinItem: items.find(it => it.key === 'vin'),
    keys: new Set(['end_date', 'status', 'vin'])
  }
})
/** 区间值解析：(120，150] / [1,2) 等，兼容全角逗号与开闭括号 */
const INTERVAL_RE = /^[([]\s*(-?\d+(?:\.\d+)?)\s*[，,]\s*(-?\d+(?:\.\d+)?)\s*[)\]]$/
const insuranceEnd = computed(() => {
  const raw = String(insurance.value?.endItem?.value ?? '').trim()
  const m = INTERVAL_RE.exec(raw)
  if (!m) return { main: raw || '暂无数据', months: '' }
  const low = Number(m[1]), high = Number(m[2])
  const main = low === high ? `${low} 天` : `${low} ~ ${high} 天`
  // 月换算：下限够一个月量级才展示，避免"约 0~1 个月"这类噪音
  let months = ''
  if (low >= 30) {
    const ml = Math.round(low / 30), mh = Math.round(high / 30)
    months = ml === mh ? `约 ${ml} 个月后到期` : `约 ${ml}~${mh} 个月后到期`
  }
  return { main, months }
})
/** 状态胶囊：此业务 status 0=查无结果(脱保) 1=查询成功（后端通用枚举会把 1 翻成"匹配"、0 翻成"未知"） */
function insuranceStatus(value) {
  const t = String(value ?? '').trim()
  if (!t) return null
  if (t.includes('无结果') || t.includes('脱保')) return { text: '查无结果（脱保）', tone: 'warn' }
  if (t.includes('匹配') || t.includes('成功')) return { text: '查询成功', tone: 'ok' }
  return { text: t, tone: 'plain' }
}
const insuranceStatusText = computed(() => {
  const item = insurance.value?.statusItem
  return item ? insuranceStatus(item.value) : null
})
function copyVin() {
  const vin = String(insurance.value?.vinItem?.value ?? '').trim()
  if (!vin) return
  uni.setClipboardData({ data: vin, success: () => uni.showToast({ title: '已复制', icon: 'success' }) })
}

/* ===== 理赔定损报告（claim_assessment_report）：全损/事故/理赔区间专属卡 =====
   识别：结果含 report.total_loss 或 report.major_accident 或 report.claims_data（全库仅该业务）。
   后端 ResultValueFormatter 已翻译 total_loss/major_accident 枚举；claims_data 形如 (0,2000]:1 由前端解构。 */
const CLAIM_LOSS_MAP = { 0: '否', 1: '是（推定全损）' }
const CLAIM_ACCIDENT_MAP = { 0: '无大事故', 1: '碰撞', 2: '水淹', 3: '火烧', 4: '拆解' }
/** 理赔区间+次数解析：(0,2000]:1 → 主文案 0 ~ 2000 元 + 次数；多段用 ; 分隔 */
const CLAIM_SEG_RE = /^[([]\s*(-?\d+(?:\.\d+)?)\s*[，,]\s*(-?\d+(?:\.\d+)?)\s*[)\]]\s*[:：]\s*(\d+)$/
function parseClaimsData(raw) {
  const t = String(raw ?? '').trim()
  if (!t) return null
  const segs = t.split(/[;；]/).map(s => s.trim()).filter(Boolean)
  const ranges = []
  let total = 0
  let parsed = 0
  for (const seg of segs) {
    const m = CLAIM_SEG_RE.exec(seg)
    if (!m) continue
    parsed++
    const low = Number(m[1]), high = Number(m[2]), n = Number(m[3])
    ranges.push(low === high ? `${low} 元` : `${low} ~ ${high} 元`)
    total += n
  }
  if (!parsed) return { main: t, countText: '' }
  return {
    main: ranges.join('、'),
    countText: total > 0 ? `共理赔 ${total} 次` : '无理赔记录'
  }
}
function claimEnumText(value, map) {
  const t = String(value ?? '').trim()
  if (t in map) return map[t]
  return t || '暂无数据'
}
const claimReport = computed(() => {
  const items = order.value.result || []
  const find = key => items.find(it => it.key === key)
  const loss = find('report.total_loss')
  const accident = find('report.major_accident')
  const claims = find('report.claims_data')
  if (!loss && !accident && !claims) return null
  return {
    loss,
    accident,
    claims,
    lossText: loss ? claimEnumText(loss.value, CLAIM_LOSS_MAP) : '',
    accidentText: accident ? claimEnumText(accident.value, CLAIM_ACCIDENT_MAP) : '',
    claimsText: claims ? parseClaimsData(claims.value) : null,
    keys: new Set(['report.total_loss', 'report.major_accident', 'report.claims_data'])
  }
})
function claimLossTone(v) {
  const t = String(v ?? '')
  if (t.includes('是')) return 'claim-bad'
  if (t.includes('否')) return 'claim-ok'
  return 'claim-plain'
}
function claimAccidentTone(v) {
  const t = String(v ?? '')
  if (t.includes('无大事故')) return 'claim-ok'
  if (!t || t === '暂无数据') return 'claim-plain'
  return 'claim-bad'
}

const conclusion = computed(() => {
  if (insurance.value) return null // 商业险上险查询的 status 由专属卡展示，不进核验横幅
  const item = (order.value.result || []).find(it => CONCLUSION_KEYS.has(it.key))
  if (!item || item.value === null || item.value === undefined || item.value === '') return null
  const text = String(item.value)
  // 先判否定词：「不匹配/不一致」包含「匹配/一致」，顺序反了会把否定判成一致
  const isNone = text.includes('无结果') || text.includes('查无')
  const isMismatch = text.includes('不匹配') || text.includes('不一致')
  const isMatch = !isNone && !isMismatch && (text.includes('匹配') || text.includes('一致'))
  const hint = isNone ? '本次核验未查询到可核验的数据' : (isMatch ? '核验一致，身份与车辆关系匹配' : '核验不一致，身份与车辆关系不匹配')
  // check_result 的 schema label 带枚举说明（如"核验结果 1.一致 2.不一致 3.查无"），横幅只留短标题
  const label = item.key === 'check_result' ? '核验结果' : item.label
  return {
    label,
    value: text,
    hint,
    icon: isNone ? '!' : (isMatch ? '✓' : '×'),
    toneClass: isNone ? 'conclusion-none' : (isMatch ? 'conclusion-match' : 'conclusion-mismatch')
  }
})

const statCards = computed(() => (order.value.result || []).filter(it => STAT_KEYS.has(it.key)))

/* ===== 新能源报告独立卡片：安全风险 / 充电与使用建议 ===== */
// 字段 key 定义在 ci_service.result_schema（后端 ProviderService.normalize 按配置顺序平铺输出 key/label/value）。
// 这里按字段后缀匹配，避免不同环境 label 文案差异（如“安全风险等级/安全风险水平等级”）导致漏配。
const SAFETY_KEY_SUFFIXES = ['volume_score_recession_level', 'volume_score_recession_narrate']
const ADVICE_KEY_SUFFIXES = ['battery_soh_level', 'discharging_suggest', 'battery_habit_assess']
function hasResultSuffix(key, suffixes) {
  const k = String(key || '')
  return suffixes.some(s => k.endsWith(s))
}
const safetyItems = computed(() => (order.value.result || []).filter(it => hasResultSuffix(it.key, SAFETY_KEY_SUFFIXES)))
const adviceItems = computed(() => (order.value.result || []).filter(it => hasResultSuffix(it.key, ADVICE_KEY_SUFFIXES)))
const safetyLevel = computed(() => safetyItems.value.find(it => String(it.key).endsWith('volume_score_recession_level')) || null)
const safetyNarrate = computed(() => safetyItems.value.find(it => String(it.key).endsWith('volume_score_recession_narrate')) || null)
// 风险等级配色（设计规范 2.2 状态色）：高=风险 / 中=提醒 / 低=成功 / 无=未知
const RISK_HIGH_WORDS = ['高', '严重']
const RISK_MID_WORDS = ['中', '一般']
function riskTone(v) {
  const t = String(v ?? '').trim()
  if (!t || t === '暂无数据') return 'none'
  if (RISK_HIGH_WORDS.some(w => t.includes(w))) return 'high'
  if (RISK_MID_WORDS.some(w => t.includes(w))) return 'mid'
  return 'low'
}

/* ===== 品牌车型卡片：结果中带 brand_id / brand_logo 时展示 ===== */
const logoFailed = ref(false)
const BRAND_KEYS = {
  logo: ['result.brand_logo', 'brand_logo'],
  id: ['result.brand_id', 'brand_id'],
  name: ['result.market_name', 'market_name', 'result.brand', 'brand'],
  model: ['result.model_name', 'model_name'],
  vin: ['result.vin', 'vin'],
  plate: ['car_no', 'chepai', 'plate_number'],
  status: ['result.status_ssess', 'status_ssess', 'query_status_text']
}
const brandCard = computed(() => {
  const items = order.value.result || []
  const find = keys => items.find(it => keys.includes(it.key))
  const logo = find(BRAND_KEYS.logo)
  const brandId = find(BRAND_KEYS.id)
  if (!logo && !brandId) return null
  const nameItem = find(BRAND_KEYS.name)
  const modelItem = find(BRAND_KEYS.model)
  const vinItem = find(BRAND_KEYS.vin)
  const plateItem = find(BRAND_KEYS.plate)
  const statusItem = find(BRAND_KEYS.status)
  const name = String(nameItem?.value ?? '')
  const model = String(modelItem?.value ?? '')
  if (!name && !model) return null
  // 卡片已展示的字段从下方网格中隐藏，避免重复
  const usedKeys = new Set([...BRAND_KEYS.logo, ...BRAND_KEYS.id])
  if (nameItem) usedKeys.add(nameItem.key)
  if (modelItem) usedKeys.add(modelItem.key)
  if (vinItem) usedKeys.add(vinItem.key)
  if (plateItem) usedKeys.add(plateItem.key)
  if (statusItem) usedKeys.add(statusItem.key)
  return {
    logo: /^https?:\/\//.test(String(logo?.value ?? '')) ? String(logo.value) : '',
    name: name || model,
    initial: (name || model).trim().charAt(0),
    vin: vinItem ? String(vinItem.value ?? '') : '',
    plate: plateItem ? String(plateItem.value ?? '') : '',
    status: statusItem ? String(statusItem.value ?? '') : '',
    usedKeys
  }
})
// 换订单/换图时重置图片加载失败标记
watch(() => brandCard.value?.logo, () => { logoFailed.value = false })

/* ===== 报告图片卡片：值为图片 URL 的标量字段单独成卡，无图片自动隐藏 ===== */
const IMAGE_URL_RE = /^https?:\/\/\S+\.(png|jpe?g|gif|webp|bmp)(\?\S*)?$/i
const imageCards = computed(() => (order.value.result || []).filter(it =>
  !it.display && typeof it.value === 'string' && IMAGE_URL_RE.test(it.value.trim())
))
const imgFailed = ref({})
watch(() => order.value.order_no, () => { imgFailed.value = {} })
function previewImageCard(url) { uni.previewImage({ urls: [url] }) }
function copyImageUrl(url) {
  uni.setClipboardData({ data: url, success: () => uni.showToast({ title: '链接已复制', icon: 'success' }) })
}

const profile = computed(() => {
  const items = order.value.result || []
  const name = items.find(it => it.key === 'name')
  const model = items.find(it => it.key === 'name_info')
  if (!name) return null
  const chips = []
  const type = items.find(it => it.key === 'type')
  const usage = items.find(it => it.key === 'usage')
  const vin = items.find(it => it.key === 'vin')
  if (type) chips.push({ label: '车辆种类', value: String(type.value ?? '—') })
  if (usage) chips.push({ label: '使用性质', value: String(usage.value ?? '—') })
  if (vin) chips.push({ label: '车架号', value: String(vin.value ?? '—') })
  return { badge: PROFILE_BADGE.name, name: String(name.value ?? '—'), model: model ? String(model.value) : '', chips }
})

const fieldGroups = computed(() => {
  // 档案字段（vin/type/usage 等）只在档案头卡实际渲染（有 name）时才从网格隐藏；
  // 无 name 的业务（如车牌查车架号仅返回 vin）不渲染头卡，字段必须留在网格里展示
  const profileKeys = profile.value
    ? new Set(['name', 'name_info', 'vin', 'type', 'usage', ...STAT_KEYS, ...CONCLUSION_KEYS])
    : new Set([...STAT_KEYS, ...CONCLUSION_KEYS])
  const hiddenKeys = brandCard.value ? brandCard.value.usedKeys : new Set()
  return (order.value.result || []).filter(it => {
    if (profileKeys.has(it.key)) return false
    if (hiddenKeys.has(it.key)) return false
    if (insurance.value?.keys.has(it.key)) return false // 商业险专属卡已展示
    if (claimReport.value?.keys.has(it.key)) return false // 理赔定损专属卡已展示
    if (hasResultSuffix(it.key, SAFETY_KEY_SUFFIXES) || hasResultSuffix(it.key, ADVICE_KEY_SUFFIXES)) return false
    if (it.display === 'array' && Array.isArray(it.value)) return false
    if (isPlainObject(it.value)) return false
    if (IMAGE_URL_RE.test(String(it.value ?? '').trim())) return false // 图片 URL 走独立卡片
    return true
  })
})
/* ===== 车辆参数档案（历史里程查询 decode_data）：内层 title:content 对扁平化为参数网格 =====
   配置侧只配 "decode_data []"（不带子字段，子字段对嵌套数组不生效），后端整组原样输出；
   这里跳过 image 提示图与 bgColor/hide 等噪音字段，仅取文本项的 标题:内容 对。 */
const DECODE_KEY = 'decode_data[]'
const decodeCard = computed(() => {
  const g = (order.value.result || []).find(it => it.key === DECODE_KEY && Array.isArray(it.value))
  if (!g) return null
  const fields = []
  for (const row of g.value) {
    const list = Array.isArray(row?.list) ? row.list : []
    for (const item of list) {
      if (String(item?.type ?? 'text') !== 'text') continue // 仅文本项，跳过提示图等
      const title = String(item?.title ?? '').trim()
      const content = String(item?.content ?? '').trim()
      if (!title || !content || content === '-') continue // 空值/占位符不展示
      fields.push({ title, content })
    }
  }
  if (!fields.length) return null
  return { label: g.label || '车辆参数档案', fields }
})
// 参数档案走专属卡，从通用数组分区分流，避免渲染成噪音行
const arrayGroups = computed(() => (order.value.result || []).filter(it => it.display === 'array' && Array.isArray(it.value) && it.key !== DECODE_KEY))
const objectGroups = computed(() => (order.value.result || []).filter(it => !it.display && isPlainObject(it.value) && !STAT_KEYS.has(it.key)))

/* ===== 方案A：数组子字段卡片的角色推断 ===== */
const REC_PREVIEW = 4 // 卡片默认展示条数，超出折叠
const BADGE_VOCAB = {
  green: new Set(['正常', '查询成功', '成功', '一致', '匹配', '无', '有效', '通过', '未调表']),
  red: new Set(['异常', '查询失败', '失败', '不一致', '不匹配', '调表', '无效', '不通过']),
  blue: new Set(['查询中', '维修', '保养', '其他', '未知', '有'])
}
const TITLE_KEY_RE = /(date|time|month)/i
const BADGE_KEY_RE = /(status|schedule|is_|hasErr)/i
const NUMERIC_RE = /^-?\d[\d,]*(\.\d+)?(%|[kK][mM]|公里)?$/

/** 后端未格式化时的兜底翻译：schedule 0/1、hasErr true/false */
function rawBadgeText(key, v) {
  if (v === null || v === undefined) return ''
  const k = String(key || '')
  if (/schedule$/i.test(k)) {
    if (v === 0 || v === '0') return '正常'
    if (v === 1 || v === '1') return '调表'
  }
  if (/hasErr$/i.test(k)) {
    if (v === true || v === 'true') return '异常'
    if (v === false || v === 'false') return '正常'
  }
  return String(v)
}
function badgeTone(text) {
  if (BADGE_VOCAB.green.has(text)) return 'green'
  if (BADGE_VOCAB.red.has(text)) return 'red'
  if (BADGE_VOCAB.blue.has(text)) return 'blue'
  return 'gray'
}
/** 从 columns 推断 title（日期类）/ badge（状态类）/ main（数值类）/ rest（脚注）；无法推断返回 null 走传统渲染 */
function inferRoles(group) {
  const cols = group.columns || []
  if (cols.length < 2 || !isObjectArray(group.value)) return null
  const title = cols.find(c => TITLE_KEY_RE.test(c.key)) || null
  const others = cols.filter(c => c !== title)
  const sample = group.value[0] || {}
  // badge 优先取值命中词表的列，其次 key 命中状态类命名的列
  let badge = others.find(c => badgeTone(rawBadgeText(c.key, sample[c.key])) !== 'gray') || null
  if (!badge) badge = others.find(c => BADGE_KEY_RE.test(c.key)) || null
  const rest1 = others.filter(c => c !== badge)
  // main 取第一个数值形态的剩余列
  const main = rest1.find(c => NUMERIC_RE.test(rawBadgeText(c.key, sample[c.key]).trim())) || null
  if (!title || !main) return null
  return { title, main, badge, rest: rest1.filter(c => c !== main) }
}
const groupRoles = computed(() => {
  const m = {}
  for (const g of arrayGroups.value) m[g.key] = inferRoles(g)
  return m
})
/** 两列且恰一列全为数值 → 条形统计图（如维保明细统计：类型 + 数量）；无法判定返回 null */
function inferBarStats(group) {
  const cols = group.columns || []
  if (cols.length !== 2 || !isObjectArray(group.value) || !group.value.length) return null
  const numericCols = cols.filter(c => group.value.every(r => NUMERIC_RE.test(rawBadgeText(c.key, r[c.key]).trim())))
  if (numericCols.length !== 1) return null
  const value = numericCols[0]
  const label = cols.find(c => c !== value)
  // 标签列需含非数值，避免两列都是数字时误判
  if (!group.value.some(r => !NUMERIC_RE.test(rawBadgeText(label.key, r[label.key]).trim()))) return null
  const nums = group.value.map(r => Number(String(rawBadgeText(value.key, r[value.key])).replace(/[^\d.-]/g, '')) || 0)
  return { label, value, max: Math.max(...nums, 1) }
}
const barGroups = computed(() => {
  const m = {}
  for (const g of arrayGroups.value) m[g.key] = inferBarStats(g)
  return m
})
function barWidth(roles, row) {
  const cur = Number(String(rawBadgeText(roles.value.key, row[roles.value.key])).replace(/[^\d.-]/g, '')) || 0
  return Math.max(4, Math.round(cur / (roles.max || 1) * 100))
}

/* ===== 标识图标化：analysis_desc 类 icon 字段渲染为彩色图标 ===== */
const ICON_CHIPS = {
  success: { glyph: '✓', tone: 'ok', text: '正常' },
  warning: { glyph: '!', tone: 'warn', text: '警告' },
  unknown: { glyph: '?', tone: 'unknown', text: '未知' }
}
function iconChip(col, row) {
  if (col.key !== 'icon') return null
  return ICON_CHIPS[String(row[col.key] ?? '').trim()] || null
}
function badgeTextFor(roles, row) { return roles.badge ? rawBadgeText(roles.badge.key, row[roles.badge.key]) : '' }
/** "里程数(km)" → { label:'里程数', unit:'km' } */
function splitLabel(label) {
  const m = /^(.+?)\s*[（(]([^（）()]+)[）)]$/.exec(String(label || ''))
  return m ? { label: m[1], unit: m[2] } : { label: String(label || ''), unit: '' }
}

/* ===== 方案B：结果分区与 Tab 条 ===== */
const showTabs = computed(() => arrayGroups.value.length + objectGroups.value.length >= 2)
const activeTab = ref(0)
const expandedRows = ref({})
const sections = computed(() => {
  const list = []
  if (fieldGroups.value.length) {
    list.push({
      key: '__fields',
      kind: 'fields',
      label: showTabs.value ? '车辆信息' : (profile.value ? '车辆档案与配置参数' : '查询结果明细'),
      items: fieldGroups.value
    })
  }
  // 新能源报告：安全风险与充电建议拆分为独立卡片，紧跟主字段卡、排在数组/对象分组之前
  if (safetyItems.value.length) list.push({ key: '__safety', kind: 'safety', label: '安全风险评估', items: safetyItems.value })
  if (adviceItems.value.length) list.push({ key: '__advice', kind: 'fields', label: '充电与使用建议', items: adviceItems.value })
  for (const g of arrayGroups.value) list.push({ key: g.key, kind: 'array', label: g.label || '查询数据', group: g, roles: groupRoles.value[g.key], bar: barGroups.value[g.key] })
  for (const g of objectGroups.value) list.push({ key: g.key, kind: 'object', label: g.label || '查询数据', group: g })
  return list
})
const visibleSections = computed(() => (showTabs.value ? sections.value.slice(activeTab.value, activeTab.value + 1) : sections.value))
// 分区变少时防止 activeTab 越界；换订单时重置 Tab 与展开状态
watch(sections, list => { if (activeTab.value > list.length - 1) activeTab.value = Math.max(0, list.length - 1) })
watch(() => order.value.order_no, () => { activeTab.value = 0; expandedRows.value = {} })
function toggleExpand(key) { expandedRows.value = { ...expandedRows.value, [key]: !expandedRows.value[key] } }
/** 单键行数组（如 [{品牌:大众},{款型:…}]）合并为一行，避免每行一个空卡片 */
function isSingleKeyRows(rows) {
  if (rows.length === 0 || !rows.every(r => isPlainObject(r) && Object.keys(r).length === 1)) return false
  // 同键列表（多行都是 {name:…}）是同质清单，合并会让后值覆盖前值 → 保持逐行
  const keys = rows.map(r => Object.keys(r)[0])
  return new Set(keys).size === keys.length
}
function visibleRows(group) {
  const rows = Array.isArray(group.value) ? group.value : []
  if (!group.columns && isSingleKeyRows(rows)) {
    const merged = {}
    for (const r of rows) Object.assign(merged, r)
    return [merged]
  }
  return expandedRows.value[group.key] ? rows : rows.slice(0, REC_PREVIEW)
}
/** 折叠开关：单键合并行无需展开 */
function showToggle(group) {
  const rows = Array.isArray(group.value) ? group.value : []
  if (!group.columns && isSingleKeyRows(rows)) return false
  return rows.length > REC_PREVIEW
}

/* ===== 生命周期与轮询 ===== */
let pollTimer=null
let elapsedTimer=null
let currentOrderNo=''
let initialLoaded=false

onLoad(async options=>{
  // 示例报告模式：直接渲染本地模拟数据，不请求订单接口
  if(String(options?.demo||'')==='battery'){
    isDemo.value=true
    order.value=buildBatteryDemo()
    loading.value=false
    initialLoaded=true
    return
  }
  currentOrderNo=String(options.order_no||'')
  // 有效期倒计时每 30 秒刷新一次显示
  if(!tickTimer)tickTimer=setInterval(()=>{nowTick.value=Date.now()},30000)
  try{
    order.value=await checkoutRequest('/checkout/status',{orderNo:currentOrderNo})
    markViewedIfNeeded()
  }catch(error){uni.showModal({title:'提示',content:error.message,showCancel:false})}
  finally{loading.value=false;initialLoaded=true}
})

onShow(async()=>{
  if(!currentOrderNo) return
  // 首次进入由 onLoad 负责加载，这里跳过，避免同一页面并发两次订单请求
  if(!initialLoaded) return
  try{
    order.value=await checkoutRequest('/checkout/status',{orderNo:currentOrderNo})
    markViewedIfNeeded()
    if(isQuerying.value&&!pollTimer){
      startPolling()
    }else if(!isQuerying.value){
      stopPolling()
    }
  }catch(e){console.warn('[onShow] 刷新订单失败',e)}
})

onPullDownRefresh(async()=>{
  if(isDemo.value){uni.stopPullDownRefresh();return}
  await reloadOrder()
  uni.stopPullDownRefresh()
})

onUnmounted(()=>{stopPolling();if(tickTimer){clearInterval(tickTimer);tickTimer=null}})

function startPolling(){
  stopPolling()
  elapsed.value=0
  elapsedTimer=setInterval(()=>{elapsed.value++},1000)
  pollTimer=setInterval(async()=>{
    try{
      if(order.value.query_id){
        // 查询状态端点（含超时降级轮询触发），完成后回查订单详情
        const res=await request(`/query-status/${order.value.query_id}`)
        if(res&&(res.status==='completed'||res.status==='failed')){
          stopPolling()
          order.value=await checkoutRequest('/checkout/status',{orderNo:order.value.order_no})
          markViewedIfNeeded()
        }
      }else{
        // 无 query_id 时直接轮询订单状态，避免请求 /query-status/undefined
        order.value=await checkoutRequest('/checkout/status',{orderNo:order.value.order_no||currentOrderNo})
        if(order.value.status!=='querying') stopPolling()
        markViewedIfNeeded()
      }
    }catch(e){console.warn('[Polling] 查询状态失败',e)}
  },5000)
}

function stopPolling(){
  if(pollTimer){clearInterval(pollTimer);pollTimer=null}
  if(elapsedTimer){clearInterval(elapsedTimer);elapsedTimer=null}
}

async function manualRefresh(){await reloadOrder()}
async function reloadOrder(){
  if(isDemo.value)return
  try{
    const prev=order.value.status
    order.value=await checkoutRequest('/checkout/status',{orderNo:order.value.order_no||currentOrderNo})
    markViewedIfNeeded()
    if(prev==='querying'&&order.value.status!=='querying') stopPolling()
  }catch(e){console.warn('[reloadOrder] 刷新失败',e)}
}

function markViewedIfNeeded(){
  if(isDemo.value)return
  const o=order.value
  if(o.status==='success'&&!isExpired.value&&o.result?.length){
    checkoutRequest('/checkout/mark-viewed',{method:'POST',orderNo:o.order_no,data:{order_no:o.order_no}}).catch(()=>{})
  }
}
function openAppleRefund(){uni.setClipboardData({data:order.value.apple_refund_url||APPLE_REFUND_URL,success:()=>uni.showToast({title:'链接已复制',icon:'success'})})}
function backHome(){uni.switchTab({url:'/pages/index/index'})}
function isPlainObject(v){return v!==null&&typeof v==='object'&&!Array.isArray(v)}
function isObjectArray(arr){return arr.length>0&&isPlainObject(arr[0])}
function formatVal(v){
  if(v===null||v===undefined||v==='')return'暂无数据'
  if(Array.isArray(v))return v.map(cellText).join('、')
  if(isPlainObject(v))return cellText(v)
  return String(v)
}
/** 数组/对象单元格文本：{text,num}→“名称×数量”，其余对象→“键:值” */
function cellText(v){
  if(v===null||v===undefined||v==='')return'暂无数据'
  if(Array.isArray(v))return v.map(cellText).join('、')
  if(isPlainObject(v)){
    if('text'in v&&'num'in v)return`${v.text}×${v.num}`
    return Object.entries(v).map(([k,val])=>`${k}:${cellText(val)}`).join('、')
  }
  return String(v)
}
</script>

<style lang="scss" scoped>
.result-page{ padding: 28rpx 28rpx 44rpx; background:#F5F7FA; }

/* ===== 首次进入：加载动画 ===== */
.loading-screen{min-height:calc(100vh - 44rpx);display:flex;flex-direction:column;align-items:center;justify-content:center;padding:0 40rpx 120rpx;background:#fff}
.loading-ring{width:84rpx;height:84rpx;border-radius:50%;border:8rpx solid #E0EEFF;border-top-color:#2E7DFF;border-right-color:#2E7DFF;animation:rotate .9s linear infinite}
.loading-title{margin-top:34rpx;font-size:28rpx;font-weight:600;color:#315580}
.loading-sub{margin-top:12rpx;font-size:22rpx;color:#9AABC0}

/* ===== 查询中：支付完成后的过渡页 ===== */
.querying-screen{min-height:calc(100vh - 44rpx);padding:104rpx 30rpx 48rpx;background:#fff;text-align:center}
.query-visual{position:relative;width:224rpx;height:224rpx;margin:0 auto 38rpx}
.query-ring{position:absolute;inset:0;border-radius:50%}
.ring-back{border:14rpx solid #e4f2ff}
.ring-progress{border:14rpx solid transparent;border-top-color:#2388ff;border-right-color:#2388ff;border-bottom-color:#71b4ff;transform:rotate(-44deg);animation:query-rotate 2.2s linear infinite}
.car-mark{position:absolute;left:50%;top:50%;width:88rpx;height:72rpx;transform:translate(-50%,-45%)}
.car-roof{position:absolute;left:20rpx;top:9rpx;width:48rpx;height:26rpx;border-radius:18rpx 18rpx 5rpx 5rpx;background:#2e8dff}
.car-body{position:absolute;left:4rpx;top:30rpx;width:80rpx;height:30rpx;border-radius:10rpx 10rpx 8rpx 8rpx;background:linear-gradient(180deg,#5cacff,#1679f4);box-shadow:0 8rpx 14rpx rgba(37,137,255,.22)}
.car-window{position:absolute;left:26rpx;top:-14rpx;width:28rpx;height:16rpx;border-radius:7rpx 7rpx 2rpx 2rpx;background:#d9efff}
.car-wheel{position:absolute;z-index:2;bottom:5rpx;width:14rpx;height:14rpx;border:5rpx solid #176fd7;border-radius:50%;background:#fff}
.wheel-left{left:13rpx}.wheel-right{right:13rpx}
.query-title{color:#315580;font-size:32rpx;font-weight:700;letter-spacing:1rpx}
.query-subtitle{margin-top:10rpx;color:#9aabc0;font-size:22rpx}
.query-steps{display:flex;align-items:flex-start;margin:76rpx auto 0;max-width:470rpx;text-align:center}
.query-step{position:relative;z-index:1;flex:none;width:92rpx;color:#8496ac;font-size:21rpx}
.query-step text{display:block;margin-top:12rpx;white-space:nowrap}
.step-dot{width:30rpx;height:30rpx;margin:auto;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#dfeeff;color:#2986f5;font-size:18rpx;font-weight:700}
.query-step.active{color:#357dc4}.query-step.active .step-dot{background:#2e8dff;box-shadow:0 0 0 8rpx #e1f0ff}.query-step.active .step-dot view{width:10rpx;height:10rpx;border-radius:50%;background:#fff}
.query-step.pending{color:#adb9c7}.query-step.pending .step-dot{background:#edf3f8;color:#aab8c6}
.step-line{height:2rpx;flex:1;margin-top:14rpx;background:#91c7ff}.step-line.muted{background:#e3ebf2}
.query-wait{margin-top:40rpx;color:#a5b2c1;font-size:20rpx}
.security-note{display:flex;align-items:center;gap:16rpx;margin-top:94rpx;padding:21rpx 24rpx;border-radius:16rpx;background:linear-gradient(90deg,#f1f8ff,#f7fbff);text-align:left}
.shield-mark{width:38rpx;height:42rpx;flex:none;display:flex;align-items:center;justify-content:center;border-radius:12rpx 12rpx 18rpx 18rpx;background:#2e8dff;color:#fff;font-size:21rpx;font-weight:700}
.security-title{color:#4088d9;font-size:23rpx;font-weight:700}.security-sub{margin-top:2rpx;color:#9aaabd;font-size:19rpx}
@keyframes query-rotate{to{transform:rotate(316deg)}}

/* ===== 状态横幅 ===== */
.status-banner{ display:flex; align-items:center; gap:22rpx; padding:36rpx 30rpx; border-radius:24rpx; margin-bottom:26rpx; }
.banner-ok{ background:#E8F8F1; border:1rpx solid #B8E6D0; }
.banner-missing{ background:#E0EEFF; border:1rpx solid #BBD6FF; }
.banner-querying{ background:#E0EEFF; border:1rpx solid #BBD6FF; flex-wrap:wrap; }
.banner-destroyed{ background:#FDEFD8; border:1rpx solid #F5D9A8; }
.banner-icon{ width:88rpx; height:88rpx; border-radius:50%; flex:none; display:flex; align-items:center; justify-content:center; font-size:46rpx; font-weight:700; }
.banner-ok .banner-icon{ background:#10B981; color:#fff; }
.banner-missing .banner-icon{ background:#2E7DFF; color:#fff; font-family:serif; }
.banner-destroyed .banner-icon{ background:#F59E0B; color:#fff; }
.banner-main{ flex:1; min-width:0; }
.banner-title{ font-size:36rpx; font-weight:700; color:#1F2937; }
.banner-sub{ font-size:23rpx; color:#6B7280; margin-top:8rpx; line-height:1.6; }
.banner-extra{ font-size:22rpx; color:#2E7DFF; background:#E0EEFF; padding:8rpx 20rpx; border-radius:24rpx; }
.retention-tip{ display:flex; align-items:center; gap:8rpx; margin:-10rpx 0 26rpx; padding:12rpx 22rpx; border-radius:14rpx; background:#F2F7FE; color:#5B7FA8; font-size:21rpx; line-height:1.5; }
.retention-icon{ flex:none; color:#2E7DFF; font-size:24rpx; }
.banner-action{ height:68rpx; line-height:68rpx; margin:18rpx 0 0; padding:0 34rpx; border-radius:34rpx; background:#2E7DFF; color:#fff; font-size:24rpx; font-weight:600; width:100%; box-shadow:0 8rpx 18rpx rgba(46,125,255,.26); }
.spinner{ border:6rpx solid #BBD6FF; border-top-color:#2E7DFF; border-radius:50%; width:42rpx; height:42rpx; margin:10rpx; animation:rotate 1s linear infinite; }
@keyframes rotate{ to{ transform:rotate(360deg); } }

/* ===== 品牌车型卡片 ===== */
.brand-card{ position:relative; display:flex; align-items:center; gap:26rpx; padding:34rpx 30rpx; margin-bottom:26rpx; }
.brand-badge{ position:absolute; top:24rpx; right:26rpx; font-size:20rpx; font-weight:700; color:#0B7A56; background:#E8F8F1; border:1rpx solid #B8E6D0; padding:6rpx 20rpx; border-radius:20rpx; }
.brand-logo{ width:150rpx; height:104rpx; flex:none; border-radius:16rpx; background:#F5F7FA; }
.brand-logo-fallback{ display:flex; align-items:center; justify-content:center; font-size:52rpx; font-weight:700; color:#2E7DFF; background:#E0EEFF; }
.brand-info{ flex:1; min-width:0; }
.brand-name{ font-size:38rpx; font-weight:700; color:#1F2937; padding-right:130rpx; word-break:break-all; }
.brand-line{ display:flex; align-items:baseline; gap:14rpx; margin-top:12rpx; }
.brand-line-label{ flex:none; font-size:22rpx; color:#9CA3AF; }
.brand-line-value{ font-size:24rpx; font-weight:600; color:#1F2937; word-break:break-all; }

/* ===== 报告图片卡片 ===== */
.image-card{ padding:32rpx 30rpx; margin-bottom:26rpx; }
.image-copy{ flex:none; font-size:21rpx; font-weight:600; color:#2E7DFF; background:#E0EEFF; padding:6rpx 20rpx; border-radius:22rpx; }
.report-image{ display:block; width:100%; border-radius:16rpx; background:#F5F7FA; }
.image-fallback{ font-size:24rpx; color:#2E7DFF; line-height:1.7; word-break:break-all; background:#F5F7FA; border-radius:16rpx; padding:24rpx 26rpx; }

/* ===== 核验结论横幅 ===== */
.conclusion{ display:flex; align-items:center; gap:24rpx; padding:40rpx 30rpx; margin-bottom:26rpx; }
.conclusion-match{ background:#E8F8F1; border:1rpx solid #B8E6D0; }
.conclusion-mismatch{ background:#FEF2F2; border:1rpx solid #F5C2C2; }
.conclusion-icon{ width:100rpx; height:100rpx; border-radius:50%; flex:none; display:flex; align-items:center; justify-content:center; font-size:54rpx; font-weight:700; color:#fff; }
.conclusion-match .conclusion-icon{ background:#10B981; }
.conclusion-mismatch .conclusion-icon{ background:#EF4444; }
.conclusion-main{ flex:1; min-width:0; }
.conclusion-label{ font-size:22rpx; color:#6B7280; }
.conclusion-value{ font-size:46rpx; font-weight:700; margin-top:6rpx; letter-spacing:2rpx; }
.conclusion-match .conclusion-value{ color:#0B7A56; }
.conclusion-mismatch .conclusion-value{ color:#B91C1C; }
/* 查无结果：中性琥珀色，区别于"不一致"的红色误判 */
.conclusion-none{ background:#FDEFD8; border:1rpx solid #F5D9A8; }
.conclusion-none .conclusion-icon{ background:#F59E0B; }
.conclusion-none .conclusion-value{ color:#B45309; }
.conclusion-hint{ font-size:22rpx; color:#6B7280; margin-top:10rpx; line-height:1.6; }

/* ===== 商业险上险查询：到期区间卡 + 车架号卡 ===== */
.ins-status{ flex:none; padding:6rpx 20rpx; border-radius:20rpx; font-size:21rpx; font-weight:700; }
.ins-ok{ background:#E8F8F1; color:#0B7A56; border:1rpx solid #B8E6D0; }
.ins-warn{ background:#FDEFD8; color:#B45309; border:1rpx solid #F5D9A8; }
.ins-plain{ background:#F5F7FA; color:#6B7280; border:1rpx solid #EEF1F5; }
.ins-main{ text-align:center; font-size:64rpx; font-weight:800; color:#1F2937; letter-spacing:2rpx; line-height:1.3; margin-top:14rpx; }
.ins-months{ text-align:center; font-size:26rpx; font-weight:600; color:#2E7DFF; margin-top:10rpx; }
.vin-value{ display:block; font-size:36rpx; font-weight:700; color:#1F2937; letter-spacing:2rpx; word-break:break-all; margin-top:8rpx; }
.vin-copy{ flex:none; font-size:21rpx; font-weight:600; color:#2E7DFF; background:#E0EEFF; padding:6rpx 20rpx; border-radius:22rpx; }

/* ===== 理赔定损报告：定损结论卡 ===== */
.claim-status-row{ display:flex; gap:20rpx; margin-top:6rpx; }
.claim-status{ flex:1; background:#F5F7FA; border-radius:16rpx; padding:22rpx 20rpx; text-align:center; }
.claim-status-label{ display:block; font-size:20rpx; color:#9CA3AF; }
.claim-status-value{ display:block; font-size:32rpx; font-weight:700; margin-top:10rpx; line-height:1.4; word-break:break-all; }
.claim-ok{ color:#0B7A56; }
.claim-bad{ color:#DC2626; }
.claim-plain{ color:#6B7280; }
.claim-claims{ margin-top:20rpx; background:#F5F7FA; border-radius:16rpx; padding:22rpx 20rpx; text-align:center; }
.claim-claims-main{ font-size:44rpx; font-weight:800; color:#1F2937; margin-top:10rpx; letter-spacing:1rpx; }
.claim-claims-count{ font-size:24rpx; font-weight:600; color:#2E7DFF; margin-top:8rpx; }

/* ===== 车辆档案头部 ===== */
.profile-head{ padding:34rpx 30rpx; margin-bottom:26rpx; }
.profile-brand{ display:flex; align-items:center; gap:18rpx; }
.profile-badge{ font-size:20rpx; color:#2E7DFF; background:#E0EEFF; border:1rpx solid #BBD6FF; padding:6rpx 18rpx; border-radius:10rpx; font-weight:700; flex:none; }
.profile-name{ font-size:42rpx; font-weight:700; color:#1F2937; }
.profile-model{ font-size:26rpx; color:#6B7280; margin-top:14rpx; line-height:1.6; }
.profile-chips{ display:flex; flex-wrap:wrap; gap:16rpx; margin-top:26rpx; padding-top:26rpx; border-top:1rpx solid #F0F2F5; }
.chip{ flex:1; min-width:calc(33.33% - 12rpx); background:#F5F7FA; border-radius:16rpx; padding:18rpx 20rpx; }
.chip-label{ display:block; font-size:20rpx; color:#9CA3AF; }
.chip-value{ display:block; font-size:26rpx; font-weight:700; color:#1F2937; margin-top:6rpx; word-break:break-all; }

/* ===== 统计大数字卡 ===== */
.stat-grid{ display:flex; gap:20rpx; margin-bottom:26rpx; }
.stat-card{ flex:1; padding:36rpx 20rpx; text-align:center; }
.stat-value{ font-size:58rpx; font-weight:700; color:#2E7DFF; line-height:1.2; }
.stat-label{ font-size:22rpx; color:#9CA3AF; margin-top:10rpx; }

/* ===== 结果分区 Tab 条 ===== */
.tab-bar{ margin-bottom:26rpx; }
.tab-scroll{ width:100%; }
.tab-track{ display:inline-flex; align-items:flex-start; gap:16rpx; padding:4rpx 2rpx; }
.tab-item{ position:relative; flex:none; display:flex; flex-direction:column; align-items:center; }
.tab-label{ padding:16rpx 30rpx; border-radius:24rpx; background:#F5F7FA; color:#6B7280; font-size:24rpx; font-weight:600; line-height:1.2; white-space:nowrap; }
.tab-item.active .tab-label{ background:#2E7DFF; color:#fff; }
.tab-underline{ width:36rpx; height:6rpx; margin-top:8rpx; border-radius:3rpx; background:transparent; }
.tab-item.active .tab-underline{ background:#2E7DFF; }

/* ===== 参数网格 ===== */
.block{ padding:32rpx 30rpx; margin-bottom:26rpx; }
.block-title{ font-size:29rpx; font-weight:700; color:#1F2937; padding-bottom:20rpx; margin-bottom:20rpx; border-bottom:1rpx solid #F0F2F5; }
.block-title-row{ display:flex; align-items:center; justify-content:space-between; gap:20rpx; }
.block-toggle{ flex:none; font-size:21rpx; font-weight:600; color:#fff; background:#0958D9; padding:6rpx 20rpx; border-radius:22rpx; }
.field-grid{ display:flex; flex-wrap:wrap; gap:18rpx 0; }
.field-cell{ width:50%; padding:16rpx 22rpx 16rpx 0; }
.field-label{ display:block; font-size:20rpx; color:#9CA3AF; }
.field-value{ display:block; font-size:26rpx; font-weight:600; color:#1F2937; margin-top:6rpx; word-break:break-all; }

/* ===== 安全风险独立卡片 ===== */
.safety-level{ display:flex; align-items:center; justify-content:space-between; gap:20rpx; background:#F5F7FA; border-radius:16rpx; padding:24rpx 26rpx; }
.safety-level-label{ font-size:22rpx; color:#7A869A; }
.safety-level-value{ flex:none; font-size:30rpx; font-weight:700; padding:10rpx 34rpx; border-radius:26rpx; }
.risk-high{ color:#fff; background:#B42318; border:1rpx solid #B42318; }
.risk-mid{ color:#fff; background:#B54708; border:1rpx solid #B54708; }
.risk-low{ color:#fff; background:#027A48; border:1rpx solid #027A48; }
.risk-none{ color:#fff; background:#475467; border:1rpx solid #475467; }
.safety-narrate{ margin-top:22rpx; }
.safety-narrate-label{ display:block; font-size:20rpx; color:#7A869A; }
.safety-narrate-text{ display:block; margin-top:10rpx; font-size:25rpx; color:#344563; line-height:1.7; word-break:break-all; }

/* ===== 列表（数组/对象） ===== */
.list-rows{ display:flex; flex-direction:column; gap:16rpx; }
.list-row{ background:#F5F7FA; border:1rpx solid #EEF1F5; border-radius:16rpx; padding:18rpx 22rpx; }
.list-cell{ display:flex; justify-content:space-between; align-items:flex-start; gap:20rpx; padding:10rpx 0; border-bottom:1rpx solid #F0F2F5; }
.list-cell:last-child{ border-bottom:0; }
.list-label{ flex:none; font-size:22rpx; color:#9CA3AF; margin-right:16rpx; }
.list-value{ font-size:24rpx; font-weight:600; color:#1F2937; text-align:right; word-break:break-all; }
.list-simple{ font-size:25rpx; color:#1F2937; line-height:1.7; }

/* ===== 记录卡片行（方案A：角色推断） ===== */
.rec-cards{ display:flex; flex-direction:column; gap:16rpx; }
.rec-card{ display:flex; align-items:center; gap:20rpx; background:#F5F7FA; border:1rpx solid #EEF1F5; border-radius:16rpx; padding:22rpx 24rpx; }
.rec-left{ flex:none; width:210rpx; }
.rec-title{ font-size:27rpx; font-weight:700; color:#1F2937; line-height:1.35; word-break:break-all; }
.rec-left-label{ font-size:20rpx; color:#9CA3AF; margin-top:6rpx; }
.rec-center{ flex:1; min-width:0; }
.rec-main{ display:flex; align-items:baseline; gap:8rpx; flex-wrap:wrap; }
.rec-main-label{ font-size:22rpx; color:#6B7280; }
.rec-main-value{ font-size:32rpx; font-weight:700; color:#1F2937; word-break:break-all; }
.rec-main-unit{ font-size:22rpx; color:#6B7280; }
.rec-notes{ display:flex; flex-wrap:wrap; gap:6rpx 20rpx; margin-top:8rpx; }
.rec-note{ font-size:20rpx; color:#9CA3AF; }
.rec-note-value{ margin-left:6rpx; color:#6B7280; font-weight:600; }
.rec-right{ flex:none; }
.rec-badge{ display:inline-block; padding:8rpx 20rpx; border-radius:20rpx; font-size:21rpx; font-weight:700; white-space:nowrap; }
.badge-green{ background:#0B7A56; color:#fff; border:1rpx solid #0B7A56; }
.badge-red{ background:#B91C1C; color:#fff; border:1rpx solid #B91C1C; }
.badge-blue{ background:#1D4ED8; color:#fff; border:1rpx solid #1D4ED8; }
.badge-gray{ background:#475467; color:#fff; border:1rpx solid #475467; }

/* ===== 条形统计图 ===== */
.bar-chart{ display:flex; flex-direction:column; gap:24rpx; }
.bar-row{ display:flex; align-items:center; gap:18rpx; }
.bar-label{ flex:none; width:130rpx; font-size:24rpx; font-weight:600; color:#1F2937; }
.bar-track{ flex:1; height:26rpx; border-radius:13rpx; background:#E8EEF6; overflow:hidden; }
.bar-fill{ height:100%; border-radius:13rpx; background:linear-gradient(90deg,#5B9DFF,#2E7DFF); }
.bar-num{ flex:none; min-width:64rpx; text-align:right; font-size:26rpx; font-weight:700; color:#2E7DFF; }

/* ===== 标识图标（success/warning/unknown） ===== */
.icon-cell{ display:flex; align-items:center; gap:12rpx; justify-content:flex-end; }
.icon-chip{ width:40rpx; height:40rpx; border-radius:50%; display:flex; align-items:center; justify-content:center; flex:none; }
.icon-chip-glyph{ font-size:24rpx; font-weight:700; color:#fff; line-height:1; }
.chip-ok{ background:#10B981; }
.chip-warn{ background:#F59E0B; }
.chip-unknown{ background:#9CA3AF; }
.icon-chip-text{ font-size:24rpx; font-weight:600; color:#1F2937; }

/* ===== 订单信息 ===== */
.order-block .block-title{ margin-bottom:6rpx; }
.order-line{ display:flex; justify-content:space-between; gap:24rpx; padding:15rpx 0; font-size:23rpx; color:#6B7280; }
.order-line text:last-child{ color:#1F2937; text-align:right; word-break:break-all; }

/* ===== 重要提示 ===== */
.notice-block{ background:#FFFBF3; border-color:#F5E3C2; }
.notice-head{ display:flex; align-items:center; gap:12rpx; font-size:26rpx; font-weight:700; color:#B45309; margin-bottom:12rpx; }
.notice-dot{ width:14rpx; height:14rpx; border-radius:50%; background:#F59E0B; }
.notice-text{ font-size:23rpx; color:#92400E; line-height:1.7; }

/* ===== 退款与免责 ===== */
.block-text{ font-size:25rpx; color:#6B7280; line-height:1.75; }
.refund-pill{ display:inline-flex; margin-top:22rpx; padding:9rpx 22rpx; border-radius:24rpx; background:#E8F8F1; color:#10B981; font-size:22rpx; font-weight:600; }
.block-btn{ margin-top:22rpx; height:76rpx; line-height:76rpx; font-size:25rpx; }
.disclaimer{ font-size:21rpx; color:#9CA3AF; line-height:1.7; padding:14rpx 10rpx 26rpx; }
.back-btn{ margin-top:6rpx; }
</style>
