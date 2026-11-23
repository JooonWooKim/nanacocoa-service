const {test} = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const source = fs.readFileSync("src/main/resources/static/product-discovery.js", "utf8");

class Element {
  constructor() { this.value = ""; this.hidden = false; this.events = {}; this.children = []; this.attributes = {}; }
  addEventListener(type, handler) { this.events[type] = handler; }
  setAttribute(key, value) { this.attributes[key] = value; }
  replaceChildren(...children) { this.children = children; }
}
class Option {
  constructor(text, value) { this.text = text; this.value = value; }
}
const response = (data, status = 200) => ({status, ok: status === 200, body: {data}});
const page = (name, page = 1, totalPages = 2) =>
  response({items: name ? [{name}] : [], page, totalPages, totalItems: totalPages ? 25 : 0});
const tick = () => new Promise(resolve => setImmediate(resolve));

function harness(path, search) {
  const elements = {};
  for (const name of ["form", "count", "state", "retry", "recover", "pagination"]) {
    elements["data-discovery-" + name] = new Element();
  }
  for (const name of ["products-grid", "products-total", "page-previous", "page-next", "page-position"]) {
    elements["data-" + name] = new Element();
  }
  const find = selector => elements[selector.slice(1, -1)];
  const form = elements["data-discovery-form"];
  form.elements = {q: new Element(), category: new Element()};
  const location = new URL(path, "http://example.test");
  const events = {};
  const calls = [];
  const window = {
    location, history: {pushState: (_state, _title, url) => { location.href = new URL(url, location).href; }},
    addEventListener: (type, handler) => { events[type] = handler; },
  };
  vm.runInNewContext(source, {
    AbortController, URLSearchParams, Option, window,
    document: {querySelector: () => ({querySelector: find})},
    renderDiscoveryCategoryLinks: () => {},
    createProductCard: product => product.name,
    readJson: async result => result.body,
    requestAuth: async (url, options) => {
      if (url === "/api/products/categories") return response([{id: 2, name: "볼"}]);
      calls.push({url, signal: options.signal});
      return search(url);
    },
  });
  return {elements, form, location, events, calls, node: name => elements["data-discovery-" + name]};
}

test("URL 상태와 페이지 이동을 복원하고 새 검색에서 첫 페이지로 이동한다", async () => {
  const h = harness("/products.html?q=old&category=2&page=2", url => page(url.includes("page=2") ? "둘째" : "첫째"));
  await tick();
  assert.equal(h.form.elements.q.value, "old");
  assert.equal(h.form.elements.category.value, "2");
  assert.deepEqual(h.elements["data-products-grid"].children, ["둘째"]);
  h.form.elements.q.value = "  새 검색 ";
  h.form.events.submit({preventDefault() {}});
  await tick();
  assert.equal(h.location.searchParams.get("q"), "새 검색");
  assert.equal(h.location.searchParams.has("page"), false);
  h.elements["data-page-next"].onclick();
  await tick();
  assert.equal(h.location.searchParams.get("page"), "2");
  h.location.search = "?q=old&category=2&page=2";
  h.events.popstate();
  await tick();
  assert.equal(h.form.elements.q.value, "old");
});

test("늦은 응답이 현재 검색 결과를 덮어쓰지 않는다", async () => {
  let resolveOld;
  const h = harness("/products.html?q=old", url => url.includes("old")
    ? new Promise(resolve => { resolveOld = resolve; }) : page("새 결과"));
  h.form.elements.q.value = "fresh";
  h.form.events.submit({preventDefault() {}});
  await tick();
  resolveOld(page("이전 결과"));
  await tick();
  assert.equal(h.calls[0].signal.aborted, true);
  assert.deepEqual(h.elements["data-products-grid"].children, ["새 결과"]);
});

test("실패 시 총계를 숨기고 동일 조건으로 재시도한다", async () => {
  let tries = 0;
  const h = harness("/products.html?q=keep&category=2", () => ++tries === 1 ? response(null, 500) : page(null, 1, 0));
  await tick();
  assert.equal(h.node("count").hidden, true);
  assert.equal(h.node("retry").hidden, false);
  h.node("retry").events.click();
  await tick();
  assert.equal(h.calls[0].url, h.calls[1].url);
  assert.match(h.node("state").textContent, /조건에 맞는 상품이 없습니다/);
  assert.equal(h.elements["data-products-total"].textContent, "0");
});

test("숨긴 분류와 범위 밖 페이지는 별도의 복구 동선을 제공한다", async () => {
  const h = harness("/products.html?category=7", url => url.includes("category=7")
    ? {status: 404, ok: false, body: {code: "PRODUCTS_08"}} : page("전체 상품"));
  await tick();
  assert.match(h.node("state").textContent, /카테고리를 이용할 수 없습니다/);
  h.node("recover").onclick();
  await tick();
  assert.equal(h.location.search, "");
  const out = harness("/products.html?q=keep&category=2&page=9", () => page(null, 9, 2));
  await tick();
  assert.equal(out.node("recover").textContent, "첫 페이지로 이동");
  out.node("recover").onclick();
  assert.equal(out.location.searchParams.get("category"), "2");
  assert.equal(out.location.searchParams.has("page"), false);
});
