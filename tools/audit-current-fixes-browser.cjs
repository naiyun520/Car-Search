// 仅连接隔离审计服务；不访问真实供应商、支付和邮件。
const a=require('./audit-common.cjs');
const {assert,sleep}=a;
const result={startedAt:new Date().toISOString(),cases:[],errors:[],mode:'实际后台和 Vue 源码浏览器验证；不代替微信真机验收'};
const save=()=>a.fs.writeFileSync(a.path.join(a.runtime,'fixes-browser-results.json'),JSON.stringify(result,null,2));
(async()=>{
 const token=await a.adminLogin();
 const tab=await fetch('http://127.0.0.1:19333/json/new?http://127.0.0.1:18082/localqa/',{method:'PUT'}).then(r=>r.json());
 a.fs.writeFileSync(a.path.join(a.runtime,'fixes-browser-tab.json'),JSON.stringify(tab));
 const ws=new WebSocket(tab.webSocketDebuggerUrl),pending=new Map();let seq=0;
 ws.onmessage=e=>{const r=JSON.parse(e.data);if(r.method==='Runtime.exceptionThrown')result.errors.push(r.params.exceptionDetails.exception?.description||r.params.exceptionDetails.text);if(r.id){pending.get(r.id)?.(r);pending.delete(r.id)}};
 await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j});
 const cmd=(method,params={})=>new Promise(resolve=>{const id=++seq;pending.set(id,resolve);ws.send(JSON.stringify({id,method,params}))});
 const ev=async expression=>{const r=await cmd('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.result?.exceptionDetails)throw Error(r.result.exceptionDetails.exception?.description||'Browser evaluation failed');return r.result?.result?.value};
 const wait=async expression=>{const t=Date.now();while(Date.now()-t<20000){if(await ev(expression))return;await sleep(100)}throw Error('Timeout '+expression)};
 const click=s=>ev('document.querySelector('+JSON.stringify(s)+').click();true');
 const set=(s,v)=>ev('document.querySelector('+JSON.stringify(s)+').value='+JSON.stringify(v)+';true');
 const snap=async name=>{const r=await cmd('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});a.fs.writeFileSync(a.path.join(a.runtime,name+'.png'),Buffer.from(r.result.data,'base64'))};
 async function test(name,fn){try{const detail=await fn();result.cases.push({name,passed:true,detail});console.log('PASS '+name)}catch(e){result.cases.push({name,passed:false,error:e.message});console.log('FAIL '+name+' '+e.message)}save()}
 await cmd('Runtime.enable');await cmd('Page.enable');await cmd('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
 await wait('document.readyState==="complete"');
 await ev('localStorage.setItem("admin_token",'+JSON.stringify(token)+');localStorage.setItem("admin_must_change","0");location.reload();true');
 await wait('document.querySelectorAll(".dashboard-stats .stat").length===6');
 await test('历史里程业务原配置不修改直接保存',async()=>{
  await click('[data-page=services]');await wait('document.querySelector(".edit-service[data-id=\\"29\\"]")');
  await click('.edit-service[data-id="29"]');await wait('document.querySelector("#service-form")');
  await click('.save-service');await wait('!document.querySelector("#service-form")');
  const schema=JSON.parse(a.sql('select result_schema from ci_service where id=29'));
  assert.ok(Object.hasOwn(schema,'content[]'));assert.ok(Object.hasOwn(schema,'content[].mileage'));
  return {arrayPath:'content[]',mileagePath:'content[].mileage'};
 });
 await test('重复结果路径提示具体行、重复对象并定位输入框',async()=>{
  await click('.edit-service[data-id="29"]');await wait('document.querySelector("#service-form")');
  await ev('document.querySelectorAll(".result-key")[1].value=document.querySelectorAll(".result-key")[0].value;true');
  await click('.save-service');await wait('document.querySelector(".admin-error-panel")');
  const text=await ev('document.querySelector(".admin-error-panel").textContent');assert.match(text,/第2行/);assert.match(text,/第1行/);
  assert.equal(await ev('document.querySelectorAll(".result-key")[1].classList.contains("validation-target")'),true);
  await snap('fixes-admin-duplicate');return {message:text};
 });
 await test('空展示名称提示结果字段具体行和修改方法',async()=>{
  await ev('openServiceEditor(serviceRows.find(x=>x.id===29));true');
  await set('.result-label','');await click('.save-service');await wait('document.querySelector(".admin-error-panel")');
  const text=await ev('document.querySelector(".admin-error-panel").textContent');assert.match(text,/第1行/);assert.match(text,/展示名称/);
  assert.equal(await ev('document.querySelector(".result-label").getAttribute("aria-invalid")'),'true');return {message:text};
 });
 await test('下拉选项格式错误定位到该行选项',async()=>{
  await ev('openServiceEditor(serviceRows.find(x=>x.id===29));true');
  await set('.field-type','select');await ev('document.querySelector(".field-type").dispatchEvent(new Event("change",{bubbles:true}));true');
  await set('.field-options','not-json');await click('.save-service');await wait('document.querySelector(".field-options.validation-target")');
  assert.match(await ev('document.querySelector(".admin-error-panel").textContent'),/第1行/);
  await click('.drawer-cancel');
 });
 await test('未提供余额接口的供应商无刷新入口且不写失败快照',async()=>{
  await click('[data-page=providers]');await wait('document.querySelector(".edit-provider[data-id=\\"2\\"]")');
  const row=await ev('document.querySelector(".edit-provider[data-id=\\"2\\"]").closest("tr").textContent');assert.match(row,/未提供余额接口/);
  assert.equal(await ev('!!document.querySelector(".refresh-balance[data-id=\\"2\\"]")'),false);
  const before=a.sql('select count(*) from ci_provider_balance_snapshot where provider_id=2');
  const response=await a.api('/admin-api/providers/2/balance/refresh',{token,method:'POST',data:{}});assert.equal(response.status,'unsupported');
  assert.equal(a.sql('select count(*) from ci_provider_balance_snapshot where provider_id=2'),before);return response;
 });
 await cmd('Page.navigate',{url:'http://127.0.0.1:18770/?page=/pages/result/result%3Fdemo%3Dbattery'});
 await test('报告双列和长建议在 375/390/414 宽度内完整布局',async()=>{
  await wait('document.querySelector(".field-grid")');await ev('document.querySelector("#audit-status").hidden=true;true');
  const sizes=[];
  for(const width of [375,390,414]){
   await cmd('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:true});await sleep(200);
   const grids=await ev('Array.from(document.querySelectorAll(".field-grid")).map(g=>{const b=g.getBoundingClientRect();return {width:b.width,items:Array.from(g.children).map(c=>{const r=c.getBoundingClientRect();return {text:c.textContent,left:r.left-b.left,right:r.right-b.left,top:r.top-b.top,width:r.width,wide:c.classList.contains("field-cell-wide")}})}})');
   assert.ok(grids.length>=2);
   for(const g of grids){assert.ok(g.items.every(c=>c.right<=g.width+1));assert.ok(g.items.some(c=>c.wide||c.left>g.width*.4),'Empty right-hand column');}
   const all=grids.flatMap(g=>g.items.map(i=>({...i,parentWidth:g.width}))),advice=all.find(i=>i.text.includes('充放电建议'));
   assert.ok(advice);assert.ok(advice.width>=advice.parentWidth-1);assert.ok(all.some((i,index)=>!i.wide&&all.some((j,n)=>n!==index&&!j.wide&&Math.abs(j.top-i.top)<1&&j.left>i.left)));
   assert.equal(await ev('document.body.scrollWidth<=innerWidth'),true);sizes.push({width,gridWidths:grids.map(g=>g.width),adviceWidth:advice.width});
   if(width===390){await snap('fixes-report-top');await ev('Array.from(document.querySelectorAll(".field-label")).find(x=>x.textContent.includes("充放电建议")).closest(".block").scrollIntoView();true');await sleep(150);await snap('fixes-report-charging');}
  }
  return sizes;
 });
 await test('无浏览器未处理异常',async()=>{assert.deepEqual(result.errors,[]);assert.deepEqual(await ev('window.auditErrors||[]'),[])});
 result.finishedAt=new Date().toISOString();save();ws.close();console.log(JSON.stringify({passed:result.cases.filter(x=>x.passed).length,total:result.cases.length}));if(result.cases.some(x=>!x.passed))process.exitCode=1;
})().catch(e=>{result.fatal=e.message;save();console.error(e);process.exit(1)});
