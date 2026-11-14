const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process'),http=require('node:http'),net=require('node:net'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),runtime=path.join(root,'.local/fulfillment-test'),database='car_search_unified_test',base='http://127.0.0.1:18081';
function sql(statement){return cp.execFileSync('E:/mySQL/bin/mysql.exe',['--no-defaults','--host=127.0.0.1','--port=13316','--user=root','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names',database],{input:statement,encoding:'utf8',maxBuffer:16e6}).trim();}
const q=value=>value==null?'NULL':"'"+String(value).replaceAll('\\','\\\\').replaceAll("'","''")+"'";
const load=()=>JSON.parse(fs.readFileSync(path.join(runtime,'runtime.json'),'utf8'));
function dumpRows(table){
 const filename=fs.readdirSync(path.join(root,'测试数据')).find(name=>name.endsWith('.sql'));
 const text=fs.readFileSync(path.join(root,'测试数据',filename),'utf8');
 const create=text.match(new RegExp('CREATE TABLE `'+table+'`\\s*\\(([\\s\\S]*?)\\n\\)'));
 if(!create)throw Error('Original table schema missing');
 const columns=[...create[1].matchAll(/^\s*`([^`]+)`/gm)].map(m=>m[1]),rows=[];
 for(const line of text.split(/\r?\n/)){
  const prefix='INSERT INTO `'+table+'` VALUES ';if(!line.startsWith(prefix))continue;
  let quoted=false,escaped=false,active=false,field='',row=[];
  for(const char of line.slice(prefix.length)){
   if(quoted){if(escaped){field+=({n:'\n',r:'\r',t:'\t','0':'\0'})[char]??char;escaped=false}else if(char==='\\')escaped=true;else if(char==="'")quoted=false;else field+=char;}
   else if(char==="'")quoted=true;
   else if(char==='('){active=true;row=[];field='';}
   else if(active&&(char===','||char===')')){row.push(field==='NULL'?null:field);field='';if(char===')'){rows.push(Object.fromEntries(columns.map((c,i)=>[c,row[i]])));active=false;}}
   else if(active)field+=char;
  }
 }
 return rows;
}
function encrypt(value,key){
 const iv=crypto.randomBytes(12),cipher=crypto.createCipheriv('aes-256-gcm',crypto.createHash('sha256').update(key).digest(),iv),body=Buffer.concat([cipher.update(JSON.stringify(value),'utf8'),cipher.final()]);
 return Buffer.concat([iv,cipher.getAuthTag(),body]).toString('base64');
}
function prepare(){
 const state=load(),unpaid=Number(sql("select count(*) from ci_order where status='pending_payment'"));
 const original=dumpRows('ci_service');if(original.length!==31)throw Error('Unexpected original service count: '+original.length);
 const fakeId=Number(sql("select id from ci_provider where code='test' and base_url='http://testapi.naiblog.cn/'"));if(fakeId!==2)throw Error('Existing fake supplier mismatch');
 const fields=['provider_api_id','input_schema','result_schema','api_path','query_mode','fetch_api_path','poll_interval_sec','poll_timeout_sec','poll_max_attempts','api_version','fetch_api_version','callback_mode','callback_secret','callback_timeout_sec','id_field_path','fetch_api_param_name','request_method','status'];
 const statements=["START TRANSACTION",
  "delete r from ci_order_result_revision r join ci_order o on o.id=r.order_id join ci_user u on u.id=o.user_id where o.client_platform='test' and u.openid='local-interface-test'",
  ...['ci_provider_call','ci_query_job','ci_email_delivery','ci_payment'].map(table=>"delete t from "+table+" t join ci_order o on o.id=t.order_id join ci_user u on u.id=o.user_id where o.client_platform='test' and u.openid='local-interface-test'"),
  "delete o from ci_order o join ci_user u on u.id=o.user_id where o.client_platform='test' and u.openid='local-interface-test'",
  "delete from ci_callback_inbox where service_id in(select id from ci_service where code like 'local_test_%')",
  "delete from ci_service where code like 'local_test_%' and provider_api_id=(select id from ci_provider where code='local_fixture')",
  "delete from ci_provider where code='local_fixture'"];
 for(const service of original){
  const values=fields.map(key=>key+'='+q(key==='provider_api_id'&&service[key]==='1'?fakeId:service[key]));
  statements.push('update ci_service set '+values.join(',')+' where id='+Number(service.id)+' and code='+q(service.code));
 }
 statements.push("update ci_provider set status=1,balance_enabled=0 where id=2","update ci_provider set status=0,balance_enabled=0 where id<>2","COMMIT");
 sql(statements.join(';')+';');
 assert.equal(Number(sql("select count(*) from ci_order where status='pending_payment'")),unpaid);
 assert.equal(Number(sql("select count(*) from ci_service")),original.length);
 state.ids=undefined;
 const token=crypto.randomBytes(32).toString('hex'),userToken=crypto.randomBytes(32).toString('hex'),hash=v=>crypto.createHash('sha256').update(v).digest('hex');
 sql("insert into ci_admin_token(admin_id,token_hash,expires_at,created_at) select id,"+q(hash(token))+",DATE_ADD(NOW(),INTERVAL 8 HOUR),NOW() from ci_admin where status=1 order by id limit 1;");
 sql("update ci_user set session_key_cipher="+q(encrypt({value:'LOCAL-TEST-SESSION'},state.key))+" where openid='local-interface-test';");
 sql("insert into ci_user_token(user_id,token_hash,expires_at,created_at) select id,"+q(hash(userToken))+",DATE_ADD(NOW(),INTERVAL 2 HOUR),NOW() from ci_user where openid='local-interface-test';");
 state.adminToken=token;state.userToken=userToken;state.originalUnpaidCount=unpaid;
 fs.writeFileSync(path.join(runtime,'runtime.json'),JSON.stringify(state));
 console.log('Local copy only: restored '+original.length+' original services; former qxb services now use existing test supplier; synthetic services/provider removed; '+unpaid+' unpaid orders preserved.');
}
function serveMail(){
 net.createServer(socket=>{
  let buffer='',data=false;socket.write('220 localhost test SMTP\r\n');
  socket.on('data',chunk=>{
   buffer+=chunk;
   while(buffer.includes('\n')){
    const index=buffer.indexOf('\n'),line=buffer.slice(0,index).replace(/\r$/,'');buffer=buffer.slice(index+1);
    if(data){if(line==='.'){data=false;socket.write('250 accepted\r\n');}continue;}
    if(/^DATA/i.test(line)){data=true;socket.write('354 end with dot\r\n');}
    else if(/^QUIT/i.test(line)){socket.end('221 bye\r\n');}
    else if(/^EHLO|^HELO/i.test(line))socket.write('250 localhost\r\n');
    else socket.write('250 ok\r\n');
   }
  });
 }).listen(11025,'127.0.0.1',()=>console.log('Local mail sink ready; no supplier server is created.'));
}
async function verify(){
 const state=load(),outcomes=[],services=JSON.parse(sql("select JSON_ARRAYAGG(JSON_OBJECT('id',id,'name',name,'api_path',api_path,'input_schema',input_schema,'query_mode',query_mode)) from ci_service where provider_api_id=2 and id in(14,27,29,30,31,32)"));
 async function api(url,options={}){
  const response=await fetch(base+url,{...options,headers:{Authorization:'Bearer '+state.adminToken,'Content-Type':'application/json',...options.headers}});
  const body=await response.json();if(!response.ok||body.code!==0)throw Error(body.message||'HTTP '+response.status);return body.data;
 }
 const started=Date.now();
 for(const service of services){
  const input={};for(const field of (Array.isArray(service.input_schema)?service.input_schema:JSON.parse(service.input_schema||'[]'))){
   input[field.key]=field.type==='select'?(field.default??field.options?.[0]?.value??'1'):field.type==='image'?'https://example.invalid/local-fixture.png':/vin/i.test(field.key)?'LTESTA12345678901':/car_no|plate|chepai/i.test(field.key)?'粤A00000':/idcard|identity/i.test(field.type)?'110101199001010010':/name/i.test(field.key)?'测试用户':'LOCAL-TEST';
  }
  try{
   const result=await api('/admin-api/service-test/'+service.id,{method:'POST',body:JSON.stringify({input})});
   outcomes.push({serviceId:service.id,name:service.name,orderNo:result.order_no,submittedAt:Date.now()});
  }catch(error){outcomes.push({serviceId:service.id,name:service.name,error:error.message});}
 }
 assert.ok(outcomes.some(item=>item.orderNo),'No test orders were accepted');
 while(outcomes.some(item=>item.orderNo&&!item.status)&&Date.now()-started<150000){
  for(const item of outcomes.filter(item=>item.orderNo&&!item.status)){
   const row=JSON.parse(sql("select JSON_OBJECT('status',status,'error',query_last_error) from ci_order where order_no="+q(item.orderNo)));
   if(!['paid','querying'].includes(row.status)){item.status=row.status;item.error=row.error;}
  }
  if(outcomes.some(item=>item.orderNo&&!item.status))await new Promise(resolve=>setTimeout(resolve,1000));
 }
 for(const item of outcomes.filter(item=>item.orderNo)){
  item.diagnosis=await api('/admin-api/orders/'+item.orderNo+'/fulfillment');
  const task=item.diagnosis.task,calls=item.diagnosis.calls||[];
  assert.equal(task.job_type,item.serviceId===14?'direct':'async');
  assert.ok(task.submit_attempts<=1);
  const submits=calls.filter(call=>call.stage==='SUBMIT'),fetches=calls.filter(call=>call.stage==='FETCH');
  if(fetches.length&&submits.length)assert.ok(new Date(String(fetches.at(-1).started_at).replace(/\[.*\]$/,''))-new Date(String(submits[0].finished_at).replace(/\[.*\]$/,''))>=59000,'First fetch must wait >=60 seconds');
  item.mailCount=Number(sql("select count(*) from ci_email_delivery where order_id=(select id from ci_order where order_no="+q(item.orderNo)+") and event_type in('query_success','query_failed')"));
  if(['success','query_failed'].includes(item.status))assert.ok(item.mailCount>=1,'Terminal query notification must exist');
  item.fetchCount=task.fetch_attempts;
  delete item.diagnosis;
 }
 assert.equal(Number(sql("select count(*) from ci_order where status='pending_payment'")),state.originalUnpaidCount);
 assert.equal(Number(sql("select count(*) from ci_service where code like 'local_test_%'")),0);
 for(const route of ['/api/callback/provider/30','/admin-api/callback-simulate/30']){
  const response=await fetch(base+route,{method:'POST',headers:{Authorization:'Bearer '+state.adminToken,'Content-Type':'application/json'},body:'{}'});
  assert.equal(response.status,404,'Removed supplier callback route must not be available');
 }
 fs.writeFileSync(path.join(runtime,'20261104-integration.json'),JSON.stringify(outcomes,null,2));
 console.log(JSON.stringify({elapsedSeconds:Math.round((Date.now()-started)/1000),outcomes},null,2));
}
(async()=>{
 switch(process.argv[2]){case 'prepare':prepare();break;case 'serve-mail':serveMail();break;case 'verify':await verify();break;default:throw Error('Use prepare (local cleanup), serve-mail, or verify. No fixture supplier/services are created.');}
})().catch(error=>{console.error(error.message);process.exitCode=1;});
