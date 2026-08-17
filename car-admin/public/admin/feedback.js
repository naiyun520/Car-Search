const feedbackTypes={bug:'功能异常',order:'订单问题',complaint:'投诉建议',other:'其他'}
let feedbackRows=new Map()

loaders.feedback=async()=>{
  const data=await api('/feedback')
  feedbackRows=new Map(data.data.map(row=>[String(row.id),row]))
  $('#content').innerHTML=`<div class="panel"><div class="panel-head"><div><h3>反馈列表</h3><p class="hint">可查看完整详情、回复用户，或选择多条记录批量删除。</p></div><button class="danger-button" id="batch-delete-feedback" disabled>批量删除</button></div><div class="table-wrap"><table><thead><tr><th class="check-cell"><input type="checkbox" id="feedback-select-all" aria-label="全选"></th><th>反馈ID</th><th>用户ID</th><th>类型</th><th>具体内容</th><th>回复状态</th><th>提交时间</th><th>操作</th></tr></thead><tbody>${data.data.map(row=>feedbackTableRow(row)).join('')||'<tr><td colspan="8" class="empty">暂无用户反馈</td></tr>'}</tbody></table></div></div><div id="feedback-modal"></div>`
  const selection=()=>[...document.querySelectorAll('.feedback-select:checked')].map(item=>item.value)
  const syncSelection=()=>{const selected=selection();const all=[...document.querySelectorAll('.feedback-select')];$('#batch-delete-feedback').disabled=selected.length===0;$('#feedback-select-all').checked=all.length>0&&selected.length===all.length;$('#feedback-select-all').indeterminate=selected.length>0&&selected.length<all.length}
  $('#feedback-select-all').onchange=event=>{document.querySelectorAll('.feedback-select').forEach(item=>item.checked=event.target.checked);syncSelection()}
  document.querySelectorAll('.feedback-select').forEach(item=>item.onchange=syncSelection)
  $('#batch-delete-feedback').onclick=async()=>{const ids=selection();if(!ids.length||!confirm(`确定删除选中的 ${ids.length} 条反馈？删除后用户端也不再显示。`))return;try{await api('/feedback/batch',{method:'DELETE',data:{ids}});toast('反馈已批量删除');await loaders.feedback()}catch(error){toast(error.message)}}
  $('#content').onclick=async event=>{
    const detail=event.target.closest('.feedback-detail')
    if(detail){const row=feedbackRows.get(detail.dataset.id);if(!row)return toast('反馈数据已失效，请刷新列表');return openFeedbackDetail(row)}
    const remove=event.target.closest('.feedback-delete')
    if(!remove||!confirm('确定删除这条反馈？删除后用户端也不再显示。'))return
    try{await api(`/feedback/${remove.dataset.id}`,{method:'DELETE'});toast('反馈已删除');await loaders.feedback()}catch(error){toast(error.message)}
  }
}

function feedbackTableRow(row){return `<tr><td class="check-cell"><input type="checkbox" class="feedback-select" value="${row.id}" aria-label="选择反馈${row.id}"></td><td>#${row.id}</td><td>${row.user_id}</td><td>${escapeHtml(feedbackTypes[row.type]||row.type)}</td><td class="feedback-summary" title="${escapeHtml(row.content)}">${escapeHtml(row.content).slice(0,55)}</td><td>${row.status==='resolved'?'<span class="tag success">已回复</span>':'<span class="tag wait">待回复</span>'}</td><td>${row.created_at}</td><td><button class="action feedback-detail" data-id="${row.id}">查看详情</button><button class="action danger feedback-delete" data-id="${row.id}">删除</button></td></tr>`}

async function openFeedbackDetail(seed){
  if(!seed||!seed.id)return toast('反馈数据无效，请刷新列表')
  let row=seed
  try{const detail=await api(`/feedback-detail/${encodeURIComponent(seed.id)}`);if(detail&&detail.id)row=detail}catch(error){return toast(error.message)}
  const replied=row.status==='resolved'&&row.reply
  $('#feedback-modal').innerHTML=`<div class="dialog-mask"><div class="dialog-card feedback-dialog"><div class="drawer-head"><div><h3>反馈详情 #${row.id}</h3><p>用户ID ${row.user_id} · ${escapeHtml(row.nickname||'微信用户')}</p></div><button class="drawer-close" type="button">×</button></div><div class="dialog-body"><div class="feedback-detail-grid"><div><span>用户ID</span><b>${row.user_id}</b></div><div><span>反馈类型</span><b>${escapeHtml(feedbackTypes[row.type]||row.type)}</b></div><div><span>回复状态</span><b class="${replied?'success-text':'wait-text'}">${replied?'已回复':'待回复'}</b></div><div><span>提交时间</span><b>${row.created_at}</b></div></div><div class="detail-block"><h4>具体内容</h4><div class="content-box">${escapeHtml(row.content)}</div></div>${replied?`<div class="detail-block replied-block"><div class="reply-meta"><h4>回复内容</h4><span>回复时间 ${row.replied_at||row.updated_at}</span></div><div class="content-box">${escapeHtml(row.reply)}</div></div>`:''}<form id="feedback-reply" class="form"><label>${replied?'更新回复':'处理回复'}<textarea name="reply" maxlength="1000" placeholder="回复保存后，用户可在小程序投诉意见页面查看" required>${escapeHtml(row.reply||'')}</textarea></label><div id="feedback-form-error" class="form-error"></div><div class="drawer-actions"><button type="button" class="secondary dialog-cancel">取消</button><button type="submit" class="primary">${replied?'更新回复':'回复并标记已回复'}</button></div></form></div></div></div>`
  const close=()=>{$('#feedback-modal').innerHTML=''}
  $('#feedback-modal .drawer-close').onclick=close
  $('#feedback-modal .dialog-cancel').onclick=close
  $('#feedback-reply').onsubmit=async event=>{event.preventDefault();const button=event.currentTarget.querySelector('button[type="submit"]');button.disabled=true;button.textContent='正在保存...';$('#feedback-form-error').textContent='';try{await api(`/feedback/${row.id}/reply`,{method:'POST',data:Object.fromEntries(new FormData(event.target))});toast('回复已发送');close();await loaders.feedback()}catch(error){$('#feedback-form-error').textContent=error.message}finally{button.disabled=false;button.textContent=replied?'更新回复':'回复并标记已回复'}}
}
