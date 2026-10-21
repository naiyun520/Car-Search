<template>
  <view class="page home">
    <!-- 顶部品牌区：原蓝色渐变 -->
    <view class="hero">
      <image class="hero-image" src="/static/images/home-hero.jpg" mode="aspectFill" />
      <view class="hero-shade" />
      <view class="safe-top" />
      <view class="brand-row">
        <view class="brand-icon"><image src="/static/images/app-avatar.png" mode="aspectFit" /></view>
        <view>
          <view class="brand-name">车档查</view>
          <view class="brand-sub">专业车辆信息查询平台</view>
        </view>
      </view>
      <view class="hero-title">一键查询<view>车辆真实信息</view></view>
      <view class="hero-desc">权威数据 · 专业解析 · 安全可靠</view>
    </view>

    <view class="content">
      <!-- 特性 -->
      <view class="assurance">
        <view class="assurance-item"><view class="assurance-icon"><image src="/static/icons/features/authority.svg" /></view><text>数据权威</text></view>
        <view class="assurance-item"><view class="assurance-icon"><image src="/static/icons/features/quick.svg" /></view><text>查询快捷</text></view>
        <view class="assurance-item"><view class="assurance-icon"><image src="/static/icons/features/complete.svg" /></view><text>信息全面</text></view>
        <view class="assurance-item"><view class="assurance-icon"><image src="/static/icons/features/privacy.svg" /></view><text>隐私安全</text></view>
      </view>

      <!-- 搜索 -->
      <view class="search-box card">
        <text class="search-icon">⌕</text>
        <input v-model="keyword" placeholder="搜索车辆信息、VIN、车牌等服务" confirm-type="search" @input="onSearchInput" />
        <text v-if="keyword" class="clear" @click="clearSearch">×</text>
      </view>

      <!-- 分类：换行显示，无需滑动 -->
      <view class="category-wrap">
        <view class="category" :class="{ active: categoryCode === '' }" @click="categoryCode = ''">全部</view>
        <view class="category" v-for="category in categories" :key="category.code" :class="{ active: categoryCode === category.code }" @click="categoryCode = category.code">{{ category.name }}</view>
      </view>

      <!-- 服务卡片 -->
      <view class="section-head">
        <view><text class="section-title">专业服务</text><text class="section-sub">按需选择车辆数据查询</text></view>
        <text class="count">{{ filteredServices.length }} 项</text>
      </view>

      <view class="service-grid" v-if="filteredServices.length">
        <view class="service-card card" v-for="service in filteredServices" :key="service.code" @click="openService(service)">
          <view class="service-icon"><image :src="iconUrl(service.icon)" mode="aspectFit" /></view>
          <view class="service-name">{{ introOf(service).title }}</view>
          <view class="service-intro">{{ introOf(service).intro }}</view>
          <view class="service-tags">
            <text class="tag" v-for="tag in introOf(service).tags" :key="tag">{{ tag }}</text>
          </view>
          <view class="service-foot">
            <text class="service-status" :class="service.status === 0 ? 'off' : 'on'">{{ service.status === 0 ? '维护中' : '立即查询' }}</text>
            <text class="service-arrow">›</text>
          </view>
        </view>
      </view>
      <view v-else class="empty card">没有找到相关服务，换个关键词试试</view>
    </view>

    <!-- 公告弹窗 -->
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

