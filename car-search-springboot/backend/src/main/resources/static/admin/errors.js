const wait=milliseconds=>new Promise(resolve=>setTimeout(resolve,milliseconds))
function clearAdminError(){
  document.querySelectorAll('.admin-error-panel').forEach(node=>node.remove())
  document.querySelectorAll('.validation-target').forEach(node=>{node.classList.remove('validation-target');node.removeAttribute('aria-invalid')})
}
function showAdminError(error){
  clearAdminError()
  const host=document.querySelector('.dialog-mask:last-of-type .dialog-body')||document.querySelector('#content')||document.querySelector('#login-error')
  if(!host)return
  const panel=document.createElement('div');panel.className='admin-error-panel';panel.setAttribute('role','alert')
  const message=document.createElement('div');message.textContent=error?.message||'当前操作未完成，请检查填写内容后重试。'
  const close=document.createElement('button');close.type='button';close.className='admin-error-close';close.textContent='关闭提示';close.onclick=()=>panel.remove()
  panel.append(message,close);host.prepend(panel)
  const d=error?.details
  let target=null
  if(d?.row>0){
    const result=d.section==='结果展示字段',rows=document.querySelectorAll(result?'#result-fields .schema-row':'#input-fields .schema-row'),row=rows[d.row-1]
    const cls=result?{key:'.result-key',label:'.result-label'}:{key:'.field-key',label:'.field-label',type:'.field-type',options:'.field-options',default:'.field-default'}
    target=row?.querySelector(cls[d.field]||'input')
  }else if(d?.field){target=document.querySelector('[name="'+CSS.escape(d.field)+'"]')}
  if(target){target.classList.add('validation-target');target.setAttribute('aria-invalid','true');target.focus();target.scrollIntoView({behavior:'smooth',block:'center'})}
}
let invalidNoticeQueued=false
document.addEventListener('invalid',event=>{
  event.preventDefault()
  if(invalidNoticeQueued)return
  invalidNoticeQueued=true
  const field=event.target
  queueMicrotask(()=>{
    invalidNoticeQueued=false
    const label=field.closest('label'),labelText=label?[...label.childNodes].filter(n=>n.nodeType===3).map(n=>n.textContent).join('').trim():''
    const name=labelText||field.placeholder||'当前输入项'
    let message='请检查填写格式。'
    if(field.validity.valueMissing)message='尚未填写，请补充这一项。'
    else if(field.validity.rangeUnderflow)message='不能小于 '+field.min+'，请调整数值。'
    else if(field.validity.rangeOverflow)message='不能大于 '+field.max+'，请调整数值。'
    else if(field.validity.typeMismatch)message='格式不正确，请按这一项要求填写。'
    else if(field.validity.stepMismatch)message='小数位数或步长不符合要求，请调整数值。'
    const error=new Error(name+'：'+message);if(field.name)error.details={field:field.name}
    showAdminError(error)
  })
},true)

api=async function(path,options={}){
  const method=options.method||'GET'
  if(method!=='GET')clearAdminError()
  const attempts=method==='GET'?2:1
  let lastError=null
  for(let attempt=0;attempt<attempts;attempt++){
    const controller=new AbortController()
    const timeout=setTimeout(()=>controller.abort(),options.timeout||15000)
    let response
    try{
      response=await fetch(API+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:options.data?JSON.stringify(options.data):undefined,cache:'no-store',credentials:'same-origin',signal:controller.signal})
    }catch(error){
      lastError=error?.name==='AbortError'?new Error(method==='GET'?'读取后台数据超时，请稍后刷新':'提交后等待响应超时。请先刷新核对是否已保存或执行，再决定是否重试。'):new Error('无法连接后台服务，当前操作未能确认完成。请检查网络，恢复后先刷新核对。')
      clearTimeout(timeout)
      if(attempt+1<attempts){await wait(500);continue}
      throw lastError
    }
    let raw=''
    try{raw=await response.text()}catch(error){
      lastError=new Error('后台响应读取失败，请稍后重试')
      clearTimeout(timeout)
      if(attempt+1<attempts){await wait(500);continue}
      throw lastError
    }
    clearTimeout(timeout)
    if([502,503,504].includes(response.status)&&attempt+1<attempts){await wait(500);continue}
    let body=null
    try{body=raw?JSON.parse(raw):null}catch(error){}
    if(response.status===401&&path!='/login'){logout();throw new Error('登录已失效，请重新登录')}
    if(response.status===428||body?.code===428){
      localStorage.setItem('admin_must_change','1')
      const error=new Error(body?.message||'首次登录必须先修改默认密码')
      error.passwordRequired=true
      throw error
    }
    if(!body){
      if(response.status>=500)throw new Error(`后台服务发生错误（HTTP ${response.status}），请先刷新核对当前数据，若仍失败，请在服务器检查此操作的错误记录`)
      throw new Error(`后台没有返回可读取的操作结果（HTTP ${response.status}），请刷新后核对数据`)
    }
    if(!response.ok||body.code!==0){const error=new Error(body.message||`当前操作未完成（HTTP ${response.status}），请检查填写内容或稍后刷新`);error.details=body.data;showAdminError(error);throw error}
    return body.data
  }
  throw lastError||new Error('请求失败，请稍后重试')
}

window.addEventListener('unhandledrejection',event=>{
  event.preventDefault()
  if(event.reason?.passwordRequired){showForcedPassword();return}
  showAdminError(event.reason)
  toast(event.reason?.message||'当前操作未完成，请查看页面内的详细说明')
})
