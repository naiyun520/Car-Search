import { API_BASE_URL } from '../config'
import { clearLogin, ensureLogin } from './auth'
import { CLIENT_VERSION } from './protocol'

export { CLIENT_VERSION }

export async function request(path, options = {}, retried = false) {
  const token = options.public ? '' : await ensureLogin()
  const method = String(options.method || 'GET').toUpperCase()
  const headers = {
    Accept: 'application/json',
    'X-Car-Client-Version': CLIENT_VERSION,
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {})
  }
  let data
  if (method !== 'GET' && method !== 'HEAD') {
    headers['Content-Type'] = 'application/json; charset=utf-8'
    data = JSON.stringify(options.data || {})
  }
  let response
  try {
    response = await new Promise((resolve, reject) => uni.request({
      url: `${API_BASE_URL}${path}`,
      method,
      data,
      header: headers,
      timeout: options.timeout || 20000,
      success: resolve,
      fail: reject
    }))
  } catch (error) {
    const networkError = new Error(error?.errMsg || error?.message || '网络连接失败，请检查网络后重试')
    networkError.network = true
    throw networkError
  }
  if (response.statusCode === 401 && !retried && !options.public) {
    clearLogin()
    return request(path, options, true)
  }
  if (response.statusCode < 200 || response.statusCode >= 300 || response.data?.code !== 0) {
    const error = new Error(response.data?.message || '服务暂时不可用，请稍后重试')
    error.statusCode = response.statusCode
    error.code = response.data?.code
    error.errorId = response.data?.error_id
    throw error
  }
  return response.data.data
}

export function checkoutRequest(path, { serviceCode = '', orderNo = '', idempotencyKey = '', ...options } = {}) {
  const headers = { ...(options.headers || {}) }
  if (serviceCode) headers['X-Car-Service-Code'] = serviceCode
  if (orderNo) headers['X-Car-Order-No'] = orderNo
  if (idempotencyKey) headers['X-Idempotency-Key'] = idempotencyKey

  // Business identifiers live in the request payload/query. Headers are a
  // redundant integrity channel, not the only copy a proxy is allowed to lose.
  const method = String(options.method || 'GET').toUpperCase()
  if (method === 'GET' || method === 'HEAD') {
    const params = []
    if (serviceCode) params.push(`service_code=${encodeURIComponent(serviceCode)}`)
    if (orderNo) params.push(`order_no=${encodeURIComponent(orderNo)}`)
    if (idempotencyKey) params.push(`request_key=${encodeURIComponent(idempotencyKey)}`)
    const query = params.join('&')
    const requestPath = query ? `${path}${path.includes('?') ? '&' : '?'}${query}` : path
    return request(requestPath, { ...options, headers })
  }

  const data = { ...(options.data || {}) }
  if (serviceCode) data.service_code = serviceCode
  if (orderNo) data.order_no = orderNo
  if (idempotencyKey) data.request_key = idempotencyKey
  return request(path, { ...options, data, headers })
}
