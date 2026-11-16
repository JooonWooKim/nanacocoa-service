const shippingEndpoint = "/api/admin/shipping/orders";
const shippingSearch = document.getElementById("shipping-search");
const shippingMessage = document.getElementById("shipping-message");
const shippingContent = document.getElementById("shipping-content");
const shippingRetry = document.getElementById("shipping-retry");
const shippingLogin = document.getElementById("shipping-login");
const shippingActions = {
  SHIP: "출고 등록", CORRECT_TRACKING: "송장 정정", DELIVER: "배송 완료",
  VERIFY_UNSHIPPED: "과거 주문 미출고 확인", IMPORT_SHIPPED: "과거 출고 기록 등록",
  IMPORT_DELIVERED: "과거 배송 완료 기록 등록",
};
const shippingOrderLabels = {
  CREATED: "미결제", PAYMENT_PENDING: "결제 확인 중", PAID: "결제 완료", CANCELED: "주문 취소",
};
const shippingPaymentLabels = {
  UNPAID: "미결제", PENDING: "결제 확인 중", SUCCESS: "결제 완료",
  FAILED: "결제 실패", CANCELLATION_PENDING: "취소 확인 중", CANCELED: "결제 취소 완료",
};

let shippingPage = 0;
let currentShippingOrder;
let shippingFilters = new URLSearchParams(new FormData(shippingSearch));
let pendingShippingView;

function shippingElement(parent, tag, text) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  parent.append(element);
  return element;
}

function shippingButton(parent, text, action) {
  const button = shippingElement(parent, "button", text);
  button.type = "button";
  button.addEventListener("click", action);
  return button;
}

function shippingFields(parent, fields) {
  const list = shippingElement(parent, "dl");
  list.className = "order-history-fields";
  for (const [label, value] of fields) {
    shippingElement(list, "dt", label);
    shippingElement(list, "dd", value ?? "정보 없음");
  }
}

function shippingSummaryFields(order) {
  return [
    ["주문 상태", shippingOrderLabels[order.orderStatus]],
    ["결제 상태", shippingPaymentLabels[order.paymentStatus]],
    ["배송 상태", shippingLabels[order.shippingStatus]],
    ["출고 시각", displayShippingTime(order.shippedAt)],
  ];
}

function showShippingError(status) {
  shippingLogin.hidden = status !== 401 && status !== 403;
  shippingMessage.textContent = status === 401 ? "로그인 후 배송 정보를 조회해 주세요."
    : status === 403 ? "관리자 권한 또는 세션을 확인해 주세요."
    : status === 404 ? "주문을 찾을 수 없습니다."
    : "배송 정보를 불러오지 못했습니다. 다시 시도해 주세요.";
  shippingRetry.hidden = false;
}

async function loadShippingView({ orderId, page = shippingPage } = {}) {
  pendingShippingView?.abort();
  const request = new AbortController();
  pendingShippingView = request;
  currentShippingOrder = orderId;
  shippingPage = page;
  shippingRetry.hidden = true;
  shippingLogin.hidden = true;
  shippingContent.replaceChildren();
  shippingMessage.textContent = "배송 정보를 조회하고 있습니다.";
  const filters = new URLSearchParams(shippingFilters);
  filters.set("page", page);
  const endpoint = orderId ? `${shippingEndpoint}/${encodeURIComponent(orderId)}` : `${shippingEndpoint}?${filters}`;
  try {
    const response = await requestAuth(endpoint, { cache: "no-store", signal: request.signal });
    if (request.signal.aborted) return;
    if (!response.ok) {
      showShippingError(response.status);
      return;
    }
    const { data } = await response.json();
    if (request.signal.aborted) return;
    shippingMessage.textContent = "";
    if (orderId) renderShippingDetail(data);
    else renderShippingList(data);
  } catch {
    if (!request.signal.aborted) showShippingError();
  }
}

