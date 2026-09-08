<template>
  <view class="page query-page" v-if="service">
    <view class="query-head">
      <view class="service-icon">
        <image v-if="serviceIconUrl" :src="serviceIconUrl" mode="aspectFill" />
        <view v-else class="default-service-icon"><view class="car-body" /><view class="wheel left" /><view class="wheel right" /></view>
      </view>
      <view class="query-title">{{ service.name }}</view>
      <view class="query-desc">{{ service.description }}</view>
    </view>
    <view class="form-card card">
      <view class="field" v-for="field in service.input_schema" :key="field.key">
        <view class="label">{{ field.label }} <text v-if="field.required">*</text></view>

        <!-- 车牌号：格子输入框 + 自定义键盘 -->
        <PlateInput v-if="field.type === 'plate'"
          ref="plateInputRefs"
          v-model="form[field.key]"
          @focus="openPlateKeyboard(field)" />

        <!-- 车牌或VIN：带切换选项 -->
        <view v-else-if="field.type === 'plate_or_vin'" class="mixed-input-wrap">
          <view class="mixed-tabs">
            <view class="tab" :class="{ active: inputMode[field.key] === 'plate' }" @tap="switchInputMode(field.key, 'plate')">车牌号</view>
            <view class="tab" :class="{ active: inputMode[field.key] === 'vin' }" @tap="switchInputMode(field.key, 'vin')">VIN车架号</view>
          </view>
          <!-- 车牌模式 -->
          <PlateInput v-if="inputMode[field.key] === 'plate'"
            :ref="el => setPlateRef(field.key, el)"
            v-model="form[field.key]"
            @focus="openPlateKeyboard(field)" />
          <!-- VIN模式 -->
          <input v-else
            v-model="form[field.key]"
            :maxlength="17"
            placeholder="请输入17位VIN车架号"
            type="text"
            @blur="normalize(field.key)" />
        </view>

        <!-- 车牌前缀：格子输入框 + 自定义键盘 -->
        <PlateInput v-else-if="field.type === 'plate_prefix'"
          ref="plateInputRefs"
          v-model="form[field.key]"
          @focus="openPlateKeyboard(field)" />

        <!-- 其他字段：原生输入框 -->
        <input v-else
          v-model="form[field.key]"
          :maxlength="maxLength(field.type)"
          :placeholder="`请输入${field.label.replace('（选填）','')}`"
          :type="getInputType(field.type)"
          @blur="normalize(field.key)" />

        <view class="field-tip" v-if="field.type==='vin'">VIN为17位字母与数字组合，不含 I、O、Q</view>
      </view>
      <view class="authorization" @click="accepted=!accepted">
        <view class="check" :class="{checked:accepted}">{{ accepted?'✓':'' }}</view>
        <view>我已阅读并同意<text @click.stop="openAgreement">《用户授权协议》与《免责声明》</text>，确认已取得合法查询授权并承担合规使用责任。</view>
      </view>
    </view>
    <view class="tips card">
      <view class="tips-title">查询须知</view>
      <view>· 支付结果以微信服务器查单为准，客户端回调不会作为付款凭证</view>
      <view>· 支付成功后系统自动查询，请停留在当前页面等待结果</view>
      <view>· 查询异常会保留付款订单，请凭订单号联系客服处理</view>
    </view>
    <view class="maintenance card" v-if="service.status===0">此功能维护中，请稍后再试</view>
    <view class="bottom-safe" />
    <view class="pay-bar"><button class="pay-btn" :class="{disabled:service.status===0}" :disabled="submitting" @click="submit">{{ service.status===0?'功能维护中':(submitting?progressText:'查询并支付') }}</button></view>

    <!-- 车牌专用键盘 -->
    <PlateKeyboard
      :visible="keyboardVisible"
      mode="plate"
      @input="onKeyInput"
      @delete="onKeyDelete"
      @close="closeKeyboard"
    />
  </view>
</template>

