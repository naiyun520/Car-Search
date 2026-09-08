const wait=milliseconds=>new Promise(resolve=>setTimeout(resolve,milliseconds))

api=async function(path,options={}){
  const method=options.method||'GET'
  const attempts=method==='GET'?2:1
  let lastError=null
  for(let attempt=0;attempt<attempts;attempt++){
    const controller=new AbortController()
    const timeout=setTimeout(()=>controller.abort(),options.timeout||15000)
    let response
    try{
      response=await fetch(API+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:options.data?JSON.stringify(options.data):undefined,cache:'no-store',credentials:'same-origin',signal:controller.signal})
    }catch(error){
      lastError=error?.name==='AbortError'?new Error('后台响应超时，请稍后重试'):new Error('无法连接后台服务，请检查网络或服务器状态')
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
      if(response.status>=500)throw new Error(`后台服务发生错误（HTTP ${response.status}），请检查 runtime/log 日志`)
      throw new Error(`后台返回格式异常（HTTP ${response.status}）`)
    }
    if(!response.ok||body.code!==0)throw new Error(body.message||`操作失败（HTTP ${response.status}）`)
    return body.data
  }
  throw lastError||new Error('请求失败，请稍后重试')
}

window.addEventListener('unhandledrejection',event=>{
  event.preventDefault()
  if(event.reason?.passwordRequired){showForcedPassword();return}
  toast(event.reason?.message||'操作失败，请稍后重试')
})
