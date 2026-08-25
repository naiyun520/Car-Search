<template>
  <view class="page feedback-page">
    <view class="card form">
      <view class="section-title">提交反馈</view>
      <view class="label">反馈类型</view><picker :range="types" range-key="label" @change="typeIndex=$event.detail.value"><view class="picker">{{ types[typeIndex].label }}<text>›</text></view></picker>
      <view class="label">详细描述 <text>*</text></view><textarea v-model="content" maxlength="1000" placeholder="请详细描述问题、发生时间及相关订单号，以便我们快速处理"/><view class="counter">{{ content.length }}/1000</view>
      <view class="label">联系方式（选填）</view><input v-model="contact" maxlength="100" placeholder="手机号或微信号"/>
      <view class="privacy">您的联系方式仅用于本次反馈，不会用于营销。</view><button class="primary-btn" :disabled="submitting" @click="submit">{{ submitting?'提交中':'提交反馈' }}</button>
    </view>
    <view class="history-title">我的反馈 <text>{{ feedback.length }}条</text></view>
    <view v-if="feedback.length" class="feedback-card card" v-for="item in feedback" :key="item.id">
      <view class="feedback-head"><text>{{ typeName(item.type) }}</text><text class="status" :class="{resolved:item.status==='resolved'}">{{ item.status==='resolved'?'已回复':'处理中' }}</text></view>
      <view class="feedback-content">{{ item.content }}</view><view class="feedback-time">提交于 {{ item.created_at }}</view>
      <view class="reply" v-if="item.reply"><view class="reply-title">客服回复</view><view>{{ item.reply }}</view><text>{{ item.replied_at || item.updated_at }}</text></view>
      <view class="waiting" v-else>客服正在处理中，请耐心等待</view>
    </view>
    <view v-else class="empty card">暂无历史反馈</view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onLoad, onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { request } from '../../utils/request'

// 分享配置
onShareAppMessage(() => ({
  title: '车辆信息查询 - 投诉与建议',
  path: '/pages/index/index'
}))
const types=[{label:'功能异常',value:'bug'},{label:'订单问题',value:'order'},{label:'投诉建议',value:'complaint'},{label:'其他',value:'other'}]
const typeIndex=ref(0),content=ref(''),contact=ref(''),submitting=ref(false),feedback=ref([])
onLoad(options=>{const orderNo=String(options?.order_no||'').trim();if(orderNo){typeIndex.value=1;content.value=`已付款订单查询未成功，请协助处理。\n订单号：${orderNo}`}})
onShow(loadFeedback)
async function loadFeedback(){try{const data=await request('/feedback?page_size=20');feedback.value=data.data}catch(error){uni.showToast({title:error.message||'反馈记录加载失败',icon:'none'})}}
function typeName(value){return types.find(item=>item.value===value)?.label||'其他'}
async function submit(){if(content.value.trim().length<5)return uni.showToast({title:'请至少填写5个字',icon:'none'});submitting.value=true;try{await request('/feedback',{method:'POST',data:{type:types[typeIndex.value].value,content:content.value,contact:contact.value}});content.value='';contact.value='';await loadFeedback();uni.showModal({title:'提交成功',content:'我们已收到您的反馈，处理回复会显示在本页面。',showCancel:false})}catch(error){uni.showToast({title:error.message||'提交失败',icon:'none'})}finally{submitting.value=false}}
</script>
<style lang="scss" scoped>
.feedback-page{padding:24rpx}.form{padding:28rpx}.section-title{font-size:32rpx;font-weight:800}.label{font-size:27rpx;font-weight:700;margin:28rpx 0 16rpx}.label text{color:#df483f}.picker,.form input{height:86rpx;padding:0 22rpx;border-radius:14rpx;background:#f6f8fb;display:flex;align-items:center;justify-content:space-between;font-size:26rpx}.form textarea{width:100%;height:260rpx;padding:22rpx;border-radius:14rpx;background:#f6f8fb;font-size:25rpx;line-height:1.7}.counter{text-align:right;color:#a0a9b6;font-size:20rpx;margin-top:8rpx}.privacy{font-size:21rpx;color:#8d98a8;margin:22rpx 0}.history-title{font-size:30rpx;font-weight:800;margin:36rpx 8rpx 18rpx}.history-title text{font-size:21rpx;color:#8b96a7;font-weight:400;margin-left:10rpx}.feedback-card{padding:26rpx;margin-bottom:20rpx}.feedback-head{display:flex;justify-content:space-between;font-size:27rpx;font-weight:700}.status{padding:6rpx 13rpx;border-radius:18rpx;background:#fff2da;color:#a96800;font-size:19rpx}.status.resolved{background:#e6f8f0;color:#07845a}.feedback-content{margin-top:20rpx;color:#45536a;font-size:25rpx;line-height:1.7;white-space:pre-wrap}.feedback-time{margin-top:12rpx;color:#9aa4b2;font-size:20rpx}.reply{margin-top:22rpx;padding:22rpx;border-radius:14rpx;background:#f1f6ff;color:#46566e;font-size:24rpx;line-height:1.7}.reply-title{color:#175cd3;font-weight:700;margin-bottom:8rpx}.reply text{display:block;margin-top:8rpx;color:#98a3b2;font-size:19rpx}.waiting{margin-top:20rpx;padding-top:18rpx;border-top:1rpx solid #edf0f4;color:#9a7a3c;font-size:22rpx}.empty{padding:60rpx;text-align:center;color:#929dad}
</style>
