import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
const source=fs.readFileSync(new URL('../src/pages/query/query.vue',import.meta.url),'utf8')
const actual=source.slice(source.indexOf('async function fulfil('),source.indexOf('async function poll('))
for(const stage of ['pending_direct','running_direct','waiting_result']){
 test('queued '+stage+' opens the appropriate report/progress view',async()=>{
  const routes=[],ctx=vm.createContext({progressText:{value:''},showLoading:()=>{},checkoutRequest:async()=>({status:'querying',query_stage:stage}),finish:no=>routes.push(['report',no]),showAsyncPending:no=>routes.push(['orders',no]),poll:()=>{},finalError:m=>new Error(m),contactError:(no,m)=>new Error(m)})
  vm.runInContext(actual,ctx);await ctx.fulfil('LOCAL-ORDER',{status:'paid'})
  assert.deepEqual(routes,[[stage.includes('direct')?'report':'orders','LOCAL-ORDER']])
 })
}
test('result refresh reads the order instead of raw supplier query records',()=>{
 const result=fs.readFileSync(new URL('../src/pages/result/result.vue',import.meta.url),'utf8')
 const polling=result.slice(result.indexOf('function startPolling(){'),result.indexOf('function stopPolling(){'))
 assert.ok(polling.includes('reportLoader.refresh()'))
 assert.ok(result.includes("()=>checkoutRequest('/checkout/status'"))
 assert.ok(!polling.includes('/query-status/'))
})
