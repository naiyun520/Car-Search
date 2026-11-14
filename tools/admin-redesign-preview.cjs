const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../car-search-springboot/backend/src/main/resources/static/admin');
http.createServer((req,res)=>{
 const u=new URL(req.url,'http://127.0.0.1');
 if(u.pathname.startsWith('/admin-api/')||u.pathname.startsWith('/actuator/')){
  const p=http.request({host:'127.0.0.1',port:Number(process.env.UI_AUDIT_BACKEND_PORT||18081),path:req.url,method:req.method,headers:req.headers},r=>{res.writeHead(r.statusCode,r.headers);r.pipe(res)});p.on('error',()=>{res.writeHead(502);res.end('Local backend unavailable')});req.pipe(p);return;
 }
 const name=u.pathname==='/localqa/'?'index.html':u.pathname.split('/').pop();
 if(!['index.html','app.js','dashboard.js','order-tools.js','settings.js','services.js','providers.js','errors.js','echarts.min.js','style.css','enhancements.css','dashboard.css'].includes(name)){res.writeHead(404);res.end();return}
 res.setHeader('Cache-Control','no-store');res.setHeader('Content-Type',name.endsWith('.js')?'text/javascript':name.endsWith('.css')?'text/css':'text/html;charset=utf-8');res.end(fs.readFileSync(path.join(root,name)));
}).listen(18881,'127.0.0.1',()=>console.log('Source UI with isolated backend: http://127.0.0.1:18881/localqa/'));
