let chartInstances=[],dashboardTimer=null,dashboardRequest=0
const dashboardRange={start:'',end:''};let dashboardRenderedRange={start:'',end:''},serviceView='orders'
window.disposeCharts=()=>{chartInstances.forEach(c=>c.dispose());chartInstances=[];clearTimeout(dashboardTimer);dashboardRequest++}
window.addEventListener('resize',()=>chartInstances.forEach(c=>c.resize()))
const analyticsNames={pending_payment:'待付款',payment_review:'付款待核对',paid:'待开始查询',querying:'查询中',success:'查询成功',query_failed:'查询失败',refunding:'退款中',refunded:'已退款',cancelled:'已取消',android:'Android',ios:'iOS',unknown:'未识别',timeout:'查询超时'}
const fmtMoney=v=>v==null?'—':'¥'+Number(v).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const moneyKeys=['gross','refunds','net','cost','unknown_cost','profit','average','event_gross','event_refunds','event_cost','event_profit']
const valueText=(k,v)=>v==null?'—':moneyKeys.includes(k)?fmtMoney(v):k.includes('rate')||k.includes('conversion')?Number(v).toFixed(1)+'%':Number(v).toLocaleString('zh-CN')
function mountChart(id,option){const el=document.getElementById(id);if(!el)return;if(typeof echarts==='undefined'){el.textContent='图表暂不可用，明细数据仍可查看';return}const c=echarts.init(el);c.setOption(option);chartInstances.push(c)}
function scheduleDashboardRefresh(){clearTimeout(dashboardTimer);dashboardTimer=setTimeout(()=>{if(currentPage!=='dashboard')return;if(document.hidden||document.activeElement?.closest('#analytics-filter'))scheduleDashboardRefresh();else loaders.dashboard().catch(e=>{toast(e.message||'刷新失败，保留上次数据');scheduleDashboardRefresh()})},60000)}
async function exportAnalytics(type){
 const response=await fetch(API+'/analytics/export?'+new URLSearchParams({...dashboardRenderedRange,type}),{headers:{Authorization:'Bearer '+token}})
 if(!response.ok){const b=await response.json().catch(()=>({}));throw Error(b.message||'导出失败')}
 const url=URL.createObjectURL(await response.blob()),link=document.createElement('a');link.href=url;link.download=type+'-'+dashboardRenderedRange.start+'-'+dashboardRenderedRange.end+'.csv';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
}
function dashboardPresetRange(key,now=new Date()){
 const iso=d=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(d)
 let end=new Date(now),start=new Date(now)
 if(key==='yesterday'){start.setUTCDate(start.getUTCDate()-1);end=new Date(start)}else if(key==='week')start.setUTCDate(start.getUTCDate()-6);else if(key==='30')start.setUTCDate(start.getUTCDate()-29)
 if(key==='month'||key==='lastmonth'){const p=iso(end).split('-').map(Number);start=new Date(Date.UTC(p[0],p[1]-1,1));if(key==='lastmonth'){end=new Date(Date.UTC(p[0],p[1]-1,0));start=new Date(Date.UTC(p[0],p[1]-2,1))}}
 return {start:iso(start),end:iso(end)}
}
loaders.dashboard=async()=>{
 const id=++dashboardRequest,data=await api('/analytics?'+new URLSearchParams(dashboardRange))
 if(id!==dashboardRequest||currentPage!=='dashboard')return
 window.disposeCharts()
 dashboardRange.start=data.start;dashboardRange.end=data.end;dashboardRenderedRange={start:data.start,end:data.end}
 const m=data.current,prev=data.previous
 const cards=[['gross','付款金额','按付款确认日期统计','income'],['net','退款后收入','付款金额减去已完成退款','blue'],['profit','估算毛利','退款后收入减去估算接口成本；未扣除平台手续费、税费和待核实成本，非供应商实际账单利润','teal'],['paid_orders','付款订单','按付款确认日期统计','violet'],['orders','新增订单','按创建日期统计','blue'],['payment_conversion','下单付款率','本期创建订单中已经付款的比例，随订单后续进展更新','amber']]
 const secondary=[['new_users','新增用户'],['refunds','已完成退款'],['cost','估算接口成本'],['unknown_cost','待核实成本（估算）'],['average','每笔付款均价'],['repeat_rate','重复付款用户占比']]
 const kpi=cards.map(([k,n,h,t])=>{
  const a=prev[k],b=m[k],delta=a!=null&&Number(a)!==0&&b!=null?(Number(b)-Number(a))/Math.abs(Number(a))*100:null
  const change=delta==null?'上期 '+valueText(k,a):'环比 '+(delta>0?'+':'')+delta.toFixed(1)+'%'
  return '<article class="stat metric-card '+t+'" title="'+escapeHtml(h)+'"><div class="metric-top"><span>'+n+'</span></div><b class="'+(Number(b)<0?'negative':'')+'">'+valueText(k,b)+'</b><div class="metric-comparison"><span>'+change+'</span>'+(delta==null?'':'<span>上期 '+valueText(k,a)+'</span>')+'</div></article>'
 }).join('')
 const table=(rows,cols)=>'<div class="table-wrap"><table><thead><tr>'+cols.map(c=>'<th'+(c[2]?' class="numeric"':'')+'>'+c[1]+'</th>').join('')+'</tr></thead><tbody>'+(rows.map(r=>'<tr>'+cols.map(c=>'<td'+(c[2]?' class="numeric"':'')+'>'+escapeHtml(c[2]?valueText(c[0],r[c[0]]):((analyticsNames[r[c[0]]]||r[c[0]])??'—'))+'</td>').join('')+'</tr>').join('')||'<tr><td colspan="'+cols.length+'"><div class="empty-state">这段时间还没有数据</div></td></tr>')+'</tbody></table></div>'

 const section=(title,scope,content,extra='',kind='')=>'<section class="panel board-section '+kind+'"><div class="section-heading"><div class="section-heading-main"><h3>'+title+'</h3>'+(scope?'<span class="section-scope">'+scope+'</span>':'')+'</div>'+extra+'</div>'+content+'</section>'
 const updated=escapeHtml(new Date(String(data.updated_at).replace(/\[.*\]$/,'')).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai'}))
 $('#content').innerHTML='<div class="operations-dashboard">'+
 '<section class="panel board-controls"><div class="range-heading"><div class="range-heading-main"><strong>统计区间</strong><span>'+data.start+' — '+data.end+'</span></div><div class="board-update"><span>累计用户 <b>'+valueText('users',data.users)+'</b></span><span>更新于 '+updated+'</span><button type="button" class="secondary compact" data-refresh-dashboard>刷新</button></div></div><div class="range-row"><div class="range-presets">'+[['today','今天'],['yesterday','昨天'],['week','近7天'],['month','本月'],['lastmonth','上月'],['30','近30天']].map(x=>'<button class="range-chip" data-range="'+x[0]+'">'+x[1]+'</button>').join('')+'</div><form id="analytics-filter" class="filter"><input aria-label="开始日期" type="date" name="start" value="'+data.start+'" required><span>至</span><input aria-label="结束日期" type="date" name="end" value="'+data.end+'" required><button class="primary">查询</button></form><div class="export-menu"><select id="export-kind" aria-label="导出报表类型"><option value="daily">经营日报</option><option value="services">服务表现</option><option value="orders">订单明细</option><option value="calls">接口调用记录</option></select><button class="secondary" data-export="selected">导出 CSV</button></div></div></section>'+
 '<div class="stats dashboard-stats">'+kpi+'</div><div class="metric-strip">'+secondary.map(([k,n])=>'<div><span>'+n+'</span><b>'+valueText(k,m[k])+'</b></div>').join('')+'</div>'+
 '<div class="board-chart-grid">'+section('收支趋势','按收支日期 · 元','<div id="cash-chart" class="chart-box"></div>')+section('订单与用户趋势','按创建日期 · 个','<div id="growth-chart" class="chart-box"></div>')+'</div>'+
 section('业务表现','','<div id="service-table"></div>','<div class="service-switch"><span id="service-scope">按下单日期</span><div class="segmented"><button data-service-view="orders">订单与查询</button><button data-service-view="money">收入与成本</button></div></div>','service-performance')+
 section('本期订单结果','按下单日期','<div class="journey-grid">'+[['cohort_orders','下单数'],['cohort_paid','已付款'],['cohort_success','查询成功（含后续退款）'],['cohort_refunded','已退款']].map(([k,n])=>'<div><span>'+n+'</span><b>'+valueText(k,m[k])+'</b></div>').join('')+'</div>')+
 '<div class="board-distributions">'+section('订单状态','本期新增订单','<div id="status-chart" class="chart-box"></div>')+section('下单设备','本期新增订单','<div id="platform-chart" class="chart-box"></div>')+section('查询失败','供应商业务码',table(data.failures,[['name','业务码'],['value','订单数',true]]),'','failure-breakdown')+'</div>'+
 section('每日经营明细','','<div class="daily-scroll">'+table(data.daily,[['date','日期'],['orders','新增订单',true],['gross','付款金额',true],['refunds','退款金额',true],['net','退款后收入',true],['cost','估算接口成本',true],['unknown_cost','待核实成本（估算）',true],['profit','估算毛利',true]])+'</div>')+'</div>'

 document.querySelectorAll('[data-range]').forEach(b=>{const r=dashboardPresetRange(b.dataset.range),active=r.start===data.start&&r.end===data.end;b.classList.toggle('active',active);b.setAttribute('aria-pressed',String(active))})
 function renderServices(){
  const cols=serviceView==='orders'?[['service_name','业务'],['orders','下单数',true],['paid_orders','付款数',true],['gross','付款金额',true],['success_orders','查询成功',true],['failed_orders','查询失败',true]]:[['service_name','业务'],['event_gross','付款金额',true],['event_refunds','退款金额',true],['event_cost','估算接口成本',true],['event_profit','估算毛利',true]]
  $('#service-table').innerHTML=table(data.services,cols)
  $('#service-scope').textContent=serviceView==='orders'?'按下单日期':'按收支日期'
  document.querySelectorAll('[data-service-view]').forEach(b=>b.classList.toggle('active',b.dataset.serviceView===serviceView))
 }
 renderServices()
 const colors=['#2563eb','#11a999','#f59e0b','#8b5cf6','#e05d65','#7c94b5']
 const option=series=>({color:colors,textStyle:{fontSize:14,color:'#526174'},tooltip:{trigger:'axis'},legend:{bottom:0,icon:'roundRect',textStyle:{fontSize:14}},grid:{left:64,right:25,top:25,bottom:55},xAxis:{type:'category',data:data.daily.map(x=>x.date.slice(5)),axisLine:{lineStyle:{color:'#dbe3ee'}},axisTick:{show:false}},yAxis:{type:'value',splitLine:{lineStyle:{color:'#edf1f6',type:'dashed'}}},series})
 mountChart('cash-chart',option([['gross','收到付款'],['refunds','完成退款'],['profit','估算毛利']].map(([k,n])=>({name:n,type:'line',smooth:false,symbolSize:5,lineStyle:{width:3},data:data.daily.map(x=>x[k])}))))
 mountChart('growth-chart',option([['orders','新增订单'],['new_users','新增用户']].map(([k,n],i)=>({name:n,type:'bar',barMaxWidth:18,itemStyle:{borderRadius:[4,4,0,0]},data:data.daily.map(x=>x[k])}))))
 for(const [id,rows]of [['status-chart',data.statuses],['platform-chart',data.platforms]]){
  mountChart(id,{color:colors,textStyle:{fontSize:14,color:'#526174'},tooltip:{trigger:'item',formatter:'{b}：{c}（{d}%）'},legend:{orient:'vertical',right:0,top:'middle',type:'scroll',textStyle:{fontSize:14},formatter:name=>{const row=rows.find(r=>(analyticsNames[r.name]||r.name)===name);return name+'  '+Number(row?.amount_value||0).toLocaleString('zh-CN')}},series:[{type:'pie',radius:['48%','72%'],center:['32%','50%'],avoidLabelOverlap:true,label:{show:false},itemStyle:{borderColor:'#fff',borderWidth:3,borderRadius:5},data:rows.map(r=>({name:analyticsNames[r.name]||r.name,value:r.amount_value,itemStyle:{color:({pending_payment:'#a8b4c4',payment_review:'#d69c2e',paid:'#5e8ed8',querying:'#3478e8',success:'#249b88',query_failed:'#db6672',refunding:'#d69c2e',refunded:'#926dc2',cancelled:'#d0d7e2',android:'#3478e8',ios:'#249b88',unknown:'#a8b4c4'})[r.name]||'#8a9bb5'}}))}],graphic:rows.length?undefined:{type:'text',left:'center',top:'middle',style:{text:'暂无数据',fill:'#8290a6'}}})
 }
 $('#analytics-filter').onsubmit=e=>{e.preventDefault();const v=Object.fromEntries(new FormData(e.target));if(v.start>v.end)return toast('开始日期不能晚于结束日期');Object.assign(dashboardRange,v);loaders.dashboard().catch(e=>toast(e.message))}
 $('#content').onclick=async e=>{
  const refresh=e.target.closest('[data-refresh-dashboard]');if(refresh){refresh.disabled=true;try{await loaders.dashboard()}catch(error){toast(error.message)}finally{refresh.disabled=false}return}
  const view=e.target.closest('[data-service-view]');if(view){serviceView=view.dataset.serviceView;renderServices();return}
  const ex=e.target.closest('[data-export]');if(ex){ex.disabled=true;try{await exportAnalytics($('#export-kind').value)}catch(error){toast(error.message)}finally{ex.disabled=false}return}
  const b=e.target.closest('[data-range]');if(!b)return
  Object.assign(dashboardRange,dashboardPresetRange(b.dataset.range));loaders.dashboard().catch(e=>toast(e.message))
 }
 scheduleDashboardRefresh()
}
