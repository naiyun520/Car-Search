<template>
  <view class="page my-page">
    <view class="my-head"><view class="safe-top"/><view class="title">个人中心</view><view class="welcome">服务记录与客户支持</view></view>
    <view class="body">
      <view class="profile card">
        <view class="avatar">{{ (user.nickname||'微信用户').substring(0,1) }}</view>
        <view class="profile-main"><view class="nickname">{{ user.nickname || '微信用户' }}</view><view class="uid">用户编号 {{ user.id || '--' }}</view></view>
      </view>
      <view class="section-label">服务管理</view>
      <view class="menu card">
        <view class="menu-row" @click="openOrders"><view class="menu-text"><text>服务订单</text><text class="menu-sub">查看付款与履约状态</text></view><text class="arrow">›</text></view>
        <button open-type="contact" class="menu-row"><view class="menu-text"><text>在线客服</text><text class="menu-sub">{{ settings.customer_service_hours || '工作日 09:00-18:00' }}</text></view><text class="arrow">›</text></button>
      </view>
      <view class="section-label">帮助与规则</view>
      <view class="menu card">
        <view class="menu-row" @click="openFeedback"><view class="menu-text"><text>投诉与建议</text><text class="menu-sub">提交问题反馈或服务建议</text></view><text class="arrow">›</text></view>
        <view class="menu-row" @click="openAgreement"><view class="menu-text"><text>协议与隐私规则</text><text class="menu-sub">用户授权、隐私保护与免责声明</text></view><text class="arrow">›</text></view>
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
function openFeedback(){uni.navigateTo({url:'/pages/feedback/feedback'})}
function openAgreement(){uni.navigateTo({url:'/pages/agreement/agreement'})}
</script>
<style lang="scss" scoped>
.my-page{background:#f3f6fa}.my-head{height:340rpx;padding:0 30rpx;color:#fff;background:linear-gradient(145deg,#092f73,#1461c9);position:relative;overflow:hidden}.my-head::after{content:"";position:absolute;width:300rpx;height:300rpx;border:50rpx solid rgba(255,255,255,.06);border-radius:50%;right:-130rpx;bottom:-125rpx}.safe-top{height:calc(var(--status-bar-height) + 18rpx)}.title{text-align:center;font-size:34rpx;font-weight:700}.welcome{font-size:42rpx;font-weight:800;margin-top:58rpx}.body{padding:0 24rpx 50rpx;margin-top:-62rpx;position:relative;z-index:2}.profile{height:170rpx;padding:30rpx;display:flex;align-items:center}.avatar{width:94rpx;height:94rpx;border-radius:24rpx;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#e8f2ff,#bfd8fb);color:#1155b6;font-size:40rpx;font-weight:800}.profile-main{margin-left:24rpx}.nickname{font-size:31rpx;font-weight:700}.uid{font-size:22rpx;color:#8995a7;margin-top:10rpx}.section-label{font-size:23rpx;color:#7f8a9b;margin:30rpx 8rpx 14rpx}.menu{padding:0 28rpx}.menu-row{width:100%;height:122rpx;padding:0;background:#fff;border-radius:0;border-bottom:1rpx solid #edf0f4;display:flex;align-items:center;text-align:left;font-size:28rpx}.menu-row:last-child{border-bottom:0}.menu-text{flex:1;display:flex;flex-direction:column}.menu-sub{font-size:21rpx;color:#98a2b1;margin-top:7rpx}.arrow{color:#a4aebe;font-size:38rpx}
</style>
