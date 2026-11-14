<template>
  <view class="page orders-page">
    <view class="page-head-bar"><view><view class="bar-title">订单记录</view><view class="bar-sub">付款记录与查询履约状态</view></view></view>
    <scroll-view scroll-x class="tabs" :show-scrollbar="false">
      <view class="tab-track">
        <view class="tab" :class="{ active: group === t.key }" v-for="t in TABS" :key="t.key" @click="switchGroup(t.key)">
          <text>{{ t.label }}</text>
        </view>
      </view>
    </scroll-view>
    <view class="list" v-if="orders.length">
      <view class="order card" v-for="item in orders" :key="item.order_no" @click="goDetail(item)">
        <view class="order-icon"><image :src="iconUrl(item)" mode="aspectFit" /></view>
        <view class="order-top">
          <view class="service-name">{{ item.service_name }}</view>
          <text class="status" :class="item.status">{{ statusText(item.status) }}</text>
        </view>
        <view class="order-row"><text>订单编号</text><text selectable>{{ item.order_no }}</text></view>
        <view class="order-row"><text>付款时间</text><text>{{ item.paid_at || '-' }}</text></view>
        <view class="auto-refund" v-if="item.payment_platform === 'android' && item.status === 'query_failed' && item.auto_refund_enabled">{{ refundText(item.refund_status) }}</view>
        <view class="support" v-if="item.payment_platform === 'ios' && item.status === 'query_failed' && item.refund_status !== 'refunded'" @click="showAppleRefund">iOS 退款说明 <text>›</text></view>
        <view class="support" v-if="['query_failed','payment_review'].includes(item.status)" @click="contactService">前往在线客服 <text>›</text></view>
        <view class="price">¥ {{ priceText(item.amount) }}</view>
      </view>
    </view>
    <view class="empty" v-else-if="!listState.loading && !listState.error"><view class="empty-mark"><view/></view><view>{{ emptyTitle }}</view><text>{{ emptyHint }}</text></view>
    <view class="list-feedback" v-if="listState.loading">正在加载订单…</view>
    <view class="list-feedback retry" v-else-if="listState.error" @click="retryLoad">{{ listState.error }} · 点击重试</view>
    <view class="list-feedback" v-else-if="orders.length && listState.finished">没有更多订单了</view>
  </view>
</template>
<script setup>
import { computed, reactive } from 'vue'
import { onLoad, onShow, onReachBottom, onShareAppMessage } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { API_BASE_URL } from '../../config'
import { serviceIcon } from '../../utils/service-icons'
import { createOrderPager } from '../../utils/order-list'

// 分享配置
onShareAppMessage(() => ({
  title: '车辆信息查询 - 查看服务订单',
  path: '/pages/index/index'
}))

