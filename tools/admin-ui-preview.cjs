// Local visual QA only: no real database, credentials or supplier requests.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../car-search-springboot/backend/src/main/resources/static/admin');
const metric={new_users:12,orders:40,paid_orders:24,gross:720,refunds:30,net:690,cost:120,unknown_cost:5,profit:570,average:30,payment_conversion:60,repeat_rate:20,cohort_orders:40,cohort_paid:24,cohort_success:22,cohort_refunded:1};
const daily=Array.from({length:7},(_,i)=>({date:'2026-11-0'+(i+1),new_users:i+1,orders:5+i,gross:100+i*10,refunds:0,net:100+i*10,cost:12,unknown_cost:0,profit:88+i*10}));
const data={start:'2026-11-01',end:'2026-11-07',updated_at:'2026-11-03T16:30:00+08:00',users:120,current:metric,previous:metric,daily,statuses:[{name:'success',amount_value:22},{name:'pending_payment',amount_value:16}],platforms:[{name:'android',amount_value:30},{name:'ios',amount_value:10}],failures:[{name:'timeout',value:2}],services:[{service_name:'车辆维保记录',orders:40,paid_orders:24,gross:720,success_orders:22,failed_orders:2,event_gross:720,event_refunds:30,event_cost:120,event_profit:570}]};
const demoOrder={order_no:'LOCAL-DEMO',status:'success',service_name:'本地模拟报告',amount:30,result:[{key:'vin',label:'VIN',value:'TEST-DEMO'},{key:'records',label:'记录',value:[{date:'2026-01-01',mileage:10000}]}]};
http.createServer((req,res)=>{
 const url=new URL(req.url,'http://127.0.0.1');
 res.setHeader('Cache-Control','no-store');
 if(url.pathname==='/admin-api/analytics'){res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({code:0,data}))}
 if(url.pathname==='/admin-api/order-detail/LOCAL-DEMO'){res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({code:0,data:demoOrder}))}
 if(url.pathname.startsWith('/admin-api/')){res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({code:0,data:url.pathname.endsWith('/fulfillment')?{task:{state:'waiting_result',fetch_attempts:2,max_fetch_attempts:20,next_run_at:'2026-11-03 16:31:00'},calls:[{stage:'SUBMIT',billing_status:'confirmed',cost_amount:5}],callbacks:[]}:[]}))}
 if(url.pathname==='/ui-check.js'){res.setHeader('Content-Type','text/javascript');return res.end(fs.readFileSync(path.join(__dirname,'admin-ui-check.js')))}
 let file=url.pathname==='/'?'/index.html':url.pathname;
 if(file==='/qa.js'){res.setHeader('Content-Type','text/javascript');return res.end("localStorage.setItem('admin_token','LOCAL-MOCK');localStorage.setItem('admin_must_change','0');window.addEventListener('load',()=>{if(location.hash==='#check'){const s=document.createElement('script');s.src='/ui-check.js';document.body.append(s)}if(location.hash==='#order'){setTimeout(()=>{showOrderDetail("+JSON.stringify(demoOrder)+");document.querySelector('[data-tool=revisions]').click()},200)}});")}
 const target=path.resolve(root,'.'+file);
 if(!target.startsWith(root+path.sep)){res.statusCode=403;return res.end()}
 try{let content=fs.readFileSync(target);if(file.endsWith('.html'))content=content.toString().replace('<script src="./echarts','<script src="/qa.js"></script><script src="./echarts');res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');res.end(content)}catch{res.statusCode=404;res.end()}
}).listen(18765,'127.0.0.1',()=>console.log('Mock UI: http://127.0.0.1:18765 (local only)'));

