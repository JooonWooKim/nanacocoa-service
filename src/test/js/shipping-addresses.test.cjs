const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/shipping-addresses.js','utf8');
const names=['label','recipientName','phoneNumber','postalCode','basicAddress','detailAddress'];
const primary={id:1,version:0,label:'집',recipientName:'가상 수령인',phoneNumber:'010-0000-0000',postalCode:'12345',basicAddress:'가상 주소',detailAddress:'상세',defaultAddress:true};
class Element {
  constructor(tag='div'){this.tag=tag;this.children=[];this.events={};this.value='';this.disabled=false;this.hidden=false;this.checked=false;this.textContent='';}
  append(...nodes){for(const node of nodes){this.children.push(node);if(node instanceof Element)node.parentElement=this;}}
  before(node){this.parentElement.append(node);}
  get textContent(){return (this._text||'')+this.children.map(node=>node instanceof Element?node.textContent:String(node)).join('');}
  set textContent(value){this._text=String(value);this.children=[];}
  replaceChildren(...nodes){this._text='';this.children=[];this.append(...nodes);}
  setAttribute(name,value){this[name]=value;}
  setCustomValidity(){}
  addEventListener(name,fn){this.events[name]=fn;}
  all(){return [this,...this.children.filter(x=>x instanceof Element).flatMap(x=>x.all())];}
  querySelectorAll(){return this.all().filter(x=>['button','input'].includes(x.tag));}
  reportValidity(){return true;}
}
const response=book=>({ok:true,status:200,body:{data:book}});
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function harness({book={revision:1,checkoutAccount:'buyer',items:[primary]},request,sessionRequest,manage=false}={}){
  const controls=Object.fromEntries(names.map(n=>[n,new Element('input')]));
  controls.makeDefault=new Element('input');
  const form=new Element('form');form.elements=controls;
  const fields=new Element(),submit=new Element('button'),search=new Element('button'),searchStatus=new Element('p');
  form.append(fields,...Object.values(controls),submit,search,searchStatus);
  form.querySelector=s=>({'[data-address-postal-code]':controls.postalCode,'[data-address-basic]':controls.basicAddress,'[data-address-detail]':controls.detailAddress,'.checkout-fields':fields,'[data-order-submit]':submit,'[type="submit"]':submit,'[data-address-search]':search,'[data-address-search-status]':searchStatus}[s]);
  controls.recipientName.value='가상 회원';
  const nodes=Object.fromEntries(['list','message','refresh','new'].map(n=>[n,new Element(n==='refresh'||n==='new'?'button':'div')]));
  const page=new Element();page.append(form,...Object.values(nodes));page.querySelector=s=>s==='[data-address-form]'?form:nodes[s.match(/data-address-(.*)\]/)?.[1]];
  const session={authenticated:true,checkoutAccount:'buyer',name:'가상 회원'},calls=[],windowEvents={};
  let submitted=false,submitting=false;
  const c={window:{location:{reload(){}},addEventListener:(name,fn)=>{(windowEvents[name]||=[]).push(fn);}},document:{querySelector:()=>manage?page:null,createElement:tag=>new Element(tag),createTextNode:s=>s},
    loadAuthSession:async()=>sessionRequest?sessionRequest({...session}):({...session}),requestAuth:async(url,options)=>{calls.push({url,options});return request?request(url,options):response(book);},readJson:async r=>r.body,
    randomUuid:()=> '11111111-1111-4111-8111-111111111111',setupKakaoAddressSearch(){},formatShippingAddress:(p,b,d)=>`[${p}] ${b} ${d}`.trim(),URLSearchParams};
  vm.createContext(c);vm.runInContext(source,c);
  return {c,controls,form,fields,submit,page,nodes,session,calls,windowEvents,book,
    start:()=>c.window.NanacocoaShippingAddresses.checkout({form,session:{...session},hasSubmission:()=>submitted,isSubmitting:()=>submitting}),
    setSubmitted:v=>submitted=v,setSubmitting:v=>submitting=v,
    panel:()=>form.all().find(x=>x.className==='saved-address-picker')};
}
test('new checkout fills default once using only GET; retry does not overwrite later edits',async()=>{
 const h=harness();h.start();await tick();assert.equal(h.controls.basicAddress.value,primary.basicAddress);assert.equal(h.controls.recipientName.value,primary.recipientName);assert.deepEqual(h.calls.map(x=>x.options.method||'GET'),['GET']);
 h.controls.basicAddress.value='직접 변경';h.controls.basicAddress.events.input();
 await h.panel().all().find(x=>x.textContent==='배송지 다시 조회').onclick();assert.equal(h.controls.basicAddress.value,'직접 변경');
});
test('late GET cannot overwrite input even after the user clears it',async()=>{
 let release;const h=harness({request:()=>new Promise(resolve=>release=resolve)});h.start();await tick();
 h.controls.basicAddress.value='입력';h.controls.basicAddress.events.input();h.controls.basicAddress.value='';h.controls.basicAddress.events.input();
 release(response(h.book));await tick();assert.equal(h.controls.basicAddress.value,'');assert.equal(h.controls.recipientName.value,'가상 회원');
});
test('silent browser autofill and prefilled phone are preserved',async()=>{
 let release;const h=harness({request:()=>new Promise(resolve=>release=resolve)});h.start();await tick();h.controls.phoneNumber.value='010-1111-1111';release(response(h.book));await tick();assert.equal(h.controls.phoneNumber.value,'010-1111-1111');assert.equal(h.controls.basicAddress.value,'');
 const ready=harness();ready.controls.phoneNumber.value='010-2222-2222';ready.start();await tick();assert.equal(ready.controls.basicAddress.value,'');
});
test('explicit selection replaces form; direct edits never write address book',async()=>{
 const h=harness();h.start();await tick();const select=h.panel().all().find(x=>x.tag==='select');
 h.controls.basicAddress.value='다른 입력';h.controls.basicAddress.events.input();select.value='1';select.events.change();assert.equal(h.controls.basicAddress.value,primary.basicAddress);
 assert.equal(h.calls.filter(x=>x.options.method&&x.options.method!=='GET').length,0);
});
test('empty and nondefault books leave checkout usable for manual input',async()=>{
 for(const items of [[],[{...primary,defaultAddress:false}]]){const h=harness({book:{revision:1,checkoutAccount:'buyer',items}});h.start();await tick();assert.equal(h.controls.basicAddress.value,'');assert.equal(h.submit.disabled,false);}
});
test('GET failure is explained, does not retry automatically and does not block manual checkout',async()=>{
 const h=harness({request:async()=>({ok:false,status:503,body:{error:'실패'}})});h.start();await tick();assert.equal(h.calls.length,1);assert.equal(h.submit.disabled,false);assert.match(h.panel().all().find(x=>x.role==='status').textContent,/직접 입력/);
});
test('existing submission and an order started while GET waits cannot be changed',async()=>{
 const old=harness();old.setSubmitted(true);old.start();await tick();assert.equal(old.calls.length,0);
 let release;const h=harness({request:()=>new Promise(resolve=>release=resolve)});h.start();await tick();h.setSubmitted(true);release(response(h.book));await tick();assert.equal(h.controls.basicAddress.value,'');
});
test('account switch and pagehide clear private data, with no automatic mutation',async()=>{
 const h=harness();h.start();await tick();h.session.checkoutAccount='other';await h.windowEvents.focus[0]();assert.equal(h.controls.basicAddress.value,'');assert.equal(h.submit.disabled,true);assert.equal(h.calls.length,1);
 const second=harness();second.start();await tick();second.windowEvents.pagehide[0]();assert.equal(second.controls.phoneNumber.value,'');
});
test('lost registration response refreshes GET and reuses original key/body only on user retry',async()=>{
 let posts=0;const empty={revision:0,checkoutAccount:'buyer',items:[]};
 const h=harness({manage:true,request:async(url,options)=>{if(options.method==='POST'){posts++;if(posts===1)throw new Error('합성 응답 유실');return response({revision:1,checkoutAccount:'buyer',items:[primary]});}return response(posts?{revision:1,checkoutAccount:'buyer',items:[primary]}:empty);}});
 await h.c.window.NanacocoaShippingAddresses.manage();h.nodes.new.onclick();
 for(const name of names)h.controls[name].value=primary[name];
 await h.form.events.submit({preventDefault(){}});assert.equal(posts,1);assert.match(h.nodes.message.textContent,/입력은 보존/);
 await h.form.events.submit({preventDefault(){}});assert.equal(posts,2);
 const writes=h.calls.filter(x=>x.options.method==='POST');assert.equal(writes[0].options.body,writes[1].options.body);assert.equal(writes[0].options.headers['Idempotency-Key'],writes[1].options.headers['Idempotency-Key']);
});
test('shipping address login return is a fixed read page, not a mutation or arbitrary URL',()=>{
 const script=fs.readFileSync('src/main/resources/static/script.js','utf8');const code=script.slice(script.indexOf('function cartLoginReturnUrl()'),script.indexOf('function checkoutLoginReturnUrl()'));
 const c={URLSearchParams,window:{location:{search:'?return=shipping-addresses&target=https://evil.test'}}};vm.createContext(c);vm.runInContext(code,c);assert.equal(c.cartLoginReturnUrl(),'shipping-addresses.html');
});