function renderShippingList(data) {
  if (!data.orders.length) shippingMessage.textContent = "조회 조건에 맞는 주문이 없습니다.";
  const list = shippingElement(shippingContent, "ul");
  list.className = "order-history-list";
  for (const order of data.orders) {
    const card = shippingElement(list, "li");
    card.className = "mypage-card";
    shippingButton(card, order.orderId, () => loadShippingView({ orderId: order.orderId }));
    shippingFields(card, shippingSummaryFields(order));
  }
  const nav = shippingElement(shippingContent, "nav");
  nav.className = "order-history-nav";
  if (data.page > 0) shippingButton(nav, "이전 페이지", () => loadShippingView({ page: data.page - 1 }));
  shippingElement(nav, "span", `${data.page + 1} 페이지`);
  if (data.hasNext) shippingButton(nav, "다음 페이지", () => loadShippingView({ page: data.page + 1 }));
}

function renderShippingDetail(detail) {
  shippingContent.replaceChildren();
  shippingButton(shippingContent, "목록으로", () => loadShippingView());
  const card = shippingElement(shippingContent, "article");
  card.className = "mypage-card";
  shippingElement(card, "h2", detail.order.orderId);
  shippingFields(card, [
    ...shippingSummaryFields(detail.order),
    ["택배사", detail.tracking.carrierName], ["송장", detail.tracking.trackingNumber],
    ["배송 완료 시각", displayShippingTime(detail.tracking.deliveredAt)],
    ["수령인", detail.recipientName], ["연락처", detail.phoneNumber],
    ["배송지", detail.shippingAddress], ["배송 요청사항", detail.customerRequest],
  ]);
  if (detail.holdReason) shippingElement(card, "p", detail.holdReason);
  shippingElement(card, "h3", "품목");
  const items = shippingElement(card, "ul");
  for (const item of detail.items) shippingElement(items, "li", `${item.productName} × ${item.quantity}`);
  if (detail.actions.length) renderShippingActions(card, detail);
  shippingElement(card, "h3", "배송 이력");
  const history = shippingElement(card, "ol");
  if (!detail.history.length) shippingElement(history, "li", "기록 없음");
  for (const event of detail.history) {
    const row = shippingElement(history, "li");
    shippingElement(row, "strong", shippingActions[event.action]);
    shippingFields(row, [
      ["상태", `${shippingLabels[event.previousStatus]} → ${shippingLabels[event.nextStatus]}`],
      ["정정 전 택배사·송장", [event.previousCarrier, event.previousTracking].filter(Boolean).join(" ") || null],
      ["기록된 택배사·송장", [event.nextCarrier, event.nextTracking].filter(Boolean).join(" ") || null],
      ["담당자 번호", event.operatorId], ["기록 시각", displayShippingTime(event.recordedAt)], ["사유", event.reason],
    ]);
  }
}

