<template>
  <view class="page orders-page">
    <view class="page-head"><view class="title">服务订单</view><text>仅展示已付款订单及履约状态，不在此处展示查询结果</text></view>
    <view class="list" v-if="orders.length">
      <view class="order card" v-for="item in orders" :key="item.order_no">
        <view class="order-top"><text class="service-name">{{ item.service_name }}</text><text class="status" :class="item.status">{{ statusText(item.status) }}</text></view>
        <view class="order-row"><text>订单编号</text><text selectable>{{ item.order_no }}</text></view>
        <view class="order-row"><text>付款时间</text><text>{{ item.paid_at || '-' }}</text></view>
        <view class="support" v-if="['query_failed','payment_review'].includes(item.status)" @click="contactService(item.order_no)">联系客服处理 <text>›</text></view>
      </view>
    </view>
    <view class="empty" v-else><view class="empty-mark"><view/></view><view>暂无服务订单</view><text>完成付款后，订单记录会显示在这里</text></view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'

const STATUS_TEXT={success:'已完成',paid:'待履约',query_failed:'服务异常',querying:'处理中',payment_review:'待核对',refunded:'已退款',refunding:'退款中'}
const orders=ref([])
onShow(load)
async function load(){try{const data=await request('/orders');orders.value=data.data||[]}catch(error){uni.showToast({title:error.message||'订单加载失败',icon:'none'})}}
function statusText(value){return STATUS_TEXT[value]||'处理中'}
function contactService(orderNo){uni.navigateTo({url:`/pages/feedback/feedback?order_no=${encodeURIComponent(orderNo)}`})}
</script>
<style lang="scss" scoped>
.orders-page{padding-bottom:40rpx}.page-head{padding:42rpx 28rpx 24rpx}.page-head .title{font-size:38rpx;font-weight:800}.page-head text{display:block;margin-top:9rpx;color:#8490a2;font-size:22rpx}.list{padding:0 22rpx}.order{padding:0 28rpx;margin-bottom:20rpx}.order-top{height:92rpx;display:flex;align-items:center;justify-content:space-between;border-bottom:1rpx solid #edf0f4}.service-name{font-size:29rpx;font-weight:700}.status{padding:7rpx 14rpx;border-radius:20rpx;background:#e6f8f0;color:#09875c;font-size:20rpx}.status.paid{background:#eaf2ff;color:#175cd3}.status.querying,.status.payment_review{background:#fff4e0;color:#9d6200}.status.query_failed{background:#fdecec;color:#c43b33}.status.refunded,.status.refunding{background:#eef0f4;color:#6b7688}.order-row{display:flex;align-items:flex-start;justify-content:space-between;gap:24rpx;padding:21rpx 0;font-size:22rpx;border-bottom:1rpx solid #f0f2f6}.order-row>text:first-child{flex:none;color:#8b96a7}.order-row>text:last-child{color:#344158;text-align:right;word-break:break-all}.support{padding:22rpx 0;color:#175cd3;text-align:right;font-size:24rpx}.support text{font-size:30rpx}.empty{padding:180rpx 30rpx;text-align:center;color:#56647a}.empty-mark{width:96rpx;height:96rpx;margin:0 auto 22rpx;border-radius:26rpx;background:#eaf2ff;display:flex;align-items:center;justify-content:center}.empty-mark view{width:42rpx;height:50rpx;border:5rpx solid #175cd3;border-radius:5rpx;position:relative}.empty-mark view::after{content:"";position:absolute;left:8rpx;right:8rpx;top:15rpx;height:5rpx;background:#175cd3;box-shadow:0 13rpx 0 #175cd3}.empty>text{display:block;font-size:22rpx;margin-top:12rpx;color:#a1aab8}
</style>
