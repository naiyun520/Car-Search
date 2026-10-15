<template>
  <view class="page home">
    <view class="hero">
      <image class="hero-image" src="/static/images/home-hero.jpg" mode="aspectFill" />
      <view class="hero-shade" />
      <view class="safe-top" />
      <view class="brand-row">
        <view class="brand-icon"><image src="/static/images/app-avatar.png" mode="aspectFit" /></view>
        <view><view class="brand-name">车档查</view><view class="brand-sub">专业车辆信息查询平台</view></view>
      </view>
      <view class="hero-title">一键查询<view>车辆真实信息</view></view>
      <view class="hero-desc">权威数据 · 专业解析 · 安全可靠</view>
    </view>
    <view class="content">
      <view class="assurance">
        <view class="assurance-item"><view class="assurance-icon"><image src="/static/icons/features/authority.svg" /></view><text>数据权威</text></view>
        <view class="assurance-item"><view class="assurance-icon"><image src="/static/icons/features/quick.svg" /></view><text>查询快捷</text></view>
        <view class="assurance-item"><view class="assurance-icon"><image src="/static/icons/features/complete.svg" /></view><text>信息全面</text></view>
        <view class="assurance-item"><view class="assurance-icon"><image src="/static/icons/features/privacy.svg" /></view><text>隐私安全</text></view>
      </view>
      <view class="search-box card">
        <text class="search-icon">⌕</text>
        <input v-model="keyword" placeholder="搜索车辆信息、VIN、车牌等服务" confirm-type="search" @input="onSearchInput" />
        <text v-if="keyword" class="clear" @click="clearSearch">×</text>
      </view>
      <scroll-view class="category-scroll" scroll-x enable-flex :show-scrollbar="false">
        <view class="category-row">
          <view class="category" :class="{ active: categoryCode === '' }" @click="categoryCode = ''">全部</view>
          <view class="category" v-for="category in categories" :key="category.code" :class="{ active: categoryCode === category.code }" @click="categoryCode = category.code">{{ category.name }}</view>
        </view>
      </scroll-view>
      <view class="section-head">
        <view><text class="section-title">专业服务</text><text class="section-sub">按需选择车辆数据查询</text></view>
        <text class="count">{{ filteredServices.length }} 项</text>
      </view>
      <view class="service-list" v-if="filteredServices.length">
        <view class="service-card card" v-for="service in filteredServices" :key="service.code" @click="openService(service)">
          <view class="service-icon"><image :src="iconUrl(service.icon)" mode="aspectFit" /></view>
          <view class="service-main">
            <text class="service-name">{{ service.short_name }}</text>
            <view class="service-meta">{{ service.category_name || '车辆信息' }} · {{ inputLabel(service) }}</view>
          </view>
          <view class="service-side">
            <text class="service-badge" :class="service.status === 0 ? 'maintenance-badge' : 'normal-badge'">{{ service.status === 0 ? '维护中' : '可查询' }}</text>
            <view class="arrow">›</view>
          </view>
        </view>
      </view>
      <view v-else class="empty card">没有找到相关服务，换个关键词试试</view>
    </view>
    <view class="modal-mask" v-if="showAnnouncement">
      <view class="modal card">
        <view class="modal-icon">i</view>
        <view class="modal-title">{{ announcement.title }}</view>
        <scroll-view scroll-y class="modal-content">{{ announcement.content }}</scroll-view>
        <view class="suppress" @click="suppress = !suppress"><view class="checkbox" :class="{ checked: suppress }">{{ suppress ? '✓' : '' }}</view><text>{{ announcement.suppress_hours }}小时内不再提示</text></view>
        <button class="primary-btn" @click="closeAnnouncement(true)">我知道了</button>
        <text class="modal-close" @click="closeAnnouncement(false)">×</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { API_BASE_URL } from '../../config'
import { serviceIcon } from '../../utils/service-icons'

