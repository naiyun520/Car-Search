const baseOrderDetail=showOrderDetail
showOrderDetail=function(row){
 baseOrderDetail(row)
 const dialog=document.querySelector('.dialog-mask:last-of-type'),body=dialog.querySelector('.dialog-body'),panel=document.createElement('section')
 panel.className='order-workbench'
 panel.innerHTML='<div class="workbench-heading"><div><span class="eyebrow">订单工作台</span><h3>查看处理进度，谨慎修订报告</h3><p>诊断用于排查，修订只改变用户看到的报告内容。</p></div></div><div class="workbench-tabs"><button data-tool="diagnose" aria-expanded="false"><span class="tool-symbol">◎</span><span><b>履约诊断</b><small>任务进度 · 调用与回调记录</small></span><i>＋</i></button><button data-tool="revisions" aria-expanded="false"><span class="tool-symbol">✎</span><span><b>结果修订</b><small>编辑草稿 · 核对发布 · 历史版本</small></span><i>＋</i></button></div><div class="order-tool-body" hidden></div>'
 body.prepend(panel)
 const target=panel.querySelector('.order-tool-body'),path='/orders/'+encodeURIComponent(row.order_no)
 const names={pending_submit:'等待提交',submitting:'正在提交',waiting_result:'等待获取报告',fetching:'正在获取报告',succeeded:'查询成功',failed:'查询失败',no_data:'无可展示数据',timed_out:'已超时',submit_unknown:'下单结果待核实',direct_unknown:'查询结果待核实',stopped:'已停止',pending_direct:'等待查询',running_direct:'正在查询',SUBMIT:'提交查询',FETCH:'获取报告',DIRECT_QUERY:'一次查询',started:'执行中',confirmed:'已确认计费',unknown:'待核实',not_billable:'不计费',completed:'已处理',received:'已接收',processing:'处理中',manual_review:'待人工复核',retryable_failed:'等待重试',success:'成功'}
 const label=v=>names[v]||String(v??'—'),esc=escapeHtml
 const grid=(rows,cols)=>'<div class="table-wrap"><table><thead><tr>'+cols.map(c=>'<th>'+c[1]+'</th>').join('')+'</tr></thead><tbody>'+(rows.map(r=>'<tr>'+cols.map(c=>'<td>'+esc(c[0]==='call_status'?({succeeded:'调用成功',failed:'调用失败'}[r[c[0]]]||label(r[c[0]])):['signature_present','signature_valid'].includes(c[0])?(Number(r[c[0]])===1?'是':'否'):label(r[c[0]]))+'</td>').join('')+'</tr>').join('')||'<tr><td colspan="'+cols.length+'"><div class="empty-state">暂无记录</div></td></tr>')+'</tbody></table></div>'
 let active='',sequence=0,items=[],dirty=false,baseItems=[]
 const leave=()=>!dirty||confirm('编辑内容尚未保存，确认放弃这些修改？')
 function heading(n,t){return '<div class="section-heading"><div><h3>'+n+'</h3><p>'+t+'</p></div></div>'}
 function setActive(v){active=v;target.hidden=!v;panel.querySelectorAll('.workbench-tabs button').forEach(b=>{const on=b.dataset.tool===v;b.classList.toggle('active',on);b.setAttribute('aria-expanded',String(on));b.querySelector('i').textContent=on?'−':'＋'})}
 function sync(){
  target.querySelectorAll('[data-field]').forEach(el=>{
   const x=items[Number(el.dataset.index)]
   if(el.dataset.field==='value'){if(el.dataset.kind==='number'&&el.value.trim()!==''&&!Number.isFinite(Number(el.value)))throw Error('数字字段必须填写有效数字');x.value=el.dataset.kind==='json'?JSON.parse(el.value):el.dataset.kind==='number'?(el.value.trim()===''?null:Number(el.value)):el.dataset.kind==='boolean'?el.value==='true':el.value}
   else x[el.dataset.field]=el.value.trim()
  })
 }
 function filterFields(){
  const keyword=(target.querySelector('#revision-search')?.value||'').trim().toLowerCase()
  target.querySelectorAll('.revision-field').forEach(card=>{card.hidden=keyword&&!Array.from(card.querySelectorAll('input[data-field]')).some(x=>x.value.toLowerCase().includes(keyword))})
 }
 function fields(){
  const preview=target.querySelector('#revision-preview');if(preview)preview.innerHTML=''
  target.querySelector('#revision-fields').innerHTML=items.map((x,i)=>{
   const structured=x.value!==null&&typeof x.value==='object',kind=structured||x.value===null?'json':typeof x.value,shown=structured||x.value===null?JSON.stringify(x.value,null,2):String(x.value??'')
   const input=kind==='boolean'?'<select data-field="value" data-kind="boolean" data-index="'+i+'"><option value="true" '+(x.value?'selected':'')+'>是（true）</option><option value="false" '+(!x.value?'selected':'')+'>否（false）</option></select>':'<textarea data-field="value" data-kind="'+kind+'" data-index="'+i+'" aria-label="字段内容">'+esc(shown)+'</textarea>'
   return '<article class="revision-field"><div class="revision-field-head"><b>字段 '+String(i+1).padStart(2,'0')+'</b><button class="action danger" data-tool="remove" data-index="'+i+'">移除此字段</button></div><div class="form-grid"><label>报告中的名称<input data-field="label" data-index="'+i+'" value="'+esc(x.label)+'" maxlength="100"></label><label>字段标识<input data-field="key" data-index="'+i+'" value="'+esc(x.key)+'" maxlength="100"><small>用于报告排版，已有标识建议保持原值</small></label></div><label>字段内容 <small>'+(kind==='json'?'数组 / 对象：使用有效 JSON':kind==='number'?'数字':'直接输入文字或选择值')+'</small>'+input+'</label></article>'
  }).join('')
  filterFields()
 }
 async function history(){
  const list=await api(path+'/result-revisions')
  const node=target.querySelector('#revision-history');if(!node)return
  node.innerHTML=heading('修订记录','原始结果保留，可载入历史内容重新创建草稿。')+(list.map(x=>'<div class="version-row"><div><span class="tag '+(x.status==='draft'?'wait':'success')+'">'+(x.status==='draft'?'未发布草稿':'已发布版本')+'</span><b>'+esc(x.source==='original'?'原始报告':'人工修订')+' · #'+esc(x.id)+'</b><small>'+esc(x.created_at)+'</small><p>'+esc(x.change_reason)+'</p></div><div><button class="secondary" data-tool="load" data-id="'+x.id+'">载入编辑</button>'+(x.status==='draft'?' <button class="secondary" data-tool="publish" data-id="'+x.id+'">核对并发布</button>':'')+'</div></div>').join('')||'<div class="empty-state">还没有修订记录。原报告仍在正常展示。</div>')
 }
 function editor(){
  target.innerHTML=heading('编辑报告草稿','修改后先预览、再保存草稿；只有发布后，用户才会看到新内容。')+'<div class="edit-steps"><span class="active">1 编辑字段</span><span>2 预览并存草稿</span><span>3 核对发布</span></div><div class="notice-box">不改变付款、退款或订单状态，不延长报告24小时可见期。</div><div class="field-search"><input id="revision-search" type="search" aria-label="搜索报告字段" placeholder="搜索展示名称或字段标识"><button class="secondary" data-tool="save">预览修改</button></div><div class="form" id="revision-fields"></div><button class="secondary" data-tool="add">＋ 添加展示字段</button><label class="revision-reason">本次为什么修改？<textarea id="revision-reason" maxlength="500" placeholder="例如：更正车辆型号的展示名称，保留供应商原始查询记录"></textarea></label><div class="editor-actions"><button class="primary" data-tool="save">预览修改内容</button><span>尚未保存，用户报告不会改变</span></div><div id="revision-preview"></div><div id="revision-history"></div>'
  fields()
 }
 function preview(before,after,action,id){
  const keys=[...new Set([...before.map(x=>x.key),...after.map(x=>x.key)])],show=v=>v===undefined?'（移除 / 不存在）':typeof v==='object'?JSON.stringify(v,null,2):String(v)
  const rows=keys.map(k=>{const b=before.find(x=>x.key===k),a=after.find(x=>x.key===k),changed=JSON.stringify(b)!==JSON.stringify(a);return '<tr class="'+(changed?'changed':'')+'"><td>'+esc(a?.label||b?.label||k)+(changed?'<small class="change-label">有修改</small>':'')+'</td><td><pre>'+esc(show(b?.value))+'</pre></td><td><pre>'+esc(show(a?.value))+'</pre></td></tr>'}).join('')
  target.querySelector('#revision-preview').innerHTML='<section class="revision-preview">'+heading('核对修改前后','高亮行表示内容或字段发生变化。请逐项核对。')+'<div class="table-wrap"><table><thead><tr><th>展示字段</th><th>修改前</th><th>修改后</th></tr></thead><tbody>'+rows+'</tbody></table></div><div class="editor-actions"><button class="primary" data-tool="'+action+'" '+(id?'data-id="'+id+'"':'')+'>'+(action==='confirm-publish'?'确认发布这个版本':'确认保存为草稿')+'</button><span>'+(action==='confirm-publish'?'发布后立即影响有效期内的用户报告':'保存草稿不会影响用户')+'</span></div></section>'
 }
 async function diagnose(response){
  const request=sequence,d=response||await api(path+'/fulfillment'),j=d.task||{}
  if(request!==sequence||active!=='diagnose'||!dialog.isConnected)return
  const activeStates=['pending_direct','pending_submit','waiting_result','fetching','submitting','running_direct'],failureStates=['failed','no_data','timed_out','submit_unknown','direct_unknown','stopped']
  const diagnosticMessage=j.last_error_message&&j.state!=='succeeded'?'<div class="notice-box '+(failureStates.includes(j.state)?'error-note':'')+'">'+esc(j.last_error_message)+'</div>':''
  target.innerHTML=heading('履约诊断','这里只展示实际记录；没有调用记录不代表历史订单未曾查询。')+'<div class="task-summary"><div><small>任务处理阶段</small><strong>'+esc(label(j.state||'暂无任务记录'))+'</strong></div><div><small>已获取 / 最多获取</small><strong>'+esc(j.fetch_attempts??'—')+' / '+esc(j.max_fetch_attempts??'—')+'</strong></div><div><small>下次检查时间</small><strong>'+esc(j.next_run_at||'无待执行检查')+'</strong></div><div><small>处理截止时间</small><strong>'+esc(j.deadline_at||'—')+'</strong></div></div>'+diagnosticMessage+'<div class="diagnostic-actions">'+(j.state==='waiting_result'?'<button class="secondary" data-tool="fetch">立即检查已提交报告</button>':'')+(activeStates.includes(j.state)?'<button class="secondary danger" data-tool="stop">停止后续处理</button>':'')+'<button class="secondary" data-tool="refresh-diagnose">刷新诊断</button></div><h4>供应商调用记录</h4>'+grid(d.calls,[['stage','步骤'],['call_status','结果'],['billing_status','计费判断'],['cost_amount','成本（元）'],['provider_balance_before','调用前余额'],['provider_balance_after','调用后余额'],['provider_balance_change','余额变化'],['duration_ms','耗时（毫秒）'],['started_at','调用时间'],['error_message','异常说明']])
 }
 panel.oninput=event=>{if(event.target.id==='revision-search'){filterFields();return}if(active==='revisions'){dirty=true;const p=target.querySelector('#revision-preview');if(p)p.innerHTML=''}}
 panel.onclick=async event=>{
  const btn=event.target.closest('[data-tool]');if(!btn)return;const action=btn.dataset.tool
  if(action==='diagnose'||action==='revisions'){
   if(!leave())return;dirty=false;const close=active===action;const request=++sequence;setActive(close?'':action);if(close){target.innerHTML='';return}
   target.innerHTML='<div class="empty-state">正在加载…</div>';btn.disabled=true
   try{
    if(action==='diagnose'){const d=await api(path+'/fulfillment');if(request!==sequence||!dialog.isConnected)return; // render only the selected pane
     // Reuse the renderer with a single cached response.
     await diagnose(d)
    }else{
     if(row.status!=='success')throw Error('只有查询成功的订单才能修订报告')
     const latest=await api('/order-detail/'+encodeURIComponent(row.order_no));if(request!==sequence||!dialog.isConnected)return
     items=structuredClone(latest.editable_result||latest.result||[]);baseItems=structuredClone(items);editor();await history()
    }
   }catch(e){if(request===sequence)target.innerHTML='<div class="empty-state">'+esc(e.message||'加载失败')+'。请收起后重新打开。</div>'}finally{btn.disabled=false}return
  }
  btn.disabled=true
  try{
   if(action==='refresh-diagnose'){await diagnose()}
   else if(action==='add'){sync();items.push({key:'',label:'',value:''});target.querySelector('#revision-search').value='';fields();dirty=true}
   else if(action==='remove'){sync();items.splice(Number(btn.dataset.index),1);fields();dirty=true}
   else if(action==='load'){if(!leave())return;const d=await api(path+'/result-revisions/'+btn.dataset.id);items=structuredClone(d.result.items||[]);dirty=true;editor();await history()}
   else if(action==='save'){
    sync();if(!items.length||items.some(x=>!x.key||!x.label))throw Error('每个字段都需要填写展示名称和标识，至少保留一个字段')
    if(new Set(items.map(x=>x.key)).size!==items.length)throw Error('字段标识不能重复')
    if(!target.querySelector('#revision-reason').value.trim())throw Error('请先填写修改原因')
    preview(baseItems,items,'confirm-save')
   }else if(action==='confirm-save'){
    sync();const reason=target.querySelector('#revision-reason').value.trim();if(!reason)throw Error('请填写修改原因')
    await api(path+'/result-revisions',{method:'POST',data:{items,reason}});dirty=false;toast('草稿已保存，用户报告尚未改变');target.querySelector('#revision-preview').innerHTML='<div class="notice-box">草稿已保存。请在下方修订记录中选择“核对并发布”。</div>';await history()
   }else if(action==='publish'){
    if(!leave())return
    if(!target.querySelector('#revision-preview')){items=[];editor()}
    const [revision,current]=await Promise.all([api(path+'/result-revisions/'+btn.dataset.id),api('/order-detail/'+encodeURIComponent(row.order_no))])
    preview(current.editable_result||current.result||[],revision.result.items||[],'confirm-publish',btn.dataset.id)
   }else if(action==='confirm-publish'){
    if(!confirm('确认发布？有效期内的用户将立即看到这个版本。'))return
    await api(path+'/result-revisions/'+btn.dataset.id+'/publish',{method:'POST'});dirty=false;toast('报告修订已发布');target.querySelector('#revision-preview').innerHTML='<div class="notice-box">已发布。用户可见期限保持不变。</div>';await history()
   }else if(action==='stop'){
    const reason=prompt('请输入停止原因。已送达供应商的请求无法撤回：');if(!reason?.trim())return
    await api(path+'/fulfillment/stop',{method:'POST',data:{reason:reason.trim()}});await diagnose();toast('已处理停止请求，请核对最新任务阶段')
   }else if(action==='fetch'){
    if(!confirm('仅检查已经提交的报告，不会重新下单。继续？'))return
    await api(path+'/fulfillment/fetch-now',{method:'POST'});toast('已加入检查队列');await diagnose()
   }
  }catch(e){toast(e instanceof SyntaxError?'结构化字段的 JSON 格式不正确，请检查后重试':e.message||'操作失败')}finally{btn.disabled=false}
 }
 dialog.addEventListener('click',e=>{if(e.target.closest('.dialog-close')&&!leave()){e.preventDefault();e.stopImmediatePropagation()}},true)
}
