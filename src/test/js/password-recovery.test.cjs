const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/password-recovery.js','utf8');
const raw = 'a'.repeat(43);
const response = (status,body) => ({ok:status<400,status,json:async()=>body});
const deferred = () => {let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};};
const tick = () => new Promise(r=>setImmediate(r));
function harness({reset=false,hash=reset?'#token='+raw:'',request}={}) {
  const inputs={email:{value:'synthetic@example.test'},newPassword:{value:'AfterRecovery123!'},passwordConfirmation:{value:'AfterRecovery123!'}};
  const button={disabled:false},message={textContent:''},events={},calls=[],history=[];
  const form={elements:inputs,querySelector:()=>button,querySelectorAll:()=>reset?[inputs.newPassword,inputs.passwordConfirmation]:[inputs.email],
    reportValidity:()=>true,addEventListener:(n,f)=>events[n]=f};
  const page={dataset:{recoveryKind:reset?'reset':'request'},querySelector:s=>s==='[data-recovery-form]'?form:message};
  const window={location:{hash,pathname:reset?'/password-reset.html':'/password-recovery.html'},
    history:{replaceState:(...args)=>history.push(args)},addEventListener:(n,f)=>events[n]=f};
  const c={document:{querySelector:()=>page},window,URLSearchParams,TextEncoder,
    fetch:async(url,options)=>{calls.push({url,options});return request?request(url,options):url==='/api/auth/csrf'
      ?response(200,{data:{headerName:'X-CSRF-TOKEN',token:'synthetic-csrf'}}):response(200,{});}};
  vm.createContext(c);vm.runInContext(source,c);
  return {inputs,button,message,events,calls,history,submit:()=>events.submit({preventDefault(){}})};
}
test('loading request and reset screens never sends a recovery POST; fragment is removed',()=>{
  const h=harness({reset:true});assert.equal(h.calls.length,0);assert.deepEqual(h.history[0],[null,'','/password-reset.html']);
});
test('request submits once with CSRF and waits for server acknowledgment',async()=>{
  const pending=deferred();const h=harness({request:(url)=>url.endsWith('/csrf')?response(200,{data:{headerName:'X-CSRF-TOKEN',token:'test'}}):pending.promise});
  const task=h.submit();await tick();assert.equal(h.button.disabled,true);assert.doesNotMatch(h.message.textContent,/발송됩니다/);
  await h.submit();assert.equal(h.calls.filter(c=>c.options.method==='POST').length,1);
  pending.resolve(response(202,{}));await task;assert.match(h.message.textContent,/받을 수 있는 경우/);
  assert.equal(h.calls[1].options.headers['X-CSRF-TOKEN'],'test');assert.equal(h.button.disabled,false);
});
test('valid reset clears password and token, disables duplicate submit, and requires manual login',async()=>{
  const h=harness({reset:true});await h.submit();assert.match(h.message.textContent,/다시 로그인/);
  assert.equal(h.inputs.newPassword.value,'');assert.equal(h.button.disabled,true);
  const n=h.calls.length;await h.submit();assert.equal(h.calls.length,n);
});
test('missing and malformed tokens keep reset disabled without requests',async()=>{
  for(const hash of ['', '#token=invalid']){const h=harness({reset:true,hash});await h.submit();assert.equal(h.calls.length,0);assert.equal(h.button.disabled,true);}
});
test('mismatch and UTF-8 overflow cannot send reset',async()=>{
  const h=harness({reset:true});h.inputs.passwordConfirmation.value='different';await h.submit();assert.equal(h.calls.length,0);
  h.inputs.newPassword.value=h.inputs.passwordConfirmation.value='가'.repeat(25);await h.submit();assert.equal(h.calls.length,0);
});
test('CSRF rejection refreshes token and requests explicit resubmission without replay',async()=>{
  const h=harness({request:url=>url.endsWith('/csrf')?response(200,{data:{headerName:'X-CSRF-TOKEN',token:'test'}}):response(403,{code:'CSRF_INVALID'})});
  await h.submit();assert.equal(h.calls.filter(c=>c.options.method==='POST').length,1);assert.equal(h.calls.length,3);
  assert.match(h.message.textContent,/다시 제출/);assert.equal(h.button.disabled,false);
});
test('invalidation while waiting for CSRF prevents mutation transmission',async()=>{
  const d=deferred();const h=harness({reset:true,request:()=>d.promise});const task=h.submit();
  h.events.pagehide();d.resolve(response(200,{data:{headerName:'X-CSRF-TOKEN',token:'test'}}));await task;
  assert.equal(h.calls.length,1);assert.equal(h.message.textContent,'');assert.equal(h.inputs.newPassword.value,'');
});
test('late success or failure after page exit cannot restore form or messages',async()=>{
  for(const fail of [false,true]) {
    const d=deferred();const h=harness({reset:true,request:url=>url.endsWith('/csrf')?response(200,{data:{headerName:'X',token:'test'}}):d.promise});
    const task=h.submit();await tick();h.events.pagehide();fail?d.reject(new Error('network')):d.resolve(response(200,{}));await task;
    assert.equal(h.message.textContent,'');assert.equal(h.inputs.newPassword.value,'');assert.equal(h.button.disabled,true);
  }
});
test('new request survives late failure and finally of old page request',async()=>{
  const old=deferred();let posts=0;
  const h=harness({request:url=>url.endsWith('/csrf')?response(200,{data:{headerName:'X',token:'test'}}):++posts===1?old.promise:response(202,{})});
  const task=h.submit();await tick();h.events.pagehide();h.events.pageshow({persisted:true});h.inputs.email.value='new@example.test';await h.submit();
  const text=h.message.textContent;old.reject(new Error('late'));await task;
  assert.equal(h.message.textContent,text);assert.equal(h.button.disabled,false);assert.equal(posts,2);
});
test('lost reset response never retries or falsely confirms, and clears sensitive inputs',async()=>{
  const h=harness({reset:true,request:url=>url.endsWith('/csrf')?response(200,{data:{headerName:'X',token:'test'}}):Promise.reject(new Error('lost'))});
  await h.submit();assert.match(h.message.textContent,/처리 결과를 확인할 수 없습니다/);assert.equal(h.inputs.newPassword.value,'');
  assert.equal(h.button.disabled,true);assert.equal(h.calls.length,2);
});
test('invalid token response gives a new-request path and does not auto login',async()=>{
  const h=harness({reset:true,request:url=>url.endsWith('/csrf')?response(200,{data:{headerName:'X',token:'test'}}):response(400,{code:'RECOVERY_01',error:'복구 안내를 다시 요청해주세요.'})});
  await h.submit();assert.match(h.message.textContent,/다시 요청/);assert.equal(h.button.disabled,true);assert.equal(h.calls.length,2);
});
