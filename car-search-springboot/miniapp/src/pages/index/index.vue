<template>
  <view class="page home">
    <view class="home-head">
      <view class="safe-top" />
      <view class="brand-row">
        <view class="brand-icon"><image src="/static/images/app-avatar.png" mode="aspectFit" /></view>
        <view><view class="brand-name">车档查</view><view class="brand-slogan">专业车辆数据查询平台</view></view>
      </view>
    </view>

    <view class="content">
      <view class="hero-banner">
        <image class="hero-image" src="/static/images/home-hero.jpg" mode="aspectFill" />
        <view class="hero-copy"><view class="hero-tags"><text class="hero-tag primary">官方认证</text><text class="hero-tag">权威数据</text></view><view class="hero-title">查询车辆档案，<text>看清真实车况</text></view><view class="hero-desc">全方位车况查询 让每一次交易更安心</view><view class="hero-trust"><view><image src="/static/icons/features/complete.svg" />车辆信息</view><view><image src="/static/icons/features/complete.svg" />维保记录</view><view><image src="/static/icons/features/complete.svg" />历史里程</view><view><image src="/static/icons/features/complete.svg" />事故记录</view></view></view>
      </view>

      <view class="service-panel card">
        <view class="section-head"><view class="section-title">全部服务</view><text class="section-more">{{ filteredServices.length }} 项服务</text></view>
        <view class="category-wrap"><view class="category" v-for="category in categories" :key="category.code" :class="{ active: categoryCode === category.code }" @click="pickCategory(category.code)">{{ category.name }}</view><view class="category" :class="{ active: categoryCode === '' }" @click="pickCategory('')">全部</view></view>
        <view class="more-grid" v-if="filteredServices.length"><view class="service-item" v-for="service in filteredServices" :key="service.code" @click="openService(service)"><view class="service-icon" :class="{ off: service.status === 0 }"><image :src="iconUrl(service)" mode="aspectFit" /></view><view class="service-name">{{ introOf(service).title }}</view></view></view>
        <view v-else class="empty">该分类下暂无服务</view>
      </view>

      <view class="example-card card">
        <view class="example-title">一份报告，了解车辆历史</view>
        <view class="example-desc">新能源电池报告示例，提前了解报告内容与结构</view>
        <view class="example-grid">
          <view class="example-item"><view class="example-icon"><image src="/static/icons/services/document.svg" mode="aspectFit" /></view><view class="example-item-text"><view class="example-item-title">电池参数信息</view><view class="example-item-sub">类型、容量与厂商</view></view></view>
          <view class="example-item"><view class="example-icon"><image src="/static/icons/services/verified.svg" mode="aspectFit" /></view><view class="example-item-text"><view class="example-item-title">续航与健康度</view><view class="example-item-sub">标称续航、SOH 等级</view></view></view>
          <view class="example-item"><view class="example-icon"><image src="/static/icons/services/search.svg" mode="aspectFit" /></view><view class="example-item-text"><view class="example-item-title">里程核验记录</view><view class="example-item-sub">月度里程趋势等</view></view></view>
          <view class="example-item"><view class="example-icon"><image src="/static/icons/services/shield.svg" mode="aspectFit" /></view><view class="example-item-text"><view class="example-item-title">安全风险评估</view><view class="example-item-sub">衰退速率与风险解析</view></view></view>
        </view>
        <button class="example-btn" @click="openDemoReport">查看报告示例 <text>→</text></button>
        <view class="example-shot"><image src="/static/images/report.jpg" mode="aspectFill" /></view>
      </view>

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
const categoryCode = ref('')
let categoryTouched = false
const BOOTSTRAP_CACHE_KEY = 'bootstrap_cache_v3'

const filteredServices = computed(() => {
  return services.value.filter(service =>
    !categoryCode.value || service.category_code === categoryCode.value
  )
})

