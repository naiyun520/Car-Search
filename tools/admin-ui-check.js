// Executed only by tools/admin-ui-preview.cjs on loopback with mock responses.
(async()=>{
 const assert=(v,m)=>{if(!v)throw Error(m)};
 await new Promise(r=>setTimeout(r,300));
 assert(document.querySelectorAll('.dashboard-stats .stat').length===6,'six primary KPI cards');
 assert(document.querySelectorAll('#export-kind option').length===4,'four export types');
 showOrderDetail({order_no:'LOCAL-DEMO',status:'success',result:[{key:'vin',label:'VIN',value:'TEST'}]});
 const click=async tool=>{document.querySelector('[data-tool="'+tool+'"]').click();await new Promise(r=>setTimeout(r,100))};
 await click('revisions');
 assert(document.querySelectorAll('.revision-field').length===2,'editor loads typed fields');
 await click('add');await click('add');
 assert(document.querySelectorAll('.revision-field').length===4,'can add multiple blank fields');
 document.querySelector('[data-tool=remove][data-index="3"]').click();
 assert(document.querySelectorAll('.revision-field').length===3,'can remove blank field');
 document.querySelector('[data-tool=remove][data-index="2"]').click();
 document.querySelector('#revision-reason').value='本地模拟修订';
 const val=document.querySelector('[data-field=value][data-kind=json]');val.value='{invalid json';
 await click('save');
 assert(!document.querySelector('.revision-preview'),'invalid JSON does not save');
 val.value='[{"date":"2026-01-01","mileage":10000}]';window.confirm=()=>true;
 await click('save');assert(document.querySelector('.revision-preview'),'save previews current and proposed result');
 await click('diagnose');
 assert(document.querySelector('[data-tool=fetch]'),'waiting task exposes fetch-only command');
 assert(document.querySelector('[data-tool=stop]'),'waiting task exposes stop');
 assert(document.querySelector('.order-tool-body table'),'diagnostics are structured tables');
 document.body.dataset.qa='PASS';document.title='QA PASS';
})().catch(e=>{document.body.dataset.qa='FAIL '+e.message;document.title='QA FAIL '+e.message});

