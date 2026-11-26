const authApi = {
  signup: "/api/auth/signup",
  login: "/api/auth/login",
  session: "/api/auth/session",
  csrf: "/api/auth/csrf",
  logout: "/api/auth/logout",
};
const membersApi = {
  profile: "/api/members/me",
  verification: "/api/members/me/verification",
  password: "/api/members/me/password",
};
const productsApi = {
  list: "/api/products",
  detail: (productId) => `/api/products/${encodeURIComponent(productId)}`,
};
const ordersApi = {
  create: "/api/orders",
  submission: "/api/orders/submission",
};
const paymentsApi = {
  clientConfig: "/api/payments/client-config",
  confirm: "/api/payments/confirm",
  detail: (paymentId) => `/api/payments/${encodeURIComponent(paymentId)}`,
};
const loginRedirectUrl = "index.html";
const defaultProductImage = "IMG_2398.JPG";
const maxProductImageSize = 5 * 1024 * 1024;
const kakaoPostcodeScriptUrl = "https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";
const tossPaymentsScriptUrl = "https://js.tosspayments.com/v2/standard";
const checkoutPaymentContextKey = "nanacocoa.checkout.payment-context";
const checkoutSubmissionPrefix = "nanacocoa.checkout.submission";
const checkoutCustomerKeyKey = "nanacocoa.checkout.customer-key";
const paymentCallbackLoginReturnUrlKey = "nanacocoa.auth.payment-callback-return-url";
let authSessionPromise;
let kakaoPostcodePromise;
let tossPaymentsSdkPromise;

const galleryViews = [
  { position: "50% 50%", label: "대표 사진" },
  { position: "31% 50%", label: "왼쪽 디테일" },
  { position: "70% 54%", label: "오른쪽 디테일" },
  { position: "50% 30%", label: "상단 디테일" },
];

function initMenu() {
  const body = document.body;
  const menuToggle = document.querySelector(".menu-toggle");
  const sideMenu = document.querySelector(".side-menu");
  const menuScrim = document.querySelector(".menu-scrim");

  if (!menuToggle || !sideMenu || !menuScrim) {
    return;
  }

  const drawerTriggers = document.querySelectorAll(".drawer-trigger");

  function setMenuOpen(isOpen) {
    body.classList.toggle("menu-open", isOpen);
    sideMenu.classList.toggle("is-open", isOpen);
    sideMenu.setAttribute("aria-hidden", String(!isOpen));
    menuToggle.setAttribute("aria-expanded", String(isOpen));
    menuToggle.setAttribute("aria-label", isOpen ? "메뉴 닫기" : "메뉴 열기");
    menuScrim.hidden = !isOpen;
  }

  menuToggle.addEventListener("click", () => {
    const isOpen = menuToggle.getAttribute("aria-expanded") === "true";
    setMenuOpen(!isOpen);
  });

  menuScrim.addEventListener("click", () => setMenuOpen(false));

  sideMenu.addEventListener("click", (event) => {
    if (event.target.closest("a")) {
      setMenuOpen(false);
    }
  });

  drawerTriggers.forEach((trigger) => {
    trigger.addEventListener("click", () => {
      const group = trigger.closest(".drawer-group");
      const isOpen = group.classList.toggle("is-open");
      trigger.setAttribute("aria-expanded", String(isOpen));
    });
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      setMenuOpen(false);
    }
  });
}

function formatPrice(price) {
  return `${Number(price || 0).toLocaleString("ko-KR")}원`;
}

function normalizeAddressPart(value) {
  return String(value || "").trim().replace(/\s+/g, " ");
}

function formatShippingAddress(postalCode, basicAddress, detailAddress) {
  const normalizedPostalCode = normalizeAddressPart(postalCode);
  const address = [basicAddress, detailAddress]
    .map(normalizeAddressPart)
    .filter(Boolean)
    .join(" ");

  return normalizedPostalCode ? `[${normalizedPostalCode}] ${address}`.trim() : address;
}

function parseStoredAddress(value) {
  const address = normalizeAddressPart(value);
  const match = address.match(/^\[([0-9]{5})\]\s*(.*)$/);

  if (!match) {
    return { postalCode: "", basicAddress: address, detailAddress: "" };
  }

  return {
    postalCode: match[1],
    basicAddress: match[2],
    detailAddress: "",
  };
}

function loadKakaoPostcode() {
  if (typeof window.kakao?.Postcode === "function") {
    return Promise.resolve(window.kakao.Postcode);
  }

  if (kakaoPostcodePromise) {
    return kakaoPostcodePromise;
  }

  kakaoPostcodePromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = kakaoPostcodeScriptUrl;
    script.async = true;
    script.dataset.kakaoPostcodeScript = "";

    script.addEventListener("load", () => {
      if (typeof window.kakao?.Postcode !== "function") {
        reject(new Error("Kakao 우편번호 서비스를 초기화하지 못했습니다."));
        return;
      }

      resolve(window.kakao.Postcode);
    }, { once: true });

    script.addEventListener("error", () => {
      reject(new Error("Kakao 우편번호 스크립트를 불러오지 못했습니다."));
    }, { once: true });

    document.head.append(script);
  });

  return kakaoPostcodePromise;
}

function setupKakaoAddressSearch({
  postalCodeInput,
  basicAddressInput,
  detailAddressInput,
  searchButton,
  statusElement,
  onAddressChange = () => {},
  shouldKeepDisabled = () => false,
}) {
  function setStatus(message) {
    statusElement.textContent = message || "";
    statusElement.hidden = !message;
  }

  function disableSearch(message) {
    searchButton.disabled = true;
    searchButton.textContent = "주소검색 불가";
    setStatus(message);
  }

  searchButton.addEventListener("click", () => {
    const Postcode = window.kakao?.Postcode;

    if (typeof Postcode !== "function") {
      disableSearch("주소검색을 사용할 수 없습니다. 우편번호와 기본주소를 직접 입력해 주세요.");
      return;
    }

    try {
      new Postcode({
        oncomplete(data) {
          const postalCode = normalizeAddressPart(data.zonecode);
          const selectedAddress = data.userSelectedType === "R" ? data.roadAddress : data.jibunAddress;
          const basicAddress = normalizeAddressPart(selectedAddress || data.address);

          if (!postalCode || !basicAddress) {
            setStatus("선택한 주소를 입력하지 못했습니다. 다시 검색하거나 직접 입력해 주세요.");
            return;
          }

          postalCodeInput.value = postalCode;
          basicAddressInput.value = basicAddress;
          detailAddressInput.value = "";
          postalCodeInput.setCustomValidity("");
          basicAddressInput.setCustomValidity("");
          detailAddressInput.setCustomValidity("");
          onAddressChange();
          setStatus("");
          detailAddressInput.focus();
        },
      }).open();
    } catch {
      disableSearch("주소검색 창을 열지 못했습니다. 우편번호와 기본주소를 직접 입력해 주세요.");
    }
  });

  [postalCodeInput, basicAddressInput, detailAddressInput].forEach((input) => {
    input.addEventListener("input", () => {
      input.setCustomValidity("");
      onAddressChange();
    });
  });

  loadKakaoPostcode()
    .then(() => {
      if (shouldKeepDisabled()) {
        return;
      }
      searchButton.disabled = false;
      searchButton.textContent = "주소검색";
      setStatus("");
    })
    .catch(() => {
      if (shouldKeepDisabled()) {
        return;
      }
      disableSearch("주소검색을 불러오지 못했습니다. 우편번호와 기본주소를 직접 입력해 주세요.");
    });
}

function loadTossPaymentsSdk() {
  if (typeof window.TossPayments === "function") {
    return Promise.resolve(window.TossPayments);
  }

  if (tossPaymentsSdkPromise) {
    return tossPaymentsSdkPromise;
  }

  tossPaymentsSdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = tossPaymentsScriptUrl;
    script.async = true;
    script.dataset.tossPaymentsScript = "";

    script.addEventListener("load", () => {
      if (typeof window.TossPayments !== "function") {
        tossPaymentsSdkPromise = null;
        reject(new Error("토스페이 결제 모듈을 초기화하지 못했습니다."));
        return;
      }

      resolve(window.TossPayments);
    }, { once: true });

    script.addEventListener("error", () => {
      tossPaymentsSdkPromise = null;
      reject(new Error("토스페이 결제 모듈을 불러오지 못했습니다. 네트워크 연결을 확인해 주세요."));
    }, { once: true });

    document.head.append(script);
  });

  return tossPaymentsSdkPromise;
}

function randomUuid() {
  if (typeof window.crypto?.randomUUID !== "function") {
    throw new Error("안전한 결제 식별자를 만들 수 없는 브라우저입니다. 최신 브라우저에서 다시 시도해 주세요.");
  }
  return window.crypto.randomUUID();
}

function readCheckoutSessionValue(key) {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    throw new Error("브라우저 저장공간을 사용할 수 없어 결제를 계속할 수 없습니다.");
  }
}

function writeCheckoutSessionValue(key, value) {
  try {
    window.sessionStorage.setItem(key, value);
  } catch {
    throw new Error("브라우저 저장공간을 사용할 수 없어 결제를 계속할 수 없습니다.");
  }
}

function removeCheckoutSessionValue(key) {
  try {
    window.sessionStorage.removeItem(key);
  } catch {
    // 저장공간 정리 실패는 이미 완료된 결제 결과를 바꾸지 않습니다.
  }
}

function isPaymentSuccessCallbackUrl(url) {
  return url.origin === window.location.origin
    && url.pathname === new URL("checkout.html", window.location.href).pathname
    && url.searchParams.get("paymentResult") === "success"
    && ["paymentKey", "orderId", "amount", "checkoutOrderId"]
      .every((parameter) => Boolean(url.searchParams.get(parameter)));
}

function preservePaymentCallbackLoginReturnUrl() {
  const callbackUrl = new URL(window.location.href);
  if (!isPaymentSuccessCallbackUrl(callbackUrl)) {
    return false;
  }

  try {
    if(callbackUrl.searchParams.has("cart"))callbackUrl.searchParams.set("review", "1");
    writeCheckoutSessionValue(paymentCallbackLoginReturnUrlKey, callbackUrl.toString());
    return true;
  } catch {
    return false;
  }
}

function takePaymentCallbackLoginReturnUrl() {
  let storedUrl;
  try {
    storedUrl = readCheckoutSessionValue(paymentCallbackLoginReturnUrlKey);
  } catch {
    return null;
  }

  removeCheckoutSessionValue(paymentCallbackLoginReturnUrlKey);
  if (!storedUrl) {
    return null;
  }

  try {
    const callbackUrl = new URL(storedUrl, window.location.href);
    return isPaymentSuccessCallbackUrl(callbackUrl) ? callbackUrl.toString() : null;
  } catch {
    return null;
  }
}

function checkoutCustomerKey() {
  const storedKey = readCheckoutSessionValue(checkoutCustomerKeyKey);
  if (storedKey) {
    return storedKey;
  }

  const customerKey = randomUuid();
  writeCheckoutSessionValue(checkoutCustomerKeyKey, customerKey);
  return customerKey;
}

function readCheckoutPaymentContext() {
  const storedContext = readCheckoutSessionValue(checkoutPaymentContextKey);
  if (!storedContext) {
    return null;
  }

  try {
    const context = JSON.parse(storedContext);
    if (!context?.orderId || !Number.isSafeInteger(context.amount) || context.amount <= 0
        || !context.idempotencyKey || !context.productId || !context.orderName) {
      return null;
    }
    return context;
  } catch {
    return null;
  }
}

function writeCheckoutPaymentContext(context) {
  writeCheckoutSessionValue(checkoutPaymentContextKey, JSON.stringify(context));
}

function clearCheckoutPaymentContext() {
  removeCheckoutSessionValue(checkoutPaymentContextKey);
}

function renewCheckoutPaymentAttempt(context) {
  context.idempotencyKey = randomUuid();
  delete context.paymentId;
  writeCheckoutPaymentContext(context);
}

function getProductImageUrl(product) {
  return product.imageUrl || defaultProductImage;
}

