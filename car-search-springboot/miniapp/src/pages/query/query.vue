<template>
  <view class="page query-page" v-if="service">
    <view class="query-head">
      <view class="service-icon">
        <image :src="serviceIconUrl" mode="aspectFit" />
      </view>
      <view class="query-title">{{ service.name }}</view>
      <view class="query-desc">{{ service.category_name || '车辆信息服务' }}</view>
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

        <!-- 图片上传：行驶证、身份证等 -->
        <view v-else-if="field.type === 'image'" class="image-upload-wrap">
          <view v-if="form[field.key]" class="image-preview">
            <image :src="form[field.key]" mode="aspectFit" class="preview-image" />
            <view class="image-actions">
              <button class="action-btn" @tap="chooseImage(field.key)">重新选择</button>
              <button class="action-btn danger" @tap="clearImage(field.key)">删除</button>
            </view>
          </view>
          <view v-else class="image-placeholder" @tap="chooseImage(field.key)">
            <text class="upload-icon">+</text>
            <text class="upload-text">点击上传{{ field.label }}</text>
            <text class="upload-hint">支持 JPG、PNG 格式，不超过 5MB</text>
          </view>
          <!-- 上传进度 -->
          <view v-if="uploading[field.key]" class="upload-progress">
            <view class="progress-bar"><view class="progress-fill" :style="{ width: uploadProgress[field.key] + '%' }"></view></view>
            <text class="progress-text">上传中 {{ uploadProgress[field.key] }}%</text>
          </view>
        </view>

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
    <view class="service-notice card"><view class="notice-icon">i</view><view><view class="notice-title">服务说明</view><view class="notice-text">{{ service.description || '请按页面要求提交真实、合法且已取得授权的查询信息。' }}</view></view></view>
    <view class="tips card">
      <view class="tips-title">查询须知</view>
      <view>· 系统查询结果仅供参考，不构成任何法律效力</view>
      <view>· 支付成功后系统自动查询，请停留在当前页面等待结果</view>
      <view>· 未查询到信息时，系统会根据实际情况做出退款指引</view>
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
import { delay, paymentPlatform, requestVirtualPayment } from '../../utils/payment'
import { hideLoading, showLoading } from '../../utils/loading'
import PlateKeyboard from '../../components/plate-keyboard/plate-keyboard.vue'
import PlateInput from '../../components/plate-input/plate-input.vue'
import { serviceIcon } from '../../utils/service-icons'

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

// 图片上传状态
const uploading = reactive({})
const uploadProgress = reactive({})

const serviceIconUrl = computed(() => {
  return serviceIcon(service.value?.icon,API_BASE_URL)
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

// 图片选择和上传
async function chooseImage(fieldKey) {
  try {
    const res = await new Promise((resolve, reject) => {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: resolve,
        fail: reject
      })
    })

    const filePath = res.tempFilePaths[0]
    const fileInfo = res.tempFiles[0]

    // 验证文件大小
    if (fileInfo.size > 5 * 1024 * 1024) {
      return uni.showToast({ title: '图片大小不能超过 5MB', icon: 'none' })
    }

    // 上传图片
    uploading[fieldKey] = true
    uploadProgress[fieldKey] = 0

    const uploadResult = await new Promise((resolve, reject) => {
      const uploadTask = uni.uploadFile({
        url: `${API_BASE_URL}/upload/image`,
        filePath: filePath,
        name: 'file',
        formData: {
          type: 'license'
        },
        header: {
          Authorization: `Bearer ${uni.getStorageSync('token') || ''}`
        },
        success: (res) => {
          if (res.statusCode === 200) {
            try {
              const data = JSON.parse(res.data)
              if (data.code === 0) {
                resolve(data.data)
              } else {
                reject(new Error(data.message || '上传失败'))
              }
            } catch (e) {
              reject(new Error('解析响应失败'))
            }
          } else if (res.statusCode === 401) {
            reject(new Error('登录已过期，请重新登录'))
          } else {
            reject(new Error('上传失败，状态码：' + res.statusCode))
          }
        },
        fail: (err) => {
          reject(new Error(err.errMsg || '网络错误'))
        }
      })

      uploadTask.onProgressUpdate((res) => {
        uploadProgress[fieldKey] = res.progress
      })
    })

    // 上传成功，保存 URL
    form.value[fieldKey] = uploadResult.url
    uni.showToast({ title: '上传成功', icon: 'success' })

  } catch (error) {
    if (error.message !== 'chooseImage:fail cancel') {
      uni.showToast({ title: error.message || '上传失败', icon: 'none' })
    }
  } finally {
    uploading[fieldKey] = false
    uploadProgress[fieldKey] = 0
  }
}

