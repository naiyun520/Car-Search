<template>
  <view class="page result-page">
    <view class="success-head"><view class="success-icon">✓</view><view class="success-title">查询完成</view><view class="success-sub">结果生成时间 {{ order.queried_at || '-' }}</view></view>
    <view class="result-card card" v-if="order.result?.length"><view class="card-title">{{ order.service_name }}</view><view class="result-row" v-for="item in order.result" :key="item.key"><text>{{ item.label }}</text><view :class="{long:Array.isArray(item.value)}">{{ Array.isArray(item.value)?item.value.join('、'):item.value }}</view></view></view>
    <view class="card order-card"><view><text>订单编号</text><text>{{ order.order_no }}</text></view></view>
    <view class="disclaimer">数据仅供合法授权场景参考，不作为行政、司法或交易决策的唯一依据。请勿截图传播包含个人敏感信息的查询结果。</view>
    <button class="primary-btn" @click="backHome">返回首页</button>
  </view>
</template>
<script setup>
import { ref } from 'vue'; import { onLoad } from '@dcloudio/uni-app'; import { checkoutRequest } from '../../utils/request'
const order=ref({})
onLoad(async options=>{try{order.value=await checkoutRequest('/checkout/status',{orderNo:String(options.order_no||'')})}catch(error){uni.showModal({title:'提示',content:error.message,showCancel:false})}})
function backHome(){uni.switchTab({url:'/pages/index/index'})}
</script>
<style lang="scss" scoped>
.result-page{padding:0 24rpx 55rpx}.success-head{text-align:center;padding:58rpx 0 38rpx}.success-icon{width:92rpx;height:92rpx;margin:auto;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#e6f8f0;color:#10a36c;font-size:48rpx;font-weight:700}.success-title{font-size:38rpx;font-weight:800;margin-top:20rpx}.success-sub{font-size:22rpx;color:#8b96a7;margin-top:8rpx}.result-card{padding:0 30rpx}.card-title{padding:30rpx 0;font-size:30rpx;font-weight:700;border-bottom:1rpx solid #edf0f4}.result-row{min-height:86rpx;padding:18rpx 0;display:flex;align-items:center;justify-content:space-between;border-bottom:1rpx solid #edf0f4;font-size:26rpx}.result-row:last-child{border-bottom:0}.result-row>text{color:#7b8798}.result-row>view{max-width:65%;text-align:right;color:#223049;font-weight:600;word-break:break-all}.order-card{margin-top:20rpx;padding:18rpx 30rpx}.order-card view{display:flex;justify-content:space-between;padding:11rpx 0;font-size:22rpx;color:#8b96a7}.disclaimer{font-size:21rpx;color:#919baa;line-height:1.7;padding:28rpx 12rpx}.primary-btn{margin-top:10rpx}
</style>
