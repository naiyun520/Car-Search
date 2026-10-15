const serviceStatus = value => ({0:['维护','wait'],1:['运行','success'],2:['隐藏','fail']}[Number(value)] || ['未知',''])
const inputTypes = [['text','普通文本'],['name','姓名'],['plate','车牌号'],['plate_prefix','车牌或前缀'],['plate_or_vin','车牌或VIN'],['vin','VIN'],['idcard','身份证号'],['identity','证件/企业代码']]

let serviceRows = []
let serviceCategories = []
let encryptionHealth = null
const serviceIconUrl = icon => /^[a-f0-9]{32}\.(?:png|jpg|webp)$/.test(String(icon||'')) ? `/api/v1/service-icon/${encodeURIComponent(icon)}` : ''
const builtinIcons=[['car','车辆'],['vin','VIN'],['plate','车牌'],['verified','核验'],['location','归属地'],['transfer','过户'],['owner','车主'],['etc','ETC'],['insurance','保险'],['search','查询'],['document','档案'],['shield','安全']]
const iconGlyph={car:'🚘',vin:'VIN',plate:'牌',verified:'✓',location:'⌖',transfer:'⇄',owner:'人',etc:'ETC',insurance:'盾',search:'⌕',document:'档',shield:'安'}
const serviceIconMarkup=icon=>{const url=serviceIconUrl(icon);if(url)return `<img src="${url}" alt="">`;const key=String(icon||'').replace(/^builtin:/,'');return `<span class="builtin-service-icon">${escapeHtml(iconGlyph[key]||'车')}</span>`}
loaders.services = async () => {
  ;[serviceRows,serviceCategories,encryptionHealth] = await Promise.all([api('/services'),api('/service-categories'),api('/encryption-health')])
  renderServiceList()
}

function renderServiceList() {
  $('#content').innerHTML = `
    <div class="panel service-list-panel">
      <div class="panel-head"><div><h3>接口服务管理</h3><p class="hint">分类、双端价格、支付道具与供应商规则统一在这里维护。列表不展示服务说明，说明仅用于小程序查询页。</p></div><button class="primary compact" id="add-service">新增接口</button></div>
      ${encryptionHealth&&!encryptionHealth.healthy?`<div class="security-warning"><b>主密钥诊断未通过</b><span>当前密钥指纹 ${escapeHtml(encryptionHealth.key_fingerprint)}，${encryptionHealth.encrypted_count} 条密文中仅 ${encryptionHealth.decryptable_count} 条可解密。请恢复原 DATA_ENCRYPT_KEY，或在编辑接口时重新填写完整 URL 后保存。</span></div>`:''}
      <div class="table-wrap"><table><thead><tr><th>排序</th><th>服务</th><th>分类</th><th>编码</th><th>接口地址</th><th>安卓 / iOS 售价</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody>
        ${serviceRows.map((row, index) => { const state=serviceStatus(row.status); const up=index===0?`<span style="opacity:.3;cursor:not-allowed;padding:4px 7px">↑</span>`:`<button class="action sort-btn" data-move="-1" style="font-size:15px;padding:4px 7px">↑</button>`; const down=index===serviceRows.length-1?`<span style="opacity:.3;cursor:not-allowed;padding:4px 7px">↓</span>`:`<button class="action sort-btn" data-move="1" style="font-size:15px;padding:4px 7px">↓</button>`; const endpoint=row.api_url_error?'<span class="tag fail">密文无法解密</span>':row.api_url_configured?escapeHtml(row.api_url):'<span class="tag fail">未配置</span>'; return `<tr><td>${up}${down}</td><td><div class="service-title-cell">${serviceIconMarkup(row.icon)}<div><b>${escapeHtml(row.name)}</b></div></div></td><td>${escapeHtml(row.category_name||'未分类')}</td><td><code>${escapeHtml(row.code)}</code></td><td class="endpoint-cell">${endpoint}</td><td><b>¥${row.sale_price_android}</b><small class="table-sub">iOS ¥${row.sale_price_ios}</small></td><td><span class="tag ${state[1]}">${state[0]}</span></td><td>${row.updated_at}</td><td><button class="action test-service" data-id="${row.id}">测试</button><button class="action edit-service" data-id="${row.id}">编辑</button><button class="action danger delete-service" data-id="${row.id}">删除</button></td></tr>` }).join('') || '<tr><td colspan="9" class="empty">暂无接口服务</td></tr>'}
      </tbody></table></div>
    </div>${categoryPanel()}<div id="service-editor"></div>`

  $('#add-service').onclick = () => openServiceEditor(null)
  $('#content').onclick = async event => {
    const sortBtn = event.target.closest('.sort-btn')
    if (sortBtn) return moveService(sortBtn)
    const test = event.target.closest('.test-service')
    if (test) {
      const service=serviceRows.find(row=>String(row.id)===String(test.dataset.id))
      if(!service)return toast('接口数据已失效，请刷新页面后重试')
      return openServiceTest(service)
    }
    const edit = event.target.closest('.edit-service')
    if (edit) {
      const service=serviceRows.find(row=>String(row.id)===String(edit.dataset.id))
      if(!service)return toast('接口数据已失效，请刷新页面后重试')
      return openServiceEditor(service)
    }
    const remove = event.target.closest('.delete-service')
    if (!remove || !confirm('确定删除该接口服务？已有历史订单的服务不能删除，可改为隐藏。')) return
    await api(`/services/${remove.dataset.id}`, { method: 'DELETE' })
    toast('接口服务已删除')
    await loaders.services()
  }
  bindCategoryActions()
}