<script setup>
import { computed, ref, reactive } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { CLIENT_VERSION, checkoutRequest, request } from '../../utils/request'
import { API_BASE_URL } from '../../config'
import { clearLogin, ensureLogin } from '../../utils/auth'
import { delay, requestVirtualPayment } from '../../utils/payment'
import { hideLoading, showLoading } from '../../utils/loading'
import PlateKeyboard from '../../components/plate-keyboard/plate-keyboard.vue'
import PlateInput from '../../components/plate-input/plate-input.vue'

// 分享配置
onShareAppMessage(() => ({
  title: '车辆信息查询 - 快速查询车辆信息',
  path: `/pages/query/query?code=${service.value?.code || ''}`
}))

const FLOW_KEY = 'checkout_flow_v2'
const service = ref(null)
const form = ref({})
const accepted = ref(false)
const submitting = ref(false)
const progressText = ref('正在处理')

// 键盘相关状态（仅用于车牌字段）
const keyboardVisible = ref(false)
const activeField = ref('')

// 格子输入框引用
const plateInputRefs = ref([])
// plate_or_vin 类型的单独引用
const mixedPlateRefs = reactive({})

// 输入模式记录（plate_or_vin 类型）
const inputMode = reactive({})

const serviceIconUrl = computed(() => {
  const filename=String(service.value?.icon||'')
  return /^[a-f0-9]{32}\.(?:png|jpg|webp)$/.test(filename) ? `${API_BASE_URL}/service-icon/${filename}` : ''
})

onLoad(async options => {
  try {
    const selected = uni.getStorageSync('selected_query_service') || null
    const flow = readFlow()
    const serviceCode = String(options?.code || selected?.code || flow?.service_code || '')
    const bootstrap = await request('/bootstrap', { public: true })
    if (bootstrap.protocol_version !== CLIENT_VERSION) throw new Error('系统正在升级，请重新打开小程序')
    service.value = bootstrap.services.find(item => item.code === serviceCode) || null
    if (!service.value) {
      uni.removeStorageSync('selected_query_service')
      throw new Error('服务目录已更新，请返回首页重新选择')
    }
    initializeForm()
    if (flow?.order_no && flow.stage === 'confirm' && flow.service_code === serviceCode) {
      await resumePaidFlow(flow.order_no)
    } else if (flow) {
      clearFlow()
    }
  } catch (error) {
    uni.showModal({ title:'提示', content:error.message || '页面加载失败', showCancel:false, success:()=>uni.navigateBack() })
  }
})

const maxLength = type => type === 'vin' ? 17 : type === 'name' ? 30 : 24
function normalize(key) { form.value[key] = String(form.value[key] || '').trim().toUpperCase() }
function openAgreement() { uni.navigateTo({ url:'/pages/agreement/agreement' }) }
function initializeForm() {
  service.value.input_schema.forEach(field => {
    form.value[field.key] = ''
    // 初始化 plate_or_vin 类型的输入模式
    if (field.type === 'plate_or_vin') {
      inputMode[field.key] = 'plate'
    }
  })
}

// 获取原生输入框类型
function getInputType(type) {
  if (type === 'idcard') return 'idcard'
  return 'text'
}

// 切换 plate_or_vin 的输入模式
function switchInputMode(fieldKey, mode) {
  inputMode[fieldKey] = mode
  // 清空已输入的内容
  form.value[fieldKey] = ''
}

// 设置混合模式的 plate ref
function setPlateRef(fieldKey, el) {
  if (el) {
    mixedPlateRefs[fieldKey] = el
  }
}

// 获取当前车牌输入框组件
function getCurrentPlateInput() {
  if (!activeField.value) return null

  // 先检查是否是 plate_or_vin 类型
  const field = service.value.input_schema.find(f => f.key === activeField.value)
  if (field?.type === 'plate_or_vin') {
    return mixedPlateRefs[activeField.value] || null
  }

  // 普通 plate 类型
  const fields = service.value.input_schema.filter(f => f.type === 'plate' || f.type === 'plate_prefix')
  const index = fields.findIndex(f => f.key === activeField.value)
  return plateInputRefs.value[index] || null
}

