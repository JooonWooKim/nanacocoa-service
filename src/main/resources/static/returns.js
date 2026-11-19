const returnLabels = {
  CANCELLATION: "출고 전 전체 취소", RETURN: "전체 반품", REQUESTED: "신청 접수", APPROVED: "승인",
  REJECTED: "반려", COLLECTING: "회수 중", RECEIVED: "회수 완료", INSPECTED: "검수 완료",
  NOT_STARTED: "미처리", PENDING: "환불 확인 중", SUCCESS: "환불 완료", FAILED: "환불 확정 실패",
  COMPLETED: "재입고 완료", FAILED_INSPECTION: "재판매 불가",
  APPROVE: "승인", REJECT: "반려", COLLECT: "회수 시작", RECEIVE: "회수 완료",
  INSPECT: "검수 기록", REFUND: "전액 환불 실행", RESTOCK: "검수 재입고",
  PREPARING: "배송 준비", ON_HOLD: "출고 보류", SHIPPED: "배송 중", DELIVERED: "배송 완료",
  CANCELED: "취소 완료", BEFORE_SHIPPING: "결제 전",
};
function returnNode(parent, tag, text) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  parent.append(node); return node;
}
function returnButton(parent, text, action) {
  const button = returnNode(parent, "button", text); button.type = "button";
  button.addEventListener("click", action); return button;
}
function returnFields(parent, values) {
  const dl = returnNode(parent, "dl"); dl.className = "order-history-fields";
  for (const [key, value] of values) { returnNode(dl, "dt", key); returnNode(dl, "dd", value ?? "정보 없음"); }
}
function renderReturnDetail(parent, row, admin = false) {
  const card = returnNode(parent, "article"); card.className = "mypage-card";
  returnNode(card, "h3", returnLabels[row.type]);
  returnFields(card, [["신청 번호", String(row.id)], ["주문 번호", row.orderId],
    ["신청 상태", returnLabels[row.status]], ["신청 사유", row.reason],
    ["정책 버전", row.policyVersion], ["환불", returnLabels[row.refundStatus]],
    ["재입고", returnLabels[row.restockStatus]], ["접수 시각", row.createdAt?.replace("T", " ")]]);
  if (row.policyGuidance) returnNode(card, "p", row.policyGuidance);
  if (row.resellable !== null) returnNode(card, "p", row.resellable ? "검수: 재판매 가능" : "검수: 재판매 불가");
  if (row.refundEligible === false && row.status !== "REJECTED")
    returnNode(card, "p", "검수 결과에 따른 후속 처리를 확인하고 있습니다. 정책 안내를 확인해주세요.");
  if (row.holdReason) returnNode(card, "p", row.holdReason);
  const history = returnNode(card, "ol");
  for (const event of row.history || []) {
    returnNode(history, "li", [returnLabels[event.action], event.recordedAt?.replace("T", " "),
      event.reason, admin && event.operatorId ? `담당자 #${event.operatorId}` : null].filter(Boolean).join(" · "));
  }
  if (admin && row.cancellationId) {
    const link = returnNode(card, "a", "환불 거래 조회·복구");
    link.href = `payment-operations.html?type=CANCELLATION&id=${encodeURIComponent(row.cancellationId)}`;
  }
  return card;
}
function returnError(status, code) {
  return code === "CSRF_INVALID" ? "보안 토큰을 확인하지 못했습니다. 상태를 다시 조회한 뒤 실행해주세요."
    : status === 401 ? "세션이 만료됐습니다. 로그인 후 상태를 다시 조회해주세요."
    : status === 403 ? "이 작업을 수행할 권한이 없습니다."
    : status === 404 ? "주문 또는 신청을 찾을 수 없습니다."
    : status === 409 ? "다른 요청이 처리됐거나 허용되지 않는 작업입니다. 최신 상태를 확인해주세요."
    : status === 503 ? "정책·결제 연동 또는 잠금 서비스를 사용할 수 없습니다. 운영 설정을 확인해주세요."
    : "응답을 확인하지 못했습니다. 재제출 전에 저장된 상태를 다시 조회해주세요.";
}
async function returnRequestKey(scope, payload) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(JSON.stringify(payload)));
  const digest = Array.from(new Uint8Array(bytes), value => value.toString(16).padStart(2, "0")).join("");
  const storageKey = `nanacocoa-return:${scope}`;
  let previous;
  try { previous = JSON.parse(sessionStorage.getItem(storageKey)); } catch {}
  if (previous?.digest === digest && /^[0-9a-f-]{36}$/.test(previous.key)) return previous.key;
  const key = crypto.randomUUID();
  // 응답 유실 후 동일 제출 키를 유지한다. 원문 사유는 저장하지 않는다.
  sessionStorage.setItem(storageKey, JSON.stringify({ digest, key }));
  return key;
}
function returnReasonForm(parent, options) {
  const form = returnNode(parent, "form");
  let submitting = false;
  const actionLabel = returnNode(form, "label", options.admin ? "처리 작업 " : "신청 유형 ");
  const select = returnNode(actionLabel, "select");
  for (const action of options.actions) {
    const option = returnNode(select, "option", returnLabels[action]); option.value = action;
  }
  const label = returnNode(form, "label", options.admin ? "사유 (반려 사유는 고객에게 공개) " : "사유 ");
  const reason = returnNode(label, "textarea"); reason.required = true; reason.maxLength = 500;
  const inspection = returnNode(form, "fieldset");
  returnNode(inspection, "legend", "검수 결과");
  function checkbox(text) {
    const label = returnNode(inspection, "label", text); const input = returnNode(label, "input"); input.type = "checkbox"; return input;
  }
  const resellable = checkbox("재판매 가능");
  const refundable = checkbox("승인 정책에 따라 환불 가능");
  function update() { inspection.hidden = select.value !== "INSPECT"; }
  select.addEventListener("change", update); update();
  const submit = returnNode(form, "button", options.admin ? "선택 작업 실행" : "신청"); submit.type = "submit";
  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (submitting) return;
    if (!reason.value.trim()) { reason.focus(); return; }
    if (["REFUND", "RESTOCK"].includes(select.value)
        && !window.confirm(`${returnLabels[select.value]} 작업을 진행할까요? 최신 상태와 검수 결과를 확인해주세요.`)) return;
    const payload = options.admin
      ? { revision: options.revision, action: select.value, reason: reason.value.trim(),
          resellable: select.value === "INSPECT" ? resellable.checked : null,
          refundEligible: select.value === "INSPECT" ? refundable.checked : null }
      : { type: select.value, reason: reason.value.trim() };
    const controls = [...form.querySelectorAll("button,input,textarea,select")];
    submitting = true;
    controls.forEach(node => { node.disabled = true; });
    try {
      payload.requestKey = await returnRequestKey(options.scope, payload);
      await options.submit(payload);
    } catch (error) { options.error(returnError(undefined, error.code)); }
    finally { submitting = false; controls.forEach(node => { node.disabled = false; }); }
  });
  return form;
}
function renderOrderReturns(parent, order) {
  const section = returnNode(parent, "section");
  returnNode(section, "h2", "취소·반품·환불");
  const notice = returnNode(section, "p"); notice.setAttribute("role", "status");
  if (order.returns?.policyGuidance) returnNode(section, "p", order.returns.policyGuidance);
  if (order.returns?.holdReason) returnNode(section, "p", order.returns.holdReason);
  const results = returnNode(section, "div");
  for (const row of order.returns?.requests || []) renderReturnDetail(results, row);
  const retry = returnButton(section, "최신 상태 조회", () => location.reload());
  const login = returnNode(section, "a", "로그인 후 주문 조회");
  login.href = `login.html?return=orders&${orderHistoryQuery({ page: 0, orderId: order.orderId })}`;
  login.hidden = true;
  async function submit(payload) {
    retry.disabled = true;
    try {
      const response = await requestAuth(`/api/orders/${encodeURIComponent(order.orderId)}/return-requests`,
        { method: "POST", body: JSON.stringify(payload) });
      const body = await readJson(response);
      login.hidden = response.status !== 401;
      if (!response.ok) { notice.textContent = returnError(response.status, body.code); return; }
      // 성공 응답도 서버에 저장된 주문 상세를 다시 읽는다.
      location.reload();
    } catch (error) { notice.textContent = returnError(undefined, error.code); }
    finally { retry.disabled = false; }
  }
  if (order.returns?.allowedTypes?.length) returnReasonForm(section, {
    actions: order.returns.allowedTypes, scope: `order:${order.orderId}`, submit,
    error: text => { notice.textContent = text; },
  });
  if (order.status === "CREATED") {
    const cancel = returnButton(section, "미결제 주문 취소", async () => {
      cancel.disabled = true;
      try {
        const response = await requestAuth(`/api/orders/${encodeURIComponent(order.orderId)}/cancel`, { method: "POST" });
        const body = await readJson(response);
        if (!response.ok) { notice.textContent = returnError(response.status, body.code); login.hidden = response.status !== 401; return; }
        location.reload();
      } catch (error) { notice.textContent = returnError(undefined, error.code); }
      finally { cancel.disabled = false; }
    });
  }
}
function returnOperationsLocation(params) {
  const state = new URLSearchParams();
  const page = params.get("page") || "0";
  if (!/^[0-9]+$/.test(page) || Number(page) > 107374182) return null;
  state.set("page", page);
  const id = params.get("id");
  if (id !== null) {
    if (!/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n) return null;
    state.set("id", id);
  }
  const orderId = params.get("orderId");
  if (orderId) { if (orderId.length > 64 || !orderId.trim()) return null; state.set("orderId", orderId); }
  for (const [key, allowed] of [["type", ["CANCELLATION", "RETURN"]],
      ["status", ["REQUESTED", "APPROVED", "REJECTED", "COLLECTING", "RECEIVED", "INSPECTED"]]]) {
    const value = params.get(key);
    if (value) { if (!allowed.includes(value)) return null; state.set(key, value); }
  }
  return state;
}
async function initReturnOperations() {
  const root = document.querySelector("[data-return-admin]");
  if (!root) return;
  const search = root.querySelector("form");
  const content = root.querySelector("[data-return-content]");
  const message = root.querySelector("[data-return-message]");
  const retry = root.querySelector("[data-return-retry]");
  const login = root.querySelector("[data-return-login]");
  let state = returnOperationsLocation(new URLSearchParams(location.search));
  let read, busy = false;
  const endpoint = "/api/admin/return-requests";
  async function load() {
    if (busy || !state) { if (!state) message.textContent = "조회 주소를 확인해주세요."; return; }
    read?.abort(); read = new AbortController(); const request = read;
    content.replaceChildren(); login.hidden = true; message.textContent = "신청을 조회하고 있습니다.";
    history.replaceState(null, "", `returns.html?${state}`);
    login.href = `login.html?return=returns&${state}`;
    for (const name of ["orderId", "type", "status"]) search.elements[name].value = state.get(name) || "";
    try {
      const response = await requestAuth(state.has("id") ? `${endpoint}/${state.get("id")}` : `${endpoint}?${state}`,
        { cache: "no-store", signal: request.signal });
      const body = await readJson(response);
      if (request.signal.aborted) return;
      if (!response.ok) { message.textContent = returnError(response.status, body.code); login.hidden = response.status !== 401; return; }
      message.textContent = "";
      if (state.has("id")) {
        const row = body.data;
        returnButton(content, "목록으로", () => { state.delete("id"); load(); });
        const card = renderReturnDetail(content, row, true);
        if (row.shipping) returnFields(card, [["배송 상태", returnLabels[row.shipping.status] || "정보 확인 중"],
          ["택배사", row.shipping.carrierName], ["송장 번호", row.shipping.trackingNumber]]);
        if (row.actions.length) returnReasonForm(card, {
          admin: true, revision: row.revision, actions: row.actions, scope: `admin:${row.id}`,
          error: text => { message.textContent = text; },
          submit: async payload => {
            busy = true; let notice;
            root.querySelectorAll("button,input,textarea,select").forEach(n => { n.disabled = true; });
            try {
              const response = await requestAuth(`${endpoint}/${row.id}/actions`, { method: "POST", body: JSON.stringify(payload) });
              const body = await readJson(response);
              notice = response.ok ? (response.status === 202 ? "환불 결과 확인 중입니다." : "처리 결과가 저장됐습니다.")
                : returnError(response.status, body.code);
            } catch (error) { notice = returnError(undefined, error.code); }
            finally {
              busy = false; root.querySelectorAll("button,input,textarea,select").forEach(n => { n.disabled = false; });
            }
            await load(); message.textContent = `${notice} ${message.textContent}`.trim();
          },
        });
      } else {
        const data = body.data;
        if (!data.items.length) message.textContent = "조회 조건에 맞는 신청이 없습니다.";
        for (const row of data.items) {
          const card = renderReturnDetail(content, row, true);
          returnButton(card, "상세·처리", () => { state.set("id", row.id); load(); });
        }
        if (data.page > 0) returnButton(content, "이전 페이지", () => { state.set("page", data.page - 1); load(); });
        if (data.hasNext) returnButton(content, "다음 페이지", () => { state.set("page", data.page + 1); load(); });
      }
    } catch (error) { if (!request.signal.aborted) message.textContent = returnError(undefined, error.code); }
  }
  search.addEventListener("submit", event => {
    event.preventDefault(); if (busy) return;
    state = new URLSearchParams({ page: "0" });
    for (const name of ["orderId", "type", "status"]) {
      const value = search.elements[name].value.trim(); if (value) state.set(name, value);
    }
    load();
  });
  retry.addEventListener("click", load);
  window.addEventListener("pagehide", () => { read?.abort(); content.replaceChildren(); });
  window.addEventListener("pageshow", e => { if (e.persisted) load(); });
  load();
}
initReturnOperations();
