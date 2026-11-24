const {test} = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const source = fs.readFileSync("src/main/resources/static/restock-subscription.js", "utf8");
const tick = () => new Promise(resolve => setImmediate(resolve));

function harness(request) {
  const methods = [];
  const events = {};
  const button = {addEventListener(name, fn) { events[name] = fn; }};
  const status = {};
  const root = {querySelector: key => key.includes("action") ? button : status};
  const window = {location: {}, addEventListener: (name, fn) => { events[name] = fn; }};
  vm.runInNewContext(source + "\ninitRestockSubscription(page, product, '42')", {
    AbortController, window,
    page: {querySelector: () => root},
    product: {restockNotificationEnabled: true, availability: "SOLD_OUT"},
    requestAuth: async (path, options) => {
      methods.push([path, options.method || "GET"]);
      return request(path, options);
    },
    readJson: async response => response,
  });
  return {window, button, status, methods, events};
}

test("로그인 복귀는 조회만 수행하고 명시적 신청 이후에만 취소 버튼을 표시한다", async () => {
  let account = null;
  let active = false;
  const h = harness((path, options) => {
    if (path === "/api/auth/session") return {ok: true, authenticated: !!account, checkoutAccount: account};
    if (options.method === "PUT") {
      assert.equal(options.headers["X-Checkout-Account"], account);
      active = true;
    }
    return {ok: true, data: {active, canSubscribe: !active}};
  });
  await tick();
  await h.events.click();
  assert.equal(h.window.location.href, "login.html?return=product&id=42");
  account = "buyer";
  await h.events.focus();
  assert.ok(h.methods.every(([, method]) => method === "GET"));
  await h.events.click();
  assert.equal(h.button.textContent, "재입고 알림 취소");
  assert.equal(h.methods.filter(([, method]) => method === "PUT").length, 1);
});

test("계정 전환과 페이지 이탈 뒤 이전 신청 응답을 버린다", async () => {
  let account = "buyer", finish;
  const h = harness((path, options) => {
    if (path === "/api/auth/session") return {ok: true, authenticated: true, checkoutAccount: account};
    if (options.method === "PUT") return new Promise(resolve => { finish = resolve; });
    return {ok: true, data: {active: false, canSubscribe: true}};
  });
  await tick();
  const pending = h.events.click();
  await tick();
  account = "other";
  await h.events.focus();
  finish({ok: true, data: {active: true, canSubscribe: false}});
  await pending;
  assert.equal(h.button.textContent, "재입고 알림 신청");
  assert.equal(h.status.textContent, "");
  h.events.pagehide();
  assert.equal(h.button.hidden, true);
});