// 车牌键盘相关函数
function openPlateKeyboard(field) {
  activeField.value = field.key
  keyboardVisible.value = true
}

function closeKeyboard() {
  keyboardVisible.value = false
  activeField.value = ''
}

function onKeyInput(key) {
  const plateInput = getCurrentPlateInput()
  if (plateInput) {
    plateInput.addChar(key)
  }
}

function onKeyDelete() {
  const plateInput = getCurrentPlateInput()
  if (plateInput) {
    plateInput.deleteChar()
  }
}

async function submit() {
  if (service.value.status !== 1) return uni.showModal({ title:'功能维护', content:'此功能维护中，请稍后再试', showCancel:false })
  if (!accepted.value) return uni.showToast({ title:'请先同意授权协议与免责声明', icon:'none' })
  submitting.value = true
  const requestKey = newRequestKey()
  try {
    progressText.value = '正在创建订单'
    saveFlow({ request_key:requestKey, service_code:service.value.code, stage:'creating' })
    const serviceCode = service.value.code
    const checkoutPath = `/checkout/create?service_code=${encodeURIComponent(serviceCode)}&request_key=${encodeURIComponent(requestKey)}`
    const created = await checkoutRequest(checkoutPath, {
      method:'POST', serviceCode:service.value.code, idempotencyKey:requestKey,
      data:{ service_code:serviceCode, request_key:requestKey, input:form.value, accepted:true }
    })
    const orderNo = created.order.order_no
    saveFlow({ request_key:requestKey, order_no:orderNo, service_code:service.value.code, stage:'payment' })
    const payment = created.payment || (await paymentParams(orderNo)).payment
    if (payment) await pay(orderNo, payment)
    await confirmAndFulfil(orderNo)
  } catch (error) {
    await handleFlowError(error)
  } finally {
    hideLoading()
    submitting.value = false
    progressText.value = '正在处理'
  }
}

async function paymentParams(orderNo) {
  return checkoutRequest('/checkout/payment', { method:'POST', orderNo, data:{ order_no:orderNo } })
}

async function pay(orderNo, payment, retried=false) {
  progressText.value = '等待支付'
  try {
    await requestVirtualPayment(payment)
    saveFlow({ ...readFlow(), order_no:orderNo, stage:'confirm' })
  } catch (error) {
    if (error.needRefreshLogin && !retried) {
      clearLogin()
      await ensureLogin()
      const refreshed = await paymentParams(orderNo)
      if (refreshed.payment) return pay(orderNo, refreshed.payment, true)
      return
    }
    if (error.cancelled) {
      clearFlow()
      throw finalError('支付已取消，本次未确认扣款')
    }
    if (error.needNewOrder) {
      clearFlow()
      throw finalError(`${error.message}。原支付单已不可使用，请重新提交查询`)
    }
    if (!error.paymentUncertain) throw error
    saveFlow({ ...readFlow(), order_no:orderNo, stage:'confirm', payment_message:error.message })
  }
}

async function confirmAndFulfil(orderNo) {
  progressText.value = '正在确认支付'
  showLoading({ title:'正在确认支付', mask:true })
  const deadline = Date.now() + 60000
  let lastError
  while (Date.now() < deadline) {
    try {
      const order = await checkoutRequest('/checkout/confirm', { method:'POST', orderNo, data:{ order_no:orderNo } })
      if (order.status === 'success') return finish(orderNo)
      if (order.status === 'paid' || order.status === 'querying' || order.status === 'query_failed') return fulfil(orderNo, order)
      if (order.status === 'payment_review') throw finalError(order.message)
      if (order.status === 'cancelled' || order.status === 'refunded') throw finalError(statusMessage(order))
    } catch (error) {
      if (error.final || !isTransientRequestError(error)) throw error
      lastError = error
    }
    await delay(1800)
  }
  const paymentMessage = readFlow()?.payment_message
  const error = lastError || new Error(paymentMessage
    ? `${paymentMessage}；支付结果仍在确认中，请勿重复付款，请凭订单号联系客服核验`
    : '支付结果仍在确认中，请勿重复付款，请凭订单号联系客服核验')
  error.preserveFlow = true
  throw error
}

