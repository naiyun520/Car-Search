// Layout preview with the actual homepage template/styles and local mock catalog only.
// This is a browser approximation, not the WeChat native renderer.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const mini=path.resolve(__dirname,'../car-search-springboot/miniapp');
const sass=require(path.join(mini,'node_modules/sass'));
const source=fs.readFileSync(path.join(mini,'src/pages/index/index.vue'),'utf8');
const template=source.match(/<template>([\s\S]*?)<\/template>/)[1].replace(/<image\b/g,'<img');
const rawStyle=source.match(/<style[^>]*>([\s\S]*?)<\/style>/)[1];
const css=sass.compileString(rawStyle).css.replaceAll(' image',' img').replace(/(-?[\d.]+)rpx/g,(_,n)=>'calc(var(--rpx) * '+n+')');
http.createServer((req,res)=>{
 const u=new URL(req.url,'http://127.0.0.1');
 if(u.pathname==='/vue.js'){res.setHeader('Content-Type','text/javascript');return res.end(fs.readFileSync(path.join(mini,'node_modules/vue/dist/vue.global.prod.js')))}
 if(u.pathname.startsWith('/static/')){
   const file=path.resolve(mini,'src','.'+u.pathname);
   if(!file.startsWith(path.join(mini,'src','static')+path.sep)){res.statusCode=403;return res.end()}
   try{res.setHeader('Content-Type',file.endsWith('.svg')?'image/svg+xml':file.endsWith('.png')?'image/png':'image/jpeg');return res.end(fs.readFileSync(file))}catch{res.statusCode=404;return res.end()}
 }
 const width=Math.max(320,Math.min(414,Number(u.searchParams.get('width'))||375));
 res.setHeader('Content-Type','text/html;charset=utf-8');
 res.end('<!doctype html><html><head><meta charset="utf-8"><style>:root{--rpx:'+width/750+'px;--status-bar-height:24px}*{box-sizing:border-box}body{margin:0;width:'+width+'px;font-family:Arial,"Microsoft YaHei",sans-serif;background:#f4f8fd}view{display:block}text{display:inline}button,input{font:inherit}button{border:0;cursor:pointer}input{width:0;outline:0;border:0;background:none;height:100%}.card{background:#fff}.section-head{display:flex}.brand-icon img{object-fit:contain}img{display:block;object-fit:contain}.hero-image{object-fit:cover}'+css+'</style></head><body><div id="app"></div><script src="/vue.js"></script><script>const {ref,computed}=Vue;Vue.createApp({template:'+JSON.stringify(template)+',setup(){const keyword=ref(""),categoryCode=ref("");const categories=[{code:"basic",name:"基础查询"},{code:"history",name:"车辆记录"},{code:"verify",name:"信息核验"}];const services=[{code:"vin",category_code:"basic",name:"VIN码车辆信息解析",description:"解析车辆型号、动力配置与出厂参数。",status:1},{code:"maintain",category_code:"history",name:"车辆维修保养记录",description:"了解历史维保记录，辅助判断车辆使用情况。",status:1},{code:"insurance",category_code:"history",name:"车辆出险记录",description:"查询历史出险信息，了解车辆保险理赔记录。",status:1},{code:"check",category_code:"verify",name:"车辆信息核验",description:"核验车辆信息是否一致，辅助确认车辆身份。",status:1}];return {keyword,categoryCode,categories,filteredServices:computed(()=>services.filter(s=>(!categoryCode.value||s.category_code===categoryCode.value)&&s.name.includes(keyword.value))),showAnnouncement:false,announcement:{},suppress:false,pickCategory:c=>categoryCode.value=c,introOf:s=>({title:s.name,intro:s.description}),iconUrl:s=>"/static/icons/services/document.svg",openService:()=>{},openDemoReport:()=>document.body.dataset.demo="opened",closeAnnouncement:()=>{}}}}).mount("#app");setTimeout(()=>{const input=document.querySelector(".search-box input"),brand=document.querySelector(".brand-row"),tag=document.querySelector(".demo-tag");const service=document.querySelector(".service-panel");document.body.dataset.layout=(service.contains(input)&&!brand.contains(tag)&&document.querySelector(".hero-banner").contains(tag)&&document.body.scrollWidth<='+width+')?"PASS":"FAIL";tag.click();input.value="维修";input.dispatchEvent(new Event("input",{bubbles:true}));setTimeout(()=>{document.body.dataset.search=document.querySelectorAll(".service-item").length===1?"PASS":"FAIL";document.title="layout:"+document.body.dataset.layout+" search:"+document.body.dataset.search+" demo:"+document.body.dataset.demo;input.value="";input.dispatchEvent(new Event("input",{bubbles:true}))},10)},100)</script></body></html>');
}).listen(18766,'127.0.0.1',()=>console.log('Homepage mock preview http://127.0.0.1:18766'));

