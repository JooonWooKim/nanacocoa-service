const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('src/main/resources/static/policies.js', 'utf8');
const script = fs.readFileSync('src/main/resources/static/script.js', 'utf8');
const signupCode = script.slice(script.indexOf('function initSignupPage()'), script.indexOf('function renderAnonymousSession'));
class Element {
  constructor(tag = 'div') { this.tag = tag; this.children = []; this.events = {}; this.textContent = ''; this.disabled = false; this.checked = false; }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = [...nodes]; }
  addEventListener(name, callback) { this.events[name] = callback; }
  removeAttribute(name) { delete this[name]; }
  all() { return [this, ...this.children.flatMap(n => n.all())]; }
  set innerHTML(_) { throw new Error('Policy HTML must never be executed'); }
}
const docs = version => [
  { documentCode: 'TERMS', version, title: '서비스 약관', consentMode: 'REQUIRED' },
  { documentCode: 'EMAIL_RECEIVE', version, title: '이메일 안내', consentMode: 'OPTIONAL_EMAIL' },
];
const response = (data, status = 200) => ({ ok: status < 400, status, json: async () => status < 400 ? { data } : data });
const tick = () => new Promise(resolve => setImmediate(resolve));
function harness(fetcher, pageMode = false, search = '') {
  const nodes = Object.fromEntries(['signup-policies', 'policy-status', 'policy-retry', 'policy-page-status', 'policy-content', 'policy-page-retry'].map(key => [key, new Element()]));
  const button = new Element('button'); button.textContent = '회원가입'; button.disabled = true;
  const form = new Element('form');
  const refusal = new Element('input');
  form.querySelector = selector => selector === '.login-submit' ? button : selector === '[name="emailReceiveConsent"][value="N"]' ? refusal : nodes[selector.slice(6, -1)];
  const page = new Element(); page.querySelector = selector => nodes[selector.slice(6, -1)];
  const document = { querySelector: selector => selector === '[data-policy-page]' ? (pageMode ? page : null) : selector === '[data-signup-form]' ? form : null, createElement: tag => new Element(tag) };
  const fields = { email: 'fixture@example.test', password: 'fixture-password', name: '가상 회원', address: '가상 주소', phoneNumber: '010-1111-2222', emailReceiveConsent: 'N' };
  Object.defineProperty(refusal, 'checked', { set(value) { if (value) fields.emailReceiveConsent = 'N'; } });
  const context = { document, window: { document, location: { search, href: '' } }, URLSearchParams,
    fetch: fetcher, FormData: class { get(key) { return fields[key]; } },
    requestAuth: (url, options) => fetcher(url, options), readJson: r => r.json(),
    authApi: { signup: '/api/auth/signup' }, setAuthMessage: value => { context.message = value; },
  };
  vm.createContext(context); vm.runInContext(code, context);
  if (!pageMode) vm.runInContext(signupCode + '\ninitSignupPage();', context);
  return { context, nodes, button, form, fields };
}
test('unapproved catalog keeps signup disabled and sends no writes', async () => {
  let posts = 0;
  const h = harness(async (url, options) => { if (options?.method === 'POST') posts++; return response({ signupReady: false, documents: [] }); });
  await tick(); await h.form.events.submit({ preventDefault() {} });
  assert.equal(h.button.disabled, true); assert.equal(posts, 0);
  assert.match(h.nodes['policy-status'].textContent, /준비 중/);
});
test('failed policy fetch can be retried without clearing profile fields or consenting automatically', async () => {
  let gets = 0;
  const h = harness(async () => { if (++gets === 1) throw new Error('network unavailable'); return response({ signupReady: true, documents: docs('v1') }); });
  await tick(); assert.equal(h.button.disabled, true);
  assert.match(h.nodes['policy-status'].textContent, /연결 상태를 확인/);
  await h.nodes['policy-retry'].events.click();
  assert.equal(h.button.disabled, false); assert.equal(h.fields.name, '가상 회원');
  const checkbox = h.nodes['signup-policies'].all().find(n => n.tag === 'input');
  assert.equal(checkbox.checked, false); assert.equal(checkbox.required, true);
});
test('changed version fetches new documents and requires a new explicit signup', async () => {
  let gets = 0, posts = 0;
  const h = harness(async (url, options) => {
    if (options?.method === 'POST') { posts++; return response({ code: 'POLICY_02', error: '정책을 다시 확인해 주세요.' }, 409); }
    return response({ signupReady: true, documents: docs(++gets === 1 ? 'v1' : 'v2') });
  });
  await tick(); h.nodes['signup-policies'].all().find(n => n.tag === 'input').checked = true;
  h.fields.emailReceiveConsent = 'Y';
  await h.form.events.submit({ preventDefault() {} });
  assert.equal(posts, 1); assert.equal(gets, 2);
  assert.equal(h.nodes['signup-policies'].all().find(n => n.tag === 'input').checked, false);
  assert.equal(h.fields.emailReceiveConsent, 'N');
  assert.match(h.nodes['signup-policies'].all().find(n => n.tag === 'a').href, /version=v2/);
  assert.equal(h.context.window.location.href, '');
});
test('concurrent submits are suppressed and optional refusal is sent with the displayed version', async () => {
  let finish, posts = 0, payload;
  const h = harness(async (url, options) => {
    if (options?.method === 'POST') { posts++; payload = JSON.parse(options.body); await new Promise(resolve => finish = resolve); return response(null,201); }
    return response({ signupReady: true, documents: docs('v1') });
  });
  await tick(); h.nodes['signup-policies'].all().find(n => n.tag === 'input').checked = true;
  const pending = h.form.events.submit({ preventDefault() {} });
  await h.form.events.submit({ preventDefault() {} });
  assert.equal(posts,1); assert.equal(h.nodes['policy-retry'].disabled,true);
  assert.deepEqual(payload.consents, [{ documentCode:'TERMS',version:'v1',agreed:true },{ documentCode:'EMAIL_RECEIVE',version:'v1',agreed:false }]);
  finish(); await pending; assert.equal(h.context.window.location.href,'login.html');
});
test('CSRF rejection and lost response never automatically repeat signup', async () => {
  for (const fail of ['csrf','lost']) {
    let posts = 0;
    const h = harness(async (url, options) => {
      if (options?.method === 'POST') { posts++; if (fail === 'lost') throw new Error('response lost'); return response({code:'CSRF_INVALID',error:'보안 토큰을 다시 확인해 주세요.'},403); }
      return response({ signupReady:true, documents:docs('v1') });
    });
    await tick(); await h.form.events.submit({ preventDefault() {} }); await tick();
    assert.equal(posts,1); assert.equal(h.context.window.location.href,''); assert.equal(h.button.disabled,false);
  }
});
test('historical policy body is rendered as text and never HTML', async () => {
  const malicious = '<script>globalThis.compromised=true</script>';
  const h = harness(async () => response({ title:'안내',version:'v1',effectiveAt:'2020-01-01T00:00:00Z',body:malicious }),true,'?document=TERMS&version=v1');
  await tick();
  assert.equal(h.nodes['policy-content'].all().find(n => n.className === 'policy-body').textContent,malicious);
  assert.equal(h.context.compromised,undefined);
});
test('current document links resolve their current version and missing documents offer retry', async () => {
  const calls=[];
  const h=harness(async url=> { calls.push(url);return url==='/api/policies'?response({documents:docs('v2')}):response({title:'약관',version:'v2',body:'plain'}); },true,'?document=TERMS');
  await tick(); assert.deepEqual(calls,['/api/policies','/api/policies/TERMS/v2']);
  const missing=harness(async()=>response({code:'POLICY_03',error:'게시된 정책 문서를 찾을 수 없습니다.'},404),true,'?document=TERMS&version=unknown');
  await tick();assert.match(missing.nodes['policy-page-status'].textContent,/찾을 수 없습니다/);assert.equal(missing.nodes['policy-page-retry'].disabled,false);
});
