// 分页、返回刷新与分类切换共用请求代次，过期响应不能混入当前分类。
export function createOrderPager(fetchPage, state, pageSize = 10) {
  Object.assign(state, { orders: [], group: 'all', page: 0, total: 0, loading: false, finished: false, error: '' })
  let generation = 0
  let retryRefresh = true
  function unique(rows) {
    return [...new Map(rows.map(row => [row.order_no, row])).values()]
  }
  async function read(refresh) {
    if (state.loading || (!refresh && state.finished)) return
    const ticket = ++generation
    const group = state.group
    const lastPage = refresh ? Math.max(1, state.page) : state.page + 1
    state.loading = true
    state.error = ''
    try {
      let rows = refresh ? [] : [...state.orders]
      let total = 0, page = lastPage, count = 0
      for (let index = refresh ? 1 : lastPage; index <= lastPage; index++) {
        const response = await fetchPage({ group, page: index, page_size: pageSize })
        if (ticket !== generation) return
        const batch = Array.isArray(response.data) ? response.data : []
        total = Number(response.total) || 0
        rows = unique([...rows, ...batch])
        page = index
        count = batch.length
        if (batch.length < pageSize || index * pageSize >= total) break
      }
      if (ticket !== generation) return
      state.orders = rows
      state.page = page
      state.total = total
      state.finished = count < pageSize || page * pageSize >= total
    } catch (error) {
      if (ticket === generation) {
        retryRefresh = refresh
        state.error = error.message || '订单加载失败，请点击重试'
      }
    } finally {
      if (ticket === generation) state.loading = false
    }
  }
  return {
    more: () => read(false),
    retry: () => read(retryRefresh),
    refresh: () => read(true),
    reset(group) {
      generation++
      Object.assign(state, { group, orders: [], page: 0, total: 0, loading: false, finished: false, error: '' })
      return read(true)
    }
  }
}
