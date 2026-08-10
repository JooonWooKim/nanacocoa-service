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
const loginRedirectUrl = "index.html";
const defaultProductImage = "IMG_2398.JPG";
const maxProductImageSize = 5 * 1024 * 1024;
const kakaoPostcodeScriptUrl = "https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";
let authSessionPromise;
let kakaoPostcodePromise;

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

      window.location.href = loginRedirectUrl;
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

async function initCheckoutPage() {
  const page = document.querySelector("[data-checkout-page]");

  if (!page) {
    return;
  }

  const form = page.querySelector("[data-checkout-form]");
  const guard = page.querySelector("[data-checkout-guard]");
  const messageBox = page.querySelector("[data-checkout-message]");
  const submitButton = page.querySelector("[data-order-submit]");
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
  const successPanel = page.querySelector("[data-checkout-success]");

  if (!form || !guard || !messageBox || !submitButton || !recipientNameInput
      || !phoneNumberInput || !postalCodeInput || !shippingAddressInput
      || !shippingAddressDetailInput || !addressSearchButton || !addressSearchStatus
      || !customerRequestInput || !customerRequestPreset || !customerRequestCustom
      || !quantityInput || !quantityDecrease || !quantityIncrease || !productImage
      || !productName || !unitPrice || !lineTotal || !orderTotal || !successPanel) {
    renderCheckoutGuard(guard, "주문 페이지를 표시하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    return;
  }

  const params = new URLSearchParams(window.location.search);
  const productId = params.get("id");

  if (!productId) {
    renderCheckoutGuard(guard, "주문할 상품 정보가 없습니다.", "products.html", "상품 목록으로 이동");
    return;
  }

  const productPromise = (async () => {
    const response = await requestAuth(productsApi.detail(productId));
    const body = await readJson(response);

    if (!response.ok || !body.data) {
      throw new Error(body.error || body.message || "상품 정보를 불러오지 못했습니다.");
    }

    return body.data;
  })();

  const [session, productResult] = await Promise.all([
    loadAuthSession(),
    productPromise.then((product) => ({ product })).catch((error) => ({ error })),
  ]);

  if (session.loadFailed) {
    renderCheckoutGuard(guard, "로그인 상태를 확인하지 못했습니다. 페이지를 새로고침해 주세요.");
    return;
  }

  if (!session.authenticated) {
    renderCheckoutGuard(guard, "주문하려면 로그인이 필요합니다.", "login.html", "로그인하기");
    return;
  }

  if (productResult.error) {
    renderCheckoutGuard(
      guard,
      productResult.error.message || "상품 정보를 불러오지 못했습니다.",
      "products.html",
      "상품 목록으로 이동",
    );
    return;
  }

  const product = productResult.product;
  let orderSubmitted = false;

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
    submitButton.textContent = `${totalText} 주문하기`;
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
      addressSearchButton.disabled = false;
      addressSearchButton.textContent = "주소검색";
      setAddressSearchStatus("");
    })
    .catch(() => {
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

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (orderSubmitted) {
      return;
    }

    const shippingAddress = validateShippingAddress();

    if (!form.reportValidity()) {
      return;
    }

    const quantity = readQuantity();
    quantityInput.value = String(quantity);
    setCheckoutMessage(messageBox, "");
    submitButton.disabled = true;
    submitButton.textContent = "주문 생성 중";

    const payload = {
      recipientName: recipientNameInput.value.trim(),
      phoneNumber: phoneNumberInput.value.trim(),
      shippingAddress,
      customerRequest: selectedCustomerRequest(),
      items: [{ productId: product.id, quantity }],
    };

    try {
      const response = await requestAuth(ordersApi.create, {
        method: "POST",
        body: JSON.stringify(payload),
      });
      const body = await readJson(response);

      if (response.status === 401) {
        setCheckoutMessage(messageBox, "로그인이 만료되었습니다. 다시 로그인해 주세요.", true);
        return;
      }

      if (!response.ok || !body.data?.orderId) {
        throw new Error(body.error || body.message || "주문을 생성하지 못했습니다.");
      }

      orderSubmitted = true;
      form.hidden = true;
      successPanel.querySelector("[data-created-order-id]").textContent = body.data.orderId;
      successPanel.querySelector("[data-created-order-total]").textContent = formatPrice(body.data.totalAmount);
      successPanel.hidden = false;
      successPanel.scrollIntoView({ behavior: "smooth", block: "center" });
    } catch (error) {
      setCheckoutMessage(messageBox, error.message || "주문 생성 중 문제가 발생했습니다.");
    } finally {
      if (!orderSubmitted) {
        submitButton.disabled = false;
        renderOrderTotal();
      }
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
