const authApi = {
  signup: "/api/auth/signup",
  login: "/api/auth/login",
  session: "/api/auth/session",
  logout: "/api/auth/logout",
};
const productsApi = {
  list: "/api/products",
  detail: (productId) => `/api/products/${encodeURIComponent(productId)}`,
};
const ordersApi = {
  create: "/api/orders",
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

function requestAuth(url, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("Accept", "application/json");

  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  return fetch(url, {
    ...options,
    credentials: "same-origin",
    headers,
  });
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

function initLoginPage() {
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

      window.location.href = takePaymentCallbackLoginReturnUrl() || loginRedirectUrl;
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

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    setAuthMessage("");

    const formData = new FormData(form);
    const payload = {
      email: String(formData.get("email") || ""),
      password: String(formData.get("password") || ""),
      name: String(formData.get("name") || ""),
    };

    submitButton.disabled = true;
    submitButton.textContent = "가입 중";

    try {
      const response = await requestAuth(authApi.signup, {
        method: "POST",
        body: JSON.stringify(payload),
      });
      const data = await readJson(response);

      if (!response.ok || !data.authenticated) {
        throw new Error(data.message || "회원가입 정보를 확인해 주세요.");
      }

      window.location.href = loginRedirectUrl;
    } catch (error) {
      setAuthMessage(error.message || "회원가입 중 문제가 발생했습니다.");
    } finally {
      submitButton.disabled = false;
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
  const logoutButton = document.createElement("button");

  message.textContent = `${session.name || session.email}님 반갑습니다.`;
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
    } catch {
      logoutButton.disabled = false;
      logoutButton.textContent = "로그아웃";
    }
  });

  menuSession.replaceChildren(message, logoutButton);
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

  if (!session.authenticated || !session.admin) {
    if (guard) {
      guard.textContent = "운영자만 상품 등록 페이지를 이용할 수 있습니다.";
    }
    window.alert("접근 권한이 없습니다.");
    return;
  }

  if (!guard || !form || !imageInput || !imageName || !submitButton) {
    if (guard) {
      guard.textContent = "상품 등록 화면을 불러오지 못했습니다.";
    }
    return;
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
        form.hidden = true;
        guard.hidden = false;
        guard.textContent = "운영자만 상품 등록 페이지를 이용할 수 있습니다.";
        window.alert("접근 권한이 없습니다.");
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
  price.textContent = formatPrice(product.price);

  info.append(name, price);
  link.append(image, info);

  return link;
}

function renderProductsState(grid, message) {
  const state = document.createElement("p");
  state.className = "products-empty";
  state.textContent = message;
  grid.replaceChildren(state);
}

async function initProductsPage() {
  const productsPage = document.querySelector("[data-products-page]");

  if (!productsPage) {
    return;
  }

  const grid = document.querySelector("[data-products-grid]");
  const total = document.querySelector("[data-products-total]");

  if (!grid || !total) {
    return;
  }

  renderProductsState(grid, "상품을 불러오는 중입니다.");

  try {
    const response = await requestAuth(productsApi.list);
    const body = await readJson(response);

    if (!response.ok) {
      throw new Error(body.error || "상품을 불러오지 못했습니다.");
    }

    const products = Array.isArray(body.data) ? body.data : [];
    total.textContent = String(products.length);

    if (products.length === 0) {
      renderProductsState(grid, "등록된 상품이 없습니다.");
      return;
    }

    grid.replaceChildren(...products.map(createProductCard));
  } catch (error) {
    total.textContent = "0";
    renderProductsState(grid, error.message || "상품을 불러오지 못했습니다.");
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

  cartButton.addEventListener("click", () => {
    const cartDot = document.querySelector(".cart-dot");
    const nextCount = Number(cartDot.textContent || "0") + 1;
    cartDot.textContent = String(nextCount);
    cartButton.textContent = "장바구니 담김";
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

function setCheckoutMessage(messageBox, message, includeLoginLink = false) {
  messageBox.replaceChildren(document.createTextNode(message || ""));

  if (!includeLoginLink) {
    return;
  }

  const loginLink = document.createElement("a");
  loginLink.href = "login.html";
  loginLink.textContent = "로그인하기";
  messageBox.append(" ", loginLink);
}

function checkoutApiError(body, fallback) {
  return body?.error || body?.message || fallback;
}

async function createTossPayment() {
  const configPromise = (async () => {
    const response = await requestAuth(paymentsApi.clientConfig);
    const body = await readJson(response);

    if (response.status === 401) {
      const error = new Error("로그인이 만료되었습니다. 다시 로그인해 주세요.");
      error.requiresLogin = true;
      throw error;
    }

    if (response.status === 503) {
      throw new Error("토스페이 설정을 사용할 수 없습니다. 관리자에게 문의해 주세요.");
    }

    if (!response.ok || !body.data?.clientKey) {
      throw new Error(checkoutApiError(body, "토스페이 설정을 불러오지 못했습니다."));
    }

    return body.data.clientKey;
  })();

  const [TossPayments, clientKey] = await Promise.all([loadTossPaymentsSdk(), configPromise]);
  return TossPayments(clientKey).payment({ customerKey: checkoutCustomerKey() });
}

function checkoutPaymentCallbackUrl(result, context) {
  const callbackUrl = new URL("checkout.html", window.location.href);
  callbackUrl.searchParams.set("id", context.productId);
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
  const paymentReturnLink = resultPanel?.querySelector("[data-payment-return]");

  if (!form || !guard || !messageBox || !submitButton || !paymentMethodToggle
      || !paymentMethodPanel || !paymentTermsInput || !recipientNameInput
      || !phoneNumberInput || !postalCodeInput || !shippingAddressInput
      || !shippingAddressDetailInput || !addressSearchButton || !addressSearchStatus
      || !customerRequestInput || !customerRequestPreset || !customerRequestCustom
      || !quantityInput || !quantityDecrease || !quantityIncrease || !productImage
      || !productName || !unitPrice || !lineTotal || !orderTotal || !resultPanel
      || !resultEyebrow || !resultTitle || !resultMessage || !resultOrderId
      || !resultTotal || !statusCheckButton || !paymentRetryButton || !paymentReturnLink) {
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

  const productId = params.get("id") || storedPaymentContext?.productId;
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

  let paymentSetupPromise;
  function prepareTossPayment() {
    if (!paymentSetupPromise) {
      paymentSetupPromise = createTossPayment().catch((error) => {
        paymentSetupPromise = null;
        throw error;
      });
    }
    return paymentSetupPromise;
  }

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
    return `checkout.html?id=${encodeURIComponent(productId)}`;
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
      const payment = await prepareTossPayment();
      renewCheckoutPaymentAttempt(context);
      resultMessage.textContent = "토스페이 결제창으로 이동하고 있습니다.";
      await requestTossPay(payment, context);
    } catch (error) {
      renderPaymentFailure(error.message || "토스페이 결제창을 열지 못했습니다.", context, true);
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

      if (response.status === 401) {
        preservePaymentCallbackLoginReturnUrl();
        renderPaymentFailure("로그인이 만료되었습니다. 다시 로그인해 결제 승인을 확인해 주세요.", context, false, true);
        return;
      }

      if (response.status === 202 && body.data?.status === "PENDING" && body.data.paymentId) {
        context.paymentId = body.data.paymentId;
        renderPaymentPending(context);
        return;
      }

      if (response.ok && body.data?.status === "SUCCESS") {
        context.paymentId = body.data.paymentId;
        renderPaymentSuccess(context);
        return;
      }

      renderPaymentFailure(
        checkoutApiError(body, "결제 승인에 실패했습니다. 주문서에서 다시 시도해 주세요."),
        context,
        response.status === 422 || response.status === 503,
      );
    } catch (error) {
      renderPaymentResult({
        eyebrow: "PAYMENT APPROVAL",
        title: "결제 승인 결과를 확인하지 못했습니다.",
        message: error.message || "네트워크 연결을 확인한 뒤 승인을 다시 시도해 주세요.",
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
    renderCheckoutGuard(guard, "주문하려면 로그인이 필요합니다.", "login.html", "로그인하기");
    return;
  }

  if (paymentResult === "success") {
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

  let submissionInProgress = false;
  let createdPaymentContext = null;

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
    const total = Number(product.price) * readQuantity();
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

  function setAddressSearchStatus(message) {
    addressSearchStatus.textContent = message || "";
    addressSearchStatus.hidden = !message;
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

  function disableAddressSearch(message) {
    addressSearchButton.disabled = true;
    addressSearchButton.textContent = "주소검색 불가";
    setAddressSearchStatus(message);
  }

  addressSearchButton.addEventListener("click", () => {
    const Postcode = window.kakao?.Postcode;

    if (typeof Postcode !== "function") {
      disableAddressSearch("주소검색을 사용할 수 없습니다. 우편번호와 기본주소를 직접 입력해 주세요.");
      return;
    }

    try {
      new Postcode({
        oncomplete(data) {
          const postalCode = normalizeAddressPart(data.zonecode);
          const selectedAddress = data.userSelectedType === "R" ? data.roadAddress : data.jibunAddress;
          const basicAddress = normalizeAddressPart(selectedAddress || data.address);

          if (!postalCode || !basicAddress) {
            setAddressSearchStatus("선택한 주소를 입력하지 못했습니다. 다시 검색하거나 직접 입력해 주세요.");
            return;
          }

          postalCodeInput.value = postalCode;
          shippingAddressInput.value = basicAddress;
          shippingAddressDetailInput.value = "";
          postalCodeInput.setCustomValidity("");
          shippingAddressInput.setCustomValidity("");
          shippingAddressDetailInput.setCustomValidity("");
          setAddressSearchStatus("");
          shippingAddressDetailInput.focus();
        },
      }).open();
    } catch {
      disableAddressSearch("주소검색 창을 열지 못했습니다. 우편번호와 기본주소를 직접 입력해 주세요.");
    }
  });

  postalCodeInput.addEventListener("input", () => postalCodeInput.setCustomValidity(""));
  shippingAddressInput.addEventListener("input", () => shippingAddressInput.setCustomValidity(""));
  shippingAddressDetailInput.addEventListener("input", () => shippingAddressDetailInput.setCustomValidity(""));

  loadKakaoPostcode()
    .then(() => {
      if (createdPaymentContext) {
        return;
      }
      addressSearchButton.disabled = false;
      addressSearchButton.textContent = "주소검색";
      setAddressSearchStatus("");
    })
    .catch(() => {
      if (createdPaymentContext) {
        return;
      }
      disableAddressSearch("주소검색을 불러오지 못했습니다. 우편번호와 기본주소를 직접 입력해 주세요.");
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

  function lockCreatedOrderFields() {
    form.querySelectorAll("input, textarea, select, button").forEach((control) => {
      if (control !== submitButton && control !== paymentMethodToggle) {
        control.disabled = true;
      }
    });
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (submissionInProgress) {
      return;
    }

    let orderPayload;
    let quantity;
    if (!createdPaymentContext) {
      const shippingAddress = validateShippingAddress();
      quantity = readQuantity();
      quantityInput.value = String(quantity);

      if (!paymentTermsInput.checked) {
        setPaymentPanelExpanded(true);
        paymentTermsInput.setCustomValidity("토스페이 결제 필수 약관에 동의해 주세요.");
      } else {
        paymentTermsInput.setCustomValidity("");
      }

      if (!form.reportValidity()) {
        return;
      }

      orderPayload = {
        recipientName: recipientNameInput.value.trim(),
        phoneNumber: phoneNumberInput.value.trim(),
        shippingAddress,
        customerRequest: selectedCustomerRequest(),
        items: [{ productId: product.id, quantity }],
      };
    }

    submissionInProgress = true;
    setCheckoutMessage(messageBox, "");
    submitButton.disabled = true;
    submitButton.textContent = "결제 준비 중";

    try {
      const payment = await prepareTossPayment();

      if (!createdPaymentContext) {
        submitButton.textContent = "주문 생성 중";
        const idempotencyKey = randomUuid();
        const response = await requestAuth(ordersApi.create, {
          method: "POST",
          body: JSON.stringify(orderPayload),
        });
        const body = await readJson(response);

        if (response.status === 401) {
          const error = new Error("로그인이 만료되었습니다. 다시 로그인해 주세요.");
          error.requiresLogin = true;
          throw error;
        }

        const serverAmount = Number(body.data?.totalAmount);
        if (!response.ok || !body.data?.orderId || !Number.isSafeInteger(serverAmount) || serverAmount <= 0) {
          throw new Error(checkoutApiError(body, "주문을 생성하지 못했습니다."));
        }

        createdPaymentContext = {
          productId: String(product.id),
          orderId: body.data.orderId,
          amount: serverAmount,
          orderName: `${product.name} ${quantity}개`.slice(0, 100),
          idempotencyKey,
          customerName: orderPayload.recipientName,
          customerEmail: session.email || null,
          customerMobilePhone: orderPayload.phoneNumber,
        };
        writeCheckoutPaymentContext(createdPaymentContext);
        lockCreatedOrderFields();
      }

      submitButton.textContent = "토스페이 여는 중";
      await requestTossPay(payment, createdPaymentContext);
    } catch (error) {
      submissionInProgress = false;
      setCheckoutMessage(
        messageBox,
        error.message || "결제 준비 중 문제가 발생했습니다.",
        Boolean(error.requiresLogin),
      );
      submitButton.disabled = false;
      renderOrderTotal();
    }
  });

  guard.hidden = true;
  form.hidden = false;
  renderOrderTotal();
}

initMenu();
initLoginPage();
initSignupPage();
initAuthSession();
initProductsPage();
initProductDetail();
initProductRegisterPage();
initCheckoutPage();
