<template>
  <view class="page my-page">
    <view class="my-head"><view class="safe-top"/><view class="title">我的</view><view class="head-glow" /></view>
    <view class="body">
      <view class="profile card">
        <view class="profile-top">
          <view class="avatar"><image v-if="user.avatar_url" :src="user.avatar_url" mode="aspectFill" /><view v-else class="neutral-avatar"><view class="head"/><view class="body-shape"/></view></view>
          <view class="profile-main">
            <view class="nickname">{{ user.nickname || '微信用户' }} <text class="nick-arrow">›</text></view>
            <view class="uid">用户编号 {{ user.id || '--' }}</view>
          </view>
        </view>
        <view class="profile-stats">
          <view class="qstat" v-for="cell in statCells" :key="cell.key" @click="openOrderGroup(cell.group)">
            <view class="qstat-num">{{ statText(cell.key) }}</view>
            <view class="qstat-label">{{ cell.label }}</view>
          </view>
        </view>
      </view>

      <view class="menu card">
        <view class="menu-row" @click="openOrders">
          <view class="menu-icon"><image src="/static/icons/my/订单.png" mode="aspectFit" /></view>
          <view class="menu-text"><text>我的订单</text><text class="menu-sub">查看付款与履约状态</text></view>
          <text class="arrow">›</text>
        </view>
        <button open-type="contact" class="menu-row">
          <view class="menu-icon"><image src="/static/icons/my/客服.png" mode="aspectFit" /></view>
          <view class="menu-text"><text>在线客服</text><text class="menu-sub">{{ settings.customer_service_hours || '工作日 09:00-18:00' }}</text></view>
          <text class="arrow">›</text>
        </button>
        <view class="menu-row" @click="openAgreement">
          <view class="menu-icon"><image src="/static/icons/my/隐私.png" mode="aspectFit" /></view>
          <view class="menu-text"><text>协议与隐私规则</text><text class="menu-sub">用户授权、隐私保护与免责声明</text></view>
          <text class="arrow">›</text>
        </view>
        <button open-type="share" class="menu-row">
          <view class="menu-icon"><image src="/static/icons/my/分享.png" mode="aspectFit" /></view>
          <view class="menu-text"><text>分享好友</text><text class="menu-sub">推荐给有需要的朋友</text></view>
          <text class="arrow">›</text>
        </button>
        <view class="menu-row" @click="openPartner">
          <view class="menu-icon"><image src="/static/icons/my/商务合作.png" mode="aspectFit" /></view>
          <view class="menu-text"><text>商户合作</text><text class="menu-sub">车商与机构批量查询</text></view>
          <text class="arrow">›</text>
        </view>
      </view>

      <button class="deactivate-account" type="default" @click="deactivateAccount">注销账号</button>
      <view class="foot-note">车档查 · 专业车辆信息查询平台</view>
    </view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { clearLogin } from '../../utils/auth'

// 分享配置（「分享好友」按钮 open-type=share 与右上角菜单共用）
onShareAppMessage(() => ({
  title: '车档查丨专业车辆数据查询平台',
  path: '/pages/index/index'
}))