onShareAppMessage(() => ({ title: '车档查 - 专业车辆信息查询平台', path: '/pages/index/index' }))
const services = ref([])
const categories = ref([])
const announcement = ref(null)
const showAnnouncement = ref(false)
const suppress = ref(false)
const keyword = ref('')
const categoryCode = ref('')
const BOOTSTRAP_CACHE_KEY = 'bootstrap_cache_v3'
const filteredServices = computed(() => {
  const word = keyword.value.trim().toLowerCase()
  return services.value.filter(service =>
    (!categoryCode.value || service.category_code === categoryCode.value) &&
    (!word || [service.name, service.short_name, service.category_name, service.code].some(value => String(value || '').toLowerCase().includes(word)))
  )
})
function applyBootstrap(data, allowAnnouncement = true) {
  if (!data?.services || !data?.categories) return
  services.value = data.services
  categories.value = data.categories
  announcement.value = data.announcement || null
  uni.setStorage({ key:'app_settings', data:data.settings || {} })
  if (allowAnnouncement && data.announcement) {
    const hiddenUntil = Number(uni.getStorageSync(`announcement_${data.announcement.id}_${data.announcement.updated_at}`) || 0)
    showAnnouncement.value = Date.now() > hiddenUntil
  }
}
onLoad(async () => {
  let freshApplied = false
  uni.getStorage({
    key: BOOTSTRAP_CACHE_KEY,
    success: result => {
      const cache = result.data
      if (!freshApplied && cache?.saved_at && Date.now() - cache.saved_at < 6 * 3600000) applyBootstrap(cache.data, false)
    }
  })
  try {
    const data = await request('/bootstrap', { public: true })
    freshApplied = true
    applyBootstrap(data, true)
    uni.setStorage({ key:BOOTSTRAP_CACHE_KEY, data:{ saved_at:Date.now(), data } })
  } catch (error) {
    if (!services.value.length) uni.showToast({ title: error.message, icon: 'none' })
  }
})
const iconUrl = icon => serviceIcon(icon, API_BASE_URL)
const inputLabel = service => (service.input_schema || []).map(item => item.label).slice(0, 2).join(' + ') || '信息查询'
function onSearchInput(event) {
  keyword.value = event?.detail?.value ?? keyword.value
  if (keyword.value.trim()) categoryCode.value = ''
}
function clearSearch() { keyword.value = '' }
function openService(service) {
  if (service.status !== 1) return uni.showToast({ title: '该服务维护中', icon: 'none' })
  uni.setStorageSync('selected_query_service', service)
  uni.navigateTo({ url: `/pages/query/query?code=${service.code}` })
}
function closeAnnouncement(fromButton) {
  if (fromButton && suppress.value) uni.setStorageSync(`announcement_${announcement.value.id}_${announcement.value.updated_at}`, Date.now() + announcement.value.suppress_hours * 3600000)
  showAnnouncement.value = false
}
</script>

