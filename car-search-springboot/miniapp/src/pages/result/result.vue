<template>
  <view class="page result-page">
    <!-- 查询中状态 -->
    <view class="result-head querying-head" v-if="isQuerying">
      <view class="querying-icon">⏳</view>
      <view class="result-title">查询处理中</view>
      <view class="result-sub">供应商正在处理您的查询，请稍候...</view>
      <view class="querying-hint">预计等待时间：{{ order.estimated_wait || '1-3' }} 分钟</view>
      <view class="querying-elapsed">已等待 {{ elapsed }} 秒</view>
    </view>

    <!-- 正常结果头 -->
    <view class="result-head" :class="{ missing:isMissing }" v-else>
      <view class="result-icon">{{ isMissing ? 'i' : '✓' }}</view>
      <view class="result-title">{{ isMissing ? '未查询到信息' : '查询完成' }}</view>
      <view class="result-sub">{{ isMissing ? '本次查询没有返回可展示的数据' : `结果生成时间 ${order.queried_at || '-'}` }}</view>
    </view>

    <view class="missing-card card" v-if="isMissing">
      <view class="missing-title">未查询到数据</view>
      <template v-if="order.client_platform === 'ios'">
        <view class="missing-text">iOS 用户请自行参考 Apple 官方退款说明，并以 Apple 的最终处理结果为准。</view>
        <button class="apple-button" @click="openAppleRefund">查看 Apple 退款说明</button>
      </template>
      <template v-else-if="order.auto_refund_enabled">
        <view class="missing-text">本次查询未查询到数据，将自动退款。退款由微信支付在3分钟内自动原路返回至您的账户，请注意查收。</view>
        <view class="refund-state">{{ refundText }}</view>
      </template>
      <template v-else>
        <view class="missing-text">当前安卓退款需要联系客服，请凭订单编号联系客服处理。</view>
      </template>
    </view>

    <view class="result-card card" v-if="!isMissing && !isQuerying && order.result?.length">
      <view class="card-title">{{ order.service_name }}</view>
      <template v-for="item in order.result" :key="item.key">
        <!-- 数组字段：卡片列表 -->
        <view v-if="item.display==='array'&&Array.isArray(item.value)" class="result-array-section">
          <view class="array-label">{{ item.label }}（{{ item.value.length }}条）</view>
          <view v-if="isObjectArray(item.value)" class="array-cards">
            <view v-for="(row,idx) in item.value" :key="idx" class="array-card">
              <view v-for="(v,k) in row" :key="k" class="array-field"><text>{{ k }}</text><text>{{ formatVal(v) }}</text></view>
            </view>
          </view>
          <view v-else class="array-simple">{{ item.value.join('、') }}</view>
        </view>
        <!-- 嵌套对象 -->
        <view v-else-if="isPlainObject(item.value)" class="result-array-section">
          <view class="array-label">{{ item.label }}</view>
          <view class="array-card">
            <view v-for="(v,k) in item.value" :key="k" class="array-field"><text>{{ k }}</text><text>{{ formatVal(v) }}</text></view>
          </view>
        </view>
        <!-- 普通字段 -->
        <view v-else class="result-row"><text>{{ item.label }}</text><view>{{ Array.isArray(item.value)?item.value.join('、'):item.value }}</view></view>
      </template>
    </view>
    <view class="card order-card"><view><text>订单编号</text><text selectable>{{ order.order_no }}</text></view></view>

    <view class="warning-card card" v-if="!isMissing && !isQuerying">
      <view class="warning-icon">!</view>
      <view class="warning-content"><view class="warning-title">重要提示</view><view class="warning-text">查询结果仅在当前页面有效，退出后信息将自动销毁。如需再次查询，需重新付费。请务必自行保存查询结果。</view></view>
    </view>

    <view class="disclaimer">数据仅供合法授权场景参考，不作为行政、司法或交易决策的唯一依据。请勿截图传播包含个人敏感信息的查询结果。</view>
    <button class="primary-btn" @click="backHome">返回首页</button>
  </view>