function renderShippingActions(card, detail) {
  const form = shippingElement(card, "form");
  form.className = "inventory-card";
  const action = shippingElement(shippingElement(form, "label", "처리 "), "select");
  for (const value of detail.actions) shippingElement(action, "option", shippingActions[value]).value = value;
  const tracking = shippingElement(form, "div");
  const carrier = shippingElement(shippingElement(tracking, "label", "택배사 "), "select");
  shippingElement(carrier, "option", "택배사 선택").value = "";
  for (const option of detail.carriers) shippingElement(carrier, "option", option.name).value = option.code;
  carrier.value = detail.carrierCode || "";
  const number = shippingElement(shippingElement(tracking, "label", "송장 번호 "), "input");
  number.maxLength = 64;
  number.value = detail.tracking.trackingNumber || "";
  const shippedLabel = shippingElement(form, "label", "확인된 출고 시각 ");
  const shippedAt = shippingElement(shippedLabel, "input");
  shippedAt.type = "datetime-local";
  const deliveredLabel = shippingElement(form, "label", "확인된 배송 완료 시각 ");
  const deliveredAt = shippingElement(deliveredLabel, "input");
  deliveredAt.type = "datetime-local";
  const reason = shippingElement(shippingElement(form, "label", "처리 사유 "), "textarea");
  reason.maxLength = 500;
  reason.required = true;
  const submit = shippingElement(form, "button", "저장");
  const hint = shippingElement(form, "p");

  function updateFields() {
    const importing = action.value.startsWith("IMPORT_");
    const needsTracking = importing || ["SHIP", "CORRECT_TRACKING"].includes(action.value);
    tracking.hidden = !needsTracking;
    carrier.required = number.required = needsTracking;
    shippedAt.required = importing;
    shippedLabel.hidden = !importing;
    deliveredAt.required = action.value === "IMPORT_DELIVERED";
    deliveredLabel.hidden = !deliveredAt.required;
    submit.disabled = needsTracking && !detail.carriers.length;
    hint.textContent = submit.disabled ? "운영 택배사 설정이 필요합니다."
      : action.value === "SHIP" ? "포장한 주문은 출고 등록이 확인된 뒤 택배사에 인계해 주세요." : "";
  }
  action.addEventListener("change", updateFields);
  updateFields();

  let lastPayload;
  let requestKey;
  let saving = false;
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (saving) return;
    const payload = {
      action: action.value, revision: detail.revision, reason: reason.value.trim(),
      carrierCode: carrier.required ? carrier.value : null,
      trackingNumber: number.required ? number.value.trim() : null,
      shippedAt: shippedAt.required ? shippedAt.value : null,
      deliveredAt: deliveredAt.required ? deliveredAt.value : null,
    };
    if (!payload.reason || (number.required && !payload.trackingNumber)) {
      shippingMessage.textContent = "송장 번호와 처리 사유를 확인해 주세요.";
      return;
    }
    const serialized = JSON.stringify(payload);
    if (serialized !== lastPayload) {
      requestKey = crypto.randomUUID();
      lastPayload = serialized;
    }
    saving = true;
    submit.disabled = true;
    shippingMessage.textContent = "배송 정보를 저장하고 있습니다.";
    try {
      const response = await requestAuth(`${shippingEndpoint}/${encodeURIComponent(detail.order.orderId)}`, {
        method: "POST",
        body: JSON.stringify({ ...payload, requestKey }),
      });
      if (!form.isConnected) return;
      if (response.status === 401 || response.status === 403) {
        shippingContent.replaceChildren();
        showShippingError(response.status);
        return;
      }
      const body = await readJson(response);
      if (!form.isConnected) return;
      if (!response.ok) {
        shippingMessage.textContent = body?.error || "저장할 수 없습니다. 입력을 확인해 주세요.";
        if (response.status === 409) shippingRetry.hidden = false;
        return;
      }
      shippingRetry.hidden = true;
      shippingMessage.textContent = "배송 정보를 저장했습니다.";
      renderShippingDetail(body.data);
    } catch (error) {
      if (form.isConnected) {
        shippingMessage.textContent = error.message || "저장 결과를 확인할 수 없습니다. 다시 조회하거나 같은 입력으로 재시도해 주세요.";
        shippingRetry.hidden = false;
      }
    } finally {
      saving = false;
      if (form.isConnected) updateFields();
    }
  });
}

shippingSearch.addEventListener("submit", (event) => {
  event.preventDefault();
  shippingFilters = new URLSearchParams(new FormData(shippingSearch));
  loadShippingView({ page: 0 });
});
shippingRetry.addEventListener("click", () => loadShippingView({ orderId: currentShippingOrder }));
window.addEventListener("pagehide", () => {
  pendingShippingView?.abort();
  shippingContent.replaceChildren();
});
window.addEventListener("pageshow", (event) => {
  if (event.persisted) loadShippingView({ orderId: currentShippingOrder });
});
loadShippingView();