function sanitizeCssUrl(url) {
  return String(url || "").replace(/["\\\n\r]/g, "");
}

function incidentRequestId(response) {
  return validIncidentRequestId(response?.headers?.get?.("X-Request-Id"));
}

function validIncidentRequestId(value) {
  return typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value) ? value : "";
}

function incidentMessage(message, requestId) {
  const valid = validIncidentRequestId(requestId);
  return valid ? `${message} 요청 번호: ${valid}` : message;
}

async function requestAuth(url, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const sameOrigin = new URL(url, window.location.href).origin === window.location.origin;
  const protectedWrite = sameOrigin && !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method);
  const headers = new Headers(options.headers || {});
  headers.set("Accept", "application/json");

  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (protectedWrite) {
    let csrf;
    try {
      const response = await fetch(authApi.csrf, {
        mode: "same-origin",
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" },
        signal: options.signal,
      });
      if (response.ok) csrf = (await readJson(response)).data;
    } catch (error) {
      if (options.signal?.aborted) throw error;
    }
    if (!csrf?.headerName || !csrf?.token) {
      throw new Error("보안 토큰을 가져오지 못했습니다. 다시 시도해 주세요.");
    }
    headers.set(csrf.headerName, csrf.token);
  }

  const response = await fetch(url, {
    ...options,
    credentials: "same-origin",
    ...(protectedWrite ? { mode: "same-origin" } : {}),
    headers,
  });
  if (protectedWrite && response.status === 403) {
    const body = await readJson(response.clone());
    if (body.code === "CSRF_INVALID") {
      const error = new Error(body.error || "보안 토큰을 다시 발급받으려면 다시 시도해 주세요.");
      error.code = body.code;
      error.requestId = incidentRequestId(response);
      throw error;
    }
  }
  return response;
}

async function readJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

function setAuthMessage(message) {
  const messageBox = document.querySelector(".login-message");

  if (!messageBox) {
    return;
  }

  messageBox.textContent = message;
  messageBox.hidden = !message;
}

function orderHistoryLocation(params) {
  const rawPage = params.get("page") ?? "0";
  const page = Number(rawPage);
  const orderId = params.get("orderId");
  if (!/^[0-9]+$/.test(rawPage) || page > 2147483647
      || (orderId !== null && !/^[A-Za-z0-9_-]{1,64}$/.test(orderId))) {
    return null;
  }
  return { page, orderId };
}

function orderHistoryQuery({ page, orderId }) {
  const params = new URLSearchParams({ page: String(page) });
  if (orderId !== null) params.set("orderId", orderId);
  return params.toString();
}

function orderHistoryLoginReturnUrl() {
  const params = new URLSearchParams(window.location.search);
  if (params.get("return") !== "orders") return null;
  const target = orderHistoryLocation(params);
  return target ? `orders.html?${orderHistoryQuery(target)}` : null;
}

function supportInquiryId(value) {
  if (!/^[1-9][0-9]{0,18}$/.test(value ?? "")) return null;
  return value.length === 19 && value > "9223372036854775807" ? null : value;
}

function cartLoginReturnUrl() {
  const params = new URLSearchParams(window.location.search);
  if (params.get("return") === "shipping-addresses") return "shipping-addresses.html";
  if (params.get("return") === "support-inquiries") {
    const id = supportInquiryId(params.get("inquiryId"));
    return id ? `support-inquiries.html?inquiryId=${id}` : "support-inquiries.html";
  }
  if (params.get("return") === "support-admin") return "support-admin.html";
  if (params.get("return") === "notifications") return "notifications.html";
  if (params.get("return") === "notification-admin") return "notification-admin.html";
  if (params.get("return") === "inventory") return "inventory.html";
  if (params.get("return") === "product-register") return "product-register.html";
  if (params.get("return") === "cart") return "cart.html";
  const id=params.get("id");
  if(params.get("return")==="product" && /^[1-9][0-9]{0,18}$/.test(id||""))return `product-detail.html?id=${id}`;
  return null;
}

function checkoutLoginReturnUrl() {
  const params = new URLSearchParams(window.location.search);
  const cart = params.get("cart");
  if(params.get("return")==="checkout" && /^[0-9a-f-]{36}$/.test(cart||""))return `checkout.html?cart=${cart}`;
  const productId = params.get("id");
  if (params.get("return") !== "checkout" || !/^[1-9][0-9]{0,18}$/.test(productId || "")) return null;
  return `checkout.html?id=${productId}`;
}

// 로그인 복귀에는 고정 경로와 검증된 조회 조건만 허용한다.
function paymentOperationsLocation(params) {
  const target = new URLSearchParams();
  const kind = params.get("kind");
  if (kind && !["VERIFY_APPROVAL", "COMPENSATE_APPROVAL", "CANCELLATION"].includes(kind)) return null;
  if (kind) target.set("kind", kind);
  const orderId = params.get("orderId");
  if (orderId && (orderId.length > 64 || !orderId.trim())) return null;
  if (orderId) target.set("orderId", orderId);
  for (const [key, fallback, min, max] of [["page", 0, 0, 2147483647], ["size", 20, 1, 100]]) {
    const raw = params.get(key) ?? String(fallback);
    const value = Number(raw);
    if (!/^[0-9]+$/.test(raw) || !Number.isSafeInteger(value) || value < min || value > max) return null;
    target.set(key, String(value));
  }
  const type = params.get("type"), id = params.get("id");
  if (type !== null || id !== null) {
    if (!["APPROVAL", "CANCELLATION"].includes(type) || !/^[1-9][0-9]{0,18}$/.test(id || "")
        || BigInt(id) > 9223372036854775807n) return null;
    target.set("type", type); target.set("id", id);
  }
  return target;
}
function paymentOperationsLoginReturnUrl() {
  const params = new URLSearchParams(window.location.search);
  if (params.get("return") !== "payment-operations") return null;
  const target = paymentOperationsLocation(params);
  return target ? `payment-operations.html?${target}` : null;
}

function returnOperationsLoginReturnUrl() {
  const params = new URLSearchParams(location.search);
  if (params.get("return") !== "returns") return null;
  const target = new URLSearchParams();
  const id = params.get("id"), page = params.get("page") || "0", orderId = params.get("orderId");
  if (!/^[0-9]+$/.test(page) || Number(page) > 107374182) return null;
  target.set("page", page);
  if (id !== null) {
    if (!/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n) return null;
    target.set("id", id);
  }
  if (orderId) {
    if (!orderId.trim() || orderId.length > 64) return null;
    target.set("orderId", orderId);
  }
  for (const [key, values] of [["type", ["CANCELLATION", "RETURN"]],
      ["status", ["REQUESTED", "APPROVED", "REJECTED", "COLLECTING", "RECEIVED", "INSPECTED"]]]) {
    const value = params.get(key);
    if (value) { if (!values.includes(value)) return null; target.set(key, value); }
  }
  return `returns.html?${target}`;
}

function initLoginPage() {
  // 관리자 신청 화면은 고정 경로와 검증된 조회 조건으로만 복귀한다.
  const form = document.querySelector("[data-login-form]");
  const socialButtons = document.querySelectorAll("[data-social-login]");

  socialButtons.forEach((button) => {
    button.addEventListener("click", () => {
      setAuthMessage("소셜 로그인은 준비 중입니다. 이메일 로그인을 이용해 주세요.");
    });
  });

  if (!form) {
    return;
  }

  const submitButton = form.querySelector(".login-submit");
  const defaultSubmitText = submitButton.textContent;

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    setAuthMessage("");

    const formData = new FormData(form);
    const payload = {
      email: String(formData.get("email") || ""),
      password: String(formData.get("password") || ""),
    };

    submitButton.disabled = true;
    submitButton.textContent = "로그인 중";

    try {
      const response = await requestAuth(authApi.login, {
        method: "POST",
        body: JSON.stringify(payload),
      });
      const data = await readJson(response);

      if (!response.ok) {
        throw new Error(data.message || "이메일 또는 비밀번호를 확인해 주세요.");
      }

      const paymentReturnUrl = takePaymentCallbackLoginReturnUrl();
      window.location.href = orderHistoryLoginReturnUrl() || checkoutLoginReturnUrl() || cartLoginReturnUrl() || paymentOperationsLoginReturnUrl() || returnOperationsLoginReturnUrl()
        || paymentReturnUrl || loginRedirectUrl;
    } catch (error) {
      setAuthMessage(error.message || "로그인 중 문제가 발생했습니다.");
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = defaultSubmitText;
    }
  });
}

function initSignupPage() {
  const form = document.querySelector("[data-signup-form]");

  if (!form) {
    return;
  }

  const submitButton = form.querySelector(".login-submit");
  const defaultSubmitText = submitButton.textContent;

  const policies = createSignupPolicyState(form);
  let pending = false;
  const fields = setupSignupFields(form, () => pending);
  policies.load();

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (pending || !policies.ready() || !fields.validate()) return;
    setAuthMessage("");

    const formData = new FormData(form);
    const payload = {
      email: String(formData.get("email") || ""),
      password: String(formData.get("password") || ""),
      name: String(formData.get("name") || ""),
      address: String(formData.get("address") || ""),
      phoneNumber: String(formData.get("phoneNumber") || ""),
      emailReceiveConsent: String(formData.get("emailReceiveConsent") || ""),
    };

    payload.consents = policies.consents(payload.emailReceiveConsent);
    pending = true;
    policies.setBusy(true);
    fields.setBusy(true);
    submitButton.disabled = true;
    submitButton.textContent = "가입 중";

    try {
      const response = await requestAuth(authApi.signup, {
        method: "POST",
        body: JSON.stringify(payload),
      });
      const data = await readJson(response);

      if (!response.ok) {
        if (data.code === "POLICY_02" || data.code === "POLICY_01") {
          policies.setBusy(false);
          await policies.load();
        }
        throw new Error(data.error || data.message || "회원가입 정보를 확인해 주세요.");
      }

      window.location.href = "login.html";
    } catch (error) {
      setAuthMessage(error instanceof TypeError
        ? "가입 응답을 확인하지 못했습니다. 로그인으로 가입 여부를 확인한 뒤 다시 시도해 주세요."
        : error.message || "회원가입 중 문제가 발생했습니다.");
    } finally {
      pending = false;
      fields.setBusy(false);
      policies.setBusy(false);
      submitButton.disabled = !policies.ready();
      submitButton.textContent = defaultSubmitText;
    }
  });
}

function renderAnonymousSession(menuSession) {
  const message = document.createElement("p");
  const loginLink = document.createElement("a");

  message.textContent = "로그인이 필요합니다.";
  loginLink.href = "login.html";
  loginLink.textContent = "로그인";

  menuSession.replaceChildren(message, loginLink);
  renderAdminProductRegisterLink({ admin: false });
}

function renderAuthenticatedSession(menuSession, session) {
  const message = document.createElement("p");
  const actions = document.createElement("span");
  const mypageLink = document.createElement("a");
  const logoutButton = document.createElement("button");

  message.textContent = `${session.name || session.email}님 반갑습니다.`;
  actions.className = "menu-session-actions";
  mypageLink.href = "mypage-verify.html";
  mypageLink.textContent = "마이페이지";
  logoutButton.type = "button";
  logoutButton.textContent = "로그아웃";
  logoutButton.addEventListener("click", async () => {
    logoutButton.disabled = true;
    logoutButton.textContent = "로그아웃 중";

    try {
      const response = await requestAuth(authApi.logout, { method: "POST" });

      if (!response.ok) {
        throw new Error("로그아웃에 실패했습니다.");
      }

      renderAnonymousSession(menuSession);
      authSessionPromise = Promise.resolve({ authenticated: false, admin: false });
      renderCartCount({totalQuantity:0});
      if(document.querySelector("[data-cart-page], [data-checkout-page], [data-shipping-address-page], [data-product-detail]"))window.location.reload();
    } catch (error) {
      message.textContent = error.message || "로그아웃에 실패했습니다. 다시 시도해 주세요.";
      logoutButton.disabled = false;
      logoutButton.textContent = "로그아웃";
    }
  });

  const addressLink = document.createElement("a");
  addressLink.href = "shipping-addresses.html"; addressLink.textContent = "배송지 관리";
  actions.append(mypageLink, addressLink, logoutButton);
  menuSession.replaceChildren(message, actions);
}

