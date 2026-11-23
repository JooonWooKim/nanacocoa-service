(() => {
  const page = document.querySelector("[data-products-page]");
  if (!page) return;

  const form = page.querySelector("[data-discovery-form]");
  const category = form.elements.category;
  const grid = page.querySelector("[data-products-grid]");
  const count = page.querySelector("[data-discovery-count]");
  const total = page.querySelector("[data-products-total]");
  const status = page.querySelector("[data-discovery-state]");
  const retry = page.querySelector("[data-discovery-retry]");
  const recover = page.querySelector("[data-discovery-recover]");
  const pagination = page.querySelector("[data-discovery-pagination]");
  const previous = page.querySelector("[data-page-previous]");
  const next = page.querySelector("[data-page-next]");
  const position = page.querySelector("[data-page-position]");
  let activeRequest;

  function locationParams() {
    const source = new URLSearchParams(window.location.search);
    const params = new URLSearchParams();
    for (const field of ["q", "category", "page"]) {
      if (source.has(field)) params.set(field, source.get(field));
    }
    return params;
  }

  function navigate(params) {
    const query = params.toString();
    const url = window.location.pathname + (query ? "?" + query : "") + window.location.hash;
    if (url !== window.location.pathname + window.location.search + window.location.hash) {
      window.history.pushState(null, "", url);
    }
    load();
  }

  function fillCategories(items, selected) {
    const options = [new Option("전체", "")];
    for (const item of items) options.push(new Option(item.name, String(item.id)));
    if (selected && !options.some(option => option.value === selected)) {
      const unavailable = new Option("이용할 수 없는 카테고리", selected);
      unavailable.disabled = true;
      options.push(unavailable);
    }
    category.replaceChildren(...options);
    category.value = selected;
  }

  function recoverWith(text, params) {
    recover.hidden = false;
    recover.textContent = text;
    recover.onclick = () => navigate(params);
  }

  async function load() {
    activeRequest?.abort();
    const request = new AbortController();
    activeRequest = request;
    const params = locationParams();
    form.elements.q.value = params.get("q") || "";
    const selected = params.get("category") || "";
    fillCategories([], selected);
    status.textContent = "상품을 불러오는 중입니다.";
    grid.replaceChildren();
    grid.setAttribute("aria-busy", "true");
    count.hidden = pagination.hidden = retry.hidden = recover.hidden = true;
    try {
      const options = {cache: "no-store", signal: request.signal};
      const [categoriesResponse, response] = await Promise.all([
        requestAuth("/api/products/categories", options),
        requestAuth("/api/products/search?" + params, options),
      ]);
      const [categoriesBody, body] = await Promise.all([readJson(categoriesResponse), readJson(response)]);
      if (activeRequest !== request || request.signal.aborted) return;
      if (!categoriesResponse.ok || !Array.isArray(categoriesBody.data)) throw new Error();
      fillCategories(categoriesBody.data, selected);
      renderDiscoveryCategoryLinks(categoriesBody.data);
      if (response.status === 404 && body.code === "PRODUCTS_08") {
        status.textContent = "이 카테고리를 이용할 수 없습니다.";
        recoverWith("전체 상품으로 이동", new URLSearchParams());
        return;
      }
      if (response.status === 400) {
        status.textContent = "검색 조건이 올바르지 않습니다.";
        recoverWith("검색 조건 초기화", new URLSearchParams());
        return;
      }
      if (!response.ok || !Array.isArray(body.data?.items)) throw new Error();
      const {items, totalItems, page: pageNumber, totalPages} = body.data;
      total.textContent = String(totalItems);
      count.hidden = false;
      if (items.length === 0) {
        if (pageNumber > Math.max(1, totalPages)) {
          status.textContent = "이 페이지에는 상품이 없습니다.";
          params.delete("page");
          recoverWith("첫 페이지로 이동", params);
        } else {
          status.textContent = "조건에 맞는 상품이 없습니다.";
        }
        return;
      }
      grid.replaceChildren(...items.map(createProductCard));
      status.textContent = "";
      pagination.hidden = totalPages < 2;
      position.textContent = pageNumber + " / " + totalPages;
      previous.disabled = pageNumber <= 1;
      next.disabled = pageNumber >= totalPages;
      const move = delta => {
        const target = new URLSearchParams(params);
        target.set("page", String(pageNumber + delta));
        navigate(target);
      };
      previous.onclick = () => move(-1);
      next.onclick = () => move(1);
    } catch {
      if (activeRequest !== request || request.signal.aborted) return;
      status.textContent = "상품 목록을 불러오지 못했습니다.";
      retry.hidden = false;
    } finally {
      if (activeRequest === request && !request.signal.aborted) grid.setAttribute("aria-busy", "false");
    }
  }

  function search() {
    const params = new URLSearchParams();
    const term = form.elements.q.value.trim();
    if (term) params.set("q", term);
    if (category.value) params.set("category", category.value);
    navigate(params);
  }

  form.addEventListener("submit", event => {
    event.preventDefault();
    search();
  });
  category.addEventListener("change", search);
  retry.addEventListener("click", load);
  window.addEventListener("popstate", load);
  window.addEventListener("pagehide", () => activeRequest?.abort());
  window.addEventListener("pageshow", event => { if (event.persisted) load(); });
  load();
})();