function categoryPanel(){return `<div class="panel"><div class="panel-head"><div><h3>服务分类</h3><p class="hint">首页分类按这里的顺序展示；停用分类会同时从小程序隐藏该分类下全部服务。</p></div><button class="primary compact" id="add-category">新增分类</button></div><div class="table-wrap"><table><thead><tr><th>排序</th><th>分类名称</th><th>编码</th><th>服务数</th><th>状态</th><th>操作</th></tr></thead><tbody>${serviceCategories.map((row,index)=>`<tr><td><button class="action category-sort" data-id="${row.id}" data-move="-1" ${index===0?'disabled':''}>↑</button><button class="action category-sort" data-id="${row.id}" data-move="1" ${index===serviceCategories.length-1?'disabled':''}>↓</button></td><td><b>${escapeHtml(row.name)}</b></td><td><code>${escapeHtml(row.code)}</code></td><td>${row.service_count}</td><td><span class="tag ${Number(row.status)===1?'success':'wait'}">${Number(row.status)===1?'启用':'停用'}</span></td><td><button class="action edit-category" data-id="${row.id}">编辑</button><button class="action danger delete-category" data-id="${row.id}">删除</button></td></tr>`).join('')}</tbody></table></div></div>`}
function bindCategoryActions(){
  $('#add-category').onclick=()=>openCategoryEditor(null)
  document.querySelectorAll('.edit-category').forEach(button=>button.onclick=()=>openCategoryEditor(serviceCategories.find(row=>String(row.id)===button.dataset.id)))
  document.querySelectorAll('.delete-category').forEach(button=>button.onclick=async()=>{if(!confirm('确认删除该分类？分类下有服务时系统会拒绝删除。'))return;await api(`/service-categories/${button.dataset.id}`,{method:'DELETE'});toast('分类已删除');await loaders.services()})
  document.querySelectorAll('.category-sort').forEach(button=>button.onclick=async()=>{const index=serviceCategories.findIndex(row=>String(row.id)===button.dataset.id),target=index+Number(button.dataset.move);if(target<0||target>=serviceCategories.length)return;[serviceCategories[index],serviceCategories[target]]=[serviceCategories[target],serviceCategories[index]];await api('/service-categories/sort',{method:'POST',data:{ids:serviceCategories.map(row=>row.id)}});await loaders.services()})
}
function openCategoryEditor(category){const item=category||{code:'',name:'',icon:'grid',status:1};$('#service-editor').innerHTML=`<div class="dialog-mask"><div class="dialog-card"><div class="drawer-head"><div><h3>${category?'编辑':'新增'}服务分类</h3><p>分类用于首页筛选与后台组织服务</p></div><button class="drawer-close">×</button></div><div class="dialog-body"><form id="category-form" class="form"><div class="form-grid"><label>分类编码<input name="code" value="${escapeHtml(item.code)}" ${category?'readonly':''} required></label><label>分类名称<input name="name" value="${escapeHtml(item.name)}" required></label><label>图标<select name="icon">${[['grid','全部'],['car','车辆'],['verified','核验'],['shield','保险'],['road','ETC'],['search','查询'],['document','档案']].map(x=>`<option value="${x[0]}" ${item.icon===x[0]?'selected':''}>${x[1]}</option>`).join('')}</select></label><label>状态<select name="status"><option value="1" ${Number(item.status)===1?'selected':''}>启用</option><option value="0" ${Number(item.status)===0?'selected':''}>停用</option></select></label></div><div class="drawer-actions"><button type="button" class="secondary dialog-cancel">取消</button><button type="submit" class="primary">保存分类</button></div></form></div></div></div>`;const close=()=>$('#service-editor').innerHTML='';$('.drawer-close').onclick=close;$('.dialog-cancel').onclick=close;$('#category-form').onsubmit=async event=>{event.preventDefault();const data=Object.fromEntries(new FormData(event.target));data.status=Number(data.status);await api(category?`/service-categories/${category.id}`:'/service-categories',{method:'POST',data});close();toast('分类已保存');await loaders.services()}}

