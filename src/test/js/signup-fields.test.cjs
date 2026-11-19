const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const script=fs.readFileSync('src/main/resources/static/script.js','utf8');
const helpers=script.slice(script.indexOf('function normalizeAddressPart'),script.indexOf('function loadTossPaymentsSdk'));
class Input {
  constructor(value=''){this.value=value;this.events={};this.type='password';this.hidden=true;this.dataset={};this.invalid='';}
  addEventListener(name,fn){this.events[name]=fn;}
  setCustomValidity(value){this.invalid=value;}
  setAttribute(name,value){this[name]=value;}
  focus(){this.focused=true;}
}
function harness(mode='success'){
  const selectors=['[name="password"]','[name="passwordConfirm"]','[data-password-confirm-status]','[data-address-postal-code]',
    '[data-address-basic]','[data-address-detail]','[data-address-search]','[name="address"]','[data-phone-middle]','[data-phone-last]',
    '[name="phoneNumber"]','[data-address-search-status]'];
  const nodes=Object.fromEntries(selectors.map(s=>[s,new Input()]));
  nodes['[name="password"]'].value='fixture-password';nodes['[name="passwordConfirm"]'].value='fixture-password';
  nodes['[data-address-postal-code]'].value='12345';nodes['[data-address-basic]'].value=' 기본   주소 ';
  nodes['[data-phone-middle]'].value='1234';nodes['[data-phone-last]'].value='5678';
  const toggle=new Input();toggle.dataset.passwordToggle='password';
  const form={querySelector:s=>nodes[s],querySelectorAll:()=>[toggle],reportValidity:()=>Object.values(nodes).every(n=>!n.invalid)};
  let callback;
  const window=mode==='script-fail'?{}:{kakao:{Postcode:class{constructor(options){callback=options.oncomplete;}open(){if(mode==='popup-fail')throw new Error('blocked');}}}};
  const document={createElement:()=>new Input(),head:{append(element){element.events.error();}}};
  const context={window,document};vm.createContext(context);
  vm.runInContext('const kakaoPostcodeScriptUrl="https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";let kakaoPostcodePromise;'+helpers+fs.readFileSync('src/main/resources/static/signup-fields.js','utf8'),context);
  const state=context.setupSignupFields(form,()=>false);
  return {nodes,toggle,state,select(data){nodes['[data-address-search]'].events.click();callback(data);}};
}
test('password confirmation blocks mismatches and visibility toggle keeps the value',()=>{
  const h=harness();h.nodes['[name="passwordConfirm"]'].value='wrong-password';
  assert.equal(h.state.validate(),false);assert.match(h.nodes['[data-password-confirm-status]'].textContent,/일치하지/);
  h.toggle.events.click();assert.equal(h.nodes['[name="password"]'].type,'text');assert.equal(h.nodes['[name="password"]'].value,'fixture-password');
  h.nodes['[name="passwordConfirm"]'].value='fixture-password';h.nodes['[name="passwordConfirm"]'].events.input();
  assert.equal(h.state.validate(),true);
});
test('postcode selection clears old details and formats existing address/phone payload fields',()=>{
  const h=harness();h.nodes['[data-address-detail]'].value='이전 상세주소';
  h.select({zonecode:' 54321 ',userSelectedType:'R',roadAddress:'가상 도로명 주소'});
  assert.equal(h.nodes['[data-address-detail]'].value,'');assert.equal(h.nodes['[data-address-detail]'].focused,true);
  h.nodes['[data-address-detail]'].value=' 101동  202호 ';assert.equal(h.state.validate(),true);
  assert.equal(h.nodes['[name="address"]'].value,'[54321] 가상 도로명 주소 101동 202호');
  assert.equal(h.nodes['[name="phoneNumber"]'].value,'010-1234-5678');
  h.select({zonecode:'12345',userSelectedType:'J',jibunAddress:'가상 지번 주소'});
  assert.equal(h.nodes['[data-address-basic]'].value,'가상 지번 주소');
});
test('script and popup failures retain manual address entry and signup eligibility',async()=>{
  for(const mode of ['script-fail','popup-fail']){
    const h=harness(mode);await new Promise(r=>setImmediate(r));
    if(mode==='popup-fail')h.nodes['[data-address-search]'].events.click();
    assert.match(h.nodes['[data-address-search-status]'].textContent,/직접 입력/);
    assert.equal(h.nodes['[data-address-search]'].disabled,true);
    assert.equal(h.state.validate(),true);assert.equal(h.nodes['[name="address"]'].value,'[12345] 기본 주소');
  }
});
test('postcode format and total combined address length are validated before submission',()=>{
  const h=harness();h.nodes['[data-address-postal-code]'].value='1234';assert.equal(h.state.validate(),false);
  h.nodes['[data-address-postal-code]'].value='12345';h.nodes['[data-address-basic]'].value='가'.repeat(492);
  assert.equal(h.state.validate(),true);assert.equal(h.nodes['[name="address"]'].value.length,500);
  h.nodes['[data-address-detail]'].value='나';assert.equal(h.state.validate(),false);
  h.nodes['[data-address-detail]'].value='';assert.equal(h.state.validate(),true);
});
test('correcting manual address fields clears stale custom validation errors',()=>{
  const h=harness();h.nodes['[data-address-postal-code]'].value='1234';
  h.nodes['[data-address-basic]'].value='   ';assert.equal(h.state.validate(),false);
  h.nodes['[data-address-postal-code]'].value='12345';h.nodes['[data-address-postal-code]'].events.input();
  h.nodes['[data-address-basic]'].value='수정한 주소';h.nodes['[data-address-basic]'].events.input();
  assert.equal(h.nodes['[data-address-postal-code]'].invalid,'');
  assert.equal(h.nodes['[data-address-basic]'].invalid,'');assert.equal(h.state.validate(),true);
});
