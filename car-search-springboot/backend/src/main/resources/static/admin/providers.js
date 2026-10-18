const authTypes = [['none', '无签名'], ['sign_md5', 'MD5 签名（汽修宝）']]
let providerRows = []
let providerEncryptionHealth = null

loaders.providers = async () => {
  ;[providerRows, providerEncryptionHealth] = await Promise.all([api('/providers'), api('/providers/encryption-health')])
  renderProviderList()
}

function renderProviderList() {
  const authLabel = value => (authTypes.find(t => t[0] === value) || ['未知', value])[1]
  const statusLabel = value => Number(value) === 1 ? '<span class="tag success">启用</span>' : '<span class="tag fail">禁用</span>'
  $('#content').innerHTML = `
    <div class="panel">
      <div class="panel-head"><div><h3>供应商管理</h3><p class="hint">管理第三方数据供应商的认证与连接配置。敏感凭证使用 AES-256-GCM 加密保存。</p></div><button class="primary compact" id="add-provider">新增供应商</button></div>
      ${providerEncryptionHealth && !providerEncryptionHealth.healthy ? `<div class="security-warning"><b>主密钥诊断未通过</b><span>当前密钥指纹 ${escapeHtml(providerEncryptionHealth.key_fingerprint)}，${providerEncryptionHealth.encrypted_count} 条密文中仅 ${providerEncryptionHealth.decryptable_count} 条可解密。</span></div>` : ''}
      <div class="table-wrap"><table><thead><tr><th>排序</th><th>名称</th><th>编码</th><th>认证方式</th><th>基础 URL</th><th>HTTP</th><th>AppID</th><th>Secret</th><th>状态</th><th>操作</th></tr></thead><tbody>
        ${providerRows.map((row, index) => {
          const url = escapeHtml(row.base_url || '-')
          return `<tr>
            <td><button class="action provider-sort" data-id="${row.id}" data-move="-1" ${index === 0 ? 'disabled' : ''}>↑</button><button class="action provider-sort" data-id="${row.id}" data-move="1" ${index === providerRows.length - 1 ? 'disabled' : ''}>↓</button></td>
            <td><b>${escapeHtml(row.name)}</b></td>
            <td><code>${escapeHtml(row.code)}</code></td>
            <td>${escapeHtml(authLabel(row.auth_type))}</td>
            <td class="endpoint-cell">${url}</td>
            <td>${row.allow_http ? '<span class="tag wait">允许</span>' : '<span class="tag">仅HTTPS</span>'}</td>
            <td><span class="tag ${row.appid_configured ? 'success' : 'fail'}">${row.appid_configured ? '已配置' : '未配置'}</span></td>
            <td><span class="tag ${row.secret_configured ? 'success' : 'fail'}">${row.secret_configured ? '已配置' : '未配置'}</span></td>
            <td>${statusLabel(row.status)}</td>
            <td><button class="action edit-provider" data-id="${row.id}">编辑</button><button class="action danger delete-provider" data-id="${row.id}">删除</button></td>
          </tr>`
        }).join('') || '<tr><td colspan="10" class="empty">暂无供应商</td></tr>'}
      </tbody></table></div>
    </div>
    <div id="provider-editor"></div>`

  $('#add-provider').onclick = () => openProviderEditor(null)
  $('#content').onclick = async event => {
    const sortBtn = event.target.closest('.provider-sort')
    if (sortBtn) return moveProvider(sortBtn)
    const edit = event.target.closest('.edit-provider')
    if (edit) {
      const provider = providerRows.find(row => String(row.id) === String(edit.dataset.id))
      if (!provider) return toast('供应商数据已失效，请刷新页面')
      return openProviderEditor(provider)
    }
    const remove = event.target.closest('.delete-provider')
    if (!remove || !confirm('确定删除该供应商？有关联服务时系统会拒绝删除。')) return
    await api(`/providers/${remove.dataset.id}`, { method: 'DELETE' })
    toast('供应商已删除')
    await loaders.providers()
  }
}

async function moveProvider(button) {
  const row = button.closest('tr')
  const index = [...row.parentElement.children].indexOf(row)
  const target = index + Number(button.dataset.move)
  if (target < 0 || target >= providerRows.length) return
  ;[providerRows[index], providerRows[target]] = [providerRows[target], providerRows[index]]
  renderProviderList()
  try {
    await api('/providers/sort', { method: 'POST', data: { ids: providerRows.map(row => row.id) } })
    toast('排序已保存')
  } catch (error) {
    toast(error.message || '排序保存失败')
    providerRows = await api('/providers')
    renderProviderList()
  }
}

