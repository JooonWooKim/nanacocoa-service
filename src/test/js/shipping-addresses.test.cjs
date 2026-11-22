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
  replaceChildren(...nodes){this.children=[];this.append(...nodes);}
  setAttribute(name,value){this[name]=value;}
  setCustomValidity(){}
  addEventListener(name,fn){this.events[name]=fn;}
  all(){return [this,...this.children.filter(x=>x instanceof Element).flatMap(x=>x.all())];}
  querySelectorAll(){return this.all().filter(x=>['button','input'].includes(x.tag));}
  reportValidity(){return true;}
}
const response=book=>({ok:true,status:200,body:{data:book}});
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function harness({book={revision:1,checkoutAccount:'buyer',items:[primary]},request,manage=false}={}){
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
    loadAuthSession:async()=>({...session}),requestAuth:async(url,options)=>{calls.push({url,options});return request?request(url,options):response(book);},readJson:async r=>r.body,
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