function setMypageMessage(messageBox, message, success = false) {
  if (!messageBox) {
    return;
  }

  messageBox.textContent = message || "";
  messageBox.hidden = !message;
  messageBox.classList.toggle("is-success", Boolean(message && success));
}

function renderMypageGuard(guard, message, linkHref, linkText) {
  const text = document.createElement("p");
  text.textContent = message;

  if (!linkHref || !linkText) {
    guard.replaceChildren(text);
    return;
  }

  const link = document.createElement("a");
  link.href = linkHref;
  link.textContent = linkText;
  guard.replaceChildren(text, link);
}

function redirectMypageVerification(response, body) {
  if (response.status === 403 && body.code === "AUTH_03") {
    window.location.replace("mypage-verify.html");
    return true;
  }
  return false;
}

// 뒤로 가기 캐시로 회원정보나 입력한 비밀번호가 다시 나타나지 않게 합니다.
function protectMypageHistory(page, content) {
  window.addEventListener("pagehide", () => {
    content.hidden = true;
    page.querySelectorAll("input").forEach((input) => {
      input.value = "";
      input.checked = false;
    });
  });
  window.addEventListener("pageshow", (event) => {
    if (event.persisted) window.location.reload();
  });
}

async function initMypageVerification() {
  const page = document.querySelector("[data-mypage-verification-page]");
  if (!page) return;
  const guard = page.querySelector("[data-mypage-guard]");
  const content = page.querySelector("[data-verification-content]");
  const form = page.querySelector("[data-verification-form]");
  const message = page.querySelector("[data-verification-message]");
  const submitButton = page.querySelector("[data-verification-submit]");
  protectMypageHistory(page, content);
  const session = await loadAuthSession();
  if (session.loadFailed) {
    renderMypageGuard(guard, "로그인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.", "mypage-verify.html", "다시 시도");
    return;
  }
  if (!session.authenticated) {
    renderMypageGuard(guard, "본인 확인은 로그인 후 이용할 수 있습니다.", "login.html", "로그인하기");
    return;
  }
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submitButton.disabled || !form.reportValidity()) return;
    setMypageMessage(message, "");
    submitButton.disabled = true;
    submitButton.textContent = "확인 중";
    try {
      const response = await requestAuth(membersApi.verification, {
        method: "POST",
        headers: { "X-Mypage-Verification": "1" },
        body: JSON.stringify({ currentPassword: form.elements.currentPassword.value }),
      });
      const body = await readJson(response);
      if (response.status === 401) {
        content.hidden = true;
        guard.hidden = false;
        renderMypageGuard(guard, "로그인이 만료되었습니다. 다시 로그인해 주세요.", "login.html", "로그인하기");
        return;
      }
      if (!response.ok) throw new Error(body.error || body.message || "본인 확인을 완료하지 못했습니다.");
      window.location.replace("mypage.html");
    } catch (error) {
      setMypageMessage(message, error instanceof TypeError
        ? "네트워크 연결을 확인한 후 다시 시도해 주세요."
        : error.message || "본인 확인을 완료하지 못했습니다. 다시 시도해 주세요.");
      form.elements.currentPassword.focus();
    } finally {
      form.elements.currentPassword.value = "";
      submitButton.disabled = false;
      submitButton.textContent = "확인하고 계속하기";
    }
  });
  guard.hidden = true;
  content.hidden = false;
  submitButton.disabled = false;
  form.elements.currentPassword.focus();
}

async function initMyPage() {
  const page = document.querySelector("[data-mypage-page]");

  if (!page) {
    return;
  }

  const guard = page.querySelector("[data-mypage-guard]");
  const content = page.querySelector("[data-mypage-content]");
  const profileForm = page.querySelector("[data-profile-form]");
  const passwordForm = page.querySelector("[data-password-form]");
  const profileMessage = page.querySelector("[data-profile-message]");
  const passwordMessage = page.querySelector("[data-password-message]");
  const postalCodeInput = profileForm?.querySelector("[data-address-postal-code]");
  const basicAddressInput = profileForm?.querySelector("[data-address-basic]");
  const detailAddressInput = profileForm?.querySelector("[data-address-detail]");
  const addressSearchButton = profileForm?.querySelector("[data-address-search]");
  const addressSearchStatus = profileForm?.querySelector("[data-address-search-status]");

  if (!guard || !content || !profileForm || !passwordForm || !postalCodeInput
      || !basicAddressInput || !detailAddressInput || !addressSearchButton
      || !addressSearchStatus) {
    return;
  }

  protectMypageHistory(page, content);
  const session = await loadAuthSession();

  if (session.loadFailed) {
    renderMypageGuard(guard, "로그인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.", "index.html", "홈으로");
    return;
  }

  if (!session.authenticated) {
    renderMypageGuard(guard, "마이페이지는 로그인 후 이용할 수 있습니다.", "login.html", "로그인하기");
    return;
  }

  let profile;
  try {
    const response = await requestAuth(membersApi.profile);
    const body = await readJson(response);

    if (redirectMypageVerification(response, body)) return;

    if (response.status === 401) {
      renderMypageGuard(guard, "로그인이 만료되었습니다. 다시 로그인해 주세요.", "login.html", "로그인하기");
      return;
    }

    if (!response.ok || !body.data) {
      throw new Error(body.error || body.message || "회원정보를 불러오지 못했습니다.");
    }

    profile = body.data;
  } catch (error) {
    renderMypageGuard(guard, error.message || "회원정보를 불러오지 못했습니다.", "index.html", "홈으로");
    return;
  }

  profileForm.elements.email.value = profile.email || "";
  profileForm.elements.name.value = profile.name || "";
  profileForm.elements.phoneNumber.value = profile.phoneNumber || "";
  let storedAddress = String(profile.address || "");
  let addressDirty = false;

  function showStoredAddress(address) {
    const parsedAddress = parseStoredAddress(address);
    storedAddress = String(address || "");
    addressDirty = false;
    postalCodeInput.value = parsedAddress.postalCode;
    basicAddressInput.value = parsedAddress.basicAddress;
    detailAddressInput.value = parsedAddress.detailAddress;
    postalCodeInput.required = Boolean(parsedAddress.postalCode);
    postalCodeInput.setCustomValidity("");
    basicAddressInput.setCustomValidity("");
    detailAddressInput.setCustomValidity("");
  }

  function markAddressChanged() {
    addressDirty = true;
    postalCodeInput.required = true;
  }

  function readProfileAddress() {
    if (!addressDirty) {
      return storedAddress;
    }

    const postalCode = normalizeAddressPart(postalCodeInput.value);
    const basicAddress = normalizeAddressPart(basicAddressInput.value);
    const detailAddress = normalizeAddressPart(detailAddressInput.value);
    const address = formatShippingAddress(postalCode, basicAddress, detailAddress);

    postalCodeInput.setCustomValidity("");
    basicAddressInput.setCustomValidity("");
    detailAddressInput.setCustomValidity("");

    if (!/^[0-9]{5}$/.test(postalCode)) {
      postalCodeInput.setCustomValidity("우편번호 5자리를 입력해 주세요.");
    }

    if (!basicAddress) {
      basicAddressInput.setCustomValidity("기본주소를 입력해 주세요.");
    }

    if (address.length > 500) {
      const invalidInput = detailAddress ? detailAddressInput : basicAddressInput;
      invalidInput.setCustomValidity("우편번호와 상세주소를 포함한 전체 주소는 500자 이하로 입력해 주세요.");
    }

    return address;
  }

  showStoredAddress(storedAddress);
  setupKakaoAddressSearch({
    postalCodeInput,
    basicAddressInput,
    detailAddressInput,
    searchButton: addressSearchButton,
    statusElement: addressSearchStatus,
    onAddressChange: markAddressChanged,
  });

  const consentInput = profileForm.querySelector(`input[name="emailReceiveConsent"][value="${profile.emailReceiveConsent || "N"}"]`);
  if (consentInput) {
    consentInput.checked = true;
  }

  guard.hidden = true;
  content.hidden = false;

  profileForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setMypageMessage(profileMessage, "");

    const formData = new FormData(profileForm);
    const submitButton = profileForm.querySelector("[data-profile-submit]");
    const defaultText = submitButton.textContent;
    const address = readProfileAddress();

    if (!profileForm.reportValidity()) {
      return;
    }

    const payload = {
      email: String(formData.get("email") || ""),
      name: String(formData.get("name") || ""),
      address,
      phoneNumber: String(formData.get("phoneNumber") || ""),
      emailReceiveConsent: String(formData.get("emailReceiveConsent") || ""),
    };

    submitButton.disabled = true;
    submitButton.textContent = "저장 중";

    try {
      const response = await requestAuth(membersApi.profile, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
      const body = await readJson(response);

      if (redirectMypageVerification(response, body)) {
        content.hidden = true;
        return;
      }

      if (response.status === 401 || response.status === 403) {
        content.hidden = true;
        guard.hidden = false;
        renderMypageGuard(guard, "로그인이 만료되었습니다. 다시 로그인해 주세요.", "login.html", "로그인하기");
        return;
      }

      if (!response.ok || !body.data) {
        throw new Error(body.error || body.message || "회원정보를 저장하지 못했습니다.");
      }

      profileForm.elements.name.value = body.data.name || "";
      profileForm.elements.phoneNumber.value = body.data.phoneNumber || "";
      storedAddress = String(body.data.address || address);
      addressDirty = false;
      postalCodeInput.required = Boolean(normalizeAddressPart(postalCodeInput.value));
      setMypageMessage(profileMessage, "회원정보가 수정되었습니다.", true);
      authSessionPromise = null;
    } catch (error) {
      setMypageMessage(profileMessage, error.message || "회원정보를 저장하지 못했습니다.");
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = defaultText;
    }
  });

  passwordForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setMypageMessage(passwordMessage, "");

    const formData = new FormData(passwordForm);
    const payload = {
      currentPassword: String(formData.get("currentPassword") || ""),
      newPassword: String(formData.get("newPassword") || ""),
      newPasswordConfirm: String(formData.get("newPasswordConfirm") || ""),
    };

    if (payload.newPassword !== payload.newPasswordConfirm) {
      setMypageMessage(passwordMessage, "새 비밀번호와 확인 값을 동일하게 입력해 주세요.");
      return;
    }

    const submitButton = passwordForm.querySelector("[data-password-submit]");
    const defaultText = submitButton.textContent;
    submitButton.disabled = true;
    submitButton.textContent = "변경 중";

    try {
      const response = await requestAuth(membersApi.password, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
      const body = await readJson(response);

      if (redirectMypageVerification(response, body)) {
        content.hidden = true;
        return;
      }

      if (response.status === 401 || response.status === 403) {
        content.hidden = true;
        guard.hidden = false;
        renderMypageGuard(guard, "로그인이 만료되었습니다. 다시 로그인해 주세요.", "login.html", "로그인하기");
        return;
      }

      if (!response.ok) {
        throw new Error(body.error || body.message || "비밀번호를 변경하지 못했습니다.");
      }

      passwordForm.reset();
      content.hidden = true;
      guard.hidden = false;
      renderMypageGuard(guard, "비밀번호가 변경되었습니다. 새 비밀번호로 다시 본인 확인해 주세요.", "mypage-verify.html", "본인 확인하기");
    } catch (error) {
      setMypageMessage(passwordMessage, error.message || "비밀번호를 변경하지 못했습니다.");
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = defaultText;
    }
  });
}

