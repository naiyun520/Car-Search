import { API_BASE_URL } from '../config'
import { CLIENT_VERSION } from './protocol'

const AUTH_VERSION = '2'
let loginPromise = null

export async function ensureLogin() {
  const token = uni.getStorageSync('access_token')
  const version = String(uni.getStorageSync('auth_version') || '')
  if (token && version === AUTH_VERSION) return token
  if (token || version) clearLogin()
  if (loginPromise) return loginPromise
  loginPromise = login()
  try {
    return await loginPromise
  } finally {
    loginPromise = null
  }
}

async function login() {
  let loginResult
  let response
  try {
    loginResult = await new Promise((resolve, reject) => uni.login({ provider: 'weixin', success: resolve, fail: reject }))
    response = await new Promise((resolve, reject) => uni.request({
      url: `${API_BASE_URL}/auth/login`, method: 'POST', data: JSON.stringify({ code:loginResult.code }),
      header:{ Accept:'application/json', 'Content-Type':'application/json; charset=utf-8', 'X-Car-Client-Version':CLIENT_VERSION },
      timeout: 20000,
      success: resolve, fail: reject
    }))
  } catch (error) {
    throw new Error(error?.errMsg || error?.message || '登录失败，请检查网络后重试')
  }
  if (response.statusCode !== 200 || response.data?.code !== 0) throw new Error(response.data?.message || '登录失败')
  uni.setStorageSync('access_token', response.data.data.token)
  uni.setStorageSync('auth_version', AUTH_VERSION)
  return response.data.data.token
}

export function clearLogin() {
  uni.removeStorageSync('access_token')
  uni.removeStorageSync('auth_version')
}