function openProviderEditor(provider) {
  const item = provider || { name: '', code: '', auth_type: 'sign_md5', base_url: '', allow_http: 0, status: 1 }
  const isEdit = !!provider
  const configuredBadge = (flag) => flag ? '<span class="tag success">已配置</span>' : '<span class="tag fail">未配置</span>'

  $('#provider-editor').innerHTML = `
    <div class="dialog-mask">
      <div class="service-drawer">
        <div class="drawer-head"><div><h3>${isEdit ? '编辑供应商' : '新增供应商'}</h3><p>配置供应商的认证信息和连接地址</p></div><button class="drawer-close" type="button">×</button></div>
        <form id="provider-form" class="form">
          <div class="form-section"><h4>基本信息</h4><div class="form-grid three">
            <label>供应商编码<input name="code" value="${escapeHtml(item.code)}" placeholder="例如 qxb" ${isEdit ? 'readonly' : ''} required>${isEdit ? '<small class="field-note">编码创建后不可修改</small>' : ''}</label>
            <label>供应商名称<input name="name" value="${escapeHtml(item.name)}" placeholder="例如 汽修宝" required></label>
            <label>认证方式<select name="auth_type">${authTypes.map(t => `<option value="${t[0]}" ${item.auth_type === t[0] ? 'selected' : ''}>${t[1]}</option>`).join('')}</select></label>
            <label>基础 URL<input name="base_url" value="${escapeHtml(item.base_url)}" placeholder="例如 http://openapi.qxbapp.com/open" required></label>
            <label>允许 HTTP<select name="allow_http"><option value="0" ${!item.allow_http ? 'selected' : ''}>否（仅 HTTPS）</option><option value="1" ${item.allow_http ? 'selected' : ''}>是（允许 HTTP）</option></select><small class="field-note">供应商接口为 HTTP 时需要开启</small></label>
            <label>状态<select name="status"><option value="1" ${Number(item.status) === 1 ? 'selected' : ''}>启用</option><option value="0" ${Number(item.status) === 0 ? 'selected' : ''}>禁用</option></select></label>
          </div></div>
          <div class="form-section"><h4>签名凭证</h4><p class="hint">敏感信息使用 AES-256-GCM 加密保存。输入框留空表示保持原值。</p>
            <div class="form-grid three">
              <label>AppID ${isEdit ? configuredBadge(item.appid_configured) : ''}<input name="appid" type="password" autocomplete="new-password" placeholder="留空保持原值"></label>
              <label>Secret ${isEdit ? configuredBadge(item.secret_configured) : ''}<input name="secret" type="password" autocomplete="new-password" placeholder="留空保持原值"></label>
              <label>版本号<input name="version" value="${escapeHtml(item.version || '1.0.0')}" placeholder="1.0.0"></label>
            </div>
          </div>
          <div id="provider-form-error" class="form-error"></div>
          <div class="drawer-actions"><button type="button" class="secondary drawer-cancel">取消</button><button type="submit" class="primary">保存供应商</button></div>
        </form>
      </div>
    </div>`

  const close = () => { $('#provider-editor').innerHTML = '' }
  $('.drawer-close').onclick = close
  $('.drawer-cancel').onclick = close
  $('#provider-form').onsubmit = async event => {
    event.preventDefault()
    const button = event.currentTarget.querySelector('button[type="submit"]')
    const errorNode = $('#provider-form-error')
    errorNode.textContent = ''
    button.disabled = true
    button.textContent = '正在保存...'
    try {
      const data = Object.fromEntries(new FormData(event.target))
      data.status = Number(data.status)
      data.allow_http = Number(data.allow_http)
      if (isEdit) data.provider_id = Number(provider.id)
      await api(isEdit ? `/providers/${provider.id}/update` : '/providers', { method: 'POST', data })
      toast(isEdit ? '供应商已保存' : '供应商已创建')
      close()
      await loaders.providers()
    } catch (error) {
      errorNode.textContent = error.message || '保存失败'
    } finally {
      button.disabled = false
      button.textContent = '保存供应商'
    }
  }
}