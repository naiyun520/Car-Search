// 实际页面 + 隔离后端数据，只检查读数、筛选与导航，不下单、不付款、不请求供应商。
const a=require('./audit-common.cjs');
const {assert,sleep}=a;
const directory=a.path.join(a.root,'.local/ui-refinement-20261105');a.fs.mkdirSync(directory,{recursive:true});
const result={startedAt:new Date().toISOString(),cases:[],errors:[]};
const save=()=>a.fs.writeFileSync(a.path.join(directory,'checks.json'),JSON.stringify(result,null,2));
(async()=>{
 const token=await a.adminLogin(),tab=await fetch('http://127.0.0.1:19333/json/new?http://127.0.0.1:18881/localqa/',{method:'PUT'}).then(r=>r.json());
 a.fs.writeFileSync(a.path.join(directory,'tab.json'),JSON.stringify(tab));
 const ws=new WebSocket(tab.webSocketDebuggerUrl),pending=new Map();let serial=0;
 ws.onmessage=e=>{const r=JSON.parse(e.data);if(r.method==='Runtime.exceptionThrown')result.errors.push(r.params.exceptionDetails.exception?.description||r.params.exceptionDetails.text);if(r.id){pending.get(r.id)?.(r);pending.delete(r.id)}};
 await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject});
 const cmd=(method,params={})=>new Promise(resolve=>{const id=++serial;pending.set(id,resolve);ws.send(JSON.stringify({id,method,params}))});
 const ev=async expression=>{const r=await cmd('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.result?.exceptionDetails)throw Error(r.result.exceptionDetails.exception?.description||'Browser evaluation failed');return r.result?.result?.value};
 const wait=async expression=>{const deadline=Date.now()+20000;while(Date.now()<deadline){if(await ev(expression))return;await sleep(100)}throw Error('Timeout '+expression)};
 const click=selector=>ev('document.querySelector('+JSON.stringify(selector)+').click();true');
 async function test(name,fn){try{result.cases.push({name,passed:true,detail:await fn()});console.log('PASS '+name)}catch(e){result.cases.push({name,passed:false,error:e.message});console.log('FAIL '+name+' '+e.message)}save()}
 await cmd('Runtime.enable');await cmd('Page.enable');await cmd('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
 await wait('document.readyState==="complete"');await ev('localStorage.setItem("admin_token",'+JSON.stringify(token)+');localStorage.setItem("admin_must_change","0");location.reload();true');
 await wait('document.querySelectorAll(".dashboard-stats .stat").length===6');
 await test('删除口号与说明板块，保留六项指标和四张图表',async()=>{
  assert.equal(await ev('!!document.querySelector(".board-hero,.reading-notes,.board-footnote")'),false);
  assert.equal(await ev('/每一笔查询，都有迹可循|读懂这份看板/.test(document.body.textContent)'),false);
  assert.equal(await ev('chartInstances.length'),4);assert.equal(await ev('document.querySelectorAll("#export-kind option").length'),4);
 });
 await test('关键指标与实际统计接口逐项一致',async()=>{
  const actual=await ev('api("/analytics?"+new URLSearchParams(dashboardRenderedRange))');
  const keys=['gross','net','profit','paid_orders','orders','payment_conversion'];
  for(let i=0;i<keys.length;i++){const expected=await ev('valueText('+JSON.stringify(keys[i])+','+JSON.stringify(actual.current[keys[i]])+')');assert.equal(await ev('document.querySelectorAll(".metric-card>b")['+i+'].textContent'),expected)}
 });
 await test('业务表切换区分下单日期与收支日期',async()=>{
  assert.equal(await ev('document.querySelector("#service-scope").textContent'),'按下单日期');
  await click('[data-service-view=money]');assert.equal(await ev('document.querySelector("#service-scope").textContent'),'按收支日期');
  assert.ok(await ev('document.querySelector("#service-table").textContent.includes("估算接口成本")'));
  await click('[data-service-view=orders]');assert.equal(await ev('document.querySelector("#service-scope").textContent'),'按下单日期');
 });
 await test('日期筛选与刷新后没有重复图表，保留当前视图',async()=>{
  await click('[data-range=today]');await wait('dashboardRenderedRange.start===dashboardRenderedRange.end&&document.querySelector("[data-range=today]").getAttribute("aria-pressed")==="true"');
  await click('[data-service-view=money]');await click('[data-refresh-dashboard]');await wait('document.querySelector("[data-refresh-dashboard]")&&!document.querySelector("[data-refresh-dashboard]").disabled');
  assert.equal(await ev('chartInstances.length'),4);assert.equal(await ev('document.querySelector("#service-scope").textContent'),'按收支日期');
  await click('[data-range=week]');await wait('dashboardRenderedRange.start!==dashboardRenderedRange.end');
 });
 await test('四类报表接口均可导出',async()=>{
  const range=await ev('dashboardRenderedRange');
  for(const type of ['daily','services','orders','calls']){const r=await a.raw('/admin-api/analytics/export?'+new URLSearchParams({...range,type}),{token});assert.equal(r.status,200);assert.match(r.headers['content-type'],/text\/csv/);assert.ok(r.body.length>10)}
 });
 await test('桌面、平板及窄屏没有整页横向溢出，主要文字不小于14px',async()=>{
  const values=[];
  for(const width of [1920,1440,1024,390]){
   await cmd('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:width<500});await sleep(120);
   const check=await ev('({page:document.body.scrollWidth,viewport:innerWidth,minimum:Math.min(...Array.from(document.querySelectorAll(".operations-dashboard th,.operations-dashboard td,.metric-top,.metric-comparison,.section-scope")).map(x=>parseFloat(getComputedStyle(x).fontSize)))})');
   assert.ok(check.page<=check.viewport,JSON.stringify({width,...check}));assert.ok(check.minimum>=14);values.push({width,...check});
  }
  return values;
 });
 await cmd('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await cmd('Page.navigate',{url:'http://127.0.0.1:18770/'});
 await wait('document.querySelectorAll(".service-item").length>0');
 await test('服务图标在卡片内居中，卡片边框和查询按钮可用',async()=>{
  const values=await ev('Array.from(document.querySelectorAll(".service-item")).map(c=>{const a=c.getBoundingClientRect(),b=c.querySelector(".service-icon").getBoundingClientRect(),s=getComputedStyle(c),button=c.querySelector("button.service-action");return {offset:Math.abs(a.left+a.width/2-b.left-b.width/2),border:parseFloat(s.borderLeftWidth),text:button?.textContent.trim(),disabled:button?.disabled}})');
  assert.ok(values.every(x=>x.offset<1&&x.border>0));assert.ok(values.every(x=>x.text==='立即查询'||x.text==='维护中'));assert.ok(values.some(x=>!x.disabled));return {cards:values.length,maximumIconOffset:Math.max(...values.map(x=>x.offset))};
 });
 await test('点击查询按钮只跳转一次，未产生下单或付款',async()=>{
  await ev('window.uiNavigationCount=0;window.uiOriginalNavigate=navigate;window.navigate=url=>{uiNavigationCount++;return uiOriginalNavigate(url)};true');
  await click('.service-action:not(:disabled)');await wait('window.auditRoute==="/pages/query/query"&&document.querySelector(".query-title")');assert.equal(await ev('uiNavigationCount'),1);assert.equal(await ev('auditNativePayments'),0);
 });
 await test('维护业务按钮禁用，点击卡片仅提示维护',async()=>{
  await ev('window.uiOriginalFetch=fetch;window.fetch=async(...args)=>{const r=await uiOriginalFetch(...args);if(String(args[0]).includes("/api/v1/bootstrap")){const d=await r.json();d.data.services[0].status=0;return new Response(JSON.stringify(d),{status:200,headers:{"Content-Type":"application/json"}})}return r};navigate("/pages/index/index");true');
  await wait('document.querySelector(".service-action-disabled")');const before=await ev('uiNavigationCount');await click('.service-action-disabled');assert.equal(await ev('uiNavigationCount'),before);
  await ev('document.querySelector(".service-action-disabled").closest(".service-item").click();true');await wait('auditLog.some(x=>x.title?.includes("维护中"))');assert.equal(await ev('uiNavigationCount'),before);await ev('window.fetch=uiOriginalFetch;true');
 });
 await test('页面无未处理脚本异常',async()=>{assert.deepEqual(result.errors,[]);assert.deepEqual(await ev('auditErrors'),[])});
 result.finishedAt=new Date().toISOString();save();ws.close();console.log(JSON.stringify({passed:result.cases.filter(x=>x.passed).length,total:result.cases.length}));if(result.cases.some(x=>!x.passed))process.exitCode=1;
})().catch(e=>{result.fatal=e.message;save();console.error(e.message);process.exitCode=1});