/* 服务介绍配置（依据《api接口详情.md》《汽修宝接口爬虫.md》编写；内联以避免新增模块注册问题） */
const SERVICE_INTRO = {
  vin_decode: { title: 'VIN码车辆信息解析', intro: '通过 17 位车架号解码车辆型号、排量、驱动方式、动力参数与排放标准，还原车辆出厂配置。', tags: ['VIN 解码', '配置解析', '动力参数'], display: 'profile' },
  vin_plate: { title: 'VIN车架号反查车牌号', intro: '通过车架号反查对应车牌号码与车辆初次登记日期，用于车辆身份交叉核验。', tags: ['反查车牌', '身份核验'], display: 'profile' },
  plate_basic: { title: '车牌五项信息', intro: '查询车辆品牌、车架号 VIN、车辆种类、初登日期与使用性质，建立车辆基础档案。', tags: ['基础档案', '品牌车型'], display: 'profile' },
  plate_full: { title: '车牌综合信息', intro: '一次查询覆盖车辆基础档案与车型配置共 20+ 项参数，含排量、驱动、变速箱与市场参考价。', tags: ['综合档案', '20+ 项参数'], display: 'profile' },
  owned_vehicle_count: { title: '名下车辆数量', intro: '通过身份证号或公司统一社会信用代码，核验个人或企业名下登记车辆总数。', tags: ['名下车辆', '归属核验'], display: 'stat' },
  owner_verify: { title: '人车关系核验', intro: '核验指定姓名与车牌号是否为同一车辆的登记关系，输出一致性结论。', tags: ['一致性核验', '人车匹配'], display: 'verify' },
  owner_verify_precise: { title: '人车关系核验V2', intro: '支持车牌号或 VIN 双通道核验，对姓名与车辆关系进行更精准的一致性确认。', tags: ['精准核验', '双通道'], display: 'verify' },
  transfer_count: { title: '车辆过户次数', intro: '查询车辆历史过户次数，辅助掌握车辆流转与交易背景。', tags: ['过户记录', '交易背景'], display: 'stat' },
  insurance_dates: { title: '车辆上险信息查询', intro: '查询车辆初次上险与最新交强险投保时间，了解车辆投保延续情况。', tags: ['上险记录', '投保时间'], display: 'stat' },
  etc_owner_verify: { title: '人车实名检验-ETC', intro: '核验指定人员是否为指定车辆 ETC 登记所有人，输出实名一致性结论。', tags: ['ETC 核验', '实名一致'], display: 'verify' },
  owned_etc_count: { title: '名下ETC车辆总数', intro: '通过证件号码查询个人名下 ETC 车辆登记总数。', tags: ['ETC 数量', '名下登记'], display: 'stat' }
}
function serviceIntro(service) {
  const base = SERVICE_INTRO[service.code] || {}
  return {
    title: base.title || service.name || service.short_name || '',
    intro: base.intro || service.description || '',
    tags: base.tags || [],
    display: base.display || 'generic'
  }
}

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
    (!word || [service.name, service.short_name, service.category_name, service.code, service.description]
      .some(value => String(value || '').toLowerCase().includes(word)))
  )
})

function introOf(service) { return serviceIntro(service) }
function applyBootstrap(data, allowAnnouncement = true) {
  if (!data?.services || !data?.categories) return
  services.value = data.services
  categories.value = data.categories
  announcement.value = data.announcement || null
  uni.setStorage({ key: 'app_settings', data: data.settings || {} })
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
    uni.setStorage({ key: BOOTSTRAP_CACHE_KEY, data: { saved_at: Date.now(), data } })
  } catch (error) {
    if (!services.value.length) uni.showToast({ title: error.message, icon: 'none' })
  }
})
const iconUrl = icon => serviceIcon(icon, API_BASE_URL)
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

