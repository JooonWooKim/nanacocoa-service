(() => {
  const section = document.querySelector("[data-category-admin]");
  if (!section) return;

  const endpoint = "/api/products/admin/categories";
  const controls = section.querySelector("[data-category-controls]");
  const message = section.querySelector("[data-category-message]");
  const refresh = section.querySelector("[data-category-refresh]");
  const create = section.querySelector("[data-category-create]");
  const list = section.querySelector("[data-category-list]");
  const assignment = section.querySelector("[data-category-assignment]");
  const productSelect = assignment.elements.productId;
  const categorySelect = assignment.elements.categoryId;
  const assignButton = assignment.querySelector("button");
  let categories = [];
  let products = [];
  let pending = false;
  let request;
  let generation = 0;

  async function call(url, {method = "GET", data} = {}, signal) {
    const response = await requestAuth(url, {
      method, body: data === undefined ? undefined : JSON.stringify(data), cache: "no-store", signal,
    });
    const body = await readJson(response);
    if (!response.ok) {
      const error = new Error(body.error || "요청을 처리하지 못했습니다.");
      error.authorization = response.status === 401 || response.status === 403 && body.code === "AUTH_02";
      throw error;
    }
    return body.data;
  }

  function updateAssignment() {
    const product = products.find(item => String(item.id) === productSelect.value);
    const current = product?.categoryId == null ? "" : String(product.categoryId);
    const options = [new Option("미분류", "")];
    for (const category of categories) {
      if (category.visible || String(category.id) === current) {
        const option = new Option(category.name + (category.visible ? "" : " (숨김)"), String(category.id));
        option.disabled = !category.visible;
        options.push(option);
      }
    }
    categorySelect.replaceChildren(...options);
    categorySelect.value = current;
    assignButton.disabled = !product || categorySelect.value === current;
  }

  function renderCategory(row) {
    const form = document.createElement("form");
    form.className = "inventory-card";
    const title = document.createElement("h3");
    title.textContent = row.name;
    const name = document.createElement("input");
    name.required = true;
    name.maxLength = 80;
    name.value = row.name;
    const order = document.createElement("input");
    order.type = "number";
    order.min = "0";
    order.max = "2147483647";
    order.step = "1";
    order.required = true;
    order.value = String(row.sortOrder);
    const visible = document.createElement("input");
    visible.type = "checkbox";
    visible.checked = row.visible;
    const button = document.createElement("button");
    button.textContent = "카테고리 저장";
    for (const [text, field] of [["이름 ", name], ["표시 순서 ", order], ["공개 ", visible]]) {
      const label = document.createElement("label");
      label.append(text, field);
      form.append(label);
    }
    form.prepend(title);
    form.append(button);
    form.addEventListener("submit", event => {
      event.preventDefault();
      run(signal => call(endpoint + "/" + row.id, {
        method: "PATCH", data: {name: name.value.trim(), sortOrder: Number(order.value), visible: visible.checked},
      }, signal), "카테고리를 저장했습니다.");
    });
    return form;
  }

  function render(selected) {
    list.replaceChildren(...categories.map(renderCategory));
    const options = products.map(product => new Option(product.name, String(product.id)));
    productSelect.replaceChildren(...options);
    if (options.some(option => option.value === selected)) productSelect.value = selected;
    updateAssignment();
    controls.hidden = false;
  }

  async function run(write, success) {
    if (pending) return;
    const current = ++generation;
    request = new AbortController();
    const {signal} = request;
    const selected = productSelect.value;
    pending = controls.disabled = refresh.disabled = true;
    let saved = false;
    try {
      if (write) {
        await write(signal);
        saved = true;
        if (generation !== current) return;
      }
      const [nextCategories, nextProducts] = await Promise.all([
        call(endpoint, {}, signal),
        call("/api/products/inventory", {}, signal),
      ]);
      if (generation !== current) return;
      categories = nextCategories;
      products = nextProducts;
      render(selected);
      if (write) create.reset();
      message.textContent = success || "카테고리와 상품 분류를 불러왔습니다.";
    } catch (error) {
      if (generation !== current) return;
      if (error.authorization) {
        controls.hidden = true;
        list.replaceChildren();
        productSelect.replaceChildren();
        categorySelect.replaceChildren();
        categories = [];
        products = [];
        create.reset();
        const login = document.createElement("a");
        login.href = "login.html?return=inventory";
        login.textContent = "운영자 로그인";
        message.replaceChildren("상품 분류를 관리하려면 운영자 권한이 필요합니다. ", login);
      } else {
        if (!write || saved) controls.hidden = true;
        message.textContent = saved
          ? "저장은 완료됐지만 목록을 갱신하지 못했습니다. 새로고침으로 확인해 주세요."
          : error.message || "목록을 불러오지 못했습니다. 다시 시도해 주세요.";
      }
    } finally {
      if (generation === current) pending = controls.disabled = refresh.disabled = false;
    }
  }

  create.addEventListener("submit", event => {
    event.preventDefault();
    const name = create.elements.name.value.trim();
    run(signal => call(endpoint, {method: "POST", data: {name}}, signal), "카테고리를 추가했습니다.");
  });
  productSelect.addEventListener("change", updateAssignment);
  categorySelect.addEventListener("change", () => {
    const product = products.find(item => String(item.id) === productSelect.value);
    assignButton.disabled = !product || categorySelect.selectedOptions[0]?.disabled
      || categorySelect.value === (product.categoryId == null ? "" : String(product.categoryId));
  });
  assignment.addEventListener("submit", event => {
    event.preventDefault();
    if (assignButton.disabled) return;
    const url = "/api/products/admin/" + encodeURIComponent(productSelect.value) + "/category";
    const categoryId = categorySelect.value ? Number(categorySelect.value) : null;
    run(signal => call(url, {method: "PUT", data: {categoryId}}, signal), "상품 분류를 저장했습니다.");
  });
  refresh.addEventListener("click", () => run());
  window.addEventListener("pagehide", () => {
    generation++;
    request?.abort();
    controls.hidden = true;
    pending = controls.disabled = refresh.disabled = false;
  });
  window.addEventListener("pageshow", event => { if (event.persisted) run(); });
  run();
})();
