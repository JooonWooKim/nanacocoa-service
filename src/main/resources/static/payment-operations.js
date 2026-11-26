(() => {
  const endpoint = "/api/admin/payment-operations";
  const search = document.getElementById("operations-search");
  const message = document.getElementById("operations-message");
  const content = document.getElementById("operations-content");
  const retry = document.getElementById("operations-retry");
  const login = document.getElementById("operations-login");
  const labels = { VERIFY_APPROVAL: "승인 확인", COMPENSATE_APPROVAL: "승인 보상", CANCELLATION: "취소 확인",
    NONE: "승인 처리 완료", PENDING: "확인 중", SUCCESS: "완료", FAILED: "실패", CANCELED: "취소 완료",
    CANCEL_PENDING: "취소 확인 중", CREATED: "미결제", PAYMENT_PENDING: "결제 확인 중", PAID: "결제 완료",
    RESERVED: "재고 예약", SOLD: "판매 확정", RELEASED: "예약 해제", RETURNED: "검수 재입고", LEGACY: "기존 주문" };
  let state = paymentOperationsLocation(new URLSearchParams(location.search)) || new URLSearchParams({ page: "0", size: "20" });
  let pendingRead, busy = false, detail;
  const label = value => labels[value] || "정보 없음";
  const time = value => value ? value.replace("T", " ") : "정보 없음";

  function element(parent, tag, text) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    parent.append(node); return node;
  }
  function button(parent, text, action) {
    const node = element(parent, "button", text); node.type = "button";
    node.addEventListener("click", action); return node;
  }
  function fields(parent, pairs) {
    const list = element(parent, "dl"); list.className = "order-history-fields";
    for (const [name, value] of pairs) { element(list, "dt", name); element(list, "dd", value ?? "정보 없음"); }
  }
  function setBusy(value) {
    busy = value;
    for (const node of document.querySelectorAll("main button, main input, main select")) node.disabled = value;
    for (const node of document.querySelectorAll("main a")) {
      node.setAttribute("aria-disabled", String(value));
      node.style.pointerEvents = value ? "none" : "";
    }
  }
  document.querySelector("main").addEventListener("click", event => {
    if (busy && event.target.closest("a")) event.preventDefault();
  });
  function errors(status, code) {
    login.hidden = status !== 401;
    message.textContent = code === "CSRF_INVALID" ? "보안 토큰이 만료되었습니다. 상태를 확인한 뒤 다시 실행해주세요."
      : status === 401 ? "로그인 후 거래를 다시 조회해주세요."
      : status === 403 ? "관리자 권한이 필요합니다. 권한을 확인해주세요."
      : status === 404 ? "거래를 찾을 수 없습니다."
      : status === 409 ? "다른 요청이 처리 중이거나 복구 예정 시각 전입니다. 상태를 다시 조회해주세요."
      : status === 503 ? "결제 연동 또는 잠금 서비스를 사용할 수 없습니다. 상태를 조회하고 운영 설정을 확인해주세요."
      : "조회에 실패했습니다. 다시 조회해주세요.";
    retry.hidden = false;
  }
  function summary(row) {
    return [["거래 유형", label(row.kind)], ["거래 번호", `${row.type} / ${row.id}`],
      ["주문 번호", row.orderId], ["금액", `${Number(row.amount).toLocaleString("ko-KR")}원`],
      ["상태", label(row.status)], ["대기 시작", time(row.createdAt)],
      ["재시도 횟수", row.retryCount], ["복구 예정", time(row.nextRetryAt)]];
  }
  async function load() {
    if (busy) return;
    pendingRead?.abort(); const request = new AbortController(); pendingRead = request;
    detail = null; content.replaceChildren(); retry.hidden = true; login.hidden = true;
    message.textContent = "거래를 조회하고 있습니다.";
    history.replaceState(null, "", `payment-operations.html?${state}`);
    const destination = new URLSearchParams(state); destination.set("return", "payment-operations");
    login.href = `login.html?${destination}`;
    search.elements.kind.value = state.get("kind") || "";
    search.elements.orderId.value = state.get("orderId") || "";
    const type = state.get("type"), id = state.get("id");
    const query = new URLSearchParams(state); query.delete("type"); query.delete("id");
    try {
      const response = await requestAuth(type ? `${endpoint}/${type}/${id}` : `${endpoint}?${query}`,
        { cache: "no-store", signal: request.signal });
      if (request.signal.aborted) return;
      const body = await readJson(response);
      if (request.signal.aborted) return;
      if (!response.ok) { errors(response.status, body.code); return; }
      message.textContent = "";
      if (type) renderDetail(body.data); else renderList(body.data);
    } catch {
      if (!request.signal.aborted) errors();
    }
  }
  function renderList(data) {
    if (!data.items.length) message.textContent = "조회 조건에 맞는 미확정 거래가 없습니다.";
    element(content, "p", `전체 ${data.totalElements}건`);
    const list = element(content, "ul"); list.className = "order-history-list";
    for (const row of data.items) {
      const card = element(list, "li"); card.className = "mypage-card";
      button(card, "거래 상세", () => { if (busy) return; state.set("type", row.type); state.set("id", row.id); load(); });
      fields(card, summary(row));
    }
    const nav = element(content, "nav"); nav.className = "order-history-nav";
    const page = data.page;
    if (page > 0) button(nav, "이전 페이지", () => { state.set("page", page - 1); load(); });
    element(nav, "span", `${page + 1} 페이지`);
    if (data.hasNext) button(nav, "다음 페이지", () => { state.set("page", page + 1); load(); });
  }
  function renderDetail(row) {
    detail = row;
    button(content, "목록으로", () => { if (busy) return; state.delete("type"); state.delete("id"); load(); });
    const card = element(content, "article"); card.className = "mypage-card";
    element(card, "h2", "거래 상세");
    fields(card, [...summary(row), ["결제 상태", label(row.paymentStatus)], ["주문 상태", label(row.orderStatus)],
      ["재고 상태", label(row.stockState)], ["갱신 시각", time(row.updatedAt)]]);
    if (row.errorMessage) element(card, "p", row.errorMessage);
    if (row.holdReason) element(card, "p", row.holdReason);
    button(card, "상태 다시 조회", load);
    const reconcile = button(card, "개별 복구", run);
    reconcile.disabled = !row.canReconcile;
  }
  async function run() {
    if (busy || !detail?.canReconcile) return;
    if (!window.confirm("선택한 거래를 복구합니다. 결제사 조회 후 기존 승인 재시도, 보상 취소 또는 환불이 실행될 수 있습니다. 계속할까요?")) return;
    const target = detail; let notice;
    setBusy(true); retry.hidden = true; login.hidden = true;
    message.textContent = "거래를 복구하고 있습니다. 결과가 나올 때까지 기다려주세요.";
    try {
      const response = await requestAuth(`${endpoint}/${target.type}/${target.id}/reconcile`, { method: "POST" });
      const body = await readJson(response);
      if (!response.ok) {
        errors(response.status, body.code);
        notice = incidentMessage(message.textContent, incidentRequestId(response));
      } else notice = response.status === 202
        ? incidentMessage("아직 결과 확인 중입니다. 예정 시각 이후 다시 조회해주세요.", incidentRequestId(response))
        : "거래 처리 결과가 확인됐습니다.";
    } catch (error) {
      notice = incidentMessage(error.code === "CSRF_INVALID" ? "보안 토큰이 만료됐습니다. 상태 확인 후 다시 실행해주세요."
        : "응답을 확인하지 못했습니다. 복구를 다시 누르기 전에 현재 상태를 조회해주세요.", error.requestId);
    } finally { setBusy(false); }
    // 결과 유실 후에도 POST를 반복하지 않고 저장된 결과만 조회한다.
    const refresh = load();
    const refreshRequest = pendingRead;
    await refresh;
    if (pendingRead !== refreshRequest) return;
    message.textContent = `${notice} ${message.textContent}`.trim();
    retry.hidden = false;
  }
  search.addEventListener("submit", event => {
    event.preventDefault(); if (busy) return;
    state = new URLSearchParams({ page: "0", size: state.get("size") || "20" });
    const kind = search.elements.kind.value, orderId = search.elements.orderId.value.trim();
    if (kind) state.set("kind", kind); if (orderId) state.set("orderId", orderId);
    load();
  });
  retry.addEventListener("click", load);
  window.addEventListener("popstate", () => {
    if (busy) return;
    state = paymentOperationsLocation(new URLSearchParams(location.search)) || new URLSearchParams({ page: "0", size: "20" });
    load();
  });
  load();
})();
