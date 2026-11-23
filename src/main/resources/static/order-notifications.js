(() => {
  const memberUrl = "/api/notifications";
  const menuCount = document.querySelector("[data-order-notification-count]");
  if (menuCount) {
    let lookup;
    function clearCount() {
      lookup?.abort();
      menuCount.textContent = "";
    }
    async function loadCount() {
      clearCount();
      lookup = new AbortController();
      const {signal} = lookup;
      try {
        const response = await requestAuth(memberUrl, {signal, cache: "no-store"});
        const body = response.ok ? await readJson(response) : null;
        if (!signal.aborted && Number.isSafeInteger(body?.data?.unreadCount)) {
          menuCount.textContent = "(미읽음 " + body.data.unreadCount + "개)";
        }
      } catch {
        // The menu link remains available when the count cannot be loaded.
      }
    }
    window.addEventListener("focus", loadCount);
    window.addEventListener("pagehide", clearCount);
    window.addEventListener("pageshow", event => { if (event.persisted) loadCount(); });
    loadCount();
  }

  const root = document.querySelector("[data-order-notifications]");
  if (!root) return;

  const select = name => root.querySelector("[data-notification-" + name + "]");
  const admin = root.dataset.orderNotifications === "admin";
  const url = admin ? "/api/admin/notifications" : memberUrl;
  const list = select("list");
  const panel = select("panel");
  const status = select("status");
  const login = select("login");
  const previous = select("prev");
  const next = select("next");
  const filter = select("filter");
  const unread = select("unread");
  const labels = {PAYMENT_COMPLETED: "결제 완료", SHIPPED: "출고 완료", REFUND_COMPLETED: "환불 완료",
    SUPPORT_ANSWERED: "문의에 답변이 등록되었습니다"};
  const statuses = {PENDING: "처리 대기", PUBLISHED: "게시 완료", FAILED: "처리 실패"};
  let active;
  let page = 0;
  let busy = false;

  function begin() {
    active?.abort();
    active = new AbortController();
    return active.signal;
  }

  async function call(path, signal, method = "GET") {
    const response = await requestAuth(path, {signal, method, cache: "no-store"});
    const body = await readJson(response);
    if (!response.ok || !body.data) {
      const error = new Error(body.error || "요청을 처리하지 못했습니다.");
      error.status = response.status;
      throw error;
    }
    return body.data;
  }

  function clear() {
    list.replaceChildren();
    if (unread) unread.textContent = "";
    panel.hidden = true;
    previous.disabled = next.disabled = true;
  }

  function fail(error) {
    if (error.status === 401 || error.status === 403) {
      clear();
      login.hidden = false;
      status.textContent = admin ? "운영자 로그인이 필요합니다." : "로그인 후 알림을 확인할 수 있습니다.";
    } else {
      status.textContent = error.message || "요청 결과를 확인하지 못했습니다.";
    }
  }

  function element(tag, text) {
    const result = document.createElement(tag);
    result.textContent = text;
    return result;
  }

  function control(text, action) {
    const button = element("button", text);
    button.type = "button";
    button.addEventListener("click", () => change(action));
    return button;
  }

  function render(row) {
    const item = element("li", "");
    item.className = "inventory-card";
    const reply = row.type === "SUPPORT_ANSWERED";
    item.append(element("h2", labels[row.type] || "주문 상태 변경"));
    item.append(element("p", reply ? "문의 " + row.inquiryId : "주문 " + row.orderId));
    if (admin) {
      item.append(element("p", (statuses[row.status] || row.status) + " · 시도 " + row.attemptCount + "회"));
      if (row.failureCode) item.append(element("p", row.failureCode + " · " + row.failedAt + " UTC"));
      if (row.status === "FAILED") item.append(control("재시도 요청", {id: row.id, method: "POST", suffix: "retry"}));
    } else {
      item.append(element("p", row.confirmedAt + " UTC · " + (row.readAt ? "읽음" : "미읽음")));
      const link = element("a", reply ? "내 문의에서 확인" : "내 주문에서 확인");
      link.href = reply ? "support-inquiries.html?inquiryId=" + encodeURIComponent(row.inquiryId)
        : "orders.html?orderId=" + encodeURIComponent(row.orderId);
      item.append(link);
      if (!row.readAt) item.append(control("읽음으로 표시", {id: row.id, method: "PUT", suffix: "read"}));
    }
    return item;
  }

  async function load(number = page, notice = "") {
    if (busy) return;
    const signal = begin();
    clear();
    status.textContent = "알림을 불러오는 중입니다.";
    const params = new URLSearchParams({page: String(number)});
    if (filter) params.set("status", filter.value);
    try {
      const data = await call(url + "?" + params, signal);
      if (signal.aborted) return;
      page = data.page;
      list.replaceChildren(...data.items.map(render));
      if (unread) unread.textContent = "미읽음 " + data.unreadCount + "개";
      previous.disabled = page <= 0;
      next.disabled = !data.hasNext;
      login.hidden = true;
      panel.hidden = false;
      status.textContent = notice || (data.items.length ? "" : "표시할 알림이 없습니다.");
    } catch (error) {
      if (!signal.aborted) fail(error);
    }
  }

  async function change({id, method, suffix}) {
    if (busy) return;
    const signal = begin();
    busy = true;
    try {
      await call(url + "/" + encodeURIComponent(id) + "/" + suffix, signal, method);
      if (signal.aborted) return;
      busy = false;
      await load(page, admin ? "재시도를 요청했습니다." : "읽음으로 표시했습니다.");
    } catch (error) {
      if (!signal.aborted) fail(error);
    } finally {
      if (!signal.aborted) busy = false;
    }
  }

  previous.addEventListener("click", () => load(page - 1));
  next.addEventListener("click", () => load(page + 1));
  filter?.addEventListener("change", () => load(0));
  select("refresh").addEventListener("click", () => load());
  window.addEventListener("pagehide", () => { active?.abort(); busy = false; clear(); });
  window.addEventListener("pageshow", event => { if (event.persisted) load(); });
  window.addEventListener("focus", () => { busy = false; load(); });
  load(0);
})();
