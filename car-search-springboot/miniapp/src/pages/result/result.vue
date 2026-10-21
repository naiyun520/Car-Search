<template>
  <view class="page result-page">
    <!-- ===== 查询中状态 ===== -->
    <view class="status-banner banner-querying" v-if="isQuerying">
      <view class="banner-icon spinner" />
      <view class="banner-main">
        <view class="banner-title">查询处理中</view>
        <view class="banner-sub">数据服务正在为您查询，预计等待 {{ order.estimated_wait || '1-3' }} 分钟</view>
      </view>
      <view class="banner-extra">已等待 {{ elapsed }} 秒</view>
      <button class="banner-action" @click="manualRefresh">刷新状态</button>
    </view>

    <!-- ===== 结果已销毁 ===== -->
    <view class="status-banner banner-destroyed" v-else-if="isDestroyed">
      <view class="banner-icon">!</view>
      <view class="banner-main">
        <view class="banner-title">结果已销毁</view>
        <view class="banner-sub">{{ order.message || '查询结果已销毁，退出后无法再次查看' }}</view>
      </view>
    </view>

    <!-- ===== 查询完成 / 未查询到 ===== -->
    <template v-else>
      <view class="status-banner" :class="isMissing ? 'banner-missing' : 'banner-ok'">
        <view class="banner-icon">{{ isMissing ? 'i' : '✓' }}</view>
        <view class="banner-main">
          <view class="banner-title">{{ isMissing ? '未查询到信息' : '查询完成' }}</view>
          <view class="banner-sub">{{ isMissing ? '本次查询未返回可展示的数据' : `结果生成时间 ${order.queried_at || '-'}` }}</view>
        </view>
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
        <!-- 核验结论横幅（人车关系 / ETC 核验类） -->
        <view class="conclusion card" v-if="conclusion" :class="conclusion.isMatch ? 'conclusion-match' : 'conclusion-mismatch'">
          <view class="conclusion-icon">{{ conclusion.isMatch ? '✓' : '×' }}</view>
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

        <!-- 车辆档案参数网格 -->
        <view class="card block" v-if="fieldGroups.length">
          <view class="block-title">{{ profile ? '车辆档案与配置参数' : '查询结果明细' }}</view>
          <view class="field-grid">
            <view class="field-cell" v-for="item in fieldGroups" :key="item.key">
              <text class="field-label">{{ item.label }}</text>
              <text class="field-value">{{ formatVal(item.value) }}</text>
            </view>
          </view>
        </view>

        <!-- 数组字段 -->
        <view class="card block" v-for="item in arrayGroups" :key="item.key">
          <view class="block-title">{{ item.label }}（{{ item.value.length }} 条）</view>
          <view v-if="isObjectArray(item.value)" class="list-rows">
            <view class="list-row" v-for="(row, idx) in item.value" :key="idx">
              <view class="list-cell" v-for="(v, k) in row" :key="k">
                <text class="list-label">{{ k }}</text>
                <text class="list-value">{{ formatVal(v) }}</text>
              </view>
            </view>
          </view>
          <view v-else class="list-simple">{{ item.value.join('、') }}</view>
        </view>

        <!-- 嵌套对象字段 -->
        <view class="card block" v-for="item in objectGroups" :key="item.key">
          <view class="block-title">{{ item.label }}</view>
          <view class="list-rows">
            <view class="list-row">
              <view class="list-cell" v-for="(v, k) in item.value" :key="k">
                <text class="list-label">{{ k }}</text>
                <text class="list-value">{{ formatVal(v) }}</text>
              </view>
            </view>
          </view>
        </view>

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
          <view class="notice-text">查询结果仅在当前页面有效，退出后信息将自动销毁。如需再次查询，需重新付费。请务必自行保存查询结果。</view>
        </view>

        <view class="disclaimer">数据仅供合法授权场景参考，不作为行政、司法或交易决策的唯一依据。请勿截图传播包含个人敏感信息的查询结果。</view>
      </template>
    </template>

    <button class="primary-btn back-btn" @click="backHome">返回首页</button>
  </view>
</template>

<script setup>
import { computed, ref, onUnmounted } from 'vue'
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { checkoutRequest, request } from '../../utils/request'