function clearImage(fieldKey) {
  form.value[fieldKey] = ''
}

async function submit() {
  if (service.value.status !== 1) return uni.showModal({ title:'功能维护', content:'此功能维护中，请稍后再试', showCancel:false })
  if (!accepted.value) return uni.showToast({ title:'请先同意授权协议与免责声明', icon:'none' })

  // 检查必填的图片字段
  for (const field of service.value.input_schema) {
    if (field.type === 'image' && field.required && !form.value[field.key]) {
      return uni.showToast({ title: `请上传${field.label}`, icon: 'none' })
    }
  }

  submitting.value = true
  const requestKey = newRequestKey()
  try {
    progressText.value = '正在创建订单'
    saveFlow({ request_key:requestKey, service_code:service.value.code, stage:'creating' })
    const serviceCode = service.value.code
    const checkoutPath = `/checkout/create?service_code=${encodeURIComponent(serviceCode)}&request_key=${encodeURIComponent(requestKey)}`
    const created = await checkoutRequest(checkoutPath, {
      method:'POST', serviceCode:service.value.code, idempotencyKey:requestKey,
      data:{ service_code:serviceCode, request_key:requestKey, client_platform:paymentPlatform(), input:form.value, accepted:true }
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
  return checkoutRequest('/checkout/payment', { method:'POST', orderNo, data:{ order_no:orderNo, client_platform:paymentPlatform() } })
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
      if (order.status === 'query_failed') return finish(orderNo)
      if (order.status === 'paid' || order.status === 'querying') return fulfil(orderNo, order)
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
      throw contactError(orderNo, '结果仍在确认中，请凭订单号联系客服核验')
    }
  }
  if (order.status === 'success') return finish(orderNo)
  if (order.status === 'payment_review') throw finalError(order.message)
  if (order.status === 'query_failed') return finish(orderNo)
  return poll(orderNo)
}

async function poll(orderNo) {
  const deadline = Date.now() + 60000
  while (Date.now() < deadline) {
    await delay(2000)
    const order = await checkoutRequest('/checkout/status', { orderNo })
    if (order.status === 'success') return finish(orderNo)
    if (order.status === 'paid') return fulfil(orderNo, order)
    if (order.status === 'query_failed') return finish(orderNo)
    if (['payment_review','cancelled','refunded'].includes(order.status)) throw finalError(order.message || statusMessage(order))
    // 回调模式：querying 状态 → 提示用户去订单列表查看
    if (order.status === 'querying') return showAsyncPending(orderNo)
  }
  // 超时后如果仍在 querying，也提示用户
  return showAsyncPending(orderNo)
}

function showAsyncPending(orderNo) {
  clearFlow()
  uni.showModal({
    title: '订单已提交',
    content: '查询结果需要一定时间处理，请耐心等待。您可以在「我的 → 订单」中随时查看查询进度和结果。',
    confirmText: '查看订单',
    cancelText: '返回首页',
    showCancel: true,
    success: res => {
      if (res.confirm) {
        uni.navigateTo({ url: '/pages/orders/orders' })
      } else {
        uni.switchTab({ url: '/pages/index/index' })
      }
    }
  })
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
    if (order.status === 'query_failed') return finish(orderNo)
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
      title:'结果确认中', content:`${error.message}\n订单号：${error.orderNo}`,
      confirmText:'前往客服', cancelText:'关闭', showCancel:true,
      success:result=>{ if (result.confirm) uni.switchTab({ url:'/pages/my/my' }) }
    })
  }
  uni.showModal({ title:'提示', content:error.message || '请求处理失败，请稍后重试', showCancel:false })
}
function finalError(message) { const error = new Error(message); error.final = true; return error }
function contactError(orderNo, message) { const error = finalError(message || '结果仍在确认中，请凭订单号联系客服核验'); error.contact = true; error.orderNo = orderNo; return error }
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
.query-page{padding-bottom:170rpx}.query-head{text-align:center;padding:55rpx 35rpx 48rpx;background:linear-gradient(180deg,#eaf3ff,#f4f7fb)}.service-icon{width:98rpx;height:98rpx;border-radius:25rpx;background:#e8f1ff;padding:18rpx;display:flex;align-items:center;justify-content:center;margin:auto}.service-icon image{width:100%;height:100%}.query-title{font-size:38rpx;font-weight:800;margin-top:20rpx}.query-desc{font-size:24rpx;color:#718097;margin-top:10rpx}.form-card{margin:0 24rpx;padding:6rpx 30rpx}.field{padding:30rpx 0;border-bottom:1rpx solid #edf0f5}.field:last-of-type{border-bottom:0}.label{font-size:27rpx;font-weight:600;margin-bottom:18rpx}.label text{color:#e3483e}.field input{height:86rpx;border-radius:14rpx;background:#f6f8fb;padding:0 22rpx;font-size:28rpx}.field-tip{font-size:21rpx;color:#8d98a9;margin-top:12rpx}.authorization{display:flex;gap:14rpx;padding:28rpx 0;color:#68758a;font-size:23rpx;line-height:1.65}.authorization text{color:#175cd3}.check{width:34rpx;height:34rpx;flex:none;margin-top:2rpx;border:2rpx solid #b7c2d1;border-radius:7rpx;display:flex;align-items:center;justify-content:center}.checked{color:#fff;background:#175cd3;border-color:#175cd3}.service-notice{margin:24rpx;padding:27rpx;display:flex;gap:18rpx;border:1rpx solid #bdd9ff;background:linear-gradient(135deg,#edf6ff,#fff)}.notice-icon{width:46rpx;height:46rpx;border-radius:50%;background:#176bdf;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:800;flex:none}.notice-title{font-size:27rpx;font-weight:750;color:#135abd}.notice-text{font-size:23rpx;color:#4f6380;line-height:1.7;margin-top:8rpx}.tips{margin:24rpx;padding:28rpx;color:#758297;font-size:23rpx;line-height:1.9}.tips-title{font-size:27rpx;color:#344158;font-weight:700;margin-bottom:8rpx}.maintenance{margin:24rpx;padding:24rpx;text-align:center;background:#fff7e8;color:#9d6200;font-size:25rpx;font-weight:600}.pay-bar{position:fixed;z-index:30;bottom:0;left:0;right:0;min-height:130rpx;padding:18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));background:#fff;box-shadow:0 -8rpx 25rpx rgba(29,49,80,.1);display:flex;align-items:center}.pay-btn{width:100%;height:88rpx;line-height:88rpx;margin:0;border-radius:16rpx;background:#175cd3;color:#fff;font-size:29rpx;font-weight:700}.pay-btn.disabled{background:#aab3c0}

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

/* 图片上传样式 */
.image-upload-wrap {
  margin-top: 10rpx;
}

.image-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 240rpx;
  background: #f6f8fb;
  border: 2rpx dashed #d0d7e2;
  border-radius: 16rpx;
  transition: all 0.2s;

  &:active {
    background: #edf0f5;
    border-color: #aab3c0;
  }

  .upload-icon {
    font-size: 60rpx;
    color: #8d98a9;
    font-weight: 300;
    line-height: 1;
  }

  .upload-text {
    font-size: 26rpx;
    color: #6b7688;
    margin-top: 16rpx;
  }

  .upload-hint {
    font-size: 21rpx;
    color: #aab3c0;
    margin-top: 8rpx;
  }
}

.image-preview {
  position: relative;
  background: #f6f8fb;
  border-radius: 16rpx;
  overflow: hidden;

  .preview-image {
    width: 100%;
    height: 320rpx;
    display: block;
  }

  .image-actions {
    display: flex;
    gap: 16rpx;
    padding: 20rpx;
    background: linear-gradient(180deg, transparent, rgba(0,0,0,0.3));

    .action-btn {
      flex: 1;
      height: 72rpx;
      line-height: 72rpx;
      font-size: 26rpx;
      font-weight: 600;
      border-radius: 12rpx;
      background: #fff;
      color: #344158;
      border: none;

      &.danger {
        background: #fff0f0;
        color: #e3483e;
      }

      &:active {
        opacity: 0.8;
      }
    }
  }
}

.upload-progress {
  margin-top: 16rpx;

  .progress-bar {
    height: 8rpx;
    background: #edf0f5;
    border-radius: 4rpx;
    overflow: hidden;

    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #175cd3, #4d8eff);
      border-radius: 4rpx;
      transition: width 0.3s ease;
    }
  }

  .progress-text {
    display: block;
    text-align: center;
    font-size: 22rpx;
    color: #8d98a9;
    margin-top: 10rpx;
  }
}
</style>