function renderAdminProductRegisterLink(session) {
  document.querySelectorAll("[data-admin-product-register]").forEach((link) => link.remove());

  if (!session.admin) {
    return;
  }

  const drawerNav = document.querySelector(".drawer-nav");

  if (!drawerNav) {
    return;
  }

  const registerLink = document.createElement("a");
  registerLink.className = "drawer-link";
  registerLink.href = "product-register.html";
  registerLink.textContent = "상품 등록";
  registerLink.dataset.adminProductRegister = "";
  drawerNav.append(registerLink);
  const inventoryLink = document.createElement("a");
  inventoryLink.className = "drawer-link";
  inventoryLink.href = "inventory.html";
  inventoryLink.textContent = "재고 관리";
  inventoryLink.dataset.adminProductRegister = "";
  drawerNav.append(inventoryLink);
  const shippingLink = document.createElement("a");
  shippingLink.className = "drawer-link";
  shippingLink.href = "shipping.html";
  shippingLink.textContent = "배송 관리";
  shippingLink.dataset.adminProductRegister = "";
  drawerNav.append(shippingLink);
  const operationsLink = document.createElement("a");
  operationsLink.className = "drawer-link";
  operationsLink.href = "payment-operations.html";
  operationsLink.textContent = "결제·환불 운영";
  operationsLink.dataset.adminProductRegister = "";
  drawerNav.append(operationsLink);
  const returnsLink = document.createElement("a");
  returnsLink.className = "drawer-link";
  returnsLink.href = "returns.html";
  returnsLink.textContent = "취소·반품 운영";
  returnsLink.dataset.adminProductRegister = "";
  drawerNav.append(returnsLink);
}

function loadAuthSession() {
  if (!authSessionPromise) {
    authSessionPromise = (async () => {
      try {
        const response = await requestAuth(authApi.session);

        if (!response.ok) {
          return { authenticated: false, admin: false, loadFailed: true };
        }

        return await readJson(response);
      } catch {
        return { authenticated: false, admin: false, loadFailed: true };
      }
    })();
  }

  return authSessionPromise;
}

async function initAuthSession() {
  const menuSession = document.querySelector(".menu-session");

  if (!menuSession) {
    return;
  }

  const session = await loadAuthSession();
  renderAdminProductRegisterLink(session);

  if (session.authenticated) {
    renderAuthenticatedSession(menuSession, session);
  } else {
    renderAnonymousSession(menuSession);
  }
}

function setProductRegisterMessage(message) {
  const messageBox = document.querySelector("[data-product-register-message]");

  if (!messageBox) {
    return;
  }

  messageBox.textContent = message;
  messageBox.hidden = !message;
}

async function initProductRegisterPage() {
  const page = document.querySelector("[data-product-register-page]");

  if (!page) {
    return;
  }

  const guard = page.querySelector("[data-product-register-guard]");
  const form = page.querySelector("[data-product-register-form]");
  const imageInput = form?.querySelector('input[name="image"]');
  const imageName = page.querySelector("[data-product-image-name]");
  const submitButton = form?.querySelector("[data-product-register-submit]");
  const session = await loadAuthSession();

  function showRegistrationGuard() {
    form.hidden = true;
    guard.hidden = false;
    const login = document.createElement("a");
    login.href = "login.html?return=product-register";
    login.textContent = "운영자 로그인";
    guard.replaceChildren("상품을 등록하려면 운영자 권한이 필요합니다. ", login);
  }

  if (session.loadFailed || !session.authenticated || !session.admin) {
    if (guard && form) {
      if (session.loadFailed) guard.textContent = "로그인 상태를 확인하지 못했습니다. 새로고침해 주세요.";
      else showRegistrationGuard();
    }
    return;
  }

  if (!guard || !form || !imageInput || !imageName || !submitButton) {
    if (guard) {
      guard.textContent = "상품 등록 화면을 불러오지 못했습니다.";
    }
    return;
  }

  const category = form.elements.categoryId;
  try {
    const response = await requestAuth("/api/products/categories", {cache: "no-store"});
    const body = await readJson(response);
    if (!response.ok || !Array.isArray(body.data)) throw new Error();
    for (const item of body.data) {
      const option = document.createElement("option");
      option.value = String(item.id);
      option.textContent = item.name;
      category.append(option);
    }
  } catch {
    setProductRegisterMessage("상품 분류를 불러오지 못했습니다. 미분류로 등록하거나 새로고침 후 다시 시도해주세요.");
  }

  guard.hidden = true;
  form.hidden = false;

  imageInput.addEventListener("change", () => {
    const image = imageInput.files?.[0];
    imageName.textContent = image ? `${image.name} · ${(image.size / 1024 / 1024).toFixed(2)}MB` : "선택된 이미지가 없습니다.";
  });

  const defaultSubmitText = submitButton.textContent;

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    setProductRegisterMessage("");

    const formValues = new FormData(form);
    const image = imageInput.files?.[0];

    if (!image) {
      setProductRegisterMessage("대표 이미지를 선택해 주세요.");
      return;
    }

    if (image.size > maxProductImageSize) {
      setProductRegisterMessage("상품 이미지는 5MB 이하만 등록할 수 있습니다.");
      return;
    }

    const payload = {
      name: String(formValues.get("name") || ""),
      price: Number(formValues.get("price") || 0),
      summary: String(formValues.get("summary") || ""),
      detailTitle: String(formValues.get("detailTitle") || ""),
      description: String(formValues.get("description") || ""),
      categoryId: category.value ? Number(category.value) : null,
    };
    const requestBody = new FormData();
    requestBody.append("product", new Blob([JSON.stringify(payload)], { type: "application/json" }));
    requestBody.append("image", image);

    submitButton.disabled = true;
    submitButton.textContent = "등록 중";

    try {
      const response = await requestAuth(productsApi.list, {
        method: "POST",
        body: requestBody,
      });
      const body = await readJson(response);

      if (response.status === 401 || response.status === 403) {
        showRegistrationGuard();
        return;
      }

      if (!response.ok || !body.data?.id) {
        throw new Error(body.error || body.message || "상품을 등록하지 못했습니다.");
      }

      window.location.href = `product-detail.html?id=${encodeURIComponent(body.data.id)}`;
    } catch (error) {
      setProductRegisterMessage(error.message || "상품 등록 중 문제가 발생했습니다.");
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = defaultSubmitText;
    }
  });
}

function createProductCard(product) {
  const link = document.createElement("a");
  const image = document.createElement("span");
  const info = document.createElement("span");
  const name = document.createElement("span");
  const price = document.createElement("span");

  link.className = "product-card";
  link.href = `product-detail.html?id=${encodeURIComponent(product.id)}`;
  link.setAttribute("aria-label", `${product.name} 상세 페이지로 이동`);

  image.className = "product-image";
  image.setAttribute("role", "img");
  image.setAttribute("aria-label", `${product.name} 이미지`);

  if (product.imageUrl) {
    image.classList.add("has-image");
    image.style.backgroundImage = `url("${sanitizeCssUrl(product.imageUrl)}")`;
  }

  info.className = "product-info";
  name.className = "product-name";
  price.className = "product-price";
  name.textContent = product.name;
  price.textContent = `${formatPrice(product.price)} · ${inventoryLabel(product)}`;

  info.append(name, price);
  link.append(image, info);

  return link;
}

function renderDiscoveryCategoryLinks(categories) {
  const byId = new Map(categories.map(category => [String(category.id), category]));
  for (const link of document.querySelectorAll("[data-discovery-category]")) {
    const category = byId.get(link.dataset.discoveryCategory);
    link.hidden = !category;
    if (category) {
      const name = link.querySelector(".category-name");
      if (name) name.textContent = category.name;
      else link.textContent = category.name;
    }
  }
}

async function initHomeDiscoveryLinks() {
  if (document.querySelector("[data-products-page]") || !document.querySelector("[data-discovery-category]")) return;
  try {
    const response = await requestAuth("/api/products/categories", {cache: "no-store"});
    const body = await readJson(response);
    if (response.ok && Array.isArray(body.data)) renderDiscoveryCategoryLinks(body.data);
  } catch {
    // 전체 상품 링크는 항상 남겨 둔다.
  }
}

function setDetailText(product) {
  document.title = `${product.name} | nanacocoa`;
  document.querySelector("#breadcrumb-product").textContent = product.name;
  document.querySelector("#product-title").textContent = product.name;
  document.querySelector("#product-summary").textContent = product.summary;
  document.querySelector("#product-price").textContent = formatPrice(product.price);
  document.querySelector("#detail-info-title").textContent = product.detailTitle;
  document.querySelector("#product-description").textContent = product.description;
}

function setProductDetailUnavailable(message) {
  const productImage = document.querySelector("#product-image");
  const dots = document.querySelector("#gallery-dots");
  const prevButton = document.querySelector("[data-gallery-prev]");
  const nextButton = document.querySelector("[data-gallery-next]");
  const cartButton = document.querySelector("[data-cart-button]");
  const buyButton = document.querySelector("[data-buy-button]");

  document.title = `${message} | nanacocoa`;
  document.querySelector("#breadcrumb-product").textContent = message;
  document.querySelector("#product-title").textContent = message;
  document.querySelector("#product-summary").textContent = "등록된 상품 정보를 다시 확인해 주세요.";
  document.querySelector("#product-price").textContent = "";
  document.querySelector("#detail-info-title").textContent = message;
  document.querySelector("#product-description").textContent = "상품 목록에서 다시 선택해 주세요.";

  productImage.src = defaultProductImage;
  productImage.alt = message;
  dots.replaceChildren();

  [prevButton, nextButton, cartButton, buyButton].forEach((button) => {
    button.disabled = true;
  });
}

async function initProductDetail() {
  const detailPage = document.querySelector("[data-product-detail]");

  if (!detailPage) {
    return;
  }

  const params = new URLSearchParams(window.location.search);
  const productId = params.get("id");
  const productImage = document.querySelector("#product-image");
  const dots = document.querySelector("#gallery-dots");
  const prevButton = document.querySelector("[data-gallery-prev]");
  const nextButton = document.querySelector("[data-gallery-next]");
  const cartButton = document.querySelector("[data-cart-button]");
  const buyButton = document.querySelector("[data-buy-button]");
  let activeSlide = 0;

  if (!productId) {
    setProductDetailUnavailable("상품을 찾을 수 없습니다.");
    return;
  }

  let product;

  try {
    const response = await requestAuth(productsApi.detail(productId));
    const body = await readJson(response);

    if (!response.ok || !body.data) {
      throw new Error(body.error || "상품을 찾을 수 없습니다.");
    }

    product = body.data;
  } catch (error) {
    setProductDetailUnavailable(error.message || "상품을 찾을 수 없습니다.");
    return;
  }

  setDetailText(product);
  initRestockSubscription(detailPage, product, productId);
  const purchasable = product.availability === "ON_SALE";
  buyButton.disabled = !purchasable;
  cartButton.disabled = !purchasable;
  buyButton.textContent = purchasable ? "구매하기" : inventoryLabel(product);
  document.querySelector("#product-summary").textContent = `${product.summary} · ${inventoryLabel(product)}`;

  function renderSlide() {
    const view = galleryViews[activeSlide];
    productImage.src = getProductImageUrl(product);
    productImage.alt = `${product.name} ${view.label}`;
    productImage.style.objectPosition = view.position;

    dots.querySelectorAll(".gallery-dot").forEach((dot, index) => {
      const isActive = index === activeSlide;
      dot.classList.toggle("is-active", isActive);
      dot.setAttribute("aria-current", isActive ? "true" : "false");
    });
  }

  galleryViews.forEach((view, index) => {
    const dot = document.createElement("button");
    dot.className = "gallery-dot";
    dot.type = "button";
    dot.setAttribute("aria-label", `${view.label} 보기`);
    dot.addEventListener("click", () => {
      activeSlide = index;
      renderSlide();
    });
    dots.append(dot);
  });

  prevButton.addEventListener("click", () => {
    activeSlide = (activeSlide - 1 + galleryViews.length) % galleryViews.length;
    renderSlide();
  });

  nextButton.addEventListener("click", () => {
    activeSlide = (activeSlide + 1) % galleryViews.length;
    renderSlide();
  });

  let pendingCartKey = null;
  cartButton.addEventListener("click", async () => {
    cartButton.disabled = true;
    try {
      const session = await freshCartSession();
      if (!session.authenticated) { window.location.href = `login.html?return=product&id=${encodeURIComponent(product.id)}`; return; }
      pendingCartKey ||= randomUuid();
      const cart = await cartRequest("/api/cart/items", {method:"POST", headers:{"Idempotency-Key":pendingCartKey}, body:JSON.stringify({productId:product.id,quantity:1})});
      pendingCartKey = null;
      renderCartCount(cart);
      cartButton.textContent = "장바구니 담김";
    } catch (error) {
      if (error.definitive) pendingCartKey = null;
      cartButton.textContent = error.message || "담기 결과 확인을 위해 다시 눌러주세요.";
    } finally { cartButton.disabled = false; }
  });

  buyButton.addEventListener("click", () => {
    window.location.href = `checkout.html?id=${encodeURIComponent(product.id)}`;
  });

  renderSlide();
}

