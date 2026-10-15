<template>
  <view class="page my-page">
    <view class="my-head"><view class="safe-top"/><view class="title">个人中心</view><view class="head-glow" /></view>
    <view class="body">
      <view class="profile card">
        <view class="avatar"><image v-if="user.avatar_url" :src="user.avatar_url" mode="aspectFill" /><view v-else class="neutral-avatar"><view class="head"/><view class="body-shape"/></view></view>
        <view class="profile-main"><view class="nickname">{{ user.nickname || '微信用户' }}</view><view class="uid">用户编号 {{ user.id || '--' }}</view></view><text class="profile-arrow">›</text>
      </view>
      <view class="section-label">服务管理</view>
      <view class="menu card">
        <view class="menu-row" @click="openOrders"><view class="menu-icon"><image src="/static/icons/services/document.svg" /></view><view class="menu-text"><text>服务订单</text><text class="menu-sub">查看付款与履约状态</text></view><text class="arrow">›</text></view>
        <button open-type="contact" class="menu-row"><view class="menu-icon"><text>客</text></view><view class="menu-text"><text>在线客服</text><text class="menu-sub">{{ settings.customer_service_hours || '工作日 09:00-18:00' }}</text></view><text class="arrow">›</text></button>
      </view>
      <view class="section-label">帮助与规则</view>
      <view class="menu card">
        <view class="menu-row" @click="openAgreement"><view class="menu-icon"><image src="/static/icons/services/shield.svg" /></view><view class="menu-text"><text>协议与隐私规则</text><text class="menu-sub">用户授权、隐私保护与免责声明</text></view><text class="arrow">›</text></view>
      </view>
    </view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { request } from '../../utils/request'

// 分享配置
onShareAppMessage(() => ({
  title: '车辆信息查询 - 个人中心',
  path: '/pages/index/index'
}))

const user=ref({})
const settings=ref(uni.getStorageSync('app_settings')||{})
onShow(async()=>{
  try{
    user.value=await request('/me')
    if(!settings.value.customer_service_hours){
      const data=await request('/bootstrap',{public:true})
      settings.value=data.settings||{}
      uni.setStorageSync('app_settings',settings.value)
    }
  }catch(error){uni.showToast({title:error.message,icon:'none'})}
})
function openOrders(){uni.navigateTo({url:'/pages/orders/orders'})}
function openAgreement(){uni.navigateTo({url:'/pages/agreement/agreement'})}
</script>
<style lang="scss" scoped>
.my-page{background:#f3f7fc}.my-head{height:350rpx;padding:0 30rpx;color:#fff;background:linear-gradient(145deg,#063d99,#1676e7);position:relative;overflow:hidden}.my-head::after,.head-glow{content:"";position:absolute;border-radius:50%;border:46rpx solid rgba(255,255,255,.06)}.my-head::after{width:300rpx;height:300rpx;right:-120rpx;bottom:-145rpx}.head-glow{width:210rpx;height:210rpx;left:-120rpx;top:100rpx}.safe-top{height:calc(var(--status-bar-height) + 18rpx)}.title{position:relative;top:30px;text-align:center;font-size:34rpx;font-weight:750}.body{padding:0 24rpx 50rpx;margin-top:-118rpx;position:relative;z-index:2}.profile{height:190rpx;padding:30rpx;display:flex;align-items:center;background:linear-gradient(135deg,#0d5eca,#1684ee);border-color:rgba(255,255,255,.2);color:#fff}.avatar{width:106rpx;height:106rpx;border-radius:50%;overflow:hidden;display:flex;align-items:center;justify-content:center;background:#e9f3ff;border:5rpx solid rgba(255,255,255,.7)}.avatar image{width:100%;height:100%}.neutral-avatar{width:100%;height:100%;position:relative;background:linear-gradient(145deg,#eef6ff,#c9dfff)}.neutral-avatar .head{position:absolute;width:34rpx;height:34rpx;border-radius:50%;background:#6f99ce;left:31rpx;top:20rpx}.body-shape{position:absolute;width:66rpx;height:40rpx;border-radius:34rpx 34rpx 0 0;background:#6f99ce;left:15rpx;bottom:8rpx}.profile-main{margin-left:24rpx;flex:1}.nickname{font-size:31rpx;font-weight:750}.uid{font-size:22rpx;color:#d4e7ff;margin-top:10rpx}.profile-arrow{font-size:42rpx;opacity:.75}.section-label{font-size:26rpx;color:#273750;font-weight:750;margin:34rpx 8rpx 15rpx}.menu{padding:0 24rpx}.menu-row{width:100%;height:128rpx;padding:0;background:#fff;border-radius:0;border-bottom:1rpx solid #edf0f4;display:flex;align-items:center;text-align:left;font-size:28rpx}.menu-row:last-child{border-bottom:0}.menu-icon{width:64rpx;height:64rpx;margin-right:20rpx;border-radius:17rpx;background:#eaf3ff;display:flex;align-items:center;justify-content:center;color:#176bdf;font-size:23rpx;font-weight:800}.menu-icon image{width:42rpx;height:42rpx}.menu-text{flex:1;display:flex;flex-direction:column}.menu-sub{font-size:21rpx;color:#98a2b1;margin-top:7rpx}.arrow{color:#a4aebe;font-size:38rpx}
</style>