// 응답 순서를 제어해 실제로 DOM/폼/안내가 오래된 요청에 의해 복원되는지 검증한다.
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};}
async function invalidateManager(h,kind='account'){
 if(kind==='pagehide'){h.windowEvents.pagehide[0]();return;}
 if(kind==='logout')h.session.authenticated=false;else h.session.checkoutAccount='other';
 assert.ok(h.windowEvents.focus?.length,'focus protection must exist while initial GET waits');
 await h.windowEvents.focus[0]();
}
function assertManagerEmpty(h){
 assert.equal(h.nodes.list.children.length,0);assert.equal(h.form.hidden,true);
 for(const name of names)assert.equal(h.controls[name].value,'');
}
function fillManager(h){h.nodes.new.onclick();for(const name of names)h.controls[name].value=primary[name];}
for(const kind of ['account','logout']){
 test(`initial GET after ${kind} cannot restore addresses or login notice`,async()=>{
  const wait=deferred();const h=harness({manage:true,request:()=>wait.promise});
  const init=h.c.window.NanacocoaShippingAddresses.manage();await tick();
  try{
   await invalidateManager(h,kind);assertManagerEmpty(h);const notice=h.nodes.message.textContent;
   wait.resolve(response(h.book));await init;assertManagerEmpty(h);assert.equal(h.nodes.message.textContent,notice);
  }finally{wait.resolve(response(h.book));await init;}
 });
 test(`manual GET after ${kind} ignores stale success`,async()=>{
  const wait=deferred();let delayed=false;
  const h=harness({manage:true,request:()=>delayed?wait.promise:response(h.book)});
  await h.c.window.NanacocoaShippingAddresses.manage();delayed=true;
  const pending=h.nodes.refresh.onclick();await tick();await invalidateManager(h,kind);
  const notice=h.nodes.message.textContent;wait.resolve(response(h.book));await pending;
  assertManagerEmpty(h);assert.equal(h.nodes.message.textContent,notice);assert.equal(h.nodes.refresh.disabled,false);
 });
}
for(const action of ['register','update','delete','default']){
 for(const outcome of ['success','failure']){
  test(`stale ${action} ${outcome} after account change cannot change UI or trigger lookup`,async()=>{
   const wait=deferred();let checking=0;
   const h=harness({manage:true,sessionRequest:s=>{checking++;return s;},request:(url,options)=>options.method&&options.method!=='GET'?wait.promise:response(h.book)});
   await h.c.window.NanacocoaShippingAddresses.manage();
   let pending;
   if(action==='register'){fillManager(h);pending=h.form.events.submit({preventDefault(){}});}
   else if(action==='update'){h.nodes.list.all().find(x=>x.textContent==='수정').onclick();h.controls.label.value='새 이름';pending=h.form.events.submit({preventDefault(){}});}
   else pending=h.nodes.list.all().find(x=>x.textContent===(action==='delete'?'삭제':'기본으로 지정')).onclick();
   await tick();await invalidateManager(h);const notice=h.nodes.message.textContent;const count=h.calls.length,sessionChecks=checking;
   if(outcome==='success')wait.resolve(response(h.book));else wait.reject(new Error('synthetic delayed failure'));
   await pending;assertManagerEmpty(h);assert.equal(h.nodes.message.textContent,notice);
   assert.equal(h.calls.length,count);assert.equal(checking,sessionChecks);assert.equal(h.nodes.refresh.disabled,false);
  });
 }
}
for(const write of [false,true]){
 test(`pagehide ignores delayed ${write?'write':'read'} and its UI effects`,async()=>{
  const wait=deferred();let delayed=false;
  const h=harness({manage:true,request:()=>delayed?wait.promise:response(h.book)});
  await h.c.window.NanacocoaShippingAddresses.manage();delayed=true;
  let pending;
  if(write){fillManager(h);pending=h.form.events.submit({preventDefault(){}});}else pending=h.nodes.refresh.onclick();
  await tick();await invalidateManager(h,'pagehide');const notice=h.nodes.message.textContent;
  wait.resolve(response(h.book));await pending;assertManagerEmpty(h);assert.equal(h.nodes.message.textContent,notice);
 });
}
for(const outcome of ['success','failure']){
 test(`obsolete ${outcome} and finally cannot unlock a newer pending request`,async()=>{
  const old=deferred(),fresh=deferred();let phase=0;
  const h=harness({manage:true,request:()=>phase===1?old.promise:phase===2?fresh.promise:response(h.book)});
  await h.c.window.NanacocoaShippingAddresses.manage();phase=1;
  const oldPending=h.nodes.refresh.onclick();await tick();await invalidateManager(h);
  phase=2;const freshPending=h.nodes.refresh.onclick();await tick();
  assert.equal(h.nodes.refresh.disabled,true);const notice=h.nodes.message.textContent;const count=h.calls.length;
  if(outcome==='success')old.resolve(response(h.book));else old.reject(new Error('synthetic obsolete failure'));
  await oldPending;assert.equal(h.nodes.refresh.disabled,true);assert.equal(h.nodes.message.textContent,notice);assert.equal(h.calls.length,count);
  const newer={revision:4,checkoutAccount:'other',items:[{...primary,label:'new-account-only'}]};
  fresh.resolve(response(newer));await freshPending;assert.match(h.nodes.list.textContent,/new-account-only/);assert.equal(h.nodes.refresh.disabled,false);
 });
 test(`old ${outcome} after newer completion cannot replace new list or notice`,async()=>{
  const old=deferred();let phase=0;
  const newer={revision:4,checkoutAccount:'other',items:[{...primary,label:'new-account-only'}]};
  const h=harness({manage:true,request:()=>phase===1?old.promise:response(phase===2?newer:h.book)});
  await h.c.window.NanacocoaShippingAddresses.manage();phase=1;
  const pending=h.nodes.refresh.onclick();await tick();await invalidateManager(h);phase=2;
  await h.nodes.refresh.onclick();const notice=h.nodes.message.textContent;
  if(outcome==='success')old.resolve(response(h.book));else old.reject(new Error('synthetic obsolete failure'));
  await pending;assert.match(h.nodes.list.textContent,/new-account-only/);assert.equal(h.nodes.message.textContent,notice);
 });
}
test('invalidation during session verification prevents starting the mutation HTTP request',async()=>{
 const wait=deferred();let hold=false,checks=0;
 const h=harness({manage:true,sessionRequest:s=>hold&&++checks===1?wait.promise:s});
 await h.c.window.NanacocoaShippingAddresses.manage();fillManager(h);hold=true;
 const pending=h.form.events.submit({preventDefault(){}});await tick();await invalidateManager(h);
 const count=h.calls.length;wait.resolve({authenticated:true,checkoutAccount:'buyer',name:'synthetic'});await pending;
 assert.equal(h.calls.length,count);assert.equal(h.calls.filter(x=>x.options.method==='POST').length,0);assertManagerEmpty(h);
});
test('pagehide during initial session verification prevents the initial GET',async()=>{
 const wait=deferred();const h=harness({manage:true,sessionRequest:()=>wait.promise});
 const pending=h.c.window.NanacocoaShippingAddresses.manage();await tick();h.windowEvents.pagehide[0]();
 wait.resolve({authenticated:true,checkoutAccount:'buyer',name:'synthetic'});await pending;
 assert.equal(h.calls.length,0);assertManagerEmpty(h);
});
test('obsolete focus rejection cannot clear a freshly loaded account',async()=>{
 const wait=deferred();let delay=false;
 const h=harness({manage:true,sessionRequest:s=>delay?wait.promise:s,request:()=>response({...h.book,checkoutAccount:h.session.checkoutAccount,items:[{...primary,label:'current-only'}]})});
 await h.c.window.NanacocoaShippingAddresses.manage();delay=true;const focus=h.windowEvents.focus[0]();await tick();
 h.windowEvents.pagehide[0]();h.session.checkoutAccount='other';delay=false;await h.nodes.refresh.onclick();
 wait.resolve({authenticated:false});await focus;assert.match(h.nodes.list.textContent,/current-only/);
});
