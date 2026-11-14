// 隔离数据库+本机网关替身；严禁改为正式服务地址。
const a=require('./audit-common.cjs');
const {assert}=a;
const out={startedAt:new Date().toISOString(),cases:[]};
const save=()=>a.fs.writeFileSync(a.path.join(a.runtime,'fixes-api-results.json'),JSON.stringify(out,null,2));
async function test(name,fn){try{out.cases.push({name,passed:true,detail:await fn()});console.log('PASS '+name)}catch(e){out.cases.push({name,passed:false,error:e.message});console.log('FAIL '+name+' '+e.message)}save()}
(async()=>{
 a.sql('delete from ci_admin_login_attempt');
 const token=await a.adminLogin();
 await test('设置错误说明具体字段，非法请求不保存',async()=>{
  const cases=[
   ['/admin-api/email-settings',{smtp_host:'x'.repeat(201)},/SMTP服务器地址/],
   ['/admin-api/email-settings',{smtp_port:0},/SMTP端口/],
   ['/admin-api/payment-settings',{wechat_app_id:'invalid'},/微信 AppID/],
   ['/admin-api/announcements',{title:'',content:'LOCAL'},/公告标题/]
  ];
  const messages=[];
  for(const [url,data,pattern]of cases){const r=await a.raw(url,{token,method:'POST',data});assert.equal(r.status,422);assert.match(r.body.message,pattern);messages.push(r.body.message)}
  return {messages};
 });
 await test('订单CSV明确区分配置估算成本与实际扣款',async()=>{
  const r=await a.raw('/admin-api/analytics/export?start=2026-11-04&end=2026-11-04&type=orders',{token});assert.equal(r.status,200);
  const header=r.body.split('\n')[0];assert.match(header,/配置接口成本（估算，非实际扣款）/);return {header};
 });
 await test('只有具备余额能力的供应商刷新自己的余额',async()=>{
  // 当前测试服务器 ProviderService 被 @Primary 假网关完全替代，绝不连接汽修宝。
  const before=a.jsonSql('select JSON_OBJECT("status",status,"enabled",balance_enabled) from ci_provider where id=1');
  try{
   a.sql('update ci_provider set status=1,balance_enabled=1 where id=1');
   a.sql("delete from ci_provider_balance_snapshot where provider_id=1 and source='manual'");
   const r=await a.api('/admin-api/providers/1/balance/refresh',{token,method:'POST',data:{}});assert.equal(r.status,'success');assert.equal(Number(r.balance),123.45);
   const unsupported=await a.api('/admin-api/providers/2/balance/refresh',{token,method:'POST',data:{}});assert.equal(unsupported.status,'unsupported');return {supported:{provider_id:r.provider_id,balance:r.balance},unsupported:unsupported.status};
  }finally{a.sql('update ci_provider set status='+before.status+',balance_enabled='+before.enabled+' where id=1')}
 });
 await test('五次并发密码错误仍触发锁定，正确密码不能绕过',async()=>{
  const config=a.server();a.sql('delete from ci_admin_login_attempt');
  const wrong=await Promise.all(Array.from({length:5},()=>a.raw('/admin-api/login',{method:'POST',data:{username:config.adminUsername,password:'AUDIT-INCORRECT-PASSWORD'}})));
  assert.ok(wrong.every(r=>r.status===401));
  const row=a.jsonSql('select JSON_OBJECT("count",fail_count,"locked",locked_until>now()) from ci_admin_login_attempt limit 1');assert.equal(row.count,5);assert.equal(Boolean(row.locked),true);
  const correct=await a.raw('/admin-api/login',{method:'POST',data:{username:config.adminUsername,password:config.adminPassword}});assert.equal(correct.status,429);
  a.sql('update ci_admin_login_attempt set locked_until=date_sub(now(),interval 1 minute)');
  const expired=await a.raw('/admin-api/login',{method:'POST',data:{username:config.adminUsername,password:config.adminPassword}});assert.equal(expired.status,200);return {wrong:wrong.map(r=>r.status),failCount:row.count,whileLocked:correct.status,afterExpiry:expired.status};
 });
 out.finishedAt=new Date().toISOString();save();if(out.cases.some(x=>!x.passed))process.exitCode=1;
})().catch(e=>{out.fatal=e.message;save();console.error(e);process.exitCode=1});
