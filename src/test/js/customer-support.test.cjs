const {test} = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const source = fs.readFileSync("src/main/resources/static/customer-support.js", "utf8");
const tick = () => new Promise(resolve => setImmediate(resolve));

class Element {
  events = {};
  children = [];
  elements = {};
  value = "";
  hidden = false;
  addEventListener(type, handler) { this.events[type] = handler; }
  replaceChildren(...children) { this.children = children; }
  append(...children) { this.children.push(...children); }
  reset() { for (const item of Object.values(this.elements)) item.value = ""; }
}
function harness(handler) {
  const nodes = new Map();
  function node(key) {
    if (!nodes.has(key)) nodes.set(key, new Element());
    return nodes.get(key);
  }
  const form = node("[data-support-create]");
  form.elements = {title: new Element(), body: new Element(), orderId: new Element()};
  form.querySelector = node;
  const root = {dataset: {}, querySelector: selector => /create|filter|reply/.test(selector)
    && !selector.includes("create") ? null : node(selector)};
  const events = {};
  const calls = [];
  let keys = 0;
  vm.runInNewContext(source, {
    AbortController, URLSearchParams, Option: class { constructor(text, value) { this.text = text; this.value = value; } },
    document: {querySelector: selector => selector === "[data-support-inquiries]" ? root : null,
      createElement: () => new Element()},
    window: {addEventListener: (type, fn) => { events[type] = fn; }},
    randomUuid: () => "key-" + ++keys,
    requestAuth: async (url, options) => {
      calls.push({url, options});
      if (url === "/api/auth/session") return {ok: true, authenticated: true, checkoutAccount: "fixture"};
      if (url.includes("?page")) return {ok: true, data: {items: [], page: 0, hasNext: false}};
      return handler(url, options);
    },
    readJson: async response => response,
  });
  return {form, events, calls, node, submit: () => form.events.submit({preventDefault() {}})};
}

test("응답 유실 뒤 같은 입력은 기존 키를 재사용하고 수정한 입력에는 새 키를 쓴다", async () => {
  const h = harness(() => Promise.reject(new Error("응답 유실")));
  await tick();
  h.form.elements.title.value = "제목";
  h.form.elements.body.value = "본문";
  h.submit();
  await tick();
  assert.equal(h.form.elements.body.value, "본문");
  h.submit();
  await tick();
  h.form.elements.body.value = "수정한 본문";
  h.submit();
  await tick();
  const writes = h.calls.filter(call => call.options.method === "POST");
  assert.deepEqual(writes.map(call => call.options.headers["Idempotency-Key"]), ["key-1", "key-1", "key-2"]);
  assert.equal(writes[0].options.headers["X-Checkout-Account"], "fixture");
});

test("뒤로 가기 복원은 진행 중 쓰기를 반복하지 않고 늦은 응답도 버린다", async () => {
  let resolve;
  const h = harness(() => new Promise(r => { resolve = r; }));
  await tick();
  h.form.elements.title.value = "합성 제목";
  h.form.elements.body.value = "합성 본문";
  h.submit();
  await tick();
  h.events.pagehide();
  h.events.pageshow({persisted: true});
  resolve({ok: true, data: {id: 1, title: "오래된 제목", body: "오래된 내용", status: "PENDING"}});
  await tick();
  assert.equal(h.form.elements.body.value, "");
  assert.equal(h.calls.filter(call => call.options.method === "POST").length, 1);
  assert.equal(h.calls.filter(call => call.url.includes("?page")).length, 2);
  assert.equal(h.node("[data-support-detail]").children.length, 0);
});
