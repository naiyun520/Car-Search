import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
const source=fs.readFileSync(new URL('../src/utils/report-loader.js',import.meta.url),'utf8')
const {createReportLoader}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'))
const settle=async()=>{for(let i=0;i<5;i++)await Promise.resolve()}
test('automatic and manual reads share one request and apply once',async()=>{
 let resolve,calls=0;const applied=[],loader=createReportLoader(()=>{calls++;return new Promise(r=>resolve=r)},r=>applied.push(r))
 const a=loader.refresh(),b=loader.refresh();assert.equal(calls,1);resolve({status:'success'});await Promise.all([a,b]);assert.equal(applied.length,1)
})
test('leaving the page discards old response; returning waits then reads fresh',async()=>{
 const resolvers=[],applied=[],loader=createReportLoader(()=>new Promise(r=>resolvers.push(r)),r=>applied.push(r))
 const old=loader.refresh();loader.invalidate();const fresh=loader.refresh();assert.equal(resolvers.length,1)
 resolvers[0]({status:'querying'});await old;await settle();assert.equal(applied.length,0);assert.equal(resolvers.length,2)
 resolvers[1]({status:'success'});await fresh;assert.deepEqual(applied,[{status:'success'}])
})
test('failed request does not prevent a later retry',async()=>{
 let calls=0;const applied=[],loader=createReportLoader(async()=>{if(++calls===1)throw Error('timeout');return {status:'success'}},r=>applied.push(r))
 await assert.rejects(loader.refresh());await loader.refresh();assert.equal(calls,2);assert.equal(applied.length,1)
})
const page=fs.readFileSync(new URL('../src/pages/result/result.vue',import.meta.url),'utf8')
function polling(){
 let sequence=0;const timers=new Map(),requests=[];const ctx=vm.createContext({pollTimer:null,elapsedTimer:null,pollingActive:false,pollingCycle:0,pageVisible:true,isDemo:{value:false},isQuerying:{get value(){return ctx.order.value.status==='querying'}},elapsed:{value:0},order:{value:{status:'querying'}},console,
 setTimeout:(fn,ms)=>{timers.set(++sequence,{fn,ms});return sequence},clearTimeout:id=>timers.delete(id),setInterval:(fn,ms)=>{timers.set(++sequence,{fn,ms});return sequence},clearInterval:id=>timers.delete(id)})
 ctx.reportLoader=createReportLoader(()=>new Promise((resolve,reject)=>requests.push({resolve,reject})),r=>{ctx.order.value=r;if(r.status!=='querying')ctx.stopPolling()})
 vm.runInContext(page.slice(page.indexOf('function startPolling(){'),page.indexOf('function markViewedIfNeeded()')),ctx)
 const tick=()=>{const [id,timer]=[...timers].find(([,t])=>t.ms===5000);timers.delete(id);return timer.fn()}
 return {ctx,timers,requests,tick,pollCount:()=>[...timers.values()].filter(t=>t.ms===5000).length}
}
test('actual report page waits for slow response before scheduling again, stops on success',async()=>{
 const p=polling();p.ctx.startPolling();const tick=p.tick();assert.equal(p.requests.length,1);assert.equal(p.pollCount(),0)
 const manual=p.ctx.reloadOrder();assert.equal(p.requests.length,1);p.requests[0].resolve({status:'success'});await Promise.all([tick,manual]);assert.equal(p.ctx.order.value.status,'success');assert.equal(p.pollCount(),0);assert.equal(p.ctx.pollingActive,false)
})
test('late callback from a hidden page cannot create an extra polling loop after return',async()=>{
 const p=polling();p.ctx.startPolling();const old=p.tick();p.ctx.pageVisible=false;p.ctx.stopPolling();p.ctx.reportLoader.invalidate();p.ctx.pageVisible=true
 const fresh=p.ctx.reloadOrder();p.requests[0].resolve({status:'querying'});await old;await settle();assert.equal(p.requests.length,2)
 p.requests[1].resolve({status:'querying'});await fresh;assert.equal(p.pollCount(),1)
})
test('polling retries after a network failure without overlapping requests',async()=>{
 const p=polling();p.ctx.startPolling();const old=p.tick();p.requests[0].reject(Error('offline'));await old;assert.equal(p.pollCount(),1)
 const next=p.tick();assert.equal(p.requests.length,2);p.requests[1].resolve({status:'success'});await next;assert.equal(p.pollCount(),0)
})