</template>
<script setup>
import { computed, ref, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { checkoutRequest, request } from '../../utils/request'

const APPLE_REFUND_URL='https://support.apple.com/zh-cn/118223'
const order=ref({})
const elapsed=ref(0)
const isQuerying=computed(()=>order.value.status==='querying')
const isMissing=computed(()=>['query_failed','refunding','refunded'].includes(order.value.status)&&!order.value.result?.length)
const refundText=computed(()=>({requesting:'退款申请提交中',processing:'退款处理中',refunded:'退款已完成',failed:'退款暂未成功，系统将自动重试'})[order.value.refund_status]||'系统将自动发起退款')

let pollTimer=null
let elapsedTimer=null

onLoad(async options=>{
  try{
    order.value=await checkoutRequest('/checkout/status',{orderNo:String(options.order_no||'')})
    if(isQuerying.value) startPolling()
  }catch(error){uni.showModal({title:'提示',content:error.message,showCancel:false})}
})

onUnmounted(()=>{stopPolling()})

function startPolling(){
  elapsed.value=0
  elapsedTimer=setInterval(()=>{elapsed.value++},1000)
  pollTimer=setInterval(async()=>{
    try{
      const res=await request(`/query-status/${order.value.query_id}`)
      if(res.code===0&&(res.status==='completed'||res.status==='failed')){
        stopPolling()
        // 重新加载订单状态
        order.value=await checkoutRequest('/checkout/status',{orderNo:order.value.order_no})
      }
    }catch(e){console.warn('[Polling] 查询状态失败',e)}
  },5000) // 每5秒轮询一次
}

function stopPolling(){
  if(pollTimer){clearInterval(pollTimer);pollTimer=null}
  if(elapsedTimer){clearInterval(elapsedTimer);elapsedTimer=null}
}

function openAppleRefund(){uni.setClipboardData({data:order.value.apple_refund_url||APPLE_REFUND_URL,success:()=>uni.showToast({title:'链接已复制',icon:'success'})})}
function backHome(){uni.switchTab({url:'/pages/index/index'})}
function isPlainObject(v){return v!==null&&typeof v==='object'&&!Array.isArray(v)}
function isObjectArray(arr){return arr.length>0&&isPlainObject(arr[0])}
function formatVal(v){if(v===null||v===undefined||v==='')return'暂无数据';if(Array.isArray(v))return v.join('、');if(isPlainObject(v))return JSON.stringify(v);return String(v)}
</script>
<style lang="scss" scoped>
.result-page{padding:0 24rpx 55rpx}.result-head{text-align:center;padding:58rpx 0 38rpx}.result-icon{width:92rpx;height:92rpx;margin:auto;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#e6f8f0;color:#10a36c;font-size:48rpx;font-weight:700}.result-head.missing .result-icon{background:#e9f2ff;color:#176bdf;font-family:serif}.result-title{font-size:38rpx;font-weight:800;margin-top:20rpx}.result-sub{font-size:22rpx;color:#8b96a7;margin-top:8rpx}.missing-card{padding:32rpx;background:linear-gradient(145deg,#f0f6ff,#fff);border:1rpx solid #cfe1fb}.missing-title{font-size:30rpx;font-weight:800;color:#1b4f96}.missing-text{font-size:25rpx;line-height:1.75;color:#53657d;margin-top:14rpx}.refund-state{display:inline-flex;margin-top:22rpx;padding:9rpx 18rpx;border-radius:22rpx;background:#e6f8f0;color:#07875a;font-size:22rpx;font-weight:650}.apple-button{height:76rpx;line-height:76rpx;margin-top:24rpx;border:1rpx solid #176bdf;border-radius:14rpx;background:#fff;color:#176bdf;font-size:25rpx}.result-card{padding:0 30rpx}.card-title{padding:30rpx 0;font-size:30rpx;font-weight:700;border-bottom:1rpx solid #edf0f4}.result-row{min-height:86rpx;padding:18rpx 0;display:flex;align-items:center;justify-content:space-between;border-bottom:1rpx solid #edf0f4;font-size:26rpx}.result-row:last-child{border-bottom:0}.result-row>text{color:#7b8798}.result-row>view{max-width:65%;text-align:right;color:#223049;font-weight:600;word-break:break-all}.order-card{margin-top:20rpx;padding:18rpx 30rpx}.order-card view{display:flex;justify-content:space-between;gap:20rpx;padding:11rpx 0;font-size:22rpx;color:#8b96a7}.order-card text:last-child{text-align:right;word-break:break-all}.warning-card{margin-top:20rpx;padding:24rpx 28rpx;display:flex;gap:20rpx;background:#fff8f0;border:1rpx solid #ffe0b2}.warning-icon{width:44rpx;height:44rpx;border-radius:50%;background:#ff9500;color:#fff;font-size:28rpx;font-weight:700;display:flex;align-items:center;justify-content:center;flex-shrink:0}.warning-content{flex:1}.warning-title{font-size:26rpx;font-weight:700;color:#e65100;margin-bottom:8rpx}.warning-text{font-size:23rpx;color:#bf360c;line-height:1.6}.disclaimer{font-size:21rpx;color:#919baa;line-height:1.7;padding:28rpx 12rpx}.primary-btn{margin-top:10rpx}
.result-array-section{padding:18rpx 0;border-bottom:1rpx solid #edf0f4}
.result-array-section:last-child{border-bottom:0}
.array-label{font-size:25rpx;font-weight:700;color:#223049;margin-bottom:14rpx}
.array-cards{display:flex;flex-direction:column;gap:12rpx}
.array-card{padding:16rpx 20rpx;border:1rpx solid #e8edf4;border-radius:14rpx;background:#f8faff}
.array-field{display:flex;justify-content:space-between;align-items:flex-start;padding:8rpx 0;font-size:23rpx;border-bottom:1rpx solid #eef1f6}
.array-field:last-child{border-bottom:0}
.array-field text:first-child{color:#7b8798;flex:none;margin-right:16rpx}
.array-field text:last-child{color:#223049;text-align:right;word-break:break-all}
.array-simple{font-size:24rpx;color:#223049;line-height:1.6}

/* 查询中状态样式 */
.querying-head{background:linear-gradient(145deg,#f0f7fc,#e6f4ff);border-radius:16rpx;margin:0 -24rpx;padding:48rpx 24rpx}
.querying-icon{font-size:72rpx;margin-bottom:16rpx;animation:pulse 1.5s infinite}
.querying-hint{font-size:24rpx;color:#5b8bb5;margin-top:16rpx;padding:12rpx 24rpx;background:rgba(91,139,181,0.1);border-radius:8rpx;display:inline-block}
.querying-elapsed{font-size:22rpx;color:#8b96a7;margin-top:12rpx}
@keyframes pulse{0%,100%{transform:scale(1)}50%{transform:scale(1.1)}}
</style>
