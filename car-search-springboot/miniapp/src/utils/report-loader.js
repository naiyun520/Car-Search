// 自动刷新、手动刷新和返回页面共享一次读取；离开页面后旧响应不再更新页面。
export function createReportLoader(load, apply) {
  let generation = 0
  let pending = null
  async function refresh() {
    if (pending) {
      if (pending.generation === generation) return pending.promise
      try { await pending.promise } catch {}
      return refresh()
    }
    const ticket = { generation, promise: null }
    pending = ticket
    ticket.promise = (async () => {
      try {
        const result = await load()
        if (ticket.generation === generation) apply(result)
        return result
      } finally {
        if (pending === ticket) pending = null
      }
    })()
    return ticket.promise
  }
  return { refresh, invalidate() { generation++ } }
}