/* ===== 顶部品牌区（原版蓝色渐变 + 原图） ===== */
.hero { height: 560rpx; padding: 0 34rpx; color: #ffffff; position: relative; overflow: hidden; background: #0757bc; }
.hero-image, .hero-shade { position: absolute; inset: 0; width: 100%; height: 100%; }
.hero-shade { background: linear-gradient(90deg, rgba(2,42,112,.96) 0%, rgba(8,78,175,.76) 50%, rgba(13,101,210,.18) 100%); }
.safe-top { height: calc(var(--status-bar-height) + 24rpx); }
.brand-row { position: relative; top: 20px; display: flex; align-items: center; gap: 16rpx; }
.brand-icon { width: 76rpx; height: 76rpx; border: 2rpx solid rgba(255,255,255,.7); border-radius: 20rpx; background: #ffffff; display: flex; align-items: center; justify-content: center; overflow: hidden; box-shadow: 0 8rpx 22rpx rgba(1,35,92,.18); }
.brand-icon image { width: 68rpx; height: 68rpx; }
.brand-name { font-size: 34rpx; font-weight: 800; letter-spacing: 2rpx; }
.brand-sub { font-size: 20rpx; opacity: .82; margin-top: 5rpx; }
.hero-title { position: relative; margin-top: 50rpx; font-size: 51rpx; font-weight: 800; line-height: 1.32; letter-spacing: 2rpx; }
.hero-desc { position: relative; margin-top: 15rpx; font-size: 23rpx; opacity: .88; }

/* ===== 内容区（白卡叠图） ===== */
.content { padding: 0 24rpx 42rpx; margin-top: -42rpx; position: relative; }

/* 特性（原版白色圆角卡） */
.assurance { min-height: 152rpx; margin: 0 -24rpx; padding: 30rpx 25rpx 22rpx; display: grid; grid-template-columns: repeat(4, 1fr); align-items: center; background: #ffffff; border-radius: 52rpx 52rpx 0 0; box-shadow: 0 -10rpx 30rpx rgba(6,72,153,.06); }
.assurance-item { min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 11rpx; color: #263a57; font-size: 22rpx; font-weight: 650; line-height: 1; white-space: nowrap; }
.assurance-icon { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; filter: drop-shadow(0 6rpx 8rpx rgba(12,99,224,.16)); }
.assurance-icon image { width: 64rpx; height: 64rpx; }

/* 搜索 */
.search-box { height: 96rpx; margin-top: 0; display: flex; align-items: center; padding: 0 24rpx; border-radius: 22rpx; }
.search-icon { font-size: 40rpx; color: #6e8098; margin-right: 14rpx; }
.search-box input { flex: 1; font-size: 26rpx; }
.clear { padding: 12rpx; color: #95a1b2; }

/* 分类：换行显示 */
.category-wrap { display: flex; flex-wrap: wrap; gap: 14rpx; margin: 22rpx 2rpx 30rpx; }
.category { height: 64rpx; padding: 0 27rpx; border-radius: 32rpx; background: #ffffff; color: #66758b; display: flex; align-items: center; font-size: 24rpx; border: 1rpx solid #e4eaf2; }
.category.active { background: #126bee; border-color: #126bee; color: #ffffff; box-shadow: 0 8rpx 20rpx rgba(18,107,238,.24); }

/* 服务卡片：双列网格 */
.section-head { display: flex; align-items: flex-end; justify-content: space-between; margin: 0 8rpx 20rpx; }
.section-title { font-size: 34rpx; font-weight: 800; color: #18243a; }
.section-sub { display: block; font-size: 21rpx; color: #929dab; margin-top: 7rpx; }
.count { font-size: 22rpx; color: #7f8b9d; }

.service-grid { display: flex; flex-wrap: wrap; gap: 20rpx; }
.service-card {
  width: calc((100% - 20rpx) / 2);
  padding: 24rpx 22rpx 20rpx;
  display: flex;
  flex-direction: column;
  border-radius: 24rpx;
}
.service-card:active { transform: scale(.98); }
.service-icon { width: 78rpx; height: 78rpx; border-radius: 22rpx; background: #eaf3ff; padding: 14rpx; flex: none; }
.service-icon image { width: 100%; height: 100%; }
.service-name { font-size: 29rpx; font-weight: 750; color: #18243a; margin-top: 18rpx; line-height: 1.35; }
.service-intro { font-size: 22rpx; color: #7d8b9e; line-height: 1.65; margin-top: 10rpx; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.service-tags { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 14rpx; }
.tag { font-size: 19rpx; color: #175cd3; background: #eaf3ff; border: 1rpx solid #d4e4f9; padding: 4rpx 12rpx; border-radius: 8rpx; line-height: 1.4; }
.service-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #eef2f7; }
.service-status { font-size: 22rpx; font-weight: 650; }
.service-status.on { color: #126bee; }
.service-status.off { color: #9b6300; }
.service-arrow { font-size: 34rpx; color: #8298b4; line-height: 1; }

/* ===== 公告弹窗 ===== */
.modal-mask { position: fixed; inset: 0; background: rgba(16,30,48,.6); z-index: 100; display: flex; align-items: center; justify-content: center; padding: 45rpx; }
.modal { width: 100%; padding: 48rpx 38rpx 36rpx; position: relative; }
.modal-icon { width: 68rpx; height: 68rpx; border-radius: 50%; margin: auto; background: #e8f1ff; color: #175cd3; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 38rpx; }
.modal-title { text-align: center; font-size: 34rpx; font-weight: 750; margin: 22rpx 0; }
.modal-content { max-height: 390rpx; line-height: 1.8; color: #59677d; font-size: 26rpx; white-space: pre-wrap; }
.suppress { display: flex; align-items: center; gap: 14rpx; margin: 30rpx 0; font-size: 24rpx; color: #657289; }
.checkbox { width: 34rpx; height: 34rpx; border: 2rpx solid #bbc5d4; border-radius: 7rpx; display: flex; align-items: center; justify-content: center; }
.checked { background: #175cd3; border-color: #175cd3; color: #ffffff; }
.modal-close { position: absolute; right: 24rpx; top: 15rpx; font-size: 50rpx; color: #a3adbb; padding: 10rpx; }
</style>