function renderCheckoutGuard(guard, message, linkHref, linkText) {
  const text = document.createElement("p");
  text.textContent = message;

  if (!linkHref || !linkText) {
    guard.replaceChildren(text);
    return;
  }

  const link = document.createElement("a");
  link.href = linkHref;
  link.textContent = linkText;
  guard.replaceChildren(text, link);
}

function setCheckoutMessage(messageBox, message, loginHref = null) {
  messageBox.replaceChildren(document.createTextNode(message || ""));

  if (!loginHref) {
    return;
  }

  const loginLink = document.createElement("a");
  loginLink.href = loginHref;
  loginLink.textContent = "로그인하기";
  messageBox.append(" ", loginLink);
}

function checkoutApiError(body, fallback) {
  return body?.error || body?.message || fallback;
}

async function fetchTossPaymentClientKey() {
  const response = await requestAuth(paymentsApi.clientConfig, { cache: "no-store" });
  const body = await readJson(response);

  if (response.status === 401) {
    const error = new Error("로그인이 만료되었습니다. 다시 로그인해 주세요.");
    error.requiresLogin = true;
    throw error;
  }

  if (response.status === 503) {
    throw new Error("현재 토스페이 결제를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
  }
  if (!response.ok || !body?.data?.clientKey) {
    throw new Error(checkoutApiError(body, "토스페이 설정을 불러오지 못했습니다."));
  }
  return body.data.clientKey;
}

async function createTossPayment(clientKey) {
  const TossPayments = await loadTossPaymentsSdk();
  return TossPayments(clientKey).payment({ customerKey: checkoutCustomerKey() });
}

function checkoutPaymentCallbackUrl(result, context) {
  const callbackUrl = new URL("checkout.html", window.location.href);
  callbackUrl.searchParams.set("id", context.productId);
  if(context.cartSelectionId)callbackUrl.searchParams.set("cart",context.cartSelectionId);
  callbackUrl.searchParams.set("paymentResult", result);
  callbackUrl.searchParams.set("checkoutOrderId", context.orderId);
  return callbackUrl.toString();
}

function checkoutMobilePhone(value) {
  const mobilePhone = String(value || "").replace(/\D/g, "");
  return mobilePhone.length >= 8 && mobilePhone.length <= 15 ? mobilePhone : null;
}

async function requestTossPay(payment, context) {
  const request = {
    method: "CARD",
    amount: {
      currency: "KRW",
      value: context.amount,
    },
    orderId: context.orderId,
    orderName: context.orderName,
    successUrl: checkoutPaymentCallbackUrl("success", context),
    failUrl: checkoutPaymentCallbackUrl("fail", context),
    card: {
      flowMode: "DIRECT",
      easyPay: "TOSSPAY",
    },
  };
  if (context.customerName) {
    request.customerName = context.customerName;
  }
  if (context.customerEmail) {
    request.customerEmail = context.customerEmail;
  }
  const mobilePhone = checkoutMobilePhone(context.customerMobilePhone);
  if (mobilePhone) {
    request.customerMobilePhone = mobilePhone;
  }

  await payment.requestPayment(request);
}

function clearCheckoutCallbackQuery(productId) {
  const cleanUrl = new URL("checkout.html", window.location.href);
  if (productId) {
    cleanUrl.searchParams.set("id", productId);
  }
  window.history.replaceState({}, "", cleanUrl);
}

async function initCheckoutPage() {
  const page = document.querySelector("[data-checkout-page]");

  if (!page) {
    return;
  }

  const form = page.querySelector("[data-checkout-form]");
  const guard = page.querySelector("[data-checkout-guard]");
  const messageBox = page.querySelector("[data-checkout-message]");
  const submitButton = page.querySelector("[data-order-submit]");
  const paymentMethodToggle = page.querySelector("[data-payment-method-toggle]");
  const paymentMethodPanel = page.querySelector("[data-payment-method-panel]");
  const paymentTermsInput = page.querySelector("[data-payment-terms]");
  const recipientNameInput = form?.querySelector('[name="recipientName"]');
  const phoneNumberInput = form?.querySelector('[name="phoneNumber"]');
  const postalCodeInput = form?.querySelector("[data-address-postal-code]");
  const shippingAddressInput = form?.querySelector("[data-address-basic]");
  const shippingAddressDetailInput = form?.querySelector("[data-address-detail]");
  const addressSearchButton = form?.querySelector("[data-address-search]");
  const addressSearchStatus = form?.querySelector("[data-address-search-status]");
  const customerRequestInput = form?.querySelector('[name="customerRequest"]');
  const customerRequestPreset = form?.querySelector("[data-customer-request-preset]");
  const customerRequestCustom = form?.querySelector("[data-customer-request-custom]");
  const quantityInput = form?.querySelector('[name="quantity"]');
  const quantityDecrease = form?.querySelector("[data-quantity-decrease]");
  const quantityIncrease = form?.querySelector("[data-quantity-increase]");
  const productImage = form?.querySelector("[data-checkout-product-image]");
  const productName = form?.querySelector("[data-checkout-product-name]");
  const unitPrice = form?.querySelector("[data-checkout-unit-price]");
  const lineTotal = form?.querySelector("[data-checkout-line-total]");
  const orderTotal = form?.querySelector("[data-checkout-total]");
  const resultPanel = page.querySelector("[data-checkout-result]");
  const resultEyebrow = resultPanel?.querySelector("[data-payment-result-eyebrow]");
  const resultTitle = resultPanel?.querySelector("[data-payment-result-title]");
  const resultMessage = resultPanel?.querySelector("[data-payment-result-message]");
  const resultOrderId = resultPanel?.querySelector("[data-payment-order-id]");
  const resultTotal = resultPanel?.querySelector("[data-payment-order-total]");
  const statusCheckButton = resultPanel?.querySelector("[data-payment-status-check]");
  const paymentRetryButton = resultPanel?.querySelector("[data-payment-retry]");
  const cancelOrderButton = resultPanel?.querySelector("[data-order-cancel]");
  const startNewOrderButton = resultPanel?.querySelector("[data-order-start-new]");
  const paymentReturnLink = resultPanel?.querySelector("[data-payment-return]");

  if (!form || !guard || !messageBox || !submitButton || !paymentMethodToggle
      || !paymentMethodPanel || !paymentTermsInput || !recipientNameInput
      || !phoneNumberInput || !postalCodeInput || !shippingAddressInput
      || !shippingAddressDetailInput || !addressSearchButton || !addressSearchStatus
      || !customerRequestInput || !customerRequestPreset || !customerRequestCustom
      || !quantityInput || !quantityDecrease || !quantityIncrease || !productImage
      || !productName || !unitPrice || !lineTotal || !orderTotal || !resultPanel
      || !resultEyebrow || !resultTitle || !resultMessage || !resultOrderId
      || !resultTotal || !statusCheckButton || !paymentRetryButton || !startNewOrderButton || !paymentReturnLink) {
    renderCheckoutGuard(guard, "주문 페이지를 표시하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    return;
  }

  const params = new URLSearchParams(window.location.search);
  let storedPaymentContext;
  try {
    storedPaymentContext = readCheckoutPaymentContext();
  } catch (error) {
    renderCheckoutGuard(guard, error.message || "결제 정보를 확인하지 못했습니다.");
    return;
  }

  const cartSelectionId = params.get("cart") || (params.get("paymentResult") ? storedPaymentContext?.cartSelectionId : null);
  let cartSelection = null;
  try { if(cartSelectionId)cartSelection=readCartSelection(cartSelectionId); }
  catch(error){renderCheckoutGuard(guard,error.message,"cart.html","장바구니로 이동");return;}
  const productId = params.get("id") || cartSelection?.items?.[0]?.productId || storedPaymentContext?.productId;
  const paymentResult = params.get("paymentResult");

  if (!productId) {
    renderCheckoutGuard(guard, "주문할 상품 정보가 없습니다.", "products.html", "상품 목록으로 이동");
    return;
  }

  function setPaymentPanelExpanded(expanded) {
    paymentMethodToggle.setAttribute("aria-expanded", String(expanded));
    paymentMethodPanel.hidden = !expanded;
  }

  paymentMethodToggle.addEventListener("click", () => {
    setPaymentPanelExpanded(paymentMethodToggle.getAttribute("aria-expanded") !== "true");
  });

  paymentTermsInput.addEventListener("change", () => paymentTermsInput.setCustomValidity(""));

  function renderPaymentResult({
    eyebrow,
    title,
    message,
    context,
    showStatusCheck = false,
    statusCheckText = "결제 상태 다시 확인",
    showPaymentRetry = false,
    returnHref = "products.html",
    returnText = "쇼핑 계속하기",
  }) {
    guard.hidden = true;
    form.hidden = true;
    if (cancelOrderButton) cancelOrderButton.hidden = true;
    startNewOrderButton.hidden = true;
    resultEyebrow.textContent = eyebrow;
    resultTitle.textContent = title;
    resultMessage.textContent = message;
    resultOrderId.textContent = context?.orderId || "-";
    resultTotal.textContent = context?.amount ? formatPrice(context.amount) : "-";
    statusCheckButton.hidden = !showStatusCheck;
    statusCheckButton.disabled = false;
    statusCheckButton.textContent = statusCheckText;
    paymentRetryButton.hidden = !showPaymentRetry;
    paymentRetryButton.disabled = false;
    paymentRetryButton.textContent = "토스페이 다시 시도";
    paymentReturnLink.href = returnHref;
    paymentReturnLink.textContent = returnText;
    paymentReturnLink.onclick = null;
    resultPanel.hidden = false;
    resultPanel.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  function checkoutReturnHref() {
    return cartSelectionId ? `checkout.html?cart=${encodeURIComponent(cartSelectionId)}` : `checkout.html?id=${encodeURIComponent(productId)}`;
  }

  function checkoutLoginHref() {
    return cartSelectionId ? `login.html?return=checkout&cart=${encodeURIComponent(cartSelectionId)}` : `login.html?return=checkout&id=${encodeURIComponent(productId)}`;
  }

  function renderPaymentSuccess(context) {
    clearCheckoutPaymentContext();
    clearCheckoutCallbackQuery(productId);
    renderPaymentResult({
      eyebrow: "PAYMENT COMPLETED",
      title: "결제가 완료되었습니다.",
      message: "토스페이 인증과 서버 승인이 모두 완료되었습니다.",
      context,
    });
  }

  async function retryTossPayment(context) {
    paymentRetryButton.disabled = true;
    paymentRetryButton.textContent = "토스페이 준비 중";
    resultMessage.textContent = "동일한 주문으로 토스페이 결제를 다시 요청하고 있습니다.";

    try {
      const clientKey = await fetchTossPaymentClientKey();
      const reservationResponse = await requestAuth(`/api/orders/${encodeURIComponent(context.orderId)}/reservation`, { method: "POST" });
      const reservationBody = await readJson(reservationResponse);
      if (!reservationResponse.ok) {
        const error = new Error(checkoutApiError(reservationBody, "주문 재고를 확인하지 못했습니다. 주문서에서 다시 시도해 주세요."));
        error.canRetry = reservationResponse.status !== 409;
        throw error;
      }
      const payment = await createTossPayment(clientKey);
      renewCheckoutPaymentAttempt(context);
      resultMessage.textContent = "토스페이 결제창으로 이동하고 있습니다.";
      await requestTossPay(payment, context);
    } catch (error) {
      renderPaymentFailure(error.message || "토스페이 결제창을 열지 못했습니다.", context, error.canRetry !== false);
    }
  }

  function renderPaymentFailure(message, context, allowPaymentRetry = false, includeLogin = false) {
    renderPaymentResult({
      eyebrow: "PAYMENT FAILED",
      title: "결제를 완료하지 못했습니다.",
      message,
      context,
      showPaymentRetry: allowPaymentRetry && Boolean(context),
      returnHref: includeLogin ? "login.html" : checkoutReturnHref(),
      returnText: includeLogin ? "로그인하기" : "주문서로 돌아가기",
    });
    paymentRetryButton.onclick = allowPaymentRetry && context
      ? () => retryTossPayment(context)
      : null;
    paymentReturnLink.onclick = includeLogin ? null : () => clearCheckoutPaymentContext();
    if (cancelOrderButton && context && !includeLogin) {
      cancelOrderButton.hidden = false;
      cancelOrderButton.disabled = false;
      cancelOrderButton.onclick = async () => {
        cancelOrderButton.disabled = true;
        try {
          const response = await requestAuth(`/api/orders/${encodeURIComponent(context.orderId)}/cancel`, { method: "POST" });
          const body = await readJson(response);
          if (!response.ok) throw new Error(checkoutApiError(body, "주문을 취소하지 못했습니다."));
          clearCheckoutPaymentContext();
          renderPaymentResult({ eyebrow: "ORDER CANCELED", title: "미결제 주문을 취소했습니다.", message: "확보한 재고를 해제했습니다.", context });
        } catch (error) { resultMessage.textContent = error.message; cancelOrderButton.disabled = false; }
      };
    }
  }

  function renderPaymentPending(context, message = "결제사 응답을 확인하고 있습니다. 잠시 후 상태를 다시 확인해 주세요.") {
    try {
      writeCheckoutPaymentContext(context);
    } catch (error) {
      message = `${message} ${error.message}`;
    }
    renderPaymentResult({
      eyebrow: "PAYMENT PENDING",
      title: "결제 승인을 확인하고 있습니다.",
      message,
      context,
      showStatusCheck: true,
    });
    statusCheckButton.onclick = () => checkPaymentStatus(context);
  }

  async function checkPaymentStatus(context) {
    if (!context.paymentId) {
      renderPaymentFailure("확인할 결제 정보가 없습니다. 주문서에서 다시 시도해 주세요.", context);
      return;
    }

    statusCheckButton.disabled = true;
    statusCheckButton.textContent = "결제 상태 확인 중";
    resultMessage.textContent = "서버에 저장된 결제 상태를 확인하고 있습니다.";

    try {
      const response = await requestAuth(paymentsApi.detail(context.paymentId));
      const body = await readJson(response);

      if (response.status === 401) {
        renderPaymentFailure("로그인이 만료되었습니다. 다시 로그인해 결제 상태를 확인해 주세요.", context, false, true);
        return;
      }

      if (!response.ok || !body.data?.status) {
        throw new Error(checkoutApiError(body, "결제 상태를 확인하지 못했습니다."));
      }

      if (body.data.status === "SUCCESS") {
        renderPaymentSuccess(context);
        return;
      }

      if (body.data.status === "PENDING" || body.data.status === "CANCEL_PENDING") {
        renderPaymentPending(context, "아직 결제 승인 확인 중입니다. 잠시 후 다시 확인해 주세요.");
        return;
      }

      renderPaymentFailure("결제 승인이 완료되지 않았습니다. 동일한 주문으로 다시 결제해 주세요.", context, true);
    } catch (error) {
      renderPaymentPending(context, error.message || "결제 상태를 확인하지 못했습니다. 잠시 후 다시 확인해 주세요.");
    }
  }

  async function processPaymentSuccessCallback() {
    const context = storedPaymentContext;
    const paymentKey = params.get("paymentKey");
    const callbackOrderId = params.get("orderId");
    const checkoutOrderId = params.get("checkoutOrderId");
    const callbackAmount = Number(params.get("amount"));

    if (!context || !paymentKey || !callbackOrderId || !checkoutOrderId
        || !Number.isSafeInteger(callbackAmount) || callbackAmount <= 0
        || context.orderId !== callbackOrderId || context.orderId !== checkoutOrderId
        || context.amount !== callbackAmount) {
      renderPaymentFailure("결제 요청 정보가 저장된 주문과 일치하지 않아 승인을 중단했습니다.", context);
      return;
    }

    renderPaymentResult({
      eyebrow: "PAYMENT APPROVAL",
      title: "결제를 승인하고 있습니다.",
      message: "창을 닫거나 새로고침하지 말고 잠시 기다려 주세요.",
      context,
    });

    try {
      const response = await requestAuth(paymentsApi.confirm, {
        method: "POST",
        headers: { "Idempotency-Key": context.idempotencyKey },
        body: JSON.stringify({
          paymentKey,
          orderId: callbackOrderId,
          amount: callbackAmount,
        }),
      });
      const body = await readJson(response);
      const requestId = incidentRequestId(response);

      if (response.status === 401) {
        preservePaymentCallbackLoginReturnUrl();
        renderPaymentFailure(incidentMessage("로그인이 만료되었습니다. 다시 로그인해 결제 승인을 확인해 주세요.", requestId), context, false, true);
        return;
      }

      if (response.status === 202 && body.data?.status === "PENDING" && body.data.paymentId) {
        context.paymentId = body.data.paymentId;
        renderPaymentPending(context, incidentMessage("결제사 응답을 확인하고 있습니다. 잠시 후 상태를 다시 확인해 주세요.", requestId));
        return;
      }

      if (response.ok && body.data?.status === "SUCCESS") {
        context.paymentId = body.data.paymentId;
        renderPaymentSuccess(context);
        return;
      }

      renderPaymentFailure(
        incidentMessage(checkoutApiError(body, "결제 승인에 실패했습니다. 주문서에서 다시 시도해 주세요."), requestId),
        context,
        response.status === 422 || response.status === 503,
      );
    } catch (error) {
      if (error.code === "CSRF_INVALID") {
        authSessionPromise = null;
        const currentSession = await loadAuthSession();
        if (!currentSession.loadFailed && !currentSession.authenticated) {
          preservePaymentCallbackLoginReturnUrl();
          renderPaymentFailure(incidentMessage("로그인이 만료되었습니다. 다시 로그인해 결제 승인을 확인해 주세요.", error.requestId), context, false, true);
          return;
        }
      }
      renderPaymentResult({
        eyebrow: "PAYMENT APPROVAL",
        title: "결제 승인 결과를 확인하지 못했습니다.",
        message: incidentMessage(error.message || "네트워크 연결을 확인한 뒤 승인을 다시 시도해 주세요.", error.requestId),
        context,
        showStatusCheck: true,
        statusCheckText: "결제 승인 다시 시도",
        returnHref: checkoutReturnHref(),
        returnText: "주문서로 돌아가기",
      });
      statusCheckButton.onclick = processPaymentSuccessCallback;
    }
  }

  const session = await loadAuthSession();

  if (session.loadFailed) {
    renderCheckoutGuard(guard, "로그인 상태를 확인하지 못했습니다. 페이지를 새로고침해 주세요.");
    return;
  }

  if (!session.authenticated) {
    if (paymentResult === "success") {
      preservePaymentCallbackLoginReturnUrl();
    }
    renderCheckoutGuard(guard, "주문하려면 로그인이 필요합니다.",
      paymentResult === "success" ? "login.html" : checkoutLoginHref(), "로그인하기");
    return;
  }

  if (paymentResult && storedPaymentContext?.checkoutAccount
      && storedPaymentContext.checkoutAccount !== session.checkoutAccount) {
    if (paymentResult === "success") {
      preservePaymentCallbackLoginReturnUrl();
    }
    renderCheckoutGuard(guard, "이 결제를 시작한 계정으로 로그인해 주세요.", "login.html", "로그인하기");
    return;
  }

  if (paymentResult === "success") {
    if(cartSelectionId && params.get("review")==="1") {
      renderPaymentResult({eyebrow:"결제 확인",title:"로그인했습니다. 결제 상태를 확인해 주세요.",message:"로그인 복귀는 결제 승인을 자동 실행하지 않습니다.",context:storedPaymentContext,showStatusCheck:true,statusCheckText:"결제 승인 확인"});
      statusCheckButton.onclick=processPaymentSuccessCallback;
      return;
    }
    await processPaymentSuccessCallback();
    return;
  }

  if (paymentResult === "fail") {
    const checkoutOrderId = params.get("checkoutOrderId");
    const context = storedPaymentContext?.orderId === checkoutOrderId ? storedPaymentContext : null;
    const failureCode = params.get("code");
    const failureMessage = failureCode === "PAY_PROCESS_CANCELED"
      ? "사용자가 토스페이 결제를 취소했습니다."
      : params.get("message") || "토스페이 인증을 완료하지 못했습니다.";
    renderPaymentFailure(failureMessage, context, Boolean(context));
    return;
  }

  if (!session.checkoutAccount) {
    renderCheckoutGuard(guard, "주문 계정을 확인하지 못했습니다. 새로고침해 주세요.");
    return;
  }
  if(cartSelectionId && (!cartSelection || cartSelection.checkoutAccount !== session.checkoutAccount)) {
    renderCheckoutGuard(guard,"선택한 장바구니 계정 또는 정보가 변경되었습니다. 다시 선택해 주세요.","cart.html","장바구니로 이동");return;
  }
  const submissionStorageKey = `${checkoutSubmissionPrefix}.${session.checkoutAccount}.${cartSelectionId ? `cart.${cartSelectionId}` : productId}`;
  let submissionKey;
  try {
    submissionKey = readCheckoutSessionValue(submissionStorageKey);
  } catch (error) {
    renderCheckoutGuard(guard, error.message);
    return;
  }

  async function requestSubmission(payload) {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 10000);
    try {
      const response = await requestAuth(payload ? ordersApi.create : ordersApi.submission, {
        method: payload ? "POST" : "GET",
        body: payload ? JSON.stringify(payload) : undefined,
        headers: { "Idempotency-Key": submissionKey, "X-Checkout-Account": session.checkoutAccount },
        cache: "no-store",
        signal: controller.signal,
      });
      const body = await readJson(response);
      if ((response.status === 200 || (payload && response.status === 201))
          && body.data?.orderId && Number.isSafeInteger(body.data.totalAmount) && body.data.totalAmount > 0
          && (cartSelection ? matchesCartOrder(body.data.items,cartSelection.items) : body.data.items?.some((item) => String(item.productId) === String(productId)))) {
        return { order: body.data, created: response.status === 201 };
      }
      if (!payload && response.status === 404) return null;
      const error = new Error(checkoutApiError(body, "주문 접수 결과를 확인하지 못했습니다."));
      error.code = body.code;
      error.requiresLogin = response.status === 401;
      error.requestId = payload ? incidentRequestId(response) : "";
      throw error;
    } finally {
      window.clearTimeout(timeout);
    }
  }

  function showSubmissionLookupError(error) {
    const accountChanged = error.code === "ORDER_06";
    renderPaymentResult({
      eyebrow: "주문 확인",
      title: "주문 접수 결과를 확인하지 못했습니다.",
      message: incidentMessage(accountChanged ? error.message : "같은 주문의 접수 결과를 다시 확인해 주세요.", error.requestId),
      showStatusCheck: !error.requiresLogin && !accountChanged,
      statusCheckText: "주문 다시 확인",
      returnHref: error.requiresLogin || accountChanged ? checkoutLoginHref() : "orders.html",
      returnText: error.requiresLogin || accountChanged ? "로그인하기" : "내 주문 보기",
    });
    statusCheckButton.onclick = () => window.location.reload();
  }

  async function openSubmittedOrder(clientKey) {
    const latest = await requestSubmission();
    if (!latest) throw new Error("접수된 주문을 다시 확인해 주세요.");
    const { order } = latest;
    if (!order.canStartPayment) {
      showSubmittedOrder(order);
      return;
    }
    const previous = storedPaymentContext?.orderId === order.orderId
      && storedPaymentContext.checkoutAccount === session.checkoutAccount ? storedPaymentContext : null;
    const item = order.items.find((entry) => String(entry.productId) === String(productId));
    const context = {
      productId: String(productId),
      cartSelectionId,
      checkoutAccount: session.checkoutAccount,
      orderId: order.orderId,
      amount: order.totalAmount,
      orderName: (order.items.length > 1 ? `${item.productName} 외 ${order.items.length-1}종` : `${item.productName} ${item.quantity}개`).slice(0, 100),
      idempotencyKey: previous?.idempotencyKey || randomUuid(),
    };
    const payment = await createTossPayment(clientKey);
    writeCheckoutPaymentContext(context);
    storedPaymentContext = context;
    await requestTossPay(payment, {
      ...context,
      customerName: order.recipientName,
      customerEmail: session.email || null,
      customerMobilePhone: order.phoneNumber,
    });
  }

  function showSubmittedOrder(order) {
    const canStart = order.canStartPayment;
    renderPaymentResult({
      eyebrow: "주문 확인",
      title: "접수된 주문을 찾았습니다.",
      message: canStart ? "저장된 주문으로 결제를 계속할 수 있습니다."
        : "현재 주문 및 결제 결과는 주문 상세에서 확인해 주세요.",
      context: { orderId: order.orderId, amount: order.totalAmount },
      showPaymentRetry: canStart,
      returnHref: `orders.html?${orderHistoryQuery({ page: 0, orderId: order.orderId })}`,
      returnText: "주문 상세 보기",
    });
    if (order.status !== "PAYMENT_PENDING" && !canStart) {
      startNewOrderButton.hidden = false;
      startNewOrderButton.onclick = () => {
        removeCheckoutSessionValue(submissionStorageKey);
        window.location.assign(checkoutReturnHref());
      };
    }
    if (!canStart) return;

    paymentRetryButton.textContent = "결제 계속하기";
    paymentRetryButton.onclick = async () => {
      paymentRetryButton.disabled = true;
      try {
        const clientKey = await fetchTossPaymentClientKey();
        await openSubmittedOrder(clientKey);
      } catch (error) {
        if (error.requiresLogin || error.code === "ORDER_06") {
          showSubmissionLookupError(error);
          return;
        }
        resultMessage.textContent = error.message || "결제창을 열지 못했습니다. 다시 시도해 주세요.";
        paymentRetryButton.disabled = false;
      }
    };
  }

  if (submissionKey) {
    try {
      const result = await requestSubmission();
      if (result) {
        showSubmittedOrder(result.order);
        return;
      }
      setCheckoutMessage(messageBox, "아직 접수 결과를 확인하지 못했습니다. 같은 내용으로 다시 제출해 주세요.");
    } catch (error) {
      showSubmissionLookupError(error);
      return;
    }
  }

  let product;
  try {
    const response = await requestAuth(productsApi.detail(productId));
    const body = await readJson(response);
    if (!response.ok || !body.data) {
      throw new Error(checkoutApiError(body, "상품 정보를 불러오지 못했습니다."));
    }
    product = body.data;
  } catch (error) {
    renderCheckoutGuard(
      guard,
      error.message || "상품 정보를 불러오지 못했습니다.",
      "products.html",
      "상품 목록으로 이동",
    );
    return;
  }

  if (!submissionKey && product.availability !== "ON_SALE") {
    renderCheckoutGuard(guard, `${inventoryLabel(product)} 상품입니다. 다른 상품을 선택해 주세요.`, "products.html", "상품 목록으로 이동");
    return;
  }

  let cartPriceConfirmation = false;
  if(cartSelection) {
    try {
      const cart = await cartRequest("/api/cart");
      const latest = validateCartSelection(cartSelection,cart);
      cartPriceConfirmation = latest.some((row,index)=>row.unitPrice !== cartSelection.items[index].expectedUnitPrice);
      cartSelection.items=latest.map(row=>({itemId:row.itemId,expectedVersion:row.version,productId:row.productId,quantity:row.selectedQuantity,expectedUnitPrice:row.unitPrice}));
      writeCheckoutSessionValue(`nanacocoa.cart.selection.${cartSelectionId}`,JSON.stringify(cartSelection));
      const single = productImage.closest(".checkout-product");single.hidden=true;
      const summary=document.createElement("div");summary.className="cart-checkout-lines";
      for(const row of latest){const line=document.createElement("p");line.textContent=`${row.name} · ${row.selectedQuantity}개 · ${formatPrice(row.unitPrice*row.selectedQuantity)}`;summary.append(line);}
      single.after(summary);
      if(cartPriceConfirmation) {
        const notice=document.createElement("p");notice.textContent="상품 가격이 변경되었습니다. 최신 금액을 확인해 주세요.";
        const confirm=document.createElement("button");confirm.type="button";confirm.textContent="변경 가격 확인";
        confirm.onclick=()=>{cartPriceConfirmation=false;notice.textContent="변경된 금액을 확인했습니다. 결제하기를 눌러주세요.";confirm.remove();};
        summary.append(notice,confirm);
      }
    } catch(error){renderCheckoutGuard(guard,error.message,"cart.html","장바구니에서 다시 선택");return;}
  }
  let submissionInProgress = false;

  recipientNameInput.value = session.name || "";
  productImage.src = getProductImageUrl(product);
  productImage.alt = `${product.name} 주문 상품 이미지`;
  productName.textContent = product.name;
  unitPrice.textContent = formatPrice(product.price);

  function readQuantity() {
    const quantity = Number(quantityInput.value);
    return Number.isSafeInteger(quantity) && quantity > 0 ? quantity : 1;
  }

  function renderOrderTotal() {
    const total = cartSelection ? cartSelection.items.reduce((sum,i)=>sum+i.expectedUnitPrice*i.quantity,0) : Number(product.price) * readQuantity();
    const totalText = formatPrice(total);
    lineTotal.textContent = totalText;
    orderTotal.textContent = totalText;
    submitButton.textContent = `${totalText} 결제하기`;
  }

  function setQuantity(quantity) {
    quantityInput.value = String(Math.max(1, quantity));
    renderOrderTotal();
  }

  function selectedCustomerRequest() {
    if (customerRequestPreset.value === "custom") {
      const request = customerRequestInput.value.trim();
      return request || null;
    }

    return customerRequestPreset.value || null;
  }

  function validateShippingAddress() {
    const postalCode = normalizeAddressPart(postalCodeInput.value);
    const basicAddress = normalizeAddressPart(shippingAddressInput.value);
    const detailAddress = normalizeAddressPart(shippingAddressDetailInput.value);
    const shippingAddress = formatShippingAddress(postalCode, basicAddress, detailAddress);

    postalCodeInput.setCustomValidity("");
    shippingAddressInput.setCustomValidity("");
    shippingAddressDetailInput.setCustomValidity("");

    if (!/^[0-9]{5}$/.test(postalCode)) {
      postalCodeInput.setCustomValidity("우편번호 5자리를 입력해 주세요.");
    }

    if (!basicAddress) {
      shippingAddressInput.setCustomValidity("기본주소를 입력해 주세요.");
    }

    if (shippingAddress.length > 500) {
      const invalidInput = detailAddress ? shippingAddressDetailInput : shippingAddressInput;
      invalidInput.setCustomValidity("우편번호와 상세주소를 포함한 전체 주소는 500자 이하로 입력해 주세요.");
    }

    return shippingAddress;
  }

  setupKakaoAddressSearch({
    postalCodeInput,
    basicAddressInput: shippingAddressInput,
    detailAddressInput: shippingAddressDetailInput,
    searchButton: addressSearchButton,
    statusElement: addressSearchStatus,
    shouldKeepDisabled: () => submissionInProgress,
  });

  customerRequestPreset.addEventListener("change", () => {
    const isCustom = customerRequestPreset.value === "custom";
    customerRequestCustom.hidden = !isCustom;

    if (isCustom) {
      customerRequestInput.focus();
    } else {
      customerRequestInput.value = "";
    }
  });

  quantityDecrease.addEventListener("click", () => setQuantity(readQuantity() - 1));
  quantityIncrease.addEventListener("click", () => setQuantity(readQuantity() + 1));
  quantityInput.addEventListener("input", renderOrderTotal);
  quantityInput.addEventListener("change", () => setQuantity(readQuantity()));

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submissionInProgress) return;
    if(cartPriceConfirmation){setCheckoutMessage(messageBox,"변경된 가격을 먼저 확인해 주세요.");return;}

    const shippingAddress = validateShippingAddress();
    const quantity = readQuantity();
    quantityInput.value = String(quantity);
    paymentTermsInput.setCustomValidity(paymentTermsInput.checked
      ? "" : "토스페이 결제 필수 약관에 동의해 주세요.");
    if (!paymentTermsInput.checked) setPaymentPanelExpanded(true);
    if (!form.reportValidity()) return;

    const payload = {
      recipientName: recipientNameInput.value.trim(),
      phoneNumber: phoneNumberInput.value.trim(),
      shippingAddress,
      customerRequest: selectedCustomerRequest(),
      items: cartSelection ? cartSelection.items.map(i=>({productId:i.productId,quantity:i.quantity})) : [{ productId: product.id, quantity }],
      ...(cartSelection ? {cartItems:cartSelection.items.map(({productId,...item})=>item)} : {}),
    };
    submissionInProgress = true;
    submitButton.disabled = true;
    submitButton.textContent = "주문 확인 중";
    setCheckoutMessage(messageBox, "");

    try {
      const isFirstSubmission = !submissionKey;
      if (!isFirstSubmission) {
        try {
          const saved = await requestSubmission();
          if (saved) {
            showSubmittedOrder(saved.order);
            return;
          }
        } catch (error) {
          showSubmissionLookupError(error);
          return;
        }
      }

      const clientKey = await fetchTossPaymentClientKey();
      if (isFirstSubmission) {
        const newKey = randomUuid();
        writeCheckoutSessionValue(submissionStorageKey, newKey);
        submissionKey = newKey;
      }

      let result;
      try {
        result = await requestSubmission(payload);
      } catch (error) {
        const rejected = ["COMMON_01", "ORDER_02", "ORDER_03", "PRODUCTS_06", "CART_01", "CART_02"].includes(error.code);
        if (rejected) {
          if (isFirstSubmission) {
            removeCheckoutSessionValue(submissionStorageKey);
            submissionKey = null;
          }
          if(cartSelection){renderCheckoutGuard(guard,incidentMessage(error.message,error.requestId),"cart.html","장바구니에서 다시 선택");form.hidden=true;guard.hidden=false;return;}
          throw error;
        }
        if (error.requiresLogin || error.code === "ORDER_06") {
          showSubmissionLookupError(error);
          return;
        }
        try {
          const saved = await requestSubmission();
          if (saved) {
            showSubmittedOrder(saved.order);
            if (error.code === "ORDER_05") resultMessage.textContent = incidentMessage(error.message, error.requestId);
            return;
          }
        } catch (lookupError) {
          showSubmissionLookupError({ ...lookupError, message: lookupError.message, requestId: error.requestId });
          return;
        }
        const missingOrder = new Error("아직 접수 결과를 확인하지 못했습니다. 같은 내용으로 다시 제출해 주세요.");
        missingOrder.requestId = error.requestId;
        throw missingOrder;
      }

      if (!result.created || !result.order.canStartPayment) {
        showSubmittedOrder(result.order);
        return;
      }
      submitButton.textContent = "토스페이 여는 중";
      try {
        await openSubmittedOrder(clientKey);
      } catch (error) {
        if (error.requiresLogin || error.code === "ORDER_06") {
          showSubmissionLookupError(error);
          return;
        }
        showSubmittedOrder(result.order);
        resultMessage.textContent = error.message || "결제창을 열지 못했습니다. 다시 시도해 주세요.";
      }
    } catch (error) {
      setCheckoutMessage(messageBox, incidentMessage(error.message || "결제 준비 중 문제가 발생했습니다.", error.requestId),
        error.requiresLogin ? checkoutLoginHref() : null);
    } finally {
      submissionInProgress = false;
      submitButton.disabled = false;
      renderOrderTotal();
    }
  });

  guard.hidden = true;
  form.hidden = false;
  renderOrderTotal();
  window.NanacocoaShippingAddresses?.checkout({form,session,hasSubmission:()=>Boolean(submissionKey),isSubmitting:()=>submissionInProgress});
}

