const API='/admin-api'
let token=localStorage.getItem('admin_token')||''
let currentPage='dashboard'
let pageRequestId=0
const pages={dashboard:['数据概览','实时掌握小程序运营情况'],orders:['订单管理','搜索、核验并处理每一笔订单'],users:['用户管理','按用户ID、手机号或昵称查询用户'],services:['接口与价格','动态管理接口、字段、价格与运行状态'],announcements:['弹窗公告','配置公告内容和不再弹出的时长'],feedback:['投诉意见','查看反馈详情并向用户回复处理结果'],settings:['系统与支付','管理客服、隐私和支付开关']}
const $=selector=>document.querySelector(selector)
const escapeHtml=value=>String(value??'').replace(/[&<>"]/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[char]))

async function api(path,options={}){const response=await fetch(API+path,{method:options.method||'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:options.data?JSON.stringify(options.data):undefined});const body=await response.json().catch(()=>({}));if(response.status===401&&path!='/login'){logout();throw new Error('登录已失效')}if(!response.ok||body.code!==0)throw new Error(body.message||'操作失败');return body.data}
function toast(message){const node=$('#toast');node.textContent=message;node.classList.add('show');setTimeout(()=>node.classList.remove('show'),2200)}
function status(value){const map={pending_payment:['待付款','wait'],payment_review:['待核对','wait'],paid:['待查询','wait'],querying:['查询中','wait'],success:['查询成功','success'],query_failed:['查询异常','fail'],refunding:['退款处理中','wait'],cancelled:['已取消',''],refunded:['已退款','fail']};const item=map[value]||['-',''];return `<span class="tag ${item[1]}">${item[0]}</span>`}
function showApp(){if(!token)return;$('#login').classList.add('hidden');$('#app').classList.remove('hidden');$('#admin-name').textContent=localStorage.getItem('admin_name')||'admin';if(localStorage.getItem('admin_must_change')==='1')showForcedPassword();else loadPage()}
function logout(){token='';localStorage.removeItem('admin_token');localStorage.removeItem('admin_name');localStorage.removeItem('admin_must_change');$('#app').classList.add('hidden');$('#login').classList.remove('hidden')}

$('#login-form').addEventListener('submit',async event=>{event.preventDefault();const data=Object.fromEntries(new FormData(event.target));try{const result=await api('/login',{method:'POST',data});token=result.token;localStorage.setItem('admin_token',token);localStorage.setItem('admin_name',result.admin.username);localStorage.setItem('admin_must_change',result.admin.must_change_password?'1':'0');$('#login-error').textContent='';showApp()}catch(error){$('#login-error').textContent=error.message}})
$('#logout').onclick=logout
const openSidebar=()=>document.body.classList.add('sidebar-open')
const closeSidebar=()=>document.body.classList.remove('sidebar-open')
$('#menu-toggle').onclick=openSidebar
$('#menu-close').onclick=closeSidebar
const sidebarMask=$('#sidebar-mask')
if(sidebarMask)sidebarMask.onclick=closeSidebar
$('#nav').onclick=event=>{if(localStorage.getItem('admin_must_change')==='1'){showForcedPassword();return}const button=event.target.closest('[data-page]');if(!button)return;document.querySelectorAll('#nav button').forEach(item=>item.classList.remove('active'));button.classList.add('active');currentPage=button.dataset.page;closeSidebar();loadPage()}
async function loadPage(){const page=currentPage;const requestId=++pageRequestId;if(window.disposeCharts)window.disposeCharts();const [title,desc]=pages[page];$('#page-title').textContent=title;$('#page-desc').textContent=desc;$('#content').innerHTML='<div class="page-loading"><span></span><p>正在加载...</p></div>';try{await loaders[page]()}catch(error){if(requestId!==pageRequestId||!token)return;if(error?.passwordRequired||localStorage.getItem('admin_must_change')==='1'){showForcedPassword();return}if(!token||error.message==='登录已失效')return logout();$('#content').innerHTML=`<div class="load-error"><div class="load-error-icon">!</div><h3>页面加载失败</h3><p>${escapeHtml(error.message||'后台服务暂时不可用')}</p><button id="retry-page" type="button">重新加载</button></div>`;$('#retry-page').onclick=loadPage}}

function showForcedPassword(){
  $('#page-title').textContent='首次登录安全设置';$('#page-desc').textContent='修改默认密码后方可使用管理平台'
  $('#content').innerHTML=`<div class="panel"><div class="panel-head"><div><h3>必须修改默认密码</h3><p class="hint">新密码需为12至128位，并至少包含大写字母、小写字母、数字、特殊字符中的三类。</p></div></div><form id="forced-password-form" class="form"><div class="form-grid"><label>当前密码<input name="old_password" type="password" autocomplete="current-password" required></label><label>新密码<input name="new_password" type="password" autocomplete="new-password" minlength="12" maxlength="128" required></label><label>确认新密码<input name="confirm_password" type="password" autocomplete="new-password" minlength="12" maxlength="128" required></label></div><div id="forced-password-error" class="form-error"></div><button class="primary" type="submit">修改密码并重新登录</button></form></div>`
  $('#forced-password-form').onsubmit=async event=>{event.preventDefault();const values=Object.fromEntries(new FormData(event.target));const errorNode=$('#forced-password-error');errorNode.textContent='';if(values.new_password!==values.confirm_password){errorNode.textContent='两次输入的新密码不一致';return}delete values.confirm_password;const button=event.submitter;button.disabled=true;try{await api('/password',{method:'POST',data:values});alert('密码已修改，请使用新密码重新登录');logout()}catch(error){errorNode.textContent=error.message||'密码修改失败';button.disabled=false}}
}

const moduleUnavailable=async()=>{throw new Error('页面模块加载失败，请强制刷新后重试')}
const orderState={keyword:'',status:'',page:1,selected:new Set()}
const userState={keyword:'',page:1}
const loaders={
  dashboard:moduleUnavailable,services:moduleUnavailable,feedback:moduleUnavailable,settings:moduleUnavailable,
  orders:()=>renderOrders(),
  users:()=>renderUsers(),
  announcements:async()=>{const rows=await api('/announcements');const item=rows[0]||{};$('#content').innerHTML=`<div class="panel"><div class="panel-head"><h3>小程序弹窗公告</h3></div><form id="announcement-form" class="form"><input type="hidden" name="id" value="${item.id||''}"><label>公告标题<input name="title" value="${escapeHtml(item.title||'服务公告')}" required></label><label>公告内容<textarea name="content" required>${escapeHtml(item.content||'')}</textarea></label><div class="form-grid"><label>勾选后不再弹出时长（小时）<input name="suppress_hours" type="number" min="1" max="720" value="${item.suppress_hours||24}"></label><label>状态<select name="status"><option value="1" ${item.status!==0?'selected':''}>启用</option><option value="0" ${item.status===0?'selected':''}>停用</option></select></label></div><button class="primary" type="submit">保存公告</button></form></div>`;$('#announcement-form').onsubmit=async event=>{event.preventDefault();await api('/announcements',{method:'POST',data:Object.fromEntries(new FormData(event.target))});toast('公告已保存');loadPage()}}
}

async function renderOrders(){
  const query=new URLSearchParams({keyword:orderState.keyword,status:orderState.status,page:String(orderState.page)})
  const data=await api(`/orders?${query}`)
  $('#content').innerHTML=`<div class="panel"><div class="panel-head"><h3>订单列表 <small>共 ${data.total||0} 笔</small></h3><div class="filter"><button type="button" id="order-delete-batch" class="danger compact">删除选中（${orderState.selected.size}）</button><form id="order-search" class="filter search-filter"><input name="keyword" value="${escapeHtml(orderState.keyword)}" placeholder="订单号 / 用户ID / 昵称 / 手机号"><select name="status"><option value="">全部状态</option>${[['pending_payment','待付款'],['payment_review','待核对'],['paid','待查询'],['querying','查询中'],['success','成功'],['query_failed','异常'],['refunding','退款处理中'],['refunded','已退款']].map(item=>`<option value="${item[0]}" ${orderState.status===item[0]?'selected':''}>${item[1]}</option>`).join('')}</select><button type="submit" class="primary compact">搜索</button><button type="button" id="order-reset" class="secondary compact">重置</button></form></div></div><div class="table-wrap"><table><thead><tr><th><input type="checkbox" id="order-check-all" title="全选本页"></th><th>订单号</th><th>用户ID</th><th>用户</th><th>服务</th><th>金额</th><th>支付终端</th><th>状态</th><th>微信履约</th><th>创建时间</th><th>操作</th></tr></thead><tbody>${orderRows(data.data||[])}</tbody></table></div>${pagination('order',data)}</div>`
  $('#order-search').onsubmit=event=>{event.preventDefault();const values=Object.fromEntries(new FormData(event.target));orderState.keyword=values.keyword.trim();orderState.status=values.status;orderState.page=1;renderOrders().catch(showPageError)}
  $('#order-reset').onclick=()=>{orderState.keyword='';orderState.status='';orderState.page=1;renderOrders().catch(showPageError)}
  bindPagination('order',data,renderOrders,orderState)
  $('#content').onclick=handleOrderAction
  $('#content').onchange=event=>{
    const box=event.target.closest('.order-check')
    if(!box)return
    if(box.checked)orderState.selected.add(box.value);else orderState.selected.delete(box.value)
    refreshOrderSelection()
  }
  $('#order-check-all').onchange=event=>{document.querySelectorAll('.order-check').forEach(node=>{node.checked=event.target.checked;if(node.checked)orderState.selected.add(node.value);else orderState.selected.delete(node.value)});refreshOrderSelection()}
  $('#order-delete-batch').onclick=async()=>{
    const count=orderState.selected.size
    if(!count)return toast('请先勾选要删除的订单')
    if(!confirm(`确定删除选中的 ${count} 笔订单？\n删除仅作用于本后台数据库，不影响微信侧已完成的支付与退款记录。`))return
    try{
      const result=await api('/orders/batch-delete',{method:'POST',data:{order_nos:[...orderState.selected]}})
      toast(result.message||'订单已删除')
    }catch(error){toast(error.message||'删除失败')}
    orderState.selected.clear()
    await renderOrders()
  }
  refreshOrderSelection()
}
function refreshOrderSelection(){
  const boxes=document.querySelectorAll('.order-check')
  const all=document.querySelector('#order-check-all')
  if(all)all.checked=boxes.length>0&&boxes.length===document.querySelectorAll('.order-check:checked').length
  const batch=document.querySelector('#order-delete-batch')
  if(batch)batch.textContent=`删除选中（${orderState.selected.size}）`
}

async function handleOrderAction(event){
  const close=event.target.closest('.dialog-close');if(close){close.closest('.dialog-mask').remove();return}
  const button=event.target.closest('.order-detail,.sync-payment,.retry,.delivery-retry,.refund,.refund-check,.manual-refund');if(!button)return
  if(button.classList.contains('order-detail')){try{const row=await api(`/order-detail/${encodeURIComponent(button.dataset.id)}`);if(!row||!row.order_no)throw new Error('订单详情响应格式异常');showOrderDetail(row)}catch(error){toast(error.message)}return}
  button.disabled=true
  try{
    if(button.classList.contains('sync-payment')){const result=await api(`/orders/${button.dataset.id}/sync-payment`,{method:'POST'});toast(result.status==='success'?'支付已核验并完成查询':result.status==='paid'?'支付状态已同步':'微信暂未确认支付')}
    else if(button.classList.contains('retry')){if(!confirm('请先核对详情中的失败原因并修正供应商配置。\n\n继续将使用当前服务配置再发起一次供应商查询，可能产生接口费用。确认继续？'))return;const result=await api(`/orders/${button.dataset.id}/retry`,{method:'POST'});toast(result.status==='success'?'查询已完成':'订单已恢复为待查询')}
    else if(button.classList.contains('delivery-retry')){await api(`/orders/${button.dataset.id}/delivery`,{method:'POST'});toast('微信发货已确认')}
    else if(button.classList.contains('refund')){if(!confirm('确认向用户发起全额退款？提交后将由微信处理。'))return;const reason=button.closest('.order-actions').querySelector('.refund-reason').value;await api(`/orders/${button.dataset.id}/refund`,{method:'POST',data:{reason}});toast('退款申请已提交')}
    else if(button.classList.contains('manual-refund')){const reason=window.prompt('请输入手动退款原因（可选）：','');if(reason===null)return;await api(`/orders/${button.dataset.id}/manual-refund`,{method:'POST',data:{reason:reason.trim()}});toast('手动退款已登记')}
    else{const result=await api(`/orders/${button.dataset.id}/refund-status`,{method:'POST'});toast(result.status==='refunded'?'退款已完成':result.status==='failed'?'退款失败，可重新申请':'退款仍在处理中')}
    await renderOrders()
  }catch(error){toast(error.message||'操作失败')}finally{button.disabled=false}
}

function orderRows(rows){return rows.map(row=>{const delivery=row.delivery_status==='delivered'?'<span class="tag success">已确认发货</span>':row.status==='success'&&row.delivery_status==='failed'?`<span class="tag fail" title="${escapeHtml(row.delivery_last_error||'')}">发货异常</span>`:row.status==='success'?'<span class="tag wait">待确认发货</span>':'-';const platform=row.payment_platform==='ios'?'<span class="tag wait">iOS（Apple）</span>':row.payment_platform==='wechat'?'<span class="tag success">安卓/鸿蒙/Windows</span>':'<span class="tag">待识别</span>';const refunding=row.status==='refunding'||row.refund_status==='processing';const refundable=['paid','query_failed','success'].includes(row.status)&&!['processing','refunded'].includes(row.refund_status);const actions=[`<button class="action order-detail" data-id="${row.order_no}">查看详情</button>`];if(['pending_payment','payment_review'].includes(row.status))actions.push(`<button class="action sync-payment" data-id="${row.order_no}">${row.status==='payment_review'?'重新核验支付':'同步支付'}</button>`);if(row.status==='query_failed')actions.push(`<button class="action retry" data-id="${row.order_no}">人工重试</button>`);if(row.status==='success'&&row.delivery_status!=='delivered')actions.push(`<button class="action delivery-retry" data-id="${row.order_no}">重试微信发货</button>`);if(refundable&&row.payment_platform==='ios')actions.push(`<button class="action manual-refund" data-id="${row.order_no}">手动退款</button>`);else if(refundable)actions.push(`<select class="refund-reason"><option value="2">售后问题</option><option value="1">产品问题</option><option value="3">用户主动退款</option><option value="4">价格问题</option><option value="5">其他原因</option><option value="0">暂无描述</option></select><button class="action refund" data-id="${row.order_no}">全额退款</button>`);if(refunding)actions.push(`<button class="action refund-check" data-id="${row.order_no}">核验退款</button>`);return `<tr><td><input type="checkbox" class="order-check" value="${row.order_no}" ${orderState.selected.has(row.order_no)?'checked':''}></td><td>${escapeHtml(row.order_no)}</td><td><b>${row.user_id}</b></td><td>${escapeHtml(row.nickname||'微信用户')}<small class="table-sub">${escapeHtml(row.phone||'-')}</small></td><td>${escapeHtml(row.service_name)}</td><td>¥${row.amount}</td><td>${platform}</td><td>${status(row.status)}</td><td>${delivery}${row.refund_status==='refunded'?'<br><span class="tag fail">退款完成</span>':''}</td><td>${row.created_at}</td><td><div class="order-actions">${actions.join(' ')}</div></td></tr>`}).join('')||'<tr><td colspan="11" class="empty">没有符合条件的订单</td></tr>'}

function showOrderDetail(row){
  const input=detailItems(row.input)
  const result=detailItems(row.result)
  const wechatOrder=detailItems(row.wechat_order)
  const envLabel=row.pay_env==null?'-':(String(row.pay_env)==='1'?'沙箱环境':'正式环境')
  const deliveryMap={pending:'待发货',delivered:'已发货',failed:'发货异常'}
  const refundMap={none:'无退款',processing:'处理中',refunded:'已退款',failed:'退款失败'}
  const money=value=>value==null?'-':`¥${Number(value).toFixed(2)}`
  document.body.insertAdjacentHTML('beforeend',`<div class="dialog-mask"><div class="dialog-card order-detail-dialog"><div class="drawer-head"><div><h3>订单详情</h3><p>${escapeHtml(row.order_no)}</p></div><button class="drawer-close dialog-close">×</button></div><div class="dialog-body"><div class="order-detail-grid"><div><span>订单状态</span><b>${status(row.status)}</b></div><div><span>用户ID</span><b>${escapeHtml(row.user_id??'-')}</b></div><div><span>用户昵称</span><b>${escapeHtml(row.nickname||'微信用户')}</b></div><div><span>手机号</span><b>${escapeHtml(row.phone||'-')}</b></div><div><span>服务</span><b>${escapeHtml(row.service_name||'-')}</b></div><div><span>服务ID</span><b>${escapeHtml(row.service_id??'-')}</b></div><div><span>金额</span><b>${money(row.amount)}</b></div><div><span>接口成本</span><b>${row.cost_amount==null?'-':`¥${Number(row.cost_amount).toFixed(4)}`}</b></div><div><span>支付环境</span><b>${envLabel}</b></div><div><span>创建时间</span><b>${escapeHtml(row.created_at||'-')}</b></div><div><span>支付时间</span><b>${escapeHtml(row.paid_at||'-')}</b></div><div><span>过期时间</span><b>${escapeHtml(row.expired_at||'-')}</b></div><div><span>查询时间</span><b>${escapeHtml(row.queried_at||'-')}</b></div><div><span>更新时间</span><b>${escapeHtml(row.updated_at||'-')}</b></div></div><div class="detail-block"><h4>查询诊断</h4><div class="detail-data"><div><span>调用次数</span><b>${escapeHtml(row.query_attempts??0)}</b></div><div><span>供应商业务码</span><b>${escapeHtml(row.provider_code||'-')}</b></div><div><span>供应商请求ID</span><b>${escapeHtml(row.provider_request_id||'-')}</b></div><div><span>失败原因</span><b>${escapeHtml(row.query_last_error||'-')}</b></div></div></div><div class="detail-block"><h4>用户查询信息</h4><div class="detail-data">${input}</div></div><div class="detail-block"><h4>接口查询结果</h4><div class="detail-data">${result}</div></div><div class="detail-block"><h4>微信支付</h4><div class="detail-data"><div><span>支付状态</span><b>${escapeHtml(row.payment_status||'-')}</b></div><div><span>微信订单号</span><b>${escapeHtml(row.transaction_id||'-')}</b></div><div><span>最近查单</span><b>${escapeHtml(row.last_checked_at||'-')}</b></div></div><div class="detail-data">${wechatOrder}</div></div><div class="detail-block"><h4>退款</h4><div class="detail-data"><div><span>退款状态</span><b>${escapeHtml(refundMap[row.refund_status]||row.refund_status||'-')}</b></div><div><span>退款单号</span><b>${escapeHtml(row.refund_order_no||'-')}</b></div><div><span>退款金额</span><b>${money(row.refund_amount)}</b></div><div><span>退款原因</span><b>${escapeHtml(row.refund_reason||'-')}</b></div><div><span>退款申请时间</span><b>${escapeHtml(row.refund_requested_at||'-')}</b></div><div><span>退款完成时间</span><b>${escapeHtml(row.refunded_at||'-')}</b></div><div><span>退款失败原因</span><b>${escapeHtml(row.refund_last_error||'-')}</b></div><div><span>手动退款金额</span><b>${money(row.manual_refund_amount)}</b></div><div><span>手动退款时间</span><b>${escapeHtml(row.manual_refund_time||'-')}</b></div><div><span>手动退款备注</span><b>${escapeHtml(row.manual_refund_remark||'-')}</b></div></div></div><div class="detail-block"><h4>虚拟支付发货</h4><div class="detail-data"><div><span>发货状态</span><b>${escapeHtml(deliveryMap[row.delivery_status]||row.delivery_status||'-')}</b></div><div><span>发货尝试次数</span><b>${escapeHtml(row.delivery_attempts??0)}</b></div><div><span>发货时间</span><b>${escapeHtml(row.delivered_at||'-')}</b></div><div><span>最近尝试</span><b>${escapeHtml(row.delivery_attempted_at||'-')}</b></div><div><span>发货失败原因</span><b>${escapeHtml(row.delivery_last_error||'-')}</b></div></div></div></div></div></div>`)
  document.querySelector('.dialog-mask:last-of-type').onclick=event=>{if(event.target.closest('.dialog-close'))event.currentTarget.remove()}
}

function detailItems(data){if(!data||((Array.isArray(data)||typeof data==='object')&&Object.keys(data).length===0))return '<div class="detail-empty">暂无数据</div>';const rows=Array.isArray(data)?data.map((item,index)=>[item.label||item.key||`字段 ${index+1}`,item.value??item]):Object.entries(data);return rows.map(([key,value])=>`<div><span>${escapeHtml(key)}</span><b>${escapeHtml(typeof value==='object'?JSON.stringify(value,null,2):value)}</b></div>`).join('')}

async function renderUsers(){
  const query=new URLSearchParams({keyword:userState.keyword,page:String(userState.page)})
  const data=await api(`/users?${query}`)
  $('#content').innerHTML=`<div class="panel"><div class="panel-head"><h3>用户列表 <small>共 ${data.total||0} 人</small></h3><form id="user-search" class="filter search-filter"><input name="keyword" value="${escapeHtml(userState.keyword)}" placeholder="用户ID / 手机号 / 昵称"><button type="submit" class="primary compact">搜索</button><button type="button" id="user-reset" class="secondary compact">重置</button></form></div><div class="table-wrap"><table><thead><tr><th>用户ID</th><th>昵称</th><th>手机号</th><th>状态</th><th>最后登录</th><th>注册时间</th><th>操作</th></tr></thead><tbody>${userRows(data.data||[])}</tbody></table></div>${pagination('user',data)}</div>`
  $('#user-search').onsubmit=event=>{event.preventDefault();userState.keyword=String(new FormData(event.target).get('keyword')||'').trim();userState.page=1;renderUsers().catch(showPageError)}
  $('#user-reset').onclick=()=>{userState.keyword='';userState.page=1;renderUsers().catch(showPageError)}
  bindPagination('user',data,renderUsers,userState)
  $('#content').onclick=async event=>{const button=event.target.closest('.user-status');if(!button)return;button.disabled=true;try{await api(`/users/${button.dataset.id}/status`,{method:'POST',data:{status:Number(button.dataset.status)}});toast('用户状态已更新');await renderUsers()}catch(error){toast(error.message)}finally{button.disabled=false}}
}

function userRows(rows){return rows.map(row=>`<tr><td><b>${row.id}</b></td><td>${escapeHtml(row.nickname)}</td><td>${escapeHtml(row.phone||'-')}</td><td>${row.status?'<span class="tag success">正常</span>':'<span class="tag fail">禁用</span>'}</td><td>${row.last_login_at||'-'}</td><td>${row.created_at}</td><td><button class="action user-status" data-id="${row.id}" data-status="${row.status?0:1}">${row.status?'禁用':'启用'}</button></td></tr>`).join('')||'<tr><td colspan="7" class="empty">没有符合条件的用户</td></tr>'}
function pagination(prefix,data){const current=Number(data.current_page||1),last=Number(data.last_page||1);return `<div class="pagination"><span>第 ${current} / ${last} 页</span><button class="secondary ${prefix}-prev" ${current<=1?'disabled':''}>上一页</button><button class="secondary ${prefix}-next" ${current>=last?'disabled':''}>下一页</button></div>`}
function bindPagination(prefix,data,renderer,state){const current=Number(data.current_page||1),last=Number(data.last_page||1);const prev=$(`.${prefix}-prev`),next=$(`.${prefix}-next`);if(prev)prev.onclick=()=>{if(current>1){state.page=current-1;renderer().catch(showPageError)}};if(next)next.onclick=()=>{if(current<last){state.page=current+1;renderer().catch(showPageError)}}}
function showPageError(error){toast(error.message||'加载失败')}
if(token){window.addEventListener('load',showApp);if(document.readyState==='complete')window.removeEventListener('load',showApp),showApp()}
