const MIN_SDK = '2.19.2'
const MIN_IOS_WECHAT = '8.0.68'
const MIN_IOS_MAJOR = 15
const MIN_IOS_PRICE_CENTS = 100

export async function requestVirtualPayment(payment) {
  if (typeof wx === 'undefined' || !supported()) {
    throw new Error('当前微信版本不支持虚拟支付，请升级微信后重试')
  }
  const missing = ['signData', 'paySig', 'signature', 'mode'].filter(key => !payment?.[key])
  if (missing.length) throw new Error(`支付参数不完整（${missing.join('、')}），请联系客服`)
  validatePayment(payment)

  return new Promise((resolve, reject) => wx.requestVirtualPayment({
    signData: payment.signData,
    paySig: payment.paySig,
    signature: payment.signature,
    mode: payment.mode,
    success: resolve,
    fail: result => {
      const error = paymentError(result)
      reject(error)
    }
  }))
}

function validatePayment(payment) {
  if (typeof payment.signData !== 'string') throw new Error('支付参数格式错误：signData 必须是字符串')
  let signData
  try {
    signData = JSON.parse(payment.signData)
  } catch (_) {
    throw new Error('支付参数格式错误：signData 不是有效 JSON')
  }
  if (!signData || Array.isArray(signData) || typeof signData !== 'object') {
    throw new Error('支付参数格式错误：signData 内容无效')
  }
  const required = ['offerId', 'buyQuantity', 'currencyType', 'outTradeNo', 'attach']
  const missing = required.filter(key => signData[key] === undefined || signData[key] === null || signData[key] === '')
  if (payment.mode === 'short_series_goods') {
    for (const key of ['productId', 'goodsPrice']) {
      if (signData[key] === undefined || signData[key] === null || signData[key] === '') missing.push(key)
    }
  }
  if (missing.length) throw new Error(`支付参数不完整（${[...new Set(missing)].join('、')}），请联系客服`)
  if (!['short_series_goods', 'short_series_coin'].includes(payment.mode)) throw new Error('支付类型配置错误，请联系客服')
  if (signData.currencyType !== 'CNY') throw new Error('支付币种配置错误，请联系客服')
  const payEnv = signData.env === undefined ? 0 : Number(signData.env)
  if (![0, 1].includes(payEnv)) throw new Error('支付环境配置错误，请联系客服')
  if (!Number.isInteger(Number(signData.buyQuantity)) || Number(signData.buyQuantity) < 1) throw new Error('购买数量配置错误，请联系客服')
  if (!/^[A-Za-z0-9_\-|*@]{8,32}$/.test(String(signData.outTradeNo)) || String(signData.outTradeNo).startsWith('_')) {
    throw new Error('支付订单号格式错误，请联系客服')
  }
  if (payment.mode === 'short_series_goods' && (!Number.isInteger(Number(signData.goodsPrice)) || Number(signData.goodsPrice) < 1)) {
    throw new Error('支付金额配置错误，请联系客服')
  }

  const device = wx.getDeviceInfo?.() || {}
  if (String(device.platform || '').toLowerCase() !== 'ios') return
  const iosMajor = Number(String(device.system || '').match(/iOS\s*(\d+)/i)?.[1] || 0)
  if (iosMajor && iosMajor < MIN_IOS_MAJOR) throw new Error('Apple 支付要求 iOS 15 或更高版本，请升级系统后重试')
  const wechatVersion = String(wx.getAppBaseInfo?.().version || '')
  if (wechatVersion && compareVersion(wechatVersion, MIN_IOS_WECHAT) < 0) throw new Error('Apple 支付要求微信 8.0.68 或更高版本，请升级微信后重试')
  if (payEnv !== 0) throw new Error('Apple 支付不支持沙箱环境，请联系客服切换为正式环境')
  if (payment.mode === 'short_series_goods' && Number(signData.goodsPrice) < MIN_IOS_PRICE_CENTS) {
    throw new Error('Apple 支付最低金额为 1 元，请返回首页选择价格不低于 1 元的服务')
  }
}

function supported() {
  if (wx.canIUse?.('requestVirtualPayment') === true) return true
  const sdk = wx.getAppBaseInfo?.().SDKVersion || '0.0.0'
  return compareVersion(sdk, MIN_SDK) >= 0
}

function compareVersion(left, right) {
  const a = left.split('.').map(Number), b = right.split('.').map(Number)
  for (let index = 0; index < Math.max(a.length, b.length); index += 1) {
    const difference = (a[index] || 0) - (b[index] || 0)
    if (difference) return difference
  }
  return 0
}

function paymentError(result) {
  let code = Number(result?.errCode ?? result?.errno ?? 0)
  const official = String(result?.errMsg || '').trim()
  if (!code) code = Number(official.match(/-150\d+|(?:^|\s)-?[1245](?:\s|$)/)?.[0]?.trim() || 0)
  const messages = {
    1001: '支付参数错误', [-1]: '支付未完成', [-2]: '支付已取消', [-4]: '支付被微信风控拦截', [-5]: '签约结果暂未确认',
    [-15001]: '微信返回支付参数错误', [-15002]: '该支付单号已使用，请重新下单', [-15003]: '微信支付服务暂时异常',
    [-15004]: '支付币种配置错误', [-15005]: '微信登录态签名错误', [-15006]: '支付签名错误，请检查当前环境 AppKey',
    [-15007]: '微信登录态已过期', [-15008]: '虚拟支付商户进件未完成', [-15009]: '虚拟代币尚未发布',
    [-15010]: '支付道具未发布或道具ID配置不一致', [-15011]: '正式版不能使用沙箱支付环境',
    [-15012]: '微信创建支付订单失败，请重新下单', [-15013]: '微信道具价格与系统价格不一致',
    [-15014]: '支付道具尚未生效，请约10分钟后重试', [-15016]: '支付参数格式错误',
    [-15017]: '商户收款功能受限', [-15018]: '支付道具审核未通过', [-15019]: '微信商户状态受限',
    [-15020]: '操作过快，请稍后重试', [-15021]: '交易过于频繁，请稍后重试'
  }
  const detail = publicErrorDetail(official, code)
  const showDetail = [1001, -15001, -15016].includes(code) && detail
  const error = new Error(`${messages[code] || '微信虚拟支付未完成'}${code ? `（${code}）` : ''}${showDetail ? `：${detail}` : ''}`)
  error.errCode = code
  error.errMsg = official
  error.cancelled = code === -2 || /cancel/i.test(official)
  error.needRefreshLogin = code === -15005 || code === -15007
  error.needNewOrder = code === -15002 || code === -15012
  // These results do not prove that no charge occurred; reconcile them with
  // query_order. All other documented failures are deterministic and must be
  // shown immediately instead of being hidden behind a 60-second poll.
  error.paymentUncertain = code === 0 || [-1, -5, -15003].includes(code)
  return error
}

function publicErrorDetail(message, code) {
  return String(message || '')
    .replace(/^requestVirtualPayment:\s*fail\s*/i, '')
    .replace(new RegExp(`(^|[\\s,，:：()（）])${String(code).replace('-', '\\-')}($|[\\s,，:：()（）])`, 'g'), ' ')
    .replace(/\b(paySig|signature|session_key|appkey)\s*[:=]\s*\S+/ig, '$1=[已隐藏]')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 160)
}

export const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds))