initMenu();
initLoginPage();
initSignupPage();
initAuthSession();

async function freshCartSession() {
  authSessionPromise=null;
  const session=await loadAuthSession();
  if(session.loadFailed)throw new Error("로그인 상태를 확인하지 못했습니다. 다시 시도해 주세요.");
  return session;
}
async function cartRequest(url,options={}) {
  const response=await requestAuth(url,{...options,cache:"no-store"});
  const body=await readJson(response);
  if(!response.ok || !body.data){
    const error=new Error(response.status===401?"로그인이 만료되었습니다. 로그인 후 다시 시도해 주세요.":checkoutApiError(body,"장바구니 결과를 확인하지 못했습니다. 같은 요청으로 다시 시도해 주세요."));
    error.definitive=[400,404,409].includes(response.status);error.status=response.status;throw error;
  }
  return body.data;
}
function renderCartCount(cart) {
  document.querySelectorAll(".cart-dot").forEach(dot=>{dot.textContent=String(cart.totalQuantity);});
}
function readCartSelection(id) {
  if(!/^[0-9a-f-]{36}$/.test(id||""))throw new Error("장바구니 선택 정보가 없습니다.");
  const selection=JSON.parse(readCheckoutSessionValue(`nanacocoa.cart.selection.${id}`)||"null");
  if(!selection?.checkoutAccount || !Array.isArray(selection.items) || !selection.items.length)throw new Error("장바구니에서 상품을 다시 선택해 주세요.");
  return selection;
}
function matchesCartOrder(items,selection) {
  return Array.isArray(items) && items.length===selection.length && selection.every(s=>items.some(i=>String(i.productId)===String(s.productId)&&i.quantity===s.quantity));
}
function validateCartSelection(selection,cart) {
  const ids=new Set();
  return selection.items.map(s=>{
    const row=cart.items.find(i=>i.itemId===s.itemId);
    if(ids.has(s.itemId)|| !row || row.version!==s.expectedVersion || row.productId!==s.productId || !Number.isInteger(s.quantity) || s.quantity<=0 || row.quantity<s.quantity || row.availability!=="ON_SALE")throw new Error("장바구니 항목이 변경되었거나 구매할 수 없습니다. 다시 선택해 주세요.");
    ids.add(s.itemId);return {...row,selectedQuantity:s.quantity};
  });
}
async function initCartNavigation() {
  document.querySelectorAll(".cart-link").forEach(link=>{link.href="cart.html";link.setAttribute("aria-label","장바구니");
    if(!link.querySelector(".cart-dot")){const dot=document.createElement("span");dot.className="cart-dot";dot.setAttribute("aria-hidden","true");dot.textContent="0";link.append(dot);}
  });
  if(!document.querySelector(".cart-dot"))return;
  try{const session=await loadAuthSession();if(session.authenticated)renderCartCount(await cartRequest("/api/cart"));}catch{document.querySelectorAll(".cart-dot").forEach(dot=>{dot.textContent="?";});}
}
async function initCartPage() {
  const page=document.querySelector("[data-cart-page]");if(!page)return;
  const message=page.querySelector("[data-cart-message]"),list=page.querySelector("[data-cart-list]"),total=page.querySelector("[data-cart-total]"),buy=page.querySelector("[data-cart-buy]"),all=page.querySelector("[data-cart-all]");
  let cart,session,selected=new Set(),busy=false;
  window.addEventListener("pagehide",()=>{list.replaceChildren();buy.disabled=true;});
  window.addEventListener("pageshow",event=>{if(event.persisted)window.location.reload();});
  function selection(){return cart.items.filter(i=>selected.has(i.itemId)&&i.availability==="ON_SALE");}
  function updateTotal(){const amount=selection().reduce((sum,i)=>sum+i.unitPrice*i.quantity,0);total.textContent=formatPrice(amount);buy.disabled=busy||!selection().length||!Number.isSafeInteger(amount);all.checked=selection().length>0&&selection().length===cart.items.filter(i=>i.availability==="ON_SALE").length;}
  async function change(url,options){
    if(busy)return;busy=true;all.disabled=true;list.querySelectorAll("button,input").forEach(e=>e.disabled=true);buy.disabled=true;
    try{
      const current=await freshCartSession();if(!current.authenticated||current.checkoutAccount!==session.checkoutAccount)throw new Error("계정이 변경되었습니다. 로그인 후 장바구니를 다시 조회해 주세요.");
      cart=await cartRequest(url,options);renderCartCount(cart);message.textContent="장바구니를 변경했습니다.";
    }catch(error){message.textContent=error.message;try{const current=await freshCartSession();if(current.checkoutAccount!==session.checkoutAccount){cart={items:[],totalQuantity:0};}else cart=await cartRequest("/api/cart");}catch{cart={items:[],totalQuantity:0};}}
    finally{busy=false;render();}
  }
  function render(){
    list.replaceChildren();
    for(const item of cart.items){
      const row=document.createElement("article");row.className="checkout-product cart-item";
      const choose=document.createElement("input");choose.type="checkbox";choose.setAttribute("aria-label",`${item.name} 선택`);choose.checked=selected.has(item.itemId)&&item.availability==="ON_SALE";choose.disabled=item.availability!=="ON_SALE";choose.onchange=()=>{choose.checked?selected.add(item.itemId):selected.delete(item.itemId);updateTotal();};
      const image=document.createElement("img");image.src=getProductImageUrl(item);image.alt=item.name;
      const copy=document.createElement("div");copy.className="checkout-product-copy";
      const name=document.createElement("strong");name.textContent=item.name;
      const price=document.createElement("span");price.textContent=`${formatPrice(item.unitPrice)} · ${item.availability==="ON_SALE"?"판매 중":inventoryLabel(item)}`;
      const qty=document.createElement("input");qty.type="number";qty.min="1";qty.max="2147483647";qty.step="1";qty.value=String(item.quantity);qty.setAttribute("aria-label",`${item.name} 수량`);
      const save=document.createElement("button");save.type="button";save.textContent="수량 변경";save.onclick=()=>{if(!qty.reportValidity())return;return change(`/api/cart/items/${item.itemId}`,{method:"PUT",body:JSON.stringify({quantity:Number(qty.value),expectedVersion:item.version})});};
      const remove=document.createElement("button");remove.type="button";remove.textContent="삭제";remove.onclick=()=>change(`/api/cart/items/${item.itemId}?expectedVersion=${item.version}`,{method:"DELETE"});
      copy.append(name,price,qty,save,remove);row.append(choose,image,copy);list.append(row);
    }
    if(!cart.items.length){const empty=document.createElement("p");empty.textContent="장바구니가 비어 있습니다.";list.append(empty);}
    all.disabled=!cart.items.some(i=>i.availability==="ON_SALE");updateTotal();
  }
  all.onchange=()=>{if(busy)return;selected=new Set(all.checked?cart.items.filter(i=>i.availability==="ON_SALE").map(i=>i.itemId):[]);render();};
  buy.onclick=async()=>{
    if(busy||!selection().length)return;busy=true;buy.disabled=true;
    try{
      const current=await freshCartSession();if(current.checkoutAccount!==session.checkoutAccount)throw new Error("계정이 변경되었습니다. 장바구니를 새로고침해 주세요.");
      const id=randomUuid();writeCheckoutSessionValue(`nanacocoa.cart.selection.${id}`,JSON.stringify({checkoutAccount:session.checkoutAccount,items:selection().map(i=>({itemId:i.itemId,expectedVersion:i.version,productId:i.productId,quantity:i.quantity,expectedUnitPrice:i.unitPrice}))}));
      window.location.assign(`checkout.html?cart=${id}`);
    }catch(error){busy=false;message.textContent=error.message;updateTotal();}
  };
  try{
    session=await freshCartSession();if(!session.authenticated){message.replaceChildren(document.createTextNode("장바구니를 보려면 로그인해 주세요. "));const login=document.createElement("a");login.href="login.html?return=cart";login.textContent="로그인하기";message.append(login);return;}
    cart=await cartRequest("/api/cart");selected=new Set(cart.items.filter(i=>i.availability==="ON_SALE").map(i=>i.itemId));message.textContent="담기는 재고 예약이 아닙니다. 주문 시 가격과 재고를 다시 확인합니다.";render();
  }catch(error){message.textContent=error.message;}
}

