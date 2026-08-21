<template>
  <view class="page home">
    <view class="hero">
      <view class="safe-top" />
      <view class="brand-row">
        <view class="brand-mark"><view class="brand-line" /><view class="brand-dot left" /><view class="brand-dot right" /></view>
        <view><view class="brand-name">车辆信息查询</view><view class="brand-sub">车辆数据服务平台</view></view>
      </view>
      <view class="hero-title">专业车辆信息服务</view>
      <view class="hero-desc">选择所需服务，授权后即可发起查询</view>
    </view>

    <view class="content">
      <view class="section-head"><text class="section-title">服务中心</text><text class="count">{{ services.length }} 项服务</text></view>
      <view class="service-grid" v-if="services.length">
        <view class="service-card card" v-for="service in services" :key="service.code" @click="openService(service)">
          <view class="service-card-top">
            <view class="service-icon">
              <image v-if="iconUrl(service.icon)" :src="iconUrl(service.icon)" mode="aspectFill" />
              <view v-else class="default-service-icon"><view class="car-body" /><view class="wheel wheel-left" /><view class="wheel wheel-right" /></view>
            </view>
            <text class="service-badge" :class="service.status===0?'maintenance-badge':'normal-badge'">{{ service.status===0?'维护中':'可查询' }}</text>
          </view>
          <view class="service-name">{{ service.short_name }}</view>
          <view class="service-desc">{{ service.description }}</view>
          <view class="service-foot"><text class="go" :class="{muted:service.status===0}">{{ service.status===0?'暂不可用':'进入服务' }}</text><text class="arrow">›</text></view>
        </view>
      </view>
      <view v-else class="empty card">暂无可用查询服务</view>
    </view>

    <view class="modal-mask" v-if="showAnnouncement">
      <view class="modal card"><view class="modal-icon">i</view><view class="modal-title">{{ announcement.title }}</view><scroll-view scroll-y class="modal-content">{{ announcement.content }}</scroll-view><view class="suppress" @click="suppress=!suppress"><view class="checkbox" :class="{checked:suppress}">{{ suppress?'✓':'' }}</view><text>{{ announcement.suppress_hours }}小时内不再提示</text></view><button class="primary-btn" @click="closeAnnouncement(true)">我知道了</button><text class="modal-close" @click="closeAnnouncement(false)">×</text></view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { API_BASE_URL } from '../../config'

// 分享配置
onShareAppMessage(() => ({
  title: '车辆信息查询 - 专业车辆数据服务平台',
  path: '/pages/index/index'
}))

const services = ref([])
const announcement = ref(null)
const showAnnouncement = ref(false)
const suppress = ref(false)

onLoad(async () => {
  try {
    const data = await request('/bootstrap', { public: true })
    services.value = data.services || []
    announcement.value = data.announcement
    uni.setStorageSync('app_settings', data.settings || {})
    if (data.announcement) {
      const hiddenUntil = Number(uni.getStorageSync(`announcement_${data.announcement.id}_${data.announcement.updated_at}`) || 0)
      showAnnouncement.value = Date.now() > hiddenUntil
    }
  } catch (error) { uni.showToast({ title: error.message, icon: 'none' }) }
})

function iconUrl(icon) {
  const filename = String(icon || '')
  return /^[a-f0-9]{32}\.(?:png|jpg|webp)$/.test(filename) ? `${API_BASE_URL}/service-icon/${filename}` : ''
}
function openService(service) {
  if (service.status !== 1) return uni.showToast({ title:'该服务维护中', icon:'none' })
  uni.setStorageSync('selected_query_service', service)
  uni.navigateTo({ url: `/pages/query/query?code=${service.code}` })
}
function closeAnnouncement(fromButton) {
  if (fromButton && suppress.value) uni.setStorageSync(`announcement_${announcement.value.id}_${announcement.value.updated_at}`, Date.now() + announcement.value.suppress_hours * 3600000)
  showAnnouncement.value = false
}
</script>

