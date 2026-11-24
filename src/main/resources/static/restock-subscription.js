function initRestockSubscription(page, product, productId) {
  const root = page.querySelector("[data-restock-subscription]");
  if (!root) return;
  const button = root.querySelector("[data-restock-action]");
  const status = root.querySelector("[data-restock-status]");
  const endpoint = "/api/restock-subscriptions/" + encodeURIComponent(productId);
  const guestCanApply = product.restockNotificationEnabled && product.availability === "SOLD_OUT";
  let controller;
  let account;
  let state;

  function clear() {
    controller?.abort();
    root.hidden = button.hidden = true;
    button.disabled = false;
    account = state = null;
    status.textContent = "";
  }

  async function call(path, signal, options = {}) {
    const response = await requestAuth(path, {signal, cache: "no-store", ...options});
    const body = await readJson(response);
    if (!response.ok) throw new Error(body.error || "재입고 알림 상태를 확인하지 못했습니다.");
    return path === "/api/auth/session" ? body : body.data;
  }

  function render(notice) {
    button.hidden = !(state?.active || state?.canSubscribe || (!account && guestCanApply));
    root.hidden = button.hidden && !notice;
    button.textContent = state?.active ? "재입고 알림 취소" : "재입고 알림 신청";
    status.textContent = notice;
  }

  async function load(expectedAccount, notice = "") {
    clear();
    controller = new AbortController();
    const {signal} = controller;
    try {
      const session = await call("/api/auth/session", signal);
      if (signal.aborted) return;
      if (!session.authenticated || !session.checkoutAccount) {
        render("");
        return;
      }
      const result = await call(endpoint, signal);
      if (signal.aborted) return;
      const currentSession = await call("/api/auth/session", signal);
      if (signal.aborted || currentSession.checkoutAccount !== session.checkoutAccount) return;
      account = session.checkoutAccount;
      state = result;
      render(expectedAccount === account ? notice : "");
    } catch (error) {
      if (signal.aborted || !product.restockNotificationEnabled) return;
      root.hidden = false;
      status.textContent = error.message + " 새로고침 후 다시 시도해 주세요.";
    }
  }

  button.addEventListener("click", async () => {
    if (!account) {
      window.location.href = "login.html?return=product&id=" + encodeURIComponent(productId);
      return;
    }
    if (button.disabled) return;
    const currentAccount = account;
    const method = state.active ? "DELETE" : "PUT";
    const {signal} = controller;
    button.disabled = true;
    try {
      await call(endpoint, signal, {method, headers: {"X-Checkout-Account": currentAccount}});
      if (!signal.aborted) await load(currentAccount, method === "PUT" ? "재입고 알림을 신청했습니다." : "재입고 알림을 취소했습니다.");
    } catch (error) {
      if (!signal.aborted) await load(currentAccount, error.message);
    }
  });

  window.addEventListener("focus", () => load());
  window.addEventListener("pagehide", clear);
  window.addEventListener("pageshow", event => { if (event.persisted) load(); });
  load();
}
