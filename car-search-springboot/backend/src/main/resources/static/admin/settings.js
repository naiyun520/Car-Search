loaders.settings = async () => {
  const [settings, payment, email] = await Promise.all([api('/settings'), api('/payment-settings'), api('/email-settings')])
  const configured = value => `<span class="tag ${value ? 'success' : 'fail'}">${value ? '已配置' : '未配置'}</span>`
  const messageCallbackUrl = `${location.origin}${payment.message_callback_path || '/wechat/message'}`
  $('#content').innerHTML = `
    <div class="panel">
      <div class="panel-head"><div><h3>运营与隐私设置</h3><p class="hint">这些内容可以由运营人员直接修改，保存后立即生效。</p></div></div>
      <form id="general-settings" class="form">
        <div class="form-grid">
          <label>运营主体全称<input name="operator_name" value="${escapeHtml(settings.operator_name || '')}" placeholder="须与小程序备案/认证主体一致" required></label>
          <label>个人信息保护联系人<input name="privacy_contact" value="${escapeHtml(settings.privacy_contact || '')}" placeholder="邮箱或联系电话" required></label>
          <label>隐私规则生效日期<input name="privacy_effective_date" type="date" value="${escapeHtml(settings.privacy_effective_date || '')}" required></label>
          <label>客服电话<input name="customer_service_phone" value="${escapeHtml(settings.customer_service_phone || '')}"></label>
          <label>客服时间<input name="customer_service_hours" value="${escapeHtml(settings.customer_service_hours || '')}"></label>
          <label>查询数据保留天数<input name="privacy_retention_days" type="number" min="1" max="365" value="${escapeHtml(settings.privacy_retention_days || 30)}"><small class="field-note">用于履约、异常排查和售后；到期由每日清理任务删除</small></label>
          <label>后台访问地址<input name="admin_path" value="${escapeHtml(settings.admin_path || 'admin')}" pattern="[a-z][a-z0-9-]{2,31}" maxlength="32" required><small class="field-note">默认 admin；保存后仅可通过 /新地址/ 进入，根域名不会跳转后台</small></label>
        </div>
        <label>免责声明<textarea name="disclaimer">${escapeHtml(settings.disclaimer || '')}</textarea></label>
        <button class="primary" type="submit">保存运营设置</button>
      </form>
    </div>
    <div class="panel">
      <div class="panel-head"><div><h3>微信与虚拟支付</h3><p class="hint">敏感凭证使用 AES-256-GCM 加密保存。后台不读取明文；输入框留空表示保持原值。</p></div></div>
      <form id="payment-settings" class="form">
        <div class="form-grid">
          <label>微信小程序 AppID<input name="wechat_app_id" value="${escapeHtml(payment.wechat_app_id || '')}" placeholder="wx开头的18位AppID"></label>
          <label>微信 AppSecret ${configured(payment.app_secret_configured)}<input name="wechat_app_secret" type="password" autocomplete="new-password" placeholder="留空保持原值"></label>
          <label>OfferID<input name="wechat_offer_id" value="${escapeHtml(payment.wechat_offer_id || '')}" placeholder="虚拟支付基础配置中复制"><small class="field-note">请勿填写商户号或AppID；OfferID 正式与沙箱环境共用同一个，AppKey 才分两套</small></label>
          <label>沙箱 AppKey ${configured(payment.sandbox_app_key_configured)}<input name="wechat_sandbox_app_key" type="password" autocomplete="new-password" placeholder="留空保持原值"></label>
          <label>正式 AppKey ${configured(payment.production_app_key_configured)}<input name="wechat_production_app_key" type="password" autocomplete="new-password" placeholder="留空保持原值"></label>
          <label>支付环境<select name="wechat_pay_env"><option value="0" ${Number(payment.wechat_pay_env) === 0 ? 'selected' : ''}>正式环境</option><option value="1" ${Number(payment.wechat_pay_env) === 1 ? 'selected' : ''}>沙箱环境</option></select></label>
          <label>支付通道<select name="payment_enabled"><option value="0" ${payment.payment_enabled !== '1' ? 'selected' : ''}>关闭/维护中</option><option value="1" ${payment.payment_enabled === '1' ? 'selected' : ''}>启用</option></select></label>
          <label>消息推送 Token ${configured(payment.message_token_configured)}<input name="wechat_message_token" type="password" autocomplete="new-password" placeholder="留空保持原值"><small class="field-note">与微信公众平台消息推送配置中的 Token 完全一致</small></label>
          <label>消息推送 EncodingAESKey ${configured(payment.message_aes_key_configured)}<input name="wechat_message_aes_key" type="password" autocomplete="new-password" placeholder="留空保持原值"><small class="field-note">使用公众平台随机生成的43位密钥</small></label>
        </div>
        <div class="hint">AppSecret 来自“小程序后台 - 开发管理 - 开发设置”，用于登录和官方订单查询。OfferID 来自“虚拟支付 - 基础配置”，正式与沙箱环境共用同一个；沙箱 AppKey、正式 AppKey 分别在基础配置的“沙箱环境”和“现网环境”下复制。系统按所选支付环境（env 参数）自动使用对应环境的 AppKey 计算支付签名。真机调试使用沙箱环境时，微信后台的道具也必须在沙箱环境发布。</div>
        <div class="hint"><b>退款通知必须配置：</b>在微信公众平台“开发管理 - 开发设置 - 消息推送”填写服务器地址 <code>${escapeHtml(messageCallbackUrl)}</code>，数据格式选择 <b>JSON</b>，消息加解密方式选择 <b>安全模式</b>，并把同一组 Token、EncodingAESKey 填到上方。该入口用于 Apple 退款问询及最终退款通知；未配置时 iOS 退款无法自动同步。</div>
        <div class="form-section"><h4>iOS 退款问询自动回复</h4><p class="hint">Apple 审核退款时系统必须立即答复，无法等待人工操作。这里修改后对新问询生效；已答复事件重推时仍返回首次答案。可用变量：<code>{order_no}</code>、<code>{delivered_at}</code>、<code>{refund_reason}</code>、<code>{decision_reason}</code>。</p><div class="form-grid"><label>已完整履约：结论<textarea name="ios_refund_reject_result_info" maxlength="200" required>${escapeHtml(payment.ios_refund_reject_result_info||'')}</textarea></label><label>已完整履约：证据<textarea name="ios_refund_reject_evidence" maxlength="1000" required>${escapeHtml(payment.ios_refund_reject_evidence||'')}</textarea></label><label>未确认完整履约：结论<textarea name="ios_refund_approve_result_info" maxlength="200" required>${escapeHtml(payment.ios_refund_approve_result_info||'')}</textarea></label><label>未确认完整履约：证据<textarea name="ios_refund_approve_evidence" maxlength="1000" required>${escapeHtml(payment.ios_refund_approve_evidence||'')}</textarea></label></div></div>
        <button class="primary" type="submit">安全保存支付配置</button>
      </form>
    </div>
    <div class="panel">
      <div class="panel-head"><div><h3>安卓未查询到信息自动退款</h3><p class="hint">仅处理开启后发生的安卓已付款订单；iOS 订单仍由用户通过 Apple 官方渠道申请退款。</p></div></div>
      <form id="android-auto-refund-settings" class="form">
        <div class="form-grid">
          <label>自动退款开关<select name="enabled"><option value="0" ${payment.android_auto_refund_query_failed !== '1' ? 'selected' : ''}>关闭</option><option value="1" ${payment.android_auto_refund_query_failed === '1' ? 'selected' : ''}>开启</option></select><small class="field-note">开启后，安卓订单未查询到信息将自动向微信发起原路退款；退款结果以微信异步处理状态为准。</small></label>
        </div>
        <button class="primary" type="submit">保存自动退款设置</button>
      </form>
    </div>
    <div class="panel">
      <div class="panel-head"><div><h3>邮件通知</h3><p class="hint">配置 SMTP 发信服务，开启后系统将在关键事件发生时自动发送邮件通知。</p></div></div>
      <form id="email-settings" class="form">
        <div class="form-grid">
          <label>SMTP 服务器<input name="smtp_host" value="${escapeHtml(email.smtp_host || '')}" placeholder="smtp.example.com"></label>
          <label>端口<input name="smtp_port" type="number" min="1" max="65535" value="${escapeHtml(email.smtp_port || '465')}"></label>
          <label>加密方式<select name="smtp_encryption"><option value="ssl" ${email.smtp_encryption==='ssl'?'selected':''}>SSL</option><option value="tls" ${email.smtp_encryption==='tls'?'selected':''}>STARTTLS</option><option value="none" ${email.smtp_encryption==='none'?'selected':''}>无加密</option></select></label>
          <label>用户名<input name="smtp_username" value="${escapeHtml(email.smtp_username || '')}" placeholder="登录邮箱或用户名"></label>
          <label>密码 ${configured(email.smtp_password_configured)}<input name="smtp_password" type="password" autocomplete="new-password" placeholder="留空保持原值"></label>
          <label>发件人地址<input name="smtp_from_address" type="email" value="${escapeHtml(email.smtp_from_address || '')}" placeholder="noreply@example.com"></label>
          <label>发件人名称<input name="smtp_from_name" value="${escapeHtml(email.smtp_from_name || '')}" placeholder="车辆查询系统"></label>
        </div>
        <div class="form-grid">
          <label>邮件通知总开关<select name="email_notify_enabled"><option value="0" ${email.email_notify_enabled!=='1'?'selected':''}>关闭</option><option value="1" ${email.email_notify_enabled==='1'?'selected':''}>开启</option></select><small class="field-note">一键关闭后不再产生或发送业务通知；测试邮件仍可使用</small></label>
        </div>
        <div class="form-section"><h4>通知事件</h4><p class="hint">勾选需要接收的事件。支付、查询、退款邮件都会标明 iOS 或安卓用户。</p>
          <div class="email-event-list">
            <label class="email-event"><input type="checkbox" name="email_event_payment_success" value="1" ${email.email_event_payment_success==='1'?'checked':''}><span><b>支付成功</b><small>微信服务端核验支付成功后通知</small></span></label>
            <label class="email-event"><input type="checkbox" name="email_event_query_success" value="1" ${email.email_event_query_success==='1'?'checked':''}><span><b>查询成功</b><small>供应商结果保存成功后通知</small></span></label>
            <label class="email-event"><input type="checkbox" name="email_event_query_failed" value="1" ${email.email_event_query_failed==='1'?'checked':''}><span><b>查询失败</b><small>包含供应商业务码、请求 ID 和具体失败原因</small></span></label>
            <label class="email-event"><input type="checkbox" name="email_event_refund_result" value="1" ${email.email_event_refund_result==='1'?'checked':''}><span><b>退款结果</b><small>仅最终成功或失败时通知，包含退款金额</small></span></label>
            <label class="email-event"><input type="checkbox" name="email_event_provider_balance" value="1" ${email.email_event_provider_balance==='1'?'checked':''}><span><b>接口余额</b><small>仅 missing_code 且失败原因为供应商余额不足时告警</small></span></label>
          </div>
        </div>
        <label>收件人邮箱<textarea name="email_recipients" placeholder="每行一个邮箱，或用逗号分隔">${escapeHtml(email.email_recipients || '')}</textarea></label>
        <div class="form-actions-row">
          <button class="primary" type="submit">保存邮件配置</button>
          <button type="button" id="email-test-btn" class="secondary">测试发送</button>
        </div>
      </form>
    </div>
    <div class="panel">
      <div class="panel-head"><div><h3>管理员安全</h3><p class="hint">修改密码后所有管理端登录会立即失效，需要使用新密码重新登录。</p></div></div>
      <form id="password-form" class="form">
        <div class="form-grid"><label>当前密码<input name="old_password" type="password" required></label><label>新密码<input name="new_password" type="password" minlength="12" maxlength="128" required placeholder="12-128位，至少三类字符"></label></div>
        <button class="primary" type="submit">修改管理员密码</button>
      </form>
    </div>
    <div class="panel">
      <div class="panel-head"><h3>基础设施配置</h3></div>
      <p class="hint">数据库地址、数据库账号和主加密密钥由安装向导写入服务器 <code>.env</code>。它们决定系统能否启动和历史密文能否解密，因此管理后台不会读取、展示或在线修改。需要迁移服务器时，请由服务器管理员安全迁移该文件。</p>
    </div>`

  $('#general-settings').onsubmit = async event => {
    event.preventDefault()
    const values=Object.fromEntries(new FormData(event.target))
    const oldPath=String(settings.admin_path||'admin')
    const result=await api('/settings', { method: 'POST', data: { settings: values } })
    toast('运营设置已保存')
    if(result?.admin_path&&result.admin_path!==oldPath){localStorage.removeItem('admin_token');setTimeout(()=>location.assign(`/${encodeURIComponent(result.admin_path)}/`),600)}
  }
  $('#payment-settings').onsubmit = async event => {
    event.preventDefault()
    const button=event.submitter||event.target.querySelector('button[type="submit"]')
    const original=button.textContent
    button.disabled=true
    button.textContent='正在保存...'
    try{
      await api('/payment-settings', { method: 'POST', data: Object.fromEntries(new FormData(event.target)) })
      toast('支付配置已安全保存')
      await loaders.settings()
    }catch(error){
      toast(error.message||'支付配置保存失败')
      button.disabled=false
      button.textContent=original
    }
  }
  $('#android-auto-refund-settings').onsubmit = async event => {
    event.preventDefault()
    const values=Object.fromEntries(new FormData(event.target))
    await api('/android-auto-refund-settings', { method: 'POST', data: values })
    toast(values.enabled==='1'?'安卓自动退款已开启':'安卓自动退款已关闭')
    await loaders.settings()
  }
  $('#email-settings').onsubmit = async event => {
    event.preventDefault()
    const button=event.submitter||event.target.querySelector('button[type="submit"]')
    const original=button.textContent
    button.disabled=true
    button.textContent='正在保存...'
    try{
      await api('/email-settings', { method: 'POST', data: Object.fromEntries(new FormData(event.target)) })
      toast('邮件配置已保存')
      await loaders.settings()
    }catch(error){
      toast(error.message||'邮件配置保存失败')
      button.disabled=false
      button.textContent=original
    }
  }
  $('#email-test-btn').onclick = async () => {
    const btn=$('#email-test-btn')
    btn.disabled=true
    btn.textContent='正在测试...'
    try{
      await api('/email-test',{method:'POST'})
      toast('测试邮件已发送，请检查收件箱')
    }catch(error){
      toast(error.message||'测试发送失败')
    }finally{
      btn.disabled=false
      btn.textContent='测试发送'
    }
  }
  $('#password-form').onsubmit = async event => {
    event.preventDefault()
    await api('/password', { method: 'POST', data: Object.fromEntries(new FormData(event.target)) })
    alert('密码已修改，请重新登录')
    logout()
  }
}