initCartNavigation();
initCartPage();
initProductDetail();
initProductRegisterPage();
initHomeDiscoveryLinks();
initCheckoutPage();
initMyPage();
initMypageVerification();

function inventoryLabel(product) {
  return product.availability === "STOPPED" ? "판매 중지"
    : product.availability === "SOLD_OUT" ? "품절" : "판매 중";
}

async function initInventoryPage() {
  const page = document.querySelector("[data-inventory-page]");
  if (!page) return;
  const message = page.querySelector("[data-inventory-message]");
  const content = page.querySelector("[data-inventory-content]");
  const grid = page.querySelector("[data-inventory-grid]");
  const session = await loadAuthSession();
  if (session.loadFailed || !session.authenticated || !session.admin) {
    message.textContent = session.loadFailed ? "로그인 상태를 확인하지 못했습니다. 새로고침해 주세요." : "운영자 로그인이 필요합니다.";
    return;
  }
  content.hidden = false;

  async function loadInventory() {
    const response = await requestAuth("/api/products/inventory");
    const body = await readJson(response);
    if (!response.ok || !Array.isArray(body.data)) throw new Error("상품 재고를 불러오지 못했습니다.");
    grid.replaceChildren();
    for (const product of body.data) {
      const form = document.createElement("form");
      form.className = "inventory-card";
      const heading = document.createElement("h2");
      heading.textContent = product.name;
      const status = document.createElement("p");
      status.textContent = `${inventoryLabel(product)} · 판매 가능 재고 ${product.stockQuantity}개 · 결제 대기 재고 ${product.reservedQuantity}개`;
      const label = document.createElement("label");
      label.textContent = "판매 가능 재고 ";
      const input = document.createElement("input");
      input.type = "number"; input.min = "0"; input.max = "1000000000"; input.step = "1"; input.required = true;
      input.value = String(product.stockQuantity);
      label.append(input);
      const saleLabel = document.createElement("label");
      saleLabel.textContent = "판매 상태 ";
      const select = document.createElement("select");
      for (const [value, text] of [["ON_SALE", "판매 중"], ["STOPPED", "판매 중지"]]) {
        const option = document.createElement("option"); option.value = value; option.textContent = text; select.append(option);
      }
      select.value = product.availability === "STOPPED" ? "STOPPED" : "ON_SALE";
      saleLabel.append(select);
      const button = document.createElement("button"); button.type = "submit"; button.textContent = "재고 저장";
      form.append(heading, status, label, saleLabel, button);
      form.addEventListener("submit", async (event) => {
        event.preventDefault(); button.disabled = true;
        try {
          const response = await requestAuth(`${productsApi.detail(product.id)}/inventory`, {
            method: "PUT", body: JSON.stringify({stockQuantity: Number(input.value), saleStatus: select.value, expectedVersion: product.stockVersion}),
          });
          const body = await readJson(response);
          if (!response.ok) throw new Error(checkoutApiError(body, "재고를 저장하지 못했습니다."));
          message.textContent = `${product.name} 재고를 저장했습니다.`;
          await loadInventory();
        } catch (error) {
          message.textContent = error.message;
          if (error.message.includes("새로고침")) await loadInventory().catch(() => {});
        } finally { button.disabled = false; }
      });
      grid.append(form);
    }
  }
  page.querySelector("[data-inventory-refresh]").addEventListener("click", () => loadInventory().catch(error => message.textContent = error.message));
  const restockForm = page.querySelector("[data-restock-form]");
  restockForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = restockForm.querySelector("button"); button.disabled = true;
    try {
      const values = new FormData(restockForm);
      const response = await requestAuth(`/api/orders/${encodeURIComponent(String(values.get("orderId")).trim())}/restock`, {
        method: "POST", body: JSON.stringify({reason: String(values.get("reason")).trim()}),
      });
      const body = await readJson(response);
      if (!response.ok) throw new Error(checkoutApiError(body, "검수 재입고를 처리하지 못했습니다."));
      message.textContent = "취소 주문의 검수 재입고를 처리했습니다. 동일 주문은 중복 반영하지 않습니다.";
      restockForm.reset(); await loadInventory();
    } catch (error) { message.textContent = error.message; }
    finally { button.disabled = false; }
  });
  try { await loadInventory(); message.textContent = "입고·재고 조정은 판매 가능 수량에 반영됩니다. 결제 대기 재고는 별도로 유지됩니다."; }
  catch (error) { message.textContent = error.message; }
}
initInventoryPage();

window.NanacocoaShippingAddresses?.manage();