const APPLE_REFUND_URL='https://support.apple.com/zh-cn/118223'
const order=ref({})
const elapsed=ref(0)
const isQuerying=computed(()=>order.value.status==='querying')
const isMissing=computed(()=>['query_failed','refunding','refunded'].includes(order.value.status)&&!order.value.result?.length)
const isDestroyed=computed(()=>order.value.result_destroyed===true)
const refundText=computed(()=>({requesting:'退款申请提交中',processing:'退款处理中',refunded:'退款已完成',failed:'退款暂未成功，系统将自动重试'})[order.value.refund_status]||'系统将自动发起退款')

/* ===== 结果分区（专业权威展示） ===== */
// 核验结论字段：state / status（后端已映射为 匹配/不匹配/查询成功无结果/一致/不一致）
const CONCLUSION_KEYS = new Set(['state', 'status'])
const STAT_KEYS = new Set(['mingxia_num', 'guohu_num'])
// 车辆档案头部字段
const PROFILE_BADGE = { name: '车辆品牌', type: '车辆种类', usage: '使用性质' }

const conclusion = computed(() => {
  const item = (order.value.result || []).find(it => CONCLUSION_KEYS.has(it.key))
  if (!item || item.value === null || item.value === undefined || item.value === '') return null
  const text = String(item.value)
  const isMatch = text.includes('匹配') || text.includes('一致')
  const isNone = text.includes('无结果')
  const hint = isNone ? '本次核验未查询到可核验的数据' : (isMatch ? '核验一致，身份与车辆关系匹配' : '核验不一致，身份与车辆关系不匹配')
  return { label: item.label, value: text, isMatch, hint }
})

const statCards = computed(() => (order.value.result || []).filter(it => STAT_KEYS.has(it.key)))

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
  const profileKeys = new Set(['name', 'name_info', 'vin', 'type', 'usage', ...STAT_KEYS, ...CONCLUSION_KEYS])
  return (order.value.result || []).filter(it => {
    if (profileKeys.has(it.key)) return false
    if (it.display === 'array' && Array.isArray(it.value)) return false
    if (isPlainObject(it.value)) return false
    return true
  })
})
const arrayGroups = computed(() => (order.value.result || []).filter(it => it.display === 'array' && Array.isArray(it.value)))
const objectGroups = computed(() => (order.value.result || []).filter(it => !it.display && isPlainObject(it.value) && !STAT_KEYS.has(it.key)))

/* ===== 生命周期与轮询 ===== */
let pollTimer=null
let elapsedTimer=null
let currentOrderNo=''

onLoad(async options=>{
  currentOrderNo=String(options.order_no||'')
  try{
    order.value=await checkoutRequest('/checkout/status',{orderNo:currentOrderNo})
    markViewedIfNeeded()
  }catch(error){uni.showModal({title:'提示',content:error.message,showCancel:false})}
})

onShow(async()=>{
  if(!currentOrderNo) return
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
  await reloadOrder()
  uni.stopPullDownRefresh()
})

onUnmounted(()=>{stopPolling()})