async function fulfil(orderNo, current=null) {
  progressText.value = '正在查询'
  showLoading({ title:'正在安全查询', mask:true })
  let order = current
  if (!order || order.status === 'paid') {
    try {
      order = await checkoutRequest('/checkout/query', { method:'POST', orderNo, data:{ order_no:orderNo }, timeout:40000 })
    } catch (error) {
      throw contactError(orderNo, '查询请求未完成，请联系客服处理')
    }
  }
  if (order.status === 'success') return finish(orderNo)
  if (order.status === 'payment_review') throw finalError(order.message)
  if (order.status === 'query_failed') throw contactError(orderNo, order.message)
  return poll(orderNo)
}

async function poll(orderNo) {
  const deadline = Date.now() + 60000
  while (Date.now() < deadline) {
    await delay(2000)
    const order = await checkoutRequest('/checkout/status', { orderNo })
    if (order.status === 'success') return finish(orderNo)
    if (order.status === 'paid') return fulfil(orderNo, order)
    if (order.status === 'query_failed') throw contactError(orderNo, order.message)
    if (['payment_review','cancelled','refunded'].includes(order.status)) throw finalError(order.message || statusMessage(order))
  }
  throw contactError(orderNo, '订单仍在处理中，请凭订单号联系客服核验')
}

async function resumePaidFlow(orderNo) {
  submitting.value = true
  try {
    const order = await checkoutRequest('/checkout/status', { orderNo })
    if (order.status === 'success') return finish(orderNo)
    if (order.status === 'pending_payment') {
      clearFlow()
      return
    }
    if (order.status === 'paid' || order.status === 'querying') return fulfil(orderNo, order)
    if (order.status === 'query_failed') throw contactError(orderNo, order.message)
    throw finalError(order.message || statusMessage(order))
  } catch (error) {
    await handleFlowError(error)
  } finally {
    hideLoading()
    submitting.value = false
  }
}

function finish(orderNo) {
  clearFlow()
  uni.redirectTo({ url:`/pages/result/result?order_no=${orderNo}` })
}
function statusMessage(order) {
  return ({ cancelled:'订单已关闭', refunded:'订单已退款', payment_review:'支付信息需要人工核对，请勿重复支付' })[order.status] || '订单暂未完成'
}
async function handleFlowError(error) {
  if (!error.preserveFlow) clearFlow()
  if (error.contact) {
    return uni.showModal({
      title:'查询未成功', content:`${error.message}\n订单号：${error.orderNo}`,
      confirmText:'联系客服', cancelText:'关闭', showCancel:true,
      success:result=>{ if (result.confirm) openFeedback(error.orderNo) }
    })
  }
  uni.showModal({ title:'提示', content:error.message || '请求处理失败，请稍后重试', showCancel:false })
}
function finalError(message) { const error = new Error(message); error.final = true; return error }
function contactError(orderNo, message) { const error = finalError(message || '查询未成功，请联系客服处理'); error.contact = true; error.orderNo = orderNo; return error }
function openFeedback(orderNo) { uni.navigateTo({ url:`/pages/feedback/feedback?order_no=${encodeURIComponent(orderNo)}` }) }
function isTransientRequestError(error) {
  if (error?.network) return true
  const status = Number(error?.statusCode || 0)
  return status === 0 || status === 408 || status === 425 || status === 429 || status >= 500
}
function newRequestKey() { return `${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}`.slice(0,64) }
function saveFlow(flow) { uni.setStorageSync(FLOW_KEY, { ...flow, updated_at:Date.now() }) }
function readFlow() {
  const flow = uni.getStorageSync(FLOW_KEY)
  if (!flow?.request_key || Date.now() - Number(flow.updated_at || 0) > 24 * 3600000) { clearFlow(); return null }
  return flow
}
function clearFlow() { uni.removeStorageSync(FLOW_KEY) }
</script>

