let loadingVisible = false

export function showLoading(options) {
  loadingVisible = true
  uni.showLoading(options)
}

export function hideLoading() {
  if (!loadingVisible) return
  loadingVisible = false
  uni.hideLoading({ fail: () => {} })
}
