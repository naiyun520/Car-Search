/** WeChat downloads the package; this controller only prompts and applies it. */
export function createUpdatePrompt(api) {
  let manager = null
  let ready = false
  let foreground = false
  let showing = false
  let applying = false

  function prompt() {
    if (!manager || !ready || !foreground || showing || applying) return
    showing = true
    try {
      api.showModal({
        title: '更新提示',
        content: '新版本已准备好，请重启更新后继续使用。',
        showCancel: false,
        confirmText: '重启更新',
        confirmColor: '#2E7DFF',
        success(result) {
          if (!result.confirm || applying) return
          applying = true
          try {
            manager.applyUpdate()
          } catch {
            applying = false
            api.showToast?.({ title: '更新启动失败，请重新进入小程序', icon: 'none' })
          }
        },
        complete() { showing = false }
      })
    } catch {
      showing = false
    }
  }

  return {
    start() {
      if (manager) return true
      if (!api || typeof api.getUpdateManager !== 'function' || typeof api.showModal !== 'function') return false
      try {
        const candidate = api.getUpdateManager()
        if (!candidate || typeof candidate.onUpdateReady !== 'function' || typeof candidate.onUpdateFailed !== 'function' || typeof candidate.applyUpdate !== 'function') return false
        manager = candidate
        manager.onUpdateReady(() => { ready = true; prompt() })
        manager.onUpdateFailed(() => { ready = false })
        return true
      } catch {
        manager = null
        return false
      }
    },
    onShow() { foreground = true; prompt() },
    onHide() { foreground = false }
  }
}