<style lang="scss" scoped>
.query-page{padding-bottom:150rpx}.query-head{text-align:center;padding:55rpx 35rpx 48rpx;background:linear-gradient(180deg,#eaf3ff,#f4f7fb)}.service-icon{width:98rpx;height:98rpx;border-radius:25rpx;background:#e8f1ff;display:flex;align-items:center;justify-content:center;margin:auto;overflow:hidden}.service-icon image{width:100%;height:100%}.default-service-icon{width:66rpx;height:50rpx;position:relative}.car-body{position:absolute;left:6rpx;right:6rpx;top:14rpx;height:26rpx;border:5rpx solid #175cd3;border-radius:12rpx 12rpx 7rpx 7rpx}.car-body::before{content:"";position:absolute;left:10rpx;right:10rpx;top:-16rpx;height:17rpx;border:5rpx solid #175cd3;border-bottom:0;border-radius:12rpx 12rpx 0 0}.wheel{position:absolute;width:10rpx;height:10rpx;background:#175cd3;border-radius:50%;bottom:0}.wheel.left{left:14rpx}.wheel.right{right:14rpx}.query-title{font-size:38rpx;font-weight:800;margin-top:20rpx}.query-desc{font-size:24rpx;color:#718097;margin-top:10rpx}.form-card{margin:0 24rpx;padding:6rpx 30rpx}.field{padding:30rpx 0;border-bottom:1rpx solid #edf0f5}.field:last-of-type{border-bottom:0}.label{font-size:27rpx;font-weight:600;margin-bottom:18rpx}.label text{color:#e3483e}.field input{height:86rpx;border-radius:14rpx;background:#f6f8fb;padding:0 22rpx;font-size:28rpx}.field-tip{font-size:21rpx;color:#8d98a9;margin-top:12rpx}.authorization{display:flex;gap:14rpx;padding:28rpx 0;color:#68758a;font-size:23rpx;line-height:1.65}.authorization text{color:#175cd3}.check{width:34rpx;height:34rpx;flex:none;margin-top:2rpx;border:2rpx solid #b7c2d1;border-radius:7rpx;display:flex;align-items:center;justify-content:center}.checked{color:#fff;background:#175cd3;border-color:#175cd3}.tips{margin:24rpx;padding:28rpx;color:#758297;font-size:23rpx;line-height:1.9}.tips-title{font-size:27rpx;color:#344158;font-weight:700;margin-bottom:8rpx}.maintenance{margin:24rpx;padding:24rpx;text-align:center;background:#fff7e8;color:#9d6200;font-size:25rpx;font-weight:600}.pay-bar{position:fixed;bottom:0;left:0;right:0;height:130rpx;padding:18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));background:#fff;box-shadow:0 -8rpx 25rpx rgba(29,49,80,.08);display:flex;align-items:center}.pay-btn{width:100%;height:88rpx;line-height:88rpx;margin:0;border-radius:16rpx;background:#175cd3;color:#fff;font-size:29rpx;font-weight:700}.pay-btn.disabled{background:#aab3c0}

/* 混合输入（车牌/VIN）样式 */
.mixed-input-wrap {
  .mixed-tabs {
    display: flex;
    gap: 16rpx;
    margin-bottom: 20rpx;

    .tab {
      flex: 1;
      height: 64rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 12rpx;
      font-size: 24rpx;
      font-weight: 500;
      background: #f0f3f7;
      color: #6b7688;
      transition: all 0.2s;

      &.active {
        background: #e8f1ff;
        color: #175cd3;
        font-weight: 600;
      }
    }
  }
}
</style>
