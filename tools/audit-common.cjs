const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),runtime=path.join(root,'.local/audit-20261104'),base='http://127.0.0.1:18082',db='car_search_audit_20261104';
const server=()=>JSON.parse(fs.readFileSync(path.join(runtime,'server.json'),'utf8'));
const sql=statement=>cp.execFileSync('E:/mySQL/bin/mysql.exe',['--no-defaults','--host=127.0.0.1','--port=13316','--user=root','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names',db],{input:statement,encoding:'utf8',maxBuffer:16e6}).trim();
const q=s=>s==null?'NULL':"'"+String(s).replaceAll('\\','\\\\').replaceAll("'","''")+"'";
const jsonSql=statement=>{const value=sql(statement);return value?JSON.parse(value):null};
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function raw(url,{token='',method='GET',data,headers={},timeout=25000}={}){
 const started=performance.now(),response=await fetch(base+url,{method,headers:{'X-Car-Client-Version':'20260921.1',...(token?{Authorization:'Bearer '+token}:{}),...(data!==undefined?{'Content-Type':'application/json'}:{}),...headers},body:data===undefined?undefined:JSON.stringify(data),signal:AbortSignal.timeout(timeout)});
 const text=await response.text();let body;try{body=JSON.parse(text)}catch{body=text}
 return {status:response.status,body,ms:performance.now()-started,bytes:Buffer.byteLength(text),headers:Object.fromEntries(response.headers)};
}
async function api(url,options){const response=await raw(url,options);if(response.status<200||response.status>=300||response.body.code!==0)throw Object.assign(Error(response.body.message||'HTTP '+response.status),response);return response.body.data}
const control=(url,data)=>raw('/__audit'+url,{method:'POST',data});
async function adminLogin(){const c=server();return (await api('/admin-api/login',{method:'POST',data:{username:c.adminUsername,password:c.adminPassword}})).token}
async function userLogin(code){return api('/api/v1/auth/login',{method:'POST',data:{code}})}
function inputFor(service,n=0){
 let schema=service.input_schema;if(typeof schema==='string')schema=JSON.parse(schema);
 const input={};
 for(const field of schema||[]){
  input[field.key]=field.type==='select'?(field.default??field.options?.[0]?.value??'1'):
   field.type==='image'?'https://example.invalid/audit.png':
   field.type==='vin'||/vin/i.test(field.key)?'LTESTA'+String(n).padStart(11,'0'):
   ['plate','plate_prefix','plate_or_vin'].includes(field.type)||/car_no|chepai/i.test(field.key)?'粤A00000':
   ['idcard','identity'].includes(field.type)?'110101199001010010':
   field.type==='name'||/name/i.test(field.key)?'本地测试用户':'LOCAL-AUDIT';
 }
 return input;
}
async function createOrder(token,service,n,{key,accepted=true,platform='android',input}={}){
 const result=await api('/api/v1/checkout/create',{token,method:'POST',data:{service_code:service.code,request_key:key||'AUDIT_'+Date.now()+'_'+n+'_'+Math.random().toString(16).slice(2),accepted,client_platform:platform,input:input||inputFor(service,n)}});return result.order;
}
async function payAndConfirm(token,no,override={}){await control('/pay/'+no,override);return api('/api/v1/checkout/confirm',{token,method:'POST',data:{order_no:no}})}
async function waitOrder(no,states=['success','query_failed'],timeout=140000){
 const started=Date.now();while(Date.now()-started<timeout){const row=jsonSql('select JSON_OBJECT("status",status,"error",query_last_error,"id",id) from ci_order where order_no='+q(no));if(states.includes(row.status))return row;await sleep(250)}throw Error('Order timed out in test '+no);
}
async function parallel(items,fn,limit=100){let index=0;const result=Array(items.length);await Promise.all(Array.from({length:Math.min(limit,items.length)},async()=>{while(index<items.length){const i=index++;try{result[i]=await fn(items[i],i)}catch(error){result[i]={error:error.message,status:error.status}}}}));return result}
const percentile=(values,p)=>{const a=[...values].sort((x,y)=>x-y);return a.length?a[Math.min(a.length-1,Math.ceil(a.length*p)-1)]:0};
const summary=responses=>({count:responses.length,errors:responses.filter(r=>r.error||r.status>=400).length,p50ms:+percentile(responses.map(r=>r.ms||0),.5).toFixed(2),p95ms:+percentile(responses.map(r=>r.ms||0),.95).toFixed(2),p99ms:+percentile(responses.map(r=>r.ms||0),.99).toFixed(2)});
module.exports={fs,path,assert,root,runtime,base,db,server,sql,q,jsonSql,sleep,raw,api,control,adminLogin,userLogin,inputFor,createOrder,payAndConfirm,waitOrder,parallel,summary};