const STATUS_TEXT={pending_payment:'待付款',success:'已完成',paid:'待履约',query_failed:'未查询到信息',querying:'处理中',payment_review:'待核对',refunded:'已退款',refunding:'退款中',cancelled:'已取消'}
// 分类口径与后端 OrderController.STATUS_GROUPS、「我的」页四项统计一致
const TABS=[{key:'all',label:'全部'},{key:'unpaid',label:'待付款'},{key:'querying',label:'查询中'},{key:'success',label:'已完成'},{key:'closed',label:'已关闭'}]
const listState=reactive({})
const pager=createOrderPager(args=>request(`/orders?group=${encodeURIComponent(args.group)}&page=${args.page}&page_size=${args.page_size}`),listState)
const orders=computed(()=>listState.orders)
const group=computed({get:()=>listState.group,set:value=>{listState.group=value}})
onLoad(options=>{const g=String(options?.group||'');if(TABS.some(t=>t.key===g))group.value=g})
onShow(()=>{
  const saved=String(uni.getStorageSync('orders_tab_group')||'')
  uni.removeStorageSync('orders_tab_group')
  if(TABS.some(t=>t.key===saved)&&saved!==group.value) return pager.reset(saved)
  return pager.refresh()
})
onReachBottom(()=>pager.more())
function switchGroup(key){if(group.value!==key)pager.reset(key)}
function retryLoad(){return pager.retry()}
const emptyTitle=computed(()=>group.value==='all'?'暂无订单记录':`暂无${TABS.find(t=>t.key===group.value)?.label||''}订单`)
const emptyHint=computed(()=>group.value==='all'?'完成付款后，订单记录会显示在这里':'切换上方分类可查看其他订单')
const iconUrl=item=>serviceIcon(item.service_icon,API_BASE_URL,item.service_code)
function statusText(value){return STATUS_TEXT[value]||'处理中'}
// 金额为元制小数（后端 amount），保留两位展示
function priceText(value){const n=Number(value);return Number.isFinite(n)?n.toFixed(2):'--'}
function refundText(value){return ({requesting:'退款申请提交中',processing:'退款处理中',refunded:'退款已完成',failed:'退款暂未成功，系统将自动重试'})[value]||'系统将自动退款'}
function contactService(){uni.switchTab({url:'/pages/my/my'})}
function goDetail(item){
  // 未付款仅显示说明，保留原订单记录，不引导生成新订单。
  if(item.status==='pending_payment'){
    return uni.showModal({
      title:'尚未付款',
      content:'该订单尚未完成付款，未发起查询，也没有查询报告。',
      showCancel:false,
      confirmText:'我知道了'
    })
  }
  if(item.status==='cancelled'){
    return uni.showModal({title:'订单已取消',content:'该订单因超时未支付已自动关闭，如需查询请重新下单。',showCancel:false,confirmText:'我知道了'})
  }
  uni.navigateTo({url:`/pages/result/result?order_no=${item.order_no}`})
}
function showAppleRefund(){
  uni.showModal({
    title:'iOS 订单退款',
    content:'未查询到数据，iOS 用户请参考 Apple 官方退款说明。最终退款结果由 Apple 决定。',
    confirmText:'复制网址',
    cancelText:'我知道了',
    success:result=>{if(result.confirm)uni.setClipboardData({data:'https://support.apple.com/zh-cn/118223'})}
  })
}
</script>
<style lang="scss" scoped>
.list-feedback{text-align:center;padding:28rpx 12rpx;color:#8a96a8;font-size:24rpx}.list-feedback.retry{color:#2177e8}
.orders-page{min-height:100vh;padding:14rpx 28rpx 44rpx;background:#f5faff}
.page-head-bar{display:none}
/* 状态分类条 */
.tabs{display:block;margin:8rpx 0 20rpx;white-space:nowrap}
.tab-track{display:inline-flex;gap:16rpx;padding:4rpx 2rpx}
.tab{flex:none;height:60rpx;padding:0 30rpx;display:flex;align-items:center;border-radius:30rpx;background:#fff;border:1rpx solid #e5edf6;color:#71829a;font-size:24rpx;white-space:nowrap}
.tab.active{background:linear-gradient(90deg,#2784ff,#1265f5);border-color:transparent;color:#fff;box-shadow:0 8rpx 18rpx rgba(18,101,245,.28)}
.list{padding:0}
.order{position:relative;min-height:184rpx;margin:0 0 18rpx;padding:22rpx 24rpx 22rpx 100rpx;border:1rpx solid #e6f0fb;border-radius:16rpx;background:#fff;box-shadow:0 6rpx 20rpx rgba(55,125,205,.07);overflow:hidden;transition:transform .15s ease}
.order-icon{position:absolute;left:28rpx;top:30rpx;width:52rpx;height:52rpx;display:flex;align-items:center;justify-content:center;border-radius:14rpx;background:#edf6ff}.order-icon image{width:52rpx;height:52rpx}
.order:active{transform:scale(.985)}
.order-top{min-height:39rpx;padding:0;border:0;display:flex;align-items:center;justify-content:space-between;gap:14rpx}
.service-name{min-width:0;overflow:hidden;color:#34435d;font-size:27rpx;font-weight:700;line-height:39rpx;white-space:nowrap;text-overflow:ellipsis}
.status{flex:none;padding:5rpx 12rpx;border-radius:7rpx;background:#dff8ed;color:#20bc87;font-size:18rpx;font-weight:600;line-height:27rpx}
.status.paid{background:#e5f1ff;color:#3d91f5}
.status.querying,.status.payment_review{background:#fff3dd;color:#ef9d27}
.status.query_failed{background:#fff0f0;color:#ff6b6b}
.status.refunded,.status.refunding,.status.cancelled{background:#f0f3f7;color:#8a96a8}
.status.pending_payment{background:#e5f1ff;color:#3d91f5}
.order-row{display:block;padding:0;border:0;color:#98a5b7;font-size:19rpx;line-height:28rpx}
/* 标题与首行订单信息留一点呼吸，行与行之间收紧（:first-of-type 在多 view 卡片中不命中，改用兄弟选择器） */
.order-top + .order-row{margin-top:10rpx}
.order-row + .order-row{margin-top:2rpx}
.order-row>text:first-child{display:none}
.order-row>text:last-child{display:block;overflow:hidden;color:#9aa7ba;text-align:left;white-space:nowrap;text-overflow:ellipsis;word-break:normal}
.order-row:first-of-type>text:last-child::before{content:"订单号："}
.order-row:nth-of-type(2)>text:last-child::before{content:"付款时间："}
.auto-refund{margin:14rpx 0 0;padding:12rpx 14rpx;border-radius:10rpx;background:#e8f8f1;color:#20a978;font-size:20rpx;line-height:30rpx}
.support{padding:14rpx 0 0;color:#3d91f5;text-align:right;font-size:21rpx;line-height:30rpx}
.support text{font-size:27rpx}
/* 订单价格：卡片右下角 */
.price{margin-top:12rpx;text-align:right;color:#1680f5;font-size:30rpx;font-weight:800;letter-spacing:1rpx;line-height:40rpx}
.empty{padding:190rpx 30rpx;text-align:center;color:#69788d}
.empty-mark{width:92rpx;height:92rpx;margin:0 auto 22rpx;border-radius:25rpx;background:#e9f4ff;display:flex;align-items:center;justify-content:center}
.empty-mark view{width:38rpx;height:47rpx;border:4rpx solid #4a9bff;border-radius:6rpx;position:relative}
.empty-mark view::after{content:"";position:absolute;left:7rpx;right:7rpx;top:13rpx;height:4rpx;background:#4a9bff;box-shadow:0 12rpx 0 #4a9bff}
.empty>text{display:block;margin-top:12rpx;color:#a2aebe;font-size:22rpx}
</style>
