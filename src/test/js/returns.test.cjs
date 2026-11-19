const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const { webcrypto } = require("node:crypto");
const code = fs.readFileSync("src/main/resources/static/returns.js", "utf8");
class Element {
  constructor(tag = "div", text = "") {
    this.tag = tag; this.textContent = text; this.children = []; this.events = {}; this.value = "";
  }
  append(node) { this.children.push(node); }
  addEventListener(name, callback) { this.events[name] = callback; }
  setAttribute() {}
  focus() {}
  all() { return [this, ...this.children.flatMap(node => node.all())]; }
  querySelectorAll() { return this.all().filter(node => ["button", "input", "textarea", "select"].includes(node.tag)); }
}
function harness() {
  const saved = new Map();
  const context = { URLSearchParams, TextEncoder, crypto: webcrypto,
    sessionStorage: { getItem: key => saved.get(key), setItem: (key,value) => saved.set(key,value) },
    document: { querySelector: () => null, createElement: tag => new Element(tag) },
    window: { confirm: () => true }, location: { search: "" },
  };
  vm.createContext(context); vm.runInContext(code, context);
  context.saved = saved;
  return context;
}
test("retry key survives response loss without storing the raw reason", async () => {
  const h = harness(); const payload = { type: "RETURN", reason: "private-test-reason" };
  const first = await h.returnRequestKey("order:a", payload);
  assert.ok([...h.saved.values()].every(value => !value.includes(payload.reason)));
  assert.equal(await h.returnRequestKey("order:a", payload), first);
  assert.notEqual(await h.returnRequestKey("order:a", { ...payload, reason: "changed" }), first);
  assert.notEqual(await h.returnRequestKey("order:b", payload), first);
});
test("form suppresses a second submission and retains an identical retry key", async () => {
  const h = harness(), parent = new Element();
  let posts = 0, release, key, notify;
  let started = new Promise(resolve => { notify = resolve; });
  const form = h.returnReasonForm(parent, { actions: ["RETURN"], scope: "test",
    error: text => assert.fail(text),
    submit: async payload => { posts++; key = payload.requestKey; await new Promise(resolve => { release = resolve; notify(); }); },
  });
  form.all().find(node => node.tag === "select").value = "RETURN";
  form.all().find(node => node.tag === "textarea").value = "reason";
  const event = { preventDefault() {} };
  const first = form.events.submit(event);
  await started;
  await form.events.submit(event);
  assert.equal(posts, 1); assert.ok(key); release(); await first;
  started = new Promise(resolve => { notify = resolve; });
  const second = form.events.submit(event);
  await started;
  assert.equal(posts, 2); assert.equal(await h.returnRequestKey("test", {type:"RETURN",reason:"reason"}), key);
  release(); await second;
});
test("admin route strips unknown destinations and rejects invalid identifiers and filters", () => {
  const h = harness();
  assert.equal(h.returnOperationsLocation(new URLSearchParams("id=1&target=https://evil.test")).toString(), "page=0&id=1");
  for (const raw of ["id=0", "id=9223372036854775808", "page=-1", "type=OTHER", "status=OTHER"])
    assert.equal(h.returnOperationsLocation(new URLSearchParams(raw)), null);
});
test("CSRF and lost-response guidance require a read before another explicit action", () => {
  const h = harness();
  assert.match(h.returnError(403, "CSRF_INVALID"), /보안 토큰/);
  assert.match(h.returnError(409), /최신 상태/);
  assert.match(h.returnError(undefined), /재제출 전에/);
});
test("login return accepts only the fixed operations page", () => {
  const script = fs.readFileSync("src/main/resources/static/script.js", "utf8");
  const h = { URLSearchParams, location: { search: "?return=returns&id=1&target=https://evil.test" } };
  vm.createContext(h);
  vm.runInContext(script.slice(script.indexOf("function returnOperationsLoginReturnUrl()"), script.indexOf("function initLoginPage()")), h);
  assert.equal(h.returnOperationsLoginReturnUrl(), "returns.html?page=0&id=1");
  h.location.search = "?return=returns&id=9223372036854775808";
  assert.equal(h.returnOperationsLoginReturnUrl(), null);
});
