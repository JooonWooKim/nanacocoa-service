const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const script = fs.readFileSync('src/main/resources/static/script.js', 'utf8');
const locationCode = script.slice(script.indexOf('function paymentOperationsLocation('), script.indexOf('function initLoginPage()'));
function parse(search) {
  const context = {URLSearchParams, window:{location:{search}}}; vm.createContext(context);
  vm.runInContext(locationCode, context);
  return context.paymentOperationsLoginReturnUrl();
}
test('login return accepts only the fixed page and validated query', () => {
  assert.equal(parse('?return=payment-operations&type=APPROVAL&id=1&target=https://evil.test'), 'payment-operations.html?page=0&size=20&type=APPROVAL&id=1');
  for (const query of ['type=OTHER&id=1', 'type=APPROVAL&id=0', 'type=APPROVAL&id=9223372036854775808', 'size=101', 'page=-1', 'kind=UNKNOWN']) {
    assert.equal(parse('?return=payment-operations&'+query), null);
  }
});
class Element {
  constructor(tag = 'div', text = '') { this.tagName=tag; this.textContent=text; this.children=[]; this.events={}; this.hidden=false; this.disabled=false; this.style={}; }
  append(node) {this.children.push(node);}
  replaceChildren() {this.children=[];}
  addEventListener(name, fn) {this.events[name]=fn;}
  setAttribute() {}
  all() {return [this, ...this.children.flatMap(node => node.all())];}
}
const row = {type:'APPROVAL', id:1, kind:'VERIFY_APPROVAL', status:'PENDING', amount:1000, retryCount:0, orderId:'test', canReconcile:true};
function harness(request) {
  const nodes = Object.fromEntries(['operations-search','operations-message','operations-content','operations-retry','operations-login'].map(id=>[id,new Element()]));
  const search=nodes['operations-search']; search.elements={kind:{value:''},orderId:{value:''}};
  const main=new Element(); Object.values(nodes).forEach(node=>main.append(node));
  const document={getElementById:id=>nodes[id],createElement:tag=>new Element(tag),querySelector:()=>main,querySelectorAll:()=>main.all().filter(node=>['button','input','select','a'].includes(node.tagName))};
  const ctx={URLSearchParams,AbortController,document,requestAuth:request,readJson:response=>response.json(),history:{replaceState(){}},location:{search:'?type=APPROVAL&id=1'},window:{confirm:()=>true,addEventListener(){},location:{search:''}}};
  vm.createContext(ctx); vm.runInContext(locationCode,ctx);
  vm.runInContext(fs.readFileSync('src/main/resources/static/payment-operations.js','utf8'),ctx);
  return {nodes,main};
}
const response=data=>({ok:true,status:200,json:async()=>({data})});
const tick=()=>new Promise(resolve=>setImmediate(resolve));
test('lost POST response performs a GET and never repeats the action', async()=>{
  let posts=0, gets=0;
  const {main,nodes}=harness(async(url,options)=>{
    if(options.method==='POST') {posts++; throw new Error('response lost');}
    gets++; return response(gets===1 ? row : {...row,status:'SUCCESS',pending:false,canReconcile:false});
  });
  await tick();
  await main.all().find(node=>node.textContent==='개별 복구').events.click();
  assert.equal(posts,1); assert.equal(gets,2);
  assert.match(nodes['operations-message'].textContent,/응답을 확인하지 못했습니다/);
  assert.equal(main.all().find(node=>node.textContent==='개별 복구').disabled,true);
});
test('concurrent clicks submit once and navigation is locked during recovery', async()=>{
  let release, posts=0;
  const {main,nodes}=harness(async(url,options)=>{
    if(options.method==='POST'){posts++; await new Promise(resolve=>release=resolve);}
    return response(row);
  });
  await tick();
  const action=main.all().find(node=>node.textContent==='개별 복구');
  const pending=action.events.click(); await tick();
  assert.equal(action.disabled,true);
  await action.events.click();
  nodes['operations-search'].events.submit({preventDefault(){}});
  assert.equal(posts,1); release(); await pending;
});
test('CSRF failure is explained and the POST is not automatically retried',async()=>{
  let posts=0;
  const {main,nodes}=harness(async(url,options)=>{
    if(options.method==='POST'){posts++; const e=new Error();e.code='CSRF_INVALID';throw e;}
    return response(row);
  });
  await tick(); await main.all().find(node=>node.textContent==='개별 복구').events.click();
  assert.equal(posts,1);assert.match(nodes['operations-message'].textContent,/보안 토큰/);
});
