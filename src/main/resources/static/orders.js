const orderStatusLabels = {
  CREATED: "미결제", PAYMENT_PENDING: "결제 확인 중", PAID: "결제 완료", CANCELED: "주문 취소",
};
const orderPaymentLabels = {
  UNPAID: "미결제", PENDING: "결제 확인 중", PAID: "결제 완료", FAILED: "결제 실패",
  CANCELLATION_PENDING: "취소 확인 중", PAYMENT_CANCELED: "결제 취소 완료", ORDER_CANCELED: "주문 취소",
};

function appendOrderElement(parent, tag, text) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  parent.append(element);
  return element;
}

function appendOrderFields(parent, fields) {
  const layout = appendOrderElement(parent, "dl");
  layout.className = "order-history-fields";
  for (const [label, value] of fields) {
    appendOrderElement(layout, "dt", label);
    appendOrderElement(layout, "dd", value?.trim() || "정보 없음");
  }
}

function orderHistoryHref(page, orderId = null) {
  return `orders.html?${orderHistoryQuery({ page, orderId })}`;
}

function renderOrderHistoryList(content, message, { orders, page, hasNext }) {
  if (orders.length === 0) {
    message.textContent = page === 0 ? "주문 내역이 없습니다." : "이 페이지에 주문이 없습니다.";
  } else {
    const list = appendOrderElement(content, "ul");
    list.className = "order-history-list";
    for (const order of orders) {
      const row = appendOrderElement(list, "li");
      row.className = "mypage-card";
      appendOrderElement(row, "a", order.productName || order.orderId).href = orderHistoryHref(page, order.orderId);
      appendOrderFields(row, [
        ["주문 번호", order.orderId],
        ["주문 일시", order.createdAt.replace("T", " ")],
        ["주문 합계", formatPrice(order.totalAmount)],
        ["주문 상태", orderStatusLabels[order.status]],
      ]);
    }
  }
  if (page > 0 || hasNext) {
    const navigation = appendOrderElement(content, "nav");
    navigation.className = "order-history-nav";
    navigation.setAttribute("aria-label", "주문 목록 페이지");
    if (page > 0) appendOrderElement(navigation, "a", "이전 페이지").href = orderHistoryHref(page - 1);
    appendOrderElement(navigation, "span", `${page + 1} 페이지`);
    if (hasNext) appendOrderElement(navigation, "a", "다음 페이지").href = orderHistoryHref(page + 1);
  }
}

function renderOrderHistoryDetail(content, order) {
  const card = appendOrderElement(content, "article");
  card.className = "mypage-card";
  appendOrderElement(card, "h2", "주문 정보");
  appendOrderFields(card, [
    ["주문 번호", order.orderId],
    ["주문 일시", order.createdAt.replace("T", " ")],
    ["주문 상태", orderStatusLabels[order.status]],
    ["주문 합계", formatPrice(order.totalAmount)],
    ["결제 결과", orderPaymentLabels[order.paymentResult]],
  ]);

  appendOrderElement(card, "h2", "구매 상품");
  if (order.items.length === 0) {
    appendOrderElement(card, "p", "정보 없음");
  } else {
    const items = appendOrderElement(card, "ul");
    for (const item of order.items) {
      appendOrderElement(items, "li",
        `${item.productName} · ${formatPrice(item.unitPrice)} × ${item.quantity} · ${formatPrice(item.lineAmount)}`);
    }
  }

  appendOrderElement(card, "h2", "주문 당시 배송 정보");
  appendOrderFields(card, [
    ["수령인", order.recipientName], ["연락처", order.phoneNumber],
    ["배송지", order.shippingAddress], ["배송 요청사항", order.customerRequest],
  ]);
}

function initOrderHistory() {
  const root = document.querySelector("[data-order-history]");
  const route = orderHistoryLocation(new URLSearchParams(window.location.search));
  const message = root.querySelector("[data-order-history-message]");
  const content = root.querySelector("[data-order-history-content]");
  const retry = root.querySelector("[data-order-history-retry]");
  const login = root.querySelector("[data-order-history-login]");
  const back = root.querySelector("[data-order-history-back]");
  if (!route) {
    message.textContent = "주문 주소를 확인해 주세요.";
    back.hidden = false;
    return;
  }

  const { page, orderId } = route;
  if (orderId !== null) {
    root.querySelector("[data-order-history-title]").textContent = "주문 상세";
    back.href = orderHistoryHref(page);
    back.hidden = false;
  }
  const endpoint = orderId === null ? `/api/orders?page=${page}` : `/api/orders/${encodeURIComponent(orderId)}`;
  login.href = `login.html?return=orders&${orderHistoryQuery(route)}`;
  let activeRequest;

  async function load() {
    activeRequest?.abort();
    const request = new AbortController();
    activeRequest = request;
    content.replaceChildren();
    retry.hidden = true;
    login.hidden = true;
    message.hidden = false;
    message.textContent = "주문을 조회하고 있습니다.";

    try {
      const response = await requestAuth(endpoint, { cache: "no-store", signal: request.signal });
      if (request.signal.aborted) return;
      if (response.status === 401) {
        message.textContent = "주문을 확인하려면 다시 로그인해 주세요.";
        login.hidden = false;
        return;
      }
      if (response.status === 404 && orderId !== null) {
        message.textContent = "주문을 찾을 수 없습니다.";
        return;
      }
      if (!response.ok) throw new Error("Order history request failed");
      const { data } = await response.json();
      if (request.signal.aborted) return;

      message.textContent = "";
      if (orderId === null) {
        renderOrderHistoryList(content, message, data);
      } else {
        renderOrderHistoryDetail(content, data);
        retry.hidden = !["PENDING", "CANCELLATION_PENDING"].includes(data.paymentResult);
      }
      message.hidden = !message.textContent;
    } catch {
      if (request.signal.aborted) return;
      content.replaceChildren();
      message.textContent = "주문을 불러오지 못했습니다. 다시 시도해 주세요.";
      retry.hidden = false;
    }
  }

  retry.addEventListener("click", load);
  window.addEventListener("pagehide", () => {
    activeRequest?.abort();
    content.replaceChildren();
  });
  window.addEventListener("pageshow", (event) => {
    if (event.persisted) load();
  });
  load();
}

initOrderHistory();
