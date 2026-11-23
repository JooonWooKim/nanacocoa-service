(() => {
  const faq = document.querySelector("[data-support-faq]");
  const page = document.querySelector("[data-support-inquiries]");
  if (!faq && !page) return;

  function element(tag, text, className) {
    const node = document.createElement(tag);
    node.textContent = text;
    if (className) node.className = className;
    return node;
  }

  async function call(url, options = {}) {
    const response = await requestAuth(url, {cache: "no-store", ...options});
    const body = await readJson(response);
    if (!response.ok) {
      const error = new Error(body.error || "요청을 처리하지 못했습니다.");
      error.status = response.status;
      error.code = body.code;
      throw error;
    }
    return url === "/api/auth/session" ? body : body.data;
  }

  if (faq) {
    const status = faq.querySelector("[data-faq-status]");
    const list = faq.querySelector("[data-faq-list]");
    const retry = faq.querySelector("[data-faq-retry]");
    async function load() {
      retry.hidden = true;
      status.textContent = "FAQ를 불러오는 중입니다.";
      try {
        const rows = await call("/api/support/faq");
        if (!Array.isArray(rows)) throw new Error();
        list.replaceChildren(...rows.map(row => {
          const item = element("article", "", "inventory-card");
          item.append(element("h3", row.question), element("p", row.answer, "support-text"));
          if (row.policyHref === "policies.html?document=SHIPPING_RETURN") {
            const link = element("a", "배송·취소·반품 안내");
            link.href = row.policyHref;
            item.append(link);
          }
          return item;
        }));
        status.textContent = rows.length ? "" : "게시된 FAQ가 없습니다.";
      } catch {
        list.replaceChildren();
        status.textContent = "FAQ를 불러오지 못했습니다.";
        retry.hidden = false;
      }
    }
    retry.addEventListener("click", load);
    load();
    return;
  }

  const admin = page.dataset.supportInquiries === "admin";
  const endpoint = admin ? "/api/admin/support/inquiries" : "/api/support/inquiries";
  const status = page.querySelector("[data-support-status]");
  const login = page.querySelector("[data-support-login]");
  const panel = page.querySelector("[data-support-private]");
  const list = page.querySelector("[data-support-list]");
  const detail = page.querySelector("[data-support-detail]");
  const previous = page.querySelector("[data-support-prev]");
  const next = page.querySelector("[data-support-next]");
  const create = page.querySelector("[data-support-create]");
  const reply = page.querySelector("[data-support-reply]");
  const filter = page.querySelector("[data-support-filter]");
  const refresh = page.querySelector("[data-support-refresh]");
  let pageNumber = 0;
  let orderPage = 0;
  let selectedId = null;
  let account = null;
  let submission = null;
  let active;
  let busy = false;

  function begin() {
    active?.abort();
    active = new AbortController();
    return active;
  }

  function clearDetail() {
    selectedId = null;
    detail.replaceChildren();
    detail.hidden = true;
    if (reply) { reply.reset(); reply.hidden = true; reply.querySelector('[type="submit"]').disabled = false; }
  }

  function clearPrivate() {
    active?.abort();
    busy = false;
    clearDetail();
    panel.hidden = true;
    list.replaceChildren();
    if (create) {
      create.reset();
      create.elements.orderId.replaceChildren(new Option("연결하지 않음", ""));
      const orders = create.querySelector("[data-support-orders]");
      orders.disabled = orders.hidden = false;
      orders.textContent = "내 주문 불러오기";
      create.querySelector('[type="submit"]').disabled = false;
      create.querySelector("[data-support-order-status]").textContent = "";
    }
    account = submission = null;
    pageNumber = orderPage = 0;
  }

  function failure(error, fallback) {
    if (error.status === 401 || error.status === 403 || error.code === "SUPPORT_05") {
      clearPrivate();
      login.hidden = false;
      status.textContent = admin ? "운영자 권한을 다시 확인해주세요." : "로그인 상태가 바뀌었습니다. 다시 확인해주세요.";
      return;
    }
    status.textContent = error.message || fallback;
  }

  async function assertAccount(signal) {
    const session = await call("/api/auth/session", {signal});
    if (!session.authenticated || !session.checkoutAccount || admin && !session.admin
        || account && account !== session.checkoutAccount) {
      const denied = new Error();
      denied.status = 401;
      throw denied;
    }
    if (!signal.aborted) account = session.checkoutAccount;
  }

  function displayDetail(row) {
    selectedId = row.id;
    detail.replaceChildren(element("h2", row.title), element("p", row.body, "support-text"),
      element("p", "접수: " + row.createdAt + " UTC"));
    if (row.orderId) detail.append(element("p", "관련 주문: " + row.orderId));
    detail.append(element("h3", row.status === "ANSWERED" ? "운영자 답변" : "답변 대기"));
    if (row.status === "ANSWERED") {
      detail.append(element("p", row.replyBody, "support-text"), element("p", "답변: " + row.repliedAt + " UTC"));
    }
    detail.hidden = false;
    if (reply) { reply.reset(); reply.hidden = row.status !== "PENDING"; }
  }

  async function loadDetail(id) {
    if (busy) return;
    const request = begin();
    const {signal} = request;
    clearDetail();
    status.textContent = "문의를 불러오는 중입니다.";
    try {
      await assertAccount(signal);
      const row = await call(endpoint + "/" + id, {signal});
      if (signal.aborted) return;
      displayDetail(row);
      status.textContent = "";
    } catch (error) {
      if (!signal.aborted) failure(error, "문의 상세를 불러오지 못했습니다.");
    }
  }

  async function loadList(number = 0, preserveDetail = false) {
    if (busy) return;
    const {signal} = begin();
    if (!preserveDetail) clearDetail();
    status.textContent = "문의 목록을 불러오는 중입니다.";
    previous.disabled = next.disabled = true;
    try {
      await assertAccount(signal);
      const params = new URLSearchParams({page: String(number)});
      if (filter?.value) params.set("status", filter.value);
      const result = await call(endpoint + "?" + params, {signal});
      if (signal.aborted) return;
      pageNumber = result.page;
      list.replaceChildren(...result.items.map(row => {
        const item = element("li", "");
        const button = element("button", row.title);
        button.type = "button";
        button.addEventListener("click", () => loadDetail(row.id));
        const state = row.status === "ANSWERED" ? "답변 완료" : "답변 대기";
        item.append(button, element("span", " · " + state + " · " + row.createdAt + " UTC"));
        return item;
      }));
      previous.disabled = pageNumber === 0;
      next.disabled = !result.hasNext;
      panel.hidden = false;
      login.hidden = true;
      status.textContent = result.items.length ? "" : "조회할 문의가 없습니다.";
    } catch (error) {
      if (!signal.aborted) {
        list.replaceChildren();
        failure(error, "문의 목록을 불러오지 못했습니다.");
      }
    }
  }

  async function write(form, url, payload, key) {
    if (busy) return;
    const {signal} = begin();
    busy = true;
    const button = form.querySelector('[type="submit"]');
    button.disabled = true;
    status.textContent = "저장 중입니다.";
    try {
      await assertAccount(signal);
      const headers = {"X-Checkout-Account": account};
      if (key) headers["Idempotency-Key"] = key;
      const row = await call(url, {method: "POST", headers, body: JSON.stringify(payload), signal});
      if (signal.aborted) return;
      submission = null;
      form.reset();
      displayDetail(row);
      const notice = key ? "문의가 접수됐습니다." : "답변을 저장했습니다.";
      busy = button.disabled = false;
      await loadList(0, true);
      if (!panel.hidden) status.textContent = notice + (status.textContent && list.children.length === 0 ? " " + status.textContent : "");
    } catch (error) {
      if (!signal.aborted) failure(error, "저장 결과를 확인하지 못했습니다. 내용을 확인한 뒤 다시 시도해주세요.");
    } finally {
      if (!signal.aborted) busy = button.disabled = false;
    }
  }

  if (create) {
    const orderButton = create.querySelector("[data-support-orders]");
    const orderStatus = create.querySelector("[data-support-order-status]");
    orderButton.addEventListener("click", async () => {
      if (busy) return;
      const {signal} = begin();
      busy = orderButton.disabled = true;
      try {
        await assertAccount(signal);
        const result = await call("/api/orders?page=" + orderPage, {signal});
        if (signal.aborted) return;
        for (const order of result.orders) {
          create.elements.orderId.append(new Option(order.orderId + " · " + order.productName, order.orderId));
        }
        orderPage++;
        orderButton.hidden = !result.hasNext;
        orderButton.textContent = "주문 더 불러오기";
        orderStatus.textContent = result.orders.length ? "주문 목록을 불러왔습니다." : "연결할 주문이 없습니다.";
      } catch (error) {
        if (!signal.aborted) failure(error, "주문을 불러오지 못했습니다. 다시 시도해주세요.");
      } finally {
        if (!signal.aborted) busy = orderButton.disabled = false;
      }
    });
    create.addEventListener("submit", event => {
      event.preventDefault();
      if (busy) return;
      const fields = create.elements;
      const payload = {title: fields.title.value.trim(), body: fields.body.value.trim(), orderId: fields.orderId.value || null};
      const fingerprint = JSON.stringify(payload);
      if (submission?.fingerprint !== fingerprint) submission = {fingerprint, key: randomUuid()};
      write(create, endpoint, payload, submission.key);
    });
  }
  if (reply) {
    reply.addEventListener("submit", event => {
      event.preventDefault();
      if (selectedId) write(reply, endpoint + "/" + selectedId + "/reply", {body: reply.elements.body.value.trim()});
    });
  }
  filter?.addEventListener("change", () => loadList());
  previous.addEventListener("click", () => loadList(pageNumber - 1));
  next.addEventListener("click", () => loadList(pageNumber + 1));
  refresh.addEventListener("click", () => selectedId ? loadDetail(selectedId) : loadList(pageNumber));
  window.addEventListener("focus", async () => {
    const observed = account;
    if (!observed) return;
    try {
      const session = await call("/api/auth/session");
      if (account === observed && (!session.authenticated || observed !== session.checkoutAccount || admin && !session.admin)) {
        failure({status: 401});
      }
    } catch {
      if (account === observed) failure({status: 401});
    }
  });
  window.addEventListener("pagehide", clearPrivate);
  window.addEventListener("pageshow", event => { if (event.persisted) loadList(); });
  loadList();
})();