function openServiceTest(service) {
  if (!service.api_url_configured) return toast('请先编辑并保存完整接口 URL')
  const fields=(service.input_schema||[]).map(field=>`<label>${escapeHtml(field.label)}${field.required?' <span class="required-mark">*</span>':''}<input class="test-input" data-key="${escapeHtml(field.key)}" placeholder="请输入有效${escapeHtml(field.label)}" maxlength="100" ${field.required?'required':''}></label>`).join('')
  $('#service-editor').innerHTML=`<div class="dialog-mask"><div class="dialog-card service-test-dialog"><div class="drawer-head"><div><h3>测试接口：${escapeHtml(service.name)}</h3><p>${escapeHtml(service.code)} · 走正式订单相同的请求与解析链路</p></div><button class="drawer-close" type="button">×</button></div><div class="dialog-body"><div class="service-test-warning">警告：测试会真实调用供应商接口，可能产生接口费用。请使用已授权的合法测试数据。</div><form id="service-test-form" class="form"><div class="form-grid">${fields}</div><div class="drawer-actions"><button type="button" class="secondary test-cancel">关闭</button><button type="submit" class="primary run-service-test">开始测试</button></div></form><div id="service-test-result"></div></div></div></div>`
  const close=()=>{$('#service-editor').innerHTML=''}
  $('#service-editor .drawer-close').onclick=close
  $('#service-editor .test-cancel').onclick=close
  $('#service-test-form').onsubmit=async event=>{
    event.preventDefault()
    if(!confirm('本次操作将真实调用供应商接口，可能产生费用。确认继续？'))return
    const button=event.currentTarget.querySelector('.run-service-test')
    const resultNode=$('#service-test-result')
    const input={}
    event.currentTarget.querySelectorAll('.test-input').forEach(node=>{input[node.dataset.key]=node.value})
    button.disabled=true;button.textContent='测试中...';resultNode.innerHTML='<div class="page-loading compact-loading"><span></span><p>正在请求供应商...</p></div>'
    try{
      const result=await api(`/service-test/${encodeURIComponent(service.id)}`,{method:'POST',data:{input},timeout:35000})
      const state=result.success?'<span class="tag success">测试成功</span>':'<span class="tag fail">测试失败</span>'
      const diagnostics=`<div class="detail-data"><div><span>测试结果</span><b>${state}</b></div><div><span>耗时</span><b>${escapeHtml(result.duration_ms??'-')} ms</b></div><div><span>供应商业务码</span><b>${escapeHtml(result.provider_code||'-')}</b></div><div><span>供应商请求ID</span><b>${escapeHtml(result.provider_request_id||'-')}</b></div></div>`
      resultNode.innerHTML=`<div class="detail-block"><h4>诊断结果</h4>${diagnostics}${result.success?`<h4 class="test-data-title">解析后数据</h4><div class="detail-data">${detailItems(result.data)}</div>`:`<div class="service-test-error">${escapeHtml(result.error||'供应商查询未成功')}</div>`}</div>`
    }catch(error){resultNode.innerHTML=`<div class="service-test-error">${escapeHtml(error.message||'接口测试请求失败')}</div>`}
    finally{button.disabled=false;button.textContent='再次测试'}
  }
}

async function moveService(button) {
  const row = button.closest('tr')
  const index = [...row.parentElement.children].indexOf(row)
  const target = index + Number(button.dataset.move)
  if (target < 0 || target >= serviceRows.length) return
  ;[serviceRows[index], serviceRows[target]] = [serviceRows[target], serviceRows[index]]
  renderServiceList()
  try {
    await api('/services/sort', { method: 'POST', data: { ids: serviceRows.map(row => row.id) } })
    toast('排序已保存')
  } catch (error) {
    toast(error.message || '排序保存失败')
    serviceRows = await api('/services')
    renderServiceList()
  }
}