<style lang="scss" scoped>
.home { background: #f3f7fc; }
.hero { height: 560rpx; padding: 0 34rpx; color: #fff; position: relative; overflow: hidden; background: #0757bc; }
.hero-image, .hero-shade { position: absolute; inset: 0; width: 100%; height: 100%; }
.hero-shade { background: linear-gradient(90deg, rgba(2,42,112,.96) 0%, rgba(8,78,175,.76) 50%, rgba(13,101,210,.18) 100%); }
.safe-top { height: calc(var(--status-bar-height) + 24rpx); }
.brand-row { position: relative; top: 20px; display: flex; align-items: center; gap: 16rpx; }
.brand-icon { width: 76rpx; height: 76rpx; border: 2rpx solid rgba(255,255,255,.7); border-radius: 20rpx; background: #fff; display: flex; align-items: center; justify-content: center; overflow: hidden; box-shadow: 0 8rpx 22rpx rgba(1,35,92,.18); }
.brand-icon image { width: 68rpx; height: 68rpx; }
.brand-name { font-size: 34rpx; font-weight: 800; letter-spacing: 2rpx; }
.brand-sub { font-size: 20rpx; opacity: .82; margin-top: 5rpx; }
.hero-title { position: relative; margin-top: 50rpx; font-size: 51rpx; font-weight: 800; line-height: 1.32; letter-spacing: 2rpx; }
.hero-desc { position: relative; margin-top: 15rpx; font-size: 23rpx; opacity: .88; }
.content { padding: 0 24rpx 42rpx; margin-top: -42rpx; position: relative; }
.assurance { min-height: 152rpx; margin: 0 -24rpx; padding: 30rpx 25rpx 22rpx; display: grid; grid-template-columns: repeat(4, 1fr); align-items: center; background: #fff; border-radius: 52rpx 52rpx 0 0; box-shadow: 0 -10rpx 30rpx rgba(6,72,153,.06); }
.assurance-item { min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 11rpx; color: #263a57; font-size: 22rpx; font-weight: 650; line-height: 1; white-space: nowrap; }
.assurance-icon { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; filter: drop-shadow(0 6rpx 8rpx rgba(12,99,224,.16)); }
.assurance-icon image { width: 64rpx; height: 64rpx; }
.search-box { height: 96rpx; margin-top: 0; display: flex; align-items: center; padding: 0 24rpx; border-radius: 22rpx; }
.search-icon { font-size: 40rpx; color: #6e8098; margin-right: 14rpx; }
.search-box input { flex: 1; font-size: 26rpx; }
.clear { padding: 12rpx; color: #95a1b2; }
.category-scroll { width: 100%; white-space: nowrap; margin: 22rpx 0 30rpx; }
.category-row { display: flex; width: max-content; min-width: 100%; gap: 14rpx; padding: 2rpx 8rpx 12rpx 2rpx; box-sizing: border-box; }
.category { flex: none; height: 64rpx; padding: 0 27rpx; border-radius: 32rpx; background: #fff; color: #66758b; display: flex; align-items: center; font-size: 24rpx; border: 1rpx solid #e4eaf2; }
.category.active { background: #126bee; border-color: #126bee; color: #fff; box-shadow: 0 8rpx 20rpx rgba(18,107,238,.24); }
.section-head { display: flex; align-items: flex-end; justify-content: space-between; margin: 0 8rpx 20rpx; }
.section-title { font-size: 34rpx; font-weight: 800; }
.section-sub { display: block; font-size: 21rpx; color: #929dab; margin-top: 7rpx; }
.count { font-size: 22rpx; color: #7f8b9d; }
.service-list { display: flex; flex-direction: column; gap: 16rpx; }
.service-card { min-height: 150rpx; padding: 21rpx 19rpx 21rpx 22rpx; display: flex; align-items: center; border: 1rpx solid #eaf0f7; border-radius: 22rpx; box-shadow: 0 8rpx 22rpx rgba(31,75,126,.065); }
.service-icon { width: 84rpx; height: 84rpx; border-radius: 22rpx; background: #eaf3ff; padding: 14rpx; flex: none; }
.service-icon image { width: 100%; height: 100%; }
.service-main { flex: 1; min-width: 0; margin-left: 22rpx; }
.service-name { display: block; min-width: 0; font-size: 28rpx; font-weight: 750; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.service-side { width: 92rpx; flex: none; margin-left: 12rpx; align-self: stretch; display: flex; flex-direction: column; align-items: flex-end; padding-top: 3rpx; box-sizing: border-box; }
.service-badge { min-width: 72rpx; padding: 7rpx 11rpx 6rpx; border-radius: 20rpx; box-sizing: border-box; text-align: center; font-size: 19rpx; font-weight: 650; line-height: 1.1; }
.normal-badge { background: #e4faee; color: #06a567; }
.maintenance-badge { background: #fff3dd; color: #9b6300; }
.service-meta { font-size: 21rpx; color: #8995a7; margin-top: 12rpx; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.arrow { margin-top: 24rpx; padding-right: 8rpx; font-size: 38rpx; line-height: 1; color: #8298b4; }
.empty { margin-top: 10rpx; }
.modal-mask { position: fixed; inset: 0; background: rgba(8,20,39,.58); z-index: 100; display: flex; align-items: center; justify-content: center; padding: 45rpx; }
.modal { width: 100%; padding: 48rpx 38rpx 36rpx; position: relative; }
.modal-icon { width: 68rpx; height: 68rpx; border-radius: 50%; margin: auto; background: #e8f1ff; color: #175cd3; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 38rpx; }
.modal-title { text-align: center; font-size: 34rpx; font-weight: 700; margin: 22rpx 0; }
.modal-content { max-height: 390rpx; line-height: 1.8; color: #59677d; font-size: 26rpx; white-space: pre-wrap; }
.suppress { display: flex; align-items: center; gap: 14rpx; margin: 30rpx 0; font-size: 24rpx; color: #657289; }
.checkbox { width: 34rpx; height: 34rpx; border: 2rpx solid #bbc5d4; border-radius: 7rpx; display: flex; align-items: center; justify-content: center; }
.checked { background: #175cd3; border-color: #175cd3; color: #fff; }
.modal-close { position: absolute; right: 24rpx; top: 15rpx; font-size: 50rpx; color: #a3adbb; padding: 10rpx; }
</style>
