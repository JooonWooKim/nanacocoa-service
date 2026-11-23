const {test} = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const source = fs.readFileSync("src/main/resources/static/order-notifications.js", "utf8");
const tick = () => new Promise(resolve => setImmediate(resolve));

class Element {
  events = {};
  children = [];
  addEventListener(type, handler) { this.events[type] = handler; }
  replaceChildren(...children) { this.children = children; }
  append(...children) { this.children.push(...children); }
}

function harness(request, menu = false) {
  const nodes = new Map(), events = {}, calls = [];
  const node = name => {
    if (!nodes.has(name)) nodes.set(name, new Element());
    return nodes.get(name);
  };
  const root = {dataset: {}, querySelector: key => key.includes("-filter]") ? null : node(key.slice(19, -1))};
  vm.runInNewContext(source, {
    AbortController, URLSearchParams,
    document: {
      querySelector: key => key === "[data-order-notification-count]" ? (menu ? node("count") : null) : (menu ? null : root),
      createElement: () => new Element(),
    },
    window: {addEventListener: (type, fn) => { events[type] = fn; }},
    requestAuth: async (path, options) => { calls.push({path,method:options.method}); return request(path,options); },
    readJson: async response => response,
  });
  return {node, events, calls};
}

const row = {id: 4, orderId: "NC_fixture", type: "SHIPPED", confirmedAt: "2026-11-23T00:00:00"};
const page = item => ({ok: true, data: {items:[item],page:0,hasNext:false,unreadCount:item.readAt ? 0 : 1}});

test("회원 알림은 주문 링크를 만들고 읽음 처리 후 새 미읽음 수를 조회한다", async () => {
  let read = false;
  const h = harness((path, options) => {
    if (options.method === "PUT") { read = true; return {ok:true,data:{}}; }
    return page({...row, readAt: read ? row.confirmedAt : null});
  });
  await tick();
  const card = h.node("list").children[0];
  assert.equal(card.children.find(child => child.href)?.href, "orders.html?orderId=NC_fixture");
  assert.equal(h.node("unread").textContent, "미읽음 1개");
  card.children.find(child => child.events.click).events.click();
  await tick();
  assert.equal(h.node("unread").textContent, "미읽음 0개");
  assert.equal(h.calls.filter(call => call.method === "PUT").length, 1);
});

test("페이지 이탈 뒤 늦은 응답을 버리고 복귀 시 조회만 재실행한다", async () => {
  let resolve;
  let calls = 0;
  const h = harness(() => ++calls === 1 ? new Promise(r => { resolve = r; }) : {ok:false,status:401});
  h.events.pagehide();
  h.events.pageshow({persisted:true});
  await tick();
  resolve(page(row));
  await tick();
  assert.equal(h.node("list").children.length, 0);
  assert.equal(h.node("panel").hidden, true);
  assert.equal(h.node("login").hidden, false);
  assert.deepEqual(h.calls.map(call => call.method), ["GET","GET"]);
});

test("읽음 응답 중 다른 계정으로 전환하면 이전 목록과 응답을 버린다", async () => {
  let resolve;
  let currentAccount = true;
  const h = harness((path, {method}) => {
    if (method === "PUT") return new Promise(r => { resolve = r; });
    return currentAccount ? page(row) : {ok:false,status:401};
  });
  await tick();
  h.node("list").children[0].children.find(child => child.events.click).events.click();
  currentAccount = false;
  h.events.focus();
  await tick();
  assert.equal(h.node("list").children.length, 0);
  assert.equal(h.node("login").hidden, false);
  resolve({ok:true,data:{}});
  await tick();
  assert.equal(h.node("login").hidden, false);
  assert.deepEqual(h.calls.map(call => call.method), ["GET","PUT","GET"]);
});

test("회원 메뉴는 미읽음 수를 표시하고 페이지 이탈 시 지운다", async () => {
  const h = harness(() => page(row), true);
  await tick();
  assert.equal(h.node("count").textContent, "(미읽음 1개)");
  h.events.pagehide();
  assert.equal(h.node("count").textContent, "");
});