function introOf(service) { return serviceIntro(service) }
function pickCategory(code) {
  categoryTouched = true
  categoryCode.value = code
}
function applyBootstrap(data, allowAnnouncement = true) {
  if (!data?.services || !data?.categories) return
  services.value = data.services
  categories.value = data.categories
  // 默认选中第一个子分类（后端按 sort,id 排序，即 id 最小的分类），用户手动点过后不再覆盖
  if (!categoryTouched && !categoryCode.value && categories.value.length) categoryCode.value = categories.value[0].code
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
const iconUrl = service => serviceIcon(service.icon, API_BASE_URL, service.code)
function openService(service) {
  if (service.status !== 1) return uni.showToast({ title: '该服务维护中', icon: 'none' })
  uni.setStorageSync('selected_query_service', service)
  uni.navigateTo({ url: `/pages/query/query?code=${service.code}` })
}
/** 报告示例：打开结果页 demo 模式（模拟新能源电池业务的假报告） */
function openDemoReport() {
  uni.navigateTo({ url: '/pages/result/result?demo=battery' })
}
function closeAnnouncement(fromButton) {
  if (fromButton && suppress.value) uni.setStorageSync(`announcement_${announcement.value.id}_${announcement.value.updated_at}`, Date.now() + announcement.value.suppress_hours * 3600000)
  showAnnouncement.value = false
}
</script>

<style lang="scss" scoped>
.home { background: #F5F7FA; }

/* ===== 顶部品牌区 ===== */
.hero { height: 520rpx; padding: 0 36rpx; color: #ffffff; position: relative; overflow: hidden; background: #2E7DFF; }
.hero-image, .hero-shade { position: absolute; inset: 0; width: 100%; height: 100%; }
.hero-shade { background: linear-gradient(90deg, rgba(30,70,180,.92) 0%, rgba(46,125,255,.72) 55%, rgba(46,125,255,.16) 100%); }
.safe-top { height: calc(var(--status-bar-height) + 56rpx); }
.brand-row { position: relative; top: 16px; display: flex; align-items: center; gap: 12rpx; }
.brand-icon { width: 68rpx; height: 68rpx; border-radius: 18rpx; background: #ffffff; display: flex; align-items: center; justify-content: center; overflow: hidden; box-shadow: 0 8rpx 20rpx rgba(1,35,92,.18); }
.brand-icon image { width: 60rpx; height: 60rpx; }
.brand-name { font-size: 34rpx; font-weight: 700; letter-spacing: 2rpx; }
.brand-dot { opacity: .55; }
.brand-slogan { font-size: 21rpx; opacity: .85; }
.hero-title { position: relative; margin-top: 44rpx; font-size: 60rpx; font-weight: 700; line-height: 1.25; letter-spacing: 3rpx; }
.hero-title-hl { margin-left: 14rpx; }
.hero-desc { position: relative; margin-top: 18rpx; font-size: 25rpx; opacity: .92; letter-spacing: 1rpx; }

/* ===== 内容区 ===== */
.content { padding: 0 28rpx 50rpx; margin-top: -56rpx; position: relative; }

/* 搜索 */
.search-box { height: 96rpx; display: flex; align-items: center; padding: 0 28rpx; border-radius: 24rpx; }
.search-icon { font-size: 40rpx; color: #9CA3AF; margin-right: 16rpx; }
.search-box input { flex: 1; font-size: 26rpx; color: #1F2937; }
.search-box input::placeholder { color: #9CA3AF; }
.clear { padding: 12rpx; color: #9CA3AF; font-size: 36rpx; }

/* 分类 */
.category-wrap { display: flex; flex-wrap: wrap; gap: 16rpx; margin: 28rpx 2rpx 34rpx; }
.category { height: 64rpx; padding: 0 28rpx; border-radius: 32rpx; background: #ffffff; color: #6B7280; display: flex; align-items: center; font-size: 24rpx; border: 1rpx solid #E5E7EB; }
.category.active { background: linear-gradient(90deg, #2784ff, #1265f5); border-color: transparent; color: #ffffff; box-shadow: 0 8rpx 18rpx rgba(18,101,245,.28); }

/* 热门查询网格 */
.section-head { display: flex; align-items: center; justify-content: space-between; margin: 0 4rpx 24rpx; }
.section-title { font-size: 36rpx; font-weight: 700; color: #1F2937; letter-spacing: 1rpx; }
.count { font-size: 22rpx; color: #9CA3AF; }

.service-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 28rpx 12rpx; padding: 8rpx 0 12rpx; }
.service-item { display: flex; flex-direction: column; align-items: center; gap: 14rpx; }
.service-item:active { opacity: .75; }
.service-icon { width: 104rpx; height: 104rpx; border-radius: 28rpx; background: #E0EEFF; display: flex; align-items: center; justify-content: center; }
.service-icon.off { background: #F1F3F5; opacity: .7; }
.service-icon image { width: 62rpx; height: 62rpx; }
.service-name { font-size: 22rpx; color: #1F2937; line-height: 1.35; text-align: center; word-break: break-all; }

/* 报告示例 */
.report-banner { margin-top: 34rpx; padding: 32rpx 30rpx; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; background: linear-gradient(120deg, #E0EEFF, #F4F8FF); border: 1rpx solid #D6E6FF; }
.report-title { font-size: 30rpx; font-weight: 700; color: #1F2937; }
.report-desc { font-size: 23rpx; color: #6B7280; margin-top: 10rpx; }
.report-btn { height: 68rpx; line-height: 68rpx; padding: 0 32rpx; border-radius: 34rpx; background: #2E7DFF; color: #ffffff; font-size: 25rpx; font-weight: 600; margin: 0; }

/* ===== 公告弹窗 ===== */
.modal-mask { position: fixed; inset: 0; background: rgba(16,30,48,.6); z-index: 100; display: flex; align-items: center; justify-content: center; padding: 45rpx; }
.modal { width: 100%; padding: 48rpx 38rpx 36rpx; position: relative; }
.modal-icon { width: 68rpx; height: 68rpx; border-radius: 50%; margin: auto; background: #E0EEFF; color: #2E7DFF; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 38rpx; }
.modal-title { text-align: center; font-size: 34rpx; font-weight: 700; margin: 22rpx 0; }
.modal-content { max-height: 390rpx; line-height: 1.8; color: #6B7280; font-size: 26rpx; white-space: pre-wrap; }
.suppress { display: flex; align-items: center; gap: 14rpx; margin: 30rpx 0; font-size: 24rpx; color: #6B7280; }
.checkbox { width: 34rpx; height: 34rpx; border: 2rpx solid #CBD5E1; border-radius: 8rpx; display: flex; align-items: center; justify-content: center; }
.checked { background: #2E7DFF; border-color: #2E7DFF; color: #ffffff; }
.modal-close { position: absolute; right: 24rpx; top: 15rpx; font-size: 50rpx; color: #9CA3AF; padding: 10rpx; }

/* 首页重构：以 375px 设计稿的 2rpx 换算为基准 */
.home{min-height:100vh;background:#f4f8fd}.home-head{padding:0 36rpx 24rpx;background:#eaf4ff}.safe-top{height:calc(var(--status-bar-height) + 56rpx)}.brand-row{position:static;display:flex;align-items:center;gap:18rpx}.brand-icon{width:72rpx;height:72rpx;border-radius:18rpx;overflow:hidden;box-shadow:0 8rpx 18rpx rgba(34,115,220,.18)}.brand-icon image{width:100%;height:100%}.brand-name{color:#1c3154;font-size:37rpx;font-weight:700;line-height:1.2}.brand-slogan{margin-top:5rpx;color:#71839c;font-size:21rpx;opacity:1}.content{padding:0 26rpx 64rpx;margin:0}.hero-banner{position:relative;height:322rpx;overflow:hidden;border-radius:22rpx;background:#d6eaff;box-shadow:0 12rpx 28rpx rgba(30,105,190,.1)}.hero-banner .hero-image{position:absolute;inset:0;width:100%;height:100%;opacity:.55}.hero-banner::after{content:"";position:absolute;inset:0;background:linear-gradient(90deg,rgba(255,255,255,.8) 0%,rgba(255,255,255,.5) 32%,rgba(255,255,255,0) 58%)}.hero-copy{position:relative;z-index:1;padding:28rpx}.hero-tags{display:flex;align-items:center;gap:12rpx}.hero-tag{display:inline-flex;align-items:center;height:38rpx;padding:0 16rpx;border-radius:10rpx;background:rgba(23,120,238,.12);color:#1778ee;font-size:20rpx;font-weight:600}.hero-tag.primary{background:#1778ee;color:#fff}.hero-banner .hero-title{margin:12rpx 0 0;color:#172e58;font-size:44rpx;font-weight:800;line-height:1.25;letter-spacing:0;white-space:nowrap}.hero-banner .hero-title text{color:#1778ee}.hero-banner .hero-desc{margin:10rpx 0 0;color:#355277;font-size:28rpx;letter-spacing:0;opacity:1}.hero-trust{display:flex;flex-wrap:wrap;gap:12rpx;margin-top:22rpx}.hero-trust view{display:flex;align-items:center;gap:6rpx;padding:10rpx 16rpx;border:1rpx solid rgba(255,255,255,.75);border-radius:13rpx;background:rgba(255,255,255,.5);backdrop-filter:blur(12rpx);-webkit-backdrop-filter:blur(12rpx);color:#1f3c63;font-size:21rpx;font-weight:700;white-space:nowrap}.hero-trust image{width:28rpx;height:28rpx}.tool-card,.service-panel{margin-top:24rpx;padding:28rpx 24rpx;border:1rpx solid #edf2f8;border-radius:22rpx;background:#fff;box-shadow:0 8rpx 28rpx rgba(30,91,160,.05)}.section-head{align-items:center;margin:0}.section-title{color:#21395f;font-size:32rpx;font-weight:700;letter-spacing:0}.section-more{color:#8a9ab0;font-size:21rpx}.service-grid,.more-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:26rpx 8rpx;margin-top:26rpx;padding:0}.more-grid{margin-top:40rpx;row-gap:36rpx}.service-item{min-width:0;display:flex;flex-direction:column;align-items:center;gap:10rpx}.service-icon{width:80rpx;height:80rpx;border-radius:21rpx;background:#fff}.service-icon.off{filter:grayscale(.6);opacity:.6}.service-icon image{width:80rpx;height:80rpx}.service-name{width:100%;overflow:hidden;color:#30405b;font-size:21rpx;line-height:1.35;text-align:center;text-overflow:ellipsis;white-space:nowrap}.empty{padding:48rpx 0;color:#9ba9bb;font-size:23rpx;text-align:center}.recommend-card{position:relative;display:flex;min-height:270rpx;margin-top:24rpx;padding:48rpx 28rpx 26rpx;overflow:hidden;border-radius:22rpx;background:linear-gradient(120deg,#eff7ff,#d9ebff);box-shadow:0 12rpx 28rpx rgba(26,118,221,.08)}.recommend-tag{position:absolute;top:0;left:0;padding:8rpx 24rpx;border-radius:0 0 14rpx 0;background:#1680f5;color:#fff;font-size:20rpx}.recommend-copy{position:relative;z-index:2;width:56%}.recommend-title{color:#1e467e;font-size:39rpx;font-weight:700}.recommend-desc{display:-webkit-box;overflow:hidden;margin-top:10rpx;color:#59718e;font-size:21rpx;line-height:1.5;-webkit-box-orient:vertical;-webkit-line-clamp:2}.recommend-btn{display:inline-flex;align-items:center;gap:12rpx;margin-top:20rpx;padding:13rpx 26rpx;border-radius:36rpx;background:linear-gradient(90deg,#2784ff,#1265f5);color:#fff;font-size:23rpx}.recommend-btn text{font-size:35rpx;line-height:.6}.report-art{position:absolute;right:12rpx;bottom:16rpx;display:flex;align-items:center}.report-sheet{width:130rpx;height:174rpx;padding:18rpx 13rpx;border-radius:10rpx;background:#fff;box-shadow:0 8rpx 20rpx rgba(42,107,181,.15);transform:rotate(-5deg)}.report-sheet-title{color:#245184;font-size:15rpx;font-weight:700}.report-car{margin:13rpx 0;color:#2b91fc;font-size:39rpx;text-align:center}.report-line{height:5rpx;margin-top:9rpx;border-radius:4rpx;background:#dbe8f6}.report-line.long{width:94%}.report-line.short{width:62%}.report-points{margin-left:10rpx;color:#386691;font-size:17rpx;line-height:2.15}.feature-pair{display:flex;gap:18rpx;margin-top:20rpx}.feature-card{width:50%;min-height:152rpx;padding:20rpx 16rpx;position:relative;display:flex;align-items:flex-start;gap:12rpx;border-radius:20rpx}.feature-icon{width:54rpx;height:54rpx;flex:none;padding:10rpx;border-radius:15rpx;background:#e6f3ff}.feature-icon image{width:100%;height:100%}.feature-main{min-width:0;color:#2a4164;font-size:22rpx;font-weight:700;line-height:1.35}.feature-main text{display:-webkit-box;overflow:hidden;margin-top:6rpx;color:#90a0b3;font-size:17rpx;font-weight:400;-webkit-box-orient:vertical;-webkit-line-clamp:2}.feature-arrow{position:absolute;right:13rpx;bottom:13rpx;color:#7c99ba;font-size:31rpx}.example-card{position:relative;margin:22rpx -10rpx 0;padding:34rpx 30rpx;overflow:hidden;border-radius:22rpx;background:#fff}.example-title{color:#1c3154;font-size:32rpx;font-weight:700}.example-desc{margin-top:12rpx;color:#8298b3;font-size:20rpx}.example-grid{display:grid;grid-template-columns:1fr 1fr;gap:22rpx 16rpx;margin-top:26rpx;width:62%}.example-item{min-width:0;display:flex;align-items:center;gap:12rpx}.example-icon{flex:none;width:40rpx;height:40rpx}.example-icon image{width:100%;height:100%}.example-item-text{min-width:0}.example-item-title{color:#2a4164;font-size:21rpx;font-weight:700;white-space:nowrap}.example-item-sub{margin-top:3rpx;color:#98a7ba;font-size:17rpx;white-space:nowrap}.example-btn{display:inline-flex;align-items:center;height:62rpx;margin:30rpx 0 0;padding:0 30rpx;border:1rpx solid #9cc7f5;border-radius:34rpx;background:#fff;color:#1d6ef0;font-size:22rpx;font-weight:600;line-height:1.2}.example-btn::after{border:0}.example-btn text{margin-left:8rpx;font-size:24rpx}.example-shot{position:absolute;right:16rpx;top:110rpx;width:206rpx;height:276rpx;border:1rpx solid #e3eefb;border-radius:14rpx;overflow:hidden;box-shadow:0 10rpx 26rpx rgba(42,107,181,.2);transform:rotate(6deg)}.example-shot image{display:block;width:100%;height:100%}.service-panel .search-box{height:76rpx;display:flex;align-items:center;margin-top:21rpx;padding:0 18rpx;border-radius:14rpx;background:#f6f9fd}.service-panel .search-icon{margin-right:10rpx;color:#8da2ba;font-size:32rpx}.service-panel .search-box input{flex:1;color:#344963;font-size:22rpx}.service-panel .category-wrap{display:flex;flex-wrap:wrap;gap:14rpx;margin:22rpx 0 0}.service-panel .category{height:64rpx;padding:0 26rpx;border:1rpx solid #e5edf6;border-radius:32rpx;background:#fff;color:#71829a;font-size:24rpx}.service-panel .category.active{border-color:transparent;background:linear-gradient(90deg,#2784ff,#1265f5);color:#fff;box-shadow:0 8rpx 18rpx rgba(18,101,245,.28)}

.example-card{border:1rpx solid #dcecff}
</style>