function startPolling(){
  stopPolling()
  elapsed.value=0
  elapsedTimer=setInterval(()=>{elapsed.value++},1000)
  pollTimer=setInterval(async()=>{
    try{
      const res=await request(`/query-status/${order.value.query_id}`)
      if(res.code===0&&(res.status==='completed'||res.status==='failed')){
        stopPolling()
        order.value=await checkoutRequest('/checkout/status',{orderNo:order.value.order_no})
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
  try{
    const prev=order.value.status
    order.value=await checkoutRequest('/checkout/status',{orderNo:order.value.order_no||currentOrderNo})
    markViewedIfNeeded()
    if(prev==='querying'&&order.value.status!=='querying') stopPolling()
  }catch(e){console.warn('[reloadOrder] 刷新失败',e)}
}

function markViewedIfNeeded(){
  const o=order.value
  if(o.status==='success'&&!o.result_destroyed&&o.result?.length){
    checkoutRequest('/checkout/mark-viewed',{method:'POST',orderNo:o.order_no,data:{order_no:o.order_no}}).catch(()=>{})
  }
}
function openAppleRefund(){uni.setClipboardData({data:order.value.apple_refund_url||APPLE_REFUND_URL,success:()=>uni.showToast({title:'链接已复制',icon:'success'})})}
function backHome(){uni.switchTab({url:'/pages/index/index'})}
function isPlainObject(v){return v!==null&&typeof v==='object'&&!Array.isArray(v)}
function isObjectArray(arr){return arr.length>0&&isPlainObject(arr[0])}
function formatVal(v){
  if(v===null||v===undefined||v==='')return'暂无数据'
  if(Array.isArray(v))return v.join('、')
  if(isPlainObject(v))return JSON.stringify(v)
  return String(v)
}
</script>

<style lang="scss" scoped>
.result-page{ padding: 26rpx 24rpx 40rpx; }

/* ===== 状态横幅 ===== */
.status-banner{ display:flex; align-items:center; gap:22rpx; padding:34rpx 28rpx; border-radius:20rpx; margin-bottom:24rpx; }
.banner-ok{ background:linear-gradient(135deg,#eef6f1,#f8fbf9); border:1rpx solid #d3e6da; }
.banner-missing{ background:linear-gradient(135deg,#f2f6fb,#fafbfd); border:1rpx solid #dbe3ee; }
.banner-querying{ background:linear-gradient(135deg,#eef3f9,#f6f9fc); border:1rpx solid #dbe4ef; flex-wrap:wrap; }
.banner-destroyed{ background:linear-gradient(135deg,#faf5ec,#fdfaf6); border:1rpx solid #ecd9b8; }
.banner-icon{ width:84rpx; height:84rpx; border-radius:50%; flex:none; display:flex; align-items:center; justify-content:center; font-size:44rpx; font-weight:800; }
.banner-ok .banner-icon{ background:#16794d; color:#fff; }
.banner-missing .banner-icon{ background:#175cd3; color:#fff; font-family:serif; }
.banner-destroyed .banner-icon{ background:#a96a00; color:#fff; }
.banner-main{ flex:1; min-width:0; }
.banner-title{ font-size:34rpx; font-weight:800; color:#18243a; }
.banner-sub{ font-size:22rpx; color:#7d8b9e; margin-top:8rpx; line-height:1.6; }
.banner-extra{ font-size:22rpx; color:#5b7a9e; background:#e4edf7; padding:8rpx 18rpx; border-radius:24rpx; }
.banner-action{ height:64rpx; line-height:64rpx; margin:0; padding:0 34rpx; border-radius:32rpx; background:#175cd3; color:#fff; font-size:24rpx; font-weight:600; margin-top:18rpx; width:100%; }
.spinner{ border:6rpx solid #dbe4ef; border-top-color:#175cd3; border-radius:50%; width:40rpx; height:40rpx; margin:10rpx; animation:rotate 1s linear infinite; }
@keyframes rotate{ to{ transform:rotate(360deg); } }

/* ===== 核验结论横幅 ===== */
.conclusion{ display:flex; align-items:center; gap:24rpx; padding:38rpx 30rpx; margin-bottom:24rpx; }
.conclusion-match{ background:linear-gradient(135deg,#eaf5ef,#f6fbf8); border:1rpx solid #bfdec9; }
.conclusion-mismatch{ background:linear-gradient(135deg,#faf0ef,#fdf8f8); border:1rpx solid #e5c6c2; }
.conclusion-icon{ width:96rpx; height:96rpx; border-radius:50%; flex:none; display:flex; align-items:center; justify-content:center; font-size:52rpx; font-weight:800; color:#fff; }
.conclusion-match .conclusion-icon{ background:#16794d; }
.conclusion-mismatch .conclusion-icon{ background:#b23a35; }
.conclusion-main{ flex:1; min-width:0; }
.conclusion-label{ font-size:22rpx; color:#7d8b9e; }
.conclusion-value{ font-size:44rpx; font-weight:800; margin-top:6rpx; letter-spacing:2rpx; }
.conclusion-match .conclusion-value{ color:#125f3d; }
.conclusion-mismatch .conclusion-value{ color:#9c2f2a; }
.conclusion-hint{ font-size:22rpx; color:#7d8b9e; margin-top:10rpx; line-height:1.6; }

/* ===== 车辆档案头部 ===== */
.profile-head{ padding:32rpx 30rpx; margin-bottom:24rpx; }
.profile-brand{ display:flex; align-items:center; gap:18rpx; }
.profile-badge{ font-size:20rpx; color:#175cd3; background:#eaf3ff; border:1rpx solid #cfe0f4; padding:6rpx 16rpx; border-radius:8rpx; font-weight:700; flex:none; }
.profile-name{ font-size:40rpx; font-weight:800; color:#18243a; }
.profile-model{ font-size:26rpx; color:#56677e; margin-top:14rpx; line-height:1.6; }
.profile-chips{ display:flex; flex-wrap:wrap; gap:16rpx; margin-top:24rpx; padding-top:24rpx; border-top:1rpx solid #eef2f7; }
.chip{ flex:1; min-width:calc(33.33% - 12rpx); background:#f6f8fb; border-radius:12rpx; padding:16rpx 18rpx; }
.chip-label{ display:block; font-size:20rpx; color:#8a97a8; }
.chip-value{ display:block; font-size:26rpx; font-weight:700; color:#18243a; margin-top:6rpx; word-break:break-all; }

/* ===== 统计大数字卡 ===== */
.stat-grid{ display:flex; gap:20rpx; margin-bottom:24rpx; }
.stat-card{ flex:1; padding:34rpx 20rpx; text-align:center; }
.stat-value{ font-size:56rpx; font-weight:800; color:#175cd3; line-height:1.2; }
.stat-label{ font-size:22rpx; color:#8a97a8; margin-top:10rpx; }

/* ===== 参数网格 ===== */
.block{ padding:30rpx; margin-bottom:24rpx; }
.block-title{ font-size:28rpx; font-weight:750; color:#18243a; padding-bottom:20rpx; margin-bottom:20rpx; border-bottom:1rpx solid #eef2f7; }
.field-grid{ display:flex; flex-wrap:wrap; gap:18rpx 0; }
.field-cell{ width:50%; padding:16rpx 22rpx 16rpx 0; }
.field-label{ display:block; font-size:20rpx; color:#8a97a8; }
.field-value{ display:block; font-size:26rpx; font-weight:650; color:#18243a; margin-top:6rpx; word-break:break-all; }

/* ===== 列表（数组/对象） ===== */
.list-rows{ display:flex; flex-direction:column; gap:16rpx; }
.list-row{ background:#f6f8fb; border:1rpx solid #e8edf4; border-radius:14rpx; padding:18rpx 22rpx; }
.list-cell{ display:flex; justify-content:space-between; align-items:flex-start; gap:20rpx; padding:10rpx 0; border-bottom:1rpx solid #eef1f6; }
.list-cell:last-child{ border-bottom:0; }
.list-label{ flex:none; font-size:22rpx; color:#8a97a8; margin-right:16rpx; }
.list-value{ font-size:24rpx; font-weight:650; color:#18243a; text-align:right; word-break:break-all; }
.list-simple{ font-size:25rpx; color:#344158; line-height:1.7; }

/* ===== 订单信息 ===== */
.order-block .block-title{ margin-bottom:6rpx; }
.order-line{ display:flex; justify-content:space-between; gap:24rpx; padding:15rpx 0; font-size:23rpx; color:#64748b; }
.order-line text:last-child{ color:#344158; text-align:right; word-break:break-all; }

/* ===== 重要提示 ===== */
.notice-block{ background:#fbf8f2; border-color:#efe4cd; }
.notice-head{ display:flex; align-items:center; gap:12rpx; font-size:26rpx; font-weight:750; color:#8a6200; margin-bottom:12rpx; }
.notice-dot{ width:14rpx; height:14rpx; border-radius:50%; background:#c99833; }
.notice-text{ font-size:23rpx; color:#7a6a45; line-height:1.7; }

/* ===== 退款与免责 ===== */
.block-text{ font-size:25rpx; color:#56677e; line-height:1.75; }
.refund-pill{ display:inline-flex; margin-top:22rpx; padding:9rpx 20rpx; border-radius:24rpx; background:#eaf5ef; color:#16794d; font-size:22rpx; font-weight:650; }
.block-btn{ margin-top:22rpx; height:76rpx; line-height:76rpx; font-size:25rpx; }
.disclaimer{ font-size:21rpx; color:#9aa5b3; line-height:1.7; padding:14rpx 10rpx 26rpx; }
.back-btn{ margin-top:6rpx; }
</style>
