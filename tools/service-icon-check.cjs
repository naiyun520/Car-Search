const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),runtime=path.join(root,'.local/fulfillment-test'),base='http://127.0.0.1:18081';
const cfg=JSON.parse(fs.readFileSync(path.join(runtime,'runtime.json'),'utf8'));
const sql=s=>cp.execFileSync('E:/mySQL/bin/mysql.exe',['--no-defaults','--host=127.0.0.1','--port=13316','--user=root','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','car_search_unified_test'],{input:s,encoding:'utf8'}).trim();
const q=s=>"'"+String(s).replaceAll('\\','\\\\').replaceAll("'","''")+"'";
const headers={Authorization:'Bearer '+cfg.adminToken,'Content-Type':'application/json'};
async function api(url,options={}){
 const response=await fetch(base+url,{...options,headers:{...headers,...options.headers}});
 const body=await response.json();if(!response.ok||body.code!==0)throw Error(body.message||'HTTP '+response.status);return body.data;
}
(async()=>{
 const original=JSON.parse(sql("select JSON_OBJECT('icon',icon,'updated_at',cast(updated_at as char)) from ci_service where id=30"));
 assert.ok(original.icon.startsWith('builtin:'),'Refuse to overwrite an existing uploaded user icon');
 const bytes=fs.readFileSync(path.join(runtime,'service-icon-test.png')),newFiles=new Set();
 const form=()=>{const data=new FormData();data.append('icon',new Blob([bytes],{type:'image/png'}),'fixture.png');return data};
 const unauth=await fetch(base+'/admin-api/service-icon-upload/30',{method:'POST',body:form()});assert.equal(unauth.status,401);
 let socket;
 try{
  const uploadResponse=await fetch(base+'/admin-api/service-icon-upload/30',{method:'POST',headers:{Authorization:headers.Authorization},body:form()});
  const uploaded=await uploadResponse.json();assert.equal(uploadResponse.status,200);assert.equal(uploaded.code,0);
  const first=uploaded.data.icon;newFiles.add(first);
  const image=await fetch(base+uploaded.data.icon_url);assert.equal(image.status,200);assert.equal(image.headers.get('content-type'),'image/png');assert.deepEqual(Buffer.from(await image.arrayBuffer()),bytes);
  const tab=await fetch('http://127.0.0.1:19333/json/new?'+base+'/localqa/',{method:'PUT'}).then(r=>r.json());
  socket=new WebSocket(tab.webSocketDebuggerUrl);const pending=new Map(),errors=[];let serial=0;
  socket.onmessage=event=>{const value=JSON.parse(event.data);if(value.method==='Runtime.exceptionThrown')errors.push(value.params.exceptionDetails.text);if(value.id){pending.get(value.id)?.(value);pending.delete(value.id)}};
  await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
  const cmd=(method,params={})=>new Promise(resolve=>{const id=++serial;pending.set(id,resolve);socket.send(JSON.stringify({id,method,params}))});
  const evaluate=async expression=>{const value=await cmd('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(value.result?.exceptionDetails)throw Error(value.result.exceptionDetails.exception?.description||'Browser error');return value.result?.result?.value};
  const wait=async expression=>{for(let i=0;i<80;i++){if(await evaluate(expression))return;await new Promise(r=>setTimeout(r,100))}throw Error('Browser timeout')};
  await cmd('Runtime.enable');await cmd('Page.enable');await cmd('DOM.enable');
  await wait('document.readyState==="complete"');
  await evaluate('localStorage.setItem("admin_token",'+JSON.stringify(cfg.adminToken)+');localStorage.setItem("admin_must_change","0");location.reload();true');
  await wait('document.querySelectorAll(".dashboard-stats .stat").length===6');
  await evaluate('document.querySelector("[data-page=services]").click();true');
  await wait('serviceRows.length===31');
  await evaluate('openServiceEditor(serviceRows.find(row=>row.id===30));true');
  assert.equal(await evaluate('document.querySelector("[name=builtin_icon]").value'),'');
  const doc=await cmd('DOM.getDocument'),input=await cmd('DOM.querySelector',{nodeId:doc.result.root.nodeId,selector:'input[name=icon_file]'});
  await cmd('DOM.setFileInputFiles',{nodeId:input.result.nodeId,files:[path.join(runtime,'service-icon-test.png')]});
  await evaluate('document.querySelector("#service-form").requestSubmit();true');
  await wait('!document.querySelector("#service-form")');
  const second=JSON.parse(sql("select JSON_OBJECT('icon',icon) from ci_service where id=30")).icon;newFiles.add(second);
  assert.notEqual(second,first);assert.equal((await fetch(base+'/api/v1/service-icon/'+first)).status,404);
  assert.equal((await fetch(base+'/api/v1/service-icon/'+second)).status,200);
  await evaluate('loaders.services().then(()=>{openServiceEditor(serviceRows.find(row=>row.id===30));return true})');
  assert.equal(await evaluate('document.querySelector("[name=builtin_icon]").value'),'');
  await evaluate('document.querySelector("#service-form").requestSubmit();true');await wait('!document.querySelector("#service-form")');
  assert.equal(JSON.parse(sql("select JSON_OBJECT('icon',icon) from ci_service where id=30")).icon,second);
  assert.deepEqual(errors,[]);
  const sort=await fetch(base+'/admin-api/services/sort',{method:'POST',headers,body:'{"ids":[]}'});assert.equal(sort.status,422);
  const deletion=await fetch(base+'/admin-api/services/30',{method:'DELETE',headers});assert.equal(deletion.status,422);
  fs.writeFileSync(path.join(runtime,'service-icon-check.json'),JSON.stringify({passed:true,unauthorizedRejected:true,httpUploadReadable:true,browserReplacementPassed:true,ordinarySavePreservesIcon:true,sortAndDeleteGuards:true,errors},null,2));
  console.log('PASS unauthorized upload rejected, real multipart upload/read, browser file selection/replacement, ordinary save retains uploaded icon, sort and delete validation, no browser errors');
 }finally{
  const current=JSON.parse(sql("select JSON_OBJECT('icon',icon) from ci_service where id=30")).icon;
  if(current!==original.icon&&/^[a-f0-9]{32}\.(png|jpg|webp)$/.test(current))newFiles.add(current);
  sql("update ci_service set icon="+q(original.icon)+",updated_at="+q(original.updated_at)+" where id=30;");
  const directory=path.resolve(runtime,'runtime/uploads/service-icons');
  for(const file of newFiles){
   assert.match(file,/^[a-f0-9]{32}\.(png|jpg|webp)$/);
   const target=path.resolve(directory,file);assert.equal(path.dirname(target),directory);
   if(fs.existsSync(target))fs.unlinkSync(target);
  }
  socket?.close();
 }
})().catch(error=>{console.error(error.message);process.exit(1)});
