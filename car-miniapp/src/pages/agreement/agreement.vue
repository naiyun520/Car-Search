<template>
  <view class="page agreement-page">
    <view class="document-head">
      <view class="document-title">协议与个人信息处理规则</view>
      <view class="document-meta">生效日期：{{ settings.privacy_effective_date || '请以本页最新公示为准' }}</view>
    </view>

    <view class="card section">
      <view class="title">一、运营主体与适用范围</view>
      <view>本小程序由“{{ operatorName }}”（以下简称“我们”）运营。本规则适用于您使用车辆信息查询、订单支付、售后反馈和客户服务的全过程。</view>
      <view>个人信息保护相关问题可通过 {{ privacyContact }} 联系我们，客服时间为 {{ settings.customer_service_hours || '页面公示时段' }}。</view>
    </view>

    <view class="card section">
      <view class="title">二、用户服务与查询授权</view>
      <view>1. 您应为查询对象本人、车辆所有人，或已经取得相关个人、车辆所有人及其他权利人的明确合法授权，并确保提交的信息真实、准确、来源合法。</view>
      <view>2. 查询结果仅限本人核验、依法授权的车辆交易核验等正当目的。禁止用于骚扰、歧视、非法调查、数据买卖、侵犯他人隐私或其他违法违规活动。</view>
      <view>3. 您不得通过自动化脚本、批量请求、逆向工程、绕过访问控制等方式使用服务。因违法使用造成的责任由行为人承担。</view>
      <view>4. 您勾选同意并提交查询，即表示您已阅读本规则，并授权我们为完成本次服务处理您主动提交的信息。</view>
    </view>

    <view class="card section">
      <view class="title">三、个人信息处理规则</view>
      <view>1. 为完成微信登录、创建和核验订单，我们会处理微信提供的用户标识、订单号、支付状态、服务类型、时间和必要的设备/网络请求信息。</view>
      <view>2. 为完成查询，我们会处理您主动填写的车牌号、VIN、姓名、证件信息或具体服务页面列明的其他字段，并将必要字段发送给对应数据服务提供方。</view>
      <view>3. 查询信息采用加密方式保存在服务器，仅用于履约、异常排查、管理员售后处理及依法履行义务，不用于广告营销、画像或与本服务无关的用途。</view>
      <view>4. 查询输入与结果默认保留 {{ retentionDays }} 天，到期由清理任务删除；法律法规另有规定、争议处理或监管要求需要延长的，从其规定。必要的订单及支付凭证按法定期限保存。</view>
      <view>5. 查询结果不会在用户历史订单中再次展示。请您在本次查询完成后妥善保管，避免截屏、转发或向无关人员披露敏感信息。</view>
    </view>

    <view class="card section">
      <view class="title">四、信息共享、委托处理与安全</view>
      <view>我们仅在完成所选查询服务所必需的范围内，向实际提供数据查询能力的服务方传输必要字段；支付由微信相关能力处理。我们要求相关服务方按照约定用途和安全要求处理信息。</view>
      <view>我们采取传输加密、敏感配置加密存储、访问鉴权、权限隔离、操作审计和异常监控等措施。互联网服务无法承诺绝对安全；发生或可能发生安全事件时，我们将依法采取补救和告知措施。</view>
    </view>

    <view class="card section">
      <view class="title">五、您的权利</view>
      <view>您可通过本小程序客服或投诉意见入口，依法申请查阅、更正、删除相关个人信息，撤回授权、注销相关数据或对处理规则进行咨询。撤回授权不影响撤回前基于授权已经进行的处理；必要信息缺失时，我们可能无法继续提供对应服务。</view>
    </view>

    <view class="card section">
      <view class="title">六、未成年人保护</view>
      <view>本服务主要面向具备完全民事行为能力的成年人。未满十四周岁的未成年人不得自行提交查询；确有必要的，应由监护人阅读并同意相关规则后操作。</view>
    </view>

    <view class="card section">
      <view class="title">七、免责声明与售后</view>
      <view>{{ settings.disclaimer || defaultDisclaimer }}</view>
      <view>因数据源更新延迟、维护、网络中断或不可抗力导致结果延迟时，我们将依据实际履约和支付状态处理。已付款但查询异常的，请提交订单号联系客服，切勿重复付款。</view>
    </view>
  </view>
</template>
<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { request } from '../../utils/request'

// 分享配置
onShareAppMessage(() => ({
  title: '车辆信息查询 - 协议与声明',
  path: '/pages/index/index'
}))

const defaultDisclaimer='查询结果来自依法接入的数据服务，仅供授权场景参考，不作为行政、司法或交易决策的唯一依据。'
const settings=ref(uni.getStorageSync('app_settings')||{})
const operatorName=computed(()=>settings.value.operator_name||'本小程序公示的运营主体')
const privacyContact=computed(()=>settings.value.privacy_contact||settings.value.customer_service_phone||'小程序在线客服')
const retentionDays=computed(()=>Math.max(1,Number(settings.value.privacy_retention_days||30)))
onLoad(async()=>{try{const data=await request('/bootstrap',{public:true});settings.value=data.settings||{};uni.setStorageSync('app_settings',settings.value)}catch(_){}})
</script>
<style lang="scss" scoped>
.agreement-page{padding:24rpx 24rpx 55rpx}.document-head{padding:24rpx 8rpx 30rpx}.document-title{font-size:38rpx;font-weight:800;color:#17243a}.document-meta{font-size:22rpx;color:#8a95a6;margin-top:10rpx}.section{padding:32rpx;margin-bottom:20rpx;color:#59687e;font-size:25rpx;line-height:1.9}.section>view:not(.title){margin-top:10rpx}.title{font-size:30rpx;font-weight:800;color:#1d2a41;margin-bottom:14rpx}
</style>