function openServiceEditor(service) {
  const item = service || {code:'',category_id:serviceCategories[0]?.id||'',payment_product_id_android:'',payment_product_id_ios:'',name:'',short_name:'',description:'',icon:'builtin:car',sale_price_android:'1.00',sale_price_ios:'1.00',cost_price:'0.0000',sort:0,status:0,request_method:'GET',response_code_path:'code',response_success_value:'200',response_data_path:'data',input_schema:[{key:'',label:'',type:'text',required:true}],result_schema:[{key:'',label:''}]}
  const currentIcon=serviceIconUrl(item.icon)
  $('#service-editor').innerHTML = `<div class="drawer-mask"><div class="service-drawer">
    <div class="drawer-head"><div><h3>${service?'编辑接口服务':'新增接口服务'}</h3><p>完整配置前端功能、供应商请求和结果展示规则</p></div><button class="drawer-close" type="button">×</button></div>
    <form id="service-form" class="form">
      <div class="form-section"><h4>前端服务信息</h4><div class="form-grid three">
        <label>服务编码<input name="code" value="${escapeHtml(item.code)}" placeholder="例如 owner_verify" ${service?'readonly':''} required>${service?'<small class="field-note">服务编码创建后不可修改</small>':''}</label>
        <label>服务分类<select name="category_id" required>${serviceCategories.map(row=>`<option value="${row.id}" ${Number(item.category_id)===Number(row.id)?'selected':''}>${escapeHtml(row.name)}</option>`).join('')}</select></label>
        <label>安卓支付道具ID<input name="payment_product_id_android" value="${escapeHtml(item.payment_product_id_android||'')}" placeholder="安卓/鸿蒙/Windows 道具ID"><small class="field-note">道具价格必须与安卓售价一致</small></label>
        <label>iOS支付道具ID<input name="payment_product_id_ios" value="${escapeHtml(item.payment_product_id_ios||'')}" placeholder="Apple 支付道具ID"><small class="field-note">道具价格必须与iOS售价一致</small></label>
        <label>服务名称<input name="name" value="${escapeHtml(item.name)}" required></label>
        <label>前端简称<input name="short_name" value="${escapeHtml(item.short_name)}" required></label>
        <label>内置服务图标<select name="builtin_icon">${builtinIcons.map(icon=>`<option value="${icon[0]}" ${String(item.icon).replace(/^builtin:/,'')===icon[0]?'selected':''}>${icon[1]}</option>`).join('')}</select><small class="field-note">也可在下一项上传自定义图片覆盖</small></label>
        <label>自定义图标<input name="icon_file" type="file" accept="image/png,image/jpeg,image/webp"><small class="field-note">${currentIcon?`<img class="service-icon-preview" src="${currentIcon}" alt="当前图标"> 当前图标；选择新文件后保存替换`:'PNG、JPG 或 WebP，32-1024像素，最大48KB'}</small></label>
        <label>安卓售价（元）<input name="sale_price_android" type="number" min="1" step="0.01" value="${item.sale_price_android}" required></label>
        <label>iOS售价（元）<input name="sale_price_ios" type="number" min="1" step="0.01" value="${item.sale_price_ios}" required><small class="field-note">Apple 支付最低1元，需与iOS道具价格一致</small></label>
        <label>成本价（元）<input name="cost_price" type="number" min="0" step="0.0001" value="${item.cost_price}" required></label>
        <label>接口状态<select name="status"><option value="0" ${Number(item.status)===0?'selected':''}>维护（前端可见，不可支付）</option><option value="1" ${Number(item.status)===1?'selected':''}>运行（正常查询支付）</option><option value="2" ${Number(item.status)===2?'selected':''}>隐藏（前端不显示）</option></select></label>
      </div><label>服务说明<textarea name="description" maxlength="255">${escapeHtml(item.description)}</textarea></label></div>

      <div class="form-section"><h4>供应商接口</h4><label>完整接口 URL ${item.api_url_configured?'<span class="tag success">已配置</span>':''}<textarea name="api_url" class="endpoint-input" placeholder="https://www.example.com/api/?id=31&key=密钥&name=姓名&chepai=车牌号">${escapeHtml(item.api_url||'')}</textarea></label>
      <div class="hint">完整地址仅在管理员登录后展示。系统提交查询时会用用户输入覆盖 URL 中同名参数，例如输入字段 key 为 <code>name</code>、<code>chepai</code>，会自动替换接口中的同名参数。</div>
      <div class="form-grid three"><label>请求方式<select name="request_method"><option ${item.request_method==='GET'?'selected':''}>GET</option><option ${item.request_method==='POST'?'selected':''}>POST</option></select></label><label>业务状态字段路径<input name="response_code_path" value="${escapeHtml(item.response_code_path)}" placeholder="例如 code，留空不校验"></label><label>成功状态值<input name="response_success_value" value="${escapeHtml(item.response_success_value)}" placeholder="例如 200"></label><label>结果数据路径<input name="response_data_path" value="${escapeHtml(item.response_data_path)}" placeholder="例如 data，留空表示整个响应"></label></div></div>

      <div class="form-section"><div class="section-title-row"><div><h4>用户输入字段</h4><p>参数名必须与供应商接口参数一致</p></div><button type="button" class="action add-input">+ 添加字段</button></div><div id="input-fields">${item.input_schema.map(inputRow).join('')}</div></div>
      <div class="form-section"><div class="section-title-row"><div><h4>结果展示字段</h4><p>支持点号路径，例如 vehicle.brand</p></div><button type="button" class="action add-result">+ 添加字段</button></div><div id="result-fields">${item.result_schema.map(resultRow).join('')}</div></div>
      <div id="service-form-error" class="form-error"></div><div class="drawer-actions"><button type="button" class="secondary drawer-cancel">取消</button><button type="submit" class="primary save-service">保存接口服务</button></div>
    </form></div></div>`

  const close = () => { $('#service-editor').innerHTML='' }
  $('.drawer-close').onclick = close
  $('.drawer-cancel').onclick = close
  $('.add-input').onclick = () => $('#input-fields').insertAdjacentHTML('beforeend', inputRow({key:'',label:'',type:'text',required:false}))
  $('.add-result').onclick = () => $('#result-fields').insertAdjacentHTML('beforeend', resultRow({key:'',label:''}))
  $('#service-form').onclick = event => { const button=event.target.closest('.remove-row'); if(button && button.closest('.schema-row').parentElement.children.length>1) button.closest('.schema-row').remove() }
  $('#service-form').onsubmit = async event => {
    event.preventDefault()
    const button=event.currentTarget.querySelector('.save-service')
    const errorNode=$('#service-form-error')
    errorNode.textContent=''
    button.disabled=true
    button.textContent='正在保存...'
    try {
      const formData=new FormData(event.target)
      const iconFile=formData.get('icon_file')
      formData.delete('icon_file')
      const fields=Object.fromEntries(formData)
      if(service)fields.service_id=Number(service.id)
      fields.status=Number(fields.status)
      fields.input_schema=[...$('#input-fields').querySelectorAll('.schema-row')].map(row=>({key:row.querySelector('.field-key').value.trim(),label:row.querySelector('.field-label').value.trim(),type:row.querySelector('.field-type').value,required:row.querySelector('.field-required').checked}))
      fields.result_schema=[...$('#result-fields').querySelectorAll('.schema-row')].map(row=>({key:row.querySelector('.result-key').value.trim(),label:row.querySelector('.result-label').value.trim()}))
      const saved=await api(service?`/services/${service.id}/update`:'/services',{method:'POST',data:fields})
      const serviceId=Number(saved?.id||service?.id||0)
      if(iconFile instanceof File && iconFile.size){
        if(iconFile.size>48*1024)throw new Error('服务图标不能超过48KB')
        const upload=new FormData();upload.append('icon',iconFile)
        const response=await fetch(`${API}/service-icon-upload/${serviceId}`,{method:'POST',headers:{Authorization:`Bearer ${token}`},body:upload,cache:'no-store'})
        const body=await response.json().catch(()=>({}))
        if(response.status===401){logout();throw new Error('登录已失效')}
        if(!response.ok||body.code!==0)throw new Error(body.message||'图标上传失败')
      }
      toast(service?'接口服务已保存':'接口服务已创建')
      close(); await loaders.services()
    } catch(error) {
      errorNode.textContent=error.message||'接口服务保存失败'
      errorNode.scrollIntoView({behavior:'smooth',block:'center'})
      toast(error.message||'保存失败')
    } finally { button.disabled=false;button.textContent='保存接口服务' }
  }
}

function inputRow(row) { return `<div class="schema-row input-row"><input class="field-key" value="${escapeHtml(row.key)}" placeholder="参数名，如 chepai"><input class="field-label" value="${escapeHtml(row.label)}" placeholder="前端名称，如 车牌号"><select class="field-type">${inputTypes.map(type=>`<option value="${type[0]}" ${row.type===type[0]?'selected':''}>${type[1]}</option>`).join('')}</select><label class="check-label"><input class="field-required" type="checkbox" ${row.required?'checked':''}>必填</label><button type="button" class="action danger remove-row">删除</button></div>` }
function resultRow(row) { return `<div class="schema-row result-row"><input class="result-key" value="${escapeHtml(row.key)}" placeholder="响应字段路径，如 vin"><input class="result-label" value="${escapeHtml(row.label)}" placeholder="展示名称，如 VIN车架号"><button type="button" class="action danger remove-row">删除</button></div>` }