const user=ref({})
/** 订单四项统计（与订单页分类一致，见后端 OrderController.STATUS_GROUPS） */
const orderStats=ref({unpaid:0,querying:0,success:0,closed:0,total:0})
const statCells=[
  {key:'unpaid',group:'unpaid',label:'待付款'},
  {key:'querying',group:'querying',label:'查询中'},
  {key:'success',group:'success',label:'查询成功'},
  {key:'total',group:'all',label:'全部订单'}
]
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
  // 订单统计独立拉取：失败时静默保留 0，不打扰页面
  try{orderStats.value=await request('/orders/stats')}catch(error){console.warn('[orders/stats]',error)}
})
function statText(key){const n=Number(orderStats.value[key]||0);return n>99?'99+':String(n)}
function openOrderGroup(group){uni.navigateTo({url:`/pages/orders/orders?group=${group}`})}
function openOrders(){uni.navigateTo({url:'/pages/orders/orders'})}
function openAgreement(){uni.navigateTo({url:'/pages/agreement/agreement'})}
function openPartner(){
  uni.showModal({
    title:'商户合作',
    content:'诚邀二手车商、汽修厂、检测机构等商户合作，支持批量查询与对公结算。请通过「在线客服」联系我们洽谈。',
    confirmText:'我知道了',
    showCancel:false
  })
}
function deactivateAccount(){
  uni.showModal({
    title:'注销账号',
    content:'注销后本账号将无法再登录，昵称、头像等个人资料将被清除，历史订单将无法查看，且此操作不可恢复。确认要注销吗？',
    confirmText:'确认注销',
    confirmColor:'#E56A6A',
    cancelText:'取消',
    success:async(result)=>{
      if(!result.confirm) return
      try{
        await request('/auth/deactivate',{method:'POST'})
        clearLogin()
        uni.showToast({title:'账号已注销',icon:'success'})
        setTimeout(()=>uni.switchTab({url:'/pages/index/index'}),1500)
      }catch(error){
        uni.showModal({title:'无法注销',content:error.message||'请稍后重试',showCancel:false})
      }
    }
  })
}
</script>
<style lang="scss" scoped>
.my-page{background:#F5F9FE}
.my-head{height:calc(var(--status-bar-height) + 184rpx);padding:0 32rpx;color:#243B60;background:#F5F9FE;position:relative}
.my-head::after,.head-glow{display:none}
.safe-top{height:calc(var(--status-bar-height) + 52rpx)}
.title{position:relative;top:12rpx;text-align:center;font-size:36rpx;font-weight:700;letter-spacing:2rpx}
.body{padding:0 28rpx 60rpx;margin-top:0;position:relative;z-index:2}

.profile{overflow:hidden;padding:0;background:linear-gradient(135deg,#ffffff 0%,#f2f8ff 70%,#e8f3ff 100%);border-color:#e3eefb;border-radius:20rpx;box-shadow:0 10rpx 24rpx rgba(46,125,255,.08)}
.profile-top{position:relative;display:flex;align-items:center;padding:32rpx 28rpx}
.avatar{width:104rpx;height:104rpx;border-radius:50%;overflow:hidden;display:flex;align-items:center;justify-content:center;background:#E0EEFF;border:4rpx solid rgba(46,125,255,.15)}
.avatar image{width:100%;height:100%}
.neutral-avatar{width:100%;height:100%;position:relative;background:linear-gradient(145deg,#E0EEFF,#BBD6FF)}
.neutral-avatar .head{position:absolute;width:32rpx;height:32rpx;border-radius:50%;background:#6B7280;left:31rpx;top:19rpx}
.body-shape{position:absolute;width:62rpx;height:38rpx;border-radius:34rpx 34rpx 0 0;background:#6B7280;left:16rpx;bottom:7rpx}
.profile-main{margin-left:22rpx;flex:1;min-width:0}
.nickname{color:#1c3154;font-size:32rpx;font-weight:700;line-height:1.2}
.nick-arrow{margin-left:6rpx;color:#8fa9c9;font-size:30rpx;font-weight:400}
.uid{margin-top:8rpx;color:#7b8ea8;font-size:22rpx}
.profile-stats{display:flex;align-items:center;padding:24rpx 20rpx;border-top:1rpx solid #e8f1fb;background:rgba(255,255,255,.55)}
.qstat{flex:1;display:flex;flex-direction:column;align-items:center;min-width:0;gap:8rpx}
.qstat:active{opacity:.65}
.qstat-num{color:#1c3154;font-size:30rpx;font-weight:800;line-height:1.1}
.qstat-label{color:#7b8ea8;font-size:20rpx;white-space:nowrap}

.menu{padding:0 28rpx;margin-top:26rpx}
.menu-row{width:100%;height:130rpx;margin:0;padding:0;background:#fff;border-radius:0;border:0;border-bottom:1rpx solid #F0F2F5;display:flex;align-items:center;text-align:left;font-size:28rpx;line-height:1.4;font-weight:400;color:inherit}
.menu-row::after{border:0}
.menu-row:last-child{border-bottom:0}
.menu-icon{width:64rpx;height:64rpx;margin-right:22rpx;display:flex;align-items:center;justify-content:center}
.menu-icon image{width:64rpx;height:64rpx}
.menu-text{flex:1;display:flex;flex-direction:column}
.menu-sub{font-size:21rpx;color:#9CA3AF;margin-top:7rpx}
.arrow{color:#C4CBD5;font-size:38rpx}

.deactivate-account{width:100%;height:82rpx;line-height:78rpx;margin-top:32rpx;border:1rpx solid #F6C7C7;border-radius:18rpx;background:#fff;color:#E56A6A;font-size:25rpx;font-weight:500}
.deactivate-account:active{background:#FFF7F7}
.foot-note{margin-top:60rpx;text-align:center;font-size:22rpx;color:#9CA3AF;letter-spacing:1rpx}
</style>
