import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
const source=fs.readFileSync(new URL('../src/utils/order-list.js',import.meta.url),'utf8')
const {createOrderPager}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'))
const rows=(start,n)=>Array.from({length:n},(_,i)=>({order_no:String(start+i)}))
test('loads every page and stops at the real total',async()=>{
 const state={},calls=[],pager=createOrderPager(async args=>{calls.push(args);return {data:rows((args.page-1)*10,Math.min(10,25-(args.page-1)*10)),total:25}},state)
 await pager.refresh();await pager.more();await pager.more();await pager.more()
 assert.equal(state.orders.length,25);assert.equal(calls.length,3);assert.equal(state.finished,true)
})
test('duplicate scroll events and duplicate rows do not duplicate orders',async()=>{
 const state={};let resolve,calls=0
 const pager=createOrderPager(()=>{calls++;return new Promise(r=>{resolve=r})},state,2)
 const first=pager.more();await pager.more();assert.equal(calls,1)
 resolve({data:rows(1,2),total:4});await first
 const second=pager.more();resolve({data:rows(2,2),total:4});await second
 assert.equal(state.orders.length,3);assert.equal(state.finished,true)
})
test('switching category discards the old in-flight response',async()=>{
 const state={},resolvers={},pager=createOrderPager(args=>new Promise(r=>{resolvers[args.group]=r}),state)
 const old=pager.refresh(),current=pager.reset('success')
 resolvers.success({data:[{order_no:'new'}],total:1});await current
 resolvers.all({data:[{order_no:'old'}],total:1});await old
 assert.equal(state.group,'success');assert.deepEqual(state.orders,[{order_no:'new'}]);assert.equal(state.loading,false)
})
test('a failed next page keeps existing orders and retries that page',async()=>{
 const state={},calls=[];let fail=true
 const pager=createOrderPager(async args=>{calls.push(args.page);if(args.page===2&&fail){fail=false;throw Error('网络暂不可用')}return {data:rows(args.page,1),total:2}},state,1)
 await pager.refresh();await pager.more();assert.equal(state.orders.length,1);assert.equal(state.page,1);assert.match(state.error,/网络/)
 await pager.retry();assert.deepEqual(calls,[1,2,2]);assert.equal(state.orders.length,2);assert.equal(state.error,'')
})
test('return refresh updates all loaded pages and retains them on partial failure',async()=>{
 const state={};let revision=0,fail=false
 const pager=createOrderPager(async args=>{if(args.page===2&&fail)throw Error('失败');return {data:[{order_no:String(args.page),revision}],total:3}},state,1)
 await pager.refresh();await pager.more();revision=1;fail=true
 await pager.refresh();assert.equal(state.orders[0].revision,0);assert.equal(state.page,2)
 fail=false;await pager.retry();assert.equal(state.orders[0].revision,1);assert.equal(state.orders[1].revision,1);assert.equal(state.page,2)
})
test('empty lists end pagination',async()=>{
 const state={},pager=createOrderPager(async()=>({data:[],total:0}),state)
 await pager.refresh();assert.equal(state.finished,true);assert.equal(state.loading,false)
})
test('unpaid orders only show an explanation and never navigate to checkout',()=>{
 const page=fs.readFileSync(new URL('../src/pages/orders/orders.vue',import.meta.url),'utf8'),start=page.indexOf('function goDetail('),end=page.indexOf('function showAppleRefund(',start)
 const modals=[],routes=[],ctx=vm.createContext({uni:{showModal:value=>modals.push(value),navigateTo:value=>routes.push(value)}})
 vm.runInContext(page.slice(start,end),ctx);ctx.goDetail({status:'pending_payment',order_no:'existing'})
 assert.equal(routes.length,0);assert.equal(modals[0].showCancel,false);assert.equal(modals[0].confirmText,'我知道了');assert.match(modals[0].content,/尚未完成付款/);assert.equal(modals[0].success,undefined)
})
