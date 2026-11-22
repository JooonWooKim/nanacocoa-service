const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const script=fs.readFileSync('src/main/resources/static/script.js','utf8');
const helpers=script.slice(script.indexOf('async function freshCartSession()'),script.indexOf('initCartNavigation();'));
const login=script.slice(script.indexOf('function cartLoginReturnUrl()'),script.indexOf('// 로그인 복귀에는'));
const item={itemId:10,version:0,productId:1,name:'합성 상품',unitPrice:41000,quantity:2,availability:'ON_SALE'};
class Element {
 constructor(tag='div'){this.tag=tag;this.children=[];this.disabled=false;this.checked=false;this.value='';}
 append(...nodes){this.children.push(...nodes);}
 replaceChildren(...nodes){this.children=[...nodes];}
 setAttribute(){}
 all(){return [this,...this.children.filter(x=>x instanceof Element).flatMap(x=>x.all())];}
 querySelectorAll(){return this.all().filter(e=>['button','input'].includes(e.tag));}
 reportValidity(){return /^\d+$/.test(this.value)&&Number(this.value)>0&&Number(this.value)<=2147483647;}
}
function harness({request=async()=>({ok:true,status:200,data:{items:[item],totalQuantity:2}}),session={authenticated:true,checkoutAccount:'buyer'},page=false}={}){
 const saved=new Map(),navigation=[],calls=[];
 const nodes=Object.fromEntries(['message','list','total','buy','all'].map(k=>[k,new Element()]));
 const main={querySelector:selector=>nodes[selector.match(/data-cart-(.*)\]/)[1]]};
 const c={URLSearchParams,Map,Set,Error,Number,JSON,document:{querySelector:s=>s==='[data-cart-page]'&&page?main:null,querySelectorAll:()=>[],createElement:tag=>new Element(tag),createTextNode:text=>text},window:{addEventListener(){},location:{search:'',assign:url=>navigation.push(url)}},
 loadAuthSession:async()=>({...session}),requestAuth:async(url,options)=>{calls.push({url,options});return request(url,options);},readJson:async r=>({data:r.data,error:r.error}),checkoutApiError:(body,fallback)=>body.error||fallback,
 formatPrice:v=>`${v}원`,inventoryLabel:()=> '품절',getProductImageUrl:()=>'/logo.png',randomUuid:()=> '11111111-1111-4111-8111-111111111111',writeCheckoutSessionValue:(k,v)=>saved.set(k,v),readCheckoutSessionValue:k=>saved.get(k)};
 vm.createContext(c);vm.runInContext(helpers+login,c);
 return {c,nodes,saved,navigation,calls,session};
}
test('selection rejects missing, foreign product, changed version, duplicates and nonpositive quantities',()=>{
 const {c}=harness();
 const s={checkoutAccount:'buyer',items:[{itemId:10,expectedVersion:0,productId:1,quantity:2,expectedUnitPrice:40000}]};
 assert.equal(c.validateCartSelection(s,{items:[item]})[0].unitPrice,41000);
 for(const change of [{expectedVersion:2},{quantity:0},{quantity:3},{productId:2},{quantity:1.5}])assert.throws(()=>c.validateCartSelection({...s,items:[{...s.items[0],...change}]},{items:[item]}));
 assert.throws(()=>c.validateCartSelection({...s,items:[s.items[0],s.items[0]]},{items:[item]}));
 assert.throws(()=>c.validateCartSelection(s,{items:[{...item,availability:'STOPPED'}]}));
});
test('order recovery must match every selected product and quantity',()=>{
 const {c}=harness();const selected=[{productId:1,quantity:2},{productId:2,quantity:1}];
 assert.equal(c.matchesCartOrder(selected,selected),true);assert.equal(c.matchesCartOrder(selected.slice(0,1),selected),false);
 assert.equal(c.matchesCartOrder([{productId:1,quantity:1},selected[1]],selected),false);
});
test('login return uses fixed cart/product/checkout pages and never trusts target',()=>{
 const {c,calls}=harness();
 c.window.location.search='?return=cart&target=https://evil.test';assert.equal(c.cartLoginReturnUrl(),'cart.html');
 c.window.location.search='?return=product&id=2';assert.equal(c.cartLoginReturnUrl(),'product-detail.html?id=2');
 c.window.location.search='?return=product&id=../admin';assert.equal(c.cartLoginReturnUrl(),null);
 c.window.location.search='?return=checkout&cart=11111111-1111-4111-8111-111111111111';assert.match(c.checkoutLoginReturnUrl(),/^checkout.html\?cart=/);assert.equal(calls.length,0);
});
test('anonymous cart offers login without running a mutation',async()=>{
 const h=harness({page:true,session:{authenticated:false}});await h.c.initCartPage();assert.equal(h.calls.length,0);assert.match(h.nodes.message.children[0],/로그인/);assert.equal(h.nodes.message.children[1].href,'login.html?return=cart');
});
test('selected purchase stores account-bound snapshot and double clicks navigate once',async()=>{
 const h=harness({page:true});await h.c.initCartPage();await Promise.all([h.nodes.buy.onclick(),h.nodes.buy.onclick()]);
 assert.equal(h.navigation.length,1);const snapshot=JSON.parse([...h.saved.values()][0]);assert.equal(snapshot.checkoutAccount,'buyer');assert.equal(snapshot.items[0].expectedVersion,0);assert.equal(snapshot.items[0].quantity,2);assert.equal(h.calls.length,1);
});
test('account switch prevents checkout of previous account selection',async()=>{
 const h=harness({page:true});await h.c.initCartPage();h.session.checkoutAccount='other';await h.nodes.buy.onclick();assert.equal(h.navigation.length,0);assert.equal(h.saved.size,0);assert.match(h.nodes.message.textContent,/계정/);
});
test('failed quantity update refreshes with GET and never repeats PUT',async()=>{
 const h=harness({page:true,request:async(url,options)=>options.method==='PUT'?{ok:false,status:409,error:'동시 수정'}:{ok:true,status:200,data:{items:[item],totalQuantity:2}}});await h.c.initCartPage();
 await h.nodes.list.all().find(e=>e.textContent==='수량 변경').onclick();
 assert.deepEqual(h.calls.map(x=>x.options.method||'GET'),['GET','PUT','GET']);assert.match(h.nodes.message.textContent,/동시 수정/);assert.equal(h.nodes.buy.disabled,false);
});
test('unavailable item stays visible but cannot be selected or purchased',async()=>{
 const h=harness({page:true,request:async()=>({ok:true,status:200,data:{items:[{...item,availability:'SOLD_OUT'}],totalQuantity:2}})});await h.c.initCartPage();assert.equal(h.nodes.buy.disabled,true);assert.equal(h.nodes.all.disabled,true);assert.equal(h.nodes.list.all().find(e=>e.type==='checkbox').disabled,true);
});