<style lang="scss" scoped>
.home{background:#f3f6fa}.hero{padding:0 34rpx 78rpx;color:#fff;background:linear-gradient(145deg,#092f73 0%,#0f56bd 62%,#2779da 100%);border-radius:0 0 44rpx 44rpx;position:relative;overflow:hidden}.hero::after{content:"";position:absolute;width:380rpx;height:380rpx;right:-155rpx;top:35rpx;border:54rpx solid rgba(255,255,255,.06);border-radius:50%}.safe-top{height:calc(var(--status-bar-height) + 22rpx)}.brand-row{display:flex;align-items:center;gap:18rpx;position:relative;z-index:1}.brand-mark{width:72rpx;height:72rpx;border-radius:18rpx;background:rgba(255,255,255,.14);border:1rpx solid rgba(255,255,255,.35);position:relative}.brand-line{position:absolute;left:16rpx;right:16rpx;top:27rpx;height:19rpx;border:5rpx solid #fff;border-radius:9rpx 9rpx 5rpx 5rpx}.brand-dot{position:absolute;width:8rpx;height:8rpx;border-radius:50%;background:#fff;bottom:13rpx}.brand-dot.left{left:18rpx}.brand-dot.right{right:18rpx}.brand-name{font-size:33rpx;font-weight:700;letter-spacing:1rpx}.brand-sub{font-size:20rpx;opacity:.7;margin-top:5rpx;letter-spacing:2rpx}.hero-title{font-size:47rpx;font-weight:800;margin-top:60rpx;position:relative;z-index:1}.hero-desc{font-size:25rpx;opacity:.76;margin-top:14rpx;position:relative;z-index:1}.content{padding:32rpx 24rpx 50rpx}.section-head{display:flex;align-items:center;justify-content:space-between;margin:0 8rpx 22rpx}.section-title{font-size:34rpx;font-weight:750}.count{font-size:22rpx;color:#8793a5}.service-grid{display:grid;grid-template-columns:1fr 1fr;gap:20rpx}.service-card{padding:25rpx;min-height:310rpx}.service-card-top{display:flex;align-items:flex-start;justify-content:space-between}.service-icon{width:76rpx;height:76rpx;border-radius:20rpx;background:#e9f2ff;overflow:hidden;display:flex;align-items:center;justify-content:center}.service-icon image{width:100%;height:100%}.default-service-icon{width:58rpx;height:46rpx;position:relative}.car-body{position:absolute;left:5rpx;right:5rpx;top:13rpx;height:23rpx;border:5rpx solid #1764d7;border-radius:11rpx 11rpx 7rpx 7rpx}.car-body::before{content:"";position:absolute;left:9rpx;right:9rpx;top:-14rpx;height:15rpx;border:5rpx solid #1764d7;border-bottom:0;border-radius:11rpx 11rpx 0 0}.wheel{position:absolute;width:9rpx;height:9rpx;background:#1764d7;border-radius:50%;bottom:1rpx}.wheel-left{left:12rpx}.wheel-right{right:12rpx}.service-badge{padding:7rpx 13rpx;border-radius:20rpx;font-size:18rpx}.maintenance-badge{background:#fff2da;color:#9a6100}.normal-badge{background:#e8f7f0;color:#087e57}.service-name{font-size:29rpx;font-weight:700;margin-top:22rpx}.service-desc{font-size:22rpx;color:#7b8799;line-height:1.55;margin-top:10rpx;height:68rpx;overflow:hidden}.service-foot{display:flex;justify-content:space-between;align-items:center;margin-top:20rpx;padding-top:18rpx;border-top:1rpx solid #eef1f5}.go{font-size:21rpx;color:#175cd3}.go.muted{color:#9aa4b3}.arrow{font-size:30rpx;color:#9aa8ba}.modal-mask{position:fixed;inset:0;background:rgba(8,20,39,.58);z-index:100;display:flex;align-items:center;justify-content:center;padding:45rpx}.modal{width:100%;padding:48rpx 38rpx 36rpx;position:relative}.modal-icon{width:68rpx;height:68rpx;border-radius:50%;margin:auto;background:#e8f1ff;color:#175cd3;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:38rpx}.modal-title{text-align:center;font-size:34rpx;font-weight:700;margin:22rpx 0}.modal-content{max-height:390rpx;line-height:1.8;color:#59677d;font-size:26rpx;white-space:pre-wrap}.suppress{display:flex;align-items:center;gap:14rpx;margin:30rpx 0;font-size:24rpx;color:#657289}.checkbox{width:34rpx;height:34rpx;border:2rpx solid #bbc5d4;border-radius:7rpx;display:flex;align-items:center;justify-content:center}.checked{background:#175cd3;border-color:#175cd3;color:#fff}.modal-close{position:absolute;right:24rpx;top:15rpx;font-size:50rpx;color:#a3adbb;padding:10rpx}.empty{margin-top:10rpx}
</style>
